import org.scalajs.dom
import org.scalajs.dom.*
import DeckParser.DeckEntry
import SearchClient.Card
import DOMIds.*

class Renderer(selectedCards: scala.collection.mutable.Map[String, String]) {

  def updateBanner(): Unit = {
    val banner = document.getElementById(Banner).asInstanceOf[HTMLElement]
    if (selectedCards.nonEmpty) {
      banner.style.display = "flex"
    } else {
      banner.style.display = "none"
    }
  }

  def updateCounts(entry: DeckEntry): Unit = {
    val selectedCount = selectedCards.values.count(_ == entry.name)
    val cells = document.querySelectorAll(s"[$DataEntryName='${entry.name}']")
    for (i <- 0 until cells.length) {
      val cell = cells(i).asInstanceOf[HTMLDivElement]
      val count = cell.querySelector(CardCount).asInstanceOf[HTMLElement]
      count.textContent = s"$selectedCount selected / ${entry.quantity} wanted"
    }
  }

  def renderMissing(missing: List[DeckEntry]): Unit = {
    val box = document.getElementById(MissingBox).asInstanceOf[HTMLElement]
    val list = document.getElementById(MissingList)
    if (missing.isEmpty) {
      box.style.display = "none"
    } else {
      box.style.display = "block"
      list.innerHTML = ""
      missing.foreach { entry =>
        val p = document.createElement("p")
        p.textContent = entry.name
        list.appendChild(p)
      }
    }
  }

  def renderResults(found: List[(DeckEntry, Card)], container: HTMLDivElement): Unit = {
    container.innerHTML = ""
    val grid = document.createElement("div").asInstanceOf[HTMLDivElement]
    grid.className = "grid"
    found.foreach { (entry, card) =>
      val variantId = card.variants.headOption.map(_.id).getOrElse("")
      val cell = document.createElement("div").asInstanceOf[HTMLDivElement]
      cell.className = "card-cell"
      cell.setAttribute("data-card-id", variantId)
      cell.setAttribute(DataEntryName, entry.name)
      cell.tabIndex = 0

      if (selectedCards.contains(variantId)) {
        cell.classList.add("selected")
      }

      cell.addEventListener("click", (e: dom.Event) => {
        e.preventDefault()
        if (selectedCards.contains(variantId)) {
          selectedCards.remove(variantId)
          cell.classList.remove("selected")
        } else {
          selectedCards(variantId) = entry.name
          cell.classList.add("selected")
        }
        updateBanner()
        updateCounts(entry)
      })

      val img = document.createElement("img").asInstanceOf[HTMLImageElement]
      img.src = card.imageUrl

      val info = document.createElement("div").asInstanceOf[HTMLDivElement]
      info.className = "card-info"
      info.textContent = s"${card.title} | ${card.set}"

      val price = document.createElement("div").asInstanceOf[HTMLDivElement]
      price.className = "card-price"
      val priceStr = card.variants.find(_.available).map(_.price).getOrElse("N/A")
      price.textContent = s"$$$priceStr"

      val count = document.createElement("div").asInstanceOf[HTMLDivElement]
      count.className = "card-count"
      val selectedCount = selectedCards.values.count(_ == entry.name)
      count.textContent = s"$selectedCount selected / ${entry.quantity} wanted"

      cell.appendChild(img)
      cell.appendChild(info)
      cell.appendChild(price)
      cell.appendChild(count)
      grid.appendChild(cell)
    }
    container.appendChild(grid)
    updateBanner()
  }
}
