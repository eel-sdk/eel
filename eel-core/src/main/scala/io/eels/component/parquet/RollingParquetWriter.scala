package io.eels.component.parquet

import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.hadoop.fs.{FileSystem, Path}

class RollingParquetWriter(basePath: Path,
                           avroSchema: Schema,
                           maxRecordsPerFile: Int,
                           maxFileSize: Long)(implicit fs: FileSystem) extends ParquetWriterSupport {

  private val isRolling = maxRecordsPerFile > 0 || maxFileSize > 0
  private var filecount = -1
  private var records = 0
  private var path = nextPath()
  private var writer = createParquetWriter(path, avroSchema)

  private def nextPath(): Path = {
    if (isRolling) {
      filecount = filecount + 1
      new Path(basePath.toString + "_" + filecount)
    } else {
      basePath
    }
  }

  private def rollover(): Unit = {
    logger.debug(s"Rolling parquet file [$records records]")
    writer.close()
    path = nextPath()
    writer = createParquetWriter(path, avroSchema)
    records = 0
  }

  private def checkForRollover(): Unit = {
    if (maxRecordsPerFile > 0 && records >= maxRecordsPerFile) {
      rollover()
    } else if (maxFileSize > 0 && fs.getFileStatus(path).getLen > maxFileSize) {
      rollover()
    }
  }

  def write(record: GenericRecord): Unit = {
    if (isRolling)
      checkForRollover()
    writer.write(record)
    records = records + 1
  }

  def close(): Unit = {
    writer.close()
  }
}