/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2am.micro.gw.tests.common.base;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIInfoDTO;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIListDTO;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIProductInfoDTO;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIProductListDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationInfoDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationListDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.SubscriptionDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.SubscriptionListDTO;
import org.wso2.am.integration.test.impl.RestAPIPublisherImpl;
import org.wso2.am.integration.test.impl.RestAPIStoreImpl;
import org.wso2.am.integration.test.utils.APIManagerIntegrationTestException;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.am.integration.test.utils.http.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2am.micro.gw.tests.common.BaseTestCase;
import org.wso2am.micro.gw.tests.common.model.TestUser;
import org.wso2am.micro.gw.tests.context.APIManagerWithMgwServerInstance;
import org.wso2am.micro.gw.tests.context.MicroGWTestException;
import org.wso2am.micro.gw.tests.util.URLs;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;

public class APIMWithMgwBaseTest extends BaseTestCase {
    private static final Logger log = LoggerFactory.getLogger(APIMWithMgwBaseTest.class);
    private static APIManagerWithMgwServerInstance apiManagerWithMgwServerInstance;

    protected static final int HTTP_RESPONSE_CODE_OK = Response.Status.OK.getStatusCode();
    static final int HTTP_RESPONSE_CODE_CREATED = Response.Status.CREATED.getStatusCode();
    private static final long WAIT_TIME = 60 * 1000;
    protected static final long API_MANAGER_SERVER_STARTUP_TIME = 60 * 3 * 1000;

    protected RestAPIStoreImpl restAPIStore;
    protected RestAPIPublisherImpl restAPIPublisher;
    protected TestUser user;

    protected void init() throws MalformedURLException {
        user = new TestUser("admin", "admin");
        init(user);
    }

    protected void init(TestUser user) throws MalformedURLException {
        restAPIPublisher = new RestAPIPublisherImpl(user.getUserNameWithoutDomain(), user.getPassword(),
                                                    user.getUserDomain(), URLs.getAPIMServiceURLHttps("/"));
        restAPIStore = new RestAPIStoreImpl(user.getUserNameWithoutDomain(), user.getPassword(),
                                            user.getUserDomain(), URLs.getAPIMServiceURLHttps("/"));
    }

    /**
     * start the mgw docker environment and mock backend.
     *
     * @param confPath   - external conf.toml file location
     * @param tlsEnabled - true if the tls based backend server is required additionally
     * @throws MicroGWTestException
     * @throws IOException
     * @throws InterruptedException
     */
    protected void startAPIMWithMGW(String confPath, boolean tlsEnabled) throws MicroGWTestException, IOException,
                                                                                InterruptedException {
        apiManagerWithMgwServerInstance = new APIManagerWithMgwServerInstance(confPath, tlsEnabled);
        apiManagerWithMgwServerInstance.startMGW();
    }

    protected void stopAPIMWithMGW() {
        apiManagerWithMgwServerInstance.stopMGW();
    }


    /**
     * Helper method to set the SSL context.
     */
    protected void setSSlSystemProperties() {
        URL certificatesTrustStore = getClass().getClassLoader()
                .getResource("keystore/client-truststore.jks");
        if (certificatesTrustStore != null) {
            System.setProperty("javax.net.ssl.trustStore", certificatesTrustStore.getPath());
        } else {
            log.error("Truststore is not set.");
        }
        System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon");
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");
    }

    /**
     * This method can be used to wait for API deployment sync.
     */
    void waitForAPIDeployment() {
        try {
            Thread.sleep(15000);
        } catch (InterruptedException ignored) {

        }
    }

