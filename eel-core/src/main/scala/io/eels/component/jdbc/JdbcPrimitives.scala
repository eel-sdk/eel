package io.eels.component.jdbc

import java.sql.{Connection, DriverManager, ResultSet}

import com.sksamuel.exts.Logging
import io.eels.component.jdbc.dialect.JdbcDialect
import io.eels.schema.StructType

trait JdbcPrimitives extends Logging {

  def connect(url: String): Connection = {
    logger.debug(s"Connecting to jdbc source $url...")
    val conn = DriverManager.getConnection(url)
    logger.debug(s"Connected to $url")
    conn
  }

  def schemaFor(dialect: JdbcDialect, rs: ResultSet): StructType = {
    val schema = JdbcSchemaFns.fromJdbcResultset(rs, dialect)
    logger.trace("Fetched schema:\n" + schema.show())
    schema
  }
}
