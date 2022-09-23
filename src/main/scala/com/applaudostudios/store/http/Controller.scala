package com.applaudostudios.store.http

import akka.actor.ActorRef
import com.applaudostudios.store.domain._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

import scala.util.{Failure, Success}
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.ask
import com.applaudostudios.store.domain.categories.CategoriesManager.{CategoryAlreadyDeletedException, CategoryAlreadyRegisteredException, CategoryAlreadyUpToDateException, CategoryNotFoundException, CreateCategory, GetCategories, GetCategory, RemoveCategory, UpdateCategory, VerifyCategory}
import com.applaudostudios.store.domain.data.{Category, InputItem, Item, User}
import com.applaudostudios.store.domain.items.ItemActor.UpdateItem
import com.applaudostudios.store.domain.items.ItemsManager.{CreateItem, GetItem, GetItems, ItemAlreadyDeletedException, ItemCreationException, ItemNotFoundException, ItemUpdateException, RemoveItem, SecureCreate}
import com.applaudostudios.store.domain.users.UserActor.{PurchaseCart, UserOverview}
import com.applaudostudios.store.domain.users.UsersManager.{BuyCart, CreateUser, GetCart, GetPurchases, GetUser, GetUsers, GetViews, PersistEvent, RemoveUser, UpdateUser, UserAlreadyDeletedException, UserAlreadyRegisteredException, UserNotFoundException}

import scala.concurrent.Future


