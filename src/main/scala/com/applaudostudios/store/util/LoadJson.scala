package com.applaudostudios.store.util

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.DateTime
import akka.stream.alpakka.json.scaladsl.JsonReader
import akka.stream.scaladsl.{FileIO, Flow, Sink}
import akka.util.ByteString
import spray.json.DefaultJsonProtocol.jsonFormat9
import spray.json._
import java.nio.file.{Files, Paths}
import scala.concurrent.duration.DurationInt

object LoadJson {

  def props(persistenceActor: ActorRef): Props = Props(new LoadJson(persistenceActor))
  case class EventFromJson(event_time:String, event_type:String, product_id:String,
                           category_id:BigInt, category_code:String, brand:String, price:String,
                           user_id:String, user_session:String)
  //Commands
  case class FilePath(file:String)


    case object LoadStarted
    case object LoadFinished
    case class LoadFailure(exception:Throwable)
    case class EventToPersist(
                               eventTime: DateTime,
                               eventType: String,
                               itemId: Int,
                               categoryId: BigInt,
                               categoryCode: String,
                               brand: String,
                               price: Double,
                               userId: Long,
                               userSession: String
                             )
}

class LoadJson(actorThatPersists: ActorRef) extends Actor with ActorLogging with SprayJsonSupport with BasicFormats {
  import LoadJson._
  implicit val system:ActorSystem = context.system
  implicit val  eventFormat: RootJsonFormat[EventFromJson] = jsonFormat9(EventFromJson)


  override def receive: Receive = {
    case FilePath(file) if Files.exists(Paths.get(file)) =>
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
        .throttle(1000, 3.seconds)
        .runWith(Sink.actorRefWithBackpressure(actorThatPersists, LoadStarted, LoadFinished, t => LoadFailure(t)))
    case FilePath(_) => log.error("Incorrect Path")
  }

  def convertEvent(content: EventFromJson): EventToPersist =
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
      content.user_id.toLong,
      content.user_session,
    )

}