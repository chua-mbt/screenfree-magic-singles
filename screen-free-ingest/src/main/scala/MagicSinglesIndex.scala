import cats.effect.*
import cats.syntax.all.*
import com.sksamuel.elastic4s.*
import com.sksamuel.elastic4s.ElasticDsl.*
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import com.sksamuel.elastic4s.requests.mappings.MappingDefinition

class MagicSinglesIndex(esClient: IOElasticClient, logger: Logger) {

  import MagicSinglesIndex.*

  def wipeAndCreate(): IO[Unit] =
    for
      _ <- logger.log(s"Wiping index '$indexName'...")
      _ <- esClient.execute(deleteIndex(indexName)).void.handleError(_ => ())
      _ <- logger.log(s"Creating index '$indexName'...")
      _ <- esClient.execute(createIndex(indexName).mapping(MagicSinglesIndex.mapping)).void
    yield ()

  def indexProducts(products: List[ShopifyApi.Product]): IO[Unit] =
    products
      .map(product => indexInto(indexName).id(product.id.toString).fields(product.toDocument))
      .grouped(batchSize)
      .toList
      .traverse_(executeBulk)

  private def executeBulk(batch: Seq[IndexRequest]): IO[Unit] =
    esClient.execute(bulk(batch).refreshImmediately).flatMap {
      case RequestFailure(_, _, _, error) =>
        logger.log(s"Index error: ${error.reason}") *> IO.raiseError(new RuntimeException(error.reason))
      case RequestSuccess(_, _, _, _) =>
        IO.unit
    }
}

object MagicSinglesIndex {
  private val indexName = "magic-singles"
  private val batchSize = 50

  private val mapping: MappingDefinition = properties(
    textField("shopifyId"),
    textField("title"),
    textField("set"),
    textField("productType"),
    textField("tags"),
    textField("imageUrl"),
    nestedField("variants").fields(
      keywordField("variantId"),
      textField("condition"),
      textField("language"),
      textField("printing"),
      keywordField("sku"),
      booleanField("available"),
      keywordField("price")
    )
  )
}
