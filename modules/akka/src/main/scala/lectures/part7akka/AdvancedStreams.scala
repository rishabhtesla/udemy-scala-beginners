package lectures.part7akka

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.{ClosedShape, SystemMaterializer}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, Zip}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object AdvancedStreams {
  def pairs()(implicit system: ActorSystem[_]): Future[Seq[(Int, Int)]] = {
    implicit val materializer: akka.stream.Materializer = SystemMaterializer(system).materializer
    val sink = Sink.seq[(Int, Int)]
    // Keep the sink's Future as the graph's materialized value. Connections carry
    // elements; materialized values describe lifecycle/results, not extra ports.
    RunnableGraph.fromGraph(GraphDSL.createGraph(sink) { implicit builder => output =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      val zip = builder.add(Zip[Int, Int]())
      Source(1 to 3) ~> broadcast.in
      broadcast.out(0) ~> Flow[Int].map(_ * 2) ~> zip.in0
      broadcast.out(1) ~> Flow[Int].map(_ * 3) ~> zip.in1
      zip.out ~> output
      ClosedShape
    }).run()
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "advanced-streams")
    try println(Await.result(pairs(), 10.seconds))
    finally {
      system.terminate()
      Await.result(system.whenTerminated, 10.seconds)
    }
  }
}
