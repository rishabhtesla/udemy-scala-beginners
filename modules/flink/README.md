# Part 9 - Flink: stateful stream processing

Flink executes dataflow graphs over bounded or unbounded data. Its streaming
runtime supports keyed state, event time, and consistent checkpoints. This module
teaches **bounded batch DataStreams**, **keyed streaming state**, and **event-time
windows**. Read Parts 1-5 first and use JDK 17.

The examples call Flink 1.20.2's **Java DataStream API from Scala 2.13**. The Java
API is supported; Flink's separate Scala API is deprecated. Explicit type
information and named function classes make serialization and generic types
visible rather than relying on erased Scala closures.

## Advanced learning path

Continue with the [detailed advanced handbook](ADVANCED.md) for state/timers,
incremental aggregation, precise watermark/late-data behavior, checkpoints and
savepoints, backpressure, connectors, Table API/SQL, rescaling, operations, and
testing. The controlled advanced experiment runs with:

```bash
sbt "flink/runMain lectures.part9flink.AdvancedEventTime"
```

It uses explicit watermarks to prove window revisions, cleanup, side outputs,
and timer firing without sleeps. Its legacy `SourceFunction` is deliberately
limited to a pinned-version teaching fixture, not a new production connector.

## Run and read

From the repository root:

```bash
sbt "flink/runMain lectures.part9flink.DataStreamBasics"
sbt "flink/runMain lectures.part9flink.KeyedState"
sbt "flink/runMain lectures.part9flink.EventTimeWindows"
sbt "flink/test"
```

| File in `src/main/scala/lectures/part9flink/` | Lesson | Expected result |
|---|---|---|
| `DataStreamBasics.scala` | Environment, source/operators, partitioning, bounded reduce | `(akka,1)`, `(flink,2)`, `(scala,2)` |
| `KeyedState.scala` | Streaming execution and runtime-managed `ValueState` | `scala:1`, `flink:2`, `scala:4` |
| `EventTimeWindows.scala` | Timestamps, out-of-order records, watermarks/windows | `scala:[0,10000)=3`, `scala:[10000,20000)=2` |

All sources are finite, even the streaming examples. `executeAndCollect` launches
a local runtime and returns a closeable result iterator. Closing it releases the
collection job resources. These small result sets may be collected locally; do
not use local collection as a sink for large production streams.

The local runtime prints initialization and shutdown logs. Flink 1.20's
collection client can also log transient coordinator-not-ready or
already-stopped MiniCluster warnings while starting/closing these finite jobs.
Those warnings are not the expected business output; an actual thrown exception
or incorrect/missing result is a failure, not something to suppress.

## Runtime architecture

```text
Scala client: StreamExecutionEnvironment --> dataflow/job graph
                                          |
                               JobManager coordinates job
                              /            |             \
                    scheduling       checkpoints       recovery
                                          |
                          TaskManagers: task slots/resources
                           +-- source subtasks
                           +-- keyed operator subtasks + state
                           +-- sink subtasks
```

The **client** builds/submits a graph. The **JobManager** coordinates execution;
the **TaskManagers** run parallel operator subtasks. A **slot** is an allocation
of task resources, not simply one thread. Compatible operators can be chained
to reduce network/serialization overhead. `setParallelism` controls how many
parallel subtasks an operator generally uses.

The local environment starts this machinery on your machine. It does not deploy
a remote cluster. `keyBy` repartitions records by key so all records for one key
reach the same keyed operator subtask. Many different keys can share a subtask.

### Trace the bounded word count

1. `fromData` describes the finite source; graph-building does not run the job.
2. `flatMap` emits `(word, 1)` tuples with explicit Flink type information.
3. `keyBy` routes matching words together and `reduce` adds their counts.
4. `executeAndCollect` submits the graph and reads the final output.

In **BATCH** mode bounded reductions produce final results per key. In
**STREAMING** mode a keyed reduce emits updates as records arrive. Do not apply
`toMap` to partial streaming updates and pretend they are guaranteed final totals.

## State and checkpoints

