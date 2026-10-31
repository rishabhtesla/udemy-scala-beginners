package lectures.part7akka

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import munit.FunSuite
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

class AdvancedAkkaSuite extends FunSuite {
  test("async initialization serves buffered requests after completion and rejects load failures") {
    val kit = ActorTestKit()
    try {
      val loaded = Promise[String]()
      val actor = kit.spawn(AsyncActor(() => loaded.future))
      val probe = kit.createTestProbe[AsyncActor.Reply]()
      actor ! AsyncActor.Read(probe.ref)
      loaded.success("ready")
      probe.expectMessage(AsyncActor.Value("ready"))

      val error = Promise[String]()
      val failed = kit.spawn(AsyncActor(() => error.future))
      failed ! AsyncActor.Read(probe.ref)
      error.failure(new IllegalStateException("fixture failure"))
      assert(probe.receiveMessage().isInstanceOf[AsyncActor.Unavailable])
    } finally kit.shutdownTestKit()
  }

  test("persistent commands are idempotent and recover after actor recreation") {
    import PersistentAccount._
    val kit = ActorTestKit(learningConfig)
    try {
      val probe = kit.createTestProbe[Reply]()
      val account = kit.spawn(PersistentAccount("recovery-test"))
      account ! Credit("a", 50L, probe.ref)
      probe.expectMessage(Accepted(50L))
      account ! Credit("b", 20L, probe.ref)
      probe.expectMessage(Accepted(70L))
      account ! Credit("a", 50L, probe.ref)
      probe.expectMessage(Accepted(50L))
      account ! Credit("a", 51L, probe.ref)
      assert(probe.receiveMessage().isInstanceOf[Rejected])
      account ! Credit(" ", 1L, probe.ref)
      assert(probe.receiveMessage().isInstanceOf[Rejected])
      account ! Credit("overflow", Long.MaxValue, probe.ref)
      assert(probe.receiveMessage().isInstanceOf[Rejected])
      kit.stop(account) // Waits for termination before reusing the persistence ID.
      val recovered = kit.spawn(PersistentAccount("recovery-test"))
      recovered ! Read(probe.ref)
      probe.expectMessage(Balance(70L))
      recovered ! Credit("b", 20L, probe.ref)
      probe.expectMessage(Accepted(70L))
    } finally kit.shutdownTestKit()
  }

  test("startup stash admits sixteen reads and explicitly rejects excess work") {
    val kit = ActorTestKit()
    try {
      val loaded = Promise[String]()
      val actor = kit.spawn(AsyncActor(() => loaded.future))
      val probe = kit.createTestProbe[AsyncActor.Reply]()
      (1 to 17).foreach(_ => actor ! AsyncActor.Read(probe.ref))
      probe.expectMessage(AsyncActor.Unavailable("Startup request buffer is full"))
      loaded.success("ready")
      (1 to 16).foreach(_ => probe.expectMessage(AsyncActor.Value("ready")))
      probe.expectNoMessage(100.millis)
    } finally kit.shutdownTestKit()
  }

  test("broadcast branches zip matching ordered elements") {
    val kit = ActorTestKit()
    try assertEquals(Await.result(AdvancedStreams.pairs()(kit.system), 10.seconds),
      Seq((2, 3), (4, 6), (6, 9)))
    finally kit.shutdownTestKit()
  }
}
