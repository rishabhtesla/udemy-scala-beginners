package lectures.part5advanced

object GivensAndUsings extends App {

  /*
    Givens/usings are Scala 3's contextual abstractions.
    They replace most common implicit-value/implicit-parameter patterns.
   */

  trait JsonSerializer[T] {
    def toJson(value: T): String
  }

  case class Person(name: String, age: Int)

  given JsonSerializer[String] with {
    def toJson(value: String): String = s"\"$value\""
  }

  given JsonSerializer[Int] with {
    def toJson(value: Int): String = value.toString
  }

  given JsonSerializer[Person] with {
    def toJson(value: Person): String =
      s"{" +
        s"\"name\": ${summon[JsonSerializer[String]].toJson(value.name)}," +
        s"\"age\": ${summon[JsonSerializer[Int]].toJson(value.age)}" +
        s"}"
  }

  def convertToJson[T](value: T)(using serializer: JsonSerializer[T]): String =
    serializer.toJson(value)

  val bob = Person("Bob", 25)
  println(convertToJson("Scala"))
  println(convertToJson(42))
  println(convertToJson(bob))

  def describePerson(name: String, age: Int)(using prefix: String): String =
    s"$prefix $name is $age years old"

  given String = "Profile:"

  println(describePerson("Alice", 30))
}

