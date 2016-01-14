package com.mesosphere.cosmos

import com.github.retronym.SbtOneJar._
import sbt.Keys._
import sbt._

object CosmosBuild extends Build {

  lazy val projectScalaVersion = "2.11.7"
  lazy val projectVersion = "0.1.0-SNAPSHOT"

  object V {
    val circe = "0.2.1"
    val finch = "0.9.2"
    val finchServer = "0.9.0"
    val logback = "1.1.3"
    val mustache = "0.9.1"
    val scalaUri = "0.4.11"
    val scalaTest = "2.2.4"
    val scalaCheck = "1.10.0"
  }

  object Deps {
    val circe = Seq(
      "io.circe" %% "circe-core" % V.circe,
      "io.circe" %% "circe-generic" % V.circe,
      "io.circe" %% "circe-parse" % V.circe
    )

    val finch = Seq(
      "com.github.finagle" %% "finch-core" % V.finch,
      "com.github.finagle" %% "finch-circe" % V.finch
    )

    val finchServer = Seq(
      "io.github.benwhitehead.finch" %% "finch-server" % V.finchServer
    )

    val finchTest = Seq(
      "com.github.finagle" %% "finch-test" % V.finch % "test"
    )

    val logback = Seq(
      "ch.qos.logback" % "logback-classic" % V.logback
    )

    val mustache = Seq(
      "com.github.spullara.mustache.java" % "compiler" % V.mustache
    )

    val scalaUri = Seq(
      "com.netaporter" %% "scala-uri" % V.scalaUri
    )

    val scalaTest = Seq(
      "org.scalatest"       %% "scalatest"        % V.scalaTest     % "test"
    )

  }

  val extraSettings = Defaults.coreDefaultSettings

  val sharedSettings = extraSettings ++ Seq(
    organization := "mesosphere.mm.collector",
    scalaVersion := projectScalaVersion,
    version := projectVersion,

    resolvers ++= Seq(
      "Clojars Repository" at "http://clojars.org/repo",
      "Conjars Repository" at "http://conjars.org/repo",
      "Twitter Maven" at "http://maven.twttr.com",
      "Finch.io" at "http://repo.konfettin.ru",
      "finch-server" at "http://storage.googleapis.com/benwhitehead_me/maven/public"
    ),

    libraryDependencies ++= Deps.scalaTest,

    javacOptions in Compile ++= Seq(
      "-source", "1.8",
      "-target", "1.8",
      "-Xlint:unchecked",
      "-Xlint:deprecation"
    ),

    scalacOptions ++= Seq(
      "-deprecation", // Emit warning and location for usages of deprecated APIs.
      "-encoding", "UTF-8",
      "-explaintypes",
      "-feature", // Emit warning and location for usages of features that should be imported explicitly.
      "-target:jvm-1.8",
      "-unchecked", // Enable additional warnings where generated code depends on assumptions.
      "-Xfuture",
      "-Xlint", // Enable recommended additional warnings.
      "-Yresolve-term-conflict:package",
      "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
      "-Ywarn-dead-code",
      "-Ywarn-inaccessible",
      "-Ywarn-infer-any",
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-Ywarn-numeric-widen",
      "-Ywarn-unused",
      "-Ywarn-unused-import",
      "-Ywarn-value-discard"
    ),

    scalacOptions in (Compile, console) ~= (_ filterNot (_ == "-Ywarn-unused-import")),

    scalacOptions in (Test, console) ~= (_ filterNot (_ == "-Ywarn-unused-import")),

    // Publishing options:
    publishMavenStyle := true,

    pomIncludeRepository := { x => false },

    publishArtifact in Test := false,

    parallelExecution in ThisBuild := false,

    parallelExecution in Test := false,

    fork := false
  )

  lazy val cosmos = Project(
    id = "cosmos",
    base = file("."),
    settings = sharedSettings ++ oneJarSettings ++ Seq(
      mainClass in oneJar := Some("com.mesosphere.cosmos.Cosmos"),
      libraryDependencies ++=
        Deps.circe
        ++ Deps.finch
        ++ Deps.finchServer
        ++ Deps.finchTest
        ++ Deps.logback
        ++ Deps.mustache
        ++ Deps.scalaTest
        ++ Deps.scalaUri
    )
  )

  //////////////////////////////////////////////////////////////////////////////
  // BUILD TASKS
  //////////////////////////////////////////////////////////////////////////////

  sys.env.get("TEAMCITY_VERSION") match {
    case None => // no-op
    case Some(teamcityVersion) =>
      // add some info into the teamcity build context so that they can be used
      // by later steps
      reportParameter("SCALA_VERSION", projectScalaVersion)
      reportParameter("PROJECT_VERSION", projectVersion)
  }

  def reportParameter(key: String, value: String): Unit = {
    println(s"##teamcity[setParameter name='env.SBT_$key' value='$value']")
    println(s"##teamcity[setParameter name='system.sbt.$key' value='$value']")
  }
}