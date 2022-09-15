package com.applaudostudios.store.domain.manager

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.applaudostudios.store.util.LoadJson.{EventToPersist, LoadFailure, LoadFinished, LoadStarted}
import com.applaudostudios.store.domain.categories.CategoriesManager.CreateCategory
import com.applaudostudios.store.domain.data.{Category, Item, User}
import com.applaudostudios.store.domain.items.ItemsManager.CreateItem
import com.applaudostudios.store.domain.users.UsersManager.PersistUserState



object EcommerceManager {
  //Commands
  case object InitBulkPersistence
  case object FinishBulkPersistence
}

class EcommerceManager(catManager:ActorRef, itemsManager:ActorRef,usersManager: ActorRef) extends Actor with ActorLogging {
  import EcommerceManager._

  override def receive: Receive = {
    case LoadStarted =>
      log.info("Data loading started.... \nChanging Managers to Loading behaviour...")
      catManager ! InitBulkPersistence
      itemsManager ! InitBulkPersistence
      usersManager ! InitBulkPersistence
      sender() ! true
    case EventToPersist(eTime, eType, itemId, cId, cCode, brand, price, uId, uSession) =>
      val category = Category(cId, cCode)
      val item = Item(itemId,brand, cId, price )
      val user = User(uId)
      catManager ! CreateCategory(category)
      itemsManager ! CreateItem(item)
      usersManager ! PersistUserState(user, eTime, eType, itemId, uSession, cId,cCode,brand,price)
      //log.info("Event persisted")
      sender() ! "Proceed"
    case LoadFinished =>
      log.info("Data loading finished.... \nChanging Managers to normal behaviour...")
      itemsManager ! FinishBulkPersistence
      catManager ! FinishBulkPersistence
      usersManager ! FinishBulkPersistence
    case LoadFailure(t) =>
      log.error(s"Actor has failed! with msg: ${t.getMessage}")

  }
}
