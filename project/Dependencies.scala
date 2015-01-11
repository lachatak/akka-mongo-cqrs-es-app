import sbt._

object Version {

  val AkkaVersion           = "2.3.6"
  val EmbeddedMongoVersion  = "1.46.1"
  val ScalazVersion         = "7.1.0"
  val PluginVersion         = "0.7.4"
  val ScalaTestVersion      = "2.2.2"
  val MockitoVersion         = "1.9.5"
}

object Library {
  val akka            = "com.typesafe.akka"     %% "akka-persistence-experimental" % Version.AkkaVersion
  val akkaTestkit     = "com.typesafe.akka"     %% "akka-testkit"                  % Version.AkkaVersion
  val akkaMongo       = "com.github.ddevore"    %% "akka-persistence-mongo-casbah" % Version.PluginVersion
  val embeddedMongo   = "de.flapdoodle.embed"   % "de.flapdoodle.embed.mongo"      % Version.EmbeddedMongoVersion
  val scalaZ          = "org.scalaz"            %% "scalaz-core"                   % Version.ScalazVersion
  val mockito         = "org.mockito"           %  "mockito-core"                  % Version.MockitoVersion
  val scalaTest       = "org.scalatest"         %% "scalatest"                     % Version.ScalaTestVersion
}

object Dependencies {

  import Library._

  val akkamongocqrses = List(
    akka,
    akkaMongo,
    embeddedMongo,
    scalaZ,
    mockito       % "test",
    akkaTestkit   % "test",
    scalaTest     % "test"
  )
}
