package models

import java.sql.Timestamp

import org.joda.time.DateTime
import play.api.Play.current
import play.api.db.slick.DatabaseConfigProvider
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.driver.MySQLDriver.api._
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery, Tag}

import scala.concurrent.ExecutionContext.Implicits.global
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

  private val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfigProvider.get[JdbcProfile]
  private val clients: TableQuery[OauthClientTableDef] = TableQuery[OauthClientTableDef]

  def validate(clientId: String, clientSecret: String, grantType: String): Future[Boolean] = {
    val query:Query[OauthClientTableDef, OauthClient, Seq] = clients.filter(_.clientId === clientId).filter(_.clientSecret === clientSecret)
    dbConfig.db.run(query.result.headOption).map(client => grantType == client.get.grantType || grantType == "refresh_token").map(booleanVal => booleanVal)
  }

  def findByClientId(clientId: String): Future[Option[OauthClient]] = {
    val query:Query[OauthClientTableDef, OauthClient, Seq] = clients.filter(_.clientId === clientId)
    dbConfig.db.run(query.result.headOption).map(oauthClient => oauthClient)
  }

  def findClientCredentials(clientId: String, clientSecret: String): Future[Option[OauthClient]] = {
    val query:Query[OauthClientTableDef, OauthClient, Seq] = clients.filter(_.clientId === clientId).filter(client => client.clientSecret === clientSecret && client.grantType === "client_credentials")
    dbConfig.db.run(query.result.headOption).map(oauthClient => oauthClient)
  }
}

class OauthClientTableDef(tag: Tag) extends Table[OauthClient](tag, "oauth_client") {
  implicit def dateTime =
    MappedColumnType.base[DateTime, Timestamp](
      dt => new Timestamp(dt.getMillis),
      ts => new DateTime(ts.getTime)
    )

  val accounts: TableQuery[AccountTableDef] = TableQuery[AccountTableDef]

  def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def ownerId: Rep[Long] = column[Long]("owner_id")
  def grantType: Rep[String] = column[String]("grant_type")
  def clientId: Rep[String] = column[String]("client_id")
  def clientSecret: Rep[String] = column[String]("client_secret")
  def redirectUri: Rep[Option[String]] = column[Option[String]]("redirect_uti")
  def createdAt: Rep[DateTime] = column[DateTime]("created_at")

  def account: ForeignKeyQuery[AccountTableDef, Account] = foreignKey("oauth_client_owner_id_fkey", ownerId, accounts)(_.id)

  def * : ProvenShape[OauthClient] = (id, ownerId, grantType, clientId, clientSecret, redirectUri, createdAt) <> ((OauthClient.apply _).tupled, OauthClient.unapply)
}
