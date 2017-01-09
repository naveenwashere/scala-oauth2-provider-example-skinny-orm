package controllers

import models.OauthClient._
import models._
import play.api.libs.json.{JsObject, Json, Writes}
import play.api.mvc.{Action, AnyContent, Controller}

import scala.concurrent.Future
import scalaoauth2.provider.OAuth2ProviderActionBuilders._
import scalaoauth2.provider._

class OAuthController extends Controller with OAuth2Provider {

  implicit val authInfoWrites = new Writes[AuthInfo[Account]] {
    def writes(authInfo: AuthInfo[Account]): JsObject = {
      Json.obj(
        "account" -> Json.obj(
          "email" -> authInfo.user.email
        ),
        "clientId" -> authInfo.clientId,
        "redirectUri" -> authInfo.redirectUri
      )
    }
  }

  override val tokenEndpoint = new TokenEndpoint {
    override val handlers = Map(
      OAuthGrantType.AUTHORIZATION_CODE -> new AuthorizationCode(),
      OAuthGrantType.REFRESH_TOKEN -> new RefreshToken(),
      OAuthGrantType.CLIENT_CREDENTIALS -> new ClientCredentials(),
      OAuthGrantType.PASSWORD -> new Password()
    )
  }

  def accessToken: Action[AnyContent] = Action.async { implicit request =>
    issueAccessToken(new MyDataHandler())
  }

  def resources: Action[AnyContent] = AuthorizedAction(new MyDataHandler()) { request =>
    Ok(Json.toJson(request.authInfo))
  }

  class MyDataHandler extends DataHandler[Account] {

    // common

    override def validateClient(request: AuthorizationRequest): Future[Boolean] = {
      val clientCredential = request.clientCredential.get
      OauthClient.validate(clientCredential.clientId, clientCredential.clientSecret.getOrElse(""), request.grantType)
        .map(success => success)
    }

    /*override def getStoredAccessToken(authInfo: AuthInfo[Account]): Future[Option[AccessToken]] =
      OauthAccessToken.findByAuthorized(authInfo.user, authInfo.clientId.getOrElse(""))
          .flatMap {
            case Some(accessToken) => accessToken.get,
            case None => None
          }*/

    override def createAccessToken(authInfo: AuthInfo[Account]): Future[AccessToken] = {
      val clientId = authInfo.clientId.getOrElse(throw new InvalidClient())
      findByClientId(clientId)
        .map(oauthClient => OauthAccessToken.create(authInfo.user, oauthClient.get))
          .map(oauthAccessToken => toAccessToken(oauthAccessToken.value.get.get))
    }

    private val accessTokenExpireSeconds = 3600
    private def toAccessToken(accessToken: OauthAccessToken) = {
      AccessToken(
        accessToken.accessToken,
        Some(accessToken.refreshToken),
        None,
        Some(accessTokenExpireSeconds),
        accessToken.createdAt.toDate
      )
    }

    override def findUser(request: AuthorizationRequest): Future[Option[Account]] = {
      super.findUser(request)
    }

    // Refresh token grant

    /*override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[Account]]] = {
      OauthAccessToken.findByRefreshToken(refreshToken).flatMap(accessToken =>
        for {
          account <- accessToken.account
          client <- accessToken.oauthClient
        } yield {
          AuthInfo(
            user = account,
            clientId = Some(client.clientId),
            scope = None,
            redirectUri = None
          )
        })
    }*/

    /*override def refreshAccessToken(authInfo: AuthInfo[Account], refreshToken: String): Future[AccessToken] = {
      val clientId = authInfo.clientId.getOrElse(throw new InvalidClient())
      val client = findByClientId(clientId).onSuccess(oauthClient => oauthClient.getOrElse(throw new InvalidClient())).andThen()
      val accessToken = OauthAccessToken.refresh(authInfo.user, client.)
      Future.successful(toAccessToken(accessToken))
    }*/

    // Authorization code grant

    /*override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[Account]]] = {
      Future.successful(OauthAuthorizationCode.findByCode(code).map(authorization =>
        for {
          account <- authorization.account
          client <- authorization.oauthClient
        } yield {
          AuthInfo(
            user = account,
            clientId = Some(client.clientId),
            scope = None,
            redirectUri = authorization.redirectUri
          )
        }
        ))
    }*/

    override def deleteAuthCode(code: String): Future[Unit] = {
      Future.successful(OauthAuthorizationCode.delete(code))
    }

    // Protected resource

    /*override def findAccessToken(token: String): Future[Option[AccessToken]] = {
      OauthAccessToken.findByAccessToken(token).map(toAccessToken => accessToken)
    }*/

    /*override def findAuthInfoByAccessToken(accessToken: AccessToken): Future[Option[AuthInfo[Account]]] = {
      OauthAccessToken.findByAccessToken(accessToken.token).map(accessToken =>
        for {
          account <- accessToken.get.account
          client <- accessToken.get.oauthClient
        } yield {
          AuthInfo(
            user = account,
            clientId = Some(client.clientId),
            scope = None,
            redirectUri = None
          )
        }
        ))
    }*/
  }
}
