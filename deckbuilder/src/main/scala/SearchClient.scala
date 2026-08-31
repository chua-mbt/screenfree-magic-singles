import org.scalajs.dom
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits._
import scala.concurrent.ExecutionContext.Implicits.global

class SearchClient(esUrl: String) {

  import SearchClient.*

  def searchCards(entries: List[DeckParser.DeckEntry]): Future[Either[Error, List[Card]]] = {
    val query = SearchClient.buildQuery(entries)

    val reqHeaders = new dom.Headers()
    reqHeaders.append("Content-Type", "application/json")

    val init = new dom.RequestInit {
      method = dom.HttpMethod.POST
      this.headers = reqHeaders
      body = query.noSpaces
    }

    for {
      response <- dom.fetch(esUrl, init).toFuture
      text <- response.text().toFuture
      decoded = decode[SearchResponse](text)
      result = decoded.map(_.hits.hits.map(_._source))
    } yield result
  }
}

object SearchClient {
  private val searchSize = 300

  private def buildQuery(entries: List[DeckParser.DeckEntry]): Json = {
    val should = entries.map { entry =>
      Json.obj("match_phrase" -> Json.obj("title" -> Json.fromString(entry.name)))
    }

    Json.obj(
      "size" -> Json.fromInt(searchSize),
      "query" -> Json.obj(
        "bool" -> Json.obj(
          "should" -> Json.fromValues(should)
        )
      )
    )
  }

  case class Card(
    id: String,
    title: String,
    set: String,
    imageUrl: String,
    variants: List[Variant]
  )

  object Card {
    implicit val decoder: Decoder[Card] =
      Decoder.forProduct5("id", "title", "set", "imageUrl", "variants")(Card.apply)
  }

  case class Variant(
    id: String,
    condition: String,
    language: String,
    printing: String,
    sku: String,
    available: Boolean,
    price: String
  )

  object Variant {
    implicit val decoder: Decoder[Variant] =
      Decoder.forProduct7("variantId", "condition", "language", "printing", "sku", "available", "price")(Variant.apply)
  }

  private case class SearchResponse(hits: Hits)

  private object SearchResponse {
    implicit val decoder: Decoder[SearchResponse] = Decoder.forProduct1("hits")(SearchResponse.apply)
  }

  private case class Hits(hits: List[Hit])

  private object Hits {
    implicit val decoder: Decoder[Hits] = Decoder.forProduct1("hits")(Hits.apply)
  }

  private case class Hit(_source: Card)

  private object Hit {
    implicit val decoder: Decoder[Hit] = Decoder.forProduct1("_source")(Hit.apply)
  }
}
