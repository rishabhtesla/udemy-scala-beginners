package lectures.part7akka

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AskPattern, Behaviors}
import akka.util.Timeout
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object TypedActors {
  // The protocol is the complete set of messages this actor accepts.
  sealed trait Command
  final case class Deposit(amount: Int, replyTo: ActorRef[Reply]) extends Command
  final case class Withdraw(amount: Int, replyTo: ActorRef[Reply]) extends Command
  final case class GetBalance(replyTo: ActorRef[Reply]) extends Command
  sealed trait Reply
  final case class Balance(amount: Int) extends Reply
  final case class Rejected(reason: String) extends Reply

  def account(balance: Int = 0): Behavior[Command] = {
    require(balance >= 0, "Initial balance must not be negative")
    Behaviors.receiveMessage {
      case Deposit(amount, replyTo) if amount > 0 && amount <= Int.MaxValue - balance =>
        replyTo ! Balance(balance + amount)
        account(balance + amount)
      case Withdraw(amount, replyTo) if amount > 0 && amount <= balance =>
        replyTo ! Balance(balance - amount)
        account(balance - amount)
      case GetBalance(replyTo) =>
        replyTo ! Balance(balance)
        Behaviors.same
      case Deposit(_, replyTo) =>
        replyTo ! Rejected("Deposit must be positive and must not overflow the balance")
        Behaviors.same
      case Withdraw(_, replyTo) =>
        replyTo ! Rejected("Withdrawal must be positive and covered by the balance")
        Behaviors.same
    }
  }

  def demo(system: ActorSystem[Command]): Future[Reply] = {
    import AskPattern._
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: akka.actor.typed.Scheduler = system.scheduler
    implicit val ec: scala.concurrent.ExecutionContext = system.executionContext

    // Ask sends a message with a temporary reply actor and returns a Future.
    // Chaining asks orders the operations; independent sends are not a transaction.
    for {
      _ <- system.ask[Reply](Deposit(100, _))
      _ <- system.ask[Reply](Withdraw(30, _))
      result <- system.ask[Reply](GetBalance(_))
    } yield result
  }

  def main(args: Array[String]): Unit = {
    val system = ActorSystem(account(), "account-demo")
    try println(Await.result(demo(system), 10.seconds)) // Balance(70)
    finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}
