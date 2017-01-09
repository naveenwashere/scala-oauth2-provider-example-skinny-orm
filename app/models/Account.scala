package models

import org.joda.time.DateTime
import play.api.db.slick.DatabaseConfigProvider
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.driver.MySQLDriver.api._
import slick.lifted.{ProvenShape, TableQuery, Tag}

import scala.concurrent.Future


case class Account(id: Long, email: String, password: String, createdAt: DateTime)

object Account {

  val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfigProvider.get[JdbcProfile]
  val accounts: TableQuery[AccountTableDef] = TableQuery[AccountTableDef]

  def authenticate(email: String, password: String): Future[Option[Account]] = {
    val query:Query[AccountTableDef, Account, Seq] = accounts.filter(account => account.email === email && account.password === password)
    dbConfig.db.run(query.result.headOption).map(account => account)
  }

  def findById(id: Long): Future[Option[Account]] = {
    val query:Query[AccountTableDef, Account, Seq] = accounts.filter(_.id == id)
    dbConfig.db.run(query.result.headOption).map(account => account)
  }
}

class AccountTableDef(tag: Tag) extends Table[Account](tag, "account") {
  def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def email: Rep[String] = column[String]("email")
  def password: Rep[String] = column[String]("password")
  def createdAt: Rep[DateTime] = column[DateTime]("createdAt")

  def * : ProvenShape[Account] = (id, email, password, createdAt) <> ((Account.apply _).tupled, Account.unapply)
}