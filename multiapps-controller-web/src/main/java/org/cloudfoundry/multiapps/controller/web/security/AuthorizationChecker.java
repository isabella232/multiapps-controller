package org.cloudfoundry.multiapps.controller.web.security;

import java.text.MessageFormat;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;

import org.cloudfoundry.multiapps.common.SLException;
import org.cloudfoundry.multiapps.controller.client.util.TokenFactory;
import org.cloudfoundry.multiapps.controller.core.Messages;
import org.cloudfoundry.multiapps.controller.core.auditlogging.AuditLoggingProvider;
import org.cloudfoundry.multiapps.controller.core.cf.CloudControllerClientProvider;
import org.cloudfoundry.multiapps.controller.core.model.CachedMap;
import org.cloudfoundry.multiapps.controller.core.util.ApplicationConfiguration;
import org.cloudfoundry.multiapps.controller.core.util.UserInfo;
import org.cloudfoundry.multiapps.controller.persistence.model.CloudTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sap.cloudfoundry.client.facade.CloudControllerClient;
import com.sap.cloudfoundry.client.facade.domain.CloudSpace;
import com.sap.cloudfoundry.client.facade.domain.UserRole;

@Named
public class AuthorizationChecker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationChecker.class);
    private CachedMap<SpaceWithUser, UserRole> userRolesCache = null;

    private final CloudControllerClientProvider clientProvider;
    private final ApplicationConfiguration applicationConfiguration;

    @Inject
    public AuthorizationChecker(CloudControllerClientProvider clientProvider, ApplicationConfiguration applicationConfiguration) {
        this.clientProvider = clientProvider;
        this.applicationConfiguration = applicationConfiguration;
        initSpaceDevelopersCache();
    }

    private synchronized void initSpaceDevelopersCache() {
        if (userRolesCache != null) {
            return;
        }
        Integer cacheExpirationInSeconds = applicationConfiguration.getSpaceDeveloperCacheExpirationInSeconds();
        userRolesCache = new CachedMap<>(cacheExpirationInSeconds);
    }

    public void ensureUserIsAuthorized(HttpServletRequest request, UserInfo userInfo, CloudTarget target, String action) {
        try {
            if (!checkPermissions(userInfo, target.getOrganizationName(), target.getSpaceName(), isGetRequest(request))) {
                String message = MessageFormat.format(Messages.UNAUTHORISED_OPERATION_ORG_SPACE, action, target.getOrganizationName(),
                                                      target.getSpaceName());
                failWithForbiddenStatus(message);
            }
        } catch (SLException e) {
            String message = MessageFormat.format(Messages.PERMISSION_CHECK_FAILED_ORG_SPACE, action, target.getOrganizationName(),
                                                  target.getSpaceName());
            failWithUnauthorizedStatus(message);
        }
    }

    public void ensureUserIsAuthorized(HttpServletRequest request, UserInfo userInfo, String spaceGuid, String action) {
        try {
            if (!checkPermissions(userInfo, spaceGuid, isGetRequest(request))) {
                String message = MessageFormat.format(Messages.UNAUTHORISED_OPERATION_SPACE_ID, action, spaceGuid);
                failWithForbiddenStatus(message);
            }
        } catch (SLException e) {
            String message = MessageFormat.format(Messages.PERMISSION_CHECK_FAILED_SPACE_ID, action, spaceGuid);
            failWithUnauthorizedStatus(message);
        }
    }

    private boolean isGetRequest(HttpServletRequest request) {
        return HttpMethod.GET.matches(request.getMethod());
    }

    boolean checkPermissions(UserInfo userInfo, String orgName, String spaceName, boolean readOnly) {
        if (hasAdminScope(userInfo)) {
            return true;
        }
        // TODO a lot of cpu time is lost in the getControllerClient method
        CloudControllerClient client = clientProvider.getControllerClient(userInfo.getName());
        UUID userGuid = UUID.fromString(userInfo.getId());
        CloudSpace space = client.getSpace(orgName, spaceName);
        return hasPermissions(client, getSpaceWithUser(userGuid, space.getGuid()), readOnly);
    }

    boolean checkPermissions(UserInfo userInfo, String spaceId, boolean readOnly) {
        if (hasAdminScope(userInfo)) {
            return true;
        }
        UUID spaceGuid = convertSpaceIdToUUID(spaceId);
        CloudControllerClient client = clientProvider.getControllerClient(userInfo.getName());
        UUID userGuid = UUID.fromString(userInfo.getId());
        return hasPermissions(client, getSpaceWithUser(userGuid, spaceGuid), readOnly);
    }

    private UUID convertSpaceIdToUUID(String spaceId) {
        UUID spaceGuid = null;
        try {
            spaceGuid = UUID.fromString(spaceId);
        } catch (IllegalArgumentException e) {
            failWithNotFoundStatus(e.getMessage());
        }
        return spaceGuid;
    }

    private boolean hasPermissions(CloudControllerClient client, SpaceWithUser spaceWithUser, boolean readOnly) {
        if (isSpaceDeveloperUsingCache(client, spaceWithUser)) {
            return true;
        }
        UserRole userRole = refreshUserRole(client, spaceWithUser);
        if (userRole.hasSpaceRole(UserRole.SpaceRole.SPACE_DEVELOPER)) {
            return true;
        }
        return readOnly
            && (userRole.hasSpaceRole(UserRole.SpaceRole.SPACE_AUDITOR) || userRole.hasSpaceRole(UserRole.SpaceRole.SPACE_MANAGER));
    }

    private SpaceWithUser getSpaceWithUser(UUID userGuid, UUID spaceGuid) {
        return new SpaceWithUser(userGuid, spaceGuid);
    }

    private boolean isSpaceDeveloperUsingCache(CloudControllerClient client, SpaceWithUser spaceWithUser) {
        UserRole userRole = userRolesCache.get(spaceWithUser, () -> client.getUserRoleBySpaceGuidAndUserGuid(spaceWithUser.getSpaceGuid(),
                                                                                                             spaceWithUser.getUserGuid()));
        return userRole.hasSpaceRole(UserRole.SpaceRole.SPACE_DEVELOPER);
    }

    private UserRole refreshUserRole(CloudControllerClient client, SpaceWithUser spaceWithUser) {
        return userRolesCache.forceRefresh(spaceWithUser, () -> client.getUserRoleBySpaceGuidAndUserGuid(spaceWithUser.getSpaceGuid(),
                                                                                                         spaceWithUser.getUserGuid()));
    }

    private boolean hasAdminScope(UserInfo userInfo) {
        return userInfo.getToken()
                       .getScope()
                       .contains(TokenFactory.SCOPE_CC_ADMIN);
    }

    private void failWithNotFoundStatus(String message) {
        failWithStatus(HttpStatus.NOT_FOUND, message);
    }

    private void failWithUnauthorizedStatus(String message) {
        failWithStatus(HttpStatus.UNAUTHORIZED, message);
    }

    private void failWithForbiddenStatus(String message) {
        failWithStatus(HttpStatus.FORBIDDEN, message);
    }

    private static void failWithStatus(HttpStatus status, String message) {
        LOGGER.warn(message);
        AuditLoggingProvider.getFacade()
                            .logSecurityIncident(message);
        throw new ResponseStatusException(status, message);
    }
}
