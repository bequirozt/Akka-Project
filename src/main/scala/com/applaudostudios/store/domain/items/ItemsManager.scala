package com.applaudostudios.store.domain.items

import com.applaudostudios.store.domain._
import akka.actor.{ActorContext, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.pattern.pipe
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import com.applaudostudios.store.domain.categories.CategoriesManager.VerifyCategory
import com.applaudostudios.store.domain.data.Item
import com.applaudostudios.store.domain.manager.EcommerceManager.{FinishBulkPersistence, InitBulkPersistence}
import com.applaudostudios.store.domain.items.ItemActor.{DisableCategory, LoadItem, UpdateItem}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success}

object ItemsManager {
  def props(categoryManager:ActorRef):Props = Props(new ItemsManager(categoryManager))

  //Commands
  case object GetItems
  case class GetItem(id:Int)
  case class CreateItem(item:Item)
  case class RemoveItem(id:Int)
  case class CategoryRemoved(id:BigInt)
  case class SecureCreate(item:Item)

  //Events
  case class ItemCreated(item: Item)
  case class ItemDisabled(id:Int)

  //Exceptions
  case class ItemCreationException(msg:String) extends RuntimeException(msg)
  case class ItemUpdateException(msg:String) extends RuntimeException(msg)
  case class ItemAlreadyDeletedException(msg:String) extends RuntimeException(msg)
  case class ItemNotFoundException(msg:String) extends RuntimeException(msg)


  case class ItemsManagerState(items: mutable.Map[Int, ActorRef] = mutable.HashMap[Int, ActorRef](),
                               disabledItems:mutable.Set[Int] = mutable.HashSet[Int]() ){
    def turnIntoSnapshot: SnapshotContent = SnapshotContent(items.keys.toList, disabledItems)
  }

  case class SnapshotContent(items:List[Int], deletedItems:mutable.Set[Int]) {
    def turnIntoState(implicit context: ActorContext, store: ActorRef): ItemsManagerState =
      ItemsManagerState(
        items.map(id =>  id->context.actorOf(ItemActor.props(id, store), s"ItemActor-$id")).to(mutable.HashMap),
        deletedItems
      )
  }
}



case class ItemsManager(categoryManager:ActorRef) extends PersistentActor with ActorLogging {

  import ItemsManager._

  var state: ItemsManagerState = ItemsManagerState()
  var operationsCount: Int = 0

  override def persistenceId: String = "Items-Manager-Actor"


  override def receiveCommand: Receive = {
    case InitBulkPersistence =>
      context.become(loadBulkData)
    case GetItems =>
      sender() ! Future.sequence(state.items.values.map(_ ? RetrieveInfo))
    case GetItem(id) if state.items.contains(id) && !state.disabledItems.contains(id) =>
      state.items(id).forward(RetrieveInfo)
    case CreateItem(Item(id, _, _, _)) if state.items.contains(id) =>
      sender() ! Failure(ItemCreationException(s"Item already registered"))
    case CreateItem(item @ Item(id, _, cat, _)) if cat == 0 =>
      persist(ItemCreated(item)) { _ =>
        val itemActor = context.actorOf(ItemActor.props(id, self), s"ItemActor-$id")
        state.items.addOne(id -> itemActor)
        itemActor forward LoadItem(item)
        isSnapshotTime()
      }
    case CreateItem(item @ Item(id, _, catId, _))  =>
      (categoryManager ? VerifyCategory(catId, sender())).onComplete {
        case Success(tuple) =>
          val (isValid, senderRef): (Boolean,ActorRef ) = tuple.asInstanceOf[(Boolean, ActorRef)]
          if(isValid){
            val itemActor = context.actorOf(ItemActor.props(id, self), s"ItemActor-$id")
            (itemActor ? LoadItem(item)).pipeTo(senderRef)
            persist(ItemCreated(item)) { _ =>
              state.items.addOne(id -> itemActor)
              isSnapshotTime()
            }
          }else{
            senderRef ! Failure(ItemCreationException("Item failed to create. The category associated with it is not registered"))
          }
        case Failure(exception) => throw exception
       }

    case UpdateItem(item @ Item(id,_,catId,_)) if state.items.contains(id) && !state.disabledItems.contains(id) && catId == 0 =>
      state.items(id) forward UpdateItem(item)

    case UpdateItem(item @ Item(id,_,catId,_)) if state.items.contains(id) && !state.disabledItems.contains(id) =>
      (categoryManager ? VerifyCategory(catId, sender())).onComplete {
        case Success(tuple) =>
          val (isValid, senderRef): (Boolean, ActorRef) = tuple.asInstanceOf[(Boolean, ActorRef)]
          if (isValid) {
              (state.items(id) ? UpdateItem(item)).pipeTo(senderRef)
          } else {
            senderRef ! Failure(ItemUpdateException("Item failed to update. The category associated with it is not registered"))
          }
        case Failure(exception) => throw exception
      }

    case CategoryRemoved(id) =>
      state.items.values.map { actor =>
        actor ! DisableCategory(id)
      }
    case RemoveItem(id) if state.disabledItems.contains(id) =>
      sender() ! Failure(ItemAlreadyDeletedException(s"Item already deleted"))
    case RemoveItem(id) if state.items.contains(id) =>
      persist(ItemDisabled(id)) { _ =>
        state.disabledItems.addOne(id)
        isSnapshotTime()
        state.items(id) forward RetrieveInfo
      }
    case UpdateItem(_) | GetItem(_) | RemoveItem(_) =>
      sender() ! Failure(ItemNotFoundException(s"Item not found"))
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
    case ItemDisabled(id) =>
      state.disabledItems.addOne(id)
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
