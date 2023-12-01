package subatomic.builders

import upickle.default._
import subatomic.Logger

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
      var hit = false
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
      var miss = false
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
