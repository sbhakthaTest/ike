package org.allenai.ike.ml.subsample

import org.allenai.common.testkit.{ ScratchDirectory, UnitSpec }
import org.allenai.ike._
import org.allenai.ike.index.TestData
import org.allenai.ike.ml.{ SimilarPhrasesSearcherStub, TokenizedQuery }

import nl.inl.blacklab.search.{ Hits, Span }

import scala.collection.JavaConverters._

class TestGeneralizedQuerySampler extends UnitSpec with ScratchDirectory {

  TestData.createTestIndex(scratchDir)
  val searcher = TestData.testSearcher(scratchDir)
  val ss = new SimilarPhrasesSearcherStub()

  def hitToCaptures(hits: Hits, captureName: String): Seq[String] = {
    hits.asScala.map(hit => {
      val kwic = hits.getKwic(hit)
      val capture = hits.getCapturedGroupMap(hit).get(captureName)
      val captureKwic = capture.start - hit.start
      kwic.getMatch("word").get(captureKwic)
    }).toSeq
  }

  def hitsToStrings(hits: Hits): Seq[String] = {
    hits.asScala.map(hit => {
      hits.getKwic(hit).getMatch("word").asScala.mkString(" ")
    }).toSeq
  }

  def span2tuple(span: Span): (Int, Int) = (span.start, span.end)

  def buildTable(positive: Seq[String], negative: Seq[String]): Table = {
    Table("testTable", Seq("testCol"),
      positive.map(x => TableRow(Seq(TableValue(x.split(" ").map(QWord.apply))))),
      negative.map(x => TableRow(Seq(TableValue(x.split(" ").map(QWord.apply))))))
  }

  it should "capture misses correctly" in {
    val testQuery = QSeq(Seq(
      QWord("I"),
      QDisj(Seq(QWord("like"), QWord("tastes"))),
      QNamed(QDisj(Seq(QPos("DT"), QPos("JJ"), QPos("NNS"))), "c1")
    ))
    val tokenized = TokenizedQuery.buildWithGeneralizations(
      testQuery,
      Seq("c1"), ss, Seq(searcher), 10
    )
    val cnames = tokenized.getNames :+ "c1"
    val hits = GeneralizedQuerySampler(1, 100).getSample(tokenized, searcher,
      Table("", Seq("c1"), Seq(), Seq()), Map(), Map())
    // Match I like mango
    assertResult(0)(hits.get(0).doc)
    assertResult(cnames.zip(Seq((0, 1), (1, 2), (-2, -3), (2, 3))).toMap)(
      hits.getCapturedGroupMap(hits.get(0)).asScala.mapValues(span2tuple)
    )
    // Match It tastes great
    assertResult(1)(hits.get(1).doc)
    assertResult(cnames.zip(Seq((0, -1), (1, 2), (2, 3), (2, 3))).toMap)(
      hits.getCapturedGroupMap(hits.get(1)).asScala.mapValues(span2tuple)
    )
    //     Match I hate those
    assertResult(2)(hits.get(2).doc)
    assertResult(cnames.zip(Seq((0, 1), (-1, -2), (2, 3), (2, 3))).toMap)(
      hits.getCapturedGroupMap(hits.get(2)).asScala.mapValues(span2tuple)
    )
  }

  it should "Limit two column queries correctly" in {
    val startingQuery =
      QueryLanguage.parse("(?<c1> {I, hate}) {those,hate} (?<c2> {mango, bananas, great})").get
    val table = Table(
      "test",
      Seq("c1", "c2"),
      Seq(
        TableRow(Seq(TableValue(Seq(QWord("I"))), TableValue(Seq(QWord("mango"))))),
        TableRow(Seq(TableValue(Seq(QWord("hate"))), TableValue(Seq(QWord("those")))))
      ),
      Seq(
        TableRow(Seq(TableValue(Seq(QWord("hate"))), TableValue(Seq(QWord("bananas")))))
      )
    )
    val tokenized = TokenizedQuery.buildWithGeneralizations(
      startingQuery, Seq("c1", "c2"), ss, Seq(searcher), 10
    )
    val expectedResults = Seq(
      "I like mango",
      "hate those bananas"
    )
    val hits = GeneralizedQuerySampler(2, 100).getLabelledSample(
      tokenized,
      searcher, table, Map(), Map(), 0, 0
    )
    assertResult(expectedResults)(hitsToStrings(hits))
    assertResult(2)(hits.size)
  }

  it should "limit one column queries correctly" in {
    val startingQuery =
      QueryLanguage.parse("{those,like} (?<c1> {mango, bananas, great})").get
    val table = Table(
      "test",
      Seq("c1"),
      Seq(
        TableRow(Seq(TableValue(Seq(QWord("mango"))))),
        TableRow(Seq(TableValue(Seq(QWord("those")))))
      ),
      Seq(TableRow(Seq(TableValue(Seq(QWord("bananas"))))))
    )
    val tokenized = TokenizedQuery.buildWithGeneralizations(startingQuery, Seq("c1"),
      ss, Seq(searcher), 10)
    val expectedResults = Seq(
      "like mango",
      "those bananas"
    )
    val hits = GeneralizedQuerySampler(2, 100).getLabelledSample(
      tokenized,
      searcher, table, Map(), Map(), 0, 0
    )
    assertResult(expectedResults)(hitsToStrings(hits))
    assertResult(2)(hits.size)
  }
}
