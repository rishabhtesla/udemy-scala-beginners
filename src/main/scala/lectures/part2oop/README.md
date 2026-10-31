# Part 2 OOP — All Concepts Explained (Bottom to Top)

This module explains how Scala groups data and behavior into types. You will build from classes and objects to inheritance, traits, case classes, and generics. You should be comfortable with Part 1 values, methods, and types first.

## How to study this module

Read `OOBasics.scala` first, then work through the files in the order shown in the Big Picture below. Run examples with `sbt "runMain lectures.part2oop.FileName"`. For `package.scala`, use a neighboring example such as `PackagingAndImports.scala`; package objects are helpers rather than runnable lessons.

Pause after each file to predict what an expression returns, then run it. Do not memorize syntax in isolation: explain what data the class owns, what behavior it exposes, and which type a value has.

---

# 📦 1. OOBasics.scala — Classes, Fields, Methods, Constructors

## What is a Class?
A class is a **blueprint** for creating objects.

```scala
class Person(name: String, val age: Int = 0) {
  val x = 2           // field defined in the body
  println(1 + 3)      // body code runs on EVERY instantiation

  def greet(name: String): Unit = println(s"${this.name} says: Hi, $name")
  def greet(): Unit = println(s"Hi, I am $name")
}
```

## Constructor Parameters vs Fields
```scala
class Person(name: String, val age: Int = 0)
//            ^^^^                ^^^
//    NOT a field (no val/var)   IS a field (val prefix)
```
- Without `val`/`var` → parameter only, accessible inside the class but NOT from outside.
- With `val` → becomes a **public field**, accessible as `person.age`.

## Default Parameters
```scala
val age: Int = 0   // default value, can omit when creating instance
```

## Method Overloading
```scala
def greet(name: String): Unit = ...   // version 1 — with param
def greet(): Unit = ...               // version 2 — no param
```
Two methods with the same name but **different parameters** — Scala picks the right one.

## `this` keyword
```scala
def greet(name: String) = s"${this.name} says Hi, $name"
//  ^^^^ refers to the field `name`, not the parameter `name`
```
`this` distinguishes between a class field and a method parameter with the same name.

## Multiple (Auxiliary) Constructors
```scala
def this(name: String) = this(name, 0)    // 2nd constructor
def this() = this("John Doe")             // 3rd constructor
```
Alternative ways to build an object with fewer arguments. They MUST call the primary constructor.

## Immutability Pattern (Counter)
```scala
class Counter(val count: Int = 0) {
  def inc = new Counter(count + 1)   // returns a NEW counter, doesn't mutate!
  def dec = new Counter(count - 1)
}

counter.inc.inc.inc.print   // chaining works because each step returns a new Counter
```
In Scala/FP, instead of mutating state, you **return a new object** with the updated value.

---

# 🔗 2. Inheritance.scala — Extending Classes

## Basic Inheritance
```scala
class Animal {
  val creatureType = "wild"
  def eat = println("nomnom")
}

class Cat extends Animal {
  def crunch = {
    eat             // ← inherits eat() from Animal
    println("crunch crunch")
  }
}
```
`Cat` gets all methods and fields of `Animal` for free.

## Constructors in Inheritance
```scala
class Person(name: String, age: Int) {
  def this(name: String) = this(name, 0)
}

class Adult(name: String, age: Int, idCard: String) extends Person(name)
//  Adult must call ONE of Person's constructors ↑
```
When a child class is created, it must call a constructor of the parent class.

## Overriding
```scala
class Dog(override val creatureType: String) extends Animal {
  override def eat = {
    super.eat            // ← call parent's version first
    println("crunch, crunch")
  }
}
```
- `override` keyword is **required** in Scala (unlike Java).
- Can override both **vals** and **defs**.
- `super` calls the parent class's implementation.

## Polymorphism
```scala
val unknownAnimal: Animal = new Dog("K9")
unknownAnimal.eat   // calls Dog's eat, not Animal's!
```
A `Dog` IS an `Animal` (Liskov). The actual method called depends on the **runtime type**, not the declared type. This is called **dynamic dispatch**.

## Preventing Overrides
```scala
final val x = ...           // 1. This specific member cannot be overridden
final class Animal { ... }  // 2. The entire class cannot be extended
sealed class Animal { ... } // 3. Can only be extended in THIS FILE
```

---

# 🎭 3. AbstractDataTypes.scala — Abstract Classes & Traits

## Abstract Classes
```scala
abstract class Animal {
  val creatureType: String = "wild"   // concrete field
  def eat: Unit                        // abstract method — no body!
}
```
- Has **abstract members** (declared but not implemented).
- Cannot be instantiated directly (`new Animal` → compile error).
- Subclasses **must** implement all abstract members.

