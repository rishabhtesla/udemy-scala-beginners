package lectures.part5advanced

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object Futures extends App {

  /*
    Future[A] represents a computation that will eventually produce an A,
    possibly on another thread.
   */

  given ExecutionContext = ExecutionContext.global

  val aFuture: Future[Int] = Future {
    println(s"Computing on thread: ${Thread.currentThread().getName}")
    Thread.sleep(200)
    42
  }

  aFuture.onComplete {
    case Success(value) => println(s"Future succeeded with $value")
    case Failure(exception) => println(s"Future failed: ${exception.getMessage}")
  }

  val doubledFuture = aFuture.map(_ * 2)
  val flatMappedFuture = aFuture.flatMap(x => Future(x + 100))
  val filteredFuture = aFuture.filter(_ % 2 == 0)

  println(Await.result(doubledFuture, 1.second))
  println(Await.result(flatMappedFuture, 1.second))
  println(Await.result(filteredFuture, 1.second))

  val firstFuture = Future("Hello")
  val secondFuture = Future("Scala")

  val combined = for {
    first <- firstFuture
    second <- secondFuture
  } yield s"$first $second"

  println(Await.result(combined, 1.second))
}

