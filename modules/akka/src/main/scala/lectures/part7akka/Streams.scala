package lectures.part7akka

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object Streams {
  def totals()(implicit system: ActorSystem[_]): Future[Int] = {
    implicit val materializer: akka.stream.Materializer = SystemMaterializer(system).materializer
    implicit val ec: scala.concurrent.ExecutionContext = system.executionContext

    val pipeline = Flow[Int]
      .filter(_ % 2 == 0)
      .mapAsync(parallelism = 2)(value => Future(value * 10))

    // These are graph blueprints until runWith materializes a running graph.
    // mapAsync caps in-flight work and preserves order. Downstream demand drives
    // upstream delivery (backpressure); it is not a guarantee of durable delivery.
    Source(1 to 6).via(pipeline).runWith(Sink.fold(0)(_ + _))
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "streams-demo")
    try println(Await.result(totals(), 10.seconds)) // 120
    finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}
