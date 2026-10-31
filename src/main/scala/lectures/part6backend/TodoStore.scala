package lectures.part6backend

import cats.effect.IO
import cats.effect.Ref

private final case class TodoState(nextId: Long, todos: Vector[Todo])

final class TodoStore private (state: Ref[IO, TodoState]) {
  def all: IO[Vector[Todo]] =
    state.get.map(_.todos)

  def find(id: Long): IO[Option[Todo]] =
    state.get.map(_.todos.find(_.id == id))

  def create(title: String): IO[Todo] =
    state.modify { current =>
      val todo = Todo(current.nextId, title, completed = false)
      val updated = current.copy(
        nextId = current.nextId + 1,
        todos = current.todos :+ todo
      )
      (updated, todo)
    }
}

object TodoStore {
  def create: IO[TodoStore] =
    Ref.of[IO, TodoState](TodoState(nextId = 1L, todos = Vector.empty))
      .map(new TodoStore(_))
}
