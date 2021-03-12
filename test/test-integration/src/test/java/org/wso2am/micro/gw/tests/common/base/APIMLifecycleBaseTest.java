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

import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.am.integration.clients.publisher.api.ApiException;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationDTO;
import org.wso2.am.integration.test.impl.RestAPIPublisherImpl;
import org.wso2.am.integration.test.impl.RestAPIStoreImpl;
import org.wso2.am.integration.test.utils.APIManagerIntegrationTestException;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.am.integration.test.utils.bean.APILifeCycleAction;
import org.wso2.am.integration.test.utils.bean.APILifeCycleState;
import org.wso2.am.integration.test.utils.bean.APIRequest;
import org.wso2.am.integration.test.utils.bean.APIRevisionDeployUndeployRequest;
import org.wso2.am.integration.test.utils.bean.APIRevisionRequest;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2am.micro.gw.tests.common.model.API;
import org.wso2am.micro.gw.tests.context.MicroGWTestException;

import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response;
import javax.xml.xpath.XPathExpressionException;

import static org.testng.Assert.assertEquals;

public class APIMLifecycleBaseTest extends APIMWithMgwBaseTest {
    private static final Logger log = LoggerFactory.getLogger(APIMLifecycleBaseTest.class);

    /**
     * @param appName         - Name of the application
     * @param description     - Description of the application
     * @param throttleTier    - Throttle tier
     * @param tokenType       - Type of the token
     * @param storeRestClient - Instance of the RestAPIStoreImpl class
     * @return Http response of the application creation
     * @throws MicroGWTestException if there is an error in application creation
     */
    public HttpResponse createApplication(String appName, String description, String throttleTier,
                                          ApplicationDTO.TokenTypeEnum tokenType, RestAPIStoreImpl storeRestClient)
            throws MicroGWTestException {
        HttpResponse applicationResponse = storeRestClient.createApplication(appName, description, throttleTier,
                                                                             tokenType);
        if (applicationResponse == null || applicationResponse.getResponseCode() != HTTP_RESPONSE_CODE_OK) {
            throw new MicroGWTestException("Error in Application creation. App name: " + appName);
        }
        return applicationResponse;
    }

    /**
     * @param apiRequest          - Constructed API request object
     * @param publisherRestClient - Instance of RestAPIPublisherImpl
     * @return http response object of the api creation
     * @throws MicroGWTestException if there is an error in api creation
     * @throws ApiException         Exception throws by the method call of addAPI() in RestAPIPublisherImpl.java.
     */
    public HttpResponse addAPI(APIRequest apiRequest, RestAPIPublisherImpl publisherRestClient)
            throws MicroGWTestException, ApiException {
        HttpResponse apiCreationResponse = publisherRestClient.addAPI(apiRequest);
        if (apiCreationResponse == null || apiCreationResponse.getResponseCode() != HTTP_RESPONSE_CODE_OK) {
            throw new MicroGWTestException(
                    "Error is API creation. " + getAPIIdentifierStringFromAPIRequest(apiRequest));
        }
        return apiCreationResponse;
    }

    /**
     * Delete a API from API Publisher.
     *
     * @param apiIdentifier       - Instance of APIIdentifier object that include the API Name, API Version and API
     *                            Provider
     * @param publisherRestClient - Instance of RestAPIPublisherImpl.
     * @throws MicroGWTestException - if there is an error in api deletion
     * @throws ApiException         Exception throws by the method call of deleteApi() in RestAPIPublisherImpl.java
     */
    protected void deleteAPI(String apiID, API apiIdentifier, RestAPIPublisherImpl publisherRestClient)
            throws MicroGWTestException, ApiException {

        HttpResponse deleteHTTPResponse = publisherRestClient.deleteAPI(apiID);
        if (deleteHTTPResponse != null && !(deleteHTTPResponse.getResponseCode() == HTTP_RESPONSE_CODE_OK)) {
            throw new MicroGWTestException("Error in API Deletion." +
                                                   getAPIIdentifierString(apiIdentifier) + " API Context :"
                                                   + deleteHTTPResponse +
                                                   "Response Code:" + deleteHTTPResponse.getResponseCode() +
                                                   " Response Data :" + deleteHTTPResponse.getData());
        }

    }

