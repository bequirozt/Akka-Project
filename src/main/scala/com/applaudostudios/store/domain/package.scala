package com.applaudostudios.store


import akka.http.scaladsl.model.DateTime
import akka.util.Timeout
import com.applaudostudios.store.domain.data.{Category, InputItem, Item, User}
import com.applaudostudios.store.domain.users.UserActor.UserOverview
import com.typesafe.config.ConfigFactory
import spray.json.{BasicFormats, DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

import java.util.UUID
import scala.concurrent.duration._
import scala.language.postfixOps

package object domain {
  implicit val timeout:Timeout= 3.seconds

  val SNAPSHOT_CATEGORIES_MANAGER_SIZE: Int = ConfigFactory.load().getConfig("snapshotSize").getInt("categoriesManager")
  val SNAPSHOT_CATEGORIES_MANAGER_BULK_SIZE: Int = ConfigFactory.load().getConfig("snapshotSize").getInt("categoriesManagerBulk")
  val SNAPSHOT_USERS_MANAGER_SIZE: Int = ConfigFactory.load().getConfig("snapshotSize").getInt("usersManager")
  val SNAPSHOT_USERS_MANAGER_BULK_SIZE: Int = ConfigFactory.load().getConfig("snapshotSize").getInt("usersManagerBulk")
  val SNAPSHOT_USER_SIZE: Int = ConfigFactory.load().getConfig("snapshotSize").getInt("user")
  val SNAPSHOT_ITEMS_MANAGER_SIZE: Int = ConfigFactory.load().getConfig("snapshotSize").getInt("itemsManager")
  val SNAPSHOT_ITEMS_MANAGER_BULK_SIZE: Int = ConfigFactory.load().getConfig("snapshotSize").getInt("itemsManagerBulk")

  val REQUEST_TIME: FiniteDuration = 2.seconds

  //Common Commands
  case object StopActor
  case object RetrieveInfo


  //DTO
  case class EventInput(itemId:Int,catId:BigInt= BigInt(0), userId:Long = 0, eventType:String)

  sealed trait EventsPersistedByUser
  case class Cart(eventTime: DateTime = DateTime.now,
                  eventType: String = "cart",
                  itemId:Int,
                  categoryId: BigInt,
                  categoryCode: String,
                  brand: String,
                  price: Double,
                  userId: Long,
                  userSession: String) extends EventsPersistedByUser {
    override def equals(obj: Any): Boolean = obj match {
      case Cart(time, _, _,_, _, _, _, _, _) => time != eventTime
      case _ => false
    }
    override def hashCode(): Int = (userId + userSession + eventTime).hashCode()

    def toPurchase(time:DateTime = DateTime.now, session:String= UUID.randomUUID().toString):Purchase =
      Purchase(time,"purchase",itemId,categoryId,categoryCode,brand,price,userId,session)
  }
  case class View(eventTime: DateTime = DateTime.now,
                  eventType: String = "view",
                  itemId:Int,
                  categoryId: BigInt,
                  categoryCode: String,
                  brand: String,
                  price: Double,
                  userId: Long,
                  userSession: String) extends EventsPersistedByUser {
    override def equals(obj: Any): Boolean = obj match {
      case View(time, _, _, _,_, _, _, _, _) => time != eventTime
      case _ => false
    }
    override def hashCode(): Int = (userId + userSession + eventTime).hashCode()
  }
  case class Purchase(eventTime: DateTime = DateTime.now,
                      eventType: String = "purchase",
                      itemId:Int,
                      categoryId: BigInt,
                      categoryCode: String,
                      brand: String,
                      price: Double,
                      userId: Long,
                      userSession: String) extends EventsPersistedByUser {
    override def equals(obj: Any): Boolean = obj match {
      case Purchase(time, _, _,_, _, _, _, _, `userSession`) => time != eventTime
      case _ => false
    }
    override def hashCode(): Int = (userId + userSession + eventTime).hashCode()
  }


  trait DataJsonProtocol extends DefaultJsonProtocol with BasicFormats {


    implicit object MyDateTimeFormatter extends JsonFormat[DateTime] {
      def write(x: DateTime): JsString = JsString(x.toIsoDateTimeString())

      def read(value: JsValue): DateTime = value match {
        case JsString(x) => DateTime.fromIsoDateTimeString(x).getOrElse(DateTime.now)
        case _ => DateTime.now
      }
    }

    implicit def itemFormatter: RootJsonFormat[Item] = jsonFormat4(Item.apply)
    implicit def categoryFormatter: RootJsonFormat[Category] = jsonFormat2(Category.apply)
    implicit def userFormatter: RootJsonFormat[User] = jsonFormat3(User.apply)
    implicit def cartFormatter: RootJsonFormat[Cart] = jsonFormat9(Cart.apply)
    implicit def viewFormatter: RootJsonFormat[View] = jsonFormat9(View.apply)
    implicit def purchaseFormatter: RootJsonFormat[Purchase] = jsonFormat9(Purchase.apply)
    implicit def formatInputItem: RootJsonFormat[InputItem] = jsonFormat3(InputItem.apply)
    implicit def formatUserOverview: RootJsonFormat[UserOverview] = jsonFormat4(UserOverview.apply)
    implicit def formatEventInput: RootJsonFormat[EventInput] = jsonFormat4(EventInput.apply)
  }
}
