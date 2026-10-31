# Part 7 - Akka: message-driven applications

Akka is a toolkit for concurrent and distributed applications, not a data warehouse.
This module teaches **Typed actors**, **supervision**, and **Akka Streams**.
Read Parts 1-5 first, especially case classes, pattern matching, immutable state,
and futures. Use JDK 17 and run all commands from the repository root.

## Advanced learning path

Continue with the [detailed advanced handbook](ADVANCED.md). It explains actor
asynchrony, request ordering, lifecycle, event sourcing, delivery/idempotency,
stream graphs, cluster/sharding architecture, HTTP boundaries, testing, capacity
planning, deployment, and failure diagnosis. Runnable advanced lessons:

```bash
sbt "akka/runMain lectures.part7akka.AsyncActor"
sbt "akka/runMain lectures.part7akka.PersistentAccount"
sbt "akka/runMain lectures.part7akka.AdvancedStreams"
```

The persistent lesson uses an in-memory journal: its tests recreate an actor
within the same runtime, not recover from a process crash.

## Run and read

```bash
sbt "akka/runMain lectures.part7akka.TypedActors"
sbt "akka/runMain lectures.part7akka.Supervision"
sbt "akka/runMain lectures.part7akka.Streams"
sbt "akka/test"
```

| File in `src/main/scala/lectures/part7akka/` | Lesson | Expected result |
|---|---|---|
| `TypedActors.scala` | Typed protocol, actor reference, ask/reply, immutable behavior state | `Balance(70)` |
| `Supervision.scala` | Guardian/child hierarchy and restarting a failed actor | Intentional error log, then `1` (not `6`) |
| `Streams.scala` | Source/Flow/Sink, materialization, async work, backpressure | `120` |

The programs terminate their actor systems. `Await` is used only at CLI/test
boundaries; do not block inside an actor or stream stage. The error printed by
the supervision demo is intentional and is not silently swallowed.

## Actor architecture

```text
Application
    |
ActorSystem (runtime, lifecycle, scheduler, dispatchers)
    |
Guardian actor
    +--> child actor reference --> mailbox --> behavior --> reply actor
                                             |
                                      next behavior/state
```

An **ActorRef[Command]** is an address accepting a typed message protocol, not a
direct reference to a mutable object. `ref ! message` enqueues a message. A
dispatcher schedules mailbox processing on shared threads; an actor does not
necessarily own a thread. One actor handles its messages sequentially, but many
actors can execute concurrently.

State belongs to the actor's behavior. In `account(balance)`, a successful
operation returns `account(newBalance)`. No other actor can read that balance
directly. `Behaviors.same` keeps the existing behavior after a rejected operation.
This isolates state; it does not make several actors' updates a transaction.

Messages from one sender to one recipient preserve order; there is no global
ordering across senders. Normal actor messaging is at-most-once, not guaranteed
delivery. The default mailbox does not provide stream-style backpressure.
Avoid flooding it with unbounded work.

### Trace a withdrawal

1. `system.ask` creates a temporary reply actor and sends `Withdraw(30, replyTo)`.
2. The account mailbox delivers it to the behavior.
3. Pattern matching checks the amount and current balance.
4. The behavior sends `Balance(70)` and returns a behavior holding `70`.
5. The reply completes the ask's `Future`. A timeout means no timely reply, not
   proof that an operation never happened; blindly retrying can duplicate an update.

The example deliberately rejects non-positive amounts, overdrafts, and deposits
that overflow an `Int`. It models whole units for learning, not production money.

## Supervision and hierarchy

The guardian in `Supervision.scala` creates a counter child. After the first
reply (`5`), the guardian sends an intentional failure and then `Add(1)` in order.
The restart strategy replaces the failed behavior with `counter(0)`, so the next
reply is `1`. The failed message is discarded, not replayed.

Restart, resume, and stop have different meanings: restart resets behavior,
resume retains current behavior, and stop ends the actor. Restarting is not a
substitute for persistence or idempotent commands. An unexpected failed child
without a suitable strategy can stop rather than recover.

## Streams architecture

```text
Source(1..6) --> Flow(filter even, mapAsync x10) --> Sink(sum)
     ^                         ^                      |
     +--------- downstream demand / backpressure -----+

Materializer: blueprint --> running graph --> Future[Int]
```

`Source`, `Flow`, and `Sink` describe a graph. `runWith` **materializes** it using
the actor system's stream runtime. The materialized value here is a future for
the final sum, not the elements themselves.

The filter keeps `2, 4, 6`; `mapAsync(2)` produces `20, 40, 60` with at most two
futures in flight and preserves output order; the sink sums them to `120`.
Adjacent synchronous stages can be fused into one execution region. Add `.async`
to introduce an asynchronous boundary when appropriate, not automatically
between every operation. Backpressure regulates demand and buffering; it does
not persist records or make delivery exactly once.

## Practice and architecture checkpoints

1. Add a `Transfer` workflow between two accounts. Explain why two asks cannot
   make it atomic and how operation IDs could make retries idempotent.
2. Add a stream that fails on one input. Compare failing the graph, resuming a
   stage, and recovering with another source; state what data each approach loses.
3. Replace `mapAsync` with `mapAsyncUnordered` and add differing work delays.
   Explain when changed ordering is acceptable.
4. Explain mailbox versus thread, typed protocol versus payload, and restart
   versus recovery from persisted events.

## Beyond this module

The advanced examples add event sourcing with a deliberately in-memory journal.
HTTP routing (Akka HTTP), external durable journals, cluster membership/sharding,
remoting, production serialization, and observability are explained in the
handbook but not enabled as deployed systems. A cluster needs explicit
configuration and deployment; a local actor system is not a distributed deployment.

**Version/licensing:** this module deliberately uses the historical Apache-2.0
Akka 2.6.21 artifacts available from Maven Central for reproducible actor lessons.
That line is old and is not a recommendation for new production systems. Newer
Akka lines have different license/distribution requirements: review their terms,
support status, and dependency advisories before choosing a production version.
Apache Pekko is a separate Apache-licensed fork, not an Akka package alias.

References: [Akka 2.6 Typed guide](https://doc.akka.io/docs/akka/2.6/typed/guide/index.html),
[supervision](https://doc.akka.io/docs/akka/2.6/typed/fault-tolerance.html),
[Streams](https://doc.akka.io/docs/akka/2.6/stream/index.html),
[current Akka licensing](https://akka.io/license).
