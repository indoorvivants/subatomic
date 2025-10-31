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

package subatomic.builders

import subatomic.Logger

import upickle.default._

trait Cache {
  def produce[I: ReadWriter, O: ReadWriter](params: I)(compute: => O): O

  def purge(): Unit

  def delete[I: ReadWriter](params: I): Unit
}

object Cache {
  object NoCaching extends Cache {
    def produce[I: ReadWriter, O: ReadWriter](params: I)(compute: => O) =
      compute

    def purge(): Unit                          = ()
    def delete[I: ReadWriter](params: I): Unit = ()
  }

  object InMemory extends Cache {
    private val mem = scala.collection.mutable.Map.empty[Any, Any]
    def produce[I: ReadWriter, O: ReadWriter](params: I)(compute: => O) =
      mem.getOrElseUpdate(params, compute).asInstanceOf[O]

    def purge(): Unit = mem.clear()

    def delete[I: ReadWriter](params: I): Unit = mem.remove(params)
  }

  class TrackingCache(cache: Cache) extends Cache {
    def produce[I: ReadWriter, O: ReadWriter](params: I)(compute: => O): O = {
      var hit        = false
      val newCompute = () => {
        hit = true
        compute
      }
      val result = cache.produce(params)(newCompute())

      if (hit) session += params

      result
    }

    def purge(): Unit = {
      cache.purge()
      clearSession()
    }

    def delete[I: ReadWriter](params: I): Unit = {
      cache.delete(params)
      session.filterInPlace(_ == params)
    }

    private val session = collection.mutable.ListBuffer.empty[Any]

    def clearSession(): Unit  = session.clear()
    def allTracked: List[Any] = session.toList

  }

  def track(cache: Cache): TrackingCache = new TrackingCache(cache)

  def verbose(cache: Cache) = new Cache {
    override def produce[I: ReadWriter, O: ReadWriter](
        params: I
    )(compute: => O): O = {
      var miss       = false
      val newCompute = () => {
        miss = true
        compute
      }
      val result = cache.produce(params)(newCompute())

      if (miss) Logger.default.logLine(s"Cache miss: ${params}")
      else Logger.default.logLine(s"Cache hit: ${params}")

      result
    }

    override def purge(): Unit = cache.purge()

    override def delete[I: ReadWriter](params: I): Unit = cache.delete(params)

  }

  def labelled(label: String, cache: Cache): Cache = new Cache {
    override def produce[I: ReadWriter, O: ReadWriter](params: I)(
        compute: => O
    ): O =
      cache.produce((label, params))(compute)

    override def purge(): Unit = cache.purge()

    override def delete[I: ReadWriter](params: I): Unit =
      cache.delete((label, params))

  }

}
