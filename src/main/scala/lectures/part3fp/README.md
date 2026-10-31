# Part 3 — Functional Programming in Scala
### All Concepts Explained from the Ground Up

This part shows how Scala code can transform values instead of repeatedly changing state. You should know Part 1 methods and basic Part 2 classes. Read each section alongside its matching source file, and run examples using `sbt "runMain lectures.part3fp.FileName"`.

For every operation, say what goes in, what comes out, and whether it changes the original collection. This input-to-output habit is central to understanding functional code and explaining it clearly in interviews.

---

# 🧠 1. WhatsAFunction.scala — Functions as First-Class Citizens

## The Core Problem
Scala runs on the JVM which was built for **Object-Oriented** programming.
But Functional Programming wants **functions as values** — pass them around, store them in variables, return them from other functions.
How does Scala solve this? By making **functions objects**.

## Under the Hood — `MyFunction` trait
```scala
trait MyFunction[A, B] {
  def apply(element: A): B
}

val doubler = new MyFunction[Int, Int] {
  override def apply(element: Int): Int = element * 2
}

println(doubler(2))   // → 4
// doubler(2) is actually doubler.apply(2) !!
```
Calling `doubler(2)` actually calls `doubler.apply(2)` — it's just an object with an `apply` method.

## Built-in Function Types
Scala ships with `Function1`, `Function2`, ..., `Function22`:
```scala
val stringToInt = new Function1[String, Int] {
  override def apply(string: String): Int = string.toInt
}

val adder: (Int, Int) => Int = new Function2[Int, Int, Int] {
  override def apply(a: Int, b: Int): Int = a + b
}
// Function2[A, B, R]  ===  (A, B) => R
```

## Functions Returning Functions (Currying Intro)
```scala
val superAdder: Function1[Int, Function1[Int, Int]] = new Function1[Int, Function1[Int, Int]] {
  override def apply(x: Int): Function1[Int, Int] = new Function1[Int, Int] {
    override def apply(y: Int): Int = x + y
  }
}

val adder3 = superAdder(3)   // returns a function: y => 3 + y
println(adder3(4))           // → 7
println(superAdder(3)(4))    // → 7  (curried style)
```

> **Key takeaway**: ALL Scala functions are objects with an `apply` method.

---

# 🏹 2. AnonymousFunctions.scala — Lambdas

## What is a Lambda?
A shorter, cleaner way to write function objects — without `new FunctionX { override def apply... }`.

```scala
// Old verbose way:
val doubler = new Function1[Int, Int] {
  override def apply(x: Int): Int = x * 2
}

// Lambda (anonymous function):
val doubler: Int => Int = (x: Int) => x * 2
```

## Lambda Syntax Variations
```scala
// Single param
val doubler: Int => Int = (x: Int) => x * 2

// Multiple params
val adder: (Int, Int) => Int = (a: Int, b: Int) => a + b

// No params
val justDoSomething: () => Int = () => 3

// Multi-line with curly braces
val stringToInt = { (str: String) =>
  str.toInt
}
```

## ⚠️ Calling vs Referencing
```scala
println(justDoSomething)    // prints the function object itself
println(justDoSomething())  // calls the function, prints 3
```

## Underscore Syntax — Ultimate Shorthand
```scala
val niceIncrementer: Int => Int     = _ + 1    // same as x => x + 1
val niceAdder: (Int, Int) => Int    = _ + _    // same as (a, b) => a + b
//                                     ^   ^
//                              1st param  2nd param (each _ is a different param)
```

## Lambda Returning a Lambda
```scala
val superAdd = (x: Int) => (y: Int) => x + y
println(superAdd(3)(4))   // → 7
```

---

# 🔧 3. HOFsCurries.scala — Higher Order Functions & Currying

## What is a Higher Order Function (HOF)?
A function that **takes a function as parameter** OR **returns a function** (or both).

```scala
// Takes a function AND returns a function = HOF
val superFunction: (Int, (String, (Int => Boolean)) => Int) => (Int => Int) = null
```

## Example: `nTimes` — Apply a function N times
```scala
def nTimes(f: Int => Int, n: Int, x: Int): Int =
  if (n <= 0) x
  else nTimes(f, n - 1, f(x))

val plusOne = (x: Int) => x + 1
println(nTimes(plusOne, 10, 1))   // → 11 (adds 1, ten times, starting from 1)
```

