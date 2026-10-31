# Advanced Flink handbook

This original handbook targets **Flink 1.20.2's Java API from Scala 2.13 on
JDK 17**. Start with [the introduction](README.md). It explains state, time,
recovery, dataflow, operations, and worked exercises in one local learning path.

The controlled event-time lesson is runnable and asserts precise results.
Kafka, external sinks, production source implementations, Table/SQL workloads,
remote deployments, and savepoint migrations below are explained designs,
not dependencies or infrastructure silently installed by this guide.
No finite handbook can document every connector/release/environment.

## Contents

1. [Run the advanced experiment](#1-run-the-advanced-experiment)
2. [Execution and partitioning mechanics](#2-execution-and-partitioning-mechanics)
3. [State, serialization, and ownership](#3-state-serialization-and-ownership)
4. [Event time, timers, windows, and late data](#4-event-time-timers-windows-and-late-data)
5. [Checkpoint and recovery internals](#5-checkpoint-and-recovery-internals)
6. [Savepoints, upgrades, and rescaling](#6-savepoints-upgrades-and-rescaling)
7. [Sources, sinks, and end-to-end guarantees](#7-sources-sinks-and-end-to-end-guarantees)
8. [Advanced processing patterns](#8-advanced-processing-patterns)
9. [Table API, SQL, and changelogs](#9-table-api-sql-and-changelogs)
10. [Operations, testing, and system design](#10-operations-testing-and-system-design)
11. [Exercises with worked answers](#11-exercises-with-worked-answers)

## 1. Run the advanced experiment

From the repository root:

```bash
sbt "flink/runMain lectures.part9flink.AdvancedEventTime"
sbt "flink/test"
```

Read `src/main/scala/lectures/part9flink/AdvancedEventTime.scala`.
Its event type is `(word, eventTimestampMilliseconds, quantity)`.
It has a controlled timeline, an incremental sum/window branch, a late-event
side output, and an independent keyed timer branch.

Expected business lines (the program sorts results):

```text
rejected-late:3000
rejected-late:4000
side-output:4000:8
timer:scala:9999:3
window:scala:0:3
window:scala:0:7
```

The window branch accepts one late-but-still-retained record; the timer branch
intentionally rejects any record at/before its current watermark. Different
policies are deliberate, not inconsistent framework behavior.

The source uses the legacy `SourceFunction` only to emit exact watermarks for
this Flink 1.20 teaching fixture. That API is deprecated; new production
connectors should use the newer `Source` API. No sleeps or wall-clock races
determine the assertions. This finite source is not a checkpointed production
source or a claim of fault-tolerant replay.

## 2. Execution and partitioning mechanics

### 2.1 Graphs and runtime roles

Your program constructs transformations in a `StreamExecutionEnvironment`.
Flink converts that into a job/dataflow graph, schedules operator subtasks,
and transfers data between them. `execute` / `executeAndCollect` is the
submission boundary; adding `map` does not itself execute a job.

```text
client code -> logical transformations -> submitted job graph
                                         |
                         JobManager coordination
                                         |
                  TaskManagers / slots / operator subtasks
                     source -> keyed work -> sink
```

The JobManager side includes scheduling/job coordination responsibilities.
TaskManagers execute tasks and hold runtime state/resources. Slots allocate
resources to tasks and can participate in slot sharing; a slot is not simply
one Java thread.

### 2.2 Chaining and the mailbox execution model

Compatible operators can be chained into a task to avoid unnecessary
serialization/network handoffs. A task's execution coordinates record processing
and other work through its runtime/mailbox model. User function callbacks should
not mutate runtime-owned state from arbitrary application threads.

Creating `Future`s inside a process function and updating `ValueState` in their
callbacks is unsafe. Use Flink's supported async I/O APIs or route work through
the framework's expected processing model.

### 2.3 Partitioning choices

| Operation | Distribution | Use/caution |
|---|---|---|
| `keyBy` | Same key to same keyed subtask | Required for keyed state; hot keys remain hot |
| `rebalance` | Distribute records across downstream partitions | Spreads load but destroys keyed ownership |
| `rescale` | Redistribute across a subset of downstream subtasks | Topology/locality-oriented redistribution |
| `broadcast` | Send records to all downstream instances | Replicates traffic and often state |
| `shuffle` | Random partition choice | Does not preserve per-key affinity |
| `forward` | Keep corresponding partition path | Requires compatible parallelism/topology |

Keying does not sort records by event time. It groups ownership; out-of-order
arrival remains possible. A key's records normally execute sequentially within
its keyed subtask, but this does not create a transaction with an external API.

### 2.4 Parallelism versus maximum parallelism

Parallelism is the number of current subtasks. Keyed state is organized into
key groups, with maximum parallelism governing the key-group space used for
redistribution. Savepoint restore can redistribute key groups across a compatible
new parallelism.

Maximum parallelism affects future rescaling/compatibility. Do not treat it as
"set it to whatever later". A large hot key still belongs to one logical keyed
owner; adding workers alone does not split that key's sequential computation.

### 2.5 Bounded versus streaming execution

The data can be bounded (finite) or unbounded (ongoing), and runtime mode controls
execution choices. The introductory batch word count emits final bounded
reductions. Streaming reduction emits evolving results.

A bounded source in streaming mode can emit a terminal watermark, closing
pending event-time work. An unbounded idle source might never advance its
watermark without additional strategy/other events. Do not extrapolate the
finite demo's automatic finalization to an infinite stream.

## 3. State, serialization, and ownership

### 3.1 Keyed versus operator state

**Keyed state** is scoped by the currently processed key after `keyBy`.
`ValueState` stores one logical value for that key; `MapState` stores a map;
`ListState` stores a list; reducing/aggregating state performs incremental updates.

**Operator state** belongs to an operator instance rather than a user key and
needs an explicit redistribution scheme for rescaling. Source offsets and
connector metadata often use operator/coordinator state concepts.

Broadcast state distributes a replicated rules/configuration view to parallel
instances. It is not the same as using a global mutable Scala map that happens
to exist in each JVM.

### 3.2 State handle versus state value

`getRuntimeContext.getState(descriptor)` returns a handle. On each keyed callback,
the handle accesses that callback's current key. The `ValueState` field itself
does not contain one counter shared across all keys.

Reading absent state can return null for nullable reference types. The examples
intentionally translate "no value yet" to zero. That is initialization semantics,
not a fallback after a failed storage operation.

Updating state and calling `out.collect` are separate actions. Neither means
an arbitrary downstream sink transaction has committed.

### 3.3 Why explicit Java tuples/types in Scala?

The fixture uses `Tuple3[String, java.lang.Long, java.lang.Integer]` plus
`Types.TUPLE(..., Types.STRING, Types.LONG, Types.INT)`. This gives Flink explicit
information about the generic fields despite Scala/JVM type erasure.

Named function classes have clearly defined serialized behavior and avoid
accidental closure capture. Do not capture the driver/client environment,
open sockets, or nonserializable resources in a function shipped to TaskManagers.
Initialize owned runtime resources in lifecycle methods and close them properly.

Schema/serializer compatibility matters after code changes: saved state is bytes
written by an earlier serializer. Changing generic types or record shape needs
a compatible restore path or a deliberate migration.

### 3.4 State backend versus checkpoint storage

A state backend controls how active state is maintained and snapshots are
produced. Checkpoint storage controls where completed recoverable snapshots
reside. They are related but distinct choices.

Heap-oriented state access can be fast but consumes JVM memory/GC. Embedded
RocksDB maintains serialized state with local/native resource needs and can
support incremental snapshots; it is not a remote durable database simply
because it uses local disk.

Local disks can disappear with a worker. Durable recovery requires compatible
checkpoint storage reachable after failure. Backends also need tuning based
on state size, access patterns, restore time, and memory limits.

### 3.5 TTL is a semantic retention choice

State TTL determines expiry/visibility/cleanup for supported state descriptors.
In Flink 1.20, supported TTL is processing-time based, not a general event-time
window eviction replacement. Cleanup may be lazy/background/backend-dependent,
so expiry policy does not always imply immediate physical reclamation.

If a deduplication ID expires after one hour, a duplicate after two hours may
act again. Define that retry horizon in the business contract. TTL settings
cannot silently downgrade "never duplicate a payment" into "usually don't
duplicate recently observed payments".

## 4. Event time, timers, windows, and late data

### 4.1 Timestamp, watermark, trigger, and allowed lateness

An event timestamp describes when the event happened.
A watermark is a monotonically advancing event-time progress signal.
A trigger decides when to emit a window result.
Allowed lateness controls retention/revisions after the initial event-time firing.

These are separate concepts. Watermark delay is not trigger frequency, and
allowed lateness is not the same as the watermark's out-of-order delay.

A periodic bounded-out-of-orderness strategy estimates progress from the largest
observed timestamp minus delay, with boundary handling. It does not physically
sort the stream or promise no older event can ever arrive.

### 4.2 Timer callbacks

`registerEventTimeTimer(t)` schedules a callback when the operator's event-time
progress reaches/passes the timer timestamp. No wall-clock sleep is required.
`registerProcessingTimeTimer(t)` instead follows runtime time.

For a given key and timestamp, event-time timer registrations are deduplicated.
Different keys or timestamps have separate timers. Timers can be checkpointed
with keyed state under supported execution, but an uncheckpointed local demo
does not prove timer recovery across crashes.

`onTimer` can read/update keyed state under the timer's key. A timer firing is
not a durable external notification unless the output/side effect also has an
appropriate commit/retry protocol.

### 4.3 The exact controlled timeline

The ten-second tumbling window is `[0,10000)`; its maximum included timestamp
is 9999. Allowed lateness is 2000 milliseconds.

| Step | Input/progress | Window branch | Timer branch |
|---|---|---|---|
| 1 | `(scala,1000,1)` | Sum 1 | Sum 1; register 9999 |
| 2 | `(scala,2000,2)` | Sum 3 | Sum 3; same timer registration |
| 3 | Watermark 9999 | Fire `window:scala:0:3` | Fire one `timer:scala:9999:3`; clear state |
| 4 | `(scala,3000,4)` | Late, retained; emit revised total 7 | Reject timestamp at/before current watermark |
| 5 | Watermark 11999 | Cleanup time reached | No pending timer |
| 6 | `(scala,4000,8)` | Too late; side output | Reject as late |
| 7 | Watermark `Long.MaxValue` | Finish remaining event-time work | Finish remaining timers |

Window cleanup uses the window's maximum timestamp plus allowed lateness:
`9999 + 2000 = 11999`. This exact boundary is why the fixture emits 11999,
not a guessed wall-clock delay.

The revised `window:scala:0:7` is the full updated total, not an additional
quantity of 7. A sink summing both outputs as independent facts would compute
10 incorrectly. Treat window identity `(key, start/end)` as an upsert key or
design an explicitly additive/delta output protocol.

### 4.4 Incremental aggregation versus buffered windows

The introductory `ProcessWindowFunction` receives all retained window events,
so state can grow proportional to records in the window.

`AdvancedEventTime.Sum` uses an accumulator `Long`. `createAccumulator` starts
at zero, `add` combines one event, `getResult` exposes the current sum, and
`merge` combines accumulators where required (for example mergeable windows).
`DescribeWindow` then adds key/window metadata to the aggregate output.

For sum, this keeps accumulator state small instead of storing every input
event. It still maintains state per active key/window and needs lateness cleanup.
The fixture quantities are small; a real numeric domain must define overflow
and precision instead of assuming any `Long` sum is always safe.

### 4.5 Window families

Tumbling windows do not overlap: each event belongs to one interval.
Sliding windows can overlap, so one event contributes to several windows and
increases active state/output. Session windows group events separated by gaps
and can merge sessions, requiring compatible merging aggregation/trigger behavior.

Count windows group by record counts rather than event-time intervals.
Global windows generally need a custom trigger/eviction/retention strategy.
Neither is automatically cleaned up by saying "we use watermarks".

### 4.6 Side outputs and data policy

`sideOutputLateData(late)` routes records whose windows can no longer accept
them. It is an explicit channel, not automatic permanent storage.
The program maps that channel to an inspectable string. A production design
could quarantine records for reconciliation with their reason and identity.

The timer branch emits `rejected-late` for timestamp `<= currentWatermark`.
That is its custom policy, separate from window allowed lateness. Its timer
timestamp is deliberately fixed at 9999 for this single-window fixture.
For general multi-window processing, derive/store timer deadlines per window
or use the window API; do not copy the hardcoded deadline for arbitrary events.

### 4.7 Multiple inputs, idleness, and alignment

An operator's watermark is constrained by the minimum progress of active inputs.
A slow partition can hold back cleanup and timers even if another is far ahead.
Marking truly idle inputs can avoid unnecessary stalls, but prematurely treating
a slow partition as idle may classify its returning older events as late.

Watermark alignment can constrain faster sources to limit how far they outrun
slower sources and thereby reduce buffering/state. It is not the same as
checkpoint barrier alignment. Support/settings depend on source/runtime integration.

## 5. Checkpoint and recovery internals

### 5.1 A checkpoint is a consistent cut

Source progress and operator state must agree. If the counter contains events
through offset 100 but a restored source resumes at 90, duplicates can occur.
If state contains only through 90 but the source resumes at 100, events are lost.
A consistent snapshot coordinates those boundaries.

```text
source positions + checkpoint barriers
                      |
         operators snapshot state consistently
                      |
            sinks participate where supported
                      |
       coordinator records completed checkpoint
```

Enabling checkpointing schedules attempts; it does not mean an attempt has
completed or is stored durably. Checkpoint success must be observed and storage
must survive the relevant failure.

### 5.2 Barrier alignment

For an aligned checkpoint, a multi-input operator waits for barriers on the
relevant input channels and prevents subsequent records on already-barriered
channels from contaminating the pre-barrier snapshot. Uneven input/backpressure
can increase alignment time.

Unaligned checkpoints capture in-flight channel data as well, allowing snapshots
to progress under certain backpressure conditions with different storage/restore
costs. They are not a cure for an indefinitely overloaded sink. Understand
compatibility and overhead before enabling them.

Watermarks signal event-time progress; checkpoint barriers coordinate snapshots.
A watermark does not commit a checkpoint, and a checkpoint does not advance
event time automatically.

### 5.3 Restore trace

After failure, the configured restart strategy allocates execution, restores
the chosen completed checkpoint, restores operator/keyed state and source
positions, then resumes processing. Work after the checkpoint may be replayed.

If external side effects happened after that checkpoint without transactional
or idempotent coordination, replay can repeat them. Exactly-once managed state
is not the same as exactly-once external behavior.

The active state backend, durable checkpoint storage, source retention, sink
commit protocol, serializers, and stable operator identities all participate.
Failure of one assumption changes the result.

### 5.4 Interval, timeout, concurrency, and backoff

Short checkpoint intervals increase snapshot overhead; long intervals can
increase replay and recovery work. Timeout bounds an attempt; tolerable failed
attempts and restart strategy determine escalation. A minimum pause can prevent
continuous checkpoint pressure.

Example reasoning: at 10,000 events/s with a 60-second interval, roughly up to
600,000 events between nominal checkpoints may need replay, plus delay from
failed/slow checkpoints. This is a rough workload estimate, not a hard recovery
bound. Measure restore throughput and input retention.

Repeated failure with immediate restart can hammer storage/dependencies.
Use explicit backoff/retry policy and alert on the root cause, not only count
restarts until the system appears green.

### 5.5 What is not proven locally

`KeyedState` enables checkpointing but may finish before a completed checkpoint.
`AdvancedEventTime` does not enable a durable recovery setup and its source
does not implement a production replay protocol.

To prove recovery, use a replayable source, persistent checkpoint storage,
real sink coordination, failure injection after a confirmed checkpoint,
and assertions on final external output/state. A directory or configuration
line alone is not the evidence.

## 6. Savepoints, upgrades, and rescaling

### 6.1 Checkpoint versus savepoint

Checkpoints primarily support automatic recovery and framework-managed lifecycle.
Savepoints are explicitly triggered snapshots used for controlled stop/upgrade/
rescale workflows, with retention/ownership that the operator manages.

A savepoint is not a universal portable database export. Format, serializers,
operator mapping, backend/features, and version compatibility constrain restore.
Stopping with a savepoint and draining semantics can affect event-time timers
and output; choose whether final event-time completion is desired.

### 6.2 Stable operator UIDs

The advanced graph assigns UIDs such as `controlled-events`, `window-sums`,
and `timer-sums`. State restore maps saved operators to current operators.
Automatically generated IDs may change after topology edits, so stable
explicit UIDs are important for stateful production pipelines.

State descriptors (`"sum"`, `"total"`) also identify state within an operator.
Renaming them or changing their serializer can create compatibility problems.
Do not treat a UID rename as cosmetic for a running stateful job.

### 6.3 Upgrade procedure as reasoning, not copy-paste

Record current job version, configuration, state schema, topology, and source/
sink versions. Obtain a completed recoverable snapshot according to the planned
stop semantics. Restore the new code in a compatible environment and validate
state/output continuity. Keep a rollback strategy capable of reading any state
written by the new version.

Allowing non-restored state can intentionally discard removed operator state;
it should not be a blanket flag to ignore unexpected restore errors.
Different framework versions/backends can require dedicated migration paths.

### 6.4 Rescale

Redistribute keyed-state key groups across the new parallelism under compatible
maximum-parallelism settings. Operator list/broadcast/source state has its own
redistribution behavior.

More parallelism can increase memory and scheduling capacity, but also changes
network and sink concurrency. It cannot split one key's exact sequential
state machine without redesign. Test restored results, not only job submission.

## 7. Sources, sinks, and end-to-end guarantees

### 7.1 Modern Source API

The newer Source API separates split enumeration/coordinator work from reader
work. A **split** is a unit such as a file fragment or broker partition.
The enumerator discovers/assigns splits; readers consume them and record progress
for recovery. Boundedness is part of the source contract.

This separation supports scalable discovery/reassignment and coordinated state.
Correct checkpointing/offset restore is a connector implementation responsibility.
Replacing a fixture with a broker source is not just replacing one constructor:
define starting offsets, partitions, timestamps/watermarks, retention, authentication,
and recovery behavior.

### 7.2 Kafka concepts without a hidden Kafka setup

A Kafka topic has partitions with ordered offsets per partition. There is no
global topic order across partitions. Flink source readers consume assigned
partitions; checkpointed source progress supports coordinated recovery.

Consumer group commits, broker retention, and Flink checkpoint offsets serve
related but different roles. A consumer group name alone does not ensure correct
exactly-once state/output. Keying records by business ID downstream may reshuffle
away from broker partition assignment.

The module has no Kafka connector/broker. A deployment must add a connector
compatible with the chosen Flink version, use externalized credentials, and
test real offset/recovery behavior.

### 7.3 Transactional and idempotent sinks

A sink writer can buffer/write records; committable/commit components can
coordinate finalization with checkpoints where the connector supports it.
A transaction prepared for checkpoint N is not the same as an arbitrary insert
already visible in another system.

For exactly-once end-to-end semantics, require replayable source, consistent
recoverable processing state, and a sink protocol that prevents duplicate/lost
commits across retries. Transaction timeouts must accommodate checkpoint
duration, failure downtime, and recovery.

An idempotent upsert can be effectively once for an identified record/version,
but not for arbitrary increments. `balance = balance + amount` retried twice
is different from setting a versioned total under a stable ID.

Email and external HTTP payment requests generally need outbox/idempotency/
reconciliation design rather than claiming checkpointing makes them exactly once.

### 7.4 Delivery vocabulary

At-most-once accepts loss/no replay. At-least-once can replay duplicates.
Exactly-once describes a specified state/commit observation under assumptions.
State the scope: managed operator state, broker output transaction, file commit,
or downstream business ledger. Avoid a blanket label for the whole company system.

## 8. Advanced processing patterns

### 8.1 Event-time deduplication

Key by event ID and store whether it has been seen. Emit only first acceptance.
Use defined timers/retention or TTL based on the required business horizon.
If cleanup removes an ID before an allowed old replay, that event can appear
again. Retention and source replay horizon must agree.

An event ID plus payload conflict check is stronger than ID existence alone:
the same ID with changed content should not silently be treated as a valid retry.

### 8.2 Broadcast rules

A low-volume rule stream can broadcast updates to all parallel event operators.
A keyed/broadcast process function reads those rules while processing events.
Every instance needs deterministic update semantics because rule/event
interleavings can differ across partitions.

For "evaluate using rules effective at event time", a single latest-rule map is
not enough. Store/version historical rules and define late-event lookup semantics.
Broadcasting a huge rules dataset multiplies state cost across workers.

### 8.3 Async I/O

Async I/O overlaps network requests without blocking the task thread. Capacity
limits in-flight requests; timeouts and ordered/unordered result modes define
observable behavior. An ordered mode can suffer head-of-line blocking.

Retries need external API idempotency and clear failure output. A checkpointed
pending request does not make the external call transactionally exactly once.
A replayed lookup can return different data if the external database changes;
define whether that is acceptable or version the lookup.

### 8.4 Joins and CEP

An interval join matches keyed events within timestamp bounds and maintains
bounded-by-time state under watermark progress. Specify equality/time bounds,
late-data policy, and multiplicity: several matches can produce several outputs.

Temporal/reference joins need a definition of which dimension version applies.
A latest-value enrichment is not equivalent to event-time historical enrichment.

CEP (Complex Event Processing) matches ordered event patterns, such as login
failures followed by success within a period. Pattern skip/contiguity/timeout
settings control match multiplicity and state. Watermarks/late data affect
event-time pattern completion. CEP libraries are not installed in this module.

### 8.5 Backpressure and hot keys

A slow downstream task limits upstream ability to emit. Backpressure is a
flow-control signal, not an error recovery guarantee. Buffers absorb bursts
but cannot fix sustained input above processing capacity.

Measure busy, idle, and backpressured time. A hot key, slow sink, high-latency
external lookup, or expensive state serialization can each be the bottleneck.
Adding workers helps only when the constrained work can be distributed safely.

For combinable totals, partial aggregation may reduce load. For one account's
strict transition sequence, arbitrary splitting changes ownership/invariants.

## 9. Table API, SQL, and changelogs

### 9.1 Table versus stream representation

Table API/SQL describes relational operations. Flink translates supported queries
into streaming/batch execution. A dynamic table evolves as events arrive; its
updates can be represented as a changelog stream.

The same SQL aggregate over a live stream is not necessarily append-only:
new input may revise an existing group's count.

### 9.2 Row kinds and sink compatibility

Changelog rows can indicate insert, update-before, update-after, or delete.
An append-only sink cannot necessarily represent an updating aggregate.
An upsert sink needs a meaningful primary key and overwrite/version semantics.

Example: key `scala` count changes from 3 to 7. Writing both as independent
append facts does not create a final count of 7 automatically. The consuming
system must understand updates/retractions or receive final append-only windows.

Planner-declared primary keys can be assumptions rather than row-by-row
enforced uniqueness. Validate actual source uniqueness and sink conflict behavior.

### 9.3 Time attributes and watermark declarations

A table schema can declare an event-time column and watermark expression.
Window table-valued functions group records into tumbling/sliding/session
windows under supported semantics. Processing-time and event-time columns
lead to different reproducibility/late-data behavior.

Temporal joins, catalogs, connector options, and changelog conversion need
compatible planner/connectors. `flink-table`/planner dependencies are not
added by the DataStream lessons; do not paste SQL snippets assuming the module
already includes that runtime.

## 10. Operations, testing, and system design

### 10.1 Deployment modes and classpaths

A local MiniCluster is for development. Session clusters share runtime resources
across submitted jobs. Application/per-job deployment models provide different
ownership/isolation depending on the platform and version.

Package application code and dependencies compatibly with cluster Flink,
connector, Scala, and JDK versions. Shipping a second Flink runtime inside the
job artifact can cause classloading conflicts. UDFs/serializers also need
consistent available classes across workers and restores.

Conceptual CLI shape (not executed, requires a real distribution/cluster):

```text
flink run -c your.package.Main <job-jar>
flink savepoint <job-id> <durable-target>
flink run -s <savepoint-path> -c your.package.Main <new-job-jar>
```

The local sbt examples are not those packaged production jobs.

### 10.2 Memory and resources

TaskManager process memory includes JVM heap, managed memory, network buffers,
and nonheap/native overhead. RocksDB/native clients may consume resources outside
the Java heap. Slot count and operator parallelism determine concurrent workloads,
but do not reserve an independent complete JVM for each slot.

Size based on state, record sizes, serialization expansion, in-flight network/
async work, and checkpoint overhead. A successful 1 GB local fixture is not a
recommended production memory setting.

### 10.3 Diagnosis table

| Symptom | Possible cause | Evidence to inspect |
|---|---|---|
| Event-time windows never finish | Stalled/idle watermark input | Per-source/operator watermark progress |
| Checkpoints time out | Alignment/storage/snapshot delay | Checkpoint duration breakdowns and failures |
| One operator backpressured | Slow downstream or hot key | Busy/backpressured time, per-key distribution |
| Heap/managed memory grows | Retention missing or buffers large | State size/TTL/windows/in-flight records |
| Restored job loses state | Changed UID/descriptor or intentional discard | Restore logs and topology/state mapping |
| External sink duplicates | Replay without idempotent/transactional output | Checkpoint/transaction and event IDs |
| Kafka lag grows | Ingress exceeds capacity or checkpoint/sink stalls | Broker lag and processing/sink metrics |

Observe completed/failed checkpoints, recovery count/duration, watermarks,
source lag, state size, sink latency, rejected/late records, and throughput.
Do not hide all WARN logs: investigate whether output/state/contracts are correct.
The local collection client can log startup/shutdown races; wrong or missing
business output is still a failure.

### 10.4 Layered tests

Test pure aggregate algebra and domain invariants first. Test process/window
functions with controlled timestamps/watermarks or appropriate operator harnesses.
Then run local integration jobs with exact output assertions. Finally test real
source/sink/checkpoint restore and remote failure conditions.

The advanced test asserts all six outputs, including full revised totals and
side-output behavior. It sorts results because union does not guarantee
cross-branch order. Sorting does not fix missing/extra records; equality checks
both contents and multiplicity.

Avoid `Thread.sleep` to guess when a watermark/checkpoint has happened. Emit
controlled watermarks, await a confirmed checkpoint, or use framework test
facilities with explicit state/progress observations.

### 10.5 Reference architecture: fraud alerts

```text
replayable event source -> validate/assign event time
                       -> key by account -> state/rules/time windows
                                            |
                                      identified alert
                                            |
                            transactional/idempotent sink

rules source -> versioned broadcast rules
late/invalid events -> explicit quarantine/reconciliation
checkpoint coordinator -> durable state/progress storage
```

Define event identity, accepted lateness, rule effective times, deduplication
horizon, alert retractions/updates, and sink commit. A correct model must explain
what happens when a late event changes an already delivered alert.

If an alert triggers an irreversible action, use approval/reconciliation or
domain idempotency as appropriate. An event-time result is not automatically
proof that every relevant future-late fact has already arrived.

### 10.6 Operational acceptance and security

Specify maximum source lag, recovery time, state growth, late-record rate,
checkpoint duration, and sink latency. Test those thresholds under representative
load and skew. Define incident procedures for expired source retention,
incompatible restores, corrupted/unavailable storage, and failed external commits.

Use least-privilege storage/broker access, encrypted transport where appropriate,
externalized credentials, protected dashboards, and payload-safe logging.
Snapshot/state storage can contain sensitive business data and needs lifecycle/
access governance. These are deployment responsibilities, not enabled by a
local source or `enableCheckpointing`.

## 11. Exercises with worked answers

### Exercise 1: predict the late update

At watermark 9999 the first window emits 3. Quantity 4 at timestamp 3000 arrives
before cleanup, so the retained window emits revised total 7. It is not a
separate sum of 4 unless you deliberately design delta output.

### Exercise 2: zero allowed lateness

Remove the two-second allowed lateness. Cleanup follows the first firing
boundary, so both timestamp 3000 and 4000 records become too late for that window.
Expected initial window total remains 3; no revision to 7. The timer branch's
custom rejection behavior is unchanged.

### Exercise 3: window boundary at 10000

An event timestamp exactly 10000 belongs to `[10000,20000)`. Modify the fixture's
window branch and assert a second-window result. Also redesign the timer branch
deadline for that window; its hardcoded 9999 intentionally supports only the
current fixture.

### Exercise 4: add a second key

Emit `flink` records and register timer 9999 under that key. Expected: isolated
sums and separate timer callbacks, despite the same timestamp. Deduplication of
timer registrations is per key and timestamp, not a global timestamp rule.

### Exercise 5: replay after a completed checkpoint

Use a supported replayable source and durable sink. Confirm checkpoint N,
process additional records, fail the job, restore, and compare authoritative
sink output. Expected replay after N; output duplication depends on sink
coordination. The fixture cannot prove this because its source is not a
production replayable/checkpoint-aware implementation.

### Exercise 6: stateful code upgrade

Rename `"timer-sums"` or `"sum"` in a savepoint-backed job. Explain how this changes
restore mapping/schema, then restore with a planned compatible migration.
Blindly ignoring missing state may start an empty total and is data loss, not
a successful upgrade.

### Exercise 7: hot account

Most events use one account key. Doubling parallelism cannot split that one
key's exact state machine. Combinable partial aggregation can reduce traffic
if only a sum is required; sequence-sensitive fraud logic may need a different
architecture. State the invariant before distributing it.

### Final mastery check

Compute firing and cleanup timestamps exactly, distinguish watermarks from
checkpoint barriers, identify state scope/serialization, explain changelog sink
requirements, and draw restore from source offset to sink commit. Every
"exactly once" claim should include scope, storage, replay, and commit assumptions.
