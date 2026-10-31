package lectures.part6backend

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class Todo(id: Long, title: String, completed: Boolean)
final case class NewTodo(title: String)

object TodoCodecs {
  given Encoder[Todo] = deriveEncoder[Todo]
  given Decoder[Todo] = deriveDecoder[Todo]
  given Decoder[NewTodo] = deriveDecoder[NewTodo]
}
