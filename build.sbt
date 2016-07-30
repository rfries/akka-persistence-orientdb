name := "akka-persistence-orientdb"

organization := "org.funobjects"

version := "1.0.0-RC3"

scalaVersion := "2.11.8"

resolvers += Resolver.sonatypeRepo("public")

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

fork in Test := true

libraryDependencies ++= {
  object v {
    val akka        = "2.4.7"
    val levelDb     = "0.7"       // required by akka persistence
    val levelDbJni  = "1.8"       // required by akka persistence
    val orientDb    = "2.2.0"
    val scalaXml    = "1.0.5"
  }
  Seq(
    "com.orientechnologies"     %  "orientdb-core"        % v.orientDb    withSources(),
    "com.typesafe.akka"         %% "akka-persistence"     % v.akka        withSources(),
    "org.iq80.leveldb"          %  "leveldb"              % v.levelDb,
    "org.fusesource.leveldbjni" %  "leveldbjni-all"       % v.levelDbJni,
    "org.scala-lang.modules"    %% "scala-xml"            % v.scalaXml    % "test",
    "com.typesafe.akka"         %% "akka-persistence-tck" % v.akka        % "test" withSources(),
    "com.typesafe.akka"         %% "akka-testkit"         % v.akka        % "test" withSources()
  )
}