    /**
     * Publish an API using REST.
     *
     * @param apiId                   - UUID of the API,
     * @param publisherRestClient     - Instance of APIPublisherRestClient
     * @param isRequireReSubscription - If publish with re-subscription required option true else false.
     * @return HttpResponse - Response of the API Publishing activity
     * @throws APIManagerIntegrationTestException -  Exception throws by the method call of
     * changeAPILifeCycleStatusToPublish()
     *                                            in APIPublisherRestClient.java.
     */
    protected HttpResponse publishAPI(String apiId, RestAPIPublisherImpl publisherRestClient,
                                      boolean isRequireReSubscription) throws ApiException {
        String lifecycleChecklist = null;
        if (isRequireReSubscription) {
            lifecycleChecklist = "Requires re-subscription when publishing the API:true";
        }
        return publisherRestClient
                .changeAPILifeCycleStatus(apiId, APILifeCycleAction.PUBLISH.getAction(), lifecycleChecklist);


    }

    /**
     * Subscribe an API.
     *
     * @param apiId           - UUID of the API
     * @param applicationId   - UUID of the application
     * @param storeRestClient - Instance of APIPublisherRestClient
     * @return HttpResponse - Response of the API subscribe action
     */
    protected HttpResponse subscribeToAPI(String apiId, String applicationId, String tier,
                                          RestAPIStoreImpl storeRestClient) {
        return storeRestClient.createSubscription(apiId, applicationId, tier);
    }

    /**
     * Create and publish a API.
     *
     * @param apiRequest              - Instance of APIRequest
     * @param publisherRestClient     - Instance of RestAPIPublisherImpl
     * @param isRequireReSubscription - If publish with re-subscription required option true else false.
     * @throws MicroGWTestException - Exception throws by API create and publish activities.
     */
    protected String createAndPublishAPI(APIRequest apiRequest,
                                         RestAPIPublisherImpl publisherRestClient,
                                         boolean isRequireReSubscription)
            throws MicroGWTestException, ApiException {
        //Create the API
        HttpResponse createAPIResponse = publisherRestClient.addAPI(apiRequest);
        if (createAPIResponse.getResponseCode() == HTTP_RESPONSE_CODE_CREATED && !StringUtils.isEmpty(
                createAPIResponse.getData())) {
            log.info("API Created :" + getAPIIdentifierStringFromAPIRequest(apiRequest));
            // Create Revision and Deploy to Gateway
            try {
                createAPIRevisionAndDeploy(createAPIResponse.getData(), publisherRestClient);
            } catch (JSONException e) {
                throw new MicroGWTestException("Error in creating and deploying API Revision", e);
            }
            //Publish the API
            HttpResponse publishAPIResponse = publishAPI(createAPIResponse.getData(), publisherRestClient,
                                                         isRequireReSubscription);
            if (!(publishAPIResponse.getResponseCode() == HTTP_RESPONSE_CODE_OK &&
                    APILifeCycleState.PUBLISHED.getState().equals(publishAPIResponse.getData()))) {
                throw new MicroGWTestException("Error in API Publishing" +
                                                       getAPIIdentifierStringFromAPIRequest(apiRequest)
                                                       + "Response Code:" + publishAPIResponse
                        .getResponseCode() +
                                                       " Response Data :" + publishAPIResponse.getData());
            }
            log.info("API Published :" + getAPIIdentifierStringFromAPIRequest(apiRequest));
            return createAPIResponse.getData();
        } else {
            throw new MicroGWTestException("Error in API Creation." +
                                                   getAPIIdentifierStringFromAPIRequest(apiRequest) +
                                                   "Response Code:" + createAPIResponse.getResponseCode()
                                                   +
                                                   " Response Data :" + createAPIResponse.getData());
        }
    }

