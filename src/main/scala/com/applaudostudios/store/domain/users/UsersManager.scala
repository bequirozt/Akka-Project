package com.applaudostudios.store.domain.users

import com.applaudostudios.store.domain._
import akka.actor.{ActorContext, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.DateTime
import akka.pattern.{ask, pipe}
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import com.applaudostudios.store.domain.categories.CategoriesManager.GetCategory
import com.applaudostudios.store.domain.data.{Category, Item, User}
import com.applaudostudios.store.domain.manager.EcommerceManager.{FinishBulkPersistence, InitBulkPersistence}
import com.applaudostudios.store.domain.items.ItemsManager.GetItem
import com.applaudostudios.store.domain.users.UserActor.{AddCart, AddPurchase, AddView, LoadUser, PurchaseCart, RetrieveCart, RetrievePurchases, RetrieveViews, UserOverview}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success}

object UsersManager{
  def props(itemsManager:ActorRef, categoryManager:ActorRef):Props = Props(new UsersManager(itemsManager,categoryManager))

  //Commands
  case object GetUsers
  case class GetUser(id:Long)
  case class CreateUser(user: User)
  case class UpdateUser(user: User)
  case class RemoveUser(id: Long)
  case class ActivateUser(id:Long)  //TODO Implement

  case class PersistUserState(user:User, eTime:DateTime, eType:String,itemId:Int, uSession:String, catId:BigInt, catCode:String, brand:String, price:Double)

  case class GetViews(id:Long )
  case class GetPurchases(id: Long)
  case class GetCart(id: Long)
  case class PersistEvent(userId:Long, itemId:Int, eventType:String)
  case class BuyCart(userId:Long)


  //Events
  case class UserCreated(user:User)
  case class UserDisabled(id:Long)
  case class UserActivated(id:Long)

  //Failures
  case class UserNotFoundException(msg: String) extends RuntimeException(msg)
  case class UserAlreadyRegisteredException(msg: String) extends RuntimeException(msg)
  case class UserAlreadyDeletedException(msg: String) extends RuntimeException(msg)




  case class UserManagerState(users: mutable.Map[Long, ActorRef] = mutable.HashMap[Long, ActorRef](),
                              disabledUsers:mutable.Set[Long] = mutable.HashSet[Long]()
                             ){
    def turnIntoSnapshot: SnapshotContent = SnapshotContent(users.keys.toList, disabledUsers)
  }
  case class SnapshotContent(users: List[Long], disabledUsers:mutable.Set[Long]) {
    def turnIntoState(implicit context: ActorContext, manager: ActorRef): UserManagerState =
      UserManagerState(
        users.map(id => id -> context.actorOf(UserActor.props(id, manager), s"UserActor-$id")).to(mutable.HashMap),
        disabledUsers
      )
  }
}

