package com.fortysevendeg.kafka.streaming

import io.circe.Codec

object Domain {
  type UserId = String
  type Profile = String
  type Product = String
  type OrderId = String

  case class Order(orderId: OrderId, user: UserId, products: List[Product], amount: Double)
  object Order {
    implicit val codec: Codec[Order] = io.circe.generic.semiauto.deriveCodec
  }
  case class Discount(profile: Profile, amount: Double) // in percentage points
  object Discount {
    implicit val codec: Codec[Discount] = io.circe.generic.semiauto.deriveCodec
  }
  case class Payment(orderId: OrderId, status: String)
  object Payment {
    implicit val codec: Codec[Payment] = io.circe.generic.semiauto.deriveCodec
  }
}
