package controllers

import db.api._
import db.charter.Sections
import javax.inject._
import model.Snowflake
import model.charter.Section
import play.api.data.Form
import play.api.data.Forms._
import slick.lifted.ColumnOrdered

import scala.concurrent.Future

@Singleton
class CharterController extends DashController {
  def index = DashAction.async { implicit req =>
    Sections.sortBy(s => s.number).result.map(sections => Ok(views.html.charter.index(sections)))
  }

  def edit(id: Option[Snowflake]) = DashAction.officers.async { implicit req =>
    id.map { i =>
        val s = Sections.filter(s => s.id === i).result.head
        s.map(s => CharterController.sectionForm.fill(CharterController.SectionForm(id, s.title, s.body)))
      }
      .getOrElse(DBIO.successful(CharterController.sectionForm))
      .map(form => Ok(views.html.charter.edit(id, form)))
  }

  def editPost(id: Option[Snowflake]) = DashAction.officers.async { implicit req =>
    CharterController.sectionForm
      .bindFromRequest()
      .fold(
        errors => Future.successful(Ok(views.html.charter.edit(id, errors))),
        data => {
          (id match {
            case Some(id) if data.body.trim.isEmpty =>
              Sections.filter(s => s.id === id).delete
            case _ =>
              id.map(i => Sections.filter(s => s.id === i).map(s => s.number).result.head)
                .getOrElse(Sections.map(s => s.number).max.getOrElse(0).result.map(_ + 1))
                .map(n => Section(id getOrElse Snowflake.next, n, data.title, data.body))
                .flatMap(s => Sections.insertOrUpdate(s))
          }).map { _ =>
            id match {
              case Some(id) => Redirect(routes.CharterController.index().withFragment(s"section-$id"))
              case None     => Redirect(routes.CharterController.index())
            }
          }
        }
      )
  }

  def moveUp(id: Snowflake)   = move(id, _ < _, _.desc)
  def moveDown(id: Snowflake) = move(id, _ > _, _.asc)

  private def move(id: Snowflake, filter: (Rep[Int], Int) => Rep[Boolean], sort: Rep[Int] => ColumnOrdered[Int]) =
    DashAction.officers.async {
      (for {
        target <- Sections.filter(s => s.id === id).result.head
        other  <- Sections.filter(s => filter(s.number, target.number)).sortBy(s => sort(s.number)).result.head
        _      <- Sections.filter(s => s.id === target.id).map(s => s.number).update(other.number)
        _      <- Sections.filter(s => s.id === other.id).map(s => s.number).update(target.number)
      } yield Redirect(routes.CharterController.index().withFragment(s"section-$id"))).transactionally
    }
}

object CharterController {
  val sectionForm = Form(
    mapping(
      "section" -> optional(of[Snowflake]),
      "title"   -> text,
      "body"    -> text
    )(SectionForm.apply)(SectionForm.unapply)
  )

  case class SectionForm(
      section: Option[Snowflake],
      title: String,
      body: String
  )
}
