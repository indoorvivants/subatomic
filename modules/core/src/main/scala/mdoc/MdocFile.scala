package subatomic

import java.util.Properties
import coursier._
import coursier.core.MinimizedExclusions
import coursier.parse.DependencyParser
import os.ProcessOutput

case class MdocFile(
    path: os.Path,
    config: MdocConfiguration
)
