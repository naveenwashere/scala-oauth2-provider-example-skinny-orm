package models

import com.github.tototoshi.slick.MySQLJodaSupport._
import org.joda.time.DateTime
import play.api.db.slick.DatabaseConfigProvider
import slick.backend.DatabaseConfig
import slick.driver.JdbcProfile
import slick.driver.MySQLDriver.api._
import slick.lifted.{ForeignKeyQuery, ProvenShape, TableQuery, Tag}

import scala.concurrent.Future

case class OauthAuthorizationCode(
                                   id: Long,
                                   accountId: Long,
                                   oauthClientId: Long,
                                   code: String,
                                   redirectUri: Option[String],
                                   createdAt: DateTime)

object OauthAuthorizationCode {

  private val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfigProvider.get[JdbcProfile]
  private val oauthcodes: TableQuery[OauthAuthorizationCodeTableDef] = TableQuery[OauthAuthorizationCodeTableDef]

  def findByCode(code: String): Future[Option[OauthAuthorizationCode]] = {
    val expireAt = new DateTime().minusMinutes(30)
    val query:Query[OauthAuthorizationCodeTableDef, OauthAuthorizationCode, Seq] = oauthcodes.filter(authcode => (authcode.code === code) && (authcode.createdAt > expireAt))
    dbConfig.db.run(query.result.headOption).map(oauthCode => oauthCode)
  }

  def delete(code: String): Unit = {
    val query:Query[OauthAuthorizationCodeTableDef, OauthAuthorizationCode, Seq] = oauthcodes.filter(_.code === code)
    dbConfig.db.run(query.delete)
  }
}

class OauthAuthorizationCodeTableDef(tag: Tag) extends Table[OauthAuthorizationCode](tag, "oauth_authorization_code") {
  val accounts: TableQuery[AccountTableDef] = TableQuery[AccountTableDef]
  val clients: TableQuery[OauthClientTableDef] = TableQuery[OauthClientTableDef]

  def id: Rep[Long] = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def accountId: Rep[Long] = column[Long]("account_id")
  def oauthClientId: Rep[Long] = column[Long]("client_id")
  def code: Rep[String] = column[String]("code")
  def redirectUri: Rep[String] = column[String]("redirect_uti")
  def createdAt: Rep[DateTime] = column[DateTime]("created_at")

  def account: ForeignKeyQuery[AccountTableDef, Account] = foreignKey("oauth_authorization_owner_id_fkey", accountId, accounts)(_.id)
  def client: ForeignKeyQuery[OauthClientTableDef, OauthClient] = foreignKey("oauth_authorization_client_id_fkey", oauthClientId, clients)(_.id)

  def * : ProvenShape[OauthAuthorizationCode] = (id, accountId, oauthClientId, code, redirectUri, createdAt) <> ((OauthAuthorizationCode.apply _).tupled, OauthAuthorizationCode.unapply)
}
