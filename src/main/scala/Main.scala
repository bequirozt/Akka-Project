import akka.actor.{ActorRef, ActorSystem, Props}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.alpakka.cassandra.scaladsl.{CassandraSession, CassandraSessionRegistry}
import com.applaudostudios.store.actor.{LoadJson, ManagerActor, ProjectionsActor}

import java.nio.file.{Files, Paths}
import scala.annotation.tailrec
import scala.io.StdIn


object Main {
  @tailrec
  private def commandLoop(loader: ActorRef): Unit = {
    //Introduce filename for .json file to load:
    StdIn.readLine() match {
      case file: String if Files.exists(Paths.get(file)) =>
        println(s"Loading file $file")
        loader ! LoadJson.FilePath(file)
        commandLoop(loader)
      case "quit" | "Quit" | "q" =>
        println("Loading over")
      case s: String =>
        println(s"$s not found!")
        commandLoop(loader)
    }

  }
  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("System")
    implicit val readerSystem: ActorSystem = ActorSystem("QueryModelSystem")

    val projectionActor = readerSystem.actorOf(ProjectionsActor.props()(readerSystem), "projection-actor" )


    val managerActor : ActorRef = system.actorOf(Props(new ManagerActor(projectionActor)), "Db-Persistence-Actor")
    val loader:ActorRef = system.actorOf(Props(new LoadJson(managerActor)), "TheReader")

    managerActor ! "test"

    commandLoop(loader)
  }
}