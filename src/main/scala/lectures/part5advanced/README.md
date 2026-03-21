# Part 5 — Advanced Scala Concepts Missing from the Project
### Modern Scala 3 concepts added as a new learning package

---

## Why this package exists
The project already covers:
- basics,
- OOP,
- functional programming,
- pattern matching and Scala power features.

But across the **whole project**, a few important Scala 3 concepts were still missing or only lightly implied.
This package adds the most useful ones in a beginner-friendly way.

---

## Concepts added in `part5advanced`

### 1. `ExtensionMethods.scala`
**Concept:** add methods to existing types without editing their source.

Example ideas covered:
- adding `.asGreeting` to `String`
- adding `.times { ... }` to `Int`

Why it matters:
- cleaner APIs
- domain-specific syntax
- Scala 3 replacement for many old implicit-class use cases

---

### 2. `GivensAndUsings.scala`
**Concept:** contextual abstractions in Scala 3.

This file explains:
- `given` values
- `using` parameters
- `summon`
- simple type class style programming

Why it matters:
- this is one of the biggest Scala 3 features
- it replaces most classic implicit parameter patterns
- it powers dependency injection, serializers, orderings, configs, and type classes

---

### 3. `OpaqueTypes.scala`
**Concept:** create a safer domain type with zero runtime cost.

Example:
- `UserId` behaves like a distinct type even though it is backed by `Int`

Why it matters:
- prevents mixing raw primitives accidentally
- makes domain models safer
- no wrapper allocation overhead at runtime

---

### 4. `UnionAndIntersectionTypes.scala`
**Concepts:**
- **Union type** `A | B` → value can be either type
- **Intersection type** `A & B` → value must satisfy both types

Why it matters:
- more precise type modeling
- especially useful in Scala 3 APIs
- expresses intent directly in the type system

---

### 5. `EnumsAndADTs.scala`
**Concepts:**
- Scala 3 enums
- Algebraic Data Types (ADTs)
- exhaustive pattern matching on finite domains

Why it matters:
- enums are more powerful in Scala 3 than Java-style enums
- ADTs are one of the most important FP modeling tools in Scala
- they work beautifully with pattern matching

---

### 6. `Futures.scala`
**Concept:** asynchronous computations in Scala.

This file explains:
- `Future[A]`
- asynchronous execution
- `ExecutionContext`
- `onComplete`
- `map` / `flatMap` / `filter`
- combining futures with for-comprehensions

Why it matters:
- this is the standard beginner entry point to concurrency in Scala
- many real applications do I/O asynchronously
- it teaches how to model work that completes later

---

### 7. `Eithers.scala`
**Concept:** explicit success/failure modeling with values.

This file explains:
- `Either[Left, Right]`
- `Left` as failure
- `Right` as success
- `map`
- for-comprehensions on `Either`
- pattern matching and fallbacks

Why it matters:
- often better than exceptions for business/domain errors
- makes failure part of the function type
- extremely common in functional Scala code

---

### 8. `package.scala`
**Concept:** package-level helpers and shared documentation values.

Why it matters:
- demonstrates package objects
- keeps the package self-explanatory
- matches the style used in earlier parts of the project

---

# Full Explanation of Each File

---

## 1. `ExtensionMethods.scala`

### What are extension methods?
They let you attach methods to an existing type:

```scala
extension (value: String)
  def asGreeting: String = s"Hello, $value"
```

Now you can write:

```scala
"Scala".asGreeting
```

Even though `String` itself was not modified.

### Why use them?
- improve readability
- add helper methods close to the type they belong to
- create fluent APIs

### Example in the file
```scala
extension (number: Int)
  def times(action: => Unit): Unit = ...
```

Usage:
```scala
3.times {
  println("extension methods are neat")
}
```

This creates loop-like syntax in a functional style.

---

## 2. `GivensAndUsings.scala`

### What is a `given`?
A `given` provides a value automatically when the compiler needs it.

```scala
given JsonSerializer[String] with {
  def toJson(value: String): String = s"\"$value\""
}
```

### What is a `using` parameter?
A method can request a contextual value:

```scala
def convertToJson[T](value: T)(using serializer: JsonSerializer[T]): String =
  serializer.toJson(value)
```

If a matching `given` is in scope, Scala supplies it automatically.

### What is `summon`?
```scala
summon[JsonSerializer[String]]
```
It fetches the matching contextual value from scope.

### Why this concept matters
This is the foundation of:
- type classes
- pluggable behavior
- dependency-style wiring
- serializers / orderings / configs

---

## 3. `OpaqueTypes.scala`

### What is an opaque type?
An opaque type hides its underlying representation outside its defining scope.

```scala
opaque type UserId = Int
```

Inside the companion/object scope, `UserId` is treated like `Int`.
Outside it, it becomes a distinct type.

### Why is that useful?
Without opaque types:
- `UserId`, `ProductId`, and `OrderId` might all just be `Int`
- easy to mix them up accidentally

With opaque types:
- the compiler can prevent that mistake
- but runtime stays efficient

---

## 4. `UnionAndIntersectionTypes.scala`

### Union types
```scala
def stringify(value: Int | String): String = ...
```
This means the function accepts either an `Int` or a `String`.

### Intersection types
```scala
def useDevice(device: Camera & Phone): Unit = ...
```
This means the value must satisfy **both** `Camera` and `Phone`.

### Why important?
These types let you model APIs more precisely than plain inheritance alone.

---

## 5. `EnumsAndADTs.scala`

