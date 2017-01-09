package models

import org.joda.time.DateTime
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import slick.driver.MySQLDriver.api._
import slick.lifted.Tag

import scala.concurrent.Future


case class Account(id: Long, email: String, password: String, createdAt: DateTime)

object Account {

  val dbConfig = DatabaseConfigProvider.get[JdbcProfile]
  val accounts = TableQuery[AccountTableDef]

  def authenticate(email: String, password: String): Future[Option[Account]] = {
    var query:Query[AccountTableDef, Account, Seq] = accounts.filter(account => account.email === email && account.password === password)
    dbConfig.db.run(query.result.headOption)
  }
}

class AccountTableDef(tag: Tag) extends Table[Account](tag, "account") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def email = column[String]("email")
  def password = column[String]("password")
  def createdAt = column[DateTime]("createdAt")

  def * = (id, email, password, createdAt) <> ((Account.apply _).tupled, Account.unapply)
}