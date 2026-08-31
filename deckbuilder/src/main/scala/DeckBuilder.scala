import org.scalajs.dom
import org.scalajs.dom.*
import scala.concurrent.ExecutionContext.Implicits.global
import DeckParser.DeckEntry
import SearchClient.Card
import DOMIds.*

object DeckBuilder {
  private type VariantId = String
  private type CardName = String

  private val basicLands = Set("Swamp", "Island", "Mountain", "Plains", "Forest")
  private val proxyUrl = "https://black-lake-1633.chibiakaii.workers.dev/"
  private val cartBaseUrl = "https://screenfreegames.com/cart/"
  private val selectedCards = scala.collection.mutable.Map[VariantId, CardName]()
  private val searchClient = new SearchClient(proxyUrl)
  private val renderer = new Renderer(selectedCards)

  def main(args: Array[String]): Unit =
    document.addEventListener("DOMContentLoaded", (_: dom.Event) => setup())

  private def setup(): Unit = {
    val app = document.getElementById(App)
    app.innerHTML = s"""
      <div class="banner" id="$Banner" style="display:none">
        <span>Select cards below, then click to add to cart</span>
        <button id="$CartBtn">Open in Shopify</button>
      </div>
      <h1>Screen Free MTG Search</h1>
      <div class="top-row">
        <div class="input-col">
          <textarea id="$Decklist" placeholder="Paste deck list here..."></textarea>
          <div class="search-row">
            <button id="$SearchBtn">Search</button>
            <label class="filter-label">
              <input type="checkbox" id="$ExcludeLands" checked> Exclude basic lands
            </label>
          </div>
          <div id="$Status"></div>
          <div id="$Errors" class="errors"></div>
        </div>
        <div class="missing-col" id="$MissingBox" style="display:none">
          <h3>Missing</h3>
          <div id="$MissingList"></div>
        </div>
      </div>
      <div id="$Results"></div>
    """

    document.getElementById(SearchBtn).addEventListener("click", (_: dom.Event) => onSearch())
    document.getElementById(CartBtn).addEventListener("click", (_: dom.Event) => openCart())
  }

  private def onSearch(): Unit = {
    val textarea = document.getElementById(Decklist).asInstanceOf[HTMLTextAreaElement]
    val status = document.getElementById(Status)
    val results = document.getElementById(Results).asInstanceOf[HTMLDivElement]

    val input = textarea.value.trim
    if (input.isEmpty) {
      status.textContent = "Please paste a deck list."
      return
    }

    val (entries, errors) = DeckParser.parseDeckList(input)
    val errorsDiv = document.getElementById(Errors)
    if (errors.nonEmpty) {
      errorsDiv.innerHTML = errors.map(error => s"<p>$error</p>").mkString
    } else {
      errorsDiv.innerHTML = ""
    }

    if (entries.isEmpty) {
      status.textContent = "No valid card names found."
      return
    }

    val excludeLands = document.getElementById(ExcludeLands).asInstanceOf[HTMLInputElement].checked
    val filteredEntries = if (excludeLands) {
      entries.filterNot(entry => basicLands.contains(entry.name))
    } else {
      entries
    }

    if (filteredEntries.isEmpty) {
      status.textContent = "No cards to search for (all basic lands excluded)."
      return
    }

    if (filteredEntries.size > 300) {
      status.textContent = s"Deck too large (${filteredEntries.size} cards). Maximum is 300."
      return
    }

    status.textContent = s"Searching for ${filteredEntries.size} cards..."
    results.innerHTML = ""

    searchClient.searchCards(filteredEntries).foreach {
      case Right(cards) =>
        val found = matchEntries(filteredEntries, cards)
        val missing = filteredEntries.filterNot(entry => found.exists(_._1.name == entry.name))
        val distinctFound = filteredEntries.size - missing.size
        status.textContent = s"Found $distinctFound of ${filteredEntries.size} cards (${found.size} printings). ${missing.size} missing."
        renderer.renderMissing(missing)
        renderer.renderResults(found, results)
        renderer.updateBanner()
      case Left(error) =>
        status.textContent = s"Search failed: ${error.getMessage}"
    }
  }

  private def openCart(): Unit = {
    if (selectedCards.isEmpty) return
    val variantIds = selectedCards.keys.toSeq
    val url = s"$cartBaseUrl${variantIds.mkString(":1,")}:1"
    dom.window.open(url, "_blank")
  }

  private def matchEntries(entries: List[DeckEntry], cards: List[Card]): List[(DeckEntry, Card)] = 
    entries.flatMap { entry =>
      cards.filter(_.title.toLowerCase.contains(entry.name.toLowerCase)).map(entry -> _)
    }
}
