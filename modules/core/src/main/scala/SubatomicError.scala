/*
 * Copyright 2020 Anton Sviridov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package subatomic

class SubatomicError(msg: String) extends Exception(msg) with scala.util.control.NoStackTrace {
  final override def toString = SubatomicError.render(msg)
}

object SubatomicError {

  def apply(msg: String) = new SubatomicError(msg)
  def render(msg: String) = {

    val maxLineLength = msg.linesIterator.map(_.length).max
    val header        = "-" * maxLineLength
    val fireMsg       = msg.linesIterator.map(_.trim).map("🔥 " + _).mkString("\n")
    val newMsg        = header + "\n" + fireMsg + "\n" + header
    Logger._redLines("\n " + newMsg)
  }

  trait messages {
    def fieldMissing(fieldName: String) =
      s"Required field ${Logger._bold(fieldName)} is missing"
  }

  object messages extends messages

  def fileConfiguration(p: os.Path, err: messages => String) =
    SubatomicError(
      s"""
    | Problem with configuration in
    |
    | $p 
    |
    | ${err(messages)}
      """.trim.stripMargin
    )

  def dangerousOverwriting(p: os.Path) =
    SubatomicError(
      s"""
    | Path 
    |
    | $p 
    |
    | already exists, but `force` mode is set to false
    |
    | If you're using a Builder from CLI - pass --force flag
    | If you're invoking the API manually, set overwrite when calling buildAt:
    |
    | .buildAt(destination, ${Logger._bold("overwrite = true")})
      """.trim.stripMargin
    )
  def mdocInvocationError(reason: String, files: Seq[String], commandFile: os.Path) =
    SubatomicError(s"""
   | Failed to invoke Mdoc
   | 
   | $reason
   |
   | The following files were passed to mdoc:
   | 
   | ${files.mkString("\n")}
   | 
   | The full java command has been written to $commandFile
   |
   | Not sure I can provide better error than that :(
   """.trim.stripMargin)
}
