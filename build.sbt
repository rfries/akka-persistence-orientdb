name := "akka-persistence-orientdb"

organization := "org.funobjects"

version := "1.0.0-RC3"

scalaVersion := "2.11.8"

resolvers += Resolver.sonatypeRepo("public")

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

fork in Test := true

libraryDependencies ++= {
  object v {
    val akka        = "2.4.8"
    val levelDbJni  = "1.8"       // required by akka persistence
    val orientDb    = "2.2.6"
    val scalaXml    = "1.0.5"
  }
  Seq(
    "com.orientechnologies"     %  "orientdb-core"        % v.orientDb    withSources(),
    "com.typesafe.akka"         %% "akka-persistence"     % v.akka        withSources(),
    "org.fusesource.leveldbjni" %  "leveldbjni-all"       % v.levelDbJni  % "test",
    "org.scala-lang.modules"    %% "scala-xml"            % v.scalaXml    % "test",
    "com.typesafe.akka"         %% "akka-persistence-tck" % v.akka        % "test" withSources(),
    "com.typesafe.akka"         %% "akka-testkit"         % v.akka        % "test" withSources()
  )
}

