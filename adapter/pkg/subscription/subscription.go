/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package subscription

import (
	"crypto/tls"
	b64 "encoding/base64"
	"encoding/json"
	logger "github.com/sirupsen/logrus"
	"github.com/wso2/micro-gw/config"
	"github.com/wso2/micro-gw/dto"
	"io/ioutil"
	"net/http"
	"reflect"
)

const (
	authorizationBasic         string = "Basic "
	authorizationHeaderDefault string = "Authorization"
	gatewayLabelQParam         string = "gatewayLabel"
	typeQParam                 string = "type"
	internalWebAppEP           string = "/internal/data/v1/"
)

var (
	subList           *dto.SubscriptionList
	appList           *dto.ApplicationList
	appKeyMappingList *dto.ApplicationKeyMappingList
	apiList           *dto.ApiList
	appPolicyList     *dto.ApplicationPolicyList
	subPolicyList     *dto.SubscriptionPolicyList
	urls              = []url{
		{
			Endpoint:     "subscriptions",
			ResponseType: subList,
		},
		{
			Endpoint:     "applications",
			ResponseType: appList,
		},
		{
			Endpoint:     "application-key-mappings",
			ResponseType: appKeyMappingList,
		},
		{
			Endpoint:     "apis",
			ResponseType: apiList,
		},
		{
			Endpoint:     "application-policies",
			ResponseType: appPolicyList,
		},
		{
			Endpoint:     "subscription-policies",
			ResponseType: subPolicyList,
		},
	}
	accessToken string
	baseUrl     string
)

type response struct {
	Endpoint string
	Error    bool
	Payload  []byte
	Type     interface{}
}

type url struct {
	Endpoint     string
	ResponseType interface{}
}

func initSubscriptionDataLoader(configFile *config.Config) {
	username := configFile.ControlPlane.Credentials.Username
	password := configFile.ControlPlane.Credentials.Password
	// generate the access token
	accessToken = generateAccessToken(username, password)

	baseUrl = configFile.ControlPlane.EventHub.ServiceUrl
}

func generateAccessToken(username string, password string) string {
	data := username + ":" + password
	return b64.StdEncoding.EncodeToString([]byte(data))
}

func LoadSubscriptionData(configFile *config.Config) {
	// initialize the SubscriptionDataLoader
	initSubscriptionDataLoader(configFile)

	var responseChannel = make(chan response)
	for _, url := range urls {
		go invokeService(url.Endpoint, url.ResponseType, accessToken, responseChannel)
	}

	var response response
	for i := 1; i <= len(urls); i++ {
		response = <-responseChannel

		responseType := reflect.TypeOf(response.Type).Elem()
		newResponse := reflect.New(responseType).Interface()

		if !response.Error && response.Payload != nil {
			err := json.Unmarshal(response.Payload, &newResponse)

			if err != nil {
				logger.Error("Error occurred while unmarshalling the response received for: /"+response.Endpoint, err)
			} else {
				switch t := newResponse.(type) {
				case *SubscriptionList:
					subList = newResponse.(*SubscriptionList)
				case *ApplicationList:
					appList = newResponse.(*ApplicationList)
				case *ApplicationKeyMappingList:
					appKeyMappingList = newResponse.(*ApplicationKeyMappingList)
				case *ApiList:
					apiList = newResponse.(*ApiList)
				case *ApplicationPolicyList:
					appPolicyList = newResponse.(*ApplicationPolicyList)
				case *SubscriptionPolicyList:
					subPolicyList = newResponse.(*SubscriptionPolicyList)
				default:
					logger.Debug("Don't know type %T\n", t)
				}
			}
		}
	}
}

func invokeService(endpoint string, responseType interface{}, accessToken string, c chan response) {

	serviceURL := baseUrl + internalWebAppEP + endpoint
	// Create the request
	req, err := http.NewRequest("GET", serviceURL, nil)

	if err != nil {
		c <- response{endpoint, true, nil, responseType}
		logger.Error("Error occurred while creating an HTTP request for serviceURL: "+serviceURL, err)
		return
	}
	//handle TLS
	tr := &http.Transport{}
	tr = &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}
	// Configuring the http client
	client := &http.Client{
		Transport: tr,
	}
	// Adding query parameters
	q := req.URL.Query()
	q.Add(gatewayLabelQParam, "Production and Sandbox")
	q.Add(typeQParam, "Envoy")
	req.URL.RawQuery = q.Encode()

	// Setting authorization header
	req.Header.Set(authorizationHeaderDefault, authorizationBasic+accessToken)

	// Make the request
	resp, err := client.Do(req)

	if err != nil {
		c <- response{endpoint, true, nil, responseType}
		logger.Error("Error occurred while calling the REST API: "+serviceURL, err)
		return
	}

	if resp.StatusCode == http.StatusOK {
		responseBytes, err := ioutil.ReadAll(resp.Body)
		if err != nil {
			c <- response{endpoint, true, nil, responseType}
			logger.Error("Error occurred while reading the response received for: "+serviceURL, err)
			return
		}
		c <- response{endpoint, false, responseBytes, responseType}

	} else {
		c <- response{endpoint, true, nil, responseType}
		logger.Error("Failed to fetch data! "+serviceURL+" responded with "+string(resp.StatusCode), err)
	}
}
