package com.applaudostudios.store.domain.items

import com.applaudostudios.store.domain._
import akka.actor.{ActorContext, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import com.applaudostudios.store.domain.categories.CategoriesManager.GetCategory
import com.applaudostudios.store.domain.data.{Category, Item}
import com.applaudostudios.store.domain.manager.EcommerceManager.{FinishBulkPersistence, InitBulkPersistence}
import com.applaudostudios.store.domain.items.ItemActor.{LoadItem, UpdateItem}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.concurrent.{Await, Future}

object ItemsManager {
  def props(categoryManager:ActorRef):Props = Props(new ItemsManager(categoryManager))
  //Commands
  case object GetItems
  case class GetItem(id:Int)
  case class CreateItem(item:Item)

  case class RemoveItem(id:Int)

  //Events
  case class ItemCreated(item: Item)


  case class ItemsManagerState(items: mutable.Map[Int, ActorRef] = mutable.HashMap[Int, ActorRef]()){
    def turnIntoSnapshot: SnapshotContent = SnapshotContent(items.keys.toList)
  }

  case class SnapshotContent(items:List[Int]) {
    def turnIntoState(implicit context: ActorContext, store: ActorRef): ItemsManagerState =
      ItemsManagerState(
        items.map(id =>  id->context.actorOf(ItemActor.props(id, store), s"ItemActor-$id")).to(mutable.HashMap)
      )
  }
}



case class ItemsManager(categoryManager:ActorRef) extends PersistentActor with ActorLogging {
  import ItemsManager._
  var state: ItemsManagerState = ItemsManagerState()
  var operationsCount:Int = 0
  override def persistenceId: String = "Items-Manager-Actor"


  override def receiveCommand: Receive = {
    case InitBulkPersistence =>
      context.become(loadBulkData)
    case GetItems =>
      sender() ! Future.sequence(state.items.values.map(_ ? RetrieveInfo))
    case GetItem(id) if state.items.contains(id)=>
      state.items(id).forward(RetrieveInfo)

    case CreateItem(Item(id,_,_,_)) if state.items.contains(id) =>
      sender() ! s"Item already registered"  //Todo: Generate Appropriate Failure BadRequest
    case CreateItem(item @ Item(id,_,cat,_)) if cat==0 =>
      persist(ItemCreated(item)) { _ =>
        val itemActor = context.actorOf(ItemActor.props(id, self), s"ItemActor-$id")
        itemActor forward LoadItem(item)
        state.items.addOne(id->itemActor)
        isSnapshotTime()
      }
    case CreateItem(item @ Item(id, _, catId, _))  =>
      val category: Option[Any] = Await.result( categoryManager ? GetCategory(catId), REQUEST_TIME ) match {
        case cat: Category => Some(cat)
        case _ => None
      }
      if(category.isDefined)
        persist(ItemCreated(item)) { _ =>
          val itemActor = context.actorOf(ItemActor.props(id, self), s"ItemActor-$id")
          itemActor forward LoadItem(item)
          state.items.addOne(id -> itemActor)
          isSnapshotTime()
        }
      else
        sender() ! s"Item failed to create. The category associated with it is not registered" //todo: Failure bad request
    case UpdateItem(item @ Item(id,_,catId,_)) if state.items.contains(id) && catId == 0 =>
      state.items(id) forward UpdateItem(item)
    case UpdateItem(item @ Item(id,_,catId,_)) if state.items.contains(id) =>
      val category: Option[Any] = Await.result(categoryManager ? GetCategory(catId), REQUEST_TIME) match {
        case cat:Category => Some(cat)
        case _ => None
      }
      if( category.isDefined)
        state.items(id) forward UpdateItem(item)
      else
        sender() ! s"Item failed to update. The category associated with it is not registered" //todo: Failure bad request
    case UpdateItem(_) | GetItem(_) =>
      sender() ! s"Item not found" //Todo: generate Appropriate Failure NotFound
    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Saving snapshot succeeded: ${metadata.persistenceId} - ${metadata.timestamp}")
    case SaveSnapshotFailure(metadata, reason) =>
      log.info(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")
      operationsCount = SNAPSHOT_ITEMS_MANAGER_SIZE
  }


  override def receiveRecover: Receive = {
    case ItemCreated(item) =>
      val itemActor = context.actorOf(ItemActor.props(item.id, self), s"ItemActor-${item.id}")
      state.items.addOne(item.id -> itemActor)
      operationsCount += 1
    case SnapshotOffer(metadata, contents:SnapshotContent) =>
      log.info(s"Recovered Snapshot for Actor: ${metadata.persistenceId} - ${metadata.timestamp}")
      state = contents.turnIntoState
    case RecoveryCompleted =>
      log.debug("Recovery completed")
  }

  def loadBulkData: Receive = {
    case CreateItem(item) if !state.items.contains(item.id) =>
      persist(ItemCreated(item)) { _ =>
        val itemActor = context.actorOf(ItemActor.props(item.id, self), s"ItemActor-${item.id}")
        itemActor ! LoadItem(item)
        state.items.addOne(item.id -> itemActor)
        isSnapshotTime(SNAPSHOT_ITEMS_MANAGER_BULK_SIZE);
      }
    case CreateItem(item) =>
      state.items(item.id) ! UpdateItem(item)
    case SaveSnapshotSuccess(metadata) =>
      log.debug(s"Saving snapshot succeeded: ${metadata.persistenceId} - ${metadata.timestamp}")
    case SaveSnapshotFailure(metadata, reason) =>
      log.debug(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")
      operationsCount = SNAPSHOT_ITEMS_MANAGER_BULK_SIZE
    case FinishBulkPersistence =>
      context.become(receiveCommand)
  }

  def isSnapshotTime(size:Int = SNAPSHOT_ITEMS_MANAGER_SIZE): Unit = {
    if (operationsCount >= size) {
      saveSnapshot(state.turnIntoSnapshot)
      operationsCount = 0
    } else
      operationsCount += 1
  }
}
