# Part 4 — Power of Scala (Pattern Matching, Extractors, Partial Functions & Braceless Syntax)
### All Concepts Explained from the Ground Up

---

# 🎯 1. PatternMatching.scala — Switch on Steroids

## What is Pattern Matching?
Pattern matching is like a `switch` statement, but **far more powerful** — it can match on values,
types, structure, and conditions all at once. It is an **expression** (returns a value).

```scala
val x = random.nextInt(10)

val description = x match {
  case 1 => "the ONE"
  case 2 => "double or nothing"
  case 3 => "third time is the charm"
  case _ => "something else"   // _ = wildcard, matches everything
}
```

## Rule 1 — Cases are matched in ORDER (top to bottom)
The first matching case wins. Put more specific cases before general ones.

## Rule 2 — No match = `MatchError` at runtime
Always include a wildcard `case _ =>` unless you're sure all cases are covered (e.g. sealed classes).

## Rule 3 — PM is an expression, not a statement
It **returns a value** — the result of the matched case. The type is the **unified (common) type** of all cases.

## Rule 4 — PM works best with Case Classes
```scala
case class Person(name: String, age: Int)
val bob = Person("Bob", 20)

val greeting = bob match {
  case Person(n, a) if a < 21 => s"Hi, I'm $n and I can't drink in the US"
  case Person(n, a)            => s"Hi, I'm $n and I am $a years old"
  case _                       => "I don't know who I am"
}
```
- **Destructuring**: `Person(n, a)` extracts the fields right in the pattern.
- **Guard** (`if a < 21`): adds a condition on top of the pattern.

## PM on Sealed Hierarchies
```scala
sealed class Animal
case class Dog(breed: String) extends Animal
case class Parrot(greeting: String) extends Animal

val animal: Animal = Dog("Terra Nova")
animal match {
  case Dog(someBreed) => println(s"Matched a dog of the $someBreed breed")
}
```
With `sealed`, the compiler **warns you** if you miss a case — great safety guarantee.

## When NOT to use PM
```scala
// Overkill — use simple arithmetic instead:
val isEven = x match {
  case n if n % 2 == 0 => true
  case _ => false
}
val isEven = x % 2 == 0   // ← much better!
```

## Exercise: Expression Printer
```scala
trait Expr
case class Number(n: Int)          extends Expr
case class Sum(e1: Expr, e2: Expr) extends Expr
case class Prod(e1: Expr, e2: Expr) extends Expr

def show(e: Expr): String = e match {
  case Number(n)    => s"$n"
  case Sum(e1, e2)  => show(e1) + " + " + show(e2)
  case Prod(e1, e2) =>
    def maybeParens(exp: Expr) = exp match {
      case Prod(_, _) => show(exp)
      case Number(_)  => show(exp)
      case _          => "(" + show(exp) + ")"   // Sum inside Prod needs parens
    }
    maybeParens(e1) + " * " + maybeParens(e2)
}

show(Sum(Number(2), Number(3)))                          // → "2 + 3"
show(Prod(Sum(Number(2), Number(1)), Number(3)))         // → "(2 + 1) * 3"
show(Sum(Prod(Number(2), Number(1)), Number(3)))         // → "2 * 1 + 3"
```
Recursive pattern matching — each branch calls `show` again for nested expressions.

---

# 🔍 2. AllThePatterns.scala — Every Pattern You Can Use

## Pattern 1 — Constants
```scala
val x: Any = "Scala"
x match {
  case 1             => "a number"
  case "Scala"       => "THE Scala"
  case true          => "The Truth"
  case AllThePatterns => "A singleton object"   // matches a specific object
}
```
Match against exact literal values or singleton objects.

## Pattern 2a — Wildcard `_`
```scala
x match {
  case _ =>   // matches ANYTHING, binds to nothing
}
```

## Pattern 2b — Variable Pattern
```scala
x match {
  case something => s"I've found $something"   // matches anything AND names it
}
```
Unlike `_`, a variable pattern **binds** the matched value to a name you can use.

## Pattern 3 — Tuples (and Nested Tuples)
```scala
val aTuple = (1, 2)
aTuple match {
  case (1, 1)         =>
  case (something, 2) => s"I've found $something"
}

val nestedTuple = (1, (2, 3))
nestedTuple match {
  case (_, (2, v)) =>   // patterns can be nested arbitrarily deep!
}
```

