package lectures.part4power

object CustomExtractors extends App {

  /*
    Custom extractors let you control how pattern matching deconstructs values.
    Pattern matching is not limited to case classes.
   */

  class Person(val name: String, val age: Int)

  object Person {
    def unapply(person: Person): Option[(String, Int)] =
      if (person == null) None
      else Some((person.name, person.age))
  }

  val bob = new Person("Bob", 25)

  val greeting = bob match {
    case Person(name, age) => s"Hi, my name is $name and I am $age years old"
    case _ => "Unknown person"
  }

  println(greeting)

  object PersonStatus {
    def unapply(age: Int): Option[String] =
      Some(if (age < 21) "minor" else "major")
  }

  val legalStatus = bob.age match {
    case PersonStatus(status) => s"My legal status is $status"
    case _ => "No status"
  }

  println(legalStatus)

  // Boolean extractor style
  object even {
    def unapply(n: Int): Boolean = n % 2 == 0
  }

  object singleDigit {
    def unapply(n: Int): Boolean = n > -10 && n < 10
  }

  val n = 8
  val mathProperty = n match {
    case singleDigit() => "single digit"
    case even() => "an even number"
    case _ => "no special property"
  }

  println(mathProperty)
}

