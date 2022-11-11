package com.applaudostudios.store.http

import akka.actor.ActorRef
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.applaudostudios.store.domain.{DataJsonProtocol, View}
import com.applaudostudios.store.domain.categories.CategoriesManager
import com.applaudostudios.store.domain.items.ItemsManager
import com.applaudostudios.store.domain.users.UsersManager
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpecLike

class ViewFuncSpec
  extends AnyFeatureSpecLike
    with GivenWhenThen
    with ScalatestRouteTest
    with DataJsonProtocol
    with SprayJsonSupport {

  info("As a client")
  info("I want to be able to handle the items viewed for a user information")
  info("So I can manage the views information")

  val categoryManager: ActorRef = system.actorOf(CategoriesManager.props(), "Categories-Manager-01")
  val itemManager: ActorRef = system.actorOf(ItemsManager.props(categoryManager), "Items-Manager-01")
  val userManager: ActorRef = system.actorOf(UsersManager.props(itemManager, categoryManager), "Users-Manager-01")
  val routes: Route = Controller(categoryManager, itemManager, userManager).mainRoute

  Feature("Create a new view item of a user") {

    Scenario("Add a new view event to an user") {

      Given("a item, a user and a POST request for add a new event view")
      val item = """{"brand":"UnItem","categoryId":0,"id":24,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check {status == StatusCodes.OK}
      val user = """{"email":"jacinto@mail.com","id":28,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check {status == StatusCodes.OK}
      val request = Post("/ecommerce/user/28/view?itemId=24")

      When("a client send the POST request to add a new event to user")
      request ~!> routes ~> check {

        Then("will response 200 status")
        assert(status == StatusCodes.OK)
        val view = entityAs[View]
        assert(view.userId == 28)
        assert(view.itemId == 24)
      }
    }

    Scenario("Add a new view event with an item that has a category to an user") {

      Given("a category, a item with the category associated, a user and a POST request for add a new event view")
      val categoryId = 50
      val itemId = 25
      val userId = 29
      val category = s"""{"code":"electronica.cellphone.Iphone","id":$categoryId}"""
      Post("/ecommerce/category").withEntity(ContentTypes.`application/json`, category) ~!> routes ~> check {status == StatusCodes.OK}
      val item = s"""{"brand":"UnItem","categoryId":$categoryId,"id":$itemId,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check {status == StatusCodes.OK}
      val user = s"""{"email":"jacinto@mail.com","id":$userId,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check {status == StatusCodes.OK}
      val request = Post(s"/ecommerce/user/$userId/view?itemId=$itemId")

      When("a client send the POST request to add a new event to user")
      request ~!> routes ~> check {

        Then("will response 200 status")
        assert(status == StatusCodes.OK)
        val view = entityAs[View]
        assert(view.userId == userId)
        assert(view.itemId == itemId)
        assert(view.categoryId == categoryId)
      }
    }

    Scenario("Add a view to a user not registered") {

      Given("a item and a user id that doesn't exist")
      val item = """{"brand":"UnItem","categoryId":0,"id":26,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check {
        status == StatusCodes.OK
      }
      val request = Post("/ecommerce/user/100100/view?itemId=26")

      When("a client send the POST request to add a new event to an user not registered")
      request ~!> routes ~> check {

        Then("will response a 404 and a 'User not Found' message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "User not Found")
      }
    }

    Scenario("Add a view to a user with an item not registered") {

      Given("a item id not registered, a user and a request")
      val user = """{"email":"jacinto@mail.com","id":30,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check {
        status == StatusCodes.OK
      }
      val request = Post("/ecommerce/user/30/view?itemId=27")

      When("a client send a POST request to add a item that doesn't exist to the user")
      request ~!> routes ~> check {

        Then("will response a 400 and a 'Item not found' message")
        assert(status == StatusCodes.BadRequest)
        assert(entityAs[String] == "Item not found")
      }
    }
  }

  Feature("Get the views information") {

    Scenario("Get the views from a user") {

      Given("a user with some items and a GET request")
      val item = """{"brand":"UnItem","categoryId":0,"id":28,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check {
        status == StatusCodes.OK
      }
      val user = """{"email":"jacinto@mail.com","id":31,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check {
        status == StatusCodes.OK
      }
      Post("/ecommerce/user/31/view?itemId=28") ~!> routes ~> check (status == StatusCodes.OK)
      val request = Get("/ecommerce/user/31/view")

      When("a client send a GET request")
      request ~!> routes ~> check {

        Then("will be response with a 200 and the list of views")
        assert(status == StatusCodes.OK)
        val response = entityAs[List[View]]
        assert(response.length == 1)
      }
    }

    Scenario("Get the views from a user not registered") {

      Given("a user id not registered and a GET request")
      val userId = 32
      val request = Get(s"/ecommerce/user/$userId/view")

      When("a client send a GET request")
      request ~!> routes ~> check {

        Then("will be response with a 404 and a information message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "User not Found")
      }
    }
  }
}