## Better Version: Return the composed function
```scala
def nTimesBetter(f: Int => Int, n: Int): (Int => Int) =
  if (n <= 0) (x: Int) => x          // identity function
  else (x: Int) => nTimesBetter(f, n - 1)(f(x))

val plus10 = nTimesBetter(plusOne, 10)
println(plus10(1))   // → 11
```
Now `nTimesBetter` **returns a function** — it's reusable without re-specifying the starting value.

## Currying
A **curried function** takes arguments **one at a time**, returning a new function for each.
```scala
val superAdder: Int => (Int => Int) = (x: Int) => (y: Int) => x + y

val add3 = superAdder(3)    // "locks in" x = 3, returns y => 3 + y
println(add3(10))           // → 13
println(superAdder(3)(10))  // → 13
```

## Curried Methods with Multiple Parameter Lists
```scala
def curriedFormatter(c: String)(x: Double): String = c.format(x)

// "partially apply" the formatter — fix the format string
val standardFormat: Double => String = curriedFormatter("%4.2f")
val preciseFormat:  Double => String = curriedFormatter("%10.8f")

println(standardFormat(Math.PI))  // → 3.14
println(preciseFormat(Math.PI))   // → 3.14159265
```

## `toCurry` and `fromCurry`
```scala
// Convert a 2-param function to a curried function
def toCurry(f: (Int, Int) => Int): (Int => Int => Int) =
  x => y => f(x, y)

// Convert a curried function back to 2-param
def fromCurry(f: (Int => Int => Int)): (Int, Int) => Int =
  (x, y) => f(x)(y)
```

## `compose` and `andThen` — Function Chaining
```scala
def compose[A, B, T](f: A => B, g: T => A): T => B = x => f(g(x))
// compose(f, g)(x) = f(g(x))  — g runs FIRST, then f

def andThen[A, B, C](f: A => B, g: B => C): A => C = x => g(f(x))
// andThen(f, g)(x) = g(f(x))  — f runs FIRST, then g

val add2   = (x: Int) => x + 2
val times3 = (x: Int) => x * 3

val composed = compose(add2, times3)   // x => add2(times3(x)) = (x*3)+2
val ordered  = andThen(add2, times3)   // x => times3(add2(x)) = (x+2)*3

println(composed(4))  // → 14   (4*3=12, 12+2=14)
println(ordered(4))   // → 18   (4+2=6,  6*3=18)
```

---

# 🗂️ 4. MapFlatmapFilterFor.scala — The Core Collection Operations

## Setup
```scala
val list = List(1, 2, 3)
```

## `map` — Transform every element
```scala
list.map(_ + 1)              // → List(2, 3, 4)
list.map(_ + " is a number") // → List("1 is a number", "2 is a number", "3 is a number")
```
Applies a function to **each element**, returns a new list of the results.

## `filter` — Keep only matching elements
```scala
list.filter(_ % 2 == 0)   // → List(2)
```
Keeps only elements where the predicate returns `true`.

## `flatMap` — Transform and flatten
```scala
val toPair = (x: Int) => List(x, x + 1)
list.flatMap(toPair)   // → List(1, 2, 2, 3, 3, 4)
```
Like `map`, but each element produces a **list**, and all lists are **flattened** into one.

## `foreach` — Side effects, no return value
```scala
list.foreach(println)   // prints 1, 2, 3 — returns Unit
```

## Combining: All Combinations of Two Lists
```scala
val numbers = List(1, 2, 3, 4)
val chars   = List('a', 'b', 'c', 'd')
val colors  = List("black", "white")

val combinations = numbers
  .filter(_ % 2 == 0)
  .flatMap(n => chars.flatMap(c => colors.map(color => s"$c$n-$color")))
// → List(a2-black, a2-white, b2-black, ...)
```

## For-Comprehensions — Readable version of flatMap/map/filter
```scala
val forCombinations = for {
  n     <- numbers if n % 2 == 0    // filter
  c     <- chars
  color <- colors
} yield s"$c$n-$color"
```
This compiles to **exactly the same bytecode** as the `flatMap`/`map`/`filter` chain above.
It's just syntactic sugar — cleaner to read.

