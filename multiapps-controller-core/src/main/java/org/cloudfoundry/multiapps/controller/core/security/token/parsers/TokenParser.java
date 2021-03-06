package org.cloudfoundry.multiapps.controller.core.security.token.parsers;

import org.springframework.security.oauth2.common.OAuth2AccessToken;

public interface TokenParser {

    OAuth2AccessToken parse(String tokenString);

}
