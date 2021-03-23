/*
Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2am.micro.gw.tests.context;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpStatus;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.DockerComposeContainer;
import org.wso2am.micro.gw.tests.util.HttpClientRequest;
import org.wso2am.micro.gw.tests.util.HttpResponse;
import org.wso2am.micro.gw.tests.util.URLs;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public abstract class MgwServerImpl implements MgwServer {

    private static final Logger log = LoggerFactory.getLogger(MgwServerImpl.class);
    DockerComposeContainer environment;

    protected File targetClassesDir;
    protected String targetDir;
    protected String mgwTmpServerPath;
    protected String mgwServerPath;

    public MgwServerImpl() throws IOException {
        targetClassesDir = new File(MgwServer.class.getProtectionDomain().getCodeSource().getLocation().getPath());
        targetDir = targetClassesDir.getParentFile().toString();
        mgwTmpServerPath = targetDir + File.separator + "server-tmp";

        final Properties properties = new Properties();
        properties.load(getClass().getClassLoader().getResourceAsStream("project.properties"));
        mgwServerPath = targetDir + File.separator + "micro-gwtmp" + File.separator + "wso2am-micro-gw-" + properties
                .getProperty("version");
    }


    @Override
    public void startMGW() throws IOException {
        try {
            environment.start();
        } catch (Exception e) {
            log.error("Error occurs when docker-compose up");
        }
        Awaitility.await().pollInterval(5, TimeUnit.SECONDS).atMost(250, TimeUnit.SECONDS).until(isBackendAvailable());
        if (!checkForBackendAvailability()) {
            log.error("MockBackend is not started");
        }
    }

    @Override
    public void stopMGW() {
        environment.stop();
    }

    /**
     * wait till the mock backend is available.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    private Callable<Boolean> isBackendAvailable() {
        return new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return checkForBackendAvailability();
            }
        };
//        Map<String, String> headers = new HashMap<String, String>();
//        HttpResponse response;
//
//        int tries = 0;
//        while (true) {
//            response = HttpClientRequest.doGet(URLs.getMockServiceURLHttp(
//                    "/v2/pet/3"), headers);
//            tries += 1;
//            if (response != null) {
//                if (response.getResponseCode() == HttpStatus.SC_OK || tries > 50) {
//                    break;
//                }
//            }
//            TimeUnit.SECONDS.sleep(5);
//        }
    }

    private Boolean checkForBackendAvailability() throws IOException {
        Map<String, String> headers = new HashMap<String, String>();
        HttpResponse response = HttpClientRequest.doGet(URLs.getMockServiceURLHttp(
                "/v2/pet/3"), headers);
        return response != null && response.getResponseCode() == HttpStatus.SC_OK;
    }
}
