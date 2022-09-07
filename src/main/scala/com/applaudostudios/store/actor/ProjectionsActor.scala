package com.applaudostudios.store.actor

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.alpakka.cassandra.scaladsl.{CassandraSession, CassandraSessionRegistry}
import akka.stream.scaladsl.{Sink, Source}
import com.applaudostudios.store.model._

import scala.concurrent.Future

object ProjectionsActor {
  def props()(implicit system: ActorSystem):Props = Props(new ProjectionsActor())
  case class UserCreated(user: User)
  case class ProductAdded(product:Product)
  case class CategoryAdded(category:Category)

  case object StartReading


}

class ProjectionsActor()(implicit readerSystem: ActorSystem) extends Actor with ActorLogging {
  import ProjectionsActor._

  val journal: CassandraReadJournal = PersistenceQuery(readerSystem).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  val session: CassandraSession = CassandraSessionRegistry(readerSystem).sessionFor("akka.projection.cassandra.session-config")

 //Todo

  override def receive:Receive = {
    case StartReading =>
      monitorManagerEvents.runWith(Sink.ignore)
  }


  def monitorManagerEvents: Source[Unit, NotUsed] = journal
    .eventsByPersistenceId("Actor-Manager", 0, Long.MaxValue)
    .map(_.event)
    .mapAsync(8) {
    case UserCreated(user) =>
      //Todo
      log.info("persisting a new user in the user Table")
      Future.successful(())
    case ProductAdded(product) =>
      //Todo
      log.info("Persisting product in the products table")
      Future.successful(())
    case CategoryAdded(category) =>
      //Todo
      log.info("Persisting the category in the category table")
      Future.successful(())
//    case PersistPurchase(date, prodId,catId,catCode,brand,price,userId,userSession) =>
//      //Todo
//      log.info("Persisting into the purchase_by_X Tables")
//      Future.successful(())
  }

}
