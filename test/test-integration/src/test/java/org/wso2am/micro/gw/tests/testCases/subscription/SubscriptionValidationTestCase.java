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
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.am.integration.test.utils.bean.APILifeCycleAction;
import org.wso2.am.integration.test.utils.bean.APIRequest;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2am.micro.gw.mockbackend.ResponseConstants;
import org.wso2am.micro.gw.tests.apim.base.APIMLifecycleBaseTest;
import org.wso2am.micro.gw.tests.context.MicroGWTestException;
import org.wso2am.micro.gw.tests.util.HttpsClientRequest;
import org.wso2am.micro.gw.tests.util.TestConstant;
import org.wso2am.micro.gw.tests.util.URLs;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class SubscriptionValidationTestCase extends APIMLifecycleBaseTest {

    private APIRequest apiRequest;
    private String apiId;
    private String applicationId;
    private Map<String, String> requestHeaders;
    private String endpointURL;

    @BeforeClass(alwaysRun = true, description = "initialise the setup")
    void setEnvironment() throws Exception {
        super.init();

        // Creating the application
        ApplicationCreationResponse appCreationResponse = createApplicationAndClientKeyClientSecret(
                "SubscriptionValidationTestApp", restAPIStore);
        applicationId = appCreationResponse.getApplicationId();

        // create the request headers after generating the access token
        String accessToken = generateUserAccessToken(appCreationResponse.getConsumerKey(),
                                                     appCreationResponse.getConsumerSecret(),
                                                     new String[]{"PRODUCTION"}, user, restAPIStore);
        requestHeaders = new HashMap<>();
        requestHeaders.put(TestConstant.AUTHORIZATION_HEADER, "Bearer " + accessToken);

        // get a predefined api request
        apiRequest = getAPIRequest("SubscriptionValidationTestAPI");
        apiRequest.setProvider(user.getUserName());

        // create and publish the api
        apiId = createAndPublishAPIWithoutRequireReSubscription(apiRequest, restAPIPublisher);

        endpointURL = URLs.getServiceURLHttps("/subscriptionValidationTestAPI/1.0.0/pet/findByStatus");
    }

    @Test(description = "Send a request to a unsubscribed REST API and check if the API invocation is forbidden")
    public void testAPIsForInvalidSubscription() throws MicroGWTestException, MalformedURLException {
        Awaitility.await().pollInterval(2, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).until(
                isResponseAvailable(endpointURL, requestHeaders));

        org.wso2am.micro.gw.tests.util.HttpResponse response;
        try {
            response = HttpsClientRequest.doGet(endpointURL, requestHeaders);
        } catch (IOException e) {
            throw new MicroGWTestException("Error occurred while invoking the endpoint: " + endpointURL, e);
        }
        Assert.assertTrue(response.getResponseCode() == HttpStatus.SC_FORBIDDEN && response.getData()
                                  .contains(TestConstant.RESOURCE_FORBIDDEN_CODE),
                          "The user invoking the API should not be granted access to the required resource. Response "
                                  + "Data:" + response.getData());
    }

    @Test(description = "Send a request to a subscribed REST API returning 200 and check if the expected result is "
            + "received", dependsOnMethods = "testAPIsForInvalidSubscription")
    public void testAPIsForValidSubscription() throws IOException, MicroGWTestException {
        HttpResponse subscriptionResponse = subscribeToAPI(apiId, applicationId,
                                                           TestConstant.SUBSCRIPTION_TIER.UNLIMITED, restAPIStore);

        assertEquals(subscriptionResponse.getResponseCode(), HttpStatus.SC_SUCCESS,
                     "Subscribing to the API request not successful " + getAPIIdentifierStringFromAPIRequest(
                             apiRequest));

        Awaitility.await().pollInterval(2, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).until(
                isResponseAvailable(endpointURL, requestHeaders));

        org.wso2am.micro.gw.tests.util.HttpResponse response = HttpsClientRequest.doGet(endpointURL, requestHeaders);
        Assert.assertNotNull(response, "Error occurred while invoking the endpoint " + endpointURL + ". HttpResponse");
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_SUCCESS,
                            "Valid subscription should be able to invoke the associated API");
        Assert.assertEquals(response.getData(), ResponseConstants.RESPONSE_BODY,
                            "Response message mismatched. Response Data: " + response.getData());
    }

    @Test(groups = {"wso2.am"}, description = "Invocation of the API after being blocked",
          dependsOnMethods = "testAPIsForValidSubscription")
    public void testInvokeAPIAfterChangeAPILifecycleToBlock() throws Exception {
        //Block the API version 1.0.0
        HttpResponse response = restAPIPublisher.changeAPILifeCycleStatus(apiId, APILifeCycleAction.BLOCK.getAction(),
                                                                          null);
        Assert.assertTrue(Objects.nonNull(response), "Could not make the API blocked");
        assertEquals(response.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                     "API publish Response code is invalid " + apiId);
        createAPIRevisionAndDeploy(apiId, restAPIPublisher);
        waitForAPIDeployment();
        Thread.sleep(10 * 1000);

        //Invoke  old version
        org.wso2am.micro.gw.tests.util.HttpResponse oldVersionInvokeResponse = HttpsClientRequest.doGet(
                URLs.getServiceURLHttps(
                        "/subscriptionValidationTestAPI/1.0.0/pet/findByStatus"), requestHeaders);
        assertEquals(oldVersionInvokeResponse.getResponseCode(), HttpStatus.SC_SERVICE_UNAVAILABLE,
                     "Response code mismatched when invoke api after block");
        assertTrue(oldVersionInvokeResponse.getData().contains(TestConstant.API_BLOCKED_CODE),
                   "Response data mismatched when invoke API after block" +
                           " Response Data:" + oldVersionInvokeResponse.getData());
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {
        super.cleanUp();
    }

//    private Callable<Boolean> isSubscriptionInvalid(String URL, Map<String, String> requestHeaders) {
//        return new Callable<Boolean>() {
//            public Boolean call() throws Exception {
//                return checkForInvalidSubscription(URL, requestHeaders);
//            }
//        };
//    }
//
//    private Callable<Boolean> isSubscriptionValid(String URL, Map<String, String> requestHeaders) {
//        return new Callable<Boolean>() {
//            public Boolean call() throws Exception {
//                return checkForValidSubscription(URL, requestHeaders);
//            }
//        };
//    }

    private Callable<Boolean> isResponseAvailable(String URL, Map<String, String> requestHeaders) {
        return new Callable<Boolean>() {
            public Boolean call() {
                return checkForResponse(URL, requestHeaders);
            }
        };
    }

//    private Boolean checkForInvalidSubscription(String URL, Map<String, String> requestHeaders) throws IOException {
//        org.wso2am.micro.gw.tests.util.HttpResponse response = invokeEndpoint(URL, requestHeaders);
//        return Objects.nonNull(response) && response.getResponseCode() == HttpStatus.SC_SUCCESS && response.getData()
//                .contains(TestConstant.RESOURCE_FORBIDDEN_CODE);
//    }
//
//    private Boolean checkForValidSubscription(String URL, Map<String, String> requestHeaders) throws IOException {
//        org.wso2am.micro.gw.tests.util.HttpResponse response = invokeEndpoint(URL, requestHeaders);
//        return Objects.nonNull(response) && response.getResponseCode() == HttpStatus.SC_FORBIDDEN && response.getData()
//                .contains(ResponseConstants.RESPONSE_BODY);
//    }

    private Boolean checkForResponse(String URL, Map<String, String> requestHeaders) {
        org.wso2am.micro.gw.tests.util.HttpResponse response;
        try {
            response = HttpsClientRequest.doGet(URL, requestHeaders);
        } catch (IOException e) {
            return false;
        }
        return Objects.nonNull(response);
    }


//    private org.wso2am.micro.gw.tests.util.HttpResponse invokeEndpoint(String URL, Map<String, String> requestHeaders)
//            throws IOException {
//        return HttpsClientRequest.doGet(URL, requestHeaders);
//    }
}
