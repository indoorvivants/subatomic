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

import scala.util.control.NoStackTrace

case class SubatomicError(msg: String) extends RuntimeException with NoStackTrace {
  override def toString = {
    val maxLineLength = msg.linesIterator.map(_.length).max
    val header        = "-" * maxLineLength
    val newMsg        = header + "\n" + msg + "\n" + header
    Logger._redLines("\n" + newMsg)
  }
}

object SubatomicError {
  def dangerousOverwriting(p: os.Path) =
    SubatomicError(
      s"""
    | Path 
    |
    | $p 
    |
    | already exists, but overwrite is set to false
    |
    | If you're using a Builder from CLI - pass --overwrite flag
    | If you're invoking the API manually, set overwrite when calling buildAt:
    |
    | .buildAt(destination, ${Logger._bold("overwrite = true")})
      """.trim.stripMargin
    )
}
