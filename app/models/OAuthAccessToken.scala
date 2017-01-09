package models

import java.security.SecureRandom

import org.joda.time.DateTime
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import slick.driver.MySQLDriver.api._
import slick.lifted.Tag

import scala.concurrent.Future
import scala.util.Random

case class OauthAccessToken(
  id: Long,
  accountId: Long,
  oauthClientId: Long,
  accessToken: String,
  refreshToken: String,
  createdAt: DateTime
)

object OauthAccessToken {

  val dbConfig = DatabaseConfigProvider.get[JdbcProfile]
  val oauthtokens = TableQuery[OauthAccessTokenTableDef]

  def create(account: Account, client: OauthClient): Future[Option[OauthAccessToken]] = {
    def randomString(length: Int) = new Random(new SecureRandom()).alphanumeric.take(length).mkString
    val accessToken = randomString(40)
    val refreshToken = randomString(40)
    val createdAt = new DateTime()

    val newToken = new OauthAccessToken(0, account.id, client.id, accessToken, refreshToken, createdAt)
    oauthtokens += newToken

    findByAccessToken(accessToken)
  }

  def delete(account: Account, client: OauthClient): Future[Int] = {
    val expireAt = new DateTime().minusMonths(1)
    var query:Query[OauthAccessTokenTableDef, OauthAccessToken, Seq] = oauthtokens.filter(oauth => oauth.accountId === account.id && oauth.oauthClientId === client.id)
    dbConfig.db.run(query.delete)
  }

  def refresh(account: Account, client: OauthClient): Future[Option[OauthAccessToken]] = {
    delete(account, client)
    create(account, client)
  }

  def findByAccessToken(accessToken: String): Future[Option[OauthAccessToken]] = {
    var query:Query[OauthAccessTokenTableDef, OauthAccessToken, Seq] = oauthtokens.filter(_.accessToken === accessToken)
    dbConfig.db.run(query.result.headOption)
  }

  def findByAuthorized(account: Account, clientId: String): Future[Option[OauthAccessToken]] = {
    var query:Query[OauthAccessTokenTableDef, OauthAccessToken, Seq] = oauthtokens.filter(oauth => oauth.accountId === account.id && oauth.oauthClientId === clientId)
    dbConfig.db.run(query.result.headOption)
  }

  def findByRefreshToken(refreshToken: String): Future[Option[OauthAccessToken]] = {
    val expireAt = new DateTime().minusMonths(1)
    var query:Query[OauthAccessTokenTableDef, OauthAccessToken, Seq] = oauthtokens.filter(oauth => oauth.refreshToken === refreshToken && oauth.createdAt > expireAt)
    dbConfig.db.run(query.result.headOption)
  }

}

class OauthAccessTokenTableDef(tag: Tag) extends Table[OauthAccessToken](tag, "oauth_access_token") {
  val accounts = TableQuery[AccountTableDef]
  val clients = TableQuery[AccountTableDef]

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def accountId = column[Long]("account_id")
  def oauthClientId = column[Long]("oauth_client_id")
  def accessToken = column[String]("access_token")
  def refreshToken = column[String]("refresh_token")
  def createdAt = column[DateTime]("created_at")

  def account = foreignKey("oauth_access_token_account_id_fkey", accountId, accounts)(_.id)
  def client = foreignKey("oauth_access_token_oauth_client_id_fkey", oauthClientId, clients)(_.id)

  override def * = (id, accountId, oauthClientId, accessToken, refreshToken, createdAt) <> ((OauthAccessToken.apply _).tupled, OauthAccessToken.unapply)
}
