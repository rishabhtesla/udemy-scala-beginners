package lectures.part7akka

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AskPattern, Behaviors}
import akka.util.Timeout
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object AsyncActor {
  sealed trait Command
  final case class Read(replyTo: ActorRef[Reply]) extends Command
  private final case class Loaded(value: String) extends Command
  private final case class LoadFailed(reason: String) extends Command
  sealed trait Reply
  final case class Value(value: String) extends Reply
  final case class Unavailable(reason: String) extends Reply

  def apply(load: () => Future[String]): Behavior[Command] = Behaviors.withStash(16) { stash =>
    Behaviors.setup { context =>
      // Future callbacks may run on another thread. Convert their results to
      // messages: only the actor's message handler changes its behavior.
      context.pipeToSelf(load()) {
        case Success(value) => Loaded(value)
        case Failure(error) => LoadFailed(error.toString)
      }
      def ready(value: String): Behavior[Command] = Behaviors.receiveMessage {
        case Read(replyTo) =>
          replyTo ! Value(value)
          Behaviors.same
        case _ => Behaviors.unhandled
      }
      def failed(reason: String): Behavior[Command] = Behaviors.receiveMessage {
        case Read(replyTo) =>
          replyTo ! Unavailable(reason)
          Behaviors.same
        case _ => Behaviors.unhandled
      }
      Behaviors.receiveMessage {
        case request: Read =>
          // Reject excess requests explicitly instead of overflowing the stash.
          if (stash.isFull) request.replyTo ! Unavailable("Startup request buffer is full")
          else stash.stash(request)
          Behaviors.same
        case Loaded(value) => stash.unstashAll(ready(value))
        case LoadFailed(reason) =>
          context.log.error("Startup load failed: {}", reason)
          stash.unstashAll(failed(reason))
      }
    }
  }

  def main(args: Array[String]): Unit = {
    import AskPattern._
    val system = ActorSystem(apply(() => Future.successful("configuration-v1")), "async-actor")
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: akka.actor.typed.Scheduler = system.scheduler
    try println(Await.result(system.ask[Reply](Read(_)), 5.seconds))
    finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}
