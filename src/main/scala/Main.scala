package com.fortysevendeg.kafka.streaming

import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.{GlobalKTable, JoinWindows, TimeWindows, Windowed}
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream.{KGroupedStream, KStream, KTable}
import org.apache.kafka.streams.scala.serialization.Serdes._

import java.time.temporal.ChronoUnit
import scala.concurrent.duration._
import scala.jdk.DurationConverters._

object Main extends App {
  import Topics._
  import Domain._
  import Implicits._

  val builder = new StreamsBuilder
  val usersOrdersStreams: KStream[UserId, Order] =
    builder.stream[UserId, Order](OrdersByUserTopic)

  val userProfilesTable: KTable[UserId, Profile] =
    builder.table[UserId, Profile](DiscountProfilesByUserTopic)

  val discountProfilesGTable: GlobalKTable[Profile, Discount] =
    builder.globalTable[Profile, Discount](DiscountsTopic)

  val expensiveOrders: KStream[UserId, Order] = usersOrdersStreams.filter { (_, order) =>
    order.amount >= 1000
  }

  val purchasedListOfProductsStream: KStream[UserId, List[Product]] =
    usersOrdersStreams.mapValues { order => order.products }

  val purchasedProductsStream: KStream[UserId, Product] = usersOrdersStreams.flatMapValues {
    order => order.products
  }

  purchasedProductsStream.foreach { (userId, product) =>
    println(s"The user $userId purchased the product $product")
  }

  val productsPurchasedByUsers: KGroupedStream[UserId, Product] =
    purchasedProductsStream.groupByKey

  val purchasedByFirstLetter: KGroupedStream[String, Product] =
    purchasedProductsStream.groupBy[String] { (userId, _) => userId.charAt(0).toLower.toString }

  val numberOfProductsByUser: KTable[UserId, Long] = productsPurchasedByUsers.count()

  val everyTenSeconds: TimeWindows = TimeWindows.of(10.seconds.toJava)

  val numberOfProductsByUserEveryTenSeconds: KTable[Windowed[UserId], Long] =
    productsPurchasedByUsers.windowedBy(everyTenSeconds).aggregate[Long](0L) {
      (_, _, counter) => counter + 1
    }

  val ordersWithUserProfileStream: KStream[UserId, (Order, Profile)] =
    usersOrdersStreams.join[Profile, (Order, Profile)](userProfilesTable) { (order, profile) =>
      (order, profile)
    }

  val discountedOrdersStream: KStream[UserId, Order] =
    ordersWithUserProfileStream.join[Profile, Discount, Order](discountProfilesGTable)(
      { case (_, (_, profile)) => profile }, // Joining key
      { case ((order, _), discount) => order.copy(amount = order.amount * discount.amount) }
    )

  val paymentsStream: KStream[OrderId, Payment] =
    builder.stream[OrderId, Payment](PaymentsTopic)

  val ordersStream: KStream[OrderId, Order] = discountedOrdersStream.selectKey { (_, order) =>
    order.orderId
  }

  val paidOrders: KStream[OrderId, Order] = {

    val joinOrdersAndPayments = (order: Order, payment: Payment) =>
      if (payment.status == "PAID") Option(order) else Option.empty[Order]

    val joinWindow = JoinWindows.of(java.time.Duration.of(5, ChronoUnit.MINUTES))

    ordersStream
      .join[Payment, Option[Order]](paymentsStream)(joinOrdersAndPayments, joinWindow)
      .flatMapValues(maybeOrder => maybeOrder.toList)
  }

  val topology: Topology = builder.build()

  println(topology.describe())

//  val props = new java.util.Properties
//  props.put(org.apache.kafka.streams.StreamsConfig.APPLICATION_ID_CONFIG, "orders-application")
//  props.put(org.apache.kafka.streams.StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
//  props.put(
//    org.apache.kafka.streams.StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
//    org.apache.kafka.streams.scala.serialization.Serdes.stringSerde.getClass
//  )
//
//  val application: org.apache.kafka.streams.KafkaStreams =
//    new org.apache.kafka.streams.KafkaStreams(topology, props)
//  application.start()
}
