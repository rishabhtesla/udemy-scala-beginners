# Advanced Spark handbook

This original handbook targets **Spark 3.5.7 / Scala 2.12 / JDK 17** and extends
[the introduction](README.md). It explains the covered topics without requiring
an external tutorial: runtime mechanics, query reasoning, storage, streaming,
failure semantics, operations, and applied exercises.

It does not document every API or connector. Local analytical and file-stream
examples are runnable; Kafka, external databases, remote clusters, lakehouse
transactions, and ML training below are explained designs, not installed systems.
Version-specific behavior and a production environment still require verification.

## Contents

1. [Executable lessons and reading order](#1-executable-lessons-and-reading-order)
2. [Execution from Scala expression to task](#2-execution-from-scala-expression-to-task)
3. [Schema, nulls, types, and query semantics](#3-schema-nulls-types-and-query-semantics)
4. [Joins and analytic windows](#4-joins-and-analytic-windows)
5. [Partitions, shuffles, caching, and skew](#5-partitions-shuffles-caching-and-skew)
6. [Storage, files, and reproducible ingestion](#6-storage-files-and-reproducible-ingestion)
7. [Structured Streaming state and time](#7-structured-streaming-state-and-time)
8. [Recovery and sink guarantees](#8-recovery-and-sink-guarantees)
9. [Cluster deployment and operations](#9-cluster-deployment-and-operations)
10. [Testing, ML, and system design](#10-testing-ml-and-system-design)
11. [Exercises with worked answers](#11-exercises-with-worked-answers)

## 1. Executable lessons and reading order

```bash
sbt "spark/runMain lectures.part8spark.AdvancedAnalytics"
sbt "spark/runMain lectures.part8spark.StreamingRecovery"
sbt "spark/test"
```

Run from the repository root. Use JDK 17 so sbt and its forked JVMs agree with
the pinned framework requirements. The module's JVM options supply the Java
module access normally provided by Spark launch scripts.

`AdvancedAnalytics` demonstrates a tiny dimension broadcast, a left join,
explicit unknown-customer display, deterministic row ranking, a cumulative
window sum, caching, and plan inspection.

Expected table, in this order:

| id | customerId | name | amount | rank | runningRevenue |
|---|---|---|---|---|---|
| 2 | 1 | Ada | 50 | 1 | 50 |
| 1 | 1 | Ada | 30 | 2 | 80 |
| 3 | 2 | Grace | 40 | 1 | 40 |
| 4 | 99 | Unknown | 10 | 1 | 10 |

`StreamingRecovery` creates a unique directory and prints its location. It writes
two input files across successive query runs, reuses the same checkpoint/output,
and runs once more with no new input. Final output is exactly:
`(1,scala)`, `(2,spark)`, `(3,flink)`.

The main deliberately retains the input, Parquet files, `_spark_metadata`, and
checkpoint for inspection. The test deletes only its own temporary tree. Do not
point this teaching program at an existing production checkpoint.

## 2. Execution from Scala expression to task

### 2.1 Driver, executors, and closure serialization

The driver runs `main`, creates SparkSession, builds plans, and coordinates jobs.
An executor runs tasks on data partitions. Your driver-side variables are not
shared mutable variables with every executor. Code used inside a distributed
transformation is serialized as part of task closures.

Capturing a driver database connection, SparkSession, or nonserializable client
in a closure can fail serialization or wrongly move a driver-only resource.
Construct appropriately scoped executor-side resources when needed, close them,
and handle retries. Never create a new connection per row for a large dataset.

Even in local mode, think in those boundaries. A program accidentally relying
on local JVM sharing can break on a real cluster.

### 2.2 Narrow and wide dependencies

A narrow dependency lets each child partition depend on a limited parent set
without all-to-all redistribution, as in ordinary `map` or `filter`.
A wide dependency redistributes data, as in grouping keys that occur on many
partitions. Shuffle writes intermediate data and downstream tasks fetch relevant
pieces.

```text
read partitions -> filter/map -> shuffle write
                                    |
                         network/local shuffle fetch
                                    |
                         aggregate/join -> output
```

The scheduler creates stages around shuffle dependencies. A task processes a
partition for a stage. One action can involve multiple jobs internally; SQL
planning, exchanges, and adaptive execution mean "one action equals exactly
one fixed number of jobs" is too simplistic.

### 2.3 Lineage, retries, and side effects

RDD lineage records how to reconstruct lost data. Task retry can rerun a
partition, and speculative execution can run additional attempts of slow tasks.
Therefore an HTTP request or insert inside `map` can execute more than once.
Lineage reconstructs computations; it cannot undo an already sent email.

Accumulators are diagnostic aggregation tools, not transactional business
counters. Use controlled sinks and idempotency for external effects.

### 2.4 Catalyst and physical execution

SQL expressions produce a logical plan. Analysis resolves columns and data types.
Catalyst applies rewrites such as filter/project optimization. Physical planning
chooses execution operators: scans, joins, exchanges, aggregates, sorts.
Generated code and internal representations reduce per-row object overhead.

`explain(true)` exposes parsed/analyzed/optimized/physical plans. Look for
`Exchange` (redistribution), join type, scan filters, and unnecessary wide
projections. An initial plan is not always the final runtime plan: Adaptive
Query Execution (AQE) can adjust decisions using runtime statistics.

A Scala collection `.map` runs immediately in the driver; a Spark Dataset/RDD
transformation generally builds a distributed plan. Similar syntax does not
imply the same execution location.

## 3. Schema, nulls, types, and query semantics

### 3.1 Dataset versus DataFrame

`Dataset[Sale]` uses an encoder for typed JVM objects. A DataFrame is
`Dataset[Row]` with named, schema-described columns. The typed API does not make
every SQL string/column expression compile-time checked. A misspelled
`col("amunt")` still fails during analysis.

Use built-in column expressions when possible so the optimizer understands
the operation. A UDF can hide semantics, introduces serialization/type concerns,
and often prevents useful optimization. UDFs are justified for behavior absent
from built-ins, not simply to reuse every ordinary Scala function.

### 3.2 Null uses three-valued logic

SQL predicates can be true, false, or unknown. `NULL = 10` is unknown, not false
in the ordinary two-valued sense. A filter retains only true rows. Thus a null
amount can disappear from `amount > 0` without a Scala exception.

`isNull`, `isNotNull`, `coalesce`, and null-safe equality are deliberate tools.
`coalesce(name, "Unknown")` is a display rule in the example. It should not
silently conceal a missing required customer relationship in a real ledger.

`count(*)` counts rows. `count(column)` excludes null values in that column.
Many aggregates ignore nulls, while all-null inputs can produce null results.
Make missing-value semantics part of the data contract and test them.

### 3.3 Types, precision, and parsing

Floating point is approximate. For financial fractional amounts use suitable
decimal precision/scale and defined rounding, not an unqualified `Double`.
The examples use `Long` whole units only to keep their arithmetic transparent.

Cast/parsing behavior depends on settings, including ANSI SQL mode. Do not
assume malformed text always produces null or always throws. Use explicit
schema, parse rules, validation, and an agreed reject/quarantine policy.

The streaming fixture sets `FAILFAST` for malformed JSON. Declaring a field
nonnullable in a read schema is not a complete business validation system:
missing/null values can still require explicit checks. Reject required-field
violations separately rather than assuming schema metadata enforces them all.

### 3.4 Time zones and deterministic ordering

An event's timestamp representation must distinguish an instant from a local
date/time. Define input format and session time zone, usually explicitly:

```scala
// Illustrative configuration, not a change to the existing fixtures:
spark.conf.set("spark.sql.session.timeZone", "UTC")
```

Sorting by a nonunique column leaves ties without a stable order. Add a unique,
meaningful tie-breaker when business ranking must be reproducible. Distributed
output is not globally sorted merely because input files were sorted.

## 4. Joins and analytic windows

### 4.1 Join meaning before join performance

| Join | Preserves | Typical use |
|---|---|---|
| Inner | Matching rows only | Require an existing dimension match |
| Left outer | Every left row, nullable right attributes | Enrich facts without dropping missing dimensions |
| Full outer | Unmatched rows from both sides too | Reconciliation |
| Left semi | Left rows with a match, no right columns | Membership filtering |
| Left anti | Left rows without a match | Missing/invalid relationships |
| Cross | Every left/right pair | Deliberate Cartesian combinations |

A join with nonunique keys on both sides can multiply rows. Three facts matching
two dimension records produce six joined rows. `distinct` afterward is not
a principled fix; define whether the dimension should be unique or time-versioned.

Null keys generally do not match using ordinary equality. Choose null-safe
matching only if "both missing" is a valid relationship.

### 4.2 Broadcast versus shuffle join

A broadcast join sends a sufficiently small side to executor memory and avoids
shuffling the large side for supported join shapes. It uses executor memory,
driver preparation, and network bandwidth. A "small file" compressed on disk can
expand greatly in memory, so measure representation size.

A sort-merge join generally shuffles compatible keys and sorts partitions.
It can handle large sides but pays network, disk, and sort costs. AQE may use
runtime statistics to switch strategies or optimize skew where supported.

`broadcast(customers)` is an explicit hint in the executable because the
dimension contains two rows. It is not a recommendation to broadcast any
customer table. Inspect the actual plan and memory pressure.

### 4.3 Analytic windows are not streaming event-time windows

An analytic SQL window computes values over related rows while retaining each
row. `groupBy` instead collapses rows into aggregates. These are different from
event-time windows that manage incremental stream state.

In `AdvancedAnalytics`, partition by `customerId`; order by amount descending
then ID ascending. `row_number` assigns 1, 2, ... in that order. The explicit
tie-breaker ensures stable numbering for equal amounts.

`rowsBetween(unboundedPreceding, currentRow)` defines a row-position frame.
For Ada, sorted amounts are 50 and 30, so cumulative sums are 50 and 80.
`rangeBetween` uses ordering values rather than row positions; peer rows can
share the same range result. Choosing a frame is semantic, not cosmetic.

`rank` leaves gaps after ties; `dense_rank` does not; `row_number` gives a unique
position even for ties. `lag`/`lead` inspect neighboring ordered rows and need
explicit handling when no such neighbor exists.

### 4.4 Cost of windows

Grouping by customer typically needs partition redistribution, and ordering
within groups needs sorting. One customer with millions of rows can become a
hot partition. The window cannot arbitrarily split that customer's exact
sequential calculation without changing the algorithm.

Do not use a global window without a partition key accidentally; it can force
most work into one partition. A global top-N can often use a different plan.

## 5. Partitions, shuffles, caching, and skew

### 5.1 Three different partitions

An execution partition is a piece of data processed by a task.
A filesystem partition is a directory layout such as `date=2026-10-31`.
A shuffle partition is a bucket produced by redistribution.
These can have different counts and are not interchangeable.

`repartition(n)` redistributes data and generally shuffles. `repartition(n, key)`
also specifies distribution by key. `coalesce(n)` commonly reduces partition
count with less redistribution, but can produce uneven work and does not create
a free balanced increase in parallelism.

Writing with `coalesce(1)` creates a bottleneck and one large writer. It is fine
for a tiny fixture, not a universal way to "clean up output".

### 5.2 Partition sizing

Too few partitions underutilize cores and create large task memory demand.
Too many tiny partitions increase task scheduling/file overhead. Size using
data volume, record expansion, shuffle costs, available cores, and sink layout.

Example: 100 GB divided into 1,000 partitions averages 100 MB before decompression
and processing expansion. That average does not prove the largest partition
is safe. Measure distribution and p95/p99 task behavior.

The fixture's `spark.sql.shuffle.partitions = 2` is for tiny local data only.
Do not copy it into a production job with terabytes.

### 5.3 Cache, persist, checkpoint

Caching retains reusable results after an action materializes them.
`persist(MEMORY_AND_DISK)` allows spill when memory is insufficient.
`unpersist` releases retained blocks when no longer needed. Calling cache alone
does not eagerly run the computation.

`AdvancedAnalytics.count()` populates the cache; `show()` may reuse it.
For four rows this is demonstration, not a performance win. Cache only data
reused enough to justify storage and eviction pressure.

RDD/DataFrame checkpointing can truncate lineage and store a checkpoint.
Streaming checkpoints track incremental progress/state and have a different
role. Local checkpointing uses less durable storage and is not disaster recovery.
Never confuse cached blocks with authoritative source data.

### 5.4 Diagnosing skew

A hot key can send most rows to one reducer. Most tasks finish quickly while
one runs much longer or spills heavily. Inspect per-task durations, input/shuffle
sizes, key distribution, and spill metrics.

Possible remedies: pre-aggregation, filtering unnecessary data, broadcast a
genuinely small dimension, AQE skew handling for supported plans, or salting
where the algebra permits it.

Salting splits one hot key into artificial subkeys, performs a partial operation,
then combines back by the original key. Summation is combinable; median or
ordered per-customer history requires more careful algorithms. Replicating one
join side to match salts changes memory/network costs and can introduce duplicate
results if constructed incorrectly.

### 5.5 A disciplined optimization loop

First validate results, then inspect the plan, then measure stages/tasks.
Change one justified cause and compare on representative data. "Increase
executor memory" can mask a shuffle/skew issue without fixing it.

Projection/filter pushdown can reduce scanned data. Avoid unnecessary columns
before joins and shuffles. Avoid `groupByKey` when combinable aggregates can use
map-side reduction. Avoid driver loops that launch thousands of tiny independent
jobs instead of one distributed transformation.

## 6. Storage, files, and reproducible ingestion

### 6.1 File formats

CSV is human-readable but has weak type/schema semantics and tricky quoting,
delimiter, timestamp, and null representations. JSON preserves field names and
nested data but can be costly and inconsistent. Parquet is columnar, typed,
compressed, and supports reading selected columns and metadata-assisted pruning.

Columnar does not mean every predicate skips every irrelevant row. Pushdown,
statistics, row groups, file layout, and predicate form affect scan efficiency.

### 6.2 Layout and small files

Partition directories by useful, reasonably bounded columns, such as date.
Do not blindly partition by a near-unique ID: it can create millions of tiny
directories/files. Pruning helps when queries filter partition columns, not
because the directories look tidy.

Tiny files increase listing and open/metadata overhead. Huge files reduce
available parallelism for some formats/workflows. Compaction and layout should
follow query/ingestion patterns and consistent transaction semantics.

### 6.3 Schema evolution and atomicity

Adding, removing, or changing field types affects historical files/readers.
Maintain contracts and test old/new data together. Schema merging can be
expensive and cannot repair contradictory business meanings.

Plain Parquet files are not a complete transactional table system. Concurrent
writers, updates/deletes, consistent snapshots, and safe schema evolution need
an appropriate catalog/table format or carefully controlled workflow.
Delta Lake, Iceberg, and Hudi provide different additional table mechanisms;
none is installed merely by depending on Spark SQL.

### 6.4 Atomic file arrival in the recovery example

The program writes a complete JSON file outside the watched `input` directory,
then moves it into place. Spark's file source should not see a file still being
written. This is a local filesystem experiment; object-store rename/commit
behavior can differ, so use its supported upload/commit protocol.

Each filename is unique and immutable. Editing an already observed file is
not a reliable way to publish a new stream event. Retention must keep input
available for whatever recovery/replay guarantees are required.

## 7. Structured Streaming state and time

### 7.1 Incremental execution model

A streaming DataFrame describes an incrementally updated table. In micro-batch
execution, each trigger reads a slice of new input and updates/output results.
Batch duration includes scheduling, input processing, state operations, and sink
commit. If work consistently takes longer than the trigger interval, latency
and backlog grow.

`AvailableNow` processes input available at invocation and ends after the
necessary batches. It is useful for reproducible incremental runs; it is not
a permanently running low-latency trigger.

### 7.2 Output modes

| Mode | Result sent to sink | Main restriction to reason about |
|---|---|---|
| Append | Newly final output rows | Aggregates may need watermarks/finalization |
| Update | Rows whose results changed | Sink must handle repeated keys/updates appropriately |
| Complete | Entire result table each trigger | State/output can grow; sink compatibility matters |

The introductory word count uses complete mode and a memory sink. The recovery
example is a stateless append query with a Parquet file sink. Do not compare
their recovery properties as if they used the same sink/state.

### 7.3 Event time and watermark reasoning

Processing time is when the engine runs. Event time is a timestamp in each
record. A watermark lets the query bound how much older state/input it needs
to retain for supported stateful operations.

Conceptual query, not an additional runnable lesson:

```scala
events
  .withWatermark("eventTime", "2 minutes")
  .groupBy(window(col("eventTime"), "10 minutes"), col("customerId"))
  .agg(sum(col("amount")).as("total"))
```

Place the watermark on the actual timestamp column used by the aggregation,
before aggregation. A ten-minute window groups event timestamps into intervals;
the two-minute delay concerns tolerated progress behind observed event time,
not the trigger period or wall-clock sleep.

Suppose the maximum observed event time is 12:20 and a two-minute delay is used:
the watermark will be around 12:18 according to query/batch progression.
An old `[12:00,12:10)` window can eventually be finalized/evicted in supported
modes. An event at 12:09 arriving after that progress may no longer contribute.

Watermarks do not mean "exactly two minutes after each arrival". Spark guarantees
data less delayed than the configured threshold is not dropped by the watermark
policy; older data may or may not still be processed depending on state/progress.
Do not promise an exact per-record cutoff based solely on the delay string.

Complete-mode aggregates require retaining the full result table; a watermark
is not a general promise that all state will be removed in every query/mode.

### 7.4 Deduplication and joins in streams

Deduplication needs state remembering IDs. Unbounded IDs imply unbounded state
unless a supported watermark/retention design bounds it. Define whether a retry
outside retention is allowed to count again.

Stream-static joins enrich each incoming event with a static relation using a
supported join shape. Stream-stream joins can need both sides' state plus
watermarks/time constraints to clean it. An outer join must know when a missing
match is final; otherwise it cannot safely emit a final unmatched row promptly.

With multiple streaming inputs, watermark policy and slow/idle input affect
progress. Faster cleanup can lose slow-input data; conservative cleanup can
increase state and latency. State these trade-offs explicitly.

### 7.5 Stateful function semantics

`mapGroupsWithState` / `flatMapGroupsWithState` allow custom per-key state and
timeout behavior with mode restrictions. The function sees a grouped slice,
not necessarily a globally ordered event history. Sort explicitly if a bounded
group's event order is required, and avoid loading unbounded histories into RAM.

State serialization, timeout semantics, late data, and checkpoint compatibility
become application responsibilities. A stateful code/schema change may not be
compatible with an existing checkpoint. Treat it as a migration, not just a
source edit.

## 8. Recovery and sink guarantees

### 8.1 Trace the runnable recovery lesson

First write records 1 and 2 and start the query. The JSON source records file
progress in the checkpoint. The file sink writes Parquet output and its commit
metadata. After graceful termination, add record 3 in a new file.

Start the same query with the same input/output/checkpoint. It resumes progress
and commits only the new file's output. A third run with unchanged input adds
no business rows. The test reads committed Parquet output and asserts all three
IDs exactly once; it does not merely check that the directory exists.

This proves graceful restart behavior for this file-source/file-sink plan.
It does not simulate a crash at every commit boundary or prove exactly-once
arbitrary external effects.

### 8.2 What a checkpoint contains

Checkpoints include query metadata, source offsets/progress, commits, and
state-store data for stateful operators. Source progress and output commit
coordination allow replay/recovery under the source/sink contracts.

Deleting the checkpoint resets progress; preserving output alone does not
tell a new query which input was already processed. Reusing a checkpoint for
an unrelated query or changed state schema can fail or produce wrong semantics.
Use one checkpoint per logical query and protect it as production state.

Query ID persists across runs recovered from a checkpoint; run ID changes each
run. They help correlate continuity versus a particular execution attempt.

### 8.3 File sink versus memory versus external sink

The supported file sink uses metadata/commit mechanisms for its documented
semantics. Read output through a supported Spark file/table reader aware of
the committed metadata; a raw file listing can expose artifacts that are not
the logical committed result.

The memory sink is for debugging and is not durable exactly-once output.
Kafka, database, and custom sinks have their own guarantees; do not transfer
the file sink's claim automatically to them.

`foreachBatch` supplies a batch DataFrame and a batch ID. Its callback is
at-least-once by default. To achieve idempotent output, tie the batch ID to a
stable query/application identity and atomically coordinate deduplication with
the actual writes.

Unsafe sequence: mark batch complete, then crash before writing rows. The retry
skips lost rows. Another unsafe sequence: write rows, crash, then mark complete;
retry can duplicate them. Use a transaction or an idempotent row/upsert design
with a clear commit boundary.

### 8.4 End-to-end assumptions

Recovery requires replayable input with adequate retention, durable compatible
checkpoint state, deterministic enough processing, and sink-specific commits.
Nondeterministic external lookups can change results on replay.

Graceful restart, process crash, executor loss, driver loss, storage outage, and
input deletion are different failure tests. A checkpoint directory does not
prove all those cases have passed.

## 9. Cluster deployment and operations

### 9.1 Local, client, and cluster modes

Local mode runs on one machine. Client deployment keeps the driver with the
submitting client; cluster deployment runs the driver under the cluster platform.
Executor reachability, driver lifecycle, dependency distribution, credentials,
and log collection change with deployment mode.

The examples hardcode `local[2]`, so they are intentionally not production
submission artifacts. A production entry point should let deployment configure
master/resources instead of overriding them in code.

Conceptual submission shape (placeholders, not executed here):

```text
spark-submit --class your.package.Main --master <cluster-master>
             --deploy-mode <client-or-cluster> <application-jar>
```

Package dependencies compatibly with cluster Spark/Scala versions. Bundling
another Spark runtime inside an application can cause classpath/version conflicts.
Java module/runtime options also need validation outside sbt.

### 9.2 Resources and failure

Executor memory covers heap, while overhead/nonheap/off-heap/native needs also
consume container/process resources. Executor cores control simultaneous tasks;
more cores per executor increase concurrent working sets, not just throughput.

Dynamic allocation changes executors within configured bounds and requires
appropriate shuffle preservation/integration. It is not a remedy for an
unbounded stateful query or a single hot key.

Executor loss can trigger task recomputation. Driver failure can end batch
coordination or require platform-managed restart. A streaming query's recovery
still depends on checkpoint/source/sink guarantees.

### 9.3 Metrics and diagnosis

| Symptom | Plausible cause | Inspect |
|---|---|---|
| One task much slower | Skew/hot key | Task input/shuffle distribution |
| All tasks spill | Working sets too large | Partition size, aggregation/join plan, memory |
| Driver OOM | Large collect/broadcast preparation | Driver result size and captured data |
| Thousands of tiny tasks/files | Excess partitioning | Scan layout, shuffle/output counts |
| Streaming lag grows | Processing slower than arrival | Input/processed rate, trigger durations, sink latency |
| State grows indefinitely | Missing/ineffective retention | Watermark progression and state operator metrics |
| Query fails after deploy | Checkpoint/schema incompatibility | Query changes and state schema |

For streaming, inspect `lastProgress` / progress events: batch ID, input count,
input/processed rows per second, duration breakdowns, event time/watermark, and
state operator metrics. A processed rate reported during one batch is not alone
a stable system throughput guarantee.

Enable event-log/history collection in real deployments so completed applications
can be diagnosed after their live UI ends. Protect logs and metrics from
leaking credentials or personal data.

### 9.4 Security and operational boundaries

Authentication to storage/brokers, transport encryption, least-privilege service
identities, credential distribution, network access, and UI protection depend
on the platform. Do not embed passwords in code or submit them in visible
command-line arguments.

Back up/check restore stateful checkpoints and table metadata as appropriate.
Define retention, maximum acceptable lag, restart procedure, rollback plan,
and what to do when input retention has already expired.

## 10. Testing, ML, and system design

### 10.1 Test the data contract

Use small fixtures covering duplicate keys, nulls, unmatched dimensions, ties,
empty input, malformed records, large/overflowing values, and late events.
Compare row values, schema, and ordering only when ordering is specified.
Avoid asserting a particular physical plan string as the only correctness test.

The analytical test verifies IDs, names, ranks, and cumulative sums. The recovery
test verifies the exact final row set after repeated query runs.
Cluster/performance tests need representative volume/skew and failure injection;
four local rows cannot establish a production SLA.

### 10.2 ML pipeline concepts

Spark ML's DataFrame-based pipeline separates **Transformers** (apply a learned
or fixed transformation) and **Estimators** (fit data to produce a model/transformer).
A pipeline combines feature processing and a model in an explicit sequence.

Example design: string indexing -> vector assembly -> scaling -> classifier.
Split training/validation/test by the real prediction problem, often time rather
than random rows for temporal data. Fit preprocessing only on training data;
fitting scaling or category statistics on test data leaks information.

Choose metrics suited to class imbalance and costs, not accuracy alone.
Cross-validation costs multiple distributed fits. Persist a model with its
feature schema and training provenance; deployment must reproduce feature
semantics. ML dependencies/models are not added in these lessons.

### 10.3 Reference architecture: transaction analytics

```text
immutable raw events -> validated/quarantined ingestion
                    -> typed/normalized analytical storage
                    -> joins/aggregates -> serving tables/reports
stream updates ------> checkpointed query -> sink commit protocol
```

Keep raw data for replay where permitted; define validation failures explicitly.
Separate event identity from input file identity. Deduplicate according to a
business key/retry horizon, not blindly on every column.

Choose table storage, query partitioning, freshness target, state retention,
input retention, and consumer consistency. If a dashboard needs subsecond keyed
alerts rather than large joins, compare Flink or a service architecture instead
of forcing Spark to satisfy every workload.

## 11. Exercises with worked answers

### Exercise 1: duplicate customer dimension

Add a second row for customer 1. Expected: both sales for customer 1 multiply.
Correct response is to validate/select the intended unique or time-effective
dimension before joining. Dropping duplicates arbitrarily may pick the wrong
customer attributes and hide the defect.

### Exercise 2: tied amounts

Make Ada's amounts both 50. With order `(amount desc, id asc)`, ID 1 ranks before
ID 2. Row-frame cumulative sums are 50 then 100. A value-range frame over amount
can include peers together; explain why that would change the result.

### Exercise 3: no new stream input

Run the query again using the same checkpoint and output. Expected: three rows,
not six. Why? Source progress resumes and sink commits already exist. Deleting
the checkpoint changes the experiment and removes that continuity contract.

### Exercise 4: failure in `foreachBatch`

Write half the rows then throw. A retry may run the batch again. A correct sink
needs atomic commit or idempotent writes with durable batch/operation identity.
Merely printing the batch ID or catching the exception is not a solution.

### Exercise 5: hot customer

Put most rows under customer 1. Observe uneven task costs. For simple sums,
partial aggregation/salting can combine partial totals. For exact ordered
cumulative revenue, the sequence dependence means arbitrary salting is not
semantically equivalent. Optimize based on the operator's algebra.

### Exercise 6: malformed and missing fields

Malformed JSON under `FAILFAST` should fail the query. A syntactically valid
record with a missing required field may need an explicit validation check.
Decide whether to fail or quarantine, then assert both outcomes; do not silently
count invalid rows as legitimate transactions.

### Final mastery check

Explain where Scala code runs, draw the shuffle boundaries, distinguish analytic
and event-time windows, justify every collect/broadcast/cache, compute tie/null
semantics, and name the recovery boundary of the sink. For a performance claim,
provide a plan and measurements, not only a configuration change.