    /**
     * Create and publish a API with re-subscription not required.
     *
     * @param apiRequest          - Instance of APIRequest
     * @param publisherRestClient - Instance of RestAPIPublisherImpl
     * @throws MicroGWTestException - Exception throws by API create and publish activities.
     */
    protected String createAndPublishAPIWithoutRequireReSubscription(APIRequest apiRequest,
                                                                     RestAPIPublisherImpl publisherRestClient)
            throws MicroGWTestException, ApiException {
        return createAndPublishAPI(apiRequest, publisherRestClient, false);
    }

    /**
     * @param apiID               - API id.
     * @param newAPIVersion       - New API version need to create
     * @param publisherRestClient - Instance of RestAPIPublisherImpl
     * @throws ApiException Exception throws by the method call of copyAPIWithReturnDTO() in RestAPIPublisherImpl.java
     */
    protected APIDTO copyAPI(String apiID, String newAPIVersion, RestAPIPublisherImpl publisherRestClient)
            throws ApiException {
        //Copy API to version  to newVersion
        return publisherRestClient.copyAPIWithReturnDTO(newAPIVersion, apiID, false);
    }

    /**
     * Create publish and subscribe a API using REST API.
     *
     * @param apiRequest          - Instance of APIRequest with all needed API information
     * @param publisherRestClient -  Instance of APIPublisherRestClient
     * @param storeRestClient     - Instance of APIStoreRestClient
     * @param applicationId       - UUID of the Application that the API need to subscribe.
     * @param tier                - Tier that needs to be subscribed.
     * @throws MicroGWTestException - Exception throws by API create publish and subscribe a API activities.
     */
    protected String createPublishAndSubscribeToAPI(APIRequest apiRequest,
                                                    RestAPIPublisherImpl publisherRestClient,
                                                    RestAPIStoreImpl storeRestClient, String applicationId,
                                                    String tier)
            throws MicroGWTestException, ApiException {
        String apiId = createAndPublishAPI(apiRequest, publisherRestClient, false);
        waitForAPIDeploymentSync(user.getUserName(), apiRequest.getName(), apiRequest.getVersion(),
                                 APIMIntegrationConstants.IS_API_EXISTS);
        HttpResponse httpResponseSubscribeAPI = subscribeToAPI(apiId, applicationId, tier, storeRestClient);
        if (!(httpResponseSubscribeAPI.getResponseCode() == HTTP_RESPONSE_CODE_OK &&
                !StringUtils.isEmpty(httpResponseSubscribeAPI.getData()))) {
            throw new MicroGWTestException("Error in API Subscribe." +
                                                   getAPIIdentifierStringFromAPIRequest(apiRequest) +
                                                   "Response Code:" + httpResponseSubscribeAPI
                    .getResponseCode());
        }
        log.info("API Subscribed :" + getAPIIdentifierStringFromAPIRequest(apiRequest));
        return apiId;
    }

    /**
     * Create API Revision and Deploy to gateway using REST API.
     *
     * @param apiId            -  API UUID
     * @param restAPIPublisher -  Instance of APIPublisherRestClient
     */
    protected String createAPIRevisionAndDeploy(String apiId, RestAPIPublisherImpl restAPIPublisher)
            throws ApiException, JSONException {
        int HTTP_RESPONSE_CODE_OK = Response.Status.OK.getStatusCode();
        int HTTP_RESPONSE_CODE_CREATED = Response.Status.CREATED.getStatusCode();
        String revisionUUID = null;
        //Add the API Revision using the API publisher.
        APIRevisionRequest apiRevisionRequest = new APIRevisionRequest();
        apiRevisionRequest.setApiUUID(apiId);
        apiRevisionRequest.setDescription("Test Revision 1");

        HttpResponse apiRevisionResponse = restAPIPublisher.addAPIRevision(apiRevisionRequest);

        assertEquals(apiRevisionResponse.getResponseCode(), HTTP_RESPONSE_CODE_CREATED,
                     "Create API Response Code is invalid." + apiRevisionResponse.getData());

        // Retrieve Revision Info
        HttpResponse apiRevisionsGetResponse = restAPIPublisher.getAPIRevisions(apiId, null);
        assertEquals(apiRevisionsGetResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                     "Unable to retrieve revisions" + apiRevisionsGetResponse.getData());
        List<JSONObject> revisionList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(apiRevisionsGetResponse.getData());

        JSONArray arrayList = jsonObject.getJSONArray("list");
        for (int i = 0, l = arrayList.length(); i < l; i++) {
            revisionList.add(arrayList.getJSONObject(i));
        }
        for (JSONObject revision : revisionList) {
            revisionUUID = revision.getString("id");
        }

        // Deploy Revision to gateway
        List<APIRevisionDeployUndeployRequest> apiRevisionDeployRequestList = new ArrayList<>();
        APIRevisionDeployUndeployRequest apiRevisionDeployRequest = new APIRevisionDeployUndeployRequest();
        apiRevisionDeployRequest.setName("Production and Sandbox");
        apiRevisionDeployRequest.setDisplayOnDevportal(true);
        apiRevisionDeployRequestList.add(apiRevisionDeployRequest);
        HttpResponse apiRevisionsDeployResponse = restAPIPublisher.deployAPIRevision(apiId, revisionUUID,
                                                                                     apiRevisionDeployRequestList);
        assertEquals(apiRevisionsDeployResponse.getResponseCode(), HTTP_RESPONSE_CODE_CREATED,
                     "Unable to deploy API Revisions:" + apiRevisionsDeployResponse.getData());
        return revisionUUID;
    }