```scala
class Dog extends Animal {
  override val creatureType = "Canine"      // implements abstract field
  def eat: Unit = println("crunch crunch")  // implements abstract method
}
```

## Traits
```scala
trait Carnivore {
  def eat(animal: Animal): Unit             // abstract
  val preferredMeal: String = "fresh meat"  // concrete (with default)
}
```
- Like abstract classes but more flexible.
- A class can **mix in multiple traits** (multiple inheritance).

```scala
class Crocodile extends Animal with Carnivore with ColdBlooded {
  def eat: Unit = println("nomnomnom")           // from Animal
  def eat(animal: Animal): Unit = println(...)   // from Carnivore
}
```

## Traits vs Abstract Classes

| | Abstract Class | Trait |
|---|---|---|
| Constructor parameters | ✅ Yes | ✅ Yes (Scala 3) |
| Multiple inheritance | ❌ Only one | ✅ Many traits |
| Meaning | A "thing" (IS-A) | A "behavior" (CAN-DO) |
| Example | `Animal` | `Carnivore`, `Flyable` |

Scala 3 traits can have constructor parameters. Older Scala material may say otherwise; for this Scala 3.3.1 project, treat traits as mixable behavior with support for parameters when needed.

---

# 🪄 4. AnonymousClasses.scala — One-off Implementations

## The Problem
Sometimes you need a class **just once** and don't want to declare it separately.

```scala
abstract class Animal {
  def eat: Unit
}

// Anonymous class — create + implement inline
val funnyAnimal: Animal = new Animal {
  override def eat: Unit = println("ahahahahahaah")
}
```

## What actually happens under the hood?
```scala
// Scala compiler secretly generates:
class AnonymousClasses$$anon$1 extends Animal {
  override def eat: Unit = println("ahahahahahaah")
}
val funnyAnimal: Animal = new AnonymousClasses$$anon$1
```
Scala creates a hidden class with a generated name. You can verify:
```scala
println(funnyAnimal.getClass)
// → class lectures.part2oop.AnonymousClasses$$anon$1
```

## Works on Concrete Classes Too
```scala
class Person(name: String) {
  def sayHi: Unit = println(s"Hi, my name is $name")
}

val jim = new Person("Jim") {
  override def sayHi: Unit = println("Hi, my name is Jim, how can I be of service?")
}
```
You can override behavior **per instance** without creating a named subclass.

---

# 🏢 5. Objects.scala — Singletons & Companions

## The Problem: No Static in Scala
Java has `static` methods/fields that belong to the class, not instances.
Scala has **no `static`** — instead it uses `object`.

## Scala `object` = Singleton
```scala
object Person {              // there is exactly ONE instance of this, ever
  val N_EYES = 2
  def canFly: Boolean = false
}

val p1 = Person
val p2 = Person
println(p1 == p2)  // → true! same object!
```

## Companion Objects
When an `object` and a `class` share the **same name** in the **same file**, they are companions.
```scala
object Person {   // companion object — "static" stuff here
  val N_EYES = 2
  def apply(mother: Person, father: Person): Person = new Person("Bobbie")
}

class Person(val name: String) {   // instance stuff here
}
```
- The object holds "class-level" things (factory methods, constants).
- The class holds instance fields and methods.
- They can access each other's private members.

## Factory Method via apply
```scala
val bobbie = Person(mary, john)   // calls Person.apply(mary, john)
```
Using `apply` in the companion object lets you create instances without `new`.

## Scala App
```scala
object MyApp extends App { ... }
```
`App` provides the `main` method. Equivalent to:
```scala
object MyApp {
  def main(args: Array[String]): Unit = { ... }
}
```

---

# ✍️ 6. MethodNotations.scala — Syntactic Sugar

## Infix Notation (Operator Notation)
Any method with **one parameter** can be called without `.` and `()`:
```scala
mary.likes("Inception")   // standard
mary likes "Inception"    // infix — reads like English!
```

## Operators ARE Methods
```scala
1 + 2         // same as
1.+(2)        // + is just a method on Int!
```
You can define your own operators:
```scala
def +(person: Person): String = s"${this.name} is hanging out with ${person.name}"
println(mary + tom)   // calls mary.+(tom)
```

## Prefix Notation (Unary Operators)
```scala
val x = -1        // same as 1.unary_-
println(!mary)    // same as mary.unary_!
```
Only `- + ~ !` can be used as unary prefix operators.
```scala
def unary_! : String = s"$name, what the heck?!"
```

