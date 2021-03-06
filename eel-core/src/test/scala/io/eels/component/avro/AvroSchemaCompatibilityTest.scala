package io.eels.component.avro

import java.util

import io.eels.schema._
import org.apache.avro.{LogicalTypes, Schema, SchemaBuilder}
import org.scalatest.{FunSuite, Matchers}

// tests that the eel <-> avro schemas are compatible
class AvroSchemaCompatibilityTest extends FunSuite with Matchers {

  test("avro record schemas should be cross compatible with eel struct types") {

    val decimal = Schema.create(Schema.Type.BYTES)
    LogicalTypes.decimal(14, 7).addToSchema(decimal)

    val timestampMillis = Schema.create(Schema.Type.LONG)
    LogicalTypes.timestampMillis().addToSchema(timestampMillis)

    val timestampMicros = Schema.create(Schema.Type.LONG)
    LogicalTypes.timestampMicros().addToSchema(timestampMicros)

    val timeMillis = Schema.create(Schema.Type.INT)
    LogicalTypes.timeMillis().addToSchema(timeMillis)

    val timeMicros = Schema.create(Schema.Type.LONG)
    LogicalTypes.timeMicros().addToSchema(timeMicros)

    val date = Schema.create(Schema.Type.INT)
    LogicalTypes.date().addToSchema(date)

    val enum = Schema.createEnum("suits", null, null, util.Arrays.asList("spades", "hearts"))

    val schema = SchemaBuilder.record("row").namespace("namespace").fields()
      .optionalBoolean("optbool")
      .optionalBytes("optbytes")
      .optionalDouble("optdouble")
      .optionalFloat("optfloat")
      .optionalInt("optint")
      .optionalLong("optlong")
      .optionalString("optstring")
      .requiredBoolean("reqbool")
      .requiredBytes("reqbytes")
      .requiredDouble("reqdouble")
      .requiredFloat("reqfloat")
      .requiredInt("reqint")
      .requiredLong("reqlong")
      .requiredString("reqstring")
      .name("reqenum").`type`(enum).noDefault()
      .name("reqdecimal").`type`(decimal).noDefault()
      .name("requiredDate").`type`(date).noDefault()
      .name("requiredTimeMillis").`type`(timeMillis).noDefault()
      .name("requiredTimeMicros").`type`(timeMicros).noDefault()
      .name("requiredTimestampMillis").`type`(timestampMillis).noDefault()
      .name("requiredTimestampMicros").`type`(timestampMicros).noDefault()
      .endRecord()

    val structType = StructType(Vector(
      Field("optbool", BooleanType, true, false),
      Field("optbytes", BinaryType, true, false),
      Field("optdouble", DoubleType, true, false),
      Field("optfloat", FloatType, true, false),
      Field("optint", IntType(true), true, false),
      Field("optlong", LongType(true), true, false),
      Field("optstring", StringType, true, false),
      Field("reqbool", BooleanType, false, false),
      Field("reqbytes", BinaryType, false, false),
      Field("reqdouble", DoubleType, false, false),
      Field("reqfloat", FloatType, false, false),
      Field("reqint", IntType(true), false, false),
      Field("reqlong", LongType(true), false, false),
      Field("reqstring", StringType, false, false),
      Field("reqenum", EnumType("suits", "spades", "hearts"), false),
      Field("reqdecimal", DecimalType(14, 7), false, false),
      Field("requiredDate", DateType, false, false),
      Field("requiredTimeMillis", TimeMillisType, false, false),
      Field("requiredTimeMicros", TimeMicrosType, false, false),
      Field("requiredTimestampMillis", TimestampMillisType, false, false),
      Field("requiredTimestampMicros", TimestampMicrosType, false, false)
    ))

    AvroSchemaFns.fromAvroSchema(schema) shouldBe structType
    AvroSchemaFns.toAvroSchema(structType) shouldBe schema
  }

  test("parquet schema fns should convert varchar to strings and keep char") {

    val schema = SchemaBuilder.record("row").namespace("namespace").fields()
      .name("varchar").`type`(Schema.create(Schema.Type.STRING)).noDefault()
      .name("char").`type`(Schema.createFixed("char", null, null, 15)).noDefault()
      .endRecord()

    val outputStruct = StructType(Vector(
      Field("varchar", StringType, false),
      Field("char", CharType(15), false)
    ))

    val inputStruct = StructType(Vector(
      Field("varchar", VarcharType(244), false),
      Field("char", CharType(15), false)
    ))

    AvroSchemaFns.fromAvroSchema(schema) shouldBe outputStruct
    AvroSchemaFns.toAvroSchema(inputStruct) shouldBe schema
  }
}
