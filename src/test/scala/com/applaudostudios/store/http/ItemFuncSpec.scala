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

class ItemFuncSpec
  extends AnyFeatureSpecLike
    with GivenWhenThen
    with ScalatestRouteTest {

  info("As a client")
  info("I want to be able to handle the items information")
  info("So I can manage all the items")

  val categoryManager: ActorRef = system.actorOf(CategoriesManager.props(), "Categories-Manager-01")
  val itemManager: ActorRef = system.actorOf(ItemsManager.props(categoryManager), "Items-Manager-01")
  val userManager: ActorRef = system.actorOf(UsersManager.props(itemManager, categoryManager), "Users-Manager-01")
  val routes: Route = Controller(categoryManager, itemManager, userManager).mainRoute

  Feature("Create a new item") {

    Scenario("A user send a request to create a new item without category") {

      Given("A json format item and a post request")
      val item = """{"id":1,"brand":"UnItem","price":22.36}"""
      val request = Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item)

      When("the request to create a new item is send")
      request ~!> routes ~> check {

        Then("is created a new item")
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == """{"brand":"UnItem","categoryId":0,"id":1,"price":22.36}""")
      }
    }

    Scenario("A user send a request to create a new item with category") {

      Given("A json format item, a category and a post request")
      val item = """{"brand":"UnItem","categoryId":1,"id":2,"price":22.36}"""
      val category = """{"id": 1,"code": "electronica.cellphone.Huawei"}"""
      Post("/ecommerce/category").withEntity(ContentTypes.`application/json`, category) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item)

      When("the request to create a new item with category is send")
      request ~!> routes ~> check {

        Then("is created a new item with a category")
          assert(status == StatusCodes.OK)
          assert(entityAs[String] == """{"brand":"UnItem","categoryId":1,"id":2,"price":22.36}""")
      }
    }

    Scenario("A user send a request to create a item already registered") {

      Given("A item, a category and a post request")
      val item = """{"brand":"UnItem","id":3,"price":22.36}"""
      val category = """{"id": 2,"code": "electronica.cellphone.Huawei"}"""
      Post("/ecommerce/category").withEntity(ContentTypes.`application/json`, category) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item)
      request ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }

      When("the request to create a already registered item is send")
      request ~!> routes ~> check {

        Then("Will be response a 400 and a 'Item already registered' message")
        assert(status == StatusCodes.BadRequest)
        assert(entityAs[String] == "Item already registered")
      }
    }

    Scenario("A user send a request to create an item with a category doesn't registered") {

      Given("A item and a request for create a item with a not registered category")
      val item = """{"brand":"UnItem","categoryId":1000,"id":1000,"price":22.36}"""
      val request = Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item)

      When("the request is send without a category registered")
      request ~!> routes ~> check {

        Then("Will be response  a 400 and a 'Item failed to create. The category associated with it is not registered' message")
        assert(status == StatusCodes.BadRequest)
        assert(entityAs[String] == "Item failed to create. The category associated with it is not registered")
      }
    }
  }

  Feature("Get the items") {

    Scenario("A user send a request to get ALL the items") {

      Given("A list of items saved in database and a GET request")
      val item = """{"brand":"UnItem","id":5,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Get("/ecommerce/item")

      When("the GET request is send")
      request ~!> routes ~> check {

        Then("Will be response a 200 status code")
        assert(status == StatusCodes.OK)
      }
    }

    Scenario("A user send a request to get a specific item") {

      Given("A item and a GET request")
      val item = """{"brand":"UnItem","categoryId":0,"id":6,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Get("/ecommerce/item/6")

      When("the GET request with a specific item id is send")
      request ~!> routes ~> check {

        Then("Will be response a 200 status and the item info")
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == item)
      }
    }

    Scenario("A user send a request to get a item that doesn't registered") {

      Given("A GET request to and id that is not registered")
      val request = Get("/ecommerce/item/7")

      When("the GET request with the id doesn't registered is send")
      request ~!> routes ~> check {

        Then("Will be response a 404 and a 'Item not found' message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "Item not found")
      }
    }

    Scenario("A user send a request to get a item deleted") {

      Given("A GET request and a item deleted")
      val item = """{"brand":"UnItem","categoryId":0,"id":8,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      Delete("/ecommerce/item/8") ~!> routes ~> check {assert(status == StatusCodes.OK)}
      val request = Get("/ecommerce/item/8")

      When("the GET request with the item id for a deleted is send")
      request ~!> routes ~> check {

        Then("Will be response a 404 and a 'Item not found' message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "Item not found")
      }
    }
  }

  Feature("Update a item") {

    Scenario("A user send a request to update a item") {

      Given("a item and an update of that item, also given a PUT request")
      val item = """{"brand":"UnItem","categoryId":0,"id":9,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val itemUpdate = """{"brand":"OtherItem","categoryId":0,"id":9,"price":33.36}"""
      val request = Put("/ecommerce/item").withEntity(ContentTypes.`application/json`, itemUpdate)

      When("the PUT request with the update item is send")
      request ~!> routes ~> check {

        Then("will be response a 200 and the update item")
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == itemUpdate)
      }
    }

    Scenario("A user send an update with a category doesn't registered") {

      Given("a item and an update with a category doesn't registered, also given a PUT request")
      val item = """{"brand":"UnItem","categoryId":0,"id":11,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val itemUpdate = """{"brand":"OtherItem","categoryId":1000,"id":11,"price":33.36}"""
      val request = Put("/ecommerce/item").withEntity(ContentTypes.`application/json`, itemUpdate)

      When("the PUT request with the update item with a wrong categoryId is send")
      request ~!> routes ~> check {

        Then("will be response a 400 and a 'Item failed to update. The category associated with it is not registered' message")
        assert(status == StatusCodes.BadRequest)
        assert(entityAs[String] == "Item failed to update. The category associated with it is not registered")
      }
    }
  }

  Feature("Delete a item") {

    Scenario("A user send a request to delete an item")  {

      Given("a item and a DELETE request")
      val item = """{"brand":"UnItem","categoryId":0,"id":12,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Delete("/ecommerce/item/12")

      When("the DELETE request is send")
      request ~!> routes ~> check {

        Then("will be response a 200 and the item deleted")
        assert(status == StatusCodes.OK)
      }
    }

    Scenario("A user send a request to delete an item already deleted") {

      Given("a item deleted and a DELETE request")
      val item = """{"brand":"UnItem","categoryId":0,"id":13,"price":22.36}"""
      Post("/ecommerce/item").withEntity(ContentTypes.`application/json`, item) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Delete("/ecommerce/item/13")
      request ~!> routes ~> check {assert(status == StatusCodes.OK)}

      When("the DELETE request is send for second time")
      request ~!> routes ~> check {

        Then("will be response a 404 and a 'Item already deleted' message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "Item already deleted")
      }
    }

    Scenario("A user send a request to delete an item that doesn't registered") {

      Given("a DELETE request with a item id that doesn't registered")
      val request = Delete("/ecommerce/item/14")

      When("the DELETE request with the id not registered is send")
      request ~!> routes ~> check {

        Then("will be response a 404 and a 'Item not found' message")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "Item not found")
      }
    }
  }
}
