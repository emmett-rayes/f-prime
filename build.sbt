ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.1"

lazy val root = (project in file("."))
    .settings(
      name := "f-prime",
      scalacOptions += "-explain",
      scalacOptions += "-feature",
      scalacOptions += "-source:future",
      scalacOptions += "-language:implicitConversions",
      libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test
    )
