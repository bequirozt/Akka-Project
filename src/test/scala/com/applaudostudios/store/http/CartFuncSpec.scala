package com.applaudostudios.store.http

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.applaudostudios.store.domain.{Cart, DataJsonProtocol}
import com.applaudostudios.store.domain.categories.CategoriesManager
import com.applaudostudios.store.domain.items.ItemsManager
import com.applaudostudios.store.domain.users.UsersManager
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpecLike

class CartFuncSpec
  extends AnyFeatureSpecLike
    with GivenWhenThen
    with ScalatestRouteTest
    with DataJsonProtocol
    with SprayJsonSupport {

  info("As a client")
  info("I want to be able to handle the cart of a user")
  info("So I can manage the cart information")

  val categoryManager: ActorRef = system.actorOf(CategoriesManager.props(), "Categories-Manager-01")
  val itemManager: ActorRef = system.actorOf(ItemsManager.props(categoryManager), "Items-Manager-01")
  val userManager: ActorRef = system.actorOf(UsersManager.props(itemManager, categoryManager), "Users-Manager-01")
  val routes: Route = Controller(categoryManager, itemManager, userManager).mainRoute

  Feature("Create a cart") {

    Scenario("Create a new cart") {

      Given("a user, a POST request and a item")
      val itemId = 30
      val item = s"""{"brand":"UnItem","categoryId":0,"id":$itemId,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check (status == StatusCodes.OK)
      val userId = 33
      val user = s"""{"email":"jacinto@mail.com","id":$userId,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check (status == StatusCodes.OK)
      val request = Post(s"/ecommerce/user/$userId/cart?itemId=$itemId")

      When("a client send a POST request to create a new cart")
      request ~!> routes ~> check {

        Then("will response with a 200 code and the cart info")
        assert(status == StatusCodes.OK)
        val entity = entityAs[Cart]
        assert(entity.userId == userId)
        assert(entity.itemId == itemId)
      }
    }

    Scenario("Try to create a new cart with a item not registered") {

      Given("a user and item id not registered")
      val itemId = 31
      val userId = 34
      val user = s"""{"email":"jacinto@mail.com","id":$userId,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check(status == StatusCodes.OK)
      val request = Post(s"/ecommerce/user/$userId/cart?itemId=$itemId")

      When("a client send a POST request with the item not registered")
      request ~!> routes ~> check {

        Then("will response a 404 and a info message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "Item not found")
      }
    }

    Scenario("Try to create a cart with a user not registered") {

      Given("a user id not registered, a POST request and a item")
      val itemId = 32
      val item = s"""{"brand":"UnItem","categoryId":0,"id":$itemId,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check(status == StatusCodes.OK)
      val userId = 35
      val request = Post(s"/ecommerce/user/$userId/cart?itemId=$itemId")

      When("a client send a POST request to create a new cart")
      request ~!> routes ~> check {

        Then("will response with a 404 code and a 'User not Found' message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "User not Found")
      }
    }

    Scenario("Buy a cart with the items") {

      Given("a user, a cart with items and a POST request")
      val itemId = 33
      val item = s"""{"brand":"UnItem","categoryId":0,"id":$itemId,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check(status == StatusCodes.OK)
      val userId = 36
      val user = s"""{"email":"jacinto@mail.com","id":$userId,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check(status == StatusCodes.OK)
      Post(s"/ecommerce/user/$userId/cart?itemId=$itemId") ~!> routes ~> check (status == StatusCodes.OK)
      val request = Post(s"/ecommerce/user/$userId/cart")

      When("a client send a POST request to buy a cart")
      request ~!> routes ~> check {

        Then("will response with a 200 and the list of items bought")
        assert(status == StatusCodes.OK)
        val entity = entityAs[List[Cart]]
        assert(entity.length == 1)
      }
    }

    Scenario("Try to buy a cart with a user not registered") {

      Given("a user id not registered and a POST request")
      val userId = 37
      val request = Post(s"/ecommerce/user/$userId/cart")

      When("a client send a POST request with the user id not registered")
      request ~!> routes ~> check {

        Then("will response with a 404 and a 'User not Found' message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "User not Found")
      }
    }

    Scenario("Get a cart information") {

      Given("a user, a cart and a GET request")
      val itemId = 34
      val item = s"""{"brand":"UnItem","categoryId":0,"id":$itemId,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check(status == StatusCodes.OK)
      val userId = 38
      val user = s"""{"email":"jacinto@mail.com","id":$userId,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check(status == StatusCodes.OK)
      Post(s"/ecommerce/user/$userId/cart?itemId=$itemId") ~!> routes ~> check(status == StatusCodes.OK)
      val request = Get(s"/ecommerce/user/$userId/cart")

      When("a client send a GET request to obtain the cart info")
      request ~!> routes ~> check {

        Then("will response a 200 and the list of the items from the cart")
        assert(status == StatusCodes.OK)
        val entity = entityAs[List[Cart]]
        assert(entity.length == 1)
      }
    }
  }

}
