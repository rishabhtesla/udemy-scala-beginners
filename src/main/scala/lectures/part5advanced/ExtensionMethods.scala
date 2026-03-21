package lectures.part5advanced

import scala.annotation.tailrec

object ExtensionMethods extends App {

  /*
    Extension methods let you add methods to existing types without modifying them.
    This is a Scala 3 replacement/improvement over implicit classes from Scala 2.
   */

  extension (value: String)
    def asGreeting: String = s"Hello, $value"
    def firstCharacterOption: Option[Char] = value.headOption

  extension (number: Int)
    @tailrec
    final def times(action: => Unit): Unit =
      if (number <= 0) ()
      else {
        action
        (number - 1).times(action)
      }

  println("Scala".asGreeting)
  println("Scala".firstCharacterOption)

  3.times {
    println("extension methods are neat")
  }
}
