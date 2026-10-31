package lectures.part7akka

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.AskPattern
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._

object PersistentAccount {
  sealed trait Command
  final case class Credit(id: String, amount: Long, replyTo: ActorRef[Reply]) extends Command
  final case class Read(replyTo: ActorRef[Reply]) extends Command
  sealed trait Reply
  final case class Accepted(balanceAfterOperation: Long) extends Reply
  final case class Rejected(reason: String) extends Reply
  final case class Balance(amount: Long) extends Reply

  final case class Credited(id: String, amount: Long)
  final case class Recorded(amount: Long, balanceAfterOperation: Long)
  final case class State(balance: Long, processed: Map[String, Recorded])

  // This journal survives actor restarts only within ONE live ActorSystem.
  // It deliberately does not claim disk durability or production serialization.
  val learningConfig = ConfigFactory.parseString("""
    akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
  """).withFallback(ConfigFactory.load())

  def apply(id: String): Behavior[Command] =
    EventSourcedBehavior[Command, Credited, State](
      PersistenceId.ofUniqueId(id),
      State(0L, Map.empty),
      (state, command) => command match {
        case Read(replyTo) => Effect.reply(replyTo)(Balance(state.balance))
        case Credit(operationId, amount, replyTo) =>
          state.processed.get(operationId) match {
            case Some(recorded) if recorded.amount == amount =>
              // Retrying returns the ORIGINAL outcome, not today's balance.
              Effect.reply(replyTo)(Accepted(recorded.balanceAfterOperation))
            case Some(_) =>
              Effect.reply(replyTo)(Rejected("Operation ID was already used with a different amount"))
            case None if operationId.trim.isEmpty || amount <= 0 || amount > Long.MaxValue - state.balance =>
              Effect.reply(replyTo)(Rejected("ID must be nonblank and credit must be positive without overflow"))
            case None =>
              // The reply is sent only AFTER the journal acknowledges persistence
              // and the event handler has produced the new state.
              Effect.persist(Credited(operationId, amount))
                .thenReply(replyTo)(next => Accepted(next.balance))
          }
      },
      (state, event) => {
        // Recovery calls this handler again. Keep it deterministic and side-effect free.
        val next = state.balance + event.amount
        State(next, state.processed.updated(event.id, Recorded(event.amount, next)))
      }
    )

  def main(args: Array[String]): Unit = {
    import AskPattern._
    val system = ActorSystem(apply("learning-account"), "persistent-account", learningConfig)
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: akka.actor.typed.Scheduler = system.scheduler
    try {
      println(Await.result(system.ask[Reply](Credit("op-1", 50L, _)), 5.seconds))
      println(Await.result(system.ask[Reply](Credit("op-1", 50L, _)), 5.seconds))
      println(Await.result(system.ask[Reply](Read(_)), 5.seconds))
    } finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}
