package controllers

import javax.inject._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.whiteprompt.domain.{Task, TaskEntity}
import com.whiteprompt.persistence.TaskRepository
import com.whiteprompt.services.TaskServiceActor
import play.api.data.Forms._
import play.api.data._
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.Future
import scala.concurrent.duration._

case class TaskData(name: String, description: String) extends Task

@Singleton
class TaskController @Inject()(system: ActorSystem) extends Controller {
  import TaskServiceActor._
  implicit val timeout = Timeout(5 seconds)
  implicit val ec = system.dispatcher

  implicit val taskImplicitWrites = Json.writes[TaskEntity]
  implicit val taskImplicitReads = Json.reads[TaskData]

  val taskService = system.actorOf(TaskServiceActor.props(TaskRepository()), "task-service")

  val taskForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText
    )(TaskData.apply)(TaskData.unapply)
  )

  def create = Action.async(BodyParsers.parse.json) { implicit request =>
    taskForm.bindFromRequest.fold(
      formWithErrors => {
        Future(BadRequest)
      },
      taskData => {
        (taskService ? CreateTask(taskData))
          .mapTo[TaskEntity]
          .map(taskEntity => Created
            .withHeaders(
              LOCATION -> s"${request.uri}/${taskEntity.id}"
            )
          )
      }
    )
  }

  def retrieve(id: Long) = Action.async {
    (taskService ? RetrieveTask(id))
      .mapTo[Option[TaskEntity]].map {
        case Some(task) => Ok(Json.toJson(task))
        case None => NotFound
    }
  }

  def update(id: Long) = Action.async(BodyParsers.parse.json) { implicit request =>
    taskForm.bindFromRequest.fold(
      formWithErrors => {
        Future(BadRequest)
      },
      taskData => {
        (taskService ? UpdateTask(id, taskData)).mapTo[Option[TaskEntity]].map{
          case Some(taskEntity) => Ok(Json.toJson(taskEntity))
          case None => NotFound
        }
      }
    )
  }

  def delete(id: Long) = Action.async {
    (taskService ? DeleteTask(id))
      .mapTo[Option[TaskEntity]].map {
        case Some(taskEntity) => NoContent
        case None => NotFound
    }
  }

  def list = Action.async {
    (taskService ? ListTasks)
      .mapTo[Seq[TaskEntity]]
      .map(tasks => Ok(Json.toJson(tasks)))
  }
}
