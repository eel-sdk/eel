package io.eels.component.orc

import com.sksamuel.exts.Logging
import io.eels.schema.StructType
import io.eels.{Row, Sink, SinkWriter}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import com.sksamuel.exts.OptionImplicits._

case class OrcSink(path: Path,
                   bloomFilterColumns: Seq[String] = Nil,
                   rowIndexStride: Option[Int] = None)
                  (implicit conf: Configuration) extends Sink with Logging {

  def withBloomFilterColumns(bloomFilterColumns: Seq[String]): OrcSink = copy(bloomFilterColumns = bloomFilterColumns)
  def withRowIndexStride(stride: Int): OrcSink = copy(rowIndexStride = stride.some)

  override def writer(schema: StructType): SinkWriter = new SinkWriter {
    val writer = new OrcWriter(path, schema, bloomFilterColumns, rowIndexStride)
    override def write(row: Row): Unit = writer.write(row)
    override def close(): Unit = writer.close()
  }
}