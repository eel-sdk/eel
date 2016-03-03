package io.eels.component.hive

import java.util.concurrent._

import com.sksamuel.scalax.Logging
import com.sksamuel.scalax.collection.BlockingQueueConcurrentIterator
import io.eels.{InternalRow, Schema, SinkWriter}
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.hive.conf.HiveConf
import org.apache.hadoop.hive.metastore.{HiveMetaStoreClient, IMetaStoreClient}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

class HiveSinkWriter(inputSchema: Schema,
                     outputSchema: Schema,
                     dbName: String,
                     tableName: String,
                     ioThreads: Int,
                     dialect: HiveDialect,
                     dynamicPartitioning: Boolean,
                     includePartitionsInData: Boolean,
                     bufferSize: Int)(implicit fs: FileSystem, hiveConf: HiveConf, client: IMetaStoreClient)
  extends SinkWriter
    with Logging {

  val base = System.nanoTime
  logger.debug(s"HiveSinkWriter created; timestamp=$base")

  val tablePath = HiveOps.tablePath(dbName, tableName)
  val lock = new Object {}

  val partitionKeyNames = HiveOps.partitionKeyNames(dbName, tableName)
  logger.debug("Dynamic partitioning enabled: " + partitionKeyNames.mkString(","))

  // these are the indexes in input rows to skip from writing because they are partition values
  val indexesToSkip = if (includePartitionsInData) Nil else partitionKeyNames.map(inputSchema.indexOf)
  // this is simply a list of indexes we want to keep, so we can efficiently iterate over it in the writing loop
  val indexesToWrite = List.tabulate(inputSchema.columns.size)(k => k).filterNot(indexesToSkip.contains)

  // Since the data can come in unordered, we need to keep open a stream per partition path.
  // This shouldn't be shared amongst threads so that we can increase throughput by increasing the number
  // of threads (if it was shared, then if a single path we might only have one writer for all the output).
  // the key should include the thread count so that each thread has its own unique writer
  val writers = mutable.Map.empty[String, HiveWriter]

  // this contains all the partitions we've checked.
  // No need for multiple threads to keep hitting the meta store
  val createdPartitions = new ConcurrentSkipListSet[String]

  def getOrCreateHiveWriter(row: InternalRow, sourceSchema: Schema, k: Long): HiveWriter = {

    val parts = RowPartitionParts(row, partitionKeyNames, sourceSchema)
    val partPath = HiveOps.partitionPathString(dbName, tableName, parts, tablePath)
    writers.getOrElseUpdate(partPath + "_" + k, {

      // this is not thread safe, so each thread needs its own copy when in here
      implicit val client = new HiveMetaStoreClient(hiveConf)

      val filePath = new Path(partPath, "part_" + System.nanoTime + "_" + k)
      logger.debug(s"Creating hive writer for $filePath")
      if (dynamicPartitioning) {
        if (parts.nonEmpty) {
          // we need to synchronize this, as its quite likely that when ioThreads>1 we have >1 thread
          // trying to create a partition at the same time. This is virtually guaranteed to happen if the data
          // is in any way sorted
          if (createdPartitions.contains(partPath.toString)) {
            lock.synchronized {
              HiveOps.createPartitionIfNotExists(dbName, tableName, parts)
              createdPartitions.add(partPath.toString)
            }
          }
        }
      } else if (!HiveOps.partitionExists(dbName, tableName, parts)) {
        sys.error(s"Partition $partPath does not exist and dynamicPartitioning = false")
      }

      // the target schema is what we use to write out with. This is generated from the schema taken
      // from the hive target table, with partition information removed.
      val targetSchema = if (includePartitionsInData || partitionKeyNames.isEmpty) {
        outputSchema
      } else {
        partitionKeyNames.foldLeft(outputSchema)((schema, name) => schema.removeColumn(name))
      }
      dialect.writer(targetSchema, filePath)
    })
  }

  val queue = new LinkedBlockingQueue[InternalRow](bufferSize)
  val latch = new CountDownLatch(ioThreads)
  val executor = Executors.newFixedThreadPool(ioThreads)

  import com.sksamuel.scalax.concurrent.ThreadImplicits.toRunnable

  for (k <- 0 until ioThreads) {
    executor.submit {
      logger.info(s"HiveSink thread $k started")
      var count = 0l
      try {
        BlockingQueueConcurrentIterator(queue, InternalRow.PoisonPill).foreach { row =>
          val writer = getOrCreateHiveWriter(row, inputSchema, k)
          // need to strip out any partition information from the written data
          // keeping this as a list as I want it ordered and no need to waste cycles on an ordered map
          if (indexesToSkip.isEmpty) {
            writer.write(row)
          } else {
            val row2 = new ListBuffer[Any]
            for (k <- indexesToWrite) {
              row2.append(row(k))
            }
            writer.write(row2)
          }
          count = count + 1
        }
      } catch {
        case e: Throwable => logger.error("Error writing row", e)
      } finally {
        logger.info(s"Sink thread $k completed; total $count rows")
        latch.countDown()
      }
    }
  }

  executor.submit {
    latch.await(1, TimeUnit.DAYS)
    logger.debug(s"Latch released; closing ${writers.size} hive writers")
    writers.values.foreach { writer =>
      try {
        writer.close()
      } catch {
        case NonFatal(e) =>
          logger.warn("Could not close writer", e)
      }
    }
  }

  executor.shutdown()

  override def close(): Unit = {
    logger.debug("Request to close hive sink writer")
    queue.put(InternalRow.PoisonPill)
    executor.awaitTermination(1, TimeUnit.DAYS)
  }

  override def write(row: InternalRow): Unit = queue.put(row)

}