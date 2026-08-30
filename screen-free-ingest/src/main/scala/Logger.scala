import cats.effect.*

class Logger(val log: String => IO[Unit])
