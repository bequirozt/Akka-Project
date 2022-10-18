package com.applaudostudios.store.domain.categories

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.PersistentActor
import com.applaudostudios.store.domain.RetrieveInfo
import com.applaudostudios.store.domain.categories.CategoriesManager.UpdateCategory
import com.applaudostudios.store.domain.data.Category

object CategoryActor {
  def props(id:BigInt, m:ActorRef):Props = Props(CategoryActor(id, m))
  //Commands

  //Events
  case class LoadCategory(category: Category)
  case class CategoryCreated(cat:Category)
  case class CategoryUpdated(cat:Category)

  case class CategoryState(id:BigInt, code:String ="")
}

case class CategoryActor(id:BigInt,manager:ActorRef) extends PersistentActor with ActorLogging{
  import CategoryActor._
  override def persistenceId: String = s"Category-$id"

  var state: CategoryState = CategoryState(id)

  def loadInfo(cat: Category): Unit = {
    state = state.copy(code = cat.code)
  }

  override def receiveCommand: Receive = {
    case LoadCategory(cat) =>
      persist(CategoryCreated(cat)) { ev =>
        loadInfo(ev.cat)
        sender ! Category(state.id, state.code)
      }
    case RetrieveInfo =>
      sender() ! Category(state.id, state.code)
    case UpdateCategory(cat) =>
      persist(CategoryUpdated(cat)) { evt =>
        loadInfo(evt.cat)
        sender ! Category(state.id, state.code)
      }
  }

  override def receiveRecover: Receive = {
    case CategoryCreated(cat) =>
      loadInfo(cat)
    case CategoryUpdated(cat) =>
      loadInfo(cat)
  }

}
