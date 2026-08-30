ThisBuild / scalaVersion := "3.3.3"

lazy val root = (project in file("."))
  .aggregate(`screen-free-ingest`)

// lazy val deckbuilder = (project in file("deckbuilder"))
//   .enablePlugins(ScalaJSPlugin)
//   .settings(
//     libraryDependencies ++= Seq(
//       "org.scala-js" %%% "scalajs-dom" % "2.8.1"
//     )
//   )

lazy val `screen-free-ingest` = (project in file("screen-free-ingest"))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel"     %% "cats-core"           % "2.13.0",
      "org.typelevel"     %% "cats-effect"          % "3.7.1",
      "org.http4s"        %% "http4s-ember-client"  % "0.23.36",
      "org.http4s"        %% "http4s-circe"         % "0.23.36",
      "org.http4s"        %% "http4s-dsl"           % "0.23.36",
      "io.circe"          %% "circe-generic"        % "0.14.16",
      "nl.gn0s1s"         %% "elastic4s-core"       % "9.3.0",
      "nl.gn0s1s"         %% "elastic4s-client-esjava" % "9.3.0"
    )
  )
