lazy val root = (project in file("."))
.enablePlugins(PlayScala)
.settings(
  scalaVersion := "2.11.8",
  scalacOptions := Seq("-language:_", "-deprecation", "-unchecked", "-feature", "-Xlint"),
  transitiveClassifiers in Global := Seq(Artifact.SourceClassifier),
  sources in (Compile, doc) := Nil,
  publishArtifact in (Compile, packageDoc) := false,
  parallelExecution in Test := false
).settings(
  resolvers += Resolver.file(
    "local-ivy-repos", file(Path.userHome + "/.ivy2/local")
  )(Resolver.ivyStylePatterns),
  libraryDependencies ++= Seq(
    "com.nulab-inc" %% "play2-oauth2-provider" % "0.17.0",
    "com.typesafe.play" %% "play-slick" % "2.0.0",
    "com.typesafe.play" %% "play-slick-evolutions" % "2.0.0",
    "mysql" % "mysql-connector-java" % "5.1.12"
  )
)

routesGenerator := InjectedRoutesGenerator
