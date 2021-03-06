package io.eels

import java.util.concurrent.{CountDownLatch, TimeUnit}

import io.eels.component.csv.{CsvSink, CsvSource}
import io.eels.datastream.DataStream
import io.eels.schema.StructType
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.scalatest.{Matchers, WordSpec}

import scala.util.Random

class ListenerTest extends WordSpec with Matchers {

  implicit val conf = new Configuration()
  implicit val fs = FileSystem.get(conf)

  val schema = StructType("a", "b", "c", "d", "e")
  val rows = List.fill(1000)(Row(schema, Random.nextBoolean(), Random.nextFloat(), Random.nextGaussian(), Random.nextLong(), Random.nextString(10)))
  val ds = DataStream.fromRows(schema, rows)

  val path = new Path("listener_test.csv")

  "DataStream" should {
    "support user's listeners" in {

      val latch = new CountDownLatch(1000)
      fs.delete(path, false)

      ds.listener(new Listener {
        override def onNext(value: Row): Unit = latch.countDown()
        override def onError(e: Throwable): Unit = ()
        override def onComplete(): Unit = ()
      }).to(CsvSink(path))

      latch.await(20, TimeUnit.SECONDS) shouldBe true

      fs.delete(path, false)
    }
    "propagate errors in listeners" in {

      class TestSink extends Sink {
        override def open(schema: StructType): SinkWriter = new SinkWriter {
          override def close(): Unit = ()
          override def write(row: Row): Unit = ()
        }
      }

      try {
        ds.listener(new Listener {
          override def onNext(value: Row): Unit = sys.error("boom")
          override def onError(e: Throwable): Unit = ()
          override def onComplete(): Unit = ()
        }).to(new TestSink)
        assert(false)
      } catch {
        case _: Throwable =>
      }
    }
  }

  "Source.toDataStream" should {
    "call on next for each row" in {

      val latch = new CountDownLatch(1000)

      fs.delete(path, false)
      ds.to(CsvSink(path))

      CsvSource(path).toDataStream(new Listener {
        override def onNext(value: Row): Unit = latch.countDown()
        override def onError(e: Throwable): Unit = ()
        override def onComplete(): Unit = ()
      }).collect

      latch.await(5, TimeUnit.SECONDS) shouldBe true
      fs.delete(path, false)
    }
    "call on complete once finished" in {

      val latch = new CountDownLatch(1001)

      fs.delete(path, false)
      ds.to(CsvSink(path))

      CsvSource(path).toDataStream(new Listener {
        override def onNext(value: Row): Unit = latch.countDown()
        override def onError(e: Throwable): Unit = ()
        override def onComplete(): Unit = latch.countDown()
      }).collect

      latch.await(5, TimeUnit.SECONDS) shouldBe true
      fs.delete(path, false)
    }
  }
}