    /**
     * Undeploy and Delete API Revisions using REST API.
     *
     * @param apiId            - API UUID
     * @param restAPIPublisher -  Instance of APIPublisherRestClient
     */
    protected String undeployAndDeleteAPIRevisions(String apiId, RestAPIPublisherImpl restAPIPublisher)
            throws ApiException, JSONException, XPathExpressionException, APIManagerIntegrationTestException {
        int HTTP_RESPONSE_CODE_OK = Response.Status.OK.getStatusCode();
        int HTTP_RESPONSE_CODE_CREATED = Response.Status.CREATED.getStatusCode();
        String revisionUUID = null;

        // Get Deployed Revisions
        HttpResponse apiRevisionsGetResponse = restAPIPublisher.getAPIRevisions(apiId, "deployed:true");
        assertEquals(apiRevisionsGetResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                     "Unable to retrieve revisions" + apiRevisionsGetResponse.getData());
        List<JSONObject> revisionList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(apiRevisionsGetResponse.getData());

        JSONArray arrayList = jsonObject.getJSONArray("list");
        for (int i = 0, l = arrayList.length(); i < l; i++) {
            revisionList.add(arrayList.getJSONObject(i));
        }
        for (JSONObject revision : revisionList) {
            revisionUUID = revision.getString("id");
        }

        if (revisionUUID == null) {
            return null;
        }

        // Undeploy Revisions
        List<APIRevisionDeployUndeployRequest> apiRevisionUndeployRequestList = new ArrayList<>();
        APIRevisionDeployUndeployRequest apiRevisionUnDeployRequest = new APIRevisionDeployUndeployRequest();
        apiRevisionUnDeployRequest.setName("Production and Sandbox");
        apiRevisionUnDeployRequest.setDisplayOnDevportal(true);
        apiRevisionUndeployRequestList.add(apiRevisionUnDeployRequest);
        HttpResponse apiRevisionsUnDeployResponse = restAPIPublisher.undeployAPIRevision(apiId, revisionUUID,
                                                                                         apiRevisionUndeployRequestList);
        assertEquals(apiRevisionsUnDeployResponse.getResponseCode(), HTTP_RESPONSE_CODE_CREATED,
                     "Unable to Undeploy API Revisions:" + apiRevisionsUnDeployResponse.getData());

        // Get Revisions
        HttpResponse apiRevisionsFullGetResponse = restAPIPublisher.getAPIRevisions(apiId, null);
        assertEquals(apiRevisionsFullGetResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                     "Unable to retrieve revisions" + apiRevisionsFullGetResponse.getData());
        List<JSONObject> revisionFullList = new ArrayList<>();
        JSONObject jsonFullObject = new JSONObject(apiRevisionsFullGetResponse.getData());

        JSONArray arrayFullList = jsonFullObject.getJSONArray("list");
        for (int i = 0, l = arrayFullList.length(); i < l; i++) {
            revisionFullList.add(arrayFullList.getJSONObject(i));
        }
        for (JSONObject revision : revisionFullList) {
            revisionUUID = revision.getString("id");
            HttpResponse apiRevisionsDeleteResponse = restAPIPublisher.deleteAPIRevision(apiId, revisionUUID);
            assertEquals(apiRevisionsDeleteResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                         "Unable to delete API Revisions:" + apiRevisionsDeleteResponse.getData());
        }

        //Waiting for API un-deployment
        HttpResponse response = restAPIPublisher.getAPI(apiId);
        Gson g = new Gson();
        APIDTO apiDto = g.fromJson(response.getData(), APIDTO.class);
        waitForAPIDeploymentSync(user.getUserName(), apiDto.getName(), apiDto.getVersion(),
                                 APIMIntegrationConstants.IS_API_NOT_EXISTS);

        return revisionUUID;
    }