### Without `yield` — for side effects
```scala
for {
  n <- numbers
} println(n)
// equivalent to numbers.foreach(println)
```

---

# ❓ 5. Options.scala — Safe Null Handling

## The Problem with `null`
```scala
def unsafeMethod(): String = null
val s = unsafeMethod()
println(s.length)   // 💥 NullPointerException!
```

## `Option` — A container that may or may not hold a value
```scala
val myFirstOption: Option[Int] = Some(4)   // holds a value
val noOption:      Option[Int] = None      // holds nothing
```

## Safe Wrapping
```scala
val result = Option(unsafeMethod())   // → None  (wraps null safely as None)
val result2 = Option("hello")         // → Some("hello")
```
Never use `Some(null)` — always use `Option(...)` when wrapping potentially null values.

## `orElse` — Fallback chaining
```scala
def backupMethod(): String = "A valid result"

val chainedResult = Option(unsafeMethod()).orElse(Option(backupMethod()))
// → Some("A valid result")
```

## `map`, `filter`, `flatMap` on Option
```scala
val opt = Some(4)

opt.map(_ * 2)                     // → Some(8)
opt.filter(x => x > 10)           // → None  (4 is not > 10)
opt.flatMap(x => Option(x * 10))  // → Some(40)
```
These operations are **safe** — they do nothing if the Option is `None`, no NPE.

## Real World Example — Chained safe operations
```scala
val config = Map("host" -> "176.45.36.1", "port" -> "80")

// flatMap style
val connectionStatus = config.get("host")
  .flatMap(host => config.get("port")
    .flatMap(port => Connection(host, port))
    .map(conn => conn.connect))

// for-comprehension style (cleaner!)
val status = for {
  host       <- config.get("host")
  port       <- config.get("port")
  connection <- Connection(host, port)
} yield connection.connect

status.foreach(println)
```
Each step is an `Option` — if any step is `None`, the whole chain short-circuits to `None`. No null checks needed.

## ⚠️ What NOT to do
```scala
myFirstOption.get   // UNSAFE — throws NoSuchElementException if None
```
Use `map`/`flatMap`/`getOrElse`/`orElse` instead.

---

# 🛡️ 6. HandlingFailure.scala — Try, Success, Failure

## The Problem
Exceptions break the flow of functional code. We want to treat failure **as a value**.

## `Try` — A container that holds either a result or an exception
```scala
import scala.util.{Try, Success, Failure}

val aSuccess = Success(3)
val aFailure = Failure(new RuntimeException("SUPER FAILURE"))

println(aSuccess)   // → Success(3)
println(aFailure)   // → Failure(java.lang.RuntimeException: SUPER FAILURE)
```

## Wrapping risky code with `Try`
```scala
def unsafeMethod(): String = throw new RuntimeException("NO STRING FOR YOU")

val potentialFailure = Try(unsafeMethod())
// → Failure(java.lang.RuntimeException: NO STRING FOR YOU)
// No crash! The exception is captured inside Failure.

// Multi-line version
val result = Try {
  // code that might throw
  unsafeMethod()
}
```

## `orElse` — Fallback
```scala
def backupMethod(): String = "A valid result"

val fallback = Try(unsafeMethod()).orElse(Try(backupMethod()))
// → Success("A valid result")
```

## `map`, `flatMap`, `filter` on Try
```scala
val s = Success(3)

s.map(_ * 2)               // → Success(6)
s.flatMap(x => Success(x * 10))  // → Success(30)
s.filter(_ > 10)           // → Failure(NoSuchElementException)
```
If the `Try` is a `Failure`, all these operations pass the failure through untouched.

## Real World: HTTP Connection Example
```scala
// Messy imperative way (nested try/catch):
try {
  val connection = HttpService.getConnection(host, port)
  try {
    val page = connection.get("/home")
    renderHTML(page)
  } catch { ... }
} catch { ... }

// Clean functional way with Try:
HttpService.getSafeConnection(host, port)
  .flatMap(connection => connection.getSafe("/home"))
  .foreach(renderHTML)
```

## `Try` vs `Option`

