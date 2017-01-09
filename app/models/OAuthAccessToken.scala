package models

import java.security.SecureRandom

import org.joda.time.DateTime
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import slick.driver.MySQLDriver.api._
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery, Tag}

import scala.concurrent.Future
import scala.util.Random
import com.github.tototoshi.slick.MySQLJodaSupport._
import slick.backend.DatabaseConfig

case class OauthAccessToken(
  id: Long,
  accountId: Long,
  oauthClientId: Long,
  accessToken: String,
  refreshToken: String,
  createdAt: DateTime
)

object OauthAccessToken {

  private val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfigProvider.get[JdbcProfile]
  private val oauthtokens: TableQuery[OauthAccessTokenTableDef] = TableQuery[OauthAccessTokenTableDef]

  def create(account: Account, client: OauthClient): Future[OauthAccessToken] = {
    def randomString(length: Int) = new Random(new SecureRandom()).alphanumeric.take(length).mkString
    val accessToken = randomString(40)
    val refreshToken = randomString(40)
    val createdAt = new DateTime()

    val newToken = new OauthAccessToken(0, account.id, client.id, accessToken, refreshToken, createdAt)
    oauthtokens += newToken

    //We will definitely have the access token here!
    findByAccessToken(accessToken).map(accessToken => accessToken.get)
  }

  def delete(account: Account, client: OauthClient): Future[Int] = {
    val query:Query[OauthAccessTokenTableDef, OauthAccessToken, Seq] = oauthtokens.filter(oauth => oauth.accountId === account.id && oauth.oauthClientId === client.id)
    dbConfig.db.run(query.delete).map(id => id)
  }

  def refresh(account: Account, client: OauthClient): Future[OauthAccessToken] = {
    delete(account, client)
    create(account, client)
  }

  def findByAccessToken(accessToken: String): Future[Option[OauthAccessToken]] = {
    val query:Query[OauthAccessTokenTableDef, OauthAccessToken, Seq] = oauthtokens.filter(_.accessToken === accessToken)
    dbConfig.db.run(query.result.headOption).map(oauthAccessToken => oauthAccessToken)
  }

  def findByAuthorized(account: Account, clientId: String): Future[Option[OauthAccessToken]] = {
    val query:Query[OauthAccessTokenTableDef, OauthAccessToken, Seq] = oauthtokens.filter(oauth => oauth.accountId === account.id && oauth.oauthClientId === clientId)
    dbConfig.db.run(query.result.headOption).map(oauthAccessToken => oauthAccessToken)
  }

  def findByRefreshToken(refreshToken: String): Future[Option[OauthAccessToken]] = {
    val expireAt = new DateTime().minusMonths(1)
    val query:Query[OauthAccessTokenTableDef, OauthAccessToken, Seq] = oauthtokens.filter(oauth => oauth.refreshToken === refreshToken && oauth.createdAt > expireAt)
    dbConfig.db.run(query.result.headOption).map(oauthAccessToken => oauthAccessToken)
  }

}

class OauthAccessTokenTableDef(tag: Tag) extends Table[OauthAccessToken](tag, "oauth_access_token") {
  val accounts: TableQuery[AccountTableDef] = TableQuery[AccountTableDef]
  val clients: TableQuery[OauthClientTableDef] = TableQuery[OauthClientTableDef]

  def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def accountId: Rep[Long] = column[Long]("account_id")
  def oauthClientId: Rep[Long] = column[Long]("oauth_client_id")
  def accessToken: Rep[String] = column[String]("access_token")
  def refreshToken: Rep[String] = column[String]("refresh_token")
  def createdAt: Rep[DateTime] = column[DateTime]("created_at")

  def account: ForeignKeyQuery[AccountTableDef, Account] = foreignKey("oauth_access_token_account_id_fkey", accountId, accounts)(_.id)
  def client: ForeignKeyQuery[OauthClientTableDef, OauthClient] = foreignKey("oauth_access_token_oauth_client_id_fkey", oauthClientId, clients)(_.id)

  override def * : ProvenShape[OauthAccessToken] = (id, accountId, oauthClientId, accessToken, refreshToken, createdAt) <> ((OauthAccessToken.apply _).tupled, OauthAccessToken.unapply)
}
