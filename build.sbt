


lazy val libdeps = Seq(
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.0" % Test,
  "org.specs2" % "specs2_2.11" % "3.7" % "test" pomOnly(),
  "tradr" %% "tradr-common" % "0.0.+",
  "tradr" %% "tradr-models" % "0.0.+"
)


lazy val root = (project in file("."))
  .enablePlugins(PlayScala, sbtdocker.DockerPlugin, JavaAppPackaging)
  .settings(Seq(
      name := "tradr-predictor",
      organization := "tradr",
      scalaVersion := "2.12.2",
      version := "1.0.0",
      libraryDependencies ++= libdeps
    ))


val productionConfFileSource = new File("/home/leifblaese/Dropbox/Privat/Tradr/production.conf")
dockerfile in docker := {
  val appDir: File = stage.value
  val targetDir = "/opt/tradr-predicor"
  new Dockerfile {
    from("java")
    copy(appDir, targetDir)
    expose(9000)
    copy(new File("/home/leifblaese/logs"), targetDir)
    copy(productionConfFileSource, targetDir)
    entryPoint(s"$targetDir/bin/${executableScriptName.value}", s"-Dconfig.file=$targetDir/production.conf")
  }
}




// Adds additional packages into Twirl
//TwirlKeys.templateImports += "tradr.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "tradr.binders._"
