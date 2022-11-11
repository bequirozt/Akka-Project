package com.applaudostudios.store.http

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.applaudostudios.store.domain.{DataJsonProtocol, Purchase}
import com.applaudostudios.store.domain.categories.CategoriesManager
import com.applaudostudios.store.domain.items.ItemsManager
import com.applaudostudios.store.domain.users.UsersManager
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpecLike

class PurchaseFuncSpec
  extends AnyFeatureSpecLike
    with GivenWhenThen
    with ScalatestRouteTest
    with DataJsonProtocol
    with SprayJsonSupport {

  info("As a client")
  info("I want to be able to handle the purchase information")
  info("So I can manage all the purchases of a user")

  val categoryManager: ActorRef = system.actorOf(CategoriesManager.props(), "Categories-Manager-01")
  val itemManager: ActorRef = system.actorOf(ItemsManager.props(categoryManager), "Items-Manager-01")
  val userManager: ActorRef = system.actorOf(UsersManager.props(itemManager, categoryManager), "Users-Manager-01")
  val routes: Route = Controller(categoryManager, itemManager, userManager).mainRoute

  Feature("Create a new purchase") {

    Scenario("Create a purchase of a item") {
      Given("a user, a POST request and a item")
      val itemId = 35
      val item = s"""{"brand":"UnItem","categoryId":0,"id":$itemId,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check(status == StatusCodes.OK)
      val userId = 39
      val user = s"""{"email":"jacinto@mail.com","id":$userId,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check(status == StatusCodes.OK)
      val request = Post(s"/ecommerce/user/$userId/purchase?itemId=$itemId")

      When("a client send a POST request to create a new purchase")
      request ~!> routes ~> check {

        Then("will response with a 200 code and the purchase info")
        assert(status == StatusCodes.OK)
        val entity = entityAs[Purchase]
        assert(entity.userId == userId)
        assert(entity.itemId == itemId)
      }
    }

    Scenario("Try to make a new purchase with a item not registered") {

      Given("a user and item id not registered")
      val itemId = 36
      val userId = 40
      val user = s"""{"email":"jacinto@mail.com","id":$userId,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check(status == StatusCodes.OK)
      val request = Post(s"/ecommerce/user/$userId/purchase?itemId=$itemId")

      When("a client send a POST request with the item not registered")
      request ~!> routes ~> check {

        Then("will response a 404 and a info message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "Item not found")
      }
    }

    Scenario("Try to make a purchase with a user not registered") {

      Given("a user id not registered, a POST request and a item")
      val itemId = 37
      val item = s"""{"brand":"UnItem","categoryId":0,"id":$itemId,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check(status == StatusCodes.OK)
      val userId = 41
      val request = Post(s"/ecommerce/user/$userId/purchase?itemId=$itemId")

      When("a client send a POST request to make a purchase")
      request ~!> routes ~> check {

        Then("will response with a 404 code and a 'User not Found' message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "User not Found")
      }
    }

    Scenario("Get a purchase information") {

      Given("a user, a cart and a GET request")
      val itemId = 38
      val item = s"""{"brand":"UnItem","categoryId":0,"id":$itemId,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check(status == StatusCodes.OK)
      val userId = 42
      val user = s"""{"email":"jacinto@mail.com","id":$userId,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check(status == StatusCodes.OK)
      Post(s"/ecommerce/user/$userId/purchase?itemId=$itemId") ~!> routes ~> check(status == StatusCodes.OK)
      val request = Get(s"/ecommerce/user/$userId/purchase")

      When("a client send a GET request to obtain the purchase info")
      request ~!> routes ~> check {

        Then("will response a 200 and the list of the items from the purchase")
        assert(status == StatusCodes.OK)
        val entity = entityAs[List[Purchase]]
        assert(entity.length == 1)
      }
    }

    Scenario("Try to get the purchase from a user not registered") {

      Given("a user id not registered and a GET request")
      val userId = 43
      val request = Get(s"/ecommerce/user/$userId/purchase")

      When("a client send a GET request")
      request ~!> routes ~> check {

        Then("will be response with a 404 and a information message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "User not Found")
      }
    }
  }
}