| | `Option[A]` | `Try[A]` |
|---|---|---|
| Success case | `Some(value)` | `Success(value)` |
| Failure case | `None` | `Failure(exception)` |
| Why failed? | No info | Exception with message |
| Use when | Value might be absent | Code might throw |

---

# 📋 7. Sequences.scala — Seq, List, Array, Vector

## `Seq` — The Base Trait
```scala
val aSequence = Seq(1, 3, 2, 4)

aSequence.reverse    // → Seq(4, 2, 3, 1)
aSequence(2)         // → 2  (access by index)
aSequence ++ Seq(7, 5, 6)  // → Seq(1, 3, 2, 4, 7, 5, 6)
aSequence.sorted     // → Seq(1, 2, 3, 4)
```
`Seq` is the common interface for ordered collections.

## Ranges
```scala
val r: Seq[Int] = 1 until 10   // 1, 2, ..., 9 (10 excluded)
val r2          = 1 to 10      // 1, 2, ..., 10 (10 included)

(1 to 10).foreach(x => println("Hello"))   // prints "Hello" 10 times
```

## `List` — Immutable Linked List
```scala
val aList = List(1, 2, 3)

42 +: aList :+ 89   // → List(42, 1, 2, 3, 89)
//  ^^           ^^
// prepend      append

List.fill(5)("apple")     // → List(apple, apple, apple, apple, apple)
aList.mkString("-|-")     // → "1-|-2-|-3"
```
- **Optimised for**: prepending (`+:`), head/tail access.
- **Slow for**: random access by index, appending.

## `Array` — Mutable, Java-backed
```scala
val numbers = Array(1, 2, 3, 4)
val empty3  = Array.ofDim[String](3)   // Array(null, null, null)

numbers(2) = 0   // mutation! (sugar for numbers.update(2, 0))
println(numbers.mkString(" "))   // → 1 2 0 4

// Arrays implicitly convert to Seq
val numbersSeq: Seq[Int] = numbers
```
- **Mutable** (unlike List/Vector).
- Fixed size, backed by JVM arrays — very fast random access.

## `Vector` — Immutable, Fast Random Access
```scala
val vector: Vector[Int] = Vector(1, 2, 3)
```
- **Immutable** like List, but **fast random access** (O(log₃₂ n)).
- Stored as a tree of 32-element chunks.

## List vs Vector Performance
```scala
// List: slow for random updates (must traverse links)
// Vector: fast for random updates (replace one 32-element chunk)
println(getWriteTime(numbersList))    // → much slower
println(getWriteTime(numbersVector))  // → much faster
```

| | List | Vector |
|---|---|---|
| Prepend | ✅ O(1) | 🟡 O(log n) |
| Random access/update | ❌ O(n) | ✅ O(log n) |
| Memory | Linked nodes | Chunked tree |
| Use when | Prepend-heavy | General purpose |

---

# 🗺️ 8. TuplesAndMaps.scala — Tuples & Maps

## Tuples — Fixed-size heterogeneous containers
```scala
val aTuple = (2, "hello, Scala")   // Tuple2[Int, String]

aTuple._1                            // → 2
aTuple._2                            // → "hello, Scala"
aTuple.copy(_2 = "goodbye Java")    // → (2, "goodbye Java")
aTuple.swap                          // → ("hello, Scala", 2)
```
Tuples hold a **fixed number** of elements of **different types** — useful for returning multiple values.

## Maps — Key → Value pairs
```scala
val phonebook = Map(
  ("Jim", 555),
  "Daniel" -> 789,    // same as ("Daniel", 789) — syntactic sugar
  ("JIM", 9000)
).withDefaultValue(-1)

phonebook.contains("Jim")   // → true
phonebook("Mary")           // → -1  (uses default value, no exception)
```

## Adding entries (immutable — returns new map)
```scala
val newPairing  = "Mary" -> 678
val newPhonebook = phonebook + newPairing
```

