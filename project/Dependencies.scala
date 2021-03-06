import org.allenai.plugins.CoreDependencies

import sbt._
import sbt.Keys._

object Dependencies extends CoreDependencies {
  val luceneGroup = "org.apache.lucene"
  val luceneVersion = "4.2.1"
  def lucene(part: String) = luceneGroup % s"lucene-${part}" % luceneVersion
  val allenAiDatastore = "org.allenai.datastore" %% "datastore" % "1.0.7"
  val nlpstackVersion = "1.10"
  def nlpstackModule(id: String) = "org.allenai.nlpstack" %% s"nlpstack-${id}" % nlpstackVersion
}
