package lectures.part7akka

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

object Supervision {
  sealed trait Command
  final case class Add(value: Int, replyTo: ActorRef[Int]) extends Command
  case object SimulateFailure extends Command

  private def counter(total: Int): Behavior[Command] =
    Behaviors.receiveMessage {
      case Add(value, replyTo) =>
        replyTo ! (total + value)
        counter(total + value)
      case SimulateFailure =>
        throw new IllegalStateException("Intentional failure for the supervision lesson")
    }

  def supervisedCounter: Behavior[Command] =
    // Restart re-creates the initial behavior. In-memory state is NOT persistence.
    // The failed message is not retried, and restarting does not send a reply.
    Behaviors.supervise(counter(0)).onFailure[IllegalStateException](SupervisorStrategy.restart)

  def guardian(result: Promise[Int]): Behavior[Int] = Behaviors.setup { context =>
    val child = context.spawn(supervisedCounter, "counter")
    child ! Add(5, context.self)
    Behaviors.receiveMessage { first =>
      require(first == 5, s"Expected initial total 5, got $first")
      // One actor sends both messages in order to the same child. The failed
      // message is discarded; the following Add is handled after the restart.
      child ! SimulateFailure
      child ! Add(1, context.self)
      Behaviors.receiveMessage { restarted =>
        result.success(restarted)
        Behaviors.stopped
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val result = Promise[Int]()
    val system = ActorSystem(guardian(result), "supervision-demo")
    try {
      println(Await.result(result.future, 10.seconds)) // 1, not 6: state was reset.
    } finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}
