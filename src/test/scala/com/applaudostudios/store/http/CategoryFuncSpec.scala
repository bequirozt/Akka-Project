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

class CategoryFuncSpec
  extends AnyFeatureSpecLike
    with GivenWhenThen
    with ScalatestRouteTest {

  info("As a client")
  info("I want to be able to handle the categories information")
  info("So I can manage all the categories")

  val categoryManager: ActorRef = system.actorOf(CategoriesManager.props(), "Categories-Manager-01")
  val itemManager: ActorRef = system.actorOf(ItemsManager.props(categoryManager), "Items-Manager-01")
  val userManager: ActorRef = system.actorOf(UsersManager.props(itemManager, categoryManager), "Users-Manager-01")
  val routes: Route = Controller(categoryManager, itemManager, userManager).mainRoute

  Feature("Create a new category") {

    Scenario("A user send a request to create a new category") {

      Given("A json format category")
      val newCategoryJson = """{"code":"electronica.cellphone.Huawei","id":1111111111177777777}"""
      val request = Post("/ecommerce/category").withEntity(ContentTypes.`application/json`, newCategoryJson)

      When("A request for create a new category is send through the endpoint")
      request ~!> routes ~> check {

        Then("A new category must be created and a 200 response with a new category json should be response")
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == newCategoryJson)
      }
    }

    Scenario("A user send a request to create a new category without a code") {

      Given("A json format category without code")
      val newCategoryJson = """{"code":"","id":1111111111177777778}"""

      When("A request for create a new category is send through the endpoint")
      val request = Post("/ecommerce/category").withEntity(ContentTypes.`application/json`, newCategoryJson)
      request ~!> routes ~> check {

        Then("A new category must be created and a 200 response with a new category json should be response")
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == newCategoryJson)
      }
    }

    Scenario("A user send a request to create a category already created") {

      Given("A json format category and a creation of that category")
      val newCategoryJson = """{"code":"electronica.cellphone.Huawei","id":1111111111177777779}"""
      val request = Post("/ecommerce/category").withEntity(ContentTypes.`application/json`, newCategoryJson)
      request ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }

      When("A request for create a category already created is send")
      request ~!> routes ~> check {

        Then("A bad request is response with a 'Category already registered' message")
        assert(status == StatusCodes.BadRequest)
        assert(entityAs[String] == "Category already registered")
      }
    }
  }

  Feature("Get categories already created") {

    Scenario("A user send a request to get a category") {

      Given("a new category created and a get request for the category")
      val newCategoryJson = """{"code":"electronica.cellphone.Huawei","id":11111111111777777710}"""
      Post("/ecommerce/category").withEntity(ContentTypes.`application/json`, newCategoryJson) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Get("/ecommerce/category/11111111111777777710")

      When("a request to get the category is send")
      request ~!> routes ~> check {

        Then("A list with ALL the categories already created is response")
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == newCategoryJson)
      }
    }

    Scenario("A user send a request to get a category that not exist") {

      Given("a request for get a category that not exist")
      val request = Get("/ecommerce/category/1111111111177777771000000")

      When("the request is send")
      request ~!> routes ~> check {

        Then("A status 404 and a 'Category not found' message is response")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "Category not found")
      }
    }
  }

  Feature("Update a category") {

    Scenario("A user send a request to update a category") {

      Given("a request for update a category and a category already created")
      val newCategoryJson = """{"code":"electronica.cellphone.Huawei","id":11111111111777777711}"""
      Post("/ecommerce/category").withEntity(ContentTypes.`application/json`, newCategoryJson) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val updateCategoryJson = """{"code":"electronica.cellphone.Samsung","id":11111111111777777711}"""
      val request = Put("/ecommerce/category").withEntity(ContentTypes.`application/json`, updateCategoryJson)

      When("A update request is send")
      request ~!> routes ~> check {

        Then("A status 200 and a category with the info updated are response")
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == updateCategoryJson)
      }
    }

    Scenario("A user send a request to update without changes") {

      Given("a request for update without changes and a category already created")
      val newCategoryJson = """{"code":"electronica.cellphone.Huawei","id":11111111111777777712}"""
      val request = Put("/ecommerce/category").withEntity(ContentTypes.`application/json`, newCategoryJson)

      When("A update request without changes is send")
      request ~!> routes ~> check {

        Then("A status 404 and a 'Category not found' message are response")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "Category not found")
      }
    }
  }

  Feature("Delete a category") {

    Scenario("A user send a request to delete a category") {

      Given("a category and a request for delete")
      val newCategoryJson = """{"code":"electronica.cellphone.Huawei","id":11111111111777777713}"""
      Post("/ecommerce/category").withEntity(ContentTypes.`application/json`, newCategoryJson) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Delete("/ecommerce/category/11111111111777777713")

      When("the request to delete is send")
      request ~!> routes ~> check {

        Then("A status 200 and the category deleted are response")
        assert(status == StatusCodes.OK)
        assert(entityAs[String] == newCategoryJson)
      }
    }

    Scenario("A user send a request to delete a category that doesn't exist") {

      Given("a request for delete a category that does not exist")
      val request = Delete("/ecommerce/category/11111111111777777714")

      When("the request is send")
      request ~!> routes ~> check {

        Then("A status 404 and a 'Category not found' message are response")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "Category not found")
      }
    }

    Scenario("A user send a request to delete a category already deleted") {

      Given("a category deleted and a request for delete the same category")
      val newCategoryJson = """{"code":"electronica.cellphone.Huawei","id":11111111111777777715}"""
      Post("/ecommerce/category").withEntity(ContentTypes.`application/json`, newCategoryJson) ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }
      val request = Delete("/ecommerce/category/11111111111777777715")
      request ~!> routes ~> check {
        assert(status == StatusCodes.OK)
      }

      When("the request to delete a category already deleted is send")
      request ~!> routes ~> check {

        Then("a status 404 and a 'Category already deleted' message are response")
        assert(status == StatusCodes.NotFound)
        assert(entityAs[String] == "Category already deleted")
      }
    }
  }
}
