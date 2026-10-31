# Part 8 - Spark: distributed data processing

Spark distributes computations over partitioned data. This module covers
**RDDs**, **Datasets/DataFrames and SQL planning**, and **Structured Streaming**.
Use JDK 17, read Parts 1-5 first, and run from the repository root.
Spark 3.5.7 here uses Scala 2.12; it does not share the root Scala 3 classpath.

## Advanced learning path

Continue with the [detailed advanced handbook](ADVANCED.md) for query execution,
joins, analytic windows, nulls, partition/storage design, skew and performance,
streaming state/recovery, sink guarantees, cluster operations, testing, and ML
pipeline concepts. Runnable advanced lessons:

```bash
sbt "spark/runMain lectures.part8spark.AdvancedAnalytics"
sbt "spark/runMain lectures.part8spark.StreamingRecovery"
```

The recovery lesson retains its generated input, Parquet output, and checkpoint
in a unique temporary directory whose path it prints. It demonstrates graceful
query restarts, not injected crash recovery.

## Run and read

```bash
sbt "spark/runMain lectures.part8spark.SparkBasics"
sbt "spark/runMain lectures.part8spark.SparkSql"
sbt "spark/runMain lectures.part8spark.StructuredStreaming"
sbt "spark/test"
```

| File in `src/main/scala/lectures/part8spark/` | Lesson | Expected result |
|---|---|---|
| `SparkBasics.scala` | Session, RDD partitions, lazy transformations, shuffle/action | `(akka,1)`, `(scala,2)`, `(spark,2)` |
| `SparkSql.scala` | Typed Dataset, column expressions, aggregation, plans | books revenue `50`, games revenue `60` |
| `StructuredStreaming.scala` | File stream, incremental aggregation, trigger/checkpoint/sink | `(flink,1)`, `(scala,2)`, `(spark,2)` |

Output also includes runtime logs; SQL prints logical/physical plans before its
table. Each program stops its session. Streaming creates and deletes only its
own temporary input/checkpoint directory and memory table.

## Runtime architecture

```text
Your Scala main / driver JVM
    |
SparkSession --> SparkContext --> DAG scheduler --> task scheduler
                                      |
                              cluster manager allocates resources
                                      |
                       executors on worker machines
                         +-- task on partition 0
                         +-- task on partition 1
                         +-- cached blocks / shuffle data
```

The **driver** builds plans, schedules work, and receives results. **Executors**
run partition tasks and hold cached/shuffle data. A cluster manager such as
Standalone, YARN, or Kubernetes allocates resources; it is not the query optimizer.
`local[2]` in this repository runs tasks on two local threads inside one process.
It teaches scheduling APIs, not multi-machine networking or high availability.

An action starts a **job**. Shuffle dependencies divide jobs into **stages**.
A stage generally runs one **task per partition**. Partitions are pieces of data,
not necessarily files or machines. Spark may optimize plans or change partition
counts, so do not assume a fixed number of stages for every query.

### Trace the RDD word count

1. `parallelize(..., 2)` creates two partitions of tiny fixture data.
2. `flatMap` tokenizes lines and `map` attaches a count of one; both are lazy
   transformations and normally stay in the same stage.
3. `reduceByKey` combines locally before shuffling counts so each key can be
   combined across partitions. This is generally cheaper than `groupByKey` for sums.
4. `collect` launches execution and transfers the small final result to the driver.

RDD **lineage** records how data was derived and can recompute lost partitions.
It is not a durable backup of arbitrary side effects. Caching keeps reusable
results in memory/disk after an action; it is not automatic durability.

## SQL / Dataset architecture

```text
Dataset[Order] / DataFrame expressions
        |
unresolved logical plan --> analysis (schema/columns)
        |
Catalyst optimization --> physical plan --> executor tasks
```

A `Dataset[Order]` gives a Scala object API with an encoder. A DataFrame is
`Dataset[Row]`, using a schema and column expressions. After grouping, this
example returns a DataFrame. It filters invalid sample amounts, groups categories,
sums revenue, and sorts for presentation.

`explain(true)` shows analysis, optimization, and physical execution choices.
Column expressions let Spark see operations and optimize them. A Scala UDF often
hides logic from those optimizations; prefer built-in functions when possible.
SQL strings can express the same relational operations, but malformed column
names still fail at runtime. Distributed rows have no inherent display order:
explicit `orderBy` is needed for ordered results.

## Structured Streaming architecture

```text
New input files --> incremental query plan --> stateful groupBy/count
                           |                         |
                      trigger boundary          checkpoint/state
                           |                         |
                           +-------> output sink <---+
```

Structured Streaming treats arriving data as an incremental table. Default
execution uses micro-batches; it is not one fresh independent batch script per
record. Our file source has real stream semantics but finite fixture input.
`AvailableNow` processes currently available input and ends, making the lesson
repeatable without an infinite loop.

`complete` output mode writes the whole result table after each trigger.
`append` writes only newly finalized rows, and `update` writes changed rows;
not every query/sink supports every mode. Aggregating a growing stream creates
state. This unwindowed word count can grow without bound as vocabulary grows.
Production event-time aggregations typically add windows/watermarks to bound state.

The checkpoint stores progress and state metadata. A durable checkpoint path,
replayable source, and compatible sink are essential to recovery. The **memory
sink is for inspection, not durable or production exactly-once output**, and we
delete this demo's checkpoint at the end. The example does not demonstrate
restart recovery or exactly-once external writes.

## Practice and architecture checkpoints

1. Increase partitions in the RDD fixture. Explain why output counts do not
   change, but task/shuffle costs can.
2. Cache a transformed Dataset used by two actions, then `unpersist` it. Explain
   when reuse offsets caching costs and when it wastes memory.
3. Add an order/category lookup join. Explain a broadcast join versus a shuffle
   join and why broadcasting a large table is dangerous.
4. Extend streaming with event timestamps, ten-second windows, and a watermark.
   Explain state retention and what happens to data arriving too late.
5. Explain driver versus executor, transformation versus action, stage versus
   task, and RDD versus DataFrame.

## Production boundaries

Do not `collect` a large dataset: it can exhaust driver memory. Prefer distributed
writes and aggregate/sample before inspection. Skewed keys, excessive tiny
partitions, large shuffles, and closures capturing driver-only resources are
common performance/failure problems. Avoid side effects inside transformations:
task retries can repeat them.

The advanced examples add Parquet storage, joins/windows, and graceful streaming
query recovery. Kafka connectors, remote storage/cluster deployment, injected
crash recovery, resource tuning, security, and monitoring are explained in the
handbook but not configured as production systems.
`SparkBasics.session` deliberately hardcodes local execution and disables the
web UI; use an appropriately configured `spark-submit` deployment for a cluster.

References: [Spark 3.5.7 overview](https://spark.apache.org/docs/3.5.7/),
[RDD guide](https://spark.apache.org/docs/3.5.7/rdd-programming-guide.html),
[SQL guide](https://spark.apache.org/docs/3.5.7/sql-programming-guide.html),
[Structured Streaming](https://spark.apache.org/docs/3.5.7/structured-streaming-programming-guide.html).
