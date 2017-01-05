package models

import java.security.MessageDigest

import org.joda.time.DateTime
import scalikejdbc._
import skinny.orm._

case class Account(id: Long, email: String, password: String, createdAt: DateTime)
object Account extends SkinnyCRUDMapper[Account] {

  override def defaultAlias = createAlias("a")
  val ownerAlias = createAlias("owner")

  override def extract(rs: WrappedResultSet, a: ResultName[Account]) = new Account(
    id = rs.get(a.id),
    email = rs.get(a.email),
    password = rs.get(a.password),
    createdAt = rs.get(a.createdAt)
  )

  def authenticate(email: String, password: String)(implicit s: DBSession): Option[Account] = {
    val a = Account.defaultAlias
    Account.where(sqls.eq(a.email, email).and.eq(a.password, password)).apply().headOption
  }
}
