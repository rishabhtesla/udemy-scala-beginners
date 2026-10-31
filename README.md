
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

## Start learning

1. Install a JDK (the Java runtime and compiler Scala uses) from [Eclipse Temurin](https://adoptium.net/), then install [sbt](https://www.scala-sbt.org/download.html), the Scala build tool. Install [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) with its Scala plugin, or work from a terminal.
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

## How to get interview-ready

Interview readiness comes from being able to explain and apply ideas, not just recognize definitions. At the end of each module, use its practice tasks and interview checkpoints: solve a small problem, explain the trade-offs, and then compare your answer with the code. For every unfamiliar term, trace it to the example in this repository. Repeat the exercises without notes before moving on.

This course gives you a structured foundation; it cannot guarantee an interview outcome. Keep practicing problem solving, communication, and the specific role requirements alongside it.
