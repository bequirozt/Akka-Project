package com.applaudostudios.store.actor

import akka.actor.ActorLogging
import akka.persistence.PersistentActor


class StoreEventActor  extends PersistentActor with ActorLogging{

  override def persistenceId: String = "Command-Actor"

  override def receiveCommand: Receive = ???

  override def receiveRecover: Receive = ???
}
