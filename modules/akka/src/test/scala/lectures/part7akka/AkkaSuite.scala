package lectures.part7akka

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.{AskPattern, Behaviors}
import akka.util.Timeout
import munit.FunSuite
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

class AkkaSuite extends FunSuite {
  test("actor messages update balance and reject invalid operations without changing state") {
    import AskPattern._
    import TypedActors._
    val system = ActorSystem(account(), "account-test")
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: akka.actor.typed.Scheduler = system.scheduler
    try {
      assertEquals(Await.result(demo(system), 10.seconds), Balance(70))
      assert(Await.result(system.ask[Reply](Withdraw(100, _)), 5.seconds).isInstanceOf[Rejected])
      assert(Await.result(system.ask[Reply](Deposit(-1, _)), 5.seconds).isInstanceOf[Rejected])
      assert(Await.result(system.ask[Reply](Deposit(Int.MaxValue, _)), 5.seconds).isInstanceOf[Rejected])
      assertEquals(Await.result(system.ask[Reply](GetBalance(_)), 5.seconds), Balance(70))
    } finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }

  test("stream filters, maps asynchronously and folds all elements") {
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "streams-test")
    try assertEquals(Await.result(Streams.totals(), 10.seconds), 120)
    finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }

  }

  test("supervision restarts with initial state and continues with the next message") {
    val result = Promise[Int]()
    val system = ActorSystem(Supervision.guardian(result), "supervision-test")
    try assertEquals(Await.result(result.future, 10.seconds), 1)
    finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}
