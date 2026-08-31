object DeckParser {
  case class DeckEntry(name: String, quantity: Int)

  def parseDeckList(input: String): (List[DeckEntry], List[String]) = {
    val lines = input.split("\n").map(_.trim).filter(_.nonEmpty).toList
    val isMoxfield = lines.exists(_.contains("("))
    val errors = scala.collection.mutable.ListBuffer[String]()
    val entries = lines.flatMap { line =>
      val qtyMatch = """^(\d+)""".r.findFirstMatchIn(line)
      val quantity = qtyMatch.map(_.group(1).toInt).getOrElse(1)
      val noQty = line.replaceFirst("^\\d+[xX]?\\s+", "")
      val name =
        if (isMoxfield) {
          val noSet = noQty.replaceFirst("\\(.*?\\)\\s*", "")
          val noFoil = noSet.replaceAll("\\*F\\*", "")
          if (noFoil.matches(".*\\d+.*")) noFoil.replaceFirst("\\s+\\S+$", "").trim else noFoil.trim
        } else {
          noQty.trim
        }
      if (name.isEmpty || !name.exists(_.isLetter)) {
        errors += line
        None
      } else {
        Some(DeckEntry(name, quantity))
      }
    }
    (entries, errors.toList)
  }
}