```text
("scala",1) --> keyBy --> ValueState for scala: 0 -> 1 --> scala:1
("flink",2) --> keyBy --> ValueState for flink: 0 -> 2 --> flink:2
("scala",3) --> keyBy --> ValueState for scala: 1 -> 4 --> scala:4
```

`RunningTotal.open` gets a state handle from the runtime. The handle accesses
the value for the **currently processed key**, not one global counter.
The initial absent value maps to zero intentionally; it is not error recovery.
State is separate from emitting output.

Checkpoints use barriers to establish a consistent snapshot of source progress
and operator state. Following failure, a configured job can restore a snapshot
and replay input after it. Managed state is not an external database and does
not automatically make arbitrary side effects exactly once.

The demo enables a one-second checkpoint interval, but its finite job may end
before any checkpoint completes. It does **not** configure durable checkpoint
storage, demonstrate failure recovery, or guarantee exactly-once external output.
A production job needs persistent checkpoint storage, a restart strategy,
replayable sources, and transactional/idempotent sinks as appropriate. State TTL
can control retention, but changes application semantics.

## Event time and watermarks

**Processing time** is the runtime machine's clock when handling a record.
**Event time** comes from the event itself. Network delays can make timestamp
order differ from arrival order.

```text
arrival timestamps: 1000, 3000, 2000, 11000 milliseconds
                              ^ out of order
timestamp assigner --> watermark strategy --> keyed ten-second windows
                                               [0,10000): 1+1+1 = 3
                                           [10000,20000): 2     = 2
```

The strategy allows two seconds of out-of-order progress. Periodic watermarks
are based on the maximum observed timestamp minus the configured delay (with
an internal boundary adjustment); a watermark is a progress estimate, not a
wall-clock timer or proof no older event can ever arrive.

Window intervals are half-open: a timestamp of `10000` belongs to the second
window, not the first. Flink closes/fires windows as watermarks pass their end.
The bounded source emits a final watermark, so the final window also closes.
This small fixture demonstrates out-of-order input, not a simulated late-data
recovery test.

Default allowed lateness is zero: records for a window already cleaned up are
dropped unless you configure a late-data side output. Allowed lateness is a
separate concept from the watermark delay. With several input partitions, progress
is constrained by the slowest active watermark; idle sources may need idleness
detection.

`WindowTotal` buffers all window events for clarity. An incremental
`AggregateFunction` plus a window function is preferable for large windows.

## Practice and architecture checkpoints

1. Add a second word to the event-time input and verify isolated window results.
2. Add a timestamp exactly at `10000` and predict which window receives it.
3. Use a controllable source that emits watermarks, then deliberately send late
   records. Add a late-data side output and verify it, rather than relying on
   timing/sleeps in a tiny bounded source.
4. Configure durable checkpoints in a separate experiment and restart after
   failure. Distinguish completed checkpoints from merely enabling checkpointing.
5. Explain JobManager versus TaskManager, parallelism versus slots, operator
   state versus keyed state, and watermark delay versus allowed lateness.

## Choosing between the three frameworks

| Need | Starting point | Important distinction |
|---|---|---|
| A service owning state and responding to commands | Akka actors | Message protocol/hierarchy; not a distributed query engine |
| Backpressured application I/O pipelines | Akka Streams | Application stream graphs; not automatic durable cluster state |
| Large analytical joins/aggregations | Spark SQL | Optimized distributed table computation |
| Incremental analytical queries | Spark Structured Streaming | Primarily micro-batches, with checkpointed state |
| Continuous keyed events and event-time timers/windows | Flink | Streaming-first dataflow with managed state |

Both Spark and Flink process bounded and unbounded data. Latency, connectors,
recovery semantics, team experience, and deployment requirements matter more
than claiming one framework always wins. Kafka, Table API/SQL, connectors,
savepoints, RocksDB state, production sinks, deployment, and monitoring are
additional topics; none is silently enabled by these local examples.

References: [Flink 1.20 concepts](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/concepts/overview/),
[DataStream](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/overview/),
[state](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/state/),
[event time](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/event-time/generating_watermarks/).
