import org.scalajs.dom
import org.scalajs.dom.HTMLTextAreaElement
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import io.circe.Decoder
import io.circe.parser.*

/** Imports deck lists from Mox Field by fetching via a CORS proxy.
 *
 *  Mox Field's API is Cloudflare-protected and blocks direct browser requests (no CORS headers).
 *  This fetches through r.jina.ai, a reader service with good IP reputation that bypasses both
 *  CORS and Cloudflare. The response may be wrapped in { data: { content: "..." } } which we unwrap.
 */
class MoxFieldImporter {

  import MoxFieldImporter.*

  def importDeck(url: String, status: dom.Element, textarea: HTMLTextAreaElement): Unit =
    extractDeckId(url) match {
      case None =>
        status.textContent = InvalidUrlMessage
      case Some(deckId) =>
        status.textContent = FetchingMessage
        fetchDeck(deckId, status, textarea)
    }

  private def fetchDeck(deckId: String, status: dom.Element, textarea: HTMLTextAreaElement): Unit =
    fetchJson(s"$MoxFieldApiBase$deckId")
      .flatMap(text => parseDeckJson(text) match {
        case Right(json) => Future.successful(json)
        case Left(e)     => Future.failed(e)
      })
      .onComplete {
        case scala.util.Success(deck) =>
          val lines = allCardLines(deck)
          if (lines.nonEmpty) {
            textarea.value = lines.mkString("\n")
            status.textContent = s"Imported ${lines.size} cards. Click Search to find them."
          } else {
            status.textContent = NoCardsMessage
          }
        case scala.util.Failure(e) =>
          status.textContent = FailedMessage + e.getMessage
      }

  private def fetchJson(api: String): Future[String] =
    for {
      response <- dom.fetch(s"$CorsProxyBase$api", jsonGet).toFuture
      _ <- if (response.ok) Future.successful(()) else Future.failed(new Exception(s"HTTP ${response.status}"))
      text <- response.text().toFuture
    } yield text

  private def jsonGet: dom.RequestInit =
    val h = new dom.Headers()
    h.append("Accept", "application/json")
    new dom.RequestInit {
      method = dom.HttpMethod.GET
      this.headers = h
    }

  private def parseDeckJson(text: String): Either[Throwable, Deck] =
    val jsonStr = extractJson(text)
    jsonStr match
      case None => Left(new Exception(NoJsonMessage))
      case Some(raw) =>
        parse(raw).left.map(e => new Exception(e.getMessage)).flatMap(decodeDeck)

  private def extractJson(text: String): Option[String] =
    val idx = text.indexOf("{")
    if (idx == -1) None
    else
      val lastIdx = text.lastIndexOf("}")
      if (lastIdx <= idx) None
      else Some(text.substring(idx, lastIdx + 1))
}

object MoxFieldImporter {
  private val MoxFieldApiBase = "https://api2.moxfield.com/v2/decks/all/"
  private val CorsProxyBase = "https://r.jina.ai/"
  private val DeckUrlPattern = """moxfield\.com/decks/([a-zA-Z0-9-]+)""".r

  private val InvalidUrlMessage = "Invalid Mox Field URL. Expected format: https://moxfield.com/decks/{deckId}"
  private val FetchingMessage = "Fetching deck from Mox Field..."
  private val NoCardsMessage = "No cards found in Mox Field deck."
  private val FailedMessage = "Failed to fetch from Mox Field: "
  private val NoJsonMessage = "No JSON in response from proxy"
  private val UnableToExtractMessage = "Unable to extract Mox Field JSON from response"

  case class DeckEntry(quantity: Int, card: Card)
  case class Card(name: String)
  case class Deck(mainboard: Map[String, DeckEntry], sideboard: Map[String, DeckEntry], commanders: Map[String, DeckEntry])

  object Deck {
    implicit val cardDecoder: Decoder[Card] = Decoder.forProduct1("name")(Card.apply)
    implicit val entryDecoder: Decoder[DeckEntry] = Decoder.forProduct2("quantity", "card")(DeckEntry.apply)
    implicit val decoder: Decoder[Deck] = Decoder.forProduct3("mainboard", "sideboard", "commanders")(Deck.apply)
  }

  private def extractDeckId(url: String): Option[String] =
    DeckUrlPattern.findFirstMatchIn(url).map(_.group(1))

  private def decodeDeck(json: io.circe.Json): Either[Throwable, Deck] =
    json.as[Deck] match {
      case Right(deck) => Right(deck)
      case Left(_) =>
        json.hcursor.downField("data").downField("content").as[String] match {
          case Right(content) => io.circe.parser.parse(content).left.map(e => new Exception(e.getMessage)).flatMap(_.as[Deck].left.map(e => new Exception(e.getMessage)))
          case Left(_) => Left(new Exception(UnableToExtractMessage))
        }
    }

  private def allCardLines(deck: Deck): List[String] =
    boardLines(deck.mainboard) ++ boardLines(deck.sideboard) ++ boardLines(deck.commanders)

  private def boardLines(entries: Map[String, DeckEntry]): List[String] =
    entries.map { case (_, entry) => s"${entry.quantity} ${entry.card.name}" }.toList
}