### Enums
```scala
enum PaymentMethod {
  case Cash, Card, Upi
}
```
A finite closed set of valid values.

### ADTs
An ADT is usually:
- a closed family of cases,
- each case can carry data,
- pattern matching handles all possibilities.

In the file:
```scala
enum Result[+A] {
  case Success(value: A)
  case Failure(message: String)
}
```

This is a small custom result type — similar in spirit to `Try`, `Either`, or `Option`.

Why it matters:
- models success/failure clearly
- avoids ad-hoc booleans or nulls
- pairs naturally with pattern matching

---

## 6. `Futures.scala`

### What is a `Future`?
A `Future[A]` represents a computation that is running now or will complete later and eventually produce:
- a successful value of type `A`, or
- a failure with an exception.

```scala
val aFuture: Future[Int] = Future {
  Thread.sleep(200)
  42
}
```

### Important idea
A future is about **asynchronous computation**.
You don't get the result immediately. Instead, you describe what should happen **when it completes**.

### `ExecutionContext`
```scala
given ExecutionContext = ExecutionContext.global
```
A future needs a thread pool/execution context to run on.
`ExecutionContext.global` is the standard shared pool for simple examples.

### `onComplete`
```scala
aFuture.onComplete {
  case Success(value) => println(s"Future succeeded with $value")
  case Failure(ex) => println(s"Future failed: ${ex.getMessage}")
}
```
This registers a callback that runs when the future finishes.

### Transforming futures
```scala
val doubledFuture = aFuture.map(_ * 2)
val flatMappedFuture = aFuture.flatMap(x => Future(x + 100))
val filteredFuture = aFuture.filter(_ % 2 == 0)
```
These work like other monads you already saw (`Option`, `Try`), but asynchronously.

### Combining futures
```scala
val combined = for {
  first <- firstFuture
  second <- secondFuture
} yield s"$first $second"
```
This is the cleanest way to chain asynchronous computations.

### Why this concept matters
Futures are foundational for:
- background work,
- asynchronous APIs,
- server programming,
- parallel composition.

> Note: `Await.result(...)` is used in the example only for teaching/demo purposes. In real applications, prefer staying asynchronous.

---

## 7. `Eithers.scala`

### What is `Either`?
`Either[L, R]` means a computation can produce one of two result types.
By convention:
- `Left` = error/failure
- `Right` = success

```scala
def safeDivide(a: Int, b: Int): Either[String, Int] =
  if (b == 0) Left("division by zero")
  else Right(a / b)
```

### Why use `Either`?
Unlike exceptions, `Either` makes failure visible in the method signature.
That means callers must consciously deal with both success and failure.

### Right-biased API
In modern Scala, `Either` is **right-biased**, which means `map` and `flatMap` operate on the `Right` value:

```scala
val success: Either[String, Int] = Right(42)
val failure: Either[String, Int] = Left("something went wrong")

success.map(_ * 2)   // Right(84)
failure.map(_ * 2)   // Left("something went wrong")
```

### Chaining with for-comprehensions
```scala
val chained = for {
  a <- safeDivide(20, 2)
  b <- safeDivide(a, 5)
} yield b
```
If any step returns `Left`, the whole chain stops there.

### Pattern matching on `Either`
```scala
safeDivide(10, 0) match {
  case Left(error) => s"Computation failed: $error"
  case Right(value) => s"Computation succeeded: $value"
}
```

### Fallbacks
```scala
safeDivide(10, 0).getOrElse(-1)
```
This lets you provide a default if the result is `Left`.

### Why this concept matters
`Either` is excellent for:
- validation,
- parsing,
- domain/business rules,
- APIs where errors should be typed rather than thrown.

---

## 8. `package.scala`

This package object gives package-wide helpers:

```scala
package object part5advanced {
  val packageGoal: String = ...
  def whyPart5: String = ...
}
```

This is mostly educational here, but useful in real projects too.

---

# Big Picture — How Part 5 Fits the Project

```text
part1basics     → syntax, functions, recursion, strings
part2oop        → classes, traits, objects, generics, packaging
part3fp         → functions, options, try, collections, HOFs
part4power      → pattern matching, extractors, partial functions, Scala 3 syntax
part5advanced   → extension methods, givens/usings, opaque types,
                  union/intersection types, enums as ADTs,
                  futures, either
```

So `part5advanced` acts like the next natural step after the current project.

---

# Quick Summary Table

| File | Main idea |
|---|---|
| `ExtensionMethods.scala` | Add methods to existing types |
| `GivensAndUsings.scala` | Scala 3 contextual abstractions |
| `OpaqueTypes.scala` | Safer domain types with zero runtime overhead |
| `UnionAndIntersectionTypes.scala` | More precise Scala 3 type modeling |
| `EnumsAndADTs.scala` | Finite domain modeling and custom result types |
| `Futures.scala` | Asynchronous computation and callbacks/composition |
| `Eithers.scala` | Typed success/failure modeling without exceptions |
| `package.scala` | Package-level shared helpers |

---

# Suggested Learning Order
1. `ExtensionMethods.scala`
2. `GivensAndUsings.scala`
3. `OpaqueTypes.scala`
4. `UnionAndIntersectionTypes.scala`
5. `EnumsAndADTs.scala`
6. `Eithers.scala`
7. `Futures.scala`
8. `package.scala`

---

# Final takeaway
If parts 1–4 teach you **how to write Scala**, then this package starts teaching you how to write **modern Scala 3** — including typed errors and asynchronous programming.
