
# Learn Scala from the Ground Up

This repository contains the examples and exercises for the [Rock the JVM Scala beginners course](https://www.udemy.com/rock-the-jvm-scala-for-beginners), plus a guided path through modern Scala 3 and a small backend API. You can start without knowing Scala or functional programming. The lessons build on one another, and the backend module is deliberately saved until after the language foundations.

This is a personal learning project based on [Rock the JVM's original repository](https://github.com/rockthejvm/udemy-scala-beginners). Credit for the original course examples belongs to their authors; the learning guides and backend extension build on that foundation.

## What you will learn

| Part | Topic | Start here |
|---|---|---|
| 1 | Scala syntax, values, expressions, functions, recursion | [`part1basics`](src/main/scala/lectures/part1basics/README.md) |
| 2 | Object-oriented programming and the Scala type system | [`part2oop`](src/main/scala/lectures/part2oop/README.md) |
| 3 | Functional programming, collections, and safe values | [`part3fp`](src/main/scala/lectures/part3fp/README.md) |
| 4 | Pattern matching and expressive Scala syntax | [`part4power`](src/main/scala/lectures/part4power/README.md) |
| 5 | Advanced Scala 3 types and asynchronous programming | [`part5advanced`](src/main/scala/lectures/part5advanced/README.md) |
| 6 | A small HTTP API with http4s and Cats Effect | [`part6backend`](src/main/scala/lectures/part6backend/README.md) |
| 7 | Akka Typed actors, supervision, and Streams | [`Akka module`](modules/akka/README.md) |
| 8 | Spark RDDs, SQL/Datasets, and Structured Streaming | [`Spark module`](modules/spark/README.md) |
| 9 | Flink DataStreams, keyed state, and event-time windows | [`Flink module`](modules/flink/README.md) |

## Start learning

1. Install **JDK 17** (the common runtime for the pinned framework examples) from [Eclipse Temurin](https://adoptium.net/), then install [sbt](https://www.scala-sbt.org/download.html), the Scala build tool. Install [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) with its Scala plugin, or work from a terminal.
2. Read [Part 1](src/main/scala/lectures/part1basics/README.md) before opening the later modules.
3. For each concept, read the matching `.scala` file, run it, change one thing, and predict what will happen before running it again.
4. Attempt each exercise without looking for a complete solution. Explain your reasoning out loud, as you would in an interview.
5. Continue to Part 6 only after you can read the earlier examples comfortably.

This project uses Scala 3.3.1 and sbt. From the repository root, compile and run an example:

```bash
sbt compile
sbt "runMain lectures.part1basics.Expressions"
```

Run the backend tests and start its local server with:

```bash
sbt test
sbt "runMain lectures.part6backend.TodoApi"
```

The backend API listens on `http://127.0.0.1:8080`. Its in-memory data resets when the process stops; persistence is intentionally left as a learner extension.

## Framework learning modules

Parts 7-9 are independent sbt subprojects under `modules/`. Learn Parts 1-5 first;
Part 6 is useful backend context but not a requirement for data processing.
Each framework guide includes an architecture diagram, a code-reading path,
expected outputs, exercises, and production limitations. The examples are original,
small learning programs, not complete production platforms or copies of framework source.

After the introductory examples, follow the advanced handbooks:
[Akka](modules/akka/ADVANCED.md), [Spark](modules/spark/ADVANCED.md), and
[Flink](modules/flink/ADVANCED.md). They explain advanced mechanics, worked
traces, failure scenarios, operations, and exercises with answer guidance.
Each labels runnable code separately from conceptual production designs.
They are self-contained for the described curriculum, not a promise to cover
every API, connector, release change, or production environment.

| Framework | Best fit | Scala / framework version used here |
|---|---|---|
| Akka | Message-driven services and backpressured application streams | Scala 2.13.12 / Akka 2.6.21 |
| Spark | Distributed analytical queries and incremental micro-batch processing | Scala 2.12.18 / Spark 3.5.7 |
| Flink | Stateful event-by-event processing with event-time semantics | Scala 2.13.12 / Flink 1.20.2 Java API |

The separate Scala 2 builds match library compatibility; the existing Scala 3.3.1
lessons are unchanged. Scala 2 examples use braces and `implicit` instead of Scala
3 indentation and `given`. Flink's Java API is callable from Scala without using
the deprecated Flink Scala API. These are pinned learning versions, not a claim
that they are the latest releases. Akka's historical release and licensing are
explained in its guide.

Use JDK 17 for sbt and its forked examples. On macOS with JDK 17 installed:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
sbt "akka/runMain lectures.part7akka.TypedActors"
sbt "spark/runMain lectures.part8spark.SparkBasics"
sbt "flink/runMain lectures.part9flink.DataStreamBasics"
sbt "akka/test" "spark/test" "flink/test"
```

On other systems set `JAVA_HOME` to your JDK 17 installation. In IntelliJ, reload
the sbt build and set both the project SDK and sbt JVM to JDK 17. Run these examples
through sbt so the forked JVM receives the module options defined in `build.sbt`.
Spark/Flink start local execution runtimes and need more memory than the basic
lessons; the first run downloads their dependencies. No Kafka, Hadoop installation,
cloud account, or external cluster is required.

Forked JVM loggers often write to stderr, which sbt can prefix with `[error]`
even for a logger's `INFO` line. Read the logger's actual level, exception,
business output, and process/task result together; do not dismiss real failures
just because other stderr lines are informational.

Root commands do **not** aggregate the framework projects: `sbt test` still runs
only the original lessons/backend tests. Run framework tests explicitly as above.

## How to get interview-ready

Interview readiness comes from being able to explain and apply ideas, not just recognize definitions. At the end of each module, use its practice tasks and interview checkpoints: solve a small problem, explain the trade-offs, and then compare your answer with the code. For every unfamiliar term, trace it to the example in this repository. Repeat the exercises without notes before moving on.

This course gives you a structured foundation; it cannot guarantee an interview outcome. Keep practicing problem solving, communication, and the specific role requirements alongside it.
