package com.applaudostudios.store.actor

import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import LoadJson.{EventToPersist, LoadFailure, LoadFinished, LoadStarted}

object ManagerActor {
  case object InitBulkPersistence

}

class ManagerActor(projectionActor: ActorRef) extends PersistentActor with ActorLogging {
  import ManagerActor._
  override def persistenceId: String = "Actor-Manager"

  override def receiveCommand: Receive = {
    case InitBulkPersistence =>
      log.info("Changing behaviour")
      context.become(receiveEventsFromFile)
    case x => log.info(s"Unhandled: $x")
  }

  override def receiveRecover: Receive = {
    case RecoveryCompleted => log.info("Ready to go") //TODO Change behaviour
    case message => log.info(s"Recoveryng Unhandled: $message")
  }


  def receiveEventsFromFile: Receive = {
    case ev@EventToPersist(eTime, eType, pId, cId, cCode, brand, price, uId, uSession) =>
      //TODO , start new actors, and ensure that the DB is persisting on tables
      log.info("Event persisted")
      sender() ! "Proceed"
    case LoadStarted =>
      log.info("Data loading starting")
      sender() ! true
    case LoadFinished =>
      log.info("Data loading finished")
      context.become(receiveCommand)
    case LoadFailure(t) =>
      log.info("Actor has failed! with msg: ${t.getMessage}")
    case x => log.warning(s"Unhandled: $x")
  }
}
