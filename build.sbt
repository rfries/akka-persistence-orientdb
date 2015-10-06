name := "akka-persistence-orientdb"

organization := "org.funobjects"

version := "1.0.0-RC2"

scalaVersion := "2.11.7"

resolvers += Resolver.sonatypeRepo("public")

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

fork in Test := true

libraryDependencies ++= {
  object v {
    val akka        = "2.4.0"
    val orientDb    = "2.1.3"
    val scalaXml    = "1.0.3"
  }
  Seq(
    "com.orientechnologies"   %  "orientdb-core"        % v.orientDb    withSources(),
    "com.typesafe.akka"       %% "akka-persistence"     % v.akka        withSources(),
    "org.iq80.leveldb"            % "leveldb"          % "0.7",
    "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
    "org.scala-lang.modules"  %% "scala-xml"            % v.scalaXml    % "test",
    "com.typesafe.akka"       %% "akka-persistence-tck" % v.akka        % "test" withSources(),
    "com.typesafe.akka"       %% "akka-testkit"         % v.akka        % "test" withSources()
  )
}

