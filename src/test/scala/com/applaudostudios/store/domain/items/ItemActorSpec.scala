package com.applaudostudios.store.domain.items

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.applaudostudios.store.domain.RetrieveInfo
import com.applaudostudios.store.domain.categories.CategoriesManager
import com.applaudostudios.store.domain.data.Item
import com.applaudostudios.store.domain.items.ItemActor.{DisableCategory, LoadItem, UpdateItem}
import com.applaudostudios.store.domain.items.ItemsManager.GetItem
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpecLike

class ItemActorSpec
  extends TestKit(ActorSystem("ItemActorSpec"))
    with AnyFeatureSpecLike
    with GivenWhenThen
    with ImplicitSender {

  info("As a external Actor")
  info("I want to be able to handle the item information")
  info("So I can get that item latter")

  Feature("Handle item actor") {

    val categoryManager = system.actorOf(CategoriesManager.props(), "Categories-Manager-01")
    val itemManager = system.actorOf(ItemsManager.props(categoryManager), "Items-Manager-01")

    Scenario("External actor request a new item with default price") {

      Given("a item actor and an item with default price")
      val itemId = 1
      val categoryId = 1
      val item = Item(itemId, "b-000001", categoryId)
      val itemActor = system.actorOf(ItemActor.props(itemId, itemManager))

      When("a LoadItem request is send with a new item")
      itemActor ! LoadItem(item)

      Then("the item actor should save the new item and response with the item information")
      expectMsg(item)
    }

    Scenario("External actor request a new item") {

      Given("a item actor and an item")
      val itemId = 2
      val categoryId = 2
      val item = Item(itemId, "b-000002", categoryId, 10.0)
      val itemActor = system.actorOf(ItemActor.props(itemId, itemManager))

      When("a LoadItem request is send with a new item")
      itemActor ! LoadItem(item)

      Then("the item actor should save the new item and response with the item information")
      expectMsg(item)
    }

    Scenario("External actor request the item information") {

      Given("a item actor and an item")
      val itemId = 3
      val categoryId = 3
      val item = Item(itemId, "b-000003", categoryId, 10.0)
      val itemActor = system.actorOf(ItemActor.props(itemId, itemManager))
      itemActor ! LoadItem(item)
      expectMsg(item)

      When("a RetrieveInfo request is send")
      itemActor ! RetrieveInfo

      Then("the item actor should response the item information")
      expectMsg(item)
    }

    Scenario("External actor request the update of the item information") {

      Given("a item actor and an item")
      val itemId = 4
      val categoryId = 4
      val item = Item(itemId, "b-000004", categoryId, 10.0)
      val itemUpdate = item.copy(brand = "b-000004", price = 20.0)
      val itemActor = system.actorOf(ItemActor.props(itemId, itemManager))
      itemActor ! LoadItem(item)
      expectMsg(item)

      When("a UpdateItem request is send")
      itemActor ! UpdateItem(itemUpdate)

      Then("the item actor should response the update item information")
      expectMsg(itemUpdate)
    }

    Scenario("External actor request the disable of a category from the item") {

      Given("a item actor and an item")
      val itemId = 5
      val categoryId = 5
      val item = Item(itemId, "b-000005", categoryId, 10.0)
      val itemUpdated = item.copy(categoryId = 0)
      val itemActor = system.actorOf(ItemActor.props(itemId, itemManager))
      itemActor ! LoadItem(item)
      expectMsg(item)

      When("a DisableCategory request is send")
      itemActor ! DisableCategory(categoryId)
      itemActor ! RetrieveInfo

      Then("the item actor should response the item with the category ID updated to 0")
      expectMsg(itemUpdated)
    }
  }
}
