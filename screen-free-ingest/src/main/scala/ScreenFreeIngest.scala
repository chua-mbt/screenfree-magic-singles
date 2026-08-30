import cats.effect.*
import cats.syntax.all.*
import org.http4s.client.Client
import org.http4s.ember.client.*

@main def run(): Unit = {
  val clients = (EmberClientBuilder.default[IO].build, IOElasticClient.fromEnv()).tupled
  val ingestion = clients.use { case (httpClient, esClient) =>
    val logger = new Logger(IO.println)
    val shopifyApi = ShopifyApi(httpClient, logger)
    val index = MagicSinglesIndex(esClient, logger)
    for
      _ <- logger.log("Fetching Screen Free Games MTG singles...")
      products <- shopifyApi.fetchAllPages()
      _ <- logger.log(s"Total products fetched: ${products.size}")
      _ <- index.wipeAndCreate()
      _ <- logger.log(s"Indexing ${products.size} products into Elasticsearch...")
      _ <- index.indexProducts(products).whenA(products.nonEmpty)
      _ <- logger.log("Done.")
    yield ()
  }
  import cats.effect.unsafe.implicits.global
  ingestion.unsafeRunSync()
}