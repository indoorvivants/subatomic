{
  if (!sys.env.contains("DEV"))
    Seq(addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.22"))
  else Seq.empty
}

/* resolvers += Resolver.sonatypeRepo("snapshots") */

addSbtPlugin("com.github.cb372"  % "sbt-explicit-dependencies" % "0.2.16")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"              % "2.4.6")
addSbtPlugin("com.github.sbt"    % "sbt-ci-release"            % "1.5.10")
addSbtPlugin("ch.epfl.scala"     % "sbt-scalafix"              % "0.10.1")
addSbtPlugin("de.heikoseeberger" % "sbt-header"                % "5.7.0")
addSbtPlugin("com.eed3si9n"      % "sbt-projectmatrix"         % "0.9.0")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo"             % "0.11.0")
addSbtPlugin("org.scala-js"      % "sbt-scalajs"               % "1.10.1")
addSbtPlugin("org.scala-native"  % "sbt-scala-native"          % "0.4.5")
addSbtPlugin("com.github.sbt"    % "sbt-native-packager"       % "1.9.11")
addSbtPlugin("com.indoorvivants" % "sbt-commandmatrix"         % "0.0.5")
