package com.applaudostudios.store.domain.users

import akka.actor.{ActorContext, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.DateTime
import akka.persistence.{PersistentActor, SaveSnapshotFailure, SaveSnapshotSuccess, SnapshotOffer}
import com.applaudostudios.store.domain.{Cart, Purchase, RetrieveInfo, SNAPSHOT_USER_SIZE, View}
import com.applaudostudios.store.domain.data.{Category, Item, User}
import com.applaudostudios.store.domain.users.UsersManager.{PersistUserState, UpdateUser}

import java.util.UUID
import scala.collection.mutable

object UserActor {
  def props(id:Long, manager:ActorRef):Props = Props(new UserActor(id, manager))
  //Command
  case class LoadUser(user:User)


  case object RetrieveViews
  case object RetrievePurchases
  case object RetrieveCart
  case object PurchaseCart
  case object DeleteUser

  case class AddView(item:Item, category:Category, eventType:String ="view")
  case class AddPurchase(item:Item, category:Category, eventType:String ="purchase")
  case class AddCart(item:Item, category:Category, eventType:String ="cart")


  //Event
  case class UserCreated(user:User)
  case class UserUpdated(user:User)
  case object CartDeleted
  case class ViewItemAdded(view: View)
  case class PurchaseProductAdded(purchase: Purchase)
  case class CartProductAdded(cart: Cart)


  //Response
  case class UserOverview(user: User, totalPurchases:Int= 0, totalViews:Int = 0, totalCarts: Int = 0)

  //State
  case class UserState(userData:User,
                       var purchases:mutable.Set[Purchase]=mutable.HashSet[Purchase](),
                       var views:mutable.Set[View]=mutable.HashSet[View](),
                       var cart: mutable.Set[Cart]=mutable.HashSet[Cart]()
                      ) {
    def turnIntoSnapshot: SnapshotContent = SnapshotContent(
      userData,
      purchases,
      views,
      cart
    )
  }

  case class SnapshotContent(userData:User, purchases:mutable.Set[Purchase], views:mutable.Set[View], cart:mutable.Set[Cart]) {
    def turnIntoState(implicit context: ActorContext, store: ActorRef): UserState =
      UserState(
        userData,
        purchases,
        views,
        cart
      )
  }

}

case class UserActor(id:Long, manager:ActorRef) extends PersistentActor with ActorLogging {
  import UserActor._
  override def persistenceId: String = s"User-$id"
  var state:UserState = UserState(User(id))

  def loadInfo(user:User):Unit = {
    val User(_,name, email ) = user
    state.userData.name = name
    state.userData.email = email
  }

  override def receiveCommand: Receive = {
    case LoadUser(user) =>
      persist(UserCreated(user)) { ev=>
        loadInfo(ev.user)
        sender ! state.userData
        isSnapshotTime()
      }
    case RetrieveInfo =>
      sender() ! UserOverview(state.userData, state.purchases.size, state.views.size, state.cart.size)
    case UpdateUser(user) =>
      persist( UserUpdated(user)) { _ =>
        loadInfo(user)
        sender ! UserOverview(state.userData, state.purchases.size, state.views.size, state.cart.size)
        isSnapshotTime()
      }
    case RetrieveViews =>
      sender() ! state.views.toList
    case RetrievePurchases =>
      sender() ! state.purchases.toList
    case RetrieveCart =>
      sender() ! state.cart.toList
    case AddView(item, category, eventType) =>
      val view = View(DateTime.now,eventType,item.id,category.id,category.code,item.brand,item.price,state.userData.id, UUID.randomUUID().toString)
      persist(ViewItemAdded(view)) { _ =>
        state.views.addOne(view)
        sender() ! view
        isSnapshotTime()
      }
    case AddPurchase(item, category, eventType) =>
      val purchase = Purchase(DateTime.now, eventType,item.id, category.id, category.code, item.brand, item.price, state.userData.id, UUID.randomUUID().toString)
      persist(PurchaseProductAdded(purchase)) { _ =>
        state.purchases.addOne(purchase)
        sender() ! purchase
        isSnapshotTime()
        persist(CartDeleted) { _ =>
          state.cart = mutable.HashSet[Cart]()
          isSnapshotTime()
        }
      }
    case PurchaseCart =>
      if (state.cart.isEmpty) sender() ! s"The cart is empty" //todo handle failure
      else {
        val elementsToPurchase: List[Purchase] = state.cart.map(e => e.toPurchase(DateTime.now, e.userSession)).toList
        val purchaseEvents: List[PurchaseProductAdded] = elementsToPurchase.map(e => PurchaseProductAdded(e))
        persistAllAsync(purchaseEvents) { ev =>
          state.purchases.addOne(ev.purchase)
          isSnapshotTime()
        }
        deferAsync(CartDeleted) { _ =>
          state.cart = mutable.HashSet[Cart]()
          isSnapshotTime()
          sender() ! elementsToPurchase
        }
      }
      case AddCart(item, category, eventType)
      =>
      val cart = Cart(DateTime.now, eventType, item.id, category.id, category.code, item.brand, item.price, state.userData.id, UUID.randomUUID().toString)
      persist(CartProductAdded(cart)) { _ =>
        state.cart.addOne(cart)
        sender() ! cart
        isSnapshotTime()
      }
    case PersistUserState(user, eTime, eType,itemId, uSession, catId, catCode, brand, price)  => eType match {
      case "view" =>
        val view: View = View(eTime, eType,itemId, catId, catCode, brand, price, user.id, uSession)
        persist(ViewItemAdded(view: View)) { _ =>
          state.views.add(view)
          sender() ! view
        }
      case "purchase" =>
        val purchase: Purchase = Purchase(eTime, eType, itemId, catId, catCode, brand, price, user.id, uSession)
        persist(PurchaseProductAdded(purchase)) { _ =>
          state.purchases.add(purchase)
          sender() ! purchase
          isSnapshotTime()
          persist(CartDeleted) { _ =>
            state.cart = mutable.HashSet[Cart]()
            isSnapshotTime()
          }
        }
      case "cart" =>
        val cart: Cart = Cart(eTime, eType, itemId,  catId, catCode, brand, price, user.id, uSession)
          persist (CartProductAdded (cart: Cart) ) { _ =>
            state.cart.add(cart)
            sender() ! cart
          }
    }
    case SaveSnapshotSuccess(metadata) =>
      log.info(s"Saving snapshot succeeded: ${metadata.persistenceId} - ${metadata.timestamp}")
    case SaveSnapshotFailure(metadata, reason) =>
      log.warning(s"Saving snapshot failed: ${metadata.persistenceId} - ${metadata.timestamp} because of $reason.")
  }

  override def receiveRecover: Receive = {
    case UserCreated(user) =>
      loadInfo(user)
    case UserUpdated(user) =>
      loadInfo(user)
    case ViewItemAdded(view) =>
      state.views.addOne(view)
    case PurchaseProductAdded(purchase) =>
      state.purchases.addOne(purchase)
    case CartDeleted =>
      state.cart = mutable.HashSet[Cart]()
    case CartProductAdded(cart) =>
      state.cart.addOne(cart)
    case SnapshotOffer(metadata, contents: SnapshotContent) =>
      log.info(s"Recovered Snapshot for Actor: ${metadata.persistenceId} - ${metadata.timestamp}")
      state = contents.turnIntoState
  }





  def isSnapshotTime(snapSize: Int = SNAPSHOT_USER_SIZE): Unit = {
    if (lastSequenceNr % snapSize == 0 && lastSequenceNr != 0) {
      saveSnapshot(state.turnIntoSnapshot)
    }
  }
}