    /**
     * This method can be used to wait for API deployment sync in distributed and clustered environment APIStatusMonitor
     * will be invoked to get API related data and then verify that data matches with expected response provided.
     *
     * @param apiProvider      - Provider of the API
     * @param apiName          - API name
     * @param apiVersion       - API version
     * @param expectedResponse - Expected response
     */
    protected void waitForAPIDeploymentSync(String apiProvider, String apiName, String apiVersion,
                                            String expectedResponse) {

        long currentTime = System.currentTimeMillis();
        long waitTime = currentTime + WAIT_TIME;
        String colonSeparatedHeader = "admin:admin";
        ;
        if (user != null) {
            colonSeparatedHeader = user.getUserNameWithoutDomain() + ":" + user.getPassword();
        }

        String authorizationHeader = "Basic " + new String(Base64.encodeBase64(colonSeparatedHeader.getBytes()));
        Map<String, String> headerMap = new HashMap();
        headerMap.put("Authorization", authorizationHeader);
        String tenantIdentifier = getTenantIdentifier();

        while (waitTime > System.currentTimeMillis()) {
            HttpResponse response = null;
            try {
                response = HttpRequestUtil.doGet(URLs.getAPIMServiceURLHttp(
                        "/APIStatusMonitor/apiInformation/api/" + tenantIdentifier + apiName + "/" + apiVersion),
                                                 headerMap);
            } catch (IOException ignored) {
                log.warn("WebAPP:" + " APIStatusMonitor not yet deployed or" + " API :" + apiName + " not yet " +
                                 "deployed " + " with provider: " + apiProvider);
            }

            log.info("WAIT for availability of API: " + apiName + " with version: " + apiVersion
                             + " with provider: " + apiProvider + " with Tenant Identifier: " + tenantIdentifier
                             + " with expected response : " + expectedResponse);

            if (response != null) {
                log.info("Data: " + response.getData());
                if (response.getData().contains(expectedResponse)) {
                    log.info("API :" + apiName + " with version: " + apiVersion +
                                     " with expected response " + expectedResponse + " found");
                    break;
                } else {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {

                    }
                }
            }
        }
    }

    /**
     * This returns "tenatDomain/tenantId/" string.
     */
    private String getTenantIdentifier() {
        int tenantId = -1234;
        String providerTenantDomain = "carbon.super";
        return providerTenantDomain + "/" + tenantId + "/";
    }

    /**
     * Cleaning up the API manager by removing all APIs and applications other than default application
     *
     * @throws Exception - occurred when calling the apis
     */
    protected void cleanUp() throws Exception {

        ApplicationListDTO applicationListDTO = restAPIStore.getAllApps();
        if (applicationListDTO.getList() != null) {
            for (ApplicationInfoDTO applicationInfoDTO : applicationListDTO.getList()) {
                SubscriptionListDTO subsDTO = restAPIStore
                        .getAllSubscriptionsOfApplication(applicationInfoDTO.getApplicationId());
                if (subsDTO != null && subsDTO.getList() != null) {
                    for (SubscriptionDTO subscriptionDTO : subsDTO.getList()) {
                        restAPIStore.removeSubscription(subscriptionDTO.getSubscriptionId());
                    }
                }
                if (!APIMIntegrationConstants.OAUTH_DEFAULT_APPLICATION_NAME.equals(applicationInfoDTO.getName())) {
                    restAPIStore.deleteApplication(applicationInfoDTO.getApplicationId());
                }
            }
        }

        APIProductListDTO allApiProducts = restAPIPublisher.getAllApiProducts();
        List<APIProductInfoDTO> apiProductListDTO = allApiProducts.getList();

        if (apiProductListDTO != null) {
            for (APIProductInfoDTO apiProductInfoDTO : apiProductListDTO) {
                restAPIPublisher.deleteApiProduct(apiProductInfoDTO.getId());
            }
        }

        APIListDTO apiListDTO = restAPIPublisher.getAllAPIs();
        if (apiListDTO != null && apiListDTO.getList() != null) {
            for (APIInfoDTO apiInfoDTO : apiListDTO.getList()) {
                restAPIPublisher.deleteAPI(apiInfoDTO.getId());
            }
        }
    }
}
