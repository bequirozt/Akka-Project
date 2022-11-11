package com.applaudostudios.store.domain.categories

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Failure

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.applaudostudios.store.domain.categories.CategoriesManager.{CategoryAlreadyDeletedException, CreateCategory, GetCategories, GetCategory, RemoveCategory, UpdateCategory, VerifyCategory}
import com.applaudostudios.store.domain.data.Category
import com.applaudostudios.store.domain.items.ItemsManager
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpecLike

class CategoryManagerSpec
  extends TestKit(ActorSystem("CategoryManagerSpec"))
    with AnyFeatureSpecLike
    with GivenWhenThen
    with ImplicitSender {

  info("As a external actor")
  info("I want to be able to handle the category manager")
  info("So I can get the categories latter")

  Feature("Handle categories manager") {

    Scenario("External actor request the creation of a category") {

      Given("a category manager actor whit a category information")
      val categoryId = 1
      val categoryManagerActor = system.actorOf(CategoriesManager.props())
      val category = Category(categoryId, "c-000001")

      When("a CreateCategory request is send")
      categoryManagerActor ! CreateCategory(category)

      Then("the category actor should response the category information")
      expectMsg(category)
    }

    Scenario("External actor request the information of a category") {

      Given("a category manager actor whit a category information and a new category")
      val categoryId = 2
      val categoryManagerActor = system.actorOf(CategoriesManager.props())
      val category = Category(categoryId, "c-000002")
      categoryManagerActor ! CreateCategory(category)

      When("a GetCategory request is send")
      categoryManagerActor ! GetCategory(categoryId)

      Then("the category actor should response the category information")
      expectMsg(category)
      expectMsg(category)
    }

    Scenario("External actor request the update of a category") {

      Given("a category manager actor whit a category information and a new category")
      val categoryId = 3
      val categoryManagerActor = system.actorOf(CategoriesManager.props())
      val category = Category(categoryId, "c-000003")
      val categoryUpdate = category.copy(code = "c-000004")
      categoryManagerActor ! CreateCategory(category)

      When("a UpdateCategory request is send")
      categoryManagerActor ! UpdateCategory(categoryUpdate)

      Then("the category actor should response the category information updated")
      expectMsg(category)
      expectMsg(categoryUpdate)
    }

    Scenario("External actor request the elimination of a category") {

      Given("a category manager actor whit a category information and a new category")
      val categoryId = 4
      val categoryManagerActor = system.actorOf(CategoriesManager.props())
      val itemsManagerActor = system.actorOf(ItemsManager.props(categoryManagerActor))
      val category = Category(categoryId, "c-000004")
      categoryManagerActor ! CreateCategory(category)

      When("a RemoveCategory request is send")
      categoryManagerActor ! RemoveCategory(categoryId, itemsManagerActor)

      Then("the category actor should response the category information")
      expectMsg(category)
      expectMsg(category)
    }

    Scenario("External actor request the elimination of a category already deleted") {

      Given("a category manager actor whit a category information and a new category")
      val categoryId = 5
      val categoryManagerActor = system.actorOf(CategoriesManager.props())
      val itemsManagerActor = system.actorOf(ItemsManager.props(categoryManagerActor))
      val category = Category(categoryId, "c-000005")
      categoryManagerActor ! CreateCategory(category)
      expectMsg(category)

      When("a RemoveCategory request is send two times")
      categoryManagerActor ! RemoveCategory(categoryId, itemsManagerActor)
      expectMsg(category)
      categoryManagerActor ! RemoveCategory(categoryId, itemsManagerActor)

      Then("the category actor should response with a exception for the second request")
      expectMsg(Failure(CategoryAlreadyDeletedException("Category already deleted")))
    }

    Scenario("External actor request the existence of a category") {

      Given("a category manager actor whit a category information and a new category")
      val categoryId = 6
      val categoryManagerActor = system.actorOf(CategoriesManager.props())
      val category = Category(categoryId, "c-000006")
      categoryManagerActor ! CreateCategory(category)
      expectMsg(category)

      When("a VerifyCategory request is send")
      categoryManagerActor ! VerifyCategory(categoryId, categoryManagerActor)

      Then("the category actor should response true")
      expectMsg((true, categoryManagerActor))
    }

    Scenario("External actor request the existence of a not existing category") {

      Given("a category manager actor whit a category information and a new category")
      val categoryId = 7
      val categoryManagerActor = system.actorOf(CategoriesManager.props())

      When("a VerifyCategory request is send")
      categoryManagerActor ! VerifyCategory(categoryId, categoryManagerActor)

      Then("the category actor should response false")
      expectMsg((false, categoryManagerActor))
    }

    Scenario("External actor request the existence of a category already deleted") {

      Given("a category manager actor whit a category information and a new category")
      val categoryId = 100
      val categoryManagerActor = system.actorOf(CategoriesManager.props())
      val itemsManagerActor = system.actorOf(ItemsManager.props(categoryManagerActor))
      val category = Category(categoryId, "c-00000100")
      categoryManagerActor ! CreateCategory(category)
      expectMsg(category)
      categoryManagerActor ! RemoveCategory(categoryId, itemsManagerActor)
      expectMsg(category)

      When("a VerifyCategory request is send")
      categoryManagerActor ! VerifyCategory(categoryId, categoryManagerActor)

      Then("the category actor should response false")
      expectMsg((false, categoryManagerActor))
    }

    Scenario("External actor request the information of all categories") {

      Given("a two category manager actor whit a two category information")
      val categoryId = 7
      val categoryId2 = 8
      val categoryManagerActor = system.actorOf(CategoriesManager.props())
      val category = Category(categoryId, "c-000001")
      val category2 = Category(categoryId2, "c-000002")
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher
      categoryManagerActor ! CreateCategory(category)
      expectMsg(category)
      categoryManagerActor ! CreateCategory(category2)
      expectMsg(category2)

      When("a two GetCategories request is send")
      categoryManagerActor ! GetCategories

      Then("the category actor should response a list with the categories information")
      expectMsg(Future(List(category, category2)))
    }
  }

}
