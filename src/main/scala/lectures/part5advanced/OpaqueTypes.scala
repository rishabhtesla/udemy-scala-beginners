package lectures.part5advanced

object OpaqueTypes extends App {

  /*
    Opaque types create a new type at compile time without runtime overhead.
    Great for domain modeling: UserId, Email, Meter, Kilogram, etc.
   */

  object Domain {
    opaque type UserId = Int

    object UserId {
      def apply(value: Int): UserId = value
    }

    extension (id: UserId)
      def value: Int = id
      def show: String = s"UserId(${id.value})"

    def findUser(id: UserId): String = s"Found user with id ${id.value}"
  }

  import Domain.*

  val id: UserId = UserId(42)
  println(id.show)
  println(findUser(id))

  // val raw: UserId = 42  // does not compile outside the opaque type scope
}