## Postfix Notation
```scala
mary.isAlive   // standard
mary isAlive   // postfix — no () needed for 0-param methods
```
⚠️ Requires `import scala.language.postfixOps` — discouraged in practice.

## `apply` Method — The Magic ()
```scala
def apply(): String = s"Hi, my name is $name"

mary.apply()   // standard
mary()         // identical! calling apply() implicitly
```
Defining `apply` lets you call an object **like a function**.

---

# 📦 7. CaseClasses.scala — Data Classes on Steroids

## The Problem Without Case Classes
Regular classes need you to manually write: `equals`, `hashCode`, `toString`, `copy`.

## Case Class gives you everything for free:
```scala
case class Person(name: String, age: Int)
```

### 1. Constructor parameters are fields automatically
```scala
val jim = new Person("Jim", 34)
println(jim.name)   // ✅ works! No val needed in case class
```

### 2. Sensible toString
```scala
println(jim)   // → Person(Jim,34)   not "lectures.part2oop.Person@3764951d"
```

### 3. equals and hashCode by value (not reference)
```scala
val jim2 = new Person("Jim", 34)
println(jim == jim2)   // → true! compares field values, not memory address
```

### 4. copy method
```scala
val jim3 = jim.copy(age = 45)   // new instance, change only what you specify
println(jim3)  // → Person(Jim,45)
```

### 5. Companion object with apply — no `new` needed
```scala
val mary = Person("Mary", 23)   // no `new` keyword!
```

### 6. Serializable — great for Akka/distributed systems
Case classes can be sent over the network out of the box.

### 7. Extractor patterns — use in Pattern Matching
```scala
person match {
  case Person(name, age) => println(s"$name is $age")
}
```

## Case Object
```scala
case object UnitedKingdom {
  def name: String = "The UK of GB and NI"
}
```
Like a case class but a **singleton** — has `toString`, `equals`, `hashCode` but no `copy`.

---

# 🔬 8. Generics.scala — Type Parameters

## The Problem
Without generics you'd need a `ListOfInts`, `ListOfStrings`, `ListOfAnimals`…

## Generic Class
```scala
class MyList[A] {   // A is a type parameter — a placeholder for any type
  def add[B >: A](element: B): MyList[B] = ???
}

val listOfInts    = new MyList[Int]
val listOfStrings = new MyList[String]
```
`[A]` means: "this class works with ANY type — you choose it when you use it."

## Generic Methods
```scala
object MyList {
  def empty[A]: MyList[A] = ???
}
val emptyListOfIntegers = MyList.empty[Int]
```

## Variance — The Hard Part

### Covariance `[+A]` — "YES, List[Cat] is a List[Animal]"
```scala
class CovariantList[+A]
val animalList: CovariantList[Animal] = new CovariantList[Cat]   // ✅
```
If `Cat extends Animal`, then `List[Cat]` can be used where `List[Animal]` is expected.

### Invariance `[A]` — "NO, List[Cat] is NOT a List[Animal]"
```scala
class InvariantList[A]
val list: InvariantList[Animal] = new InvariantList[Animal]   // must be exact
val list2: InvariantList[Animal] = new InvariantList[Cat]     // ❌ compile error
```

### Contravariance `[-A]` — "Trainer[Animal] can train a Cat"
```scala
class Trainer[-A]
val trainer: Trainer[Cat] = new Trainer[Animal]   // ✅
```
If a trainer can train ANY animal, they can certainly train a cat.

## Bounded Types
```scala
class Cage[A <: Animal](animal: A)   // A must be Animal or a subtype
val cage = new Cage(new Dog)         // ✅ Dog is an Animal
val cage2 = new Cage(new Car)        // ❌ Car is not an Animal
```
- `[A <: Animal]` = upper bound — A must be a **subtype** of Animal
- `[A >: Animal]` = lower bound — A must be a **supertype** of Animal

---

# 💥 9. Exceptions.scala — Error Handling

## The Exception Hierarchy
```
Throwable
├── Exception  ← recoverable (use try/catch)
└── Error      ← unrecoverable (StackOverflowError, OutOfMemoryError)
```

## Throwing Exceptions
```scala
throw new RuntimeException("No int for you!")
// throw is an expression of type Nothing
```

## Catching Exceptions
```scala
val result = try {
  getInt(false)                         // code that might throw
} catch {
  case e: RuntimeException => 43        // handle specific exception type
} finally {
  println("finally")                    // ALWAYS runs, whether or not exception occurred
}
```
- `try` — wrap risky code.
- `catch` — handle specific exceptions using pattern matching.
- `finally` — always runs (cleanup: close files, DB connections).
- `finally` does **NOT** affect the return value of the expression.