## Functional Operations on Maps
```scala
// mapValues — transform all values
phonebook.view.mapValues(number => "0245-" + number).toMap
// → Map(Jim -> 0245-555, Daniel -> 0245-789, ...)

// filterKeys — keep only some keys
phonebook.view.filterKeys(_.startsWith("J")).toMap
// → Map(Jim -> 555, JIM -> 9000)

// toList — convert to list of pairs
phonebook.toList   // → List((Jim,555), (Daniel,789), ...)

// groupBy — group list elements into a map
val names = List("Bob", "James", "Angela", "Mary")
names.groupBy(name => name.charAt(0))
// → Map(B -> List(Bob), J -> List(James), A -> List(Angela), M -> List(Mary))
```

## Social Network Exercise — Map + Set
Demonstrates building a full social network with just `Map[String, Set[String]]`:

```scala
// Each person maps to their set of friends
def add(network: Map[String, Set[String]], person: String) =
  network + (person -> Set())

def friend(network: Map[String, Set[String]], a: String, b: String) = {
  val friendsA = network(a)
  val friendsB = network(b)
  network + (a -> (friendsA + b)) + (b -> (friendsB + a))   // mutual friendship
}

def unfriend(...)   // removes from both sides
def remove(...)     // unfriends everyone, then removes person

def mostFriends(network: Map[String, Set[String]]): String =
  network.maxBy(pair => pair._2.size)._1

def nPeopleWithNoFriends(network: Map[String, Set[String]]): Int =
  network.count(_._2.isEmpty)
```

### BFS — Social Connection (are two people connected?)
```scala
def socialConnection(network: Map[String, Set[String]], a: String, b: String): Boolean = {
  @tailrec
  def bfs(target: String, visited: Set[String], toVisit: Set[String]): Boolean = {
    if (toVisit.isEmpty) false
    else {
      val person = toVisit.head
      if (person == target) true
      else if (visited.contains(person)) bfs(target, visited, toVisit.tail)
      else bfs(target, visited + person, toVisit.tail ++ network(person))
    }
  }
  bfs(b, Set(), network(a) + a)
}
```
Tail-recursive **Breadth-First Search** using only immutable collections.

---

# 🗺️ Big Picture — How They All Connect

```
WhatsAFunction      → Functions are objects with apply()
       ↓
AnonymousFunctions  → Lambdas: shorter syntax for function objects
       ↓
HOFsCurries         → Pass/return functions; currying; compose/andThen
       ↓
MapFlatmapFilterFor → Core operations on collections: map/flatMap/filter/for
       ↓
Options             → Option[T]: safe null handling with map/flatMap
       ↓
HandlingFailure     → Try[T]: safe exception handling with map/flatMap
       ↓
Sequences           → Seq, List, Array, Vector — performance trade-offs
       ↓
TuplesAndMaps       → Tuples for grouping; Maps for key-value data
```

---

# 💡 One-Line Summaries

| File | Core Idea |
|---|---|
| `WhatsAFunction` | Functions are objects; `apply()` makes them callable |
| `AnonymousFunctions` | Lambdas `(x) => x * 2` are shorthand for function objects |
| `HOFsCurries` | HOFs take/return functions; currying fixes arguments one at a time |
| `MapFlatmapFilterFor` | Transform(map), flatten(flatMap), keep(filter) — for-comprehension sugar |
| `Options` | `Option[T]` replaces null — chain safely with map/flatMap/orElse |
| `HandlingFailure` | `Try[T]` wraps exceptions as values — `Success` or `Failure` |
| `Sequences` | List=linked, Array=mutable, Vector=fast random access |
| `TuplesAndMaps` | Tuples=fixed heterogeneous groups; Maps=key→value with functional ops |

## Practice and interview checkpoints

- Given a list of numbers, produce the squares of the even numbers. First use `filter` and `map`, then express the same transformation as a `for` comprehension.
- Use `Option` to safely read a missing map key and transform its value without calling `.get`.
- Explain with a small example why `flatMap` is useful when each input can produce zero or many outputs.
- Write a function that takes another function as an argument. Identify its input and output types.
- For a data structure with frequent random access, compare the trade-offs of `List` and `Vector`.
- Explain when `Try` is useful and how it differs from `Option`.

Interview checkpoints: What makes a function higher-order? What is the difference between `map` and `flatMap`? How does `Option` avoid null checks? What does a for-comprehension translate into? Which operations return a new immutable collection, and which can perform side effects?

Before moving to Part 4, be able to trace a short `map`/`flatMap` chain and explain where an absent value or failure goes.
