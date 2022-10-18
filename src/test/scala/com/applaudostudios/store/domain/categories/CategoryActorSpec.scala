package com.applaudostudios.store.domain.categories

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpecLike

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}

import com.applaudostudios.store.domain.RetrieveInfo
import com.applaudostudios.store.domain.categories.CategoriesManager.UpdateCategory
import com.applaudostudios.store.domain.categories.CategoryActor.LoadCategory
import com.applaudostudios.store.domain.data.Category

class CategoryActorSpec
  extends TestKit(ActorSystem("CategoryActorSpec"))
    with AnyFeatureSpecLike
    with GivenWhenThen
    with ImplicitSender {

  info("As a external Actor")
  info("I want to be able to handle the category information")
  info("So I can get that category latter")

  Feature("Handle category actor") {

    val categoryManager = system.actorOf(CategoriesManager.props(), "Categories-Manager-01")

    Scenario("External actor request a new category") {

      Given("a category actor and a category")
      val categoryId = 1
      val categoryActor = system.actorOf(CategoryActor.props(categoryId, categoryManager))
      val category = Category(categoryId, "c-000001")

      When("a LoadCategory message is send with a new category object to category actor")
      categoryActor ! LoadCategory(category)

      Then("the category actor should save the new category and response the category information")
      expectMsg(category)
    }

    Scenario("External actor request the category information") {

      Given("a category actor whit a category information")
      val categoryId = 2
      val categoryActor = system.actorOf(CategoryActor.props(categoryId, categoryManager))
      val category = Category(categoryId, "c-000002")
      categoryActor ! LoadCategory(category)

      When("a RetrieveInfo request is send")
      categoryActor ! RetrieveInfo

      Then("the category actor should response the category information")
      expectMsg(category)
      expectMsg(category)
    }

    Scenario("External actor request the updated of a category") {

      Given("a category actor whit a category information and a category updated")
      val categoryId = 3
      val categoryActor = system.actorOf(CategoryActor.props(categoryId, categoryManager))
      val category = Category(categoryId, "c-000003")
      val categoryUpdated = category.copy(code = "c-000004")
      categoryActor ! LoadCategory(category)

      When("a UpdateCategory request is send")
      categoryActor ! UpdateCategory(categoryUpdated)

      Then("the category actor should response the updated information")
      expectMsg(category)
      expectMsg(categoryUpdated)
    }

    Scenario("External actor request the updated of the ID of a existing category") {

      Given("a category actor whit a category information and a category update with a new ID")
      val categoryId = 4
      val categoryActor = system.actorOf(CategoryActor.props(categoryId, categoryManager))
      val category = Category(categoryId, "c-000004")
      val categoryUpdated = category.copy(id = 5)
      categoryActor ! LoadCategory(category)

      When("a UpdateCategory request is send with a new ID")
      categoryActor ! UpdateCategory(categoryUpdated)

      Then("the category actor should response the updated information with the original ID")
      expectMsg(category)
      expectMsg(category)
    }
  }
}
