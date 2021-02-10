addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"              % "0.1.13")
addSbtPlugin("com.github.cb372"          % "sbt-explicit-dependencies" % "0.2.13")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"              % "2.4.2")
addSbtPlugin("com.geirsson"              % "sbt-ci-release"            % "1.5.5")
addSbtPlugin("ch.epfl.scala"             % "sbt-scalafix"              % "0.9.25")
addSbtPlugin("org.scalameta"             % "sbt-mdoc"                  % "2.2.9")
addSbtPlugin("de.heikoseeberger"         % "sbt-header"                % "5.6.0")
addSbtPlugin("com.eed3si9n"              % "sbt-projectmatrix"         % "0.7.0")
addSbtPlugin("com.eed3si9n"              % "sbt-buildinfo"             % "0.10.0")
addSbtPlugin("org.scala-js"              % "sbt-scalajs"               % "1.4.0")
addSbtPlugin("org.scala-native"          % "sbt-scala-native"          % "0.4.0")
addSbtPlugin("com.typesafe.sbt"          % "sbt-native-packager"       % "1.8.0")

// so that we can use SubatomicPlugin in the build itself
// like many build-related things, this was copied from Mdoc's excellent
// configuration
//
// https://github.com/scalameta/mdoc/blob/master/project/plugins.sbt#L12
unmanagedSourceDirectories.in(Compile) +=
  baseDirectory.in(ThisBuild).value.getParentFile /
    "sbt-plugin" / "src" / "main" / "scala"
