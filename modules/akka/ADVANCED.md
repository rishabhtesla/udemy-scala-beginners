# Advanced Akka handbook

This is an original, self-contained explanation of the advanced curriculum in
this repository. It targets **Akka 2.6.21 / Scala 2.13 / JDK 17**. Start with
[the introduction](README.md). You need to understand a case class, pattern
match, immutable collection, `Future`, and the difference between a type and a
value. New framework terms are introduced below.

This is not a claim to document every Akka API. Actor/stream/persistence lessons
are runnable. Cluster, sharding, HTTP, external journals, and deployment sections
are **explained designs**, not secretly installed or production-validated systems.
Akka 2.6 is historical; check licensing/support before selecting newer Akka.

## Contents

1. [Learning map and executable contracts](#1-learning-map-and-executable-contracts)
2. [What an actor actually guarantees](#2-what-an-actor-actually-guarantees)
3. [Asynchronous work, stashing, and overload](#3-asynchronous-work-stashing-and-overload)
4. [Lifecycle, supervision, timers, and discovery](#4-lifecycle-supervision-timers-and-discovery)
5. [Event sourcing and recovery](#5-event-sourcing-and-recovery)
6. [Delivery, retries, and transactions](#6-delivery-retries-and-transactions)
7. [Streams beyond map and filter](#7-streams-beyond-map-and-filter)
8. [Cluster and sharding architecture](#8-cluster-and-sharding-architecture)
9. [HTTP, persistence storage, and system boundaries](#9-http-persistence-storage-and-system-boundaries)
10. [Testing and operational reasoning](#10-testing-and-operational-reasoning)
11. [Worked design and exercises with answers](#11-worked-design-and-exercises-with-answers)

## 1. Learning map and executable contracts

Run from the repository root using JDK 17:

```bash
sbt "akka/runMain lectures.part7akka.AsyncActor"
sbt "akka/runMain lectures.part7akka.PersistentAccount"
sbt "akka/runMain lectures.part7akka.AdvancedStreams"
sbt "akka/test"
```

| Source under `src/main/scala/lectures/part7akka/` | Contract |
|---|---|
| `AsyncActor.scala` | Load once asynchronously; serve requests after success; explicitly report failure or a full startup buffer |
| `PersistentAccount.scala` | Credit with an operation ID; persist before replying; replay events; reject conflicting ID reuse |
| `AdvancedStreams.scala` | Broadcast each integer into two ordered branches and zip their results |

Expected business output: `Value(configuration-v1)`; then, for the persistent
example, `Accepted(50)`, `Accepted(50)`, `Balance(50)`; and for the graph,
a sequence of `(2,3)`, `(4,6)`, `(6,9)` (collection formatting may be `Vector`).
Runtime logs are separate from these values.

The tests check actor recreation/replay, duplicate and conflicting requests,
validation, async success/failure, and graph results. They do not test cross-process
journal durability, multi-node partitions, or every production overload scenario.

## 2. What an actor actually guarantees

### 2.1 Identity, reference, protocol, behavior, state

An actor has an identity within a running system. Its `ActorRef[A]` is a typed
address that accepts messages of type `A`. A protocol is the ADT (sealed trait
plus case classes) defining permitted requests. A behavior decides what a message
means. State is data that the behavior carries between messages.

These are different things: changing behavior does not change actor identity;
changing state does not expose it directly to another actor. A reference is not
a method pointer, and `!` does not wait for execution.

```text
producer -- message --> mailbox -- scheduled processing --> behavior
                                                        |
                                                   next behavior
```

The runtime processes one message at a time for a particular actor. A dispatcher
shares threads between actors. A busy actor is not necessarily a busy dedicated
thread, and a thousand actors do not require a thousand threads.

The actor's handler still needs invariants. Sequential processing cannot prevent
integer overflow, invalid prices, duplicate business commands, or using an
untrusted ID. The account examples validate these at the command boundary.

### 2.2 Ordering and visibility

Sending two messages from one actor to one recipient preserves their enqueue
order under the normal actor delivery model. It does not guarantee they arrive,
and does not give global order between different senders. Async computations
started in one order can finish in another order.

Two separate `ask` calls use separate temporary reply actors. Chaining asks by
`flatMap` waits for the first reply before submitting the next operation; submitting
both immediately does not create a transaction or a reliable business ordering.

Do not pass a mutable collection and then mutate it after sending. Actor
isolation does not deep-copy ordinary local messages. Prefer immutable case
classes and immutable payloads; remote serialization also requires supported,
versioned formats.

### 2.3 Ask is a protocol adaptation, not a remote function call

`ask` creates a temporary reply destination and a future. The receiver must
send a compatible reply. Timeouts, stopped actors, unknown messages, and a
missing reply can all cause the future to fail.

If a debit commits at time 10 but its reply is delayed beyond the client's timeout
at time 11, retrying a fresh debit at time 12 can debit twice. A timeout says
"no result observed before the deadline", not "operation rolled back".

Carry correlation IDs for tracing and operation IDs for deduplication. Those
IDs serve different purposes: retries can have different request traces but the
same business operation ID.

### 2.4 Encapsulation has a thread boundary

The following conceptual pattern is unsafe:

```scala
// Unsafe design, not used in this repository:
// future.foreach(value => currentState = value)
```

The callback can run on another thread concurrently with actor processing.
Use `pipeToSelf` so completion becomes another protocol message. A synchronous
method that starts a future must also be considered: if that method throws
before returning the future, actor supervision handles the exception, not
`pipeToSelf`. Validate service contracts and test both failure styles.

## 3. Asynchronous work, stashing, and overload

### 3.1 Trace `AsyncActor.scala` one transition at a time

The public request is `Read(replyTo)`. Replies are `Value(text)` and
`Unavailable(reason)`. `Loaded` and `LoadFailed` are private internal messages.
Keeping completions private prevents clients from pretending to complete startup.

The actor constructs a stash of capacity 16. Startup calls `load()` once and
uses `context.pipeToSelf` to turn the future into `Loaded` or `LoadFailed`.
While waiting, reads go into the stash. On success it switches to `ready` and
replays queued reads using `unstashAll`. On failure it logs the cause and replays
reads against a failed behavior, which replies explicitly.

```text
                    Loaded(v) --> Ready(v) --> Read -> Value(v)
Waiting / stash ----+
                    LoadFailed --> Failed --> Read -> Unavailable
```

This is a small state machine: state transitions are specified rather than
hidden in callback mutation. The future is not blocked with `Await`, so the
dispatcher remains available.

### 3.2 A stash is not the mailbox

The mailbox holds incoming messages not yet processed. The stash holds messages
the actor already inspected but deliberately postponed. A stash's capacity does
not cap the mailbox or total ingress traffic.

The example rejects a read when `stash.isFull`. That prevents stash overflow,
but does not bound all producer traffic. A production request boundary should
also limit admitted requests, deadlines, payload size, and per-client load.

Unstashing preserves stash order into the selected behavior, but messages from
other senders may have arrived in an interleaved order. Never use a stash as a
durable business queue; it disappears with process memory and lifecycle changes.

### 3.3 Slow work and execution contexts

`Future(blockingCall())` does not make blocking disappear; it moves the blocking
to whichever execution context executes it. Blocking database/file calls should
use a deliberately sized, isolated dispatcher or a genuinely asynchronous client.
Do not route them to the same pool running latency-sensitive actor mailboxes.

Conceptual HOCON for a dedicated dispatcher:

```hocon
blocking-dispatcher {
  type = Dispatcher
  executor = "thread-pool-executor"
  thread-pool-executor.fixed-pool-size = 4
  throughput = 1
}
```

This configuration is illustrative, not installed. Pool size 4 is not a universal
recommendation: size from concurrency, service latency, connection pool limits,
and memory. A bounded upstream admission policy is still needed if work can queue.

### 3.4 Rate, concurrency, and memory are different limits

For a stable system, approximate in-flight work with Little's law:
`L = arrival_rate * average_time_in_system`. If 200 requests/s take 0.25 seconds,
about 50 requests are in flight on average. This is not a peak or tail bound.
If service time doubles and admission stays unchanged, memory/queue pressure grows.

A rate limiter caps arrivals per unit time. A concurrency limiter caps work
simultaneously in progress. A queue limit caps waiting work. Use all appropriate
limits, not a huge queue intended to hide sustained overload.

## 4. Lifecycle, supervision, timers, and discovery

### 4.1 Startup, stop, restart, and DeathWatch

`Behaviors.setup` runs when that behavior is created and provides actor context.
`Behaviors.stopped` ends the actor. A stopped child is not automatically recreated
just because someone retains its old reference.

`context.watch(child)` requests a termination signal. DeathWatch detects actor
termination, not "business work completed". Unexpected termination requires
handling the signal or using a domain-specific watched message where suitable.
Stopping a parent also stops its children.

On restart, the chosen initial behavior is re-created; ordinary in-memory
state is reset. Lifecycle signals such as `PreRestart` and `PostStop` allow
appropriate cleanup, but must not be treated as durable transactions. Resource
ownership should be explicit: do not close a shared database pool when one child
restarts if the whole application owns that pool.

### 4.2 Which failure strategy?

| Strategy | Meaning | Appropriate question |
|---|---|---|
| Stop | End the actor | Is the fault unrecoverable for this actor? |
| Resume | Skip failed message, retain behavior | Is current state still trustworthy? |
| Restart | Rebuild initial behavior | Can state be recreated/recovered? |
| Restart with backoff | Restart after increasing/randomized delays | Would immediate retry amplify an unavailable dependency? |

Invalid domain requests should normally receive a typed rejection rather than
throwing to trigger restart. Programming defects or unrecoverable internal faults
are different from an overdraft or invalid title. Do not use a catch-all resume
strategy that hides corrupt state.

### 4.3 Timers and stale completions

Typed timers send messages; they do not run business mutations on a scheduler
thread. A timer key lets the application replace/cancel a scheduled timer.
Processing timers track runtime time, not durable customer deadlines across crashes.

Consider an order with attempt 1 and attempt 2. A slow completion for attempt 1
may arrive after attempt 2 starts. Include an attempt/generation ID in completion
messages and ignore or explicitly log stale results. Otherwise a valid older
result can overwrite the newer state even though every handler is sequential.

For important deadlines, persist the deadline and determine on recovery whether
it has passed. Reschedule from that durable fact; do not assume an in-memory
timer survived termination.

### 4.4 Receptionist and routing

The typed Receptionist registers/discovers services using `ServiceKey`s.
Discovery can yield an empty or changing set; design an explicit "not available"
state. A pool router owns worker children; a group router routes to discovered
actors. Routing distributes work but does not automatically persist queued jobs,
avoid duplicate effects, or create strict ordering per customer.

For work with keyed state, random routing is usually wrong: updates for a given
entity must reach its owner or an explicitly coordinated shared store.

## 5. Event sourcing and recovery

### 5.1 Command versus event versus state

A **command** asks for a change: `Credit("op-1", 50, replyTo)`.
An **event** records an accepted fact: `Credited("op-1", 50)`.
**State** is the accumulated interpretation of accepted events:
balance plus recorded operation results.

Commands can be rejected and are not themselves necessarily journaled. Events
should represent already accepted domain facts. A reply address belongs in the
command, not the persisted event: the original request actor may not exist after
recovery.

```text
command + current state
          |
     validate/deduplicate
          |
   journal event and await acknowledgement
          |
     event handler -> new state
          |
       send reply

Recovery: empty state -> replay event 1 -> replay event 2 -> recovered state
```

### 5.2 Trace the executable persistent account

`PersistenceId.ofUniqueId(id)` identifies the journal stream. IDs must be stable
for the same logical entity and not collide between independent entities.
State starts as balance zero and an empty processed-operation map.

For an unseen valid ID, `Effect.persist(Credited(...))` writes one event.
`thenReply` runs after successful persistence and state application. A rejected
credit uses `Effect.reply` and writes no event.

The event handler adds the amount and records both its amount and the balance
immediately after that operation. It does not call a database, publish a payment,
or send an email. Recovery repeats event handling, so repeating external effects
there would be unsafe.

For repeated `op-1` with the same amount, return its original `Accepted(50)`.
If another operation has since raised balance to 70, retrying `op-1` still replies
50. The current balance is obtained separately via `Read`.
If `op-1` is reused with another amount, reject it as a conflict.

This is intentional API semantics, not an accidental stale result. It is
valuable when a caller wants to learn the outcome of a previously timed-out
operation without changing it.

### 5.3 What recovery is actually demonstrated?

The example configures `akka.persistence.journal.inmem`. The journal lives in
one actor system's memory. The test stops the account, waits for termination,
creates another account with the same persistence ID in the **same** system,
and verifies replay plus duplicate handling.

Ending the JVM or actor system destroys this journal. The demo does not test
machine failure, disk persistence, journal replication, or snapshot storage.
An external durable plugin and serialization strategy are prerequisites for
production recovery. This distinction is more important than the word
"persistent" in a class name.

The runtime can warn that no default snapshot store is configured. This lesson
deliberately does not take snapshots and replays its tiny journal from the
beginning. That warning is not proof the journal failed; an actual persist or
recovery error must still fail the operation/test.

### 5.4 Snapshots, retention, and schema changes

Replaying a million events may be slow. A snapshot stores an aggregate state
at a sequence number so recovery can load that state and replay only later
events. Snapshots are an optimization, not a replacement for designing event
history and retention policy.

Deleting old journal events without a recoverable compatible snapshot can
destroy recovery. Keeping snapshots forever can grow storage. Plan compatibility
and retention together, including disaster recovery copies.

Serialized events outlive the source code that created them. Use an explicit
versioned serialization format and migrations/adapters when shape or meaning
changes. Renaming a field, changing currency semantics, or removing an event
type can break historical recovery. Do not rely on general Java serialization
as a safe versioned public contract.

### 5.5 Event sourcing, durable state, and CQRS

Event sourcing stores history and derives state. Durable-state persistence stores
the latest state/revision rather than a full domain event history. Choose based
on audit, temporal reconstruction, operational complexity, and access patterns.
Neither automatically provides a transaction across multiple entities.

CQRS separates the write model from read/query models. An account actor owns
command validation; a projection updates an SQL/search read model from journaled
events. The projection may lag, so a read-after-write via the projection need
not immediately show the new balance.

A projection needs its own progress offset, replay strategy, and idempotent or
transactional output. Seeing an event twice must not insert the same credit twice
in a downstream ledger. A read model is disposable only if it can really be
reconstructed from retained source history.

## 6. Delivery, retries, and transactions

### 6.1 Delivery vocabulary

| Phrase | Actual claim |
|---|---|
| At-most-once | No retransmission guarantee; messages can be lost |
| At-least-once | Retry until acknowledged; duplicates are possible |
| Effectively once | Duplicates may arrive but deduplication makes one business effect |
| Exactly-once transaction | A specific atomicity/commit guarantee over specified participants |

No transport label removes the need to define the observation boundary.
"Exactly once" for an internal state update is not exactly once for an email
or arbitrary remote HTTP payment.

Akka reliable-delivery controllers are additional machinery, not used here.
They regulate delivery/acknowledgement and flow control, but business idempotency
and durable state still need design. Acknowledging receipt before durable
acceptance can lose work after a crash.

### 6.2 Deduplication has a storage cost

`PersistentAccount.processed` grows with every unique accepted operation. Keeping
all IDs makes the teaching contract simple but unbounded. Removing IDs after
24 hours means an older retry can act again. A retention bound is therefore a
business guarantee, not merely a performance setting.

Define operation ID ownership, tenant/account scope, retry horizon, payload hash,
conflict behavior, and retention. For money-like workflows use appropriate
precision/currency modeling and a real ledger; these whole-unit fixtures are
not a payment implementation.

### 6.3 Outbox and saga

Suppose a credit event commits and the process crashes before publishing it to
a broker. An ordinary "persist then publish" callback leaves a gap. An outbox
records intent durably with the business change, and a separate relay retries
publication. The receiver still deduplicates because publication can repeat.

A saga coordinates several local transactions and compensation. Reserving stock,
charging a card, and confirming an order is not a single actor transaction.
Compensation is a new business action (refund/release), not magical time travel.
Compensation can itself fail and needs retry/visibility.

Akka persistence cannot atomically commit an arbitrary event journal transaction
with an unrelated external database or payment API. Explicitly state each
transaction boundary rather than using "actor" as a substitute for it.

## 7. Streams beyond map and filter

### 7.1 Three type parameters to read

`Source[Out, Mat]` emits elements and has a materialized value.
`Flow[In, Out, Mat]` transforms elements and has a materialized value.
`Sink[In, Mat]` consumes elements and has a materialized value.

`Mat` might be `NotUsed`, `Future[Done]`, a queue handle, or a kill switch.
It is produced when a blueprint becomes a running graph. It is not another
element flowing through the graph.

`viaMat`, `toMat`, and `Keep.left/right/both` choose which lifecycle handles to
retain when composing graphs. Dropping the completion future can make it hard
to observe failures. Keep the handles needed to shut down and report completion.

### 7.2 GraphDSL walkthrough

`AdvancedStreams` builds:

```text
                     --> map(*2) --\
Source(1,2,3) -> Broadcast           Zip -> Sink.seq
                     --> map(*3) --/
```

`Broadcast(2)` sends each input to both outlets and normally needs demand from
both. `Zip` waits for one item on each inlet and produces a pair. Neither branch
filters or reorders, so corresponding elements remain paired.

If the left branch filters out `1`, its first output becomes `4` while the right
branch's first output is `3`: the pair would be `(4,3)`, not a joined result by
identity. `Zip` aligns position, not a business key. If one branch ends early,
the graph may complete without pairing the unmatched remaining values.

GraphDSL imports port syntax `~>` for connections. The sink is supplied to
`GraphDSL.createGraph(sink)` to retain its future as the runnable graph's materialized
value. `ClosedShape` means every inlet/outlet in the top-level graph is connected.

### 7.3 Common fan-in/fan-out choices

| Operator | Behavior | Main caution |
|---|---|---|
| Broadcast | Replicate to branches | Slow branch can backpressure the others |
| Balance | Distribute elements among available outputs | No per-key order guarantee across workers |
| Merge | Emit from inputs as available | Cross-input order is not deterministic |
| Concat | Consume inputs in sequence | Later input waits |
| Zip | Pair positions | Different filtering/order destroys identity alignment |
| Partition | Route using a function | Routing hot spots can limit throughput |

Graph wiring alone does not create a distributed cluster. Most stream execution
here is local. A separate deployment/transport layer is needed to cross machines.

### 7.4 Async boundaries and head-of-line blocking

Adjacent synchronous stages are often fused to reduce scheduling overhead.
`.async` introduces a boundary with buffering/scheduling. Add it when independent
work benefits from concurrency or isolation, not as a default after every `map`.

`mapAsync(4)` permits four futures in flight but emits in input order. If the
first future takes ten seconds and later ones take milliseconds, later results
wait behind it: head-of-line blocking. `mapAsyncUnordered(4)` avoids that ordered
wait but changes output order. Business rules decide whether that is acceptable.

A completed future with a huge payload still consumes memory. Concurrency four
does not imply tiny memory, and waiting work upstream can still accumulate.

### 7.5 Buffers, queues, throttling, and failure

A buffer absorbs short bursts; it cannot make a permanently slower sink faster.
Overflow strategies such as backpressure, drop-oldest, drop-newest, and fail
encode different data-loss policies. Never choose a drop strategy for required
financial events without an explicit business acceptance of loss.

A queue source's offer result distinguishes accepted/enqueued, dropped,
closed, and failed. "Enqueued" means accepted by that queue, not durably committed
by the eventual sink. Observe the offer result and the stream completion.

`throttle` shapes a rate; it does not increase capacity. A kill switch can stop
a running graph. Shutdown should stop admission, choose whether to drain pending
work, wait with a deadline, then close owned resources.

By default an exception fails the stream. A resume/restart decision for a stage
can drop the failing element; restarting a stage can reset that stage's state.
`recover` often emits a replacement final element and completes, whereas
`recoverWithRetries` can switch to another source. Neither automatically replays
already consumed source offsets. Restarting sources needs retry/backoff and
source-specific delivery reasoning.

## 8. Cluster and sharding architecture

### 8.1 Remoting is not the cluster

Remoting transports actor messages across runtimes and serializes payloads.
Cluster membership tracks which runtimes belong to a group, their status, and
reachability. Nodes communicate membership information through gossip and
failure detection. An unreachable node may be alive but disconnected.

Do not confuse unreachable with safely stopped. During a network partition,
both sides can think the other is unhealthy. A split-brain/downing strategy must
choose which side may continue. Bad decisions can produce competing owners.
The decision depends on topology and availability/consistency goals.

### 8.2 Cluster sharding

```text
client -> shard region/proxy -> shard coordinator allocation
                                      |
                           shard on a cluster node
                                      |
                            entity(account-123)
```

Sharding routes commands using a stable entity ID and distributes entity
ownership. The shard coordinator allocates/rebalances shards; entities can be
started on demand. Passivation stops idle entities to save resources, so their
important state must be recoverable.

An entity reference is a routing handle, not proof the entity is currently
running or that commands have been persisted. Sharding does not create a
transaction between `account-A` and `account-B`. Migration/rebalance introduces
lifecycle and latency considerations.

Entity ID to persistence ID mapping must stay stable. Concurrent independently
running actors writing the same journal stream without correct ownership/fencing
can corrupt business semantics. Multi-node tests and journal plugin guarantees
matter.

### 8.3 Distributed data and singleton

Distributed Data uses replicated data structures, often CRDTs, whose merge
properties support convergence. Eventual convergence does not satisfy every
invariant: a convergent counter is not by itself an overdraft prevention system.
Consistency/read-write settings alter availability and latency.

A cluster singleton runs one coordinated actor instance across a healthy cluster
under its configured membership decisions. Handover and failure detection take
time; it is not an infinitely available external lock or a substitute for
fencing a database writer during uncertain partitions.

These cluster features are not dependency-enabled by the local lessons.
Deploying them requires cluster modules, networking, serializers, discovery,
downing policy, and real multi-process validation.

## 9. HTTP, persistence storage, and system boundaries

### 9.1 HTTP to actor trace

A conceptual Akka HTTP service follows:

```text
HTTP bytes -> size/time limits -> JSON decode -> authenticate/authorize
          -> validate command -> ask entity -> domain reply -> HTTP response
```

An HTTP request does not belong inside a persisted event. Decode transport data
into a domain command, carry an operation ID, and map replies deliberately:
validation rejection to a client error, missing resource to not-found, overload
to an explicit retryable response, and internal unavailability to a service error.

Avoid exposing actor paths or arbitrary message construction to clients. A
timeout needs a response plus an explanation of retry semantics; it is not
automatically safe to retry an unkeyed mutation.

Akka HTTP dependencies/routes are not added in this module. The repository's
existing http4s backend is a different HTTP stack and remains unchanged.

### 9.2 Durable storage choices

A real persistence deployment chooses an appropriate journal and snapshot
plugin, external database availability/credentials, schema, partitioning,
retention, backups, and serializers. Validate acknowledgement semantics: when
the plugin says success, what replication/storage guarantee actually holds?

Permission to write journal data should be narrowly scoped. Keep credentials
outside source files, secure transport, and avoid logging customer payloads.
Back up and restore-test the storage, not just the application artifact.

### 9.3 Graceful shutdown and rolling changes

Coordinate inbound admission, stream completion, actor work, cluster departure,
and external client cleanup. An arbitrary `Thread.sleep(5 seconds)` is not a
proof all commands committed.

Rolling deployment can run old and new nodes simultaneously. Messages and
events must be compatible across those versions. Make schema transitions in
steps: readers understand both forms before writers emit only the new form.
Rollback must also be able to interpret events already written by the new code.

## 10. Testing and operational reasoning

### 10.1 Layered tests

Test pure event handlers/invariants independently, actor protocols with probes,
stream transformations with finite fixtures, journal recovery with the intended
plugin, and system behavior under real process failures.

`ActorTestKit.stop` waits for stop before reusing a persistence ID; merely sending
a stop request and immediately spawning a replacement can overlap owners.
Probes observe messages rather than guessed timing. Avoid sleeps as synchronization.

Useful cases: no reply, delayed reply, duplicate command, same ID/different payload,
overflow, timeout-after-commit, dependency outage, restart, and recovered state.
Test async saturation with a future kept pending and more than 16 submitted reads;
distinguish explicit startup-buffer rejection from mailbox overload.

### 10.2 Metrics to connect to architecture

Measure request rate/latency, actor mailbox waiting, dispatcher utilization,
ask timeouts, dead letters, restart frequency, journal write latency, replay
duration, stream throughput/backpressure, and downstream error rate.

| Symptom | Plausible cause | First discriminating check |
|---|---|---|
| Unrelated actors become slow | Shared dispatcher blocked | Thread dumps and blocking client use |
| Many ask timeouts with successful writes | Reply path/queue delay | Trace operation IDs through persist and reply |
| Restart loop | Dependency unavailable or repeatable defect | First cause, backoff, recovery logs |
| Memory rises despite stable actor count | Mailboxes/stashes/payloads grow | Admission rates and queue/state sizes |
| Stream throughput collapses | Slow downstream or ordered async wait | Per-stage timing and in-flight work |
| Recovery fails after deploy | Incompatible events/snapshots | Serializer/schema and historical fixtures |

Dead letters can occur during normal shutdown, but a sustained increase during
normal traffic is evidence worth tracing. Do not classify every log as harmless.
Logging full sensitive payloads is not necessary for correlation.

### 10.3 Capacity and operational acceptance

Define expected rate, p95/p99 latency, overload behavior, maximum recovery time,
acceptable loss/duplication, persistence durability, and shutdown deadline.
Load-test the real dispatcher/journal/sink combination. A successful unit test
on a laptop is not evidence for those production thresholds.

## 11. Worked design and exercises with answers

### Order reservation design

An order entity accepts `Reserve(orderId, operationId, items)`. It validates
inventory/prices according to the system's chosen ownership model and persists
`ReservationRequested`. A durable workflow contacts inventory/payment services
using stable operation IDs. Their results become commands, producing accepted
events. A projection exposes queryable order status.

The hard questions are not syntax: who owns inventory invariants, when the
customer receives acknowledgement, what survives a crash, and how a timed-out
external operation is reconciled. An actor by itself cannot answer them.

### Exercise 1: duplicate credit after later credits

Given events `a:+50`, `b:+20`, retry `a:+50`. Expected reply: `Accepted(50)`;
current balance: 70. Why? The operation lookup stores original outcome. Returning
70 would conflate "current account state" with "outcome of operation a".
Retry `a:+51` must reject without writing a new event.

### Exercise 2: crash between persistence and reply

Expected reasoning: replay recovers the accepted event; the original reply
future can still time out. Retrying the same operation ID returns the recorded
result. This reasoning requires a durable journal for an actual process crash;
the in-memory demo cannot supply that guarantee.

### Exercise 3: slow startup and 17 requests

Keep `load` pending. The waiting actor can stash 16 reads and explicitly reject
the seventeenth once it processes it. Complete the future and verify the stashed
reads receive the same value. This bounds the stash, not total mailbox memory.
The full-buffer result must not be mistaken for successful initialization.

### Exercise 4: filter one graph branch

Filter the `*2` branch to remove its first element. Predict positional zip
misalignment and possible loss of unmatched tail elements. Correct solution for
identity-sensitive pairing: carry IDs and design a keyed correlation stage with
bounded waiting/timeouts, not assume zip joins by identity.

### Exercise 5: bounded deduplication retention

Specify a 24-hour retry guarantee. Store acceptance time and payload identity
alongside the outcome. Define explicit behavior for a request older than the
guarantee: reject expired operations or consult an authoritative ledger, rather
than silently accepting a potentially duplicate mutation.

### Final mastery check

You should be able to draw protocol and lifecycle transitions, locate every
side effect/thread boundary, explain an ask timeout after a committed write,
reconstruct state from events, distinguish stream elements from materialized
values, and name the exact durable boundary of your design. If a claim says
"exactly once", state who observes what, where it commits, and how it recovers.
