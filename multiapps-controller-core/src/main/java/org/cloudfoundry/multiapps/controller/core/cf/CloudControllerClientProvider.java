package org.cloudfoundry.multiapps.controller.core.cf;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.cloudfoundry.multiapps.common.SLException;
import org.cloudfoundry.multiapps.controller.core.Messages;
import org.cloudfoundry.multiapps.controller.core.security.token.TokenService;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.util.ConcurrentReferenceHashMap;

import com.sap.cloudfoundry.client.facade.CloudControllerClient;
import com.sap.cloudfoundry.client.facade.CloudOperationException;

@Named
public class CloudControllerClientProvider {

    @Inject
    private ClientFactory clientFactory;

    @Inject
    private TokenService tokenService;

    // Cached clients. These are stored in memory-sensitive cache, i.e. no OutOfMemory error would
    // occur before GC tries to release the not-used clients.
    private final Map<String, CloudControllerClient> clients = new ConcurrentReferenceHashMap<>();

    /**
     * Returns a client for the specified user name, organization, space and process id by either getting it from the clients cache or
     * creating a new one.
     *
     * @param userName the user name associated with the client
     * @param org the organization associated with the client
     * @param space the space associated with the client
     * @param processId the processId for the client
     * @return a CF client for the specified access token, organization, and space
     */
    public CloudControllerClient getControllerClient(String userName, String org, String space, String processId) {
        try {
            return getClientFromCache(userName, org, space, processId);
        } catch (CloudOperationException e) {
            throw new SLException(e, Messages.CANT_CREATE_CLIENT_2, org, space);
        }
    }

    /**
     * Returns a client for the specified user name, space id and process id by either getting it from the clients cache or creating a new
     * one.
     *
     * @param userName the user name associated with the client
     * @param orgName the organization name associated with the client
     * @param spaceName the space name associated with the client
     * @return a CF client for the specified access token, organization, and space
     */
    public CloudControllerClient getControllerClient(String userName, String orgName, String spaceName) {
        try {
            return getClientFromCache(userName, orgName, spaceName);
        } catch (CloudOperationException e) {
            throw new SLException(e, Messages.CANT_CREATE_CLIENT_2, orgName, spaceName);
        }
    }

    /**
     * Returns a client for the specified user name and space id by either getting it from the clients cache or creating a new one.
     *
     * @param userName the user name associated with the client
     * @param spaceGuid the space guid associated with the client
     * @return a CF client for the specified access token, organization, and space
     */
    public CloudControllerClient getControllerClient(String userName, String spaceGuid) {
        try {
            return getClientFromCache(userName, spaceGuid);
        } catch (CloudOperationException e) {
            throw new SLException(e, Messages.CANT_CREATE_CLIENT_FOR_SPACE_ID, spaceGuid);
        }
    }

    /**
     * Returns a client for the specified user name by creating a new one.
     *
     * @param userName the user name associated with the client
     * @return a CF client for the specified access token, organization, and space
     */
    public CloudControllerClient getControllerClient(String userName) {
        try {
            return clientFactory.createClient(getValidToken(userName));
        } catch (CloudOperationException e) {
            throw new SLException(e, Messages.CANT_CREATE_CLIENT);
        }
    }

    /**
     * Releases the client for the specified user name, organization and space by removing it from the clients cache.
     *
     * @param userName the user name associated with the client
     * @param org the organization associated with the client
     * @param space the space associated with the client
     */
    public void releaseClient(String userName, String org, String space) {
        clients.remove(getKey(userName, org, space));
    }

    /**
     * Releases the client for the specified user name and space id by removing it from the clients cache.
     *
     * @param userName the user name associated with the client
     * @param spaceGuid the space id associated with the client
     */
    public void releaseClient(String userName, String spaceGuid) {
        clients.remove(getKey(userName, spaceGuid));
    }

    private OAuth2AccessToken getValidToken(String userName) {
        OAuth2AccessToken token = tokenService.getToken(userName);
        if (token == null) {
            throw new SLException(Messages.NO_VALID_TOKEN_FOUND, userName);
        }

        if (token.isExpired() && token.getRefreshToken() == null) {
            tokenService.removeToken(token);
            throw new SLException(Messages.TOKEN_EXPIRED, userName);
        }

        return token;
    }

    private CloudControllerClient getClientFromCache(String userName, String org, String space) {
        return getClientFromCache(userName, org, space, null);
    }

    private CloudControllerClient getClientFromCache(String userName, String org, String space, String processId) {
        String key = getKey(userName, org, space);
        CloudControllerClient client = clients.get(key);
        if (client == null) {
            client = clientFactory.createClient(getValidToken(userName), org, space);
            if (processId != null) {
                clients.put(key, client);
            }
        }
        return client;
    }

    private CloudControllerClient getClientFromCache(String userName, String spaceId) {
        String key = getKey(userName, spaceId);
        return clients.computeIfAbsent(key, k -> clientFactory.createClient(getValidToken(userName), spaceId));
    }

    private String getKey(String... args) {
        return String.join("|", args);
    }
}