## Pattern 4 — Case Class Constructor Pattern
```scala
val aList: MyList[Int] = Cons(1, Cons(2, Empty))
aList match {
  case Empty                           =>
  case Cons(head, Cons(subhead, tail)) =>   // nested destructuring
}
```
Case classes expose their constructor arguments for matching and destructuring.

## Pattern 5 — List Patterns
```scala
val list = List(1, 2, 3, 42)
list match {
  case List(1, _, _, _)  =>          // exactly 4 elements, first is 1
  case List(1, _*)       =>          // starts with 1, any length (vararg)
  case 1 :: List(_)      =>          // infix pattern: head :: tail
  case List(1, 2, _) :+ 42 => "lala" // infix: init :+ last
}
```

## Pattern 6 — Type Specifiers
```scala
val unknown: Any = 2
unknown match {
  case list: List[Int] =>   // matches if unknown IS a List[Int]
  case _ =>
}
```
Check and cast to a type in one step.

## Pattern 7 — Name Binding with `@`
```scala
aList match {
  case nonEmptyList @ Cons(_, _) =>
  // nonEmptyList is now bound to the whole matched value AND we destructured it

  case Cons(1, rest @ Cons(2, _)) =>
  // rest is bound to the inner Cons(2, _) sub-structure
}
```
`@` lets you **name** a matched sub-pattern so you can reference the whole thing.

## Pattern 8 — Multi-Patterns (OR)
```scala
aList match {
  case Empty | Cons(0, _) =>   // matches Empty OR a list starting with 0
}

anything match {
  case _: RuntimeException | _: NullPointerException => "some exception"
}
```

## Pattern 9 — If Guards
```scala
aList match {
  case Cons(_, Cons(specialElement, _)) if specialElement % 2 == 0 =>
  // only matches if the second element is even
}
```
Add a boolean condition on top of any structural pattern.

## ⚠️ JVM Trick — Type Erasure
```scala
val numbers = List(1, 2, 3)
numbers match {
  case listOfStrings: List[String] => "a list of strings"
  case listOfNumbers: List[Int]    => "a list of numbers"
  case _ => ""
}
// → "a list of strings" !!
```
At runtime the JVM erases generic type parameters — `List[String]` and `List[Int]` are both just `List`.
The **first case always wins** because both look the same to the JVM. This is a known pitfall.

---

# 🧩 3. CustomExtractors.scala — `unapply` and Custom Pattern Matching

## Why do we need custom extractors?
Case classes work great with pattern matching because Scala automatically knows how to deconstruct them.
But what if:
- you don't own the class,
- the class is not a case class,
- or you want a custom matching view?

That's where **extractors** come in.

## `unapply` — teach Scala how to deconstruct a value
```scala
class Person(val name: String, val age: Int)

object Person {
  def unapply(person: Person): Option[(String, Int)] =
    if (person == null) None
    else Some((person.name, person.age))
}
```
Now this works even though `Person` is a regular class:
```scala
val bob = new Person("Bob", 25)

val greeting = bob match {
  case Person(name, age) => s"Hi, my name is $name and I am $age years old"
  case _ => "Unknown person"
}
```

### Why `Option[(A, B)]`?
- `Some(...)` means the pattern **matched** and extracted values.
- `None` means the pattern **did not match**.

## Extracting from another type
You can pattern match on an `Int` too — not just on objects:
```scala
object PersonStatus {
  def unapply(age: Int): Option[String] =
    Some(if (age < 21) "minor" else "major")
}

bob.age match {
  case PersonStatus(status) => s"My legal status is $status"
}
```
Here the extractor turns an `Int` into a more meaningful label.

## Boolean extractors
Sometimes you don't want to extract values — you just want a yes/no match:
```scala
object even {
  def unapply(n: Int): Boolean = n % 2 == 0
}

object singleDigit {
  def unapply(n: Int): Boolean = n > -10 && n < 10
}

val n = 8
n match {
  case singleDigit() => "single digit"
  case even() => "an even number"
  case _ => "no special property"
}
```

### Mental model
- `unapply(value): Option[...]` → **match + extract**
- `unapply(value): Boolean` → **match only**

---

