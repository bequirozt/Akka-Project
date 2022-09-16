package com.applaudostudios.store.domain.items

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted}
import com.applaudostudios.store.domain.{RetrieveInfo, StopActor}
import com.applaudostudios.store.domain.data.Item
import com.applaudostudios.store.domain.items.ItemsManager.ItemCreated

object ItemActor {
  def props(id:Int, manager:ActorRef):Props = Props(new ItemActor(id, manager))
  //Command
  case class LoadItem(item:Item)
  case class UpdateItem(item:Item)
  case class AssociateCategory(catId:BigInt)
  case class DeleteCategory(id:BigInt)

  //Event
  case class ItemUpdated(item:Item)
  case class CategoryDeleted(id:BigInt)

  case class ItemState(id:Int, var brand:String="",var categoryId:BigInt=BigInt("0"),var price:Double = 0.0, var prevCategory:BigInt =BigInt("0"))
}

case class ItemActor(id:Int, manager:ActorRef) extends PersistentActor with ActorLogging {
  import ItemActor._
  override def persistenceId: String = s"Item-$id"
  val state:ItemState = ItemState(id)

  def loadInfo(item:Item):Unit = {
    val Item(_, brand, categoryId, price) = item
    state.brand = brand
    state.price = price
    state.categoryId = categoryId
  }
  override def receiveCommand: Receive = {
    case LoadItem(item) =>
      persist(ItemCreated(item)) { _ =>
        loadInfo(item)
        sender() ! item
      }
    case RetrieveInfo =>
      sender() ! Item(id, state.brand,state.categoryId,state.price)
    case UpdateItem(item) =>
      persist(ItemUpdated(item)) { ev =>
        loadInfo(ev.item)
        sender() ! item
      }
    case AssociateCategory(catId:BigInt) =>
      val item = Item(state.id,state.brand,catId,state.price)
      persist(ItemUpdated(item)) {_=>
        loadInfo(item)
        sender() ! item
      }
    case DeleteCategory(catId) if state.categoryId==catId =>
      persist(CategoryDeleted(catId)) { _ =>
        state.prevCategory = state.categoryId
        state.categoryId = 0
      }
    case StopActor =>
      sender() ! Item(state.id,state.brand,state.categoryId,state.price)
      context.stop(self)
  }

  override def receiveRecover: Receive = {
    case ItemCreated(item) =>
      loadInfo(item)
    case ItemUpdated(item) =>
      loadInfo(item)
    case CategoryDeleted(id) =>
      state.prevCategory = id
      state.categoryId = 0
    case RecoveryCompleted =>
      log.debug("Recovery completed")
  }
}
