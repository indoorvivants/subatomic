{
  if (!sys.env.contains("DEV"))
    Seq(addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.13"))
  else Seq.empty
}

addSbtPlugin("com.github.cb372"  % "sbt-explicit-dependencies" % "0.2.16")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"              % "2.4.2")
addSbtPlugin("com.geirsson"      % "sbt-ci-release"            % "1.5.5")
addSbtPlugin("ch.epfl.scala"     % "sbt-scalafix"              % "0.9.28")
addSbtPlugin("org.scalameta"     % "sbt-mdoc"                  % "2.2.21")
addSbtPlugin("de.heikoseeberger" % "sbt-header"                % "5.6.0")
addSbtPlugin("com.eed3si9n"      % "sbt-projectmatrix"         % "0.8.0")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo"             % "0.10.0")
addSbtPlugin("org.scala-js"      % "sbt-scalajs"               % "1.5.1")
addSbtPlugin("org.scala-native"  % "sbt-scala-native"          % "0.4.0")
addSbtPlugin("com.typesafe.sbt"  % "sbt-native-packager"       % "1.8.0")
addSbtPlugin("com.indoorvivants" % "sbt-commandmatrix"         % "0.0.3")

// so that we can use SubatomicPlugin in the build itself
// like many build-related things, this was copied from Mdoc's excellent
// configuration
//
// https://github.com/scalameta/mdoc/blob/master/project/plugins.sbt#L12
unmanagedSourceDirectories.in(Compile) +=
  baseDirectory.in(ThisBuild).value.getParentFile /
    "sbt-plugin" / "src" / "main" / "scala"
