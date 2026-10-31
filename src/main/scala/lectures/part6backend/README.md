# Part 6 — Build a Small Scala Backend

This is the first web-backend module. It puts ideas from Parts 1–5 together in a tiny HTTP API using **http4s** and **Cats Effect**. The API stores todos in memory so you can focus on HTTP, Scala types, and asynchronous effects before adding a database.

This part assumes you have worked through the earlier modules, especially methods, case classes, collections, `Either`, and `Future`. You do not need prior web-framework experience.

## 1. Run the tests and start the API

From the repository root:

```bash
sbt test
sbt "runMain lectures.part6backend.TodoApi"
```

The server binds to `127.0.0.1:8080`. In another terminal, try the routes:

```bash
curl http://127.0.0.1:8080/health
curl http://127.0.0.1:8080/todos
curl -i -X POST http://127.0.0.1:8080/todos \
  -H 'Content-Type: application/json' \
  -d '{"title":"Learn Scala"}'
curl http://127.0.0.1:8080/todos/1
```

Stop the server with Ctrl+C. Todos are held in memory, so they disappear when the server stops. That is intentional for this first backend exercise.

## 2. What the API teaches

An HTTP request has a method (`GET`, `POST`), a path (`/todos`), and sometimes a body. The API responds with a status code and, for these routes, JSON:

| Request | Purpose | Expected response |
|---|---|---|
| `GET /health` | Check that the server responds | `200 OK` with `{"status":"ok"}` |
| `GET /todos` | List todos | `200 OK` with a JSON array |
| `POST /todos` | Create a todo from `{"title":"..."}` | `201 Created` with the new todo |
| `GET /todos/{id}` | Find one todo | `200 OK`, or `404 Not Found` |

A blank title or a body that cannot be decoded is rejected with `400 Bad Request`.

## 3. Follow one request through the code

Read the files in this order:

1. **`Todo.scala`** — `Todo` is the response model and `NewTodo` is the create-request model. The Circe codecs convert between these Scala values and JSON.
2. **`TodoStore.scala`** — a small in-memory repository. `Ref[IO, TodoState]` safely holds state that may change while the program runs. `IO[A]` describes a computation that can produce an `A`; it does not run merely because it was described.
3. **`TodoRoutes.scala`** — http4s matches method/path pairs and builds a response. Each route returns an `IO[Response[IO]]`; the effect describes the work of reading or updating the store and creating the response.
4. **`TodoApi.scala`** — the application entry point wires the store and routes into an Ember HTTP server.
5. **`src/test/scala/lectures/part6backend/TodoRoutesSuite.scala`** — tests call the routes directly without opening a network port. Read the tests before adding behavior; they show how to make a request and check its response.

### Trace the create route

For `POST /todos`, http4s first matches the request path and method. The body decoder turns JSON into `NewTodo`. The route checks the title, asks the store to create a `Todo`, encodes that value as JSON, and returns `201 Created`. The `IO` type makes the state-changing work explicit, and `Ref.modify` updates the state atomically.

## 4. Build it yourself

Make a small change, run `sbt test`, and check the API with `curl` before moving to the next task:

1. Add a `DELETE /todos/{id}` route and a `remove` operation on `TodoStore`. Return `204 No Content` when an item is removed and `404 Not Found` if it does not exist.
2. Add a `completed: Boolean` field to the create request with a default of `false`. Decide how your JSON decoder should behave if the field is omitted.
3. Add a `PUT /todos/{id}/complete` route that marks an existing todo complete. Keep the existing immutable `Todo` case class and update the store state with a copied value.
4. Add tests for each success case and each missing-item case. Test the route behavior, not only the repository method.
5. Add a query parameter to filter completed todos. Explain what should happen for an invalid query value.

Do not add a database yet. First make sure you can describe the request, route, state change, and response for each endpoint.

## 5. Interview checkpoints

- What is an HTTP route, and how do method and path determine which route handles a request?
- Why do create and fetch use different HTTP methods and status codes?
- What does `IO[A]` represent? When does the work it describes run?
- Why does `Ref` fit this small example, and why should an application not store its only copy of important production data in process memory?
- What does a JSON decoder do? Why should malformed input become a client error rather than an unhandled server failure?
- How does a route test differ from a test that starts a real HTTP server?
- What is the difference between `404 Not Found` and `400 Bad Request` in this API?

## Next steps

Once you can explain and extend these routes, useful follow-on topics are database persistence, configuration, structured logging, integration tests, and deployment. Treat those as separate lessons: each introduces additional failure modes and should be added only after the in-memory API is clear.