    /**
     * Create API Product Revision and Deploy to gateway using REST API.
     *
     * @param apiId            - API UUID
     * @param restAPIPublisher - Instance of APIPublisherRestClient
     */
    protected String createAPIProductRevisionAndDeploy(String apiId, RestAPIPublisherImpl restAPIPublisher)
            throws ApiException, JSONException {
        int HTTP_RESPONSE_CODE_OK = Response.Status.OK.getStatusCode();
        int HTTP_RESPONSE_CODE_CREATED = Response.Status.CREATED.getStatusCode();
        String revisionUUID = null;
        //Add the API Revision using the API publisher.
        APIRevisionRequest apiRevisionRequest = new APIRevisionRequest();
        apiRevisionRequest.setApiUUID(apiId);
        apiRevisionRequest.setDescription("Test Revision 1");

        HttpResponse apiRevisionResponse = restAPIPublisher.addAPIProductRevision(apiRevisionRequest);

        assertEquals(apiRevisionResponse.getResponseCode(), HTTP_RESPONSE_CODE_CREATED,
                     "Create API Response Code is invalid." + apiRevisionResponse.getData());

        // Retrieve Revision Info
        HttpResponse apiRevisionsGetResponse = restAPIPublisher.getAPIRevisions(apiId, null);
        assertEquals(apiRevisionsGetResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                     "Unable to retrieve revisions" + apiRevisionsGetResponse.getData());
        List<JSONObject> revisionList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(apiRevisionsGetResponse.getData());

        JSONArray arrayList = jsonObject.getJSONArray("list");
        for (int i = 0, l = arrayList.length(); i < l; i++) {
            revisionList.add(arrayList.getJSONObject(i));
        }
        for (JSONObject revision : revisionList) {
            revisionUUID = revision.getString("id");
        }

        // Deploy Revision to gateway
        List<APIRevisionDeployUndeployRequest> apiRevisionDeployRequestList = new ArrayList<>();
        APIRevisionDeployUndeployRequest apiRevisionDeployRequest = new APIRevisionDeployUndeployRequest();
        apiRevisionDeployRequest.setName("Production and Sandbox");
        apiRevisionDeployRequest.setDisplayOnDevportal(true);
        apiRevisionDeployRequestList.add(apiRevisionDeployRequest);
        HttpResponse apiRevisionsDeployResponse = restAPIPublisher.deployAPIProductRevision(apiId, revisionUUID,
                                                                                            apiRevisionDeployRequestList);
        assertEquals(apiRevisionsDeployResponse.getResponseCode(), HTTP_RESPONSE_CODE_CREATED,
                     "Unable to deploy API Product Revisions:" + apiRevisionsDeployResponse.getData());
        //Waiting for API deployment
        waitForAPIDeployment();
        return revisionUUID;
    }


