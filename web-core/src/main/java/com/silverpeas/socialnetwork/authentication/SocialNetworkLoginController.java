/**
 * Copyright (C) 2000 - 2012 Silverpeas
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * As a special exception to the terms and conditions of version 3.0 of
 * the GPL, you may redistribute this Program in connection with Free/Libre
 * Open Source Software ("FLOSS") applications as described in Silverpeas's
 * FLOSS exception.  You should have received a copy of the text describing
 * the FLOSS exception, and it is also available here:
 * "http://www.silverpeas.org/legal/licensing"
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.silverpeas.socialnetwork.authentication;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.social.connect.UserProfile;

import com.silverpeas.admin.service.UserService;
import com.silverpeas.admin.service.UserServiceProvider;
import com.silverpeas.socialnetwork.connectors.SocialNetworkConnector;
import com.silverpeas.socialnetwork.model.ExternalAccount;
import com.silverpeas.socialnetwork.model.SocialNetworkID;
import com.silverpeas.socialnetwork.service.AccessToken;
import com.silverpeas.socialnetwork.service.SocialNetworkAuthorizationException;
import com.silverpeas.socialnetwork.service.SocialNetworkService;
import com.stratelia.silverpeas.peasCore.URLManager;
import com.stratelia.silverpeas.silvertrace.SilverTrace;
import com.stratelia.webactiv.beans.admin.AdminException;
import com.stratelia.webactiv.util.ResourceLocator;

/**
 * Controller to log remote social network users into Silverpeas
 * @author Ludovic BERTIN
 */
public class SocialNetworkLoginController extends HttpServlet {

  private static final long serialVersionUID = 3019716885114707069L;

  private UserService userService = null;
  private ResourceLocator authenticationSettings = null;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    processLogin(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    processLogin(req, resp);
  }

  @Override
  public void init() throws ServletException {
    super.init();
    userService = UserServiceProvider.getInstance().getService();
    authenticationSettings = new ResourceLocator("com.silverpeas.authentication.settings.authenticationSettings", "");
  }

  /**
   * Process Handshake to authenticate user in remote social network and then log user in
   * Silverpeas. If no account has been created yet, it is automatically generated by get user's
   * remote social network profile info.
   * @param req HTTP request
   * @param resp HTTP response
   * @throws IOException
   * @throws ServletException
   */
  private void processLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException,
      ServletException {
    String command = req.getParameter("command");

    // First step, check Linked authentication
    if (command == null) {
      SocialNetworkID networkId = SocialNetworkID.valueOf(req.getParameter("networkId"));
      String authenticateURL = getAuthenticateURL(networkId, req);
      resp.sendRedirect(authenticateURL);
    }

    // Then
    else if ("backFromSocialNetworkAuthentication".equals(command)) {
      SocialNetworkID networkId = SocialNetworkID.valueOf(req.getParameter("networkId"));
      SocialNetworkConnector connector = getSocialNetworkConnector(networkId);

      AccessToken authorizationToken;
      try {
        authorizationToken = connector.exchangeForAccessToken(req, getRedirectURL(networkId, req));
      } catch (SocialNetworkAuthorizationException e) {
        resp.sendRedirect("/Login.jsp");
        return;
      }

      SocialNetworkService.getInstance().storeAuthorizationToken(req.getSession(true), networkId,
          authorizationToken);

      // Try to retrieve a silverpeas account linked to remote social network account
      String profileId = connector.getUserProfileId(authorizationToken);
      ExternalAccount account =
          SocialNetworkService.getInstance().getExternalAccount(networkId, profileId);

      // no Silverpeas account yet
      if (account == null) {

        // if new registration is enabled on platform, redirects user to registration
        if ( authenticationSettings.getBoolean("newRegistrationEnabled", false) ) {
          UserProfile profile = connector.getUserProfile(authorizationToken);
          req.setAttribute("userProfile", profile);
          req.setAttribute("networkId", networkId);
          req.getRequestDispatcher("/admin/jsp/registerFromRemoteSocialNetwork.jsp").forward(req,
              resp);
        }

        // new registration is disabled : redirect user to Login
        else {
          resp.sendRedirect(URLManager.getFullApplicationURL(req) +"/Login.jsp?ErrorCode=5");
        }
      }

      // Silverpeas account found, log user
      else {
        RequestDispatcher dispatcher = req.getRequestDispatcher("/AuthenticationServlet");
        dispatcher.forward(req, resp);
      }
    }

    // Silverpeas registration
    else if ("register".equals(command)) {
      SocialNetworkID networkId = SocialNetworkID.valueOf(req.getParameter("networkId"));
      SocialNetworkConnector socialNetworkConnector = getSocialNetworkConnector(networkId);

      String firstName = req.getParameter("firstName");
      String lastName = req.getParameter("lastName");
      String email = req.getParameter("email");

      try {
        String userId = userService.registerUser(firstName, lastName, email, "0");
        AccessToken authorizationToken =
            SocialNetworkService.getInstance().getStoredAuthorizationToken(req.getSession(true),
            networkId);
        String profileId = socialNetworkConnector.getUserProfileId(authorizationToken);
        SocialNetworkService.getInstance().createExternalAccount(networkId, userId, profileId);
      } catch (AdminException e) {
        SilverTrace.error("socialNetwork", "SocialNetworkLoginController.register",
            "socialNetwork.EX_CANT_REGISTER_USER");
        RequestDispatcher dispatcher = req.getRequestDispatcher("/admin/jsp/alreadyRegistered.jsp");
        dispatcher.forward(req, resp);
        return;
      }

      // Forward to authentication servlet
      RequestDispatcher dispatcher = req.getRequestDispatcher("/AuthenticationServlet");
      dispatcher.forward(req, resp);
    }

  }

  private String getRedirectURL(SocialNetworkID networkId, HttpServletRequest request) {
    StringBuffer redirectURL = new StringBuffer();
    redirectURL.append(URLManager.getFullApplicationURL(request));
    redirectURL
        .append("/SocialNetworkLogin?command=backFromSocialNetworkAuthentication&networkId=");
    redirectURL.append(networkId);

    return redirectURL.toString();
  }

  private SocialNetworkConnector getSocialNetworkConnector(SocialNetworkID networkId) {
    return SocialNetworkService.getInstance().getSocialNetworkConnector(networkId);
  }

  /**
   * Get URL to invoke remote social network authentication
   * @return
   */
  private String getAuthenticateURL(SocialNetworkID networkId, HttpServletRequest request) {
    return getSocialNetworkConnector(networkId).buildAuthenticateUrl(
        getRedirectURL(networkId, request));
  }

}