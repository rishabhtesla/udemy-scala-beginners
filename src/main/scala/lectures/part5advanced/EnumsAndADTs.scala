package lectures.part5advanced

object EnumsAndADTs extends App {

  /*
    Algebraic Data Types (ADTs) are usually modeled with a sealed trait/enum + cases.
    They are ideal for finite, well-defined domains and exhaustive pattern matching.
   */

  enum PaymentMethod {
    case Cash, Card, Upi
  }

  def paymentMessage(method: PaymentMethod): String = method match {
    case PaymentMethod.Cash => "Paying with cash"
    case PaymentMethod.Card => "Paying with card"
    case PaymentMethod.Upi => "Paying with UPI"
  }

  println(paymentMessage(PaymentMethod.Card))

  enum Result[+A] {
    case Success(value: A)
    case Failure(message: String)
  }

  def divide(a: Int, b: Int): Result[Int] =
    if (b == 0) Result.Failure("division by zero")
    else Result.Success(a / b)

  val result = divide(10, 2)
  println(result)

  val explanation = result match {
    case Result.Success(value) => s"Result is $value"
    case Result.Failure(message) => s"Computation failed: $message"
  }

  println(explanation)
}

