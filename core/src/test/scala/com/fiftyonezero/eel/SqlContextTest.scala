package com.fiftyonezero.eel

import com.fiftyonezero.eel.source.CsvSource
import com.sksamuel.scalax.io.IO
import org.scalatest.{WordSpec, Matchers}

class SqlContextTest extends WordSpec with Matchers {

  "SqlContext" should {
    "accept simple queries" in {
      val frame = CsvSource(IO.pathFromResource("/us-500.csv"))
      val sqlContext = SqlContext()
      sqlContext.registerFrame("people", frame)
      val result = sqlContext.sql("select first_name, last_name from people ")
      result.schema shouldBe FrameSchema(Seq(
        Column("FIRST_NAME", SchemaType.String, true),
        Column("LAST_NAME", SchemaType.String, true)
      ))
      result.size shouldBe 500
    }
    "accept group by queries" in {
      val frame = CsvSource(IO.pathFromResource("/us-500.csv"))
      val sqlContext = SqlContext()
      sqlContext.registerFrame("people", frame)
      val result = sqlContext.sql("select state, count(*) from people group by state")
      result.schema shouldBe FrameSchema(Seq(
        Column("STATE", SchemaType.String, true),
        Column("COUNT(*)", SchemaType.BigInt, false)
      ))
      result.size shouldBe 47
    }
  }
}