case class UsersManager(itemsManager:ActorRef, catManager:ActorRef) extends PersistentActor with ActorLogging {
  import UsersManager._
  override def persistenceId: String = s"Users-Manager-Actor"

  var state:UserManagerState = UserManagerState()

  override def receiveCommand: Receive = {
    case InitBulkPersistence =>
      context.become(loadBulkData)
      //Users Related events
    case GetUsers =>
      sender() ! Future.sequence(state.users.values.map(_ ? RetrieveInfo))
    case GetUser(id) if state.users.contains(id) && !state.disabledUsers.contains(id)=>
      state.users(id) forward RetrieveInfo
    case CreateUser(user @ User(id,_,_)) if !state.users.contains(id) =>
      persist(UserCreated(user)) { _ =>
        val userActor = context.actorOf(UserActor.props(user.id,self),s"UserActor-${user.id}" )
        userActor forward LoadUser(user)
        state.users.addOne(user.id -> userActor)
        isSnapshotTime()
      }
    case UpdateUser(user)  if state.users.contains(user.id) && !state.disabledUsers.contains(user.id)=>
      state.users(user.id) forward UpdateUser(user)

      //User Store events
    case GetViews(id) if state.users.contains(id) && !state.disabledUsers.contains(id) =>
      state.users(id) forward RetrieveViews
    case GetCart(id) if state.users.contains(id) && !state.disabledUsers.contains(id) =>
      state.users(id) forward RetrieveCart
    case GetPurchases(id) if state.users.contains(id) && !state.disabledUsers.contains(id)=>
      state.users(id) forward RetrievePurchases
    case PersistEvent(userId, itemId, eventType) if state.users.contains(userId) && !state.disabledUsers.contains(userId)=> //Todo: Errors
        Future { sender()}.andThen {
        case Success(vale) =>
          val senderRef = vale
          (itemsManager ? GetItem(itemId)).andThen {
            case Success(value) =>
              val itemValue = value.asInstanceOf[Item]
              if (itemValue.categoryId == 0) {
                eventType match {
                  case "view" =>
                    (state.users(userId) ? AddView(itemValue, Category(0,""))).pipeTo(senderRef)
                  case "cart" =>
                    (state.users(userId) ? AddCart(itemValue,  Category(0,""))).pipeTo(senderRef)
                  case "purchase" =>
                    (state.users(userId) ? AddPurchase(itemValue,  Category(0,""))).pipeTo(senderRef)
                }
              }else{
                (catManager ? GetCategory(itemValue.categoryId)).andThen {
                  case Success(finalValue) =>
                    val catValue = finalValue.asInstanceOf[Category]
                    eventType match {
                      case "view" =>
                        (state.users(userId) ? AddView(itemValue, catValue)).pipeTo(senderRef)
                      case "cart" =>
                        (state.users(userId) ? AddCart(itemValue, catValue)).pipeTo(senderRef)
                      case "purchase" =>
                        (state.users(userId) ? AddPurchase(itemValue, catValue)).pipeTo(senderRef)
                    }
                }
              }
            case Failure(exception) =>
              senderRef ! exception
          }
      }
    case BuyCart(id) if state.users.contains(id) && !state.disabledUsers.contains(id)=>
      state.users(id) forward PurchaseCart

      // User Activation / Disabling
    case RemoveUser(id) if state.disabledUsers.contains(id) =>
      sender() ! Failure(UserAlreadyDeletedException(s"User already deleted"))
    case ActivateUser(id) if !state.disabledUsers.contains(id) =>
      sender() ! s"User already active"
    case RemoveUser(id) if state.users.contains(id) =>
      persist(UserDisabled(id)) { _ =>
        state.disabledUsers.addOne(id)
        isSnapshotTime()
        state.users(id) forward RetrieveInfo
      }

    case ActivateUser(id) if state.disabledUsers.contains(id) =>
      persist(UserActivated(id)) {_=>
        state.disabledUsers -= id
        isSnapshotTime()
        sender() ! "User activated successfully"
      }

    case GetCart(_) | GetViews(_) | GetPurchases(_) | GetUser(_) | UpdateUser(_) |
         BuyCart(_) |  PersistEvent(_,_,_) | RemoveUser(_) | ActivateUser(_) =>
      sender() ! Failure(UserNotFoundException(s"User not Found"))
    case CreateUser(User(id, _, _)) if state.users.contains(id) =>
      sender() ! Failure(UserAlreadyRegisteredException(s"User already registered with id: $id"))

    //Snapshot commands
    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Saving snapshot succeeded: ${metadata.persistenceId} - ${metadata.timestamp}")
    case SaveSnapshotFailure(metadata, reason) =>
      log.warning(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")
  }

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      log.debug("Recovery completed")
    case UserCreated(user) =>
      val userActor = context.actorOf(UserActor.props(user.id, self), s"UserActor-${user.id}")
      state.users.addOne(user.id -> userActor)
    case UserDisabled(id) =>
      state.disabledUsers.addOne(id)
    case UserActivated(id) =>
      state.disabledUsers-=id
    case SnapshotOffer(metadata, contents:SnapshotContent) =>
      log.info(s"Recovered Snapshot for Actor: ${metadata.persistenceId} - ${metadata.timestamp}")
      state = contents.turnIntoState
  }

  def loadBulkData: Receive = {
    case event @ PersistUserState(user, _, _,_, _, _, _, _,_) if state.users.contains(user.id) =>
      state.users(user.id) ! event
    case event @ PersistUserState(user,_, _, _, _, _, _, _, _) =>
      persist(UserCreated(user)) { _ =>
        val userActor = context.actorOf(UserActor.props(user.id,self),s"UserActor-${user.id}" )
        userActor ! LoadUser(user)
        userActor ! event
        state.users.addOne(user.id -> userActor)
        isSnapshotTime(SNAPSHOT_USERS_MANAGER_BULK_SIZE)
      }
    case FinishBulkPersistence =>
      context.unbecome()
    case SaveSnapshotSuccess(metadata) =>
      log.debug(s"Saving snapshot succeeded: ${metadata.persistenceId} - ${metadata.timestamp}")
    case SaveSnapshotFailure(metadata, reason) =>
      log.warning(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")
  }


  def isSnapshotTime(snapSize:Int = SNAPSHOT_USERS_MANAGER_SIZE) : Unit = {
    if (lastSequenceNr % snapSize == 0 && lastSequenceNr != 0) {
      saveSnapshot(state.turnIntoSnapshot)
    }
  }
}