# ⚡ 4. PartialFunctions.scala — Functions Defined Only for Some Inputs

## What is a partial function?
A **total function** works for every valid input of its type.
A **partial function** only works for **some** inputs.

```scala
val aPartialFunction: PartialFunction[Int, Int] = {
  case 1 => 42
  case 2 => 56
  case 5 => 999
}
```
This function knows what to do with `1`, `2`, and `5` — but not with other numbers.

## Calling a partial function
```scala
aPartialFunction(2)   // → 56
// aPartialFunction(67) would throw MatchError
```

## `isDefinedAt`
```scala
aPartialFunction.isDefinedAt(67)   // → false
```
Use this to check whether a value is supported.

## `lift` — convert a partial function into a total function
```scala
val lifted: Int => Option[Int] = aPartialFunction.lift

lifted(2)   // → Some(56)
lifted(98)  // → None
```
Now instead of crashing, it safely returns `Option`.

## `orElse` — chain partial functions
```scala
val pfChain = aPartialFunction.orElse[Int, Int] {
  case 45 => 67
}

pfChain(45)   // → 67
```
This creates a bigger partial function by combining smaller ones.

## Partial functions in collections
```scala
List(1, 2, 3).map {
  case 1 => "the one"
  case 2 => "double"
  case 3 => "third"
}
```
A block of `case` statements passed directly like this is a **partial function literal**.

## Why partial functions matter
They are heavily used in:
- pattern matching,
- actor/message handlers,
- collections,
- event dispatching,
- mini interpreters/chatbots.

---

# 📦 5. package.scala — Package-Level Helpers

## What is a package object?
A `package object` lets you define values and methods that belong to the whole package.
Everything inside it is available to files in the same package without extra imports.

```scala
package lectures

package object part4power {
  val patternMatchingReminder: String =
    "Pattern matching is an expression that can deconstruct values."

  def explainWildcardPattern: String =
    "case _ => matches anything when you do not need to bind the value"
}
```

## Why use it?
Use a package object when you want:
- shared constants,
- shared helper methods,
- package-wide aliases or defaults.

This package object is small on purpose — it demonstrates the concept clearly.

---

# 🌍 6. PatternsEverywhere.scala — PM Is Hiding Everywhere

## Big Idea 1 — `catch` blocks ARE pattern matching
```scala
try {
  // code
} catch {
  case e: RuntimeException    => "runtime"
  case npe: NullPointerException => "npe"
  case _ => "something else"
}
```
This compiles to exactly:
```scala
catch(e) {
  e match {
    case e: RuntimeException    => "runtime"
    case npe: NullPointerException => "npe"
    case _ => "something else"
  }
}
```
You've been using pattern matching in every `try/catch` all along!

## Big Idea 2 — `for` generators ARE pattern matching
```scala
val tuples = List((1, 2), (3, 4))

val result = for {
  (first, second) <- tuples    // ← this is a pattern!
} yield first * second
// → List(2, 12)
```
The `<-` generator uses pattern matching to destructure each element. Works with any pattern:
```scala
for {
  x <- list if x % 2 == 0   // guard = case guard in PM
} yield 10 * x
```

## Big Idea 3 — `val` definitions ARE pattern matching
```scala
val tuple = (1, 2, 3)
val (a, b, c) = tuple    // destructures tuple into 3 vals
println(b)               // → 2

val head :: tail = List(1, 2, 3)
println(head)   // → 1
println(tail)   // → List(2, 3)
```
You can destructure **any pattern** on the left side of a `val`. This is pattern matching!

## Big Idea 4 — Partial Functions ARE pattern matching
```scala
val mappedList = list.map {
  case v if v % 2 == 0 => s"$v is even"
  case 1               => "the one"
  case _               => "something else"
}
// equivalent to:
val mappedList2 = list.map { x => x match {
  case v if v % 2 == 0 => s"$v is even"
  case 1               => "the one"
  case _               => "something else"
}}
```
A block of `case` clauses passed directly to a function is a **partial function literal**.
Scala automatically wraps it — it's still pattern matching under the hood.

---

# 📐 7. BracelessSyntax.scala — Scala 3 Indentation-Based Syntax

## What is Braceless Syntax?
Scala 3 introduced **significant indentation** (like Python) as an alternative to `{}` braces.
Both styles work — they are 100% equivalent.

