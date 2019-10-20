package controllers

import db.api._
import db.bounties.{Bounties, Objectives}
import javax.inject._
import model.Snowflake

@Singleton
class BountiesController extends DashController {
  def index = DashAction.authenticated.async { implicit req =>
    Bounties
      .joinLeft(Objectives)
      .on((b, o) => b.id === o.bounty)
      .result
      .map { rows =>
        rows
          .groupBy { case (b, _) => b }
          .view
          .mapValues { obj =>
            obj.collect { case (_, Some(o)) => o }.sortBy(o => o.label)
          }
          .toSeq
          .sortBy { case (b, _) => b.title }
      }
      .map(bounties => Ok(views.html.bounties.index(bounties)))
  }

  def edit(id: Option[Snowflake]) = DashAction.check(_.isOfficer).async { implicit req =>
    ???
  }

  def editPost(id: Option[Snowflake]) = DashAction.check(_.isOfficer).async { implicit req =>
    ???
  }
}
