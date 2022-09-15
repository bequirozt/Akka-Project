package com.applaudostudios.store.http

import akka.actor.ActorRef
import com.applaudostudios.store.domain._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

import scala.util.{Failure, Success}
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.ask
import com.applaudostudios.store.domain.categories.CategoriesManager.{CreateCategory, GetCategories, GetCategory, RemoveCategory, UpdateCategory}
import com.applaudostudios.store.domain.data.{Category, InputItem, Item, User}
import com.applaudostudios.store.domain.items.ItemActor.UpdateItem
import com.applaudostudios.store.domain.items.ItemsManager.{CreateItem, GetItem, GetItems, RemoveItem}
import com.applaudostudios.store.domain.users.UserActor.{PurchaseCart, UserOverview}
import com.applaudostudios.store.domain.users.UsersManager.{BuyCart, CreateUser, GetCart, GetPurchases, GetUser, GetUsers, GetViews, PersistEvent, RemoveUser, UpdateUser}

import scala.concurrent.Future


case class Controller(catManager:ActorRef,itemsManager:ActorRef,usersManager:ActorRef)
  extends DataJsonProtocol
    with SprayJsonSupport {

  val mainRoute: Route = {
    pathPrefix("ecommerce") {
      pathPrefix("item") {
        pathEndOrSingleSlash {
          get {
            onSuccess(itemsManager ? GetItems) {
              case seq: Future[Seq[Item]] =>
                onComplete(seq) {
                  case Success(items) => complete(items)
                  case _ => complete(StatusCodes.InternalServerError, "Internal server Error") //Failure ISE
                }
            }
          } ~
          post {
            entity(as[Item]) { item =>
              onSuccess(itemsManager ? CreateItem(item)) {
                case item: Item =>
                  complete(StatusCodes.OK, item)
                case _ => complete(StatusCodes.BadRequest) //Todo: Failure (Cat NotFound)
              }
            }
          }~
          post {
            entity(as[InputItem]) { inputItem =>
              onSuccess(itemsManager ? CreateItem(Item(inputItem.id, inputItem.brand, BigInt("0"), inputItem.price))) {
                case item: Item => complete(StatusCodes.OK, item)
                case msg: String => complete(StatusCodes.BadRequest, msg) //Todo Failure (BadReq)
              }
            }
          }~
          (put | patch) {
            entity(as[Item]) { item =>
              onSuccess(itemsManager ? UpdateItem(item)) {
                case updatedItem: Item => complete(updatedItem)
                case error: String => complete(StatusCodes.BadRequest, error) //Todo: Failure (Cat Not Found | Item NF)
              }
            }
          }
        } ~
        path(Segment) { id: String =>
          get {
            onSuccess(itemsManager ? GetItem(id.toInt)) {
              case item: Item => complete(StatusCodes.OK, item)
              case failure: String => complete(StatusCodes.NotFound, failure) //Todo: Failure (Item NotFound)
            }
          }
        }
      }~
      pathPrefix("category") {
        pathEndOrSingleSlash {
          get {
            onSuccess(catManager ? GetCategories) {
              case seq: Future[Seq[Category]] =>
                onComplete(seq) {
                  case Success(categories) => complete(categories)
                  case _ => complete(StatusCodes.InternalServerError, "Internal Server Error") //TODO Failure SERVER ERR
                }
            }
          } ~
          post {
            entity(as[Category]) { inputCat =>
              onSuccess(catManager ? CreateCategory(inputCat)) {
                case category: Category => complete(StatusCodes.OK, category)
                case msg: String => complete(StatusCodes.BadRequest, msg) //Todo Failure (BadReq)
              }
            }
          }~
          (put | patch) {
            entity(as[Category]) { catInput =>
              onSuccess(catManager ? UpdateCategory(catInput)) {
                case updatedCategory: Category => complete(updatedCategory)
                case msg:String => complete(StatusCodes.NotFound, msg)//Todo: Failure (Cat Not found | Cat NF)
              }
            }
          }
        } ~
        path(Segment) { id: String =>
          get {
            onSuccess(catManager ? GetCategory(BigInt(id))) {
              case cat: Category => complete(StatusCodes.OK, cat)
              case failure: String => complete(StatusCodes.NotFound, failure) //Todo: Failure (Cat NotFound)
            }
          }
        }
      } ~
      pathPrefix("user") {
        pathEndOrSingleSlash {
          get {
            onSuccess(usersManager ? GetUsers) {
              case seq: Future[Seq[UserOverview]] =>
                onComplete(seq) {
                  case Success(users) => complete(users)
                  case Failure(exception) => throw exception
                }
            }
          } ~
          post {
            entity(as[User]) { inputUser =>
              onSuccess(usersManager ? CreateUser(inputUser)) {
                case user: User => complete(StatusCodes.OK, user)
                case msg: String=> complete(StatusCodes.BadRequest, msg) //Todo Failure (BadReq)
              }
            }
          }~
          (put | patch) {
            entity(as[User]) { userInput =>
              onSuccess(usersManager ? UpdateUser(userInput)) {
                case updatedUser: UserOverview => complete(updatedUser)
                case msg:String => complete(StatusCodes.NotFound, msg) //Todo: Failure (User Not found)
              }
            }
          }
        } ~
        path(Segment) { id: String =>
          get {
            onSuccess(usersManager ? GetUser(id.toLong)) {
              case user: UserOverview => complete(StatusCodes.OK, user)
              case failure: String => complete(StatusCodes.NotFound, failure) //Todo: Failure (User NotFound)
            }
          } ~
          delete {
            onSuccess(usersManager ? RemoveUser(id.toLong)) {
              case user: User => complete(StatusCodes.OK, user)
              case _ => complete(StatusCodes.NotFound)
            }
          }
        }~
        pathPrefix(LongNumber / "view") { id =>
          get {
            onSuccess(usersManager ? GetViews(id)) {
              case views: List[View] => complete(StatusCodes.OK, views)
              case x: String => complete(StatusCodes.NotFound, x)
            }
          } ~
          post {
            parameter("itemId".as[Int]) { itemId =>
              onSuccess(usersManager ? PersistEvent(id, itemId, "view")) {
                case view: View => complete(StatusCodes.OK, view)
                case msg: String => complete(StatusCodes.BadRequest, msg) //Todo Failure (BadReq)
              }
            }
          }
        }~
        pathPrefix(LongNumber / "purchase") { id =>
          get {
            onSuccess(usersManager ? GetPurchases(id)) {
              case purchases: List[Purchase] => complete(StatusCodes.OK, purchases)
              case x: String => complete(StatusCodes.NotFound, x)
            }
          }~
          post {
            parameter("itemId".as[Int]) { itemId =>
              onSuccess(usersManager ? PersistEvent(id, itemId, "purchase")) {
                case purchase: Purchase => complete(StatusCodes.OK, purchase)
                case msg: String => complete(StatusCodes.BadRequest, msg) //Todo Failure (BadReq)
              }
            }
          }
        }~
        pathPrefix(LongNumber / "cart") { id=>
          get {
            onSuccess(usersManager ? GetCart(id)) {
              case carts: List[Cart] => complete(StatusCodes.OK, carts)
              case x: String => complete(StatusCodes.NotFound, x)
            }
          }~
          post {
            parameter("itemId".as[Int]) { itemId =>
              onSuccess(usersManager ? PersistEvent(id, itemId, "cart")) {
                case cart: Cart => complete(StatusCodes.OK, cart)
                case msg: String => complete(StatusCodes.BadRequest, msg) //Todo Failure (BadReq)
              }
            }
          }~
          post {
            onSuccess(usersManager ? BuyCart(id)) {
              case newPurchases: List[Purchase] => complete(StatusCodes.OK, newPurchases)
              case msg: String => complete(StatusCodes.BadRequest, msg) //Todo Failure
            }
          }
        }
      }
    }
  }
}
