package io.eels.component.kafka

import java.util.Properties
import java.util.concurrent.TimeUnit

import com.sksamuel.kafka.embedded.{EmbeddedKafka, EmbeddedKafkaConfig}
import io.eels.{Column, SchemaType}
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class KafkaSourceTest extends WordSpec with Matchers with BeforeAndAfterAll {

  val config = EmbeddedKafkaConfig(zookeeperPort = 2401, kafkaPort = 9405)
  val kafka = new EmbeddedKafka(config)
  kafka.start()

  "KafkaSource" should {
    "read from topic" in {

      val topic = "kafka-source-test"

      val producerProps = new Properties
      producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:" + config.kafkaPort)
      val producer = new KafkaProducer[String, String](producerProps, new StringSerializer, new StringSerializer)

      for ( _ <- 1 to 100 ) {
        val record = new ProducerRecord[String, String](topic, """{ "name" : "sam", "location" : "london" }""")
        producer.send(record).get(1, TimeUnit.MINUTES)
      }

      producer.close(1, TimeUnit.MINUTES)

      val sourceConfig = KafkaSourceConfig("localhost:" + config.kafkaPort, "myconsumer2")
      val source = KafkaSource(sourceConfig, Set(topic), JsonKafkaDeserializer)
      val rows = source.toSeq.run
      rows.size shouldBe 100
      rows.head shouldBe Seq("sam", "london")
    }
  }

  override protected def afterAll(): Unit = {
    kafka.stop()
  }
}