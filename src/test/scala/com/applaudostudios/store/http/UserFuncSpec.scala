package com.applaudostudios.store.http

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.applaudostudios.store.domain.categories.CategoriesManager
import com.applaudostudios.store.domain.items.ItemsManager
import com.applaudostudios.store.domain.users.UsersManager
import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpecLike

class UserFuncSpec
  extends AnyFeatureSpecLike
    with GivenWhenThen
    with ScalatestRouteTest {

  info("As a client")
  info("I want to be able to handle the users information")
  info("So I can manage all the users")

  val categoryManager: ActorRef = system.actorOf(CategoriesManager.props(), "Categories-Manager-01")
  val itemManager: ActorRef = system.actorOf(ItemsManager.props(categoryManager), "Items-Manager-01")
  val userManager: ActorRef = system.actorOf(UsersManager.props(itemManager, categoryManager), "Users-Manager-01")
  val routes: Route = Controller(categoryManager, itemManager, userManager).mainRoute

  Feature("Create a new user") {

    Scenario("A client send a request to create a new user") {

      Given("a user information and a POST request")
      val user = """{"email":"jacinto@mail.com","id":1,"name":"Jacinto"}"""
      val request = Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user)

      When("a request for create a new user is send to the server")
      request ~!> routes ~> check {

       Then("will be create a new user and will response with the user info and a 200 status")
       assert(status == StatusCodes.OK)
       assert(entityAs[String] == user)
      }
    }

    Scenario("A client send a request to create a user already created") {

      Given("a user and a POST request")
      val user = """{"email":"jacinto@mail.com","id":2,"name":"Jacinto"}"""
      val request = Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user)
      request ~!> routes ~> check {assert(status == StatusCodes.OK)}

      When("a request for create a already created user is send to the server")
      request ~!> routes ~> check {

        Then("will response with a 200 and a User already registered with id: 2 message")
        assert(status == StatusCodes.BadRequest)
        assert(entityAs[String] == "User already registered with id: 2")
      }
    }
  }

  Feature("Get the user information") {

    Scenario("A client send a request to get the information of ALL the users") {

      Given("a users and a GET request for ALL the users")
      val user = """{"email":"jacinto@mail.com","id":3,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Get("/ecommerce/user")

      When("a GET request for ALL the users is send")
      request ~!> routes ~> check {

        Then("will response with a 200 status")
        assert(status == StatusCodes.OK)
      }
    }

    Scenario("A client send a request to get a user") {

      Given("a user and a GET request with the user id")
      val user = """{"email":"jacinto@mail.com","id":4,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Get("/ecommerce/user/4")

      When("a GET request to an specific id is send")
      request ~!> routes ~> check {

        Then("will response with a 200 and the user information")
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == """{"totalCarts":0,"totalPurchases":0,"totalViews":0,"user":{"email":"jacinto@mail.com","id":4,"name":"Jacinto"}}""")
      }
    }

    Scenario("A client send a request to get a user doesn't registered") {

      Given("a GET request with an user id not registered")
      val request = Get("/ecommerce/user/5")

      When("a GET request with the user id that is not registered")
      request ~!> routes ~> check {

        Then("will response with a 404 and a 'User not Found' message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "User not Found")
      }
    }
  }

  Feature("Update a user") {

    Scenario("A client send a request to update a user") {

      Given("a user and a PUT request")
      val user = """{"email":"jacinto@mail.com","id":6,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val userUpdate = """{"email":"estrada@mail.com","id":6,"name":"Estrada"}"""
      val request = Put("/ecommerce/user").withEntity(ContentTypes.`application/json`, userUpdate)

      When("a PUT request with the user update is send")
      request ~!> routes ~> check {

        Then("will response with a 200 and the user updated information")
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == """{"totalCarts":0,"totalPurchases":0,"totalViews":0,"user":{"email":"estrada@mail.com","id":6,"name":"Estrada"}}""")
      }
    }

    Scenario("A client send a request to update a user doesn't registered") {

      Given("a PUT request with an user id not registered")
      val user = """{"email":"jacinto@mail.com","id":1000,"name":"Jacinto"}"""
      val request = Put("/ecommerce/user").withEntity(ContentTypes.`application/json`, user)

      When("a PUT request with the user id that is not registered")
      request ~!> routes ~> check {

        Then("will response with a 404 and a 'User not Found' message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "User not Found")
      }
    }
  }

  Feature("Delete a user") {

    Scenario("A client send a request to delete a user") {

      Given("a user and a request for delete")
      val user = """{"email":"jacinto@mail.com","id":7,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Delete("/ecommerce/user/7")

      When("the request to delete the user is send")
      request ~!> routes ~> check {

        Then("A status 200 and the user deleted are response")
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == """{"totalCarts":0,"totalPurchases":0,"totalViews":0,"user":{"email":"jacinto@mail.com","id":7,"name":"Jacinto"}}""")
      }
    }

    Scenario("A client send a request to delete a user that doesn't exist") {

      Given("a request for delete a user that does not exist")
      val request = Delete("/ecommerce/user/1001")

      When("the request is send")
      request ~!> routes ~> check {

        Then("A status 404 and a 'User not Found' message are response")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "User not Found")
      }
    }

    Scenario("A client send a request to delete a user already deleted") {

      Given("a user deleted and a request for delete the same user")
      val user = """{"email":"jacinto@mail.com","id":8,"name":"Jacinto"}"""
      Post("/ecommerce/user").withEntity(ContentTypes.`application/json`, user) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Delete("/ecommerce/user/8")
      request ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }

      When("the request to delete a user already deleted is send")
      request ~!> routes ~> check {

        Then("a status 404 and a 'User already deleted' message are response")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "User already deleted")
      }
    }
  }
}