case class Controller(catManager:ActorRef,itemsManager:ActorRef,usersManager:ActorRef)
  extends DataJsonProtocol
    with SprayJsonSupport {

  val myExceptionHandler: ExceptionHandler = ExceptionHandler {
    case ItemCreationException(msg) => complete(StatusCodes.BadRequest, msg)
    case ItemUpdateException(msg) => complete(StatusCodes.BadRequest, msg)
    case ItemAlreadyDeletedException(msg) => complete(StatusCodes.NotFound, msg)
    case ItemNotFoundException(msg) => complete(StatusCodes.NotFound, msg)

    case CategoryAlreadyRegisteredException(msg) => complete(StatusCodes.BadRequest, msg)
    case CategoryAlreadyUpToDateException(msg) => complete(StatusCodes.BadRequest, msg)
    case CategoryAlreadyDeletedException(msg) => complete(StatusCodes.NotFound, msg)
    case CategoryNotFoundException(msg) => complete(StatusCodes.NotFound, msg)

    case UserAlreadyRegisteredException(msg) =>complete(StatusCodes.BadRequest, msg)
    case UserNotFoundException(msg) =>complete(StatusCodes.NotFound, msg)
    case UserAlreadyDeletedException(msg) => complete(StatusCodes.NotFound, msg)
  }



  val mainRoute: Route = {
    pathPrefix("ecommerce") {
      handleExceptions(myExceptionHandler) {
        pathPrefix("item") {
          pathEndOrSingleSlash {
            get {
              onSuccess(itemsManager ? GetItems) {
                case seq: Future[Seq[Item]] =>
                  onComplete(seq) {
                    case Success(items) => complete(items)
                  }
              }
            } ~
            post {
              entity(as[Item]) { item =>
                onSuccess(itemsManager ? CreateItem(item)){
                  case el: Item => complete(StatusCodes.OK, el)
                  case Failure(x) => throw x
                }
              }
            }~
            post {
              entity(as[InputItem]) { inputItem =>
                onSuccess(itemsManager ? CreateItem(Item(inputItem.id, inputItem.brand, BigInt("0"), inputItem.price))) {
                  case item: Item => complete(StatusCodes.OK, item)
                  case Failure(x) => throw x
                }
              }
            }~
            (put | patch) {
              entity(as[Item]) { item =>
                onSuccess(itemsManager ? UpdateItem(item)) {
                  case updatedItem: Item => complete(StatusCodes.OK, updatedItem)
                  case Failure(x) => throw x
                }
              }
            }
          } ~
          path(Segment) { id: String =>
            get {
              onSuccess(itemsManager ? GetItem(id.toInt)) {
                case item: Item => complete(StatusCodes.OK, item)
                case Failure(x) => throw x
              }
            } ~
            delete {
              onSuccess(itemsManager ? RemoveItem(id.toInt)) {
                case item: Item => complete(StatusCodes.OK, item)
                case Failure(x) => throw x
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
                  }
              }
            } ~
            post {
              entity(as[Category]) { inputCat =>
                onSuccess(catManager ? CreateCategory(inputCat)) {
                  case category: Category => complete(StatusCodes.OK, category)
                  case Failure(x) => throw x
                }
              }
            }~
            (put | patch) {
              entity(as[Category]) { catInput =>
                onSuccess(catManager ? UpdateCategory(catInput)) {
                  case updatedCategory: Category => complete(updatedCategory)
                  case Failure(x) => throw x
                }
              }
            }
          } ~
          path(Segment) { id: String =>
            get {
              onSuccess(catManager ? GetCategory(BigInt(id))) {
                case cat: Category => complete(StatusCodes.OK, cat)
                case Failure(x) => throw x
              }
            } ~
            delete {
              onSuccess(catManager ? RemoveCategory(BigInt(id),itemsManager)) {
                case cat: Category => complete(StatusCodes.OK, cat)
                case Failure(x) => throw x
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
                  case Failure(exception) => throw exception
                }
              }
            }~
            (put | patch) {
              entity(as[User]) { userInput =>
                onSuccess(usersManager ? UpdateUser(userInput)) {
                  case updatedUser: UserOverview => complete(updatedUser)
                  case Failure(exception) => throw exception
                }
              }
            }
          } ~
          path(Segment) { id: String =>
            get {
              onSuccess(usersManager ? GetUser(id.toLong)) {
                case user: UserOverview => complete(StatusCodes.OK, user)
                case Failure(exception) => throw exception
              }
            } ~
            delete {
              onSuccess(usersManager ? RemoveUser(id.toLong)) {
                case user: UserOverview => complete(StatusCodes.OK, user)
                case Failure(exception) => throw exception
              }
            }
          }~
          pathPrefix(LongNumber / "view") { id =>
            get {
              onSuccess(usersManager ? GetViews(id)) {
                case views: List[View] => complete(StatusCodes.OK, views)
                case Failure(exception) => throw exception
              }
            } ~
            post {
              parameter("itemId".as[Int]) { itemId =>
                onSuccess(usersManager ? PersistEvent(id, itemId, "view")) {
                  case view: View => complete(StatusCodes.OK, view)
                  case Failure(exception) => throw exception
                }
              }
            }
          }~
          pathPrefix(LongNumber / "purchase") { id =>
            get {
              onSuccess(usersManager ? GetPurchases(id)) {
                case purchases: List[Purchase] => complete(StatusCodes.OK, purchases)
                case Failure(exception) => throw exception
              }
            }~
            post {
              parameter("itemId".as[Int]) { itemId =>
                onSuccess(usersManager ? PersistEvent(id, itemId, "purchase")) {
                  case purchase: Purchase => complete(StatusCodes.OK, purchase)
                  case Failure(exception) => throw exception
                }
              }
            }
          }~
          pathPrefix(LongNumber / "cart") { id=>
            get {
              onSuccess(usersManager ? GetCart(id)) {
                case carts: List[Cart] => complete(StatusCodes.OK, carts)
                case Failure(exception) => throw exception
              }
            }~
            post {
              parameter("itemId".as[Int]) { itemId =>
                onSuccess(usersManager ? PersistEvent(id, itemId, "cart")) {
                  case cart: Cart => complete(StatusCodes.OK, cart)
                  case Failure(exception) => throw exception
                }
              }
            }~
            post {
              onSuccess(usersManager ? BuyCart(id)) {
                case newPurchases: List[Purchase] => complete(StatusCodes.OK, newPurchases)
                case Failure(exception) => throw exception
              }
            }
          }
        }
      }
    }
  }
}
