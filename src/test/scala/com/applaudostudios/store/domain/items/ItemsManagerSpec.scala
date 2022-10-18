package com.applaudostudios.store.domain.items

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.applaudostudios.store.domain.categories.CategoriesManager
import com.applaudostudios.store.domain.data.Item
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpecLike

class ItemsManagerSpec
  extends TestKit(ActorSystem("ItemsManagerSpec"))
    with AnyFeatureSpecLike
    with GivenWhenThen
    with ImplicitSender {

  info("As a external actor")
  info("I want to be able to handle the items manager")
  info("So I can get the items latter")

  Feature("Handle items manager") {

    val categoryManager = system.actorOf(CategoriesManager.props(), "Categories-Manager-01")

    Scenario("External actor request the creation of a item") {

      Given("External actor request the creation of a item")
      val itemId = 1
      val categoryId = 1
      val item = Item(itemId, "b-000001", categoryId)
      val itemsManager = system.actorOf(ItemsManager.props(categoryManager))

      When("a item manager actor whit a item information")
      Then("")
    }
  }
}
