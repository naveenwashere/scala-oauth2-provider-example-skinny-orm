package models

import java.security.SecureRandom
import java.sql.Timestamp

import com.github.tototoshi.slick.MySQLJodaSupport._
import org.joda.time.DateTime
import play.api.Play.current
import play.api.db.slick.DatabaseConfigProvider
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.driver.MySQLDriver.api._
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery, Tag}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

case class OauthAccessToken(
  id: Long,
  accountId: Long,
  oauthClientId: String,
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

    dbConfig.db.run(DBIO.seq(oauthtokens += OauthAccessToken(0, account.id, client.clientId, accessToken, refreshToken, createdAt))).flatMap(_ => {
      //We will definitely have the access token here!
      findByAccessToken(accessToken).map {accessToken => accessToken match {
        case Some(accessToken) => accessToken
      }}
    })
  }

  def delete(account: Account, client: OauthClient): Future[Int] = {
    val query:Query[OauthAccessTokenTableDef, OauthAccessToken, Seq] = oauthtokens.filter(oauth => oauth.accountId === account.id && oauth.oauthClientId === client.clientId)
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
  implicit def dateTime =
    MappedColumnType.base[DateTime, Timestamp](
      dt => new Timestamp(dt.getMillis),
      ts => new DateTime(ts.getTime)
    )

  val accounts: TableQuery[AccountTableDef] = TableQuery[AccountTableDef]
  val clients: TableQuery[OauthClientTableDef] = TableQuery[OauthClientTableDef]

  def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def accountId: Rep[Long] = column[Long]("account_id")
  def oauthClientId: Rep[String] = column[String]("oauth_client_id")
  def accessToken: Rep[String] = column[String]("access_token")
  def refreshToken: Rep[String] = column[String]("refresh_token")
  def createdAt: Rep[DateTime] = column[DateTime]("created_at")

  def account: ForeignKeyQuery[AccountTableDef, Account] = foreignKey("oauth_access_token_account_id_fkey", accountId, accounts)(_.id)
  def client: ForeignKeyQuery[OauthClientTableDef, OauthClient] = foreignKey("oauth_access_token_oauth_client_id_fkey", oauthClientId, clients)(_.clientId)

  override def * : ProvenShape[OauthAccessToken] = (id, accountId, oauthClientId, accessToken, refreshToken, createdAt) <> ((OauthAccessToken.apply _).tupled, OauthAccessToken.unapply)
}
