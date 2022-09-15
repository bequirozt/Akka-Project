import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http

import com.applaudostudios.store.util.LoadJson
import com.applaudostudios.store.domain.categories.CategoriesManager
import com.applaudostudios.store.domain.manager.EcommerceManager
import com.applaudostudios.store.domain.items.ItemsManager
import com.applaudostudios.store.domain.users.UsersManager
import com.applaudostudios.store.http.Controller



import scala.io.StdIn


object Main {

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("RootSystem")

    val catManager = system.actorOf(CategoriesManager.props(), "Categories-Manager-01")
    val itemsManager = system.actorOf(ItemsManager.props(catManager), "Items-Manager-01")
    val usersManager = system.actorOf(UsersManager.props(itemsManager,catManager), "Users-Manager-01")

    val ecommerceManager : ActorRef = system.actorOf(Props(new EcommerceManager(catManager, itemsManager, usersManager)), "Ecommerce-Manager")
    val loader:ActorRef = system.actorOf(Props(new LoadJson(ecommerceManager)), "JsonFile-Reader")

    val controller = Controller(catManager, itemsManager, usersManager)
    Http().newServerAt("localhost",8090).bind(controller.mainRoute)

    system.log.info("Provide the file path of the file to load data from:\n")
    loader ! LoadJson.FilePath(StdIn.readLine())

  }
}