## `if` Expressions
```scala
// Traditional (Scala 2 & 3)
val v1 = if (2 > 3) "bigger" else "smaller"

val v2 =
  if (2 > 3) {
    "bigger"
  } else {
    "smaller"
  }

// Scala 3 braceless
val v3 =
  if 2 > 3 then       // no parentheses needed around condition
    "bigger"          // body indented more than `if`
  else
    "smaller"

// Scala 3 with multiple statements per branch
val v4 =
  if 2 > 3 then
    val result = "bigger"
    result
  else
    val result = "smaller"
    result

// Scala 3 one-liner
val v5 = if 2 > 3 then "bigger" else "smaller"
```

## `for` Comprehensions
```scala
// Traditional
val r1 = for {
  n <- List(1, 2, 3)
  s <- List("black", "white")
} yield s"$n$s"

// Scala 3 braceless
val r2 =
  for
    n <- List(1, 2, 3)
    s <- List("black", "white")
  yield s"$n$s"
```

## Pattern Matching
```scala
// Traditional
val r1 = x match {
  case 1 => "the one"
  case 2 => "double"
  case _ => "other"
}

// Scala 3 braceless
val r2 =
  x match
    case 1 => "the one"
    case 2 => "double"
    case _ => "other"
```

## Methods
```scala
// No braces needed — indentation defines the method body
def computeMeaningOfLife(arg: Int): Int =
  val partialResult = 40
  partialResult + 2   // still part of the method, just indented
```

## Class / Trait / Object Definitions
```scala
// Traditional
class Animal {
  def eat(): Unit = {
    println("I'm eating")
  }
}

// Scala 3 braceless — use colon `:` instead of `{`
class Animal:
  def eat(): Unit =
    println("I'm eating")
  end eat       // optional `end` marker for clarity

  def grow(): Unit =
    println("I'm growing")
end Animal      // marks the end of Animal — useful for long classes
```

## Anonymous Classes
```scala
val aSpecialAnimal = new Animal:
  override def eat(): Unit = println("I'm special")
```

## `end` Markers
`end` markers are optional but useful for long blocks — they help you know what a closing indentation refers to:
```scala
end eat      // ends the `eat` method
end Animal   // ends the Animal class
// Also works for: if, match, for, object, trait, enum
```

## Indentation Rules
- Indentation must be **strictly larger** than the parent block.
- Mixing spaces and tabs can cause ambiguity — **stick to one**.
```
3 spaces + 2 tabs  >  2 spaces + 2 tabs   ✅
3 spaces + 2 tabs  >  3 spaces + 1 tab    ✅
3 tabs   + 2 spaces  vs  2 tabs + 3 spaces  ❌ ambiguous!
```

---

# 🗺️ Big Picture — How They All Connect

```
PatternMatching         → The foundation: match/case on values, structures, guards
        ↓
AllThePatterns          → Every pattern type: constants, tuples, case classes,
                          lists, types, name binding (@), multi-patterns, guards
        ↓
CustomExtractors        → `unapply` teaches Scala how to match custom classes/values
        ↓
PartialFunctions        → functions defined only for some inputs; lift/orElse
        ↓
package.scala           → package-level shared helpers and constants
        ↓
PatternsEverywhere      → PM is hidden in: catch blocks, for generators,
                          val definitions, partial function literals
        ↓
BracelessSyntax         → Scala 3: replace {} with indentation (if/then/else,
                          match/case, for/yield, class:, end markers)
```

---

# 💡 One-Line Summaries

| File | Core Idea |
|---|---|
| `PatternMatching` | `match/case` = structured switch that deconstructs values and returns results |
| `AllThePatterns` | 9 pattern types: constants, wildcards, variables, tuples, CCs, lists, types, `@`, guards |
| `CustomExtractors` | `unapply` lets regular objects participate in pattern matching |
| `PartialFunctions` | Functions may be defined only for some inputs; `lift` and `orElse` make them practical |
| `package.scala` | Package-level shared values and helper methods available across `part4power` |
| `PatternsEverywhere` | `catch`, `for` generators, `val` destructuring, and partial functions all use PM silently |
| `BracelessSyntax` | Scala 3 lets you replace `{}` with indentation; `then`, `:`, and `end` are the new keywords |

