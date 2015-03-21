name := "akka-persistence-orientdb"

organization := "org.funobjects"

version := "0.9.0-SNAPSHOT"

scalaVersion := "2.11.6"

resolvers += Resolver.sonatypeRepo("public")

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

fork in Test := true

libraryDependencies ++= {
  object v {
    val akka        = "2.3.9"
    val orientDb    = "2.0.5"
    val scalaXml    = "1.0.3"
  }
  Seq(
    "com.orientechnologies"   %  "orientdb-core"                      % v.orientDb    withSources(),
    "com.typesafe.akka"       %% "akka-persistence-experimental"      % v.akka        withSources(),
    "org.scala-lang.modules"  %% "scala-xml"                          % v.scalaXml    % "test",
    "com.typesafe.akka"       %% "akka-persistence-tck-experimental"  % v.akka        % "test" withSources(),
    "com.typesafe.akka"       %% "akka-testkit"                       % v.akka        % "test" withSources()
  )
}

