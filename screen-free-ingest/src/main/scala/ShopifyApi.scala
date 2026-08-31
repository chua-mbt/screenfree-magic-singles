import cats.effect.*
import io.circe.*
import io.circe.generic.semiauto.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.client.Client

import scala.concurrent.duration.*

class ShopifyApi(client: Client[IO], logger: Logger) {

  import ShopifyApi.*

  def fetchPage(page: Int): IO[Either[Throwable, ProductPage]] = {
    val uri = Uri.unsafeFromString(s"$baseUrl?limit=$pageSize&page=$page")
    val request = Request[IO](Method.GET, uri)
    client
      .expect[ProductPage](request)
      .map(Right(_))
      .handleError(error => Left(error))
  }

  def fetchAllPages(page: Int = 1, allPages: List[Product] = Nil): IO[List[Product]] =
    fetchPage(page).flatMap {
      case Right(pageData) if pageData.products.nonEmpty =>
        val first = pageData.products.head.title
        val last = pageData.products.last.title
        logger.log(s"Page $page: ${pageData.products.size} products ($first — $last)") *>
          Temporal[IO].sleep(pageDelay) *>
          fetchAllPages(page + 1, allPages ++ pageData.products)
      case Right(_) =>
        logger.log(s"Page $page: empty, done") *> IO.pure(allPages)
      case Left(err) =>
        logger.log(s"Page $page: error - ${err.getMessage}") *> IO.pure(allPages)
    }
}

object ShopifyApi {
  private val baseUrl = "https://screenfreegames.com/collections/magic-singles/products.json"
  private val pageSize = 250 // https://shopify.dev/docs/api/admin-rest/usage/pagination — max limit is 250
  private val pageDelay = 1.second

  def apply(client: Client[IO], logger: Logger): ShopifyApi = new ShopifyApi(client, logger)

  case class ProductVariant(
    id: Long,
    title: String,
    option1: String,
    option2: Option[String],
    option3: Option[String],
    sku: Option[String],
    available: Boolean,
    price: String
  ) {
    def toDocument: Map[String, Any] = Map(
      "variantId" -> id.toString,
      "condition" -> option1,
      "language" -> option2.getOrElse(""),
      "printing" -> option3.getOrElse(""),
      "sku" -> sku.getOrElse(""),
      "available" -> available,
      "price" -> price
    )
  }

  object ProductVariant {
    implicit val decoder: Decoder[ProductVariant] = deriveDecoder[ProductVariant]
    implicit val encoder: Encoder[ProductVariant] = deriveEncoder[ProductVariant]
  }

  case class ProductImage(id: Long, src: String, width: Int, height: Int)

  object ProductImage {
    implicit val decoder: Decoder[ProductImage] = deriveDecoder[ProductImage]
    implicit val encoder: Encoder[ProductImage] = deriveEncoder[ProductImage]
  }

  case class Product(
    id: Long,
    title: String,
    vendor: String,
    product_type: String,
    tags: List[String],
    variants: List[ProductVariant],
    images: List[ProductImage]
  ) {
    def toDocument: Map[String, Any] = Map(
      "shopifyId" -> id.toString,
      "title" -> title,
      "set" -> vendor,
      "productType" -> product_type,
      "tags" -> tags.mkString(","),
      "imageUrl" -> images.headOption.map(_.src).getOrElse(""),
      "variants" -> variants.map(_.toDocument)
    )
  }

  object Product {
    implicit val decoder: Decoder[Product] = deriveDecoder[Product]
    implicit val encoder: Encoder[Product] = deriveEncoder[Product]
  }

  case class ProductPage(products: List[Product])

  object ProductPage {
    implicit val decoder: Decoder[ProductPage] = deriveDecoder[ProductPage]
    implicit val encoder: Encoder[ProductPage] = deriveEncoder[ProductPage]
  }
}
