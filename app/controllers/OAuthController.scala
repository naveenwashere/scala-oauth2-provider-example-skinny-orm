package controllers

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

    override def getStoredAccessToken(authInfo: AuthInfo[Account]): Future[Option[AccessToken]] =
      OauthAccessToken.findByAuthorized(authInfo.user, authInfo.clientId.getOrElse(""))
          .map { optionVal =>
            optionVal.map { accessToken =>
              toAccessToken(accessToken)
            }
          }

    override def createAccessToken(authInfo: AuthInfo[Account]): Future[AccessToken] = {
      val clientId = authInfo.clientId.getOrElse(throw new InvalidClient())
      for {
        oauthClient <- OauthClient.findByClientId(clientId)
        oauthAccessToken <- OauthAccessToken.create(authInfo.user, oauthClient.get)
      } yield toAccessToken(oauthAccessToken)
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
      request match {
        case request: PasswordRequest =>
          Account.authenticate(request.username, request.password)
        case request: ClientCredentialsRequest =>
          request.clientCredential match {
            case Some(credential) =>
              OauthClient.findClientCredentials(credential.clientId, credential.clientSecret.getOrElse(""))
                  .flatMap(oauthClient => Account.findById(oauthClient.get.ownerId))
            case None =>
              Future.successful(None)
          }
        case _ =>
          Future.successful(None)
      }
    }

    // Refresh token grant

    override def findAuthInfoByRefreshToken(refreshToken: String): Future[Option[AuthInfo[Account]]] = {
      for {
        oauthAccessToken <- OauthAccessToken.findByRefreshToken(refreshToken)
        account <- Account.findById(oauthAccessToken.get.accountId)
        client <- OauthClient.findByClientId(oauthAccessToken.get.oauthClientId)
      } yield {
        Option(AuthInfo(
          user = account.get,
          clientId = Some(client.get.clientId),
          scope = None,
          redirectUri = None
        ))
      }
    }

    override def refreshAccessToken(authInfo: AuthInfo[Account], refreshToken: String): Future[AccessToken] = {
      for {
        client <- OauthClient.findByClientId(authInfo.clientId.get)
        accessToken <- OauthAccessToken.refresh(authInfo.user, client.get)
      } yield {
        toAccessToken(accessToken)
      }
    }

    // Authorization code grant

    override def findAuthInfoByCode(code: String): Future[Option[AuthInfo[Account]]] = {
      for {
          authorization <- OauthAuthorizationCode.findByCode(code)
          account <- Account.findById(authorization.get.accountId)
          client <- OauthClient.findByClientId(authorization.get.oauthClientId)
        } yield {
          Option(AuthInfo(
            user = account.get,
            clientId = Some(client.get.clientId),
            scope = None,
            redirectUri = authorization.get.redirectUri
          ))
        }
    }

    override def deleteAuthCode(code: String): Future[Unit] = {
      Future.successful(OauthAuthorizationCode.delete(code))
    }

    // Protected resource

    override def findAccessToken(token: String): Future[Option[AccessToken]] = {
      OauthAccessToken.findByAccessToken(token).map { oauthAccessToken =>
        oauthAccessToken.map {
          accessToken => toAccessToken(accessToken)
        }}
    }

    override def findAuthInfoByAccessToken(accessToken: AccessToken): Future[Option[AuthInfo[Account]]] = {
      for {
        accessToken <- OauthAccessToken.findByAccessToken(accessToken.token)
        account <- Account.findById(accessToken.get.accountId)
        client <- OauthClient.findByClientId(accessToken.get.oauthClientId)
      } yield {
        Option(AuthInfo(
          user = account.get,
          clientId = Some(client.get.clientId),
          scope = None,
          redirectUri = None
        ))
      }
    }
  }
}
