package models

import org.joda.time.DateTime
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import slick.driver.MySQLDriver.api._
import slick.lifted.{TableQuery, Tag}

import scala.concurrent.Future

case class OauthAuthorizationCode(
                                   id: Long,
                                   accountId: Long,
                                   oauthClientId: Long,
                                   code: String,
                                   redirectUri: Option[String],
                                   createdAt: DateTime)

object OauthAuthorizationCode {

  val dbConfig = DatabaseConfigProvider.get[JdbcProfile]
  val oauthcodes = TableQuery[OauthAuthorizationCodeTableDef]

  def findByCode(code: String): Future[Option[OauthAuthorizationCode]] = {
    val expireAt = new DateTime().minusMinutes(30).millisOfSecond()
    var query:Query[OauthAuthorizationCodeTableDef, OauthAuthorizationCode, Seq] = oauthcodes.filter(authcode => authcode.code === code && authcode.createdAt > expireAt)
    dbConfig.db.run(query.result.headOption)
  }

  def delete(code: String): Unit = {
    var query:Query[OauthAuthorizationCodeTableDef, OauthAuthorizationCode, Seq] = oauthcodes.filter(_.code === code)
    dbConfig.db.run(query.delete)
  }
}

class OauthAuthorizationCodeTableDef(tag: Tag) extends Table[OauthAuthorizationCode](tag, "oauth_authorization_code") {
  val accounts = TableQuery[AccountTableDef]
  val clients = TableQuery[OauthClientTableDef]

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def accountId = column[Long]("account_id")
  def oauthClientId = column[Long]("client_id")
  def code = column[String]("code")
  def redirectUri = column[String]("redirect_uti")
  def createdAt = column[DateTime]("created_at")

  def account = foreignKey("oauth_authorization_owner_id_fkey", accountId, accounts)(_.id)
  def client = foreignKey("oauth_authorization_client_id_fkey", oauthClientId, clients)(_.id)

  def * = (id, accountId, oauthClientId, code, redirectUri, createdAt) <> ((OauthAuthorizationCode.apply _).tupled, OauthAuthorizationCode.unapply)
}
