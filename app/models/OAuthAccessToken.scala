package models

import java.security.SecureRandom

import org.joda.time.DateTime
import slick.lifted.Tag
import slick.model.Table

import scala.util.Random

case class OauthAccessToken(
  id: Long,
  accountId: Long,
  account: Option[Account] = None,
  oauthClientId: Long,
  oauthClient: Option[OauthClient] = None,
  accessToken: String,
  refreshToken: String,
  createdAt: DateTime
)

object OauthAccessToken {

  belongsTo[Account](Account, (oat, account) => oat.copy(account = account)).byDefault
  belongsTo[OauthClient](OauthClient, (oat, client) => oat.copy(oauthClient = client)).byDefault

  def create(account: Account, client: OauthClient): OauthAccessToken = {
    def randomString(length: Int) = new Random(new SecureRandom()).alphanumeric.take(length).mkString
    val accessToken = randomString(40)
    val refreshToken = randomString(40)
    val createdAt = new DateTime()

    val oauthAccessToken = new OauthAccessToken(
      id = 0,
      accountId = account.id,
      oauthClientId = client.id,
      accessToken = accessToken,
      refreshToken = refreshToken,
      createdAt = createdAt
    )

    val generatedId = OauthAccessToken.createWithNamedValues(
      column.accountId -> oauthAccessToken.accountId,
      column.oauthClientId -> oauthAccessToken.oauthClientId,
      column.accessToken -> oauthAccessToken.accessToken,
      column.refreshToken -> oauthAccessToken.refreshToken,
      column.createdAt -> oauthAccessToken.createdAt
    )
    oauthAccessToken.copy(id = generatedId)
  }

  def delete(account: Account, client: OauthClient): Int = {
    OauthAccessToken.deleteBy(sqls
      .eq(column.accountId, account.id).and
      .eq(column.oauthClientId, client.id)
    )
  }

  def refresh(account: Account, client: OauthClient): OauthAccessToken = {
    delete(account, client)
    create(account, client)
  }

  def findByAccessToken(accessToken: String): Option[OauthAccessToken] = {
    val oat = OauthAccessToken.defaultAlias
    OauthAccessToken.where(sqls.eq(oat.accessToken, accessToken)).apply().headOption
  }

  def findByAuthorized(account: Account, clientId: String): Option[OauthAccessToken] = {
    val oat = OauthAccessToken.defaultAlias
    val oac = OauthClient.defaultAlias
    OauthAccessToken.where(sqls
      .eq(oat.accountId, account.id).and
      .eq(oac.clientId, clientId)
    ).apply().headOption
  }

  def findByRefreshToken(refreshToken: String): Option[OauthAccessToken] = {
    val expireAt = new DateTime().minusMonths(1)
    val oat = OauthAccessToken.defaultAlias
    OauthAccessToken.where(sqls
      .eq(oat.refreshToken, refreshToken).and
      .gt(oat.createdAt, expireAt)
    ).apply().headOption
  }
}

class OauthAccessTokenTableDef(tag: Tag) extends Table[OauthAccessToken](tag, "oauth_access_token") {
  def id = column[Long]("id", O.PrimaryKey,O.AutoInc)
  def accountId = column[Long]("account_id")
  def email = column[String]("email")
  def password = column[String]("password")
  def createdAt = column[DateTime]("createdAt")

  override def * =
    (id, email, password, createdAt) <>(OauthAccessToken.tupled, OauthAccessToken.unapply)
}
