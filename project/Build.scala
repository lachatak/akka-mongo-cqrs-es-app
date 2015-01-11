import sbt._
import Keys._


object Build extends Build with BuildExtra {

  import BuildSettings._

  override lazy val settings = super.settings :+ {
    shellPrompt := { s => "[" + scala.Console.BLUE + Project.extract(s).currentProject.id + scala.Console.RESET + "] $ "}
  }

  lazy val akkamongocqrses = Project("akkamongocqrses", file("akkamongocqrses"))
    .settings(basicSettings: _*)
    .settings(libraryDependencies ++= Dependencies.akkamongocqrses)

}