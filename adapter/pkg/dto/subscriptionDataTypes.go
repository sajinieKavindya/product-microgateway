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

package dto

type event struct {
	EventID      string
	TimeStamp    int64
	Type         string
	TenantID     int32
	TenantDomain string
}

type Subscription struct {
	SubscriptionID    int    `json:"subscriptionId"`
	PolicyID          string `json:"policyId"`
	APIID             int    `json:"apiId"`
	AppID             int    `json:"appId" json:"applicationId"`
	SubscriptionState string `json:"subscriptionState"`
	Event             event  `json:"event,omitempty"`
}

type SubscriptionList struct {
	List []Subscription `json:"list"`
}

type Application struct {
	UUID       string            `json:"uuid"`
	ID         int               `json:"id" json:"applicationId"`
	Name       string            `json:"name" jsonn:"applicationName"`
	SubName    string            `json:"subName" json:"subscriber"`
	Policy     string            `json:"policy" json:"applicationPolicy"`
	TokenType  string            `json:"tokenType"`
	GroupIds   []string          `json:"groupIds"`
	Attributes map[string]string `json:"attributes"`
	Event      event             `json:"event,omitempty"`
}

type ApplicationList struct {
	List []Application `json:"list"`
}

type ApplicationKeyMapping struct {
	ApplicationID int    `json:"applicationId"`
	ConsumerKey   string `json:"consumerKey"`
	KeyType       string `json:"keyType"`
	KeyManager    string `json:"keyManager"`
	Event         event  `json:"event,omitempty"`
}

type ApplicationKeyMappingList struct {
	List []ApplicationKeyMapping `json:"list"`
}

type Apis struct {
	APIID            int    `json:"apiId"`
	Provider         string `json:"provider"`
	Name             string `json:"name"`
	Version          string `json:"version"`
	Context          string `json:"context"`
	Policy           string `json:"policy"`
	APIType          string `json:"apiType"`
	IsDefaultVersion bool   `json:"isDefaultVersion"`
	Event            event  `json:"event,omitempty"`
}

type ApiList struct {
	List []Apis `json:"list"`
}

type ApplicationPolicy struct {
	ID        int    `json:"id"`
	TenantID  int    `json:"tenantId"`
	Name      string `json:"name"`
	QuotaType string `json:"quotaType"`
	Event     event  `json:"event,omitempty"`
}

type ApplicationPolicyList struct {
	List []ApplicationPolicy `json:"list"`
}

type SubscriptionPolicy struct {
	ID                   int    `json:"id" json:"policyId"`
	TenantID             int    `json:"tenantId"`
	Name                 string `json:"name"`
	QuotaType            string `json:"quotaType"`
	GraphQLMaxComplexity int    `json:"graphQLMaxComplexity"`
	GraphQLMaxDepth      int    `json:"graphQLMaxDepth"`
	RateLimitCount       int    `json:"rateLimitCount"`
	RateLimitTimeUnit    string `json:"rateLimitTimeUnit"`
	StopOnQuotaReach     bool   `json:"stopOnQuotaReach"`
	Event                event  `json:"event,omitempty"`
}

type SubscriptionPolicyList struct {
	List []SubscriptionPolicy `json:"list"`
}
