package lectures.part6backend

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import munit.FunSuite
import org.http4s.{Method, Request, Status}
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.implicits.*

class TodoRoutesSuite extends FunSuite {
  import TodoCodecs.given

  private def app =
    TodoStore.create.map(store => new TodoRoutes(store).httpApp)

  test("health route reports that the API is ready") {
    val response = app.flatMap(_.run(Request[IO](Method.GET, uri"/health"))).unsafeRunSync()
    assertEquals(response.status, Status.Ok)
  }

  test("a todo can be created and then read") {
    val result = for {
      httpApp <- app
      created <- httpApp.run(
        Request[IO](Method.POST, uri"/todos")
          .withEntity(Json.obj("title" -> Json.fromString("Learn Scala")))
      )
      todo <- created.as[Todo]
      fetched <- httpApp.run(Request[IO](Method.GET, uri"/todos/1"))
      fetchedTodo <- fetched.as[Todo]
    } yield (created.status, todo, fetched.status, fetchedTodo)

    val (createdStatus, todo, fetchedStatus, fetchedTodo) = result.unsafeRunSync()
    assertEquals(createdStatus, Status.Created)
    assertEquals(todo, Todo(1L, "Learn Scala", completed = false))
    assertEquals(fetchedStatus, Status.Ok)
    assertEquals(fetchedTodo, todo)
  }

  test("invalid todo titles are rejected") {
    val result = for {
      httpApp <- app
      response <- httpApp.run(
        Request[IO](Method.POST, uri"/todos")
          .withEntity(Json.obj("title" -> Json.fromString("   ")))
      )
    } yield response.status

    assertEquals(result.unsafeRunSync(), Status.BadRequest)
  }

  test("malformed JSON is rejected") {
    val result = for {
      httpApp <- app
      response <- httpApp.run(
        Request[IO](Method.POST, uri"/todos")
          .withEntity("{not-json")
      )
    } yield response.status

    assertEquals(result.unsafeRunSync(), Status.BadRequest)
  }

  test("unknown todos return not found") {
    val response = app
      .flatMap(_.run(Request[IO](Method.GET, uri"/todos/999")))
      .unsafeRunSync()

    assertEquals(response.status, Status.NotFound)
  }
}
