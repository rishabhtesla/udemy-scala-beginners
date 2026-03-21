package lectures.part5advanced

object Eithers extends App {

  /*
    Either[Left, Right] models a computation that can end in two possible ways.
    By convention:
    - Left  = error/failure
    - Right = success
   */

  def safeDivide(a: Int, b: Int): Either[String, Int] =
    if (b == 0) Left("division by zero")
    else Right(a / b)

  println(safeDivide(10, 2))
  println(safeDivide(10, 0))

  val success: Either[String, Int] = Right(42)
  val failure: Either[String, Int] = Left("something went wrong")

  println(success.map(_ * 2))
  println(failure.map(_ * 2))

  val chained = for {
    a <- safeDivide(20, 2)
    b <- safeDivide(a, 5)
  } yield b

  println(chained)

  val handled = safeDivide(10, 0) match {
    case Left(error) => s"Computation failed: $error"
    case Right(value) => s"Computation succeeded: $value"
  }

  println(handled)

  val fallback = safeDivide(10, 0).getOrElse(-1)
  println(fallback)
}

