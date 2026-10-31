package lectures.part6backend

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder

object TodoApi extends IOApp.Simple {
  def run: IO[Unit] =
    TodoStore.create.flatMap { store =>
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"8080")
        .withHttpApp(new TodoRoutes(store).httpApp)
        .build
        .useForever
    }
}
