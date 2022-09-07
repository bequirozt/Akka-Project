package com.applaudostudios.store.actor

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.DateTime
import akka.stream.alpakka.json.scaladsl.JsonReader
import akka.stream.scaladsl.{FileIO, Flow, Sink}
import akka.util.ByteString
import com.applaudostudios.store.actor.ManagerActor.InitBulkPersistence
import spray.json._

import java.io.FileNotFoundException
import java.nio.file.{Files, Paths}
import scala.util.Failure

object LoadJson {

  def props(persistenceActor: ActorRef): Props = Props(new LoadJson(persistenceActor))

  case class FilePath(file:String)
  case class EventFromJson(event_time:String, event_type:String, product_id:String,
                           category_id:BigInt, category_code:String, brand:String, price:String,
                           user_id:String, user_session:String)

  //Bulk loading directives
    case object LoadStarted
    case object LoadFinished
    case class LoadFailure(exception:Throwable)
    case class EventToPersist(
                               eventTime: DateTime,
                               eventType: String,
                               productId: Int,
                               categoryId: BigInt,
                               categoryCode: String,
                               brand: String,
                               price: Double,
                               userId: Int,
                               userSession: String
                             )

}

trait EventStoreJsonProtocol extends DefaultJsonProtocol {
  import LoadJson._
  implicit val eventFormat: RootJsonFormat[EventFromJson] = jsonFormat9(EventFromJson)

  implicit object MyStringJsonFormat extends JsonFormat[String] {
    def write(x: String): JsString = JsString(x)

    def read(value: JsValue):String = value match {
      case JsString(x) => x
      case x => deserializationError("Expected String as JsString, but got " + x)
    }
  }
}


class LoadJson(actorThatPersists: ActorRef) extends Actor with ActorLogging with SprayJsonSupport with EventStoreJsonProtocol {
  import LoadJson._
  implicit val system:ActorSystem = context.system

  def convertEvent(content:EventFromJson) : EventToPersist =
    EventToPersist(
      DateTime.fromIsoDateTimeString({
        val length = content.event_time.length
        content.event_time.slice(0, length - 4).split(" ").mkString("T")
      }).get,
      content.event_type,
      content.product_id.toInt,
      content.category_id,
      content.category_code,
      content.brand,
      content.price.toDouble,
      content.user_id.toInt,
      content.user_session,
      )

  override def receive: Receive = {
    case FilePath(file) if Files.exists(Paths.get(file)) =>
      actorThatPersists ! InitBulkPersistence
      val path = Paths.get(file)
      val src = FileIO.fromPath(path)
      //Json must be between brackets [ {}, {},...]
      val selectJson: Flow[ByteString, ByteString, NotUsed] = JsonReader.select("$[*]")
      val parseJson: Flow[ByteString, EventToPersist, NotUsed] = Flow.fromFunction { str: ByteString =>
        val v = str.utf8String.parseJson.convertTo[EventFromJson]
        convertEvent(v)
      }
      src
        .via(selectJson)
        .via(parseJson)
        .runWith(Sink.actorRefWithBackpressure(actorThatPersists, LoadStarted, LoadFinished, t => LoadFailure(t)))
    case FilePath(_) => sender() ! Failure(new FileNotFoundException())
  }
}