package com.applaudostudios.store.domain.users

import com.applaudostudios.store.domain._
import akka.actor.{ActorContext, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.DateTime
import akka.pattern.ask
import akka.persistence.{PersistentActor, RecoveryCompleted, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import com.applaudostudios.store.domain.categories.CategoriesManager.GetCategory
import com.applaudostudios.store.domain.data.{Category, Item, User}
import com.applaudostudios.store.domain.manager.EcommerceManager.{FinishBulkPersistence, InitBulkPersistence}
import com.applaudostudios.store.domain.items.ItemsManager.GetItem
import com.applaudostudios.store.domain.users.UserActor.{AddCart, AddPurchase, AddView, LoadUser, PurchaseCart, RetrieveCart, RetrievePurchases, RetrieveViews, UserOverview}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.concurrent.{Await, Future}

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
  var operationsCount:Int = 0


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
    case PersistEvent(userId, itemId, eventType) if state.users.contains(userId) && !state.disabledUsers.contains(userId)=>
      val infoToAdd: Option[(Option[Item],Option[Category])] = Await.result(itemsManager ? GetItem(itemId), REQUEST_TIME) match {
        case item: Item =>
          if (item.categoryId == 0 )
            Some(Some(item), None)
          else {
            val category: Option[Category] = Await.result(catManager ? GetCategory(item.categoryId), REQUEST_TIME) match {
              case cat: Category => Some(cat)
              case _ => None
            }
            if( category.isDefined) {
              Some(Some(item), category)
            }else
            Some(Some(item), None)
          }
        case _ => None
      }
      if (infoToAdd.isDefined) {
        val info = infoToAdd.get
        val item = info._1.get
        val cat = info._2.getOrElse(Category(0, ""))
        if (eventType.equals("view")) state.users(userId) forward AddView(item, cat)
        if (eventType.equals("purchase")) state.users(userId) forward AddPurchase(item, cat)
        if (eventType.equals("cart")) state.users(userId) forward AddCart(item, cat)
      }
    case BuyCart(id) if state.users.contains(id) && !state.disabledUsers.contains(id)=>
      state.users(id) forward PurchaseCart

      // User Activation / Disabling
    case RemoveUser(id) if !state.users.contains(id) =>
      sender() ! s"The User is not registered" //Todo handle failure
    case ActivateUser(id) if !state.users.contains(id) =>
      sender() ! s"The User is not registered" //Todo handle failure
    case RemoveUser(id) if state.disabledUsers.contains(id) =>
      sender() ! s"User already deleted"
    case ActivateUser(id) if !state.disabledUsers.contains(id) =>
      sender() ! s"User already active"
    case RemoveUser(id) =>
      persist(UserDisabled(id)) { _ =>
        state.disabledUsers.addOne(id)
        isSnapshotTime()
        val user: Option[User] = Await.result(state.users(id) ? RetrieveInfo , REQUEST_TIME) match {
          case user: UserOverview => Some(user.user)
          case _ => None
        }
        sender() ! user.getOrElse("User not found")
      }
    case ActivateUser(id) if state.disabledUsers.contains(id) =>
      persist(UserActivated(id)) {_=>
        state.disabledUsers -= id
        isSnapshotTime()
        sender() ! "User activated successfully"
      }

    case GetCart(_) | GetViews(_) | GetPurchases(_) | GetUser(_) | UpdateUser(_) | BuyCart(_) |  PersistEvent(_,_,_) =>
      sender() ! s"User not Found" //Todo Failure
    case CreateUser(User(id, _, _)) if state.users.contains(id) =>
      sender() ! s"User already registered with id: $id" //Todo: Failure

    //Snapshot commands
    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Saving snapshot succeeded: ${metadata.persistenceId} - ${metadata.timestamp}")
    case SaveSnapshotFailure(metadata, reason) =>
      log.info(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")
      operationsCount = SNAPSHOT_USERS_MANAGER_SIZE
  }

  override def receiveRecover: Receive = {
    case RecoveryCompleted =>
      log.debug("Recovery completed")
    case UserCreated(user) =>
      val userActor = context.actorOf(UserActor.props(user.id, self), s"UserActor-${user.id}")
      state.users.addOne(user.id -> userActor)
      operationsCount += 1
    case UserDisabled(id) =>
      state.disabledUsers.addOne(id)
      operationsCount += 1
    case UserActivated(id) =>
      state.disabledUsers-=id
      operationsCount+=1
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
      log.debug(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")
      operationsCount = SNAPSHOT_USERS_MANAGER_BULK_SIZE
  }


  def isSnapshotTime(snapSize:Int = SNAPSHOT_USERS_MANAGER_SIZE) : Unit = {
    if (operationsCount >= snapSize) {
      saveSnapshot(state.turnIntoSnapshot)
      operationsCount = 0
    } else
      operationsCount+=1
  }
}

