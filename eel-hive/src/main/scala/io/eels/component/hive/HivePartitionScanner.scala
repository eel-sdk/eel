package io.eels.component.hive

import com.sksamuel.exts.Logging
import com.typesafe.config.{Config, ConfigFactory}
import io.eels.component.hive.partition.PartitionMetaData
import io.eels.schema.PartitionConstraint
import org.apache.hadoop.fs.{FileSystem, LocatedFileStatus}

// scans partitions for files, returning the files and the meta data object for each partition
class HivePartitionScanner(implicit fs: FileSystem) extends Logging {

  private val config: Config = ConfigFactory.load()
  private val missingPartitionAction: String = config.getString("eel.hive.source.missingPartitionAction")

  def scan(partitions: Seq[PartitionMetaData],
           constraints: Seq[PartitionConstraint] = Nil): Map[PartitionMetaData, Seq[LocatedFileStatus]] = {
    logger.debug(s"Scanning ${partitions.size} partitions for applicable files ${partitions.map(_.location).mkString(", ").take(100)}")

    // first we filter out any partitions not matching the constraints
    val filteredPartitions = partitions.filter { meta =>
      constraints.forall(_.eval(meta.partition))
    }
    logger.debug(s"Filtered partitions: ${filteredPartitions.map(_.location).mkString(", ")})")

    // next, we check that the directories that the partitions point to actually exist
    // this will avoid a situation where a location exists in the metastore but not on disk
    val exantPartitions = filteredPartitions.filter { partition =>
      if (fs.exists(partition.location)) {
        true
      } else {
        if (missingPartitionAction == "error") {
          throw new IllegalStateException(s"Partition [${partition.name}] was specified in the hive metastore at [${partition.location}] but did not exist on disk. To disable these exceptions set eel.hive.source.missingPartitionAction=warn or eel.hive.source.missingPartitionAction=none")
        } else if (missingPartitionAction == "warn") {
          logger.warn(s"Partition [${partition.name}] was specified in the hive metastore at [${partition.location}] but did not exist on disk. To disable these warnings set eel.hive.source.missingPartitionAction=none")
          false
        } else {
          false
        }
      }
    }

    // next we grab all the data files from each of these partitions
    exantPartitions.map { meta =>
      meta -> HiveFileScanner(meta.location, false)
    }.toMap
  }
}