    /**
     * Undeploy and Delete API Product Revisions using REST API.
     *
     * @param apiId            - API UUID
     * @param restAPIPublisher -  Instance of APIPublisherRestClient
     */
    protected String undeployAndDeleteAPIProductRevisions(String apiId, RestAPIPublisherImpl restAPIPublisher)
            throws ApiException, JSONException, XPathExpressionException, APIManagerIntegrationTestException {
        int HTTP_RESPONSE_CODE_OK = Response.Status.OK.getStatusCode();
        int HTTP_RESPONSE_CODE_CREATED = Response.Status.CREATED.getStatusCode();
        String revisionUUID = null;

        // Get Deployed Revisions
        HttpResponse apiRevisionsGetResponse = restAPIPublisher.getAPIProductRevisions(apiId, "deployed:true");
        assertEquals(apiRevisionsGetResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                     "Unable to retrieve revisions" + apiRevisionsGetResponse.getData());
        List<JSONObject> revisionList = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(apiRevisionsGetResponse.getData());

        JSONArray arrayList = jsonObject.getJSONArray("list");
        for (int i = 0, l = arrayList.length(); i < l; i++) {
            revisionList.add(arrayList.getJSONObject(i));
        }
        for (JSONObject revision : revisionList) {
            revisionUUID = revision.getString("id");
        }

        if (revisionUUID == null) {
            return null;
        }

        // Un deploy Revisions
        List<APIRevisionDeployUndeployRequest> apiRevisionUndeployRequestList = new ArrayList<>();
        APIRevisionDeployUndeployRequest apiRevisionUnDeployRequest = new APIRevisionDeployUndeployRequest();
        apiRevisionUnDeployRequest.setName("Production and Sandbox");
        apiRevisionUnDeployRequest.setDisplayOnDevportal(true);
        apiRevisionUndeployRequestList.add(apiRevisionUnDeployRequest);
        HttpResponse apiRevisionsUnDeployResponse = restAPIPublisher.undeployAPIProductRevision(apiId, revisionUUID,
                                                                                                apiRevisionUndeployRequestList);
        assertEquals(apiRevisionsUnDeployResponse.getResponseCode(), HTTP_RESPONSE_CODE_CREATED,
                     "Unable to Undeploy API Product Revisions:" + apiRevisionsUnDeployResponse.getData());

        // Get Revisions
        HttpResponse apiRevisionsFullGetResponse = restAPIPublisher.getAPIProductRevisions(apiId, null);
        assertEquals(apiRevisionsFullGetResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                     "Unable to retrieve revisions" + apiRevisionsFullGetResponse.getData());
        List<JSONObject> revisionFullList = new ArrayList<>();
        JSONObject jsonFullObject = new JSONObject(apiRevisionsFullGetResponse.getData());

        JSONArray arrayFullList = jsonFullObject.getJSONArray("list");
        for (int i = 0, l = arrayFullList.length(); i < l; i++) {
            revisionFullList.add(arrayFullList.getJSONObject(i));
        }
        for (JSONObject revision : revisionFullList) {
            revisionUUID = revision.getString("id");
            HttpResponse apiRevisionsDeleteResponse = restAPIPublisher.deleteAPIProductRevision(apiId, revisionUUID);
            assertEquals(apiRevisionsDeleteResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                         "Unable to delete API Product Revisions:" + apiRevisionsDeleteResponse.getData());
        }

        //Waiting for API un-deployment
        waitForAPIDeployment();
        return revisionUUID;
    }

    /**
     * Return a String with combining the value of API Name, API Version, and API Provider Name as key:value format
     *
     * @param api - Instance of API object that includes the API Name, API Version, and API Provider Name to create the
     *            String
     * @return String - with API Name,API Version and API Provider Name as key:value format
     */
    private String getAPIIdentifierString(API api) {
        return " API Name:" + api.getName() + ", API Version:" + api.getVersion() +
                ", API Provider Name :" + api.getProvider() + " ";

    }

    /**
     * Return a String with combining the value of API Name, API Version, and API Provider Name as key:value format.
     *
     * @param apiRequest - Instance of APIRequest object  that include the  API Name,API Version and API Provider Name
     *                   to create the String
     * @return String - with API Name, API Version, and API Provider Name as key:value format
     */
    protected String getAPIIdentifierStringFromAPIRequest(APIRequest apiRequest) {
        return " API Name:" + apiRequest.getName() + " API Version:" + apiRequest.getVersion() +
                " API Provider Name :" + apiRequest.getProvider() + " ";

    }

}
