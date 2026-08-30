import cats.effect.*
import scala.concurrent.{ExecutionContext, Future}

import com.sksamuel.elastic4s.*
import com.sksamuel.elastic4s.ElasticDsl.*
import com.sksamuel.elastic4s.http.JavaClient

class IOElasticClient(client: ElasticClient[Future]) {
  def execute[T, U](request: T)(using Handler[T, U], CommonRequestOptions): IO[Response[U]] =
    IO.fromFuture(IO(client.execute(request)))

  def close(): IO[Unit] = IO.blocking(client.close())
}

object IOElasticClient {
  def fromEnv(): Resource[IO, IOElasticClient] = {
    val rawUrl = sys.env.getOrElse("BONSAI_URL",
      throw new RuntimeException("BONSAI_URL environment variable is not set"))
    Resource.make(
      IO.blocking {
        implicit val ec: ExecutionContext = ExecutionContext.global
        val uri = java.net.URI.create(rawUrl)
        val host = uri.getHost
        val port = if (uri.getPort != -1) uri.getPort else 443
        val endpoint = ElasticNodeEndpoint("https", host, port, None)
        val props = ElasticProperties(Seq(endpoint))
        val client = Option(uri.getUserInfo) match
          case Some(userInfo) =>
            val Array(user, pass) = userInfo.split(":", 2)
            val credentialsProvider = new org.apache.http.impl.client.BasicCredentialsProvider()
            credentialsProvider.setCredentials(
              org.apache.http.auth.AuthScope.ANY,
              new org.apache.http.auth.UsernamePasswordCredentials(user, pass)
            )
            JavaClient(
              props,
              requestConfigCallback = identity,
              httpClientConfigCallback = builder => builder.setDefaultCredentialsProvider(credentialsProvider)
            )
          case None =>
            JavaClient(props)
        new IOElasticClient(ElasticClient[Future](client))
      }
    )(c => c.close())
  }
}
