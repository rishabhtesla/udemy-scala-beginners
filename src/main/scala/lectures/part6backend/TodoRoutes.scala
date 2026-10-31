package lectures.part6backend

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*

final class TodoRoutes(store: TodoStore) {
  import TodoCodecs.given

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      Ok(Json.obj("status" -> Json.fromString("ok")))

    case GET -> Root / "todos" =>
      store.all.flatMap(todos => Ok(todos.asJson))

    case request @ POST -> Root / "todos" =>
      request.attemptAs[NewTodo].value.flatMap {
        case Left(_) =>
          BadRequest(Json.obj("error" -> Json.fromString("Body must contain a title string.")))
        case Right(input) if input.title.trim.isEmpty =>
          BadRequest(Json.obj("error" -> Json.fromString("Title must not be empty.")))
        case Right(input) =>
          store.create(input.title.trim).flatMap(todo => Created(todo.asJson))
      }

    case GET -> Root / "todos" / LongVar(id) =>
      store.find(id).flatMap {
        case Some(todo) => Ok(todo.asJson)
        case None => NotFound(Json.obj("error" -> Json.fromString(s"Todo $id was not found.")))
      }
  }

  val httpApp = routes.orNotFound
}
