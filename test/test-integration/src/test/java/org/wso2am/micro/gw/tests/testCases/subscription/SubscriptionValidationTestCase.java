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

package org.wso2am.micro.gw.tests.testCases.subscription;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.am.integration.clients.publisher.api.ApiException;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIOperationsDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationKeyDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationKeyGenerateRequestDTO;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.am.integration.test.utils.bean.APIRequest;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2am.micro.gw.mockbackend.ResponseConstants;
import org.wso2am.micro.gw.tests.common.base.APIMLifecycleBaseTest;
import org.wso2am.micro.gw.tests.context.MicroGWTestException;
import org.wso2am.micro.gw.tests.util.HttpsClientRequest;
import org.wso2am.micro.gw.tests.util.TestGroup;
import org.wso2am.micro.gw.tests.util.URLs;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

@Test(groups = {TestGroup.MGW_WITH_BACKEND_TLS_AND_API})
public class SubscriptionValidationTestCase extends APIMLifecycleBaseTest {

    private final String UNLIMITED = "Unlimited";
    private APIRequest apiRequest;
    private String apiId;
    private String applicationId;
    private Map<String, String> requestHeaders;

    @BeforeClass(alwaysRun = true, description = "initialise the setup")
    void setEnvironment() throws Exception {
        super.init();
        // Creating the application
        String APPLICATION_NAME = "SubscriptionValidationTestCase";
        HttpResponse applicationResponse = restAPIStore.createApplication(APPLICATION_NAME,
                                                                          "Test Application for "
                                                                                  + "SubscriptionValidationTestCase",
                                                                          UNLIMITED, ApplicationDTO.TokenTypeEnum.JWT);
        applicationId = applicationResponse.getData();

        ArrayList<String> grantTypes = new ArrayList<>();
        grantTypes.add(APIMIntegrationConstants.GRANT_TYPE.PASSWORD);
        grantTypes.add(APIMIntegrationConstants.GRANT_TYPE.CLIENT_CREDENTIAL);
        ApplicationKeyDTO applicationKeyDTO = restAPIStore.generateKeys(applicationId, "36000", "",
                                                                        ApplicationKeyGenerateRequestDTO.KeyTypeEnum.PRODUCTION,
                                                                        null, grantTypes);

        // get Consumer Key and Consumer Secret
        String consumerKey = applicationKeyDTO.getConsumerKey();
        String consumerSecret = applicationKeyDTO.getConsumerSecret();

        //Obtain user access token
        String requestBody = "grant_type=password&username=" + user.getUserName() + "&password=" + user.getPassword() +
                "&scope=PRODUCTION";
        URL tokenEndpointURL = new URL(URLs.getAPIMServiceURLHttps("/oauth2/token"));

        JSONObject accessTokenGenerationResponse = new JSONObject(
                restAPIStore.generateUserAccessKey(consumerKey, consumerSecret, requestBody, tokenEndpointURL)
                        .getData());
        String accessToken = accessTokenGenerationResponse.getString("access_token");
        requestHeaders = new HashMap<>();
        requestHeaders.put("Authorization", "Bearer " + accessToken);

        // Create the API request
        String API_NAME = "SubscriptionValidationTestAPI";
        String API_CONTEXT = "subscriptionValidationTestAPI";
        String API_END_POINT_POSTFIX_URL = "/v2";
        apiRequest = new APIRequest(API_NAME, API_CONTEXT,
                                    new URL(URLs.getMockServiceURLHttp(API_END_POINT_POSTFIX_URL)));
        String API_VERSION_1_0_0 = "1.0.0";
        apiRequest.setVersion(API_VERSION_1_0_0);
        apiRequest.setTiersCollection(UNLIMITED);
        apiRequest.setTier(UNLIMITED);
        apiRequest.setProvider(user.getUserName());

        APIOperationsDTO apiOperationsDTO1 = new APIOperationsDTO();
        apiOperationsDTO1.setVerb("GET");
        String API_END_POINT_METHOD = "/pet/findByStatus";
        apiOperationsDTO1.setTarget(API_END_POINT_METHOD);
        apiOperationsDTO1.setThrottlingPolicy(UNLIMITED);

        List<APIOperationsDTO> operationsDTOS = new ArrayList<>();
        operationsDTOS.add(apiOperationsDTO1);
        apiRequest.setOperationsDTOS(operationsDTOS);
    }

    @Test(description = "Send a request to a unsubscribed REST API and check if the API invocation is forbidden")
    public void testAPIsForInvalidSubscription()
            throws ApiException, IOException, MicroGWTestException {
        apiId = createAndPublishAPIWithoutRequireReSubscription(apiRequest, restAPIPublisher);

        org.wso2am.micro.gw.tests.util.HttpResponse response = HttpsClientRequest.doGet(URLs.getServiceURLHttps(
                "/subscriptionValidationTestAPI/1.0.0/pet/findByStatus"), requestHeaders);

        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_FORBIDDEN,
                            "API can be invoked by an invalid subscription");
    }

    @Test(description = "Send a request to a subscribed REST API returning 200 and check if the expected result is "
            + "received")
    public void testAPIsForValidSubscription() throws IOException {

        HttpResponse subscriptionResponse = subscribeToAPI(apiId, applicationId, UNLIMITED, restAPIStore);

        assertEquals(subscriptionResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                     "Subscribing to the API request not successful " +
                             getAPIIdentifierStringFromAPIRequest(apiRequest));

        org.wso2am.micro.gw.tests.util.HttpResponse response = HttpsClientRequest.doGet(URLs.getServiceURLHttps(
                "/subscriptionValidationTestAPI/1.0.0/pet/findByStatus"), requestHeaders);

        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_SUCCESS,
                            "API cannot be invoked by a valid subscription");
        Assert.assertEquals(response.getData(), ResponseConstants.RESPONSE_BODY,
                            "Response message mismatched");
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        super.cleanUp();
    }
}
