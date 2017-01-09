package models

import org.joda.time.DateTime
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import slick.driver.MySQLDriver.api._
import slick.lifted.{TableQuery, Tag}

import scala.concurrent.Future

case class OauthClient(
  id: Long,
  ownerId: Long,
  grantType: String,
  clientId: String,
  clientSecret: String,
  redirectUri: Option[String],
  createdAt: DateTime
)

object OauthClient {

  val dbConfig = DatabaseConfigProvider.get[JdbcProfile]
  val clients = TableQuery[OauthClientTableDef]

  def validate(clientId: String, clientSecret: String, grantType: String): Future[Boolean] = {
    var query:Query[OauthClientTableDef, OauthClient, Seq] = clients.filter(_.clientId === clientId).filter(_.clientSecret === clientSecret)
    dbConfig.db.run(query.result.headOption).map(client => grantType == client.get.grantType || grantType == "refresh_token")
  }

  def findByClientId(clientId: String): Future[Option[OauthClient]] = {
    var query:Query[OauthClientTableDef, OauthClient, Seq] = clients.filter(_.clientId === clientId)
    dbConfig.db.run(query.result.headOption)
  }

  def findClientCredentials(clientId: String, clientSecret: String): Future[Option[OauthClient]] = {
    var query:Query[OauthClientTableDef, OauthClient, Seq] = clients.filter(_.clientId === clientId).filter(client => client.clientSecret === clientSecret && client.grantType === "client_credentials")
    dbConfig.db.run(query.result.headOption)
  }
}

class OauthClientTableDef(tag: Tag) extends Table[OauthClient](tag, "oauth_client") {
  val accounts = TableQuery[AccountTableDef]

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def ownerId = column[Long]("owner_id")
  def grantType = column[String]("grant_type")
  def clientId = column[String]("client_id")
  def clientSecret = column[String]("client_secret")
  def redirectUri = column[String]("redirect_uti")
  def createdAt = column[DateTime]("created_at")

  def account = foreignKey("oauth_client_owner_id_fkey", ownerId, accounts)(_.id)

  def * = (id, ownerId, grantType, clientId, clientSecret, redirectUri, createdAt) <> ((OauthClient.apply _).tupled, OauthClient.unapply)
}
