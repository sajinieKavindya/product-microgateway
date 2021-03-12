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

package org.wso2am.micro.gw.tests.testCaseBefore;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.am.integration.test.ClientAuthenticator;
import org.wso2.am.integration.test.impl.RestAPIPublisherImpl;
import org.wso2.am.integration.test.impl.RestAPIStoreImpl;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.am.integration.test.utils.bean.DCRParamRequest;
import org.wso2am.micro.gw.tests.common.base.APIMWithMgwBaseTest;
import org.wso2am.micro.gw.tests.context.MicroGWTestException;
import org.wso2am.micro.gw.tests.util.HttpClientRequest;
import org.wso2am.micro.gw.tests.util.URLs;

import java.net.URL;

public class APIManagerInitializeTestCase extends APIMWithMgwBaseTest {

    @BeforeClass(description = "initialise the setup")
    void start() throws Exception {
        URL configToml = getClass().getClassLoader().getResource("apim/config.toml");
        if (configToml == null) {
            throw new MicroGWTestException("Config toml cannot be found. Hence, not starting the API Manager server with Mgw server");
        }
        String configTomlPath = configToml.getPath();
        super.startAPIMWithMGW(configTomlPath, true);
    }

    @Test(description = "Test to check the JWT auth working")
    public void verifyAPIMServerStartup() throws Exception {
        setSSlSystemProperties();
        long waitTime = System.currentTimeMillis() + API_MANAGER_SERVER_STARTUP_TIME;
        //api yaml file should put to the resources/apis/openApis folder
        org.wso2am.micro.gw.tests.util.HttpResponse response;
        boolean exit = true;
        do {
            response = HttpClientRequest.doGet(URLs.getAPIMServiceURLHttp("/services/Version"));
            if (response != null) {
                if (response.getResponseCode() == 200) {
                    exit = false;
                }
            }
        }
        while (exit && waitTime > System.currentTimeMillis());

        Assert.assertNotNull(response);
        Assert.assertEquals(response.getResponseCode(), HttpStatus.SC_SUCCESS,
                            "APIM server not started");
    }

    @Test(alwaysRun = true, dependsOnMethods = "verifyAPIMServerStartup")
    public void testDynamicClientRegistration() throws Exception {
        String dcrURL = URLs.getAPIMServiceURLHttps("/client-registration/v0.17/register");
        //DCR call for publisher app
        DCRParamRequest publisherParamRequest =
                new DCRParamRequest(RestAPIPublisherImpl.appName, RestAPIPublisherImpl.callBackURL,
                                    RestAPIPublisherImpl.tokenScope, RestAPIPublisherImpl.appOwner,
                                    RestAPIPublisherImpl.grantType,
                                    dcrURL, RestAPIPublisherImpl.username, RestAPIPublisherImpl.password,
                                    APIMIntegrationConstants.SUPER_TENANT_DOMAIN);
        ClientAuthenticator.makeDCRRequest(publisherParamRequest);

        //DCR call for dev portal app
        DCRParamRequest devPortalParamRequest =
                new DCRParamRequest(RestAPIStoreImpl.appName, RestAPIStoreImpl.callBackURL,
                                    RestAPIStoreImpl.tokenScope, RestAPIStoreImpl.appOwner, RestAPIStoreImpl.grantType,
                                    dcrURL, RestAPIStoreImpl.username, RestAPIStoreImpl.password,
                                    APIMIntegrationConstants.SUPER_TENANT_DOMAIN);
        ClientAuthenticator.makeDCRRequest(devPortalParamRequest);

        super.init();
        Assert.assertNotNull(restAPIPublisher, "restAPIPublisher");
        Assert.assertNotNull(restAPIStore, "restAPIStore");
    }
}
