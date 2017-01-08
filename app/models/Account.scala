package models

import org.joda.time.DateTime
import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import slick.driver.MySQLDriver.api._
import slick.lifted.Tag


case class Account(id: Long, email: String, password: String, createdAt: DateTime)
object Account {

  val dbConfig = DatabaseConfigProvider.get[JdbcProfile](Play.current)
  val accounts: TableQuery[AccountTableDef] =
    TableQuery[AccountTableDef]((tag: Tag) => new AccountTableDef(tag))

  def authenticate(email: String, password: String) = {
    var query:Query[AccountTableDef, Account, Seq] = accounts.filter(_.email == email).filter(_.password == password)
    query.result
  }
}

class AccountTableDef(tag: Tag) extends Table[Account](tag, "account") {
  def id = column[Long]("id", O.PrimaryKey,O.AutoInc)
  def email = column[String]("email")
  def password = column[String]("password")
  def createdAt = column[DateTime]("createdAt")

  def * =
    (id, email, password, createdAt) <>((Account.apply _).tupled, Account.unapply)
}