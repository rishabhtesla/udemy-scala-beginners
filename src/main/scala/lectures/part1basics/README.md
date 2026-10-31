# Part 1 — Scala Basics

Start here if Scala is new to you. This module introduces the building blocks used by every later part: values, types, expressions, methods, and recursion. You do not need to know object-oriented or functional programming first.

## Before you begin

You need a JDK, sbt, and this repository opened in IntelliJ IDEA or a terminal. To check that the project compiles and run your first example from the repository root:

```bash
sbt compile
sbt "runMain lectures.part1basics.ValuesVariablesTypes"
```

Each example is a Scala `object` extending `App`; `runMain` names the object to execute. The printed output is a useful first feedback loop: make a small change, rerun it, and compare what you expected with what happened.

## Learn in this order

### 1. Values, variables, and types — `ValuesVariablesTypes.scala`

A **value** is a named result. `val` creates a name that cannot be reassigned; `var` creates a name that can be reassigned. A **type** tells Scala what kind of value is allowed, such as `Int` for whole numbers or `String` for text. Scala can often infer the type, but writing it explicitly is useful while learning.

```scala
val age: Int = 20
val greeting = "Hello"
var score = 0
score = score + 1
```

Prefer `val` unless you have a clear reason to change a variable. This habit makes programs easier to reason about.

### 2. Expressions and code blocks — `Expressions.scala`

An **expression** computes a value. In Scala, `if` and a `{ ... }` code block can produce values too:

```scala
val message = if (age >= 18) "adult" else "minor"
```

Contrast an expression with a **side effect**, which changes something outside the expression or interacts with the world (for example, printing or reassigning a `var`). The file also demonstrates why loops that mutate variables are less idiomatic Scala; Part 1 introduces recursion as an alternative.

### 3. Strings and interpolation — `StringOps.scala`

Strings contain text. Methods such as `.length`, `.substring`, and `.reverse` produce information or a new string. An interpolated string inserts values into text:

```scala
val name = "Sam"
val greeting = s"Hello, $name"
```

Use `${...}` when the inserted value is an expression, for example `s"Next year: ${age + 1}"`.

### 4. Methods and recursion — `Functions.scala`, then `Recursion.scala`

A **method** has a name, parameters, and a result type. A method can call itself; this is **recursion**. Each call should move toward a stopping condition, called the **base case**.

```scala
def factorial(n: Int): Int =
  if (n <= 1) 1
  else n * factorial(n - 1)
```

`Functions.scala` introduces ordinary recursion. `Recursion.scala` shows **tail recursion**, where the recursive call is the final operation. The `@tailrec` annotation asks the compiler to check that the method can be optimized, avoiding a growing call stack.

The starter examples are for learning, not general-purpose libraries: for example, the simple factorial uses `Int` and does not validate negative input. Think about these limits when you practice.

### 5. Default arguments and evaluation — `DefaultArgs.scala`, `CBNvsCBV.scala`

Default arguments let callers omit a parameter when a sensible default is available. Named arguments let callers make the value they are setting clear. The final example compares **call-by-value** (evaluate an argument before calling the method) with **call-by-name** (evaluate it when the parameter is used).

## Study routine

For each file:

1. Read the source from top to bottom and write down any unfamiliar symbol.
2. Run it with `sbt "runMain lectures.part1basics.FileName"`.
3. Predict the output of one expression before running it.
4. Change an input or expression, then run it again.
5. Explain what happens in plain language without reading the comments.

## Practice before moving on

- Write a method that takes a name and age and returns a greeting.
- Write a recursive sum from `1` through `n`; identify its base case.
- Rewrite the sum using tail recursion and add `@tailrec`.
- Change a `val` to `var` in a tiny example, then explain why reassignment works in one version and not the other.
- In `CBNvsCBV.scala`, use a counter or timestamp to observe when a by-name argument is evaluated.

## Interview checkpoints

Try to answer aloud, then use the source files to check yourself:

- What is the difference between `val` and `var`?
- What is a type, and when can Scala infer one?
- How is an expression different from a statement or side effect?
- What are the base case and recursive case?
- What makes a recursive method tail-recursive, and what does `@tailrec` verify?
- What is the difference between call-by-value and call-by-name?

Move on when you can trace a small method call by hand, identify its input and output types, and explain how it reaches its base case.
