package lectures.part4power

object PartialFunctions extends App {

  /*
    Partial function = a function that may not be defined for every input.
    Example: {1 => 42, 2 => 56, 5 => 999}
    It only knows what to do for 1, 2 and 5.
   */

  val aPartialFunction: PartialFunction[Int, Int] = {
    case 1 => 42
    case 2 => 56
    case 5 => 999
  }

  println(aPartialFunction(2))
  println(aPartialFunction.isDefinedAt(67))

  val lifted: Int => Option[Int] = aPartialFunction.lift
  println(lifted(2))
  println(lifted(98))

  val pfChain = aPartialFunction.orElse[Int, Int] {
    case 45 => 67
  }

  println(pfChain(45))

  val aMappedList = List(1, 2, 3).map {
    case 1 => "the one"
    case 2 => "double"
    case 3 => "third"
  }

  println(aMappedList)

  // Partial functions can be written like tiny chatbots too.
  val chatbot: PartialFunction[String, String] = {
    case "hello" => "Hi, my name is ScalaBot"
    case "goodbye" => "Once you start talking to me there is no return."
    case "call mom" => "Unable to find your phone without recursive permissions."
  }

  println(chatbot("hello"))
}