## Custom Exceptions
```scala
class MyException extends Exception
class OverflowException extends RuntimeException
class MathCalculationException extends RuntimeException("Division by 0")
```
Extend `Exception` or `RuntimeException` to create your own exception types.

## PocketCalculator — Putting it together
```scala
def add(x: Int, y: Int) = {
  val result = x + y
  if (x > 0 && y > 0 && result < 0) throw new OverflowException   // integer overflow!
  else result
}
def divide(x: Int, y: Int) =
  if (y == 0) throw new MathCalculationException
  else x / y
```

---

# 🎨 10. Enums.scala — Sealed Sets of Values (Scala 3)

## Basic Enum
```scala
enum Permissions {
  case READ, WRITE, EXECUTE, NONE
}

val p: Permissions = Permissions.READ
```
An enum defines a **fixed set of named values** — like a type-safe constant list.

## Enum with Methods
```scala
enum Permissions {
  case READ, WRITE, EXECUTE, NONE

  def openDocument(): Unit =
    if (this == READ) println("opening document...")
    else println("reading not allowed.")
}

Permissions.READ.openDocument()   // → "opening document..."
```

## Enum with Constructor Arguments
```scala
enum PermissionsWithBits(bits: Int) {
  case READ    extends PermissionsWithBits(4)   // 100 in binary
  case WRITE   extends PermissionsWithBits(2)   // 010
  case EXECUTE extends PermissionsWithBits(1)   // 001
  case NONE    extends PermissionsWithBits(0)   // 000
}
```
Each case carries a value (bit representation here).

## Standard Enum API
```scala
somePermissions.ordinal          // → 0 (position in enum, 0-indexed)
PermissionsWithBits.values       // → Array of all enum values
Permissions.valueOf("READ")      // → Permissions.READ (string → enum)
```

---

# 📁 11. PackagingAndImports.scala — Organizing Code

## Packages
```scala
package lectures.part2oop
```
- Organizes code into namespaces (like folders).
- **Must match folder structure** on disk.
- Members in the same package are accessible by simple name.

## Imports
```scala
import playground.PrinceCharming               // import one class
import playground.{PrinceCharming, Cinderella} // import multiple
import playground._                            // import everything (wildcard)
```

## Import Aliasing
```scala
import java.util.Date
import java.sql.{Date => SqlDate}   // rename to avoid conflict
```
When two packages have a class with the same name, alias one.

## Package Objects
```scala
// In package.scala:
package object part2oop {
  def sayHello: Unit = println("Hello!")
  val SPEED_OF_LIGHT = 299792458
}

// Used directly in any file in the package — no import needed:
sayHello
println(SPEED_OF_LIGHT)
```
Package objects hold **utility functions and constants** available to the whole package without import.

## Default Imports (always available, no import needed)
```
java.lang   → String, Object, Exception
scala       → Int, Boolean, Nothing, Function
scala.Predef → println, ???, require
```

---

# 🗺️ Big Picture — How They All Connect

```
OOBasics          → class, field, method, constructor, immutability
    ↓
Inheritance       → extend, override, super, polymorphism, sealed
    ↓
AbstractDataTypes → abstract class, trait, abstract members
    ↓
AnonymousClasses  → inline implementation without naming a class
    ↓
Objects           → singleton, companion object, factory methods
    ↓
MethodNotations   → infix, prefix, postfix, apply = syntactic sugar
    ↓
CaseClasses       → data classes with equals/toString/copy for free
    ↓
Generics          → type parameters, variance, bounded types
    ↓
Exceptions        → try/catch/finally, custom exceptions
    ↓
Enums             → sealed named value sets (Scala 3)
    ↓
PackagingImports  → organizing code into packages, imports, aliasing
```

## Practice and interview checkpoints

Try these without copying an existing example:

- Model a `BankAccount` with a balance and operations that return a new account instead of changing the old one.
- Describe when to use a trait instead of an abstract class. Give one example of a type that can mix in multiple traits.
- Make two case-class values with equal fields. Explain why they compare equal and what `copy` returns.
- Write a generic method that accepts a type parameter, then explain what covariance and contravariance mean in plain language.
- Explain the difference between a class and its companion object, and when a factory method is useful.

Interview checkpoints: What is the difference between a class parameter and a `val` field? What does `override` guarantee? What is dynamic dispatch? Why are immutable values useful? What does a type parameter let you express? Why can `List[Cat]` safely be treated as `List[Animal]` only when the collection is covariant?

Before moving to Part 3, be able to sketch a small domain model using a case class and a trait, and explain how an instance gets created and which implementation a method call invokes.
