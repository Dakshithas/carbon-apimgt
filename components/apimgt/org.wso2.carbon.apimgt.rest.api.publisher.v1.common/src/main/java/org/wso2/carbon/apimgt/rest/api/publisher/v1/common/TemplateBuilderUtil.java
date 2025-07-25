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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.apimgt.rest.api.publisher.v1.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.wso2.carbon.apimgt.api.APIConstants.AIAPIConstants;
import org.wso2.carbon.apimgt.api.APIDefinition;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.TokenBasedThrottlingCountHolder;
import org.wso2.carbon.apimgt.api.dto.ClientCertificateDTO;
import org.wso2.carbon.apimgt.api.dto.EndpointConfigDTO;
import org.wso2.carbon.apimgt.api.dto.EndpointDTO;
import org.wso2.carbon.apimgt.api.gateway.CredentialDto;
import org.wso2.carbon.apimgt.api.gateway.GatewayAPIDTO;
import org.wso2.carbon.apimgt.api.gateway.GatewayContentDTO;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIProduct;
import org.wso2.carbon.apimgt.api.model.APIProductIdentifier;
import org.wso2.carbon.apimgt.api.model.APIProductResource;
import org.wso2.carbon.apimgt.api.model.CORSConfiguration;
import org.wso2.carbon.apimgt.api.model.Environment;
import org.wso2.carbon.apimgt.api.model.SequenceBackendData;
import org.wso2.carbon.apimgt.api.model.SimplifiedEndpoint;
import org.wso2.carbon.apimgt.api.model.URITemplate;
import org.wso2.carbon.apimgt.api.model.WebSocketTopicMappingConfiguration;
import org.wso2.carbon.apimgt.common.gateway.graphql.GraphQLSchemaDefinitionUtil;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.certificatemgt.exceptions.CertificateManagementException;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dto.SoapToRestMediationDto;
import org.wso2.carbon.apimgt.impl.importexport.ImportExportConstants;
import org.wso2.carbon.apimgt.impl.template.APITemplateBuilder;
import org.wso2.carbon.apimgt.impl.template.APITemplateException;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.utils.CertificateMgtUtils;
import org.wso2.carbon.apimgt.impl.utils.GatewayUtils;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.common.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.common.mappings.APIMappingUtil;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.common.mappings.ImportUtils;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.common.template.APITemplateBuilderImpl;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.dto.APIDTO;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.dto.APIOperationsDTO;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.dto.MediationPolicyDTO;
import org.wso2.carbon.apimgt.spec.parser.definitions.GraphQLSchemaDefinition;
import org.wso2.carbon.apimgt.spec.parser.definitions.OASParserUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import static org.wso2.carbon.apimgt.impl.APIConstants.API_ENDPOINT_CONFIG_PROTOCOL_TYPE;

/**
 * This class used to utility for Template.
 */
public class TemplateBuilderUtil {

    private static final String ENDPOINT_PRODUCTION = "_PRODUCTION_";
    private static final String ENDPOINT_SANDBOX = "_SANDBOX_";

    private static final Log log = LogFactory.getLog(TemplateBuilderUtil.class);

    public static APITemplateBuilderImpl getAPITemplateBuilder(API api, String tenantDomain,
                                                           List<ClientCertificateDTO> clientCertificateDTOSProduction,
                                                           List<ClientCertificateDTO> clientCertificateDTOSSandbox,
                                                           List<SoapToRestMediationDto> soapToRestInMediationDtos,
                                                           List<SoapToRestMediationDto> soapToRestMediationDtos)
            throws APIManagementException {

        int tenantId = APIUtil.getTenantIdFromTenantDomain(tenantDomain);
        APITemplateBuilderImpl vtb = new APITemplateBuilderImpl(api, soapToRestInMediationDtos,
                soapToRestMediationDtos);
        Map<String, String> latencyStatsProperties = new HashMap<String, String>();
        latencyStatsProperties.put(APIConstants.API_UUID, api.getUUID());
        if (!APIUtil.isStreamingApi(api)) {
            vtb.addHandler(
                    "org.wso2.carbon.apimgt.gateway.handlers.common.APIMgtLatencyStatsHandler",
                    latencyStatsProperties);
        }
        Map<String, String> corsProperties = new HashMap<String, String>();
        corsProperties.put(APIConstants.CORSHeaders.IMPLEMENTATION_TYPE_HANDLER_VALUE, api.getImplementation());

        //Get authorization header from the API object or from the tenant registry
        String authorizationHeader;
        if (!StringUtils.isBlank(api.getAuthorizationHeader())) {
            authorizationHeader = api.getAuthorizationHeader();
        } else {
            //Retrieves the auth configuration from tenant registry or api-manager.xml if not available
            // in tenant registry
            authorizationHeader = APIUtil.getOAuthConfiguration(tenantDomain, APIConstants.AUTHORIZATION_HEADER);
        }
        if (!StringUtils.isBlank(authorizationHeader)) {
            corsProperties.put(APIConstants.AUTHORIZATION_HEADER, authorizationHeader);
        }

        String apiKeyHeader;
        if (!StringUtils.isBlank(api.getApiKeyHeader())) {
            apiKeyHeader = api.getApiKeyHeader();
        } else {
            apiKeyHeader = APIConstants.API_KEY_HEADER_DEFAULT;
        }
        if (!StringUtils.isBlank(apiKeyHeader)) {
            corsProperties.put(APIConstants.API_KEY_HEADER, apiKeyHeader);
        }

        if (!(APIConstants.APITransportType.WS.toString().equals(api.getType()))) {
            if (api.getCorsConfiguration() != null && api.getCorsConfiguration().isCorsConfigurationEnabled()) {
                CORSConfiguration corsConfiguration = api.getCorsConfiguration();
                if (corsConfiguration.getAccessControlAllowHeaders() != null) {
                    StringBuilder allowHeaders = new StringBuilder();
                    for (String header : corsConfiguration.getAccessControlAllowHeaders()) {
                        allowHeaders.append(header).append(',');
                    }
                    if (allowHeaders.length() != 0) {
                        allowHeaders.deleteCharAt(allowHeaders.length() - 1);
                        corsProperties.put(APIConstants.CORSHeaders.ALLOW_HEADERS_HANDLER_VALUE,
                                allowHeaders.toString());
                    }
                }
                if (corsConfiguration.getAccessControlAllowOrigins() != null) {
                    StringBuilder allowOrigins = new StringBuilder();
                    for (String origin : corsConfiguration.getAccessControlAllowOrigins()) {
                        allowOrigins.append(origin).append(',');
                    }
                    if (allowOrigins.length() != 0) {
                        allowOrigins.deleteCharAt(allowOrigins.length() - 1);
                        corsProperties.put(APIConstants.CORSHeaders.ALLOW_ORIGIN_HANDLER_VALUE,
                                allowOrigins.toString());
                    }
                }
                if (corsConfiguration.getAccessControlAllowMethods() != null) {
                    StringBuilder allowedMethods = new StringBuilder();
                    for (String methods : corsConfiguration.getAccessControlAllowMethods()) {
                        allowedMethods.append(methods).append(',');
                    }
                    if (allowedMethods.length() != 0) {
                        allowedMethods.deleteCharAt(allowedMethods.length() - 1);
                        corsProperties.put(APIConstants.CORSHeaders.ALLOW_METHODS_HANDLER_VALUE,
                                allowedMethods.toString());
                    }
                }
                if (corsConfiguration.isAccessControlAllowCredentials()) {
                    corsProperties.put(APIConstants.CORSHeaders.ALLOW_CREDENTIALS_HANDLER_VALUE,
                            String.valueOf(corsConfiguration.isAccessControlAllowCredentials()));
                }
                vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.security.CORSRequestHandler"
                        , corsProperties);
            } else if (APIUtil.isCORSEnabled()) {
                vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.security.CORSRequestHandler"
                        , corsProperties);
            }
            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.common.APIStatusHandler", Collections.emptyMap());
        }
        Map<String, String> clientCertificateObject = new HashMap<>();
        CertificateMgtUtils certificateMgtUtils = CertificateMgtUtils.getInstance();
        if (clientCertificateDTOSProduction != null) {
            for (ClientCertificateDTO clientCertificateDTO : clientCertificateDTOSProduction) {
                /* appending the values without using a data structure to store them separately to avoid conflicts
                 when reading from certificatesDetails string at MutualSSLAuthenticator */
                clientCertificateObject.put(certificateMgtUtils
                                .getUniqueIdentifierOfCertificate(clientCertificateDTO.getCertificate()),
                        clientCertificateDTO.getTierName().concat(APIConstants.DELEM_COLON)
                                .concat(APIConstants.API_KEY_TYPE_PRODUCTION));
            }
        }
        if (clientCertificateDTOSSandbox != null) {
            for (ClientCertificateDTO clientCertificateDTO : clientCertificateDTOSSandbox) {
                /* appending the values without using a data structure to store them separately to avoid conflicts
                 when reading from certificatesDetails string at MutualSSLAuthenticator */
                clientCertificateObject.put(certificateMgtUtils
                                .getUniqueIdentifierOfCertificate(clientCertificateDTO.getCertificate()),
                        clientCertificateDTO.getTierName().concat(APIConstants.DELEM_COLON)
                                .concat(APIConstants.API_KEY_TYPE_SANDBOX));
            }
        }

        Map<String, String> authProperties = new HashMap<>();
        if (!StringUtils.isBlank(authorizationHeader)) {
            authProperties.put(APIConstants.AUTHORIZATION_HEADER, authorizationHeader);
        }
        if (!StringUtils.isBlank(apiKeyHeader)) {
            authProperties.put(APIConstants.API_KEY_HEADER, apiKeyHeader);
        }
        String apiSecurity = api.getApiSecurity();
        String apiLevelPolicy = api.getApiLevelPolicy();
        String audiences;
        Set<String> audienceList = api.getAudiences();
        if (audienceList != null) {
            audiences = String.join(",", audienceList);
        } else {
            audiences = "";
        }
        authProperties.put(APIConstants.AUDIENCES, audiences);
        authProperties.put(APIConstants.API_SECURITY, apiSecurity);
        authProperties.put(APIConstants.API_LEVEL_POLICY, apiLevelPolicy);
        if (!clientCertificateObject.isEmpty()) {
            authProperties.put(APIConstants.CERTIFICATE_INFORMATION, clientCertificateObject.toString());
        }
        //Get RemoveHeaderFromOutMessage from tenant registry or api-manager.xml
        String removeHeaderFromOutMessage = APIUtil
                .getOAuthConfiguration(tenantDomain, APIConstants.REMOVE_OAUTH_HEADER_FROM_OUT_MESSAGE);
        if (!StringUtils.isBlank(removeHeaderFromOutMessage)) {
            authProperties.put(APIConstants.REMOVE_OAUTH_HEADER_FROM_OUT_MESSAGE, removeHeaderFromOutMessage);
        } else {
            authProperties.put(APIConstants.REMOVE_OAUTH_HEADER_FROM_OUT_MESSAGE,
                    APIConstants.REMOVE_OAUTH_HEADER_FROM_OUT_MESSAGE_DEFAULT);
        }
        authProperties.put(APIConstants.API_UUID, api.getUUID());
        authProperties.put("keyManagers", String.join(",", api.getKeyManagers()));
        if (APIConstants.GRAPHQL_API.equals(api.getType())) {
            Map<String, String> apiUUIDProperty = new HashMap<String, String>();
            apiUUIDProperty.put(APIConstants.API_UUID, api.getUUID());
            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.graphQL.GraphQLAPIHandler",
                    apiUUIDProperty);
        }

        if (APIConstants.APITransportType.WEBSUB.toString().equals(api.getType())) {
            authProperties.put(APIConstants.WebHookProperties.EVENT_RECEIVING_RESOURCE_PATH,
                    APIConstants.WebHookProperties.DEFAULT_SUBSCRIPTION_RESOURCE_PATH);
            authProperties.put(APIConstants.WebHookProperties.TOPIC_QUERY_PARAM_NAME,
                    APIConstants.WebHookProperties.DEFAULT_TOPIC_QUERY_PARAM_NAME);
            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.streaming.webhook.WebhookApiHandler",
                    authProperties);
            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.streaming.webhook." +
                    "WebhooksExtensionHandler", Collections.emptyMap());
        } else if (APIConstants.APITransportType.SSE.toString().equals(api.getType())) {
            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.streaming.sse.SseApiHandler",
                    authProperties);
        } else if (!(APIConstants.APITransportType.WS.toString().equals(api.getType()))) {
            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.security.APIAuthenticationHandler",
                    authProperties);
        }

        if (APIConstants.GRAPHQL_API.equals(api.getType())) {
            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.graphQL.GraphQLQueryAnalysisHandler",
                    Collections.emptyMap());
        }

        if (!APIUtil.isStreamingApi(api)) {
            Map<String, String> properties = new HashMap<String, String>();

            if (api.getProductionMaxTps() != null) {
                properties.put("productionMaxCount", api.getProductionMaxTps());
            }

            if (api.getSandboxMaxTps() != null) {
                properties.put("sandboxMaxCount", api.getSandboxMaxTps());
            }

            if (api.getProductionTimeUnit() != null) {
                properties.put("productionUnitTime", api.getProductionTimeUnit());
            }

            if (api.getSandboxTimeUnit() != null) {
                properties.put("sandboxUnitTime", api.getSandboxTimeUnit());
            }

            if (api.getBackendThrottlingConfiguration() != null
                    && api.getBackendThrottlingConfiguration().getTokenBasedThrottlingConfiguration() != null
                    && api.getBackendThrottlingConfiguration().getTokenBasedThrottlingConfiguration()
                    .isTokenBasedThrottlingEnabled()) {

                TokenBasedThrottlingCountHolder tokenBasedThrottlingCountHolder
                        = api.getBackendThrottlingConfiguration().getTokenBasedThrottlingConfiguration();
                properties.put("isTokenBasedThrottlingEnabled",
                        tokenBasedThrottlingCountHolder.isTokenBasedThrottlingEnabled().toString());
                if (tokenBasedThrottlingCountHolder.getProductionMaxPromptTokenCount() != null) {
                    properties.put("productionMaxPromptTokenCount",
                            tokenBasedThrottlingCountHolder.getProductionMaxPromptTokenCount());
                }
                if (tokenBasedThrottlingCountHolder.getProductionMaxCompletionTokenCount() != null) {
                    properties.put("productionMaxCompletionTokenCount",
                            tokenBasedThrottlingCountHolder.getProductionMaxCompletionTokenCount());
                }
                if (tokenBasedThrottlingCountHolder.getProductionMaxTotalTokenCount() != null) {
                    properties.put("productionMaxTotalTokenCount",
                            tokenBasedThrottlingCountHolder.getProductionMaxTotalTokenCount());
                }
                if (tokenBasedThrottlingCountHolder.getSandboxMaxPromptTokenCount() != null) {
                    properties.put("sandboxMaxPromptTokenCount",
                            tokenBasedThrottlingCountHolder.getSandboxMaxPromptTokenCount());
                }
                if (tokenBasedThrottlingCountHolder.getSandboxMaxCompletionTokenCount() != null) {
                    properties.put("sandboxMaxCompletionTokenCount",
                            tokenBasedThrottlingCountHolder.getSandboxMaxCompletionTokenCount());
                }
                if (tokenBasedThrottlingCountHolder.getSandboxMaxTotalTokenCount() != null) {
                    properties.put("sandboxMaxTotalTokenCount",
                            tokenBasedThrottlingCountHolder.getSandboxMaxTotalTokenCount());
                }
            }

            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.throttling.ThrottleHandler"
                    , properties);

            properties = new HashMap<String, String>();
            properties.put("configKey", APIConstants.GA_CONF_KEY);
            vtb.addHandler(
                    "org.wso2.carbon.apimgt.gateway.handlers.analytics.APIMgtGoogleAnalyticsTrackingHandler"
                    , properties);

            String extensionHandlerPosition = getExtensionHandlerPosition(tenantDomain);
            if ("top".equalsIgnoreCase(extensionHandlerPosition)) {
                vtb.addHandlerPriority(
                        "org.wso2.carbon.apimgt.gateway.handlers.ext.APIManagerExtensionHandler",
                        Collections.emptyMap(), 2);
            } else {
                vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.ext.APIManagerExtensionHandler",
                        Collections.emptyMap());
            }
        }

        if (APIConstants.APITransportType.WS.toString().equals(api.getType())) {
            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.ext.WebSocketExtensionHandler",
                    Collections.emptyMap());
        }

        return vtb;
    }

    public static APITemplateBuilderImpl getAPITemplateBuilder(APIProduct apiProduct, String tenantDomain,
                   List<ClientCertificateDTO> clientCertificateDTOSProduction,
                   List<ClientCertificateDTO> clientCertificateDTOSSandbox, Map<String, APIDTO> associatedAPIMap)
            throws APIManagementException {

        int tenantId = APIUtil.getTenantIdFromTenantDomain(tenantDomain);
        APITemplateBuilderImpl vtb = new APITemplateBuilderImpl(apiProduct, associatedAPIMap);
        Map<String, String> latencyStatsProperties = new HashMap<String, String>();
        latencyStatsProperties.put(APIConstants.API_UUID, apiProduct.getUuid());
        if (!APIUtil.isStreamingApi(apiProduct)) {
            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.common.APIMgtLatencyStatsHandler",
                    latencyStatsProperties);
        }

        Map<String, String> corsProperties = new HashMap<>();
        corsProperties.put(APIConstants.CORSHeaders.IMPLEMENTATION_TYPE_HANDLER_VALUE,
                APIConstants.IMPLEMENTATION_TYPE_ENDPOINT);

        //Get authorization header from the API object or from the tenant registry
        String authorizationHeader;
        if (!StringUtils.isBlank(apiProduct.getAuthorizationHeader())) {
            authorizationHeader = apiProduct.getAuthorizationHeader();
        } else {
            //Retrieves the auth configuration from tenant registry or api-manager.xml if not available
            // in tenant registry
            authorizationHeader = APIUtil.getOAuthConfiguration(tenantDomain, APIConstants.AUTHORIZATION_HEADER);
        }
        if (!StringUtils.isBlank(authorizationHeader)) {
            corsProperties.put(APIConstants.AUTHORIZATION_HEADER, authorizationHeader);
        }

        String apiKeyHeader;
        if (!StringUtils.isBlank(apiProduct.getApiKeyHeader())) {
            apiKeyHeader = apiProduct.getApiKeyHeader();
        } else {
            apiKeyHeader = APIConstants.API_KEY_HEADER_DEFAULT;
        }
        if (!StringUtils.isBlank(apiKeyHeader)) {
            corsProperties.put(APIConstants.API_KEY_HEADER, apiKeyHeader);
        }

        if (apiProduct.getCorsConfiguration() != null &&
                apiProduct.getCorsConfiguration().isCorsConfigurationEnabled()) {
            CORSConfiguration corsConfiguration = apiProduct.getCorsConfiguration();
            if (corsConfiguration.getAccessControlAllowHeaders() != null) {
                StringBuilder allowHeaders = new StringBuilder();
                for (String header : corsConfiguration.getAccessControlAllowHeaders()) {
                    allowHeaders.append(header).append(',');
                }
                if (allowHeaders.length() != 0) {
                    allowHeaders.deleteCharAt(allowHeaders.length() - 1);
                    corsProperties.put(APIConstants.CORSHeaders.ALLOW_HEADERS_HANDLER_VALUE, allowHeaders.toString());
                }
            }
            if (corsConfiguration.getAccessControlAllowOrigins() != null) {
                StringBuilder allowOrigins = new StringBuilder();
                for (String origin : corsConfiguration.getAccessControlAllowOrigins()) {
                    allowOrigins.append(origin).append(',');
                }
                if (allowOrigins.length() != 0) {
                    allowOrigins.deleteCharAt(allowOrigins.length() - 1);
                    corsProperties.put(APIConstants.CORSHeaders.ALLOW_ORIGIN_HANDLER_VALUE, allowOrigins.toString());
                }
            }
            if (corsConfiguration.getAccessControlAllowMethods() != null) {
                StringBuilder allowedMethods = new StringBuilder();
                for (String methods : corsConfiguration.getAccessControlAllowMethods()) {
                    allowedMethods.append(methods).append(',');
                }
                if (allowedMethods.length() != 0) {
                    allowedMethods.deleteCharAt(allowedMethods.length() - 1);
                    corsProperties.put(APIConstants.CORSHeaders.ALLOW_METHODS_HANDLER_VALUE, allowedMethods.toString());
                }
            }
            if (corsConfiguration.isAccessControlAllowCredentials()) {
                corsProperties.put(APIConstants.CORSHeaders.ALLOW_CREDENTIALS_HANDLER_VALUE,
                        String.valueOf(corsConfiguration.isAccessControlAllowCredentials()));
            }
            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.security.CORSRequestHandler"
                    , corsProperties);
        } else if (APIUtil.isCORSEnabled()) {
            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.security.CORSRequestHandler"
                    , corsProperties);
        }
        vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.common.APIStatusHandler", Collections.emptyMap());

        Map<String, String> clientCertificateObject = new HashMap<>();
        CertificateMgtUtils certificateMgtUtils = CertificateMgtUtils.getInstance();
        if (clientCertificateDTOSProduction != null) {
            for (ClientCertificateDTO clientCertificateDTO : clientCertificateDTOSProduction) {
                /* appending the values without using a data structure to store them separately to avoid conflicts
                 when reading from certificatesDetails string at MutualSSLAuthenticator */
                clientCertificateObject.put(certificateMgtUtils.
                                getUniqueIdentifierOfCertificate(clientCertificateDTO.getCertificate()),
                        clientCertificateDTO.getTierName().concat(APIConstants.DELEM_COLON)
                                .concat(APIConstants.API_KEY_TYPE_PRODUCTION));
            }
        }
        if (clientCertificateDTOSSandbox != null) {
            for (ClientCertificateDTO clientCertificateDTO : clientCertificateDTOSSandbox) {
                /* appending the values without using a data structure to store them separately to avoid conflicts
                 when reading from certificatesDetails string at MutualSSLAuthenticator */
                clientCertificateObject.put(certificateMgtUtils.
                                getUniqueIdentifierOfCertificate(clientCertificateDTO.getCertificate()),
                        clientCertificateDTO.getTierName().concat(APIConstants.DELEM_COLON)
                                .concat(APIConstants.API_KEY_TYPE_SANDBOX));
            }
        }

        Map<String, String> authProperties = new HashMap<String, String>();
        if (!StringUtils.isBlank(authorizationHeader)) {
            authProperties.put(APIConstants.AUTHORIZATION_HEADER, authorizationHeader);
        }
        if (!StringUtils.isBlank(apiKeyHeader)) {
            authProperties.put(APIConstants.API_KEY_HEADER, apiKeyHeader);
        }
        String apiSecurity = apiProduct.getApiSecurity();
        String apiLevelPolicy = apiProduct.getProductLevelPolicy();
        authProperties.put(APIConstants.API_SECURITY, apiSecurity);
        authProperties.put(APIConstants.API_LEVEL_POLICY, apiLevelPolicy);
        String audiences;
        Set<String> audienceList = apiProduct.getAudiences();
        if (audienceList != null) {
            audiences = String.join(",", audienceList);
        } else {
            audiences = "";
        }
        authProperties.put(APIConstants.AUDIENCES, audiences);
        if (!clientCertificateObject.isEmpty()) {
            authProperties.put(APIConstants.CERTIFICATE_INFORMATION, clientCertificateObject.toString());
        }

        //Get RemoveHeaderFromOutMessage from tenant registry or api-manager.xml
        String removeHeaderFromOutMessage = APIUtil
                .getOAuthConfiguration(tenantDomain, APIConstants.REMOVE_OAUTH_HEADER_FROM_OUT_MESSAGE);
        if (!StringUtils.isBlank(removeHeaderFromOutMessage)) {
            authProperties.put(APIConstants.REMOVE_OAUTH_HEADER_FROM_OUT_MESSAGE, removeHeaderFromOutMessage);
        } else {
            authProperties.put(APIConstants.REMOVE_OAUTH_HEADER_FROM_OUT_MESSAGE,
                    APIConstants.REMOVE_OAUTH_HEADER_FROM_OUT_MESSAGE_DEFAULT);
        }

        authProperties.put("apiType", APIConstants.ApiTypes.PRODUCT_API.name());
        vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.security.APIAuthenticationHandler",
                authProperties);
        Map<String, String> properties = new HashMap<String, String>();

        if (apiProduct.getProductionMaxTps() != null) {
            properties.put("productionMaxCount", apiProduct.getProductionMaxTps());
        }

        if (apiProduct.getSandboxMaxTps() != null) {
            properties.put("sandboxMaxCount", apiProduct.getSandboxMaxTps());
        }

        if (!APIUtil.isStreamingApi(apiProduct)) {
            vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.throttling.ThrottleHandler"
                    , properties);

            properties = new HashMap<String, String>();
            properties.put("configKey", APIConstants.GA_CONF_KEY);
            vtb.addHandler(
                    "org.wso2.carbon.apimgt.gateway.handlers.analytics.APIMgtGoogleAnalyticsTrackingHandler"
                    , properties);

            String extensionHandlerPosition = getExtensionHandlerPosition(tenantDomain);
            if ("top".equalsIgnoreCase(extensionHandlerPosition)) {
                vtb.addHandlerPriority(
                        "org.wso2.carbon.apimgt.gateway.handlers.ext.APIManagerExtensionHandler",
                        Collections.emptyMap(), 2);
            } else {
                vtb.addHandler("org.wso2.carbon.apimgt.gateway.handlers.ext.APIManagerExtensionHandler",
                        Collections.emptyMap());
            }
        }

        return vtb;
    }

    /**
     * Retrieves Extension Handler Position from the tenant-config.json.
     *
     * @return ExtensionHandlerPosition
     * @throws APIManagementException
     */
    private static String getExtensionHandlerPosition(String tenantDomain) throws APIManagementException {

        JSONObject tenantConf = APIUtil.getTenantConfig(tenantDomain);
        return (String) tenantConf.get(APIConstants.EXTENSION_HANDLER_POSITION);
    }

    public static GatewayAPIDTO retrieveGatewayAPIDto(API api, Environment environment, String tenantDomain,
                                                      APIDTO apidto, String extractedFolderPath)
            throws APIManagementException, XMLStreamException, APITemplateException {

        List<ClientCertificateDTO> clientCertificatesDTOListProduction =
                ImportUtils.retrieveClientCertificates(extractedFolderPath, APIConstants.API_KEY_TYPE_PRODUCTION);
        List<ClientCertificateDTO> clientCertificatesDTOListSandbox =
                ImportUtils.retrieveClientCertificates(extractedFolderPath, APIConstants.API_KEY_TYPE_SANDBOX);
        List<SoapToRestMediationDto> soapToRestInMediationDtoList =
                ImportUtils.retrieveSoapToRestFlowMediations(extractedFolderPath, ImportUtils.IN);
        List<SoapToRestMediationDto> soapToRestOutMediationDtoList =
                ImportUtils.retrieveSoapToRestFlowMediations(extractedFolderPath, ImportUtils.OUT);
        List<EndpointDTO> endpointDTOList = null;
        if (APIConstants.API_SUBTYPE_AI_API.equals(api.getSubtype())) {
            endpointDTOList = ImportUtils.retrieveEndpointConfigs(extractedFolderPath);
            addEndpointsFromConfig(endpointDTOList, api);
        }
        JSONObject originalProperties = api.getAdditionalProperties();
        JSONObject modifiedProperties = getModifiedProperties(originalProperties);
        api.setAdditionalProperties(modifiedProperties);

        String endpointConfigString = api.getEndpointConfig();
        if (StringUtils.isNotBlank(endpointConfigString)) {
            try {
                // Avoid number format issues in Endpoint Configuration
                JsonObject endpointConf = JsonParser.parseString(api.getEndpointConfig()).getAsJsonObject();
                if (endpointConf != null && APIConstants.ENDPOINT_TYPE_SEQUENCE.equals(
                        endpointConf.get(API_ENDPOINT_CONFIG_PROTOCOL_TYPE).getAsString()) && StringUtils.equals(
                        api.getType().toLowerCase(), APIConstants.API_TYPE_HTTP.toLowerCase())) {
                    ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
                    // To modify the endpoint config string
                    JSONParser parser = new JSONParser();
                    ObjectMapper objectMapper = new ObjectMapper();
                    JSONObject endpointConfig = (JSONObject) parser.parse(endpointConfigString);
                    if (APIConstants.ENDPOINT_TYPE_SEQUENCE.equals(
                            endpointConfig.get(API_ENDPOINT_CONFIG_PROTOCOL_TYPE))) {
                        String policyDirectory =
                                extractedFolderPath + File.separator + ImportExportConstants.CUSTOM_BACKEND_DIRECTORY;
                        String seqName = APIUtil.getCustomBackendName(api.getUuid(), APIConstants.API_KEY_TYPE_SANDBOX);
                        SequenceBackendData seqData = apiMgtDAO.getCustomBackendByAPIUUID(api.getUuid(),
                                APIConstants.API_KEY_TYPE_SANDBOX);
                        if (seqData != null) {
                            String name = seqData.getName();
                            if (!StringUtils.isEmpty(name) && !name.contains(
                                    APIConstants.SYNAPSE_POLICY_DEFINITION_EXTENSION_XML)) {
                                name = name + APIConstants.SYNAPSE_POLICY_DEFINITION_EXTENSION_XML;
                            }
                            if (APIUtil.checkFileExistence(policyDirectory + File.separator + name)) {
                                endpointConfig.put("sandbox", seqName);
                            }
                        }

                        seqName = APIUtil.getCustomBackendName(api.getUuid(), APIConstants.API_KEY_TYPE_PRODUCTION);
                        seqData = apiMgtDAO.getCustomBackendByAPIUUID(api.getUuid(),
                                APIConstants.API_KEY_TYPE_PRODUCTION);
                        if (seqData != null) {
                            String name = seqData.getName();
                            if (!StringUtils.isEmpty(name) && !name.contains(
                                    APIConstants.SYNAPSE_POLICY_DEFINITION_EXTENSION_XML)) {
                                name = name + APIConstants.SYNAPSE_POLICY_DEFINITION_EXTENSION_XML;
                            }
                            if (APIUtil.checkFileExistence(policyDirectory + File.separator + name)) {
                                endpointConfig.put("production", seqName);
                            }
                        }
                        api.setEndpointConfig(objectMapper.writeValueAsString(endpointConfig));
                    }
                }
            } catch (IOException | ParseException ex) {
                throw new APIManagementException("Error when updating Endpoint Configuration of API: " + api.getUuid(),
                        ex);
            }
        }

        APITemplateBuilder apiTemplateBuilder = TemplateBuilderUtil
                .getAPITemplateBuilder(api, tenantDomain, clientCertificatesDTOListProduction,
                        clientCertificatesDTOListSandbox, soapToRestInMediationDtoList, soapToRestOutMediationDtoList);
        GatewayAPIDTO gatewayAPIDto = createAPIGatewayDTOtoPublishAPI(environment, api, apiTemplateBuilder,
                tenantDomain, extractedFolderPath, apidto, clientCertificatesDTOListProduction,
                clientCertificatesDTOListSandbox, endpointDTOList);
        // Reset the additional properties to the original values
        if (originalProperties != null) {
            api.setAdditionalProperties(originalProperties);
        }
        return gatewayAPIDto;
    }

    /**
     * Adds production and sandbox endpoints from the API's endpoint configuration to the given endpoint list.
     *
     * @param endpointDTOList The list to which the generated endpoints will be added.
     * @param api             The API containing the endpoint configuration.
     */
    private static void addEndpointsFromConfig(List<EndpointDTO> endpointDTOList, API api) {

        if (api.getEndpointConfig() == null) {
            return;
        }
        EndpointConfigDTO endpointConfig = new Gson().fromJson(api.getEndpointConfig(), EndpointConfigDTO.class);
        if (endpointConfig == null) {
            return;
        }
        if (endpointConfig.getProductionEndpoints() != null) {
            endpointDTOList.add(createEndpointDTO(APIConstants.APIEndpoint.PRODUCTION,
                    AIAPIConstants.DEFAULT_PRODUCTION_ENDPOINT_NAME, endpointConfig));
        }
        if (endpointConfig.getSandboxEndpoints() != null) {
            endpointDTOList.add(createEndpointDTO(APIConstants.APIEndpoint.SANDBOX,
                    AIAPIConstants.DEFAULT_SANDBOX_ENDPOINT_NAME, endpointConfig));
        }
    }

    /**
     * Creates an EndpointDTO object with the specified parameters.
     *
     * @param stage          The deployment stage (production or sandbox).
     * @param name           The name of the endpoint.
     * @param endpointConfig The endpoint configuration.
     * @return An initialized EndpointDTO instance.
     */
    private static EndpointDTO createEndpointDTO(String stage, String name, EndpointConfigDTO endpointConfig) {

        EndpointDTO endpoint = new EndpointDTO();
        endpoint.setEndpointConfig(endpointConfig);
        String defaultEndpointId = APIConstants.APIEndpoint.PRODUCTION.equals(stage) ?
                APIConstants.APIEndpoint.DEFAULT_PROD_ENDPOINT_ID :
                APIConstants.APIEndpoint.DEFAULT_SANDBOX_ENDPOINT_ID;
        endpoint.setId(defaultEndpointId);
        endpoint.setName(name);
        endpoint.setDeploymentStage(stage);
        return endpoint;
    }

    public static GatewayAPIDTO retrieveGatewayAPIDto(API api, Environment environment, String tenantDomain,
                                                      APIDTO apidto, String extractedFolderPath, String apiDefinition)
            throws APIManagementException, XMLStreamException, APITemplateException {

        APIDefinition parser = OASParserUtil.getOASParser(apiDefinition);
        if (parser != null) {
            Set<URITemplate> uriTemplates = parser.getURITemplates(apiDefinition);
            for (URITemplate uriTemplate : uriTemplates) {
                for (URITemplate template : api.getUriTemplates()) {
                    if (template.getHTTPVerb().equalsIgnoreCase(uriTemplate.getHTTPVerb()) &&
                            template.getUriTemplate().equals(uriTemplate.getUriTemplate())) {
                        template.setMediationScript(uriTemplate.getMediationScript());
                        template.setMediationScripts(uriTemplate.getHTTPVerb(), uriTemplate.getMediationScript());
                        template.setAmznResourceName(uriTemplate.getAmznResourceName());
                        template.setAmznResourceTimeout(uriTemplate.getAmznResourceTimeout());
                        template.setAmznResourceContentEncoded(uriTemplate.getAmznResourceContentEncoded());
                        break;
                    }
                }
            }
        }
        return retrieveGatewayAPIDto(api, environment, tenantDomain, apidto, extractedFolderPath);
    }

    public static GatewayAPIDTO retrieveGatewayAPIDtoForStreamingAPI(API api, Environment environment,
                                                                     String tenantDomain, APIDTO apidto,
                                                                     String extractedFolderPath)
            throws APIManagementException, XMLStreamException, APITemplateException, CertificateManagementException {

        return retrieveGatewayAPIDto(api, environment, tenantDomain, apidto, extractedFolderPath);
    }

    public static GatewayAPIDTO retrieveGatewayAPIDto(APIProduct apiProduct, Environment environment,
            String tenantDomain, String extractedFolderPath, String apiDefinition)
            throws APIManagementException, XMLStreamException, APITemplateException {

        List<ClientCertificateDTO> clientCertificatesDTOListProduction =
                ImportUtils.retrieveClientCertificates(extractedFolderPath, APIConstants.API_KEY_TYPE_PRODUCTION);
        List<ClientCertificateDTO> clientCertificatesDTOListSandbox =
                ImportUtils.retrieveClientCertificates(extractedFolderPath, APIConstants.API_KEY_TYPE_SANDBOX);
        Map<String, APIDTO> apidtoMap = retrieveAssociatedApis(extractedFolderPath);
        Map<String, APIDTO> associatedAPIsMap = convertAPIIdToDto(apidtoMap.values());
        APIDefinition parser = OASParserUtil.getOASParser(apiDefinition);
        Set<URITemplate> uriTemplates = Collections.emptySet();
        if (parser != null) {
            uriTemplates = parser.getURITemplates(apiDefinition);
        }
        for (APIProductResource productResource : apiProduct.getProductResources()) {
            String apiId = productResource.getApiId();
            APIDTO apidto = associatedAPIsMap.get(apiId);
            if (apidto != null) {
                API api = APIMappingUtil.fromDTOtoAPI(apidto, apidto.getProvider());
                productResource.setApiIdentifier(api.getId());
                if (APIConstants.IMPLEMENTATION_TYPE_INLINE.equalsIgnoreCase(api.getImplementation())) {
                    for (URITemplate uriTemplate : uriTemplates) {
                        URITemplate template = productResource.getUriTemplate();
                        if (template.getHTTPVerb()
                                .equalsIgnoreCase(uriTemplate.getHTTPVerb()) && template.getUriTemplate()
                                .equals(uriTemplate.getUriTemplate())) {
                            template.setMediationScript(uriTemplate.getMediationScript());
                            template.setMediationScripts(uriTemplate.getHTTPVerb(), uriTemplate.getMediationScript());
                            break;
                        }
                    }
                }
                if (api.isAdvertiseOnly()) {
                    productResource.setEndpointConfig(APIUtil.generateEndpointConfigForAdvertiseOnlyApi(api));
                } else {
                    productResource.setEndpointConfig(api.getEndpointConfig());
                }
                if (StringUtils.isNotEmpty(api.getInSequence())) {
                    String sequenceName = APIUtil.getSequenceExtensionName(apiProduct.getId().getName(),
                            apiProduct.getId().getVersion()).concat("--").concat(productResource.getApiId())
                            + APIConstants.API_CUSTOM_SEQ_IN_EXT;
                    productResource.setInSequenceName(sequenceName);
                }
                if (StringUtils.isNotEmpty(api.getOutSequence())) {
                    String sequenceName = APIUtil.getSequenceExtensionName(apiProduct.getId().getName(),
                            apiProduct.getId().getVersion()).concat("--").concat(productResource.getApiId())
                            + APIConstants.API_CUSTOM_SEQ_OUT_EXT;
                    productResource.setOutSequenceName(sequenceName);
                }
                if (StringUtils.isNotEmpty(api.getFaultSequence())) {
                    String sequenceName = APIUtil.getSequenceExtensionName(apiProduct.getId().getName(),
                            apiProduct.getId().getVersion()).concat("--").concat(productResource.getApiId())
                            + APIConstants.API_CUSTOM_SEQ_FAULT_EXT;
                    productResource.setFaultSequenceName(sequenceName);
                }
                productResource.setProductIdentifier(apiProduct.getId());
                productResource.setEndpointSecurityMap(APIUtil.setEndpointSecurityForAPIProduct(api));
            }
        }
        APITemplateBuilder
                apiTemplateBuilder =
                TemplateBuilderUtil.getAPITemplateBuilder(apiProduct, tenantDomain, clientCertificatesDTOListProduction,
                        clientCertificatesDTOListSandbox, convertAPIIdToDto(associatedAPIsMap.values()));
        return createAPIGatewayDTOtoPublishAPI(environment, apiProduct, apiTemplateBuilder, tenantDomain,
                apidtoMap, clientCertificatesDTOListProduction, clientCertificatesDTOListSandbox);
    }

    private static GatewayAPIDTO createAPIGatewayDTOtoPublishAPI(Environment environment, APIProduct apiProduct,
            APITemplateBuilder builder, String tenantDomain, Map<String, APIDTO> associatedAPIsMap,
            List<ClientCertificateDTO> clientCertificatesDTOListProduction,
            List<ClientCertificateDTO> clientCertificatesDTOListSandbox)
            throws APITemplateException, XMLStreamException, APIManagementException {

        APIProductIdentifier id = apiProduct.getId();
        GatewayAPIDTO productAPIDto = new GatewayAPIDTO();
        productAPIDto.setProvider(id.getProviderName());
        productAPIDto.setApiId(apiProduct.getUuid());
        productAPIDto.setName(id.getName());
        productAPIDto.setApiContext(apiProduct.getContext());
        productAPIDto.setVersion(id.getVersion());
        productAPIDto.setTenantDomain(tenantDomain);
        productAPIDto.setKeyManagers(Collections.singletonList(APIConstants.KeyManager.API_LEVEL_ALL_KEY_MANAGERS));
        String definition = apiProduct.getDefinition();
        productAPIDto.setLocalEntriesToBeRemove(GatewayUtils.addStringToList(apiProduct.getUuid(),
                productAPIDto.getLocalEntriesToBeRemove()));
        GatewayContentDTO productLocalEntry = new GatewayContentDTO();
        productLocalEntry.setName(apiProduct.getUuid());
        productLocalEntry.setContent("<localEntry key=\"" + apiProduct.getUuid() + "\">" +
                definition.replaceAll("&(?!amp;)", "&amp;").
                        replaceAll("<", "&lt;").replaceAll(">", "&gt;")
                + "</localEntry>");
        productAPIDto.setLocalEntriesToBeAdd(addGatewayContentToList(productLocalEntry,
                productAPIDto.getLocalEntriesToBeAdd()));
        setClientCertificatesToBeAdded(tenantDomain, productAPIDto, clientCertificatesDTOListProduction,
                clientCertificatesDTOListSandbox);
        for (Map.Entry<String, APIDTO> apidtoEntry : associatedAPIsMap.entrySet()) {
            String apiExtractedPath = apidtoEntry.getKey();
            APIDTO apidto = apidtoEntry.getValue();
            API api = APIMappingUtil.fromDTOtoAPI(apidto, apidto.getProvider());
            api.setUuid(apidto.getId());
            GatewayUtils.setCustomSequencesToBeRemoved(apiProduct.getId(), api.getUuid(), productAPIDto);
            APITemplateBuilder apiTemplateBuilder = new APITemplateBuilderImpl(api, apiProduct);
            // check the endpoint type
            if (!StringUtils.isEmpty(api.getEndpointConfig())) {
                JsonObject endpointConfObj = JsonParser.parseString(api.getEndpointConfig()).getAsJsonObject();
                if (!APIConstants.ENDPOINT_TYPE_SEQUENCE.equals(endpointConfObj.get(API_ENDPOINT_CONFIG_PROTOCOL_TYPE)
                        .getAsString()) && !APIConstants.IMPLEMENTATION_TYPE_INLINE.equalsIgnoreCase(
                        api.getImplementation())) {
                    addEndpoints(api, apiTemplateBuilder, productAPIDto, null);
                }
            } else {
                addEndpoints(api, apiTemplateBuilder, productAPIDto, null);
            }
            setCustomSequencesToBeAdded(apiProduct, api, productAPIDto, apiExtractedPath, apidto);
            setAPIFaultSequencesToBeAdded(api, productAPIDto, apiExtractedPath, apidto);
            String prefix = id.getName() + "--v" + id.getVersion();
            setSecureVaultPropertyToBeAdded(prefix, api, productAPIDto);
        }
        productAPIDto.setApiDefinition(builder.getConfigStringForTemplate(environment));
        return productAPIDto;
    }

    private static void setCustomSequencesToBeAdded(APIProduct apiProduct, API api, GatewayAPIDTO gatewayAPIDTO,
                                                    String extractedPath, APIDTO apidto) throws APIManagementException {

        if (APIUtil.isSequenceDefined(api.getInSequence()) || APIUtil.isSequenceDefined(api.getOutSequence())) {
            GatewayContentDTO gatewayInContentDTO = retrieveSequence(apiProduct, extractedPath,
                    apidto.getMediationPolicies(), APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN, api);
            if (gatewayInContentDTO != null) {
                gatewayAPIDTO.setSequenceToBeAdd(addGatewayContentToList(gatewayInContentDTO,
                        gatewayAPIDTO.getSequenceToBeAdd()));
            }
            GatewayContentDTO gatewayOutContentDTO = retrieveSequence(apiProduct, extractedPath,
                    apidto.getMediationPolicies(), APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT, api);
            if (gatewayOutContentDTO != null) {
                gatewayAPIDTO.setSequenceToBeAdd(addGatewayContentToList(gatewayOutContentDTO,
                        gatewayAPIDTO.getSequenceToBeAdd()));
            }
        } else {
            GatewayContentDTO gatewayInContentDTO = retrieveOperationPolicySequenceForProducts(apiProduct, api,
                    extractedPath, APIConstants.OPERATION_SEQUENCE_TYPE_REQUEST);
            if (gatewayInContentDTO != null) {
                gatewayAPIDTO.setSequenceToBeAdd(
                        addGatewayContentToList(gatewayInContentDTO, gatewayAPIDTO.getSequenceToBeAdd()));
            }
            GatewayContentDTO gatewayOutContentDTO = retrieveOperationPolicySequenceForProducts(apiProduct, api,
                    extractedPath, APIConstants.OPERATION_SEQUENCE_TYPE_RESPONSE);
            if (gatewayOutContentDTO != null) {
                gatewayAPIDTO.setSequenceToBeAdd(
                        addGatewayContentToList(gatewayOutContentDTO, gatewayAPIDTO.getSequenceToBeAdd()));
            }
            GatewayContentDTO gatewayFaultContentDTO = retrieveOperationPolicySequenceForProducts(apiProduct, api,
                    extractedPath, APIConstants.OPERATION_SEQUENCE_TYPE_FAULT);
            if (gatewayFaultContentDTO != null) {
                gatewayAPIDTO.setSequenceToBeAdd(
                        addGatewayContentToList(gatewayFaultContentDTO, gatewayAPIDTO.getSequenceToBeAdd()));
            }

            JsonObject endpointConfigMap = JsonParser.parseString(api.getEndpointConfig()).getAsJsonObject();
            if (endpointConfigMap != null && APIConstants.ENDPOINT_TYPE_SEQUENCE.equals(
                    endpointConfigMap.get(API_ENDPOINT_CONFIG_PROTOCOL_TYPE).getAsString())
                    && APIConstants.API_TYPE_HTTP.equals(api.getType())) {
                GatewayContentDTO gatewayCustomBackendSequenceDTO = retrieveSequenceBackendForAPIProduct(api,
                        apiProduct, APIConstants.API_KEY_TYPE_SANDBOX, extractedPath);
                if (gatewayCustomBackendSequenceDTO != null) {
                    gatewayAPIDTO.setSequenceToBeAdd(addGatewayContentToList(gatewayCustomBackendSequenceDTO,
                            gatewayAPIDTO.getSequenceToBeAdd()));
                }
                gatewayCustomBackendSequenceDTO = retrieveSequenceBackendForAPIProduct(api, apiProduct,
                        APIConstants.API_KEY_TYPE_PRODUCTION, extractedPath);
                if (gatewayCustomBackendSequenceDTO != null) {
                    gatewayAPIDTO.setSequenceToBeAdd(addGatewayContentToList(gatewayCustomBackendSequenceDTO,
                            gatewayAPIDTO.getSequenceToBeAdd()));
                }
            }
        }
    }

    private static GatewayAPIDTO createAPIGatewayDTOtoPublishAPI(Environment environment, API api,
                                                         APITemplateBuilder builder, String tenantDomain,
                                                         String extractedPath, APIDTO apidto,
                                                         List<ClientCertificateDTO> productionClientCertificatesDTOList,
                                                         List<ClientCertificateDTO> sandboxClientCertificatesDTOList,
                                                         List<EndpointDTO> endpointList)
            throws APIManagementException, APITemplateException, XMLStreamException {

        GatewayAPIDTO gatewayAPIDTO = new GatewayAPIDTO();
        gatewayAPIDTO.setName(api.getId().getName());
        gatewayAPIDTO.setVersion(api.getId().getVersion());
        gatewayAPIDTO.setProvider(api.getId().getProviderName());
        gatewayAPIDTO.setApiId(api.getUUID());
        gatewayAPIDTO.setApiContext(api.getContext());
        gatewayAPIDTO.setTenantDomain(tenantDomain);
        gatewayAPIDTO.setKeyManagers(api.getKeyManagers());

        String definition;
        boolean isGraphQLSubscriptionAPI = false;
        String endpointConfigString = api.getEndpointConfig();
        org.json.JSONObject endpointConfig = null;
        if (StringUtils.isNotBlank(endpointConfigString)) {
            endpointConfig = new org.json.JSONObject(endpointConfigString);
        }


        if (api.getType() != null && APIConstants.APITransportType.GRAPHQL.toString().equals(api.getType())) {
            //Build schema with additional info
            gatewayAPIDTO.setLocalEntriesToBeRemove(GatewayUtils.addStringToList(api.getUUID() + "_graphQL",
                    gatewayAPIDTO.getLocalEntriesToBeRemove()));
            GatewayContentDTO graphqlLocalEntry = new GatewayContentDTO();
            graphqlLocalEntry.setName(api.getUUID() + "_graphQL");
            graphqlLocalEntry.setContent("<localEntry key=\"" + api.getUUID() + "_graphQL" + "\">" + "<![CDATA[" +
                    api.getGraphQLSchema() + "]]>" + "</localEntry>");
            gatewayAPIDTO.setLocalEntriesToBeAdd(addGatewayContentToList(graphqlLocalEntry,
                    gatewayAPIDTO.getLocalEntriesToBeAdd()));
            gatewayAPIDTO.setGraphQLSchema(api.getGraphQLSchema());
            Set<URITemplate> uriTemplates = new HashSet<>();
            URITemplate template = new URITemplate();
            template.setAuthType("Any");
            template.setHTTPVerb("POST");
            template.setHttpVerbs("POST");
            template.setUriTemplate("/*");
            uriTemplates.add(template);
            api.setUriTemplates(uriTemplates);

            GraphQLSchemaDefinition graphql = new GraphQLSchemaDefinition();
            if (GraphQLSchemaDefinitionUtil.isSubscriptionAvailable(api.getGraphQLSchema())) {
                isGraphQLSubscriptionAPI = true;
                // if subscriptions are available add new URI template with wild card resource without http verb.
                template = new URITemplate();
                template.setUriTemplate("/*");
                uriTemplates.add(template);
                api.setUriTemplates(uriTemplates);
                api.setEndpointConfig(populateSubscriptionEndpointConfig(endpointConfigString));
                addGqlWebSocketTopicMappings(api);
            }
        } else if (api.getType() != null && (APIConstants.APITransportType.HTTP.toString().equals(api.getType())
                || APIConstants.API_TYPE_SOAP.equals(api.getType())
                || APIConstants.API_TYPE_SOAPTOREST.equals(api.getType()))) {
            definition = api.getSwaggerDefinition();
            gatewayAPIDTO.setLocalEntriesToBeRemove(GatewayUtils.addStringToList(api.getUUID(),
                    gatewayAPIDTO.getLocalEntriesToBeRemove()));

            GatewayContentDTO apiLocalEntry = new GatewayContentDTO();
            apiLocalEntry.setName(api.getUUID());
            apiLocalEntry.setContent("<localEntry key=\"" + api.getUUID() + "\">" +
                    definition.replaceAll("&(?!amp;)", "&amp;").
                            replaceAll("<", "&lt;").replaceAll(">", "&gt;")
                    + "</localEntry>");
            gatewayAPIDTO.setLocalEntriesToBeAdd(addGatewayContentToList(apiLocalEntry,
                    gatewayAPIDTO.getLocalEntriesToBeAdd()));
        } else if (api.getType() != null && (APIConstants.APITransportType.WS.toString().equals(api.getType())
                || APIConstants.APITransportType.SSE.toString().equals(api.getType())
                || APIConstants.APITransportType.WEBSUB.toString().equals(api.getType()))) {
            gatewayAPIDTO.setLocalEntriesToBeRemove(GatewayUtils.addStringToList(api.getUUID(),
                    gatewayAPIDTO.getLocalEntriesToBeRemove()));
            definition = api.getAsyncApiDefinition();
            GatewayContentDTO apiLocalEntry = new GatewayContentDTO();
            apiLocalEntry.setName(api.getUUID());
            apiLocalEntry.setContent("<localEntry key=\"" + api.getUUID() + "\">" +
                    definition.replaceAll("&(?!amp;)", "&amp;").
                            replaceAll("<", "&lt;").replaceAll(">", "&gt;")
                    + "</localEntry>");
            gatewayAPIDTO.setLocalEntriesToBeAdd(addGatewayContentToList(apiLocalEntry,
                    gatewayAPIDTO.getLocalEntriesToBeAdd()));
        }

        // If the API exists in the Gateway and If the Gateway type is 'production' and a production url has not been
        // specified Or if the Gateway type is 'sandbox' and a sandbox url has not been specified

        if (endpointConfig != null && !APIConstants.ENDPOINT_TYPE_AWSLAMBDA.equals(
                endpointConfig.get(API_ENDPOINT_CONFIG_PROTOCOL_TYPE)) && (
                (APIConstants.GATEWAY_ENV_TYPE_PRODUCTION.equals(environment.getType())
                        && !APIUtil.isProductionEndpointsExists(api.getEndpointConfig())) || (
                        APIConstants.GATEWAY_ENV_TYPE_SANDBOX.equals(environment.getType())
                                && !APIUtil.isSandboxEndpointsExists(api.getEndpointConfig())))) {
            if (log.isDebugEnabled()) {
                log.debug("Not adding API to environment " + environment.getName() + " since its endpoint URL "
                        + "cannot be found");
            }
            return null;
        }
        GatewayUtils.setCustomSequencesToBeRemoved(api, gatewayAPIDTO);
        setAPIFaultSequencesToBeAdded(api, gatewayAPIDTO, extractedPath, apidto);
        setCustomSequencesToBeAdded(api, gatewayAPIDTO, extractedPath, apidto);
        setClientCertificatesToBeAdded(tenantDomain, gatewayAPIDTO, productionClientCertificatesDTOList,
                sandboxClientCertificatesDTOList);
        boolean isWsApi = APIConstants.APITransportType.WS.toString().equals(api.getType());
        if (isWsApi) {
            addWebsocketTopicMappings(api, apidto);
        }

        //Add the API
        if (APIConstants.IMPLEMENTATION_TYPE_INLINE.equalsIgnoreCase(api.getImplementation())) {
            String prototypeScriptAPI = builder.getConfigStringForPrototypeScriptAPI(environment);
            gatewayAPIDTO.setApiDefinition(prototypeScriptAPI);
        } else if (APIConstants.IMPLEMENTATION_TYPE_ENDPOINT.equalsIgnoreCase(api.getImplementation())) {
            String apiConfig = null;
            if (APIConstants.API_SUBTYPE_AI_API.equals(api.getSubtype())) {

                Map<String, List<SimplifiedEndpoint>> groupedEndpoints = simplifyEndpoints(endpointList).stream()
                        .collect(Collectors.groupingBy(SimplifiedEndpoint::getDeploymentStage));

                List<SimplifiedEndpoint> productionEndpoints = new ArrayList<>(
                        groupedEndpoints.getOrDefault(APIConstants.APIEndpoint.PRODUCTION, Collections.emptyList()));
                List<SimplifiedEndpoint> sandboxEndpoints = new ArrayList<>(
                        groupedEndpoints.getOrDefault(APIConstants.APIEndpoint.SANDBOX, Collections.emptyList()));

                SimplifiedEndpoint defaultProductionEndpoint = Optional.ofNullable(api.getPrimaryProductionEndpointId())
                        .map(id -> findEndpointByUuid(productionEndpoints, id))
                        .orElseGet(() -> productionEndpoints.isEmpty() ? null : productionEndpoints.get(0));

                SimplifiedEndpoint defaultSandboxEndpoint = Optional.ofNullable(api.getPrimarySandboxEndpointId())
                        .map(id -> findEndpointByUuid(sandboxEndpoints, id))
                        .orElseGet(() -> sandboxEndpoints.isEmpty() ? null : sandboxEndpoints.get(0));

                if (defaultProductionEndpoint != null) {
                    addEndpointsSequence(APIConstants.APIEndpoint.PRODUCTION, productionEndpoints,
                            defaultProductionEndpoint, api,
                            gatewayAPIDTO, builder);
                }
                if (defaultSandboxEndpoint != null) {
                    addEndpointsSequence(APIConstants.APIEndpoint.SANDBOX, sandboxEndpoints,
                            defaultSandboxEndpoint, api,
                            gatewayAPIDTO, builder);
                }
                apiConfig = builder.getConfigStringForAIAPI(environment, defaultProductionEndpoint,
                 defaultSandboxEndpoint);
            } else {
                apiConfig = builder.getConfigStringForTemplate(environment);
            }
            gatewayAPIDTO.setApiDefinition(apiConfig);
            if (endpointConfig != null && !endpointConfig.get(API_ENDPOINT_CONFIG_PROTOCOL_TYPE)
                    .equals(APIConstants.ENDPOINT_TYPE_AWSLAMBDA) && !endpointConfig.get(
                    API_ENDPOINT_CONFIG_PROTOCOL_TYPE).equals(APIConstants.ENDPOINT_TYPE_SEQUENCE)) {
                if (!isWsApi) {
                    addEndpoints(api, builder, gatewayAPIDTO, endpointList);
                }
                if (isWsApi || isGraphQLSubscriptionAPI) {
                    addWebSocketResourceEndpoints(api, builder, gatewayAPIDTO);
                }
            }
        }
        setSecureVaultPropertyToBeAdded(null, api, gatewayAPIDTO);
        return gatewayAPIDTO;
    }

    private static void addEndpointsSequence(String type, List<SimplifiedEndpoint> endpoints,
                                             SimplifiedEndpoint defaultEndpoint, API api, GatewayAPIDTO gatewayAPIDTO
            , APITemplateBuilder builder) throws APIManagementException, XMLStreamException, APITemplateException {

        String endpointsString = builder.getStringForEndpoints(type, endpoints, defaultEndpoint);
        OMElement endpointsElement = APIUtil.buildOMElement(
                new ByteArrayInputStream(endpointsString.getBytes()));

        if (endpointsElement != null) {
            QName nameAttribute = new QName(APIConstants.OM_ELEMENT_NAME);
            if (endpointsElement.getAttribute(nameAttribute) != null) {
                endpointsElement.getAttribute(nameAttribute).setAttributeValue(
                        getEndpointKey(api) + AIAPIConstants.ENDPOINT_SEQUENCE + type);
            }
            GatewayContentDTO endpointSequence = new GatewayContentDTO();
            endpointSequence.setName(getEndpointKey(api) + AIAPIConstants.ENDPOINT_SEQUENCE + type);
            endpointSequence.setContent(APIUtil.convertOMtoString(endpointsElement));
            gatewayAPIDTO.setSequenceToBeAdd(
                    addGatewayContentToList(endpointSequence, gatewayAPIDTO.getSequenceToBeAdd()));
        }
    }

    /**
     * Finds an endpoint by its unique identifier.
     *
     * @param endpointList The list of endpoints to search
     * @param endpointUuid The UUID of the endpoint to find
     * @return The matching {@link EndpointDTO} if found, otherwise null
     */
    public static SimplifiedEndpoint findEndpointByUuid(List<SimplifiedEndpoint> endpointList,
                                                           String endpointUuid) {

        return endpointList.stream()
                .filter(endpoint -> endpointUuid.equals(endpoint.getEndpointUuid()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Simplifies a list of EndpointDTO objects into a list of SimplifiedEndpointDTO objects.
     *
     * @param endpoints The list of endpoints to simplify
     * @return A list of simplified endpoint DTOs
     */
    public static List<SimplifiedEndpoint> simplifyEndpoints(List<EndpointDTO> endpoints) {

        if (endpoints == null || endpoints.isEmpty()) {
            return new ArrayList<>();
        }
        return endpoints.stream()
                .map(SimplifiedEndpoint::new)
                .collect(Collectors.toList());
    }

    private static void addWebsocketTopicMappings(API api, APIDTO apidto) {

        org.json.JSONObject endpointConfiguration = new org.json.JSONObject(api.getEndpointConfig());
        String sandboxEndpointUrl = !endpointConfiguration.isNull(APIConstants.API_DATA_SANDBOX_ENDPOINTS) ?
                endpointConfiguration.getJSONObject(APIConstants.API_DATA_SANDBOX_ENDPOINTS).getString("url") : null;
        String productionEndpointUrl = !endpointConfiguration.isNull(APIConstants.API_DATA_PRODUCTION_ENDPOINTS) ?
                endpointConfiguration.getJSONObject(APIConstants.API_DATA_PRODUCTION_ENDPOINTS).getString("url") : null;

        Map<String, Map<String, String>> perTopicMappings = new HashMap<>();
        for (APIOperationsDTO operation : apidto.getOperations()) {
            String key = operation.getTarget();
            String mapping = operation.getUriMapping() == null ? "" : operation.getUriMapping();
            Map<String, String> endpoints = new HashMap<>();
            if (sandboxEndpointUrl != null) {
                endpoints.put(APIConstants.GATEWAY_ENV_TYPE_SANDBOX, sandboxEndpointUrl + mapping);
            }
            if (productionEndpointUrl != null) {
                endpoints.put(APIConstants.GATEWAY_ENV_TYPE_PRODUCTION, productionEndpointUrl + mapping);
            }
            perTopicMappings.put(key, endpoints);
        }

        api.setWebSocketTopicMappingConfiguration(new WebSocketTopicMappingConfiguration(perTopicMappings));
        addWebsocketTopicResourceKeys(api);
    }

    /**
     * This method is used to add websocket topic mappings for GraphQL subscription. Here both production and sandbox
     * endpoint urls are added under single wild card resource.
     *
     * @param api GraphQL API
     */
    public static void addGqlWebSocketTopicMappings(API api) {

        org.json.JSONObject endpointConfiguration =
                new org.json.JSONObject(api.getEndpointConfig()).getJSONObject(APIConstants.WS_PROTOCOL);
        String sandboxEndpointUrl = !endpointConfiguration.isNull(APIConstants.API_DATA_SANDBOX_ENDPOINTS) ?
                endpointConfiguration.getJSONObject(APIConstants.API_DATA_SANDBOX_ENDPOINTS).getString(
                        APIConstants.ENDPOINT_URL) : null;
        String productionEndpointUrl = !endpointConfiguration.isNull(APIConstants.API_DATA_PRODUCTION_ENDPOINTS) ?
                endpointConfiguration.getJSONObject(APIConstants.API_DATA_PRODUCTION_ENDPOINTS)
                        .getString(APIConstants.ENDPOINT_URL) : null;

        Map<String, String> endpoints = new HashMap<>();
        if (sandboxEndpointUrl != null) {
            endpoints.put(APIConstants.GATEWAY_ENV_TYPE_SANDBOX, sandboxEndpointUrl);
        }
        if (productionEndpointUrl != null) {
            endpoints.put(APIConstants.GATEWAY_ENV_TYPE_PRODUCTION, productionEndpointUrl);
        }
        Map<String, Map<String, String>> perTopicMappings = new HashMap<>();
        perTopicMappings.put("/*", endpoints);
        api.setWebSocketTopicMappingConfiguration(new WebSocketTopicMappingConfiguration(perTopicMappings));
        addWebsocketTopicResourceKeys(api);
    }

    private static void setCustomSequencesToBeAdded(API api, GatewayAPIDTO gatewayAPIDTO, String extractedPath,
                                                    APIDTO apidto) throws APIManagementException {

        if (APIUtil.isSequenceDefined(api.getInSequence()) || APIUtil.isSequenceDefined(api.getOutSequence())) {
            // This is to preserve the previous non migrated API project. Mediation files are used and they will be
            // deployed in the gateway as it is.
            GatewayContentDTO gatewayInContentDTO = retrieveSequence(extractedPath, apidto.getMediationPolicies(),
                    APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN, api);
            if (gatewayInContentDTO != null) {
                gatewayAPIDTO.setSequenceToBeAdd(
                        addGatewayContentToList(gatewayInContentDTO, gatewayAPIDTO.getSequenceToBeAdd()));
            }
            GatewayContentDTO gatewayOutContentDTO = retrieveSequence(extractedPath, apidto.getMediationPolicies(),
                    APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT
                    , api);
            if (gatewayOutContentDTO != null) {
                gatewayAPIDTO.setSequenceToBeAdd(
                        addGatewayContentToList(gatewayOutContentDTO, gatewayAPIDTO.getSequenceToBeAdd()));
            }
        } else {
            GatewayContentDTO gatewayInContentDTO =
                    retrieveOperationPolicySequence(extractedPath, api, APIConstants.OPERATION_SEQUENCE_TYPE_REQUEST);
            if (gatewayInContentDTO != null) {
                gatewayAPIDTO.setSequenceToBeAdd(
                        addGatewayContentToList(gatewayInContentDTO, gatewayAPIDTO.getSequenceToBeAdd()));
            }
            GatewayContentDTO gatewayOutContentDTO =
                    retrieveOperationPolicySequence(extractedPath, api, APIConstants.OPERATION_SEQUENCE_TYPE_RESPONSE);
            if (gatewayOutContentDTO != null) {
                gatewayAPIDTO.setSequenceToBeAdd(
                        addGatewayContentToList(gatewayOutContentDTO, gatewayAPIDTO.getSequenceToBeAdd()));
            }
            GatewayContentDTO gatewayFaultContentDTO =
                    retrieveOperationPolicySequence(extractedPath, api, APIConstants.OPERATION_SEQUENCE_TYPE_FAULT);
            if (gatewayFaultContentDTO != null) {
                gatewayAPIDTO.setSequenceToBeAdd(
                        addGatewayContentToList(gatewayFaultContentDTO, gatewayAPIDTO.getSequenceToBeAdd()));
            }
        }
        Map<String, Object> endpointConfigMap = (Map) apidto.getEndpointConfig();

        if (endpointConfigMap != null && APIConstants.ENDPOINT_TYPE_SEQUENCE.equals(
                endpointConfigMap.get(API_ENDPOINT_CONFIG_PROTOCOL_TYPE))) {
            GatewayContentDTO gatewayCustomBackendSequenceDTO = retrieveCustomBackendSequence(api,
                    APIConstants.API_KEY_TYPE_SANDBOX, extractedPath);
            if (gatewayCustomBackendSequenceDTO != null) {
                gatewayAPIDTO.setSequenceToBeAdd(
                        addGatewayContentToList(gatewayCustomBackendSequenceDTO, gatewayAPIDTO.getSequenceToBeAdd()));
            }
            gatewayCustomBackendSequenceDTO = retrieveCustomBackendSequence(api, APIConstants.API_KEY_TYPE_PRODUCTION,
                    extractedPath);
            if (gatewayCustomBackendSequenceDTO != null) {
                gatewayAPIDTO.setSequenceToBeAdd(
                        addGatewayContentToList(gatewayCustomBackendSequenceDTO, gatewayAPIDTO.getSequenceToBeAdd()));
            }
        }
    }

    private static void setAPIFaultSequencesToBeAdded(API api, GatewayAPIDTO gatewayAPIDTO, String extractedPath,
                                                      APIDTO apidto)
            throws APIManagementException {

        String faultSeqExt = APIUtil.getSequenceExtensionName(api) + APIConstants.API_CUSTOM_SEQ_FAULT_EXT;
        if (APIUtil.isSequenceDefined(api.getFaultSequence())) {
            gatewayAPIDTO.setSequencesToBeRemove(
                    GatewayUtils.addStringToList(faultSeqExt, gatewayAPIDTO.getSequencesToBeRemove()));
            List<MediationPolicyDTO> mediationPolicies = apidto.getMediationPolicies();
            GatewayContentDTO faultSequenceContent =
                    retrieveSequence(extractedPath, mediationPolicies, APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT,
                            api);
            if (faultSequenceContent != null) {
                gatewayAPIDTO.setSequenceToBeAdd(
                        addGatewayContentToList(faultSequenceContent, gatewayAPIDTO.getSequenceToBeAdd()));
            }
        }
        gatewayAPIDTO.setSequencesToBeRemove(
                GatewayUtils.addStringToList(faultSeqExt, gatewayAPIDTO.getSequencesToBeRemove()));
    }

    /**
     * To deploy client certificate in given API environment.
     *
     * @param tenantDomain              Tenant domain.
     * @param productionClientCertificatesDTOList
     * @param sandboxClientCertificatesDTOList
     */
    private static void setClientCertificatesToBeAdded(String tenantDomain, GatewayAPIDTO gatewayAPIDTO,
                                                       List<ClientCertificateDTO> productionClientCertificatesDTOList,
                                                       List<ClientCertificateDTO> sandboxClientCertificatesDTOList) {

        int tenantId = APIUtil.getTenantIdFromTenantDomain(tenantDomain);

        if (productionClientCertificatesDTOList != null) {
            for (ClientCertificateDTO clientCertificateDTO : productionClientCertificatesDTOList) {
                GatewayContentDTO clientCertificate = new GatewayContentDTO();
                clientCertificate.setName(APIConstants.API_KEY_TYPE_PRODUCTION + "_" + clientCertificateDTO.getAlias()
                        + "_" + tenantId);
                clientCertificate.setContent(clientCertificateDTO.getCertificate());
                gatewayAPIDTO.setClientCertificatesToBeAdd(addGatewayContentToList(clientCertificate,
                        gatewayAPIDTO.getClientCertificatesToBeAdd()));
            }
        }
        if (sandboxClientCertificatesDTOList != null) {
            for (ClientCertificateDTO clientCertificateDTO : sandboxClientCertificatesDTOList) {
                GatewayContentDTO clientCertificate = new GatewayContentDTO();
                clientCertificate.setName(APIConstants.API_KEY_TYPE_SANDBOX + "_" + clientCertificateDTO.getAlias()
                        + "_" + tenantId);
                clientCertificate.setContent(clientCertificateDTO.getCertificate());
                gatewayAPIDTO.setClientCertificatesToBeAdd(addGatewayContentToList(clientCertificate,
                        gatewayAPIDTO.getClientCertificatesToBeAdd()));
            }
        }
    }

    public static GatewayContentDTO[] addGatewayContentToList(GatewayContentDTO gatewayContentDTO,
                                                               GatewayContentDTO[] gatewayContents) {

        if (gatewayContents == null) {
            return new GatewayContentDTO[]{gatewayContentDTO};
        } else {
            Set<GatewayContentDTO> gatewayContentDTOList = new HashSet<>();
            Collections.addAll(gatewayContentDTOList, gatewayContents);
            gatewayContentDTOList.add(gatewayContentDTO);
            return gatewayContentDTOList.toArray(new GatewayContentDTO[gatewayContentDTOList.size()]);
        }
    }

    /**
     * This method is used to merge the list of GatewayContentDTO with the existing array of GatewayContentDTOs.
     *
     * @param gatewayContentDTOList List of GatewayContentDTOs to add
     * @param gatewayContents       Array of gateway contents to add
     * @return An array of GatewayContentDTOs
     */
    private static GatewayContentDTO[] addGatewayContentsToList(List<GatewayContentDTO> gatewayContentDTOList,
                                                                GatewayContentDTO[] gatewayContents) {

        if (gatewayContents != null) {
            Collections.addAll(gatewayContentDTOList, gatewayContents);
        }
        return gatewayContentDTOList.toArray(new GatewayContentDTO[gatewayContentDTOList.size()]);
    }

    private static void addEndpoints(API api, APITemplateBuilder builder, GatewayAPIDTO gatewayAPIDTO,
                                     List<EndpointDTO> endpointDTOList)
            throws APITemplateException, XMLStreamException {

        if (endpointDTOList != null && !endpointDTOList.isEmpty()) {
            for (EndpointDTO endpointDTO : endpointDTOList) {
                String endpointType = (APIConstants.APIEndpoint.PRODUCTION.equals(endpointDTO.getDeploymentStage())) ?
                        APIConstants.API_DATA_PRODUCTION_ENDPOINTS : APIConstants.API_DATA_SANDBOX_ENDPOINTS;
                String endpointConfigContext = builder
                        .getConfigStringEndpointConfigTemplate(endpointType,
                                endpointDTO.getId(), endpointDTO.getEndpointConfig());
                GatewayContentDTO endpoint = new GatewayContentDTO();
                endpoint.setName(getEndpointKey(api) + "_API_LLMEndpoint_" + endpointDTO.getId());
                endpoint.setContent(endpointConfigContext);
                gatewayAPIDTO.setEndpointEntriesToBeAdd(addGatewayContentToList(endpoint,
                        gatewayAPIDTO.getEndpointEntriesToBeAdd())
                );
            }
        } else {
            ArrayList<String> arrayListToAdd = getEndpointType(api);
            for (String type : arrayListToAdd) {
                String endpointConfigContext = builder.getConfigStringEndpointConfigTemplate(type, null, null);
                GatewayContentDTO endpoint = new GatewayContentDTO();
                endpoint.setName(getEndpointName(endpointConfigContext));
                endpoint.setContent(endpointConfigContext);
                gatewayAPIDTO.setEndpointEntriesToBeAdd(addGatewayContentToList(endpoint,
                        gatewayAPIDTO.getEndpointEntriesToBeAdd()));
            }
        }
    }

    /**
     * Get the endpoint key name.
     *
     * @param api API that the endpoint belong
     * @return String of endpoint key
     */
    private static String getEndpointKey(API api) {
        return api.getId().getApiName() + "--v" + api.getId().getVersion();
    }

    private static void addWebsocketTopicResourceKeys(API api) {

        WebSocketTopicMappingConfiguration mappingsConfig = api.getWebSocketTopicMappingConfiguration();
        for (String topic : mappingsConfig.getMappings().keySet()) {
            mappingsConfig.setResourceKey(topic, getWebsocketResourceKey(topic));
        }
    }

    private static String getWebsocketResourceKey(String topic) {

        String resourceKey;
        if (topic.contains("{") || (topic.contains("*") && !topic.endsWith("/*"))) {
            resourceKey = "template_" + topic;
        } else {
            resourceKey = "mapping_" + topic;
        }
        return resourceKey.replaceAll("/", "_")
                .replaceAll("\\{", "(")
                .replaceAll("}", ")")
                .replaceAll("\\*", "wildcard");
    }

    public static void addWebSocketResourceEndpoints(API api, APITemplateBuilder builder, GatewayAPIDTO gatewayAPIDTO)
            throws APITemplateException, XMLStreamException {

        Set<URITemplate> uriTemplates = api.getUriTemplates();
        Map<String, Map<String, String>> topicMappings = api.getWebSocketTopicMappingConfiguration().getMappings();
        List<GatewayContentDTO> endpointsToAdd = new ArrayList<>();
        for (URITemplate resource : uriTemplates) {
            String topic = resource.getUriTemplate();
            Map<String, String> endpoints = topicMappings.get(topic);
            // Production and Sandbox endpoints
            for (Map.Entry<String, String> endpointData : endpoints.entrySet()) {
                if (!"resourceKey".equals(endpointData.getKey())) {
                    String endpointType = endpointData.getKey();
                    String endpointUrl = endpointData.getValue();

                    String endpointConfigContext = builder.getConfigStringForWebSocketEndpointTemplate(
                            endpointType, getWebsocketResourceKey(topic), endpointUrl);
                    GatewayContentDTO endpoint = new GatewayContentDTO();
                    // For WS APIs, resource type is not applicable,
                    // so we can just use the uriTemplate/uriMapping to identify the resource
                    endpoint.setName(getEndpointName(endpointConfigContext));
                    endpoint.setContent(endpointConfigContext);
                    endpointsToAdd.add(endpoint);
                }
            }
            // Graphql APIs with subscriptions has only one wild card resource mapping to WS endpoints. Hence, iterating
            // once through resources is enough.
            if (APIConstants.GRAPHQL_API.equals(api.getType())) {
                break;
            }
        }
        gatewayAPIDTO.setEndpointEntriesToBeAdd(addGatewayContentsToList(endpointsToAdd,
                gatewayAPIDTO.getEndpointEntriesToBeAdd()));
    }

    /**
     * Returns the defined endpoint types of the in the publisher.
     *
     * @param api API that the endpoint/s belong
     * @return ArrayList containing defined endpoint types
     */
    public static ArrayList<String> getEndpointType(API api) {

        ArrayList<String> arrayList = new ArrayList<>();
        if (api.isAdvertiseOnly()) {
            api.setEndpointConfig(APIUtil.generateEndpointConfigForAdvertiseOnlyApi(api));
        }
        if (APIUtil.isProductionEndpointsExists(api.getEndpointConfig()) &&
                !APIUtil.isSandboxEndpointsExists(api.getEndpointConfig())) {
            arrayList.add(APIConstants.API_DATA_PRODUCTION_ENDPOINTS);
        } else if (APIUtil.isSandboxEndpointsExists(api.getEndpointConfig()) &&
                !APIUtil.isProductionEndpointsExists(api.getEndpointConfig())) {
            arrayList.add(APIConstants.API_DATA_SANDBOX_ENDPOINTS);
        } else {
            arrayList.add(APIConstants.API_DATA_PRODUCTION_ENDPOINTS);
            arrayList.add(APIConstants.API_DATA_SANDBOX_ENDPOINTS);
        }
        return arrayList;
    }

    private static String getEndpointName(String endpointConfig) throws XMLStreamException {

        OMElement omElement = AXIOMUtil.stringToOM(endpointConfig);
        OMAttribute nameAttribute = omElement.getAttribute(new QName("name"));
        if (nameAttribute != null) {
            return nameAttribute.getAttributeValue();
        } else {
            return null;
        }

    }

    private static void setSecureVaultPropertyToBeAdded(String prefix, API api, GatewayAPIDTO gatewayAPIDTO) {

        boolean isSecureVaultEnabled = Boolean.parseBoolean(ServiceReferenceHolder.getInstance().
                getAPIManagerConfiguration().getFirstProperty(APIConstants.API_SECUREVAULT_ENABLE));

        if (isSecureVaultEnabled) {
            org.json.JSONObject endpointConfig = new org.json.JSONObject(api.getEndpointConfig());

            if (endpointConfig.has(APIConstants.ENDPOINT_SECURITY)) {
                org.json.JSONObject endpoints =
                        (org.json.JSONObject) endpointConfig.get(APIConstants.ENDPOINT_SECURITY);
                org.json.JSONObject productionEndpointSecurity = (org.json.JSONObject)
                        endpoints.get(APIConstants.ENDPOINT_SECURITY_PRODUCTION);
                org.json.JSONObject sandboxEndpointSecurity =
                        (org.json.JSONObject) endpoints.get(APIConstants.ENDPOINT_SECURITY_SANDBOX);

                boolean isProductionEndpointSecured = (boolean)
                        productionEndpointSecurity.get(APIConstants.ENDPOINT_SECURITY_ENABLED);
                boolean isSandboxEndpointSecured = (boolean)
                        sandboxEndpointSecurity.get(APIConstants.ENDPOINT_SECURITY_ENABLED);
                //for production endpoints
                if (isProductionEndpointSecured) {
                    addCredentialsToList(prefix, api, gatewayAPIDTO, productionEndpointSecurity,
                            APIConstants.ENDPOINT_SECURITY_PRODUCTION);
                }
                if (isSandboxEndpointSecured) {
                    addCredentialsToList(prefix, api, gatewayAPIDTO, sandboxEndpointSecurity,
                            APIConstants.ENDPOINT_SECURITY_SANDBOX);

                }
            } else if (APIConstants.ENDPOINT_TYPE_AWSLAMBDA
                    .equals(endpointConfig.get(API_ENDPOINT_CONFIG_PROTOCOL_TYPE))) {
                addAWSCredentialsToList(prefix, api, gatewayAPIDTO, endpointConfig);
            }
        }
    }

    private static void addAWSCredentialsToList(String prefix, API api, GatewayAPIDTO gatewayAPIDTO,
                                                org.json.JSONObject endpointConfig) {

        if (StringUtils.isNotEmpty((String) endpointConfig.get(APIConstants.AMZN_SECRET_KEY))) {
            CredentialDto awsSecretDto = new CredentialDto();
            if (StringUtils.isNotEmpty(prefix)) {
                awsSecretDto.setAlias(prefix.concat("--")
                        .concat(GatewayUtils.retrieveAWSCredAlias(api.getId().getApiName(),
                                api.getId().getVersion(), APIConstants.ENDPOINT_TYPE_AWSLAMBDA)));
            } else {
                awsSecretDto.setAlias(GatewayUtils.retrieveAWSCredAlias(api.getId().getApiName(),
                        api.getId().getVersion(), APIConstants.ENDPOINT_TYPE_AWSLAMBDA));
            }
            awsSecretDto.setPassword((String) endpointConfig.get(APIConstants.AMZN_SECRET_KEY));
            gatewayAPIDTO.setCredentialsToBeAdd(addCredentialsToList(awsSecretDto,
                    gatewayAPIDTO.getCredentialsToBeAdd()));
        }
    }

    private static void addCredentialsToList(String prefix, API api, GatewayAPIDTO gatewayAPIDTO,
                                             org.json.JSONObject endpointSecurity, String type) {

        if (APIConstants.ENDPOINT_SECURITY_TYPE_OAUTH.equalsIgnoreCase((String) endpointSecurity
                .get(APIConstants.ENDPOINT_SECURITY_TYPE))) {
            CredentialDto clientSecretDto = new CredentialDto();
            if (StringUtils.isNotEmpty(prefix)) {
                clientSecretDto.setAlias(prefix.concat("--").concat(GatewayUtils
                        .retrieveOauthClientSecretAlias(api.getId().getApiName(), api.getId().getVersion(), type)));
            } else {
                clientSecretDto.setAlias(GatewayUtils.retrieveOauthClientSecretAlias(api.getId().getApiName()
                        , api.getId().getVersion(), type));
            }
            clientSecretDto.setPassword((String) endpointSecurity
                    .get(APIConstants.ENDPOINT_SECURITY_CLIENT_SECRET));
            gatewayAPIDTO.setCredentialsToBeAdd(addCredentialsToList(clientSecretDto,
                    gatewayAPIDTO.getCredentialsToBeAdd()));
            if (endpointSecurity.has(APIConstants.ENDPOINT_SECURITY_PASSWORD)) {
                CredentialDto passwordDto = new CredentialDto();
                if (StringUtils.isNotEmpty(prefix)) {
                    passwordDto.setAlias(prefix.concat("--").concat(GatewayUtils
                            .retrieveOAuthPasswordAlias(api.getId().getApiName(), api.getId().getVersion(), type)));
                } else {
                    passwordDto.setAlias(GatewayUtils.retrieveOAuthPasswordAlias(api.getId().getApiName()
                            , api.getId().getVersion(), type));
                }
                passwordDto.setPassword((String) endpointSecurity
                        .get(APIConstants.ENDPOINT_SECURITY_PASSWORD));
                gatewayAPIDTO.setCredentialsToBeAdd(addCredentialsToList(passwordDto,
                        gatewayAPIDTO.getCredentialsToBeAdd()));
            }
            if (endpointSecurity.has(APIConstants.PROXY_CONFIGS)) {
                org.json.JSONObject proxyConfigs = (org.json.JSONObject) endpointSecurity.get(APIConstants
                        .PROXY_CONFIGS);
                if (Boolean.TRUE.equals(proxyConfigs.get(APIConstants.PROXY_ENABLED))) {
                    String proxyPassword = (String) proxyConfigs.get(APIConstants.ENDPOINT_SECURITY_PROXY_PASSWORD);
                    CredentialDto proxyPasswordDto = new CredentialDto();
                    if (StringUtils.isNotEmpty(prefix)) {
                        proxyPasswordDto.setAlias(prefix.concat("--").concat(GatewayUtils
                                .retrieveOAuthProxyPasswordAlias(api.getId().getApiName(), api.getId().getVersion(),
                                        type)));
                    } else {
                        proxyPasswordDto.setAlias(GatewayUtils.retrieveOAuthProxyPasswordAlias(api.getId().getApiName(),
                                api.getId().getVersion(), type));
                    }
                    proxyPasswordDto.setPassword(proxyPassword);
                    gatewayAPIDTO.setCredentialsToBeAdd(addCredentialsToList(proxyPasswordDto,
                            gatewayAPIDTO.getCredentialsToBeAdd()));
                }
            }
        } else if (APIConstants.ENDPOINT_SECURITY_TYPE_BASIC.equalsIgnoreCase((String)
                endpointSecurity.get(APIConstants.ENDPOINT_SECURITY_TYPE))) {
            CredentialDto credentialDto = new CredentialDto();
            if (StringUtils.isNotEmpty(prefix)) {
                credentialDto.setAlias(prefix.concat("--").concat(GatewayUtils
                        .retrieveBasicAuthAlias(api.getId().getApiName(), api.getId().getVersion(), type)));
            } else {
                credentialDto.setAlias(GatewayUtils.retrieveBasicAuthAlias(api.getId().getApiName()
                        , api.getId().getVersion(), type));
            }
            credentialDto.setPassword((String)
                    endpointSecurity.get(APIConstants.ENDPOINT_SECURITY_PASSWORD));
            gatewayAPIDTO.setCredentialsToBeAdd(addCredentialsToList(credentialDto,
                    gatewayAPIDTO.getCredentialsToBeAdd()));
        }
    }

    private static CredentialDto[] addCredentialsToList(CredentialDto credential, CredentialDto[] credentials) {

        if (credentials == null) {
            return new CredentialDto[]{credential};
        } else {
            Set<CredentialDto> credentialList = new HashSet<>();
            Collections.addAll(credentialList, credentials);
            credentialList.add(credential);
            return credentialList.toArray(new CredentialDto[credentialList.size()]);
        }
    }

    private static GatewayContentDTO retrieveSequence(APIProduct apiProduct, String pathToAchieve,
                                                      List<MediationPolicyDTO> mediationPolicies,
                                                      String type, API api) throws APIManagementException {

        APIProductIdentifier apiProductIdentifier = apiProduct.getId();
        MediationPolicyDTO mediationPolicyDTO = null;
        for (MediationPolicyDTO mediationPolicy : mediationPolicies) {
            if (type.equalsIgnoreCase(mediationPolicy.getType())) {
                mediationPolicyDTO = mediationPolicy;
                break;
            }
        }
        if (mediationPolicyDTO != null) {
            GatewayContentDTO sequenceContentDto = new GatewayContentDTO();

            String sequenceContent = ImportUtils
                    .retrieveSequenceContent(pathToAchieve, !mediationPolicyDTO.isShared(), type.toLowerCase(),
                            mediationPolicyDTO.getName());
            if (StringUtils.isNotEmpty(sequenceContent)) {
                try {
                    OMElement omElement = APIUtil.buildOMElement(new ByteArrayInputStream(sequenceContent.getBytes()));
                    if (omElement != null) {
                        String seqExt = APIUtil.getSequenceExtensionName(apiProductIdentifier.getName(),
                                apiProductIdentifier.getVersion()).concat("--").concat(api.getUuid());
                        if (APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT.equalsIgnoreCase(type)) {
                            seqExt = seqExt + APIConstants.API_CUSTOM_SEQ_FAULT_EXT;
                        } else if (APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT.equalsIgnoreCase(type)) {
                            seqExt = seqExt + APIConstants.API_CUSTOM_SEQ_OUT_EXT;
                        } else if (APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN.equalsIgnoreCase(type)) {
                            seqExt = seqExt + APIConstants.API_CUSTOM_SEQ_IN_EXT;
                        }

                        if (omElement.getAttribute(new QName("name")) != null) {
                            omElement.getAttribute(new QName("name")).setAttributeValue(seqExt);
                        }
                        sequenceContentDto.setName(seqExt);
                        sequenceContentDto.setContent(APIUtil.convertOMtoString(omElement));
                        return sequenceContentDto;
                    }
                } catch (Exception e) {
                    throw new APIManagementException(e);
                }
            }
        }
        return null;
    }

    private static GatewayContentDTO retrieveSequence(String pathToAchieve, List<MediationPolicyDTO> mediationPolicies,
                                                      String type, API api) throws APIManagementException {

        MediationPolicyDTO mediationPolicyDTO = null;
        for (MediationPolicyDTO mediationPolicy : mediationPolicies) {
            if (type.equalsIgnoreCase(mediationPolicy.getType())) {
                mediationPolicyDTO = mediationPolicy;
                break;
            }
        }
        if (mediationPolicyDTO != null) {
            GatewayContentDTO sequenceContentDto = new GatewayContentDTO();

            String sequenceContent = ImportUtils
                    .retrieveSequenceContent(pathToAchieve, !mediationPolicyDTO.isShared(), type.toLowerCase(),
                            mediationPolicyDTO.getName());
            if (StringUtils.isNotEmpty(sequenceContent)) {
                try {
                    OMElement omElement = APIUtil.buildOMElement(new ByteArrayInputStream(sequenceContent.getBytes()));
                    if (omElement != null) {
                        String seqExt = APIUtil.getSequenceExtensionName(api);

                        if (APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT.equalsIgnoreCase(type)) {
                            seqExt = seqExt + APIConstants.API_CUSTOM_SEQ_FAULT_EXT;
                        } else if (APIConstants.API_CUSTOM_SEQUENCE_TYPE_OUT.equalsIgnoreCase(type)) {
                            seqExt = seqExt + APIConstants.API_CUSTOM_SEQ_OUT_EXT;
                        } else if (APIConstants.API_CUSTOM_SEQUENCE_TYPE_IN.equalsIgnoreCase(type)) {
                            seqExt = seqExt + APIConstants.API_CUSTOM_SEQ_IN_EXT;
                        }

                        if (omElement.getAttribute(new QName("name")) != null) {
                            omElement.getAttribute(new QName("name")).setAttributeValue(seqExt);
                        }
                        sequenceContentDto.setName(seqExt);
                        sequenceContentDto.setContent(APIUtil.convertOMtoString(omElement));
                        return sequenceContentDto;
                    }
                } catch (Exception e) {
                    throw new APIManagementException(e);
                }
            }
        }
        return null;
    }

    private static GatewayContentDTO retrieveOperationPolicySequence(String pathToAchieve, API api, String flow)
            throws APIManagementException {

        GatewayContentDTO operationPolicySequenceContentDto = new GatewayContentDTO();

        String policySequence = null;
        String seqExt = APIUtil.getSequenceExtensionName(api) + SynapsePolicyAggregator.getSequenceExtensionFlow(flow);
        try {
            policySequence = SynapsePolicyAggregator.generatePolicySequenceForUriTemplateSet(api.getUriTemplates(), api,
                    seqExt, flow, pathToAchieve);
        } catch (IOException e) {
            throw new APIManagementException(e);
        }

        if (StringUtils.isNotEmpty(policySequence)) {
            try {
                OMElement omElement = APIUtil.buildOMElement(new ByteArrayInputStream(policySequence.getBytes()));
                if (omElement != null) {
                    if (omElement.getAttribute(new QName("name")) != null) {
                        omElement.getAttribute(new QName("name")).setAttributeValue(seqExt);
                    }
                    operationPolicySequenceContentDto.setName(seqExt);
                    operationPolicySequenceContentDto.setContent(APIUtil.convertOMtoString(omElement));
                    switch (flow) {
                    case APIConstants.OPERATION_SEQUENCE_TYPE_REQUEST:
                        api.setInSequence(seqExt);
                        break;
                    case APIConstants.OPERATION_SEQUENCE_TYPE_RESPONSE:
                        api.setOutSequence(seqExt);
                        break;
                    case APIConstants.OPERATION_SEQUENCE_TYPE_FAULT:
                        api.setFaultSequence(seqExt);
                        break;
                    }
                    return operationPolicySequenceContentDto;
                }
            } catch (Exception e) {
                throw new APIManagementException(e);
            }
        }
        return null;
    }

    private static GatewayContentDTO retrieveSequenceBackendForAPIProduct(API api, APIProduct apiProduct,
            String endpointType, String pathToAchieve) throws APIManagementException {
        GatewayContentDTO customBackendSequenceContentDto = new GatewayContentDTO();
        String customSequence = null;
        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        SequenceBackendData data = apiMgtDAO.getCustomBackendByAPIUUID(api.getUuid(), endpointType);
        if (data != null) {
            String seqExt = data.getName();
            if (!StringUtils.isEmpty(seqExt) && seqExt.contains(".xml")) {
                seqExt = seqExt + ".xml";
            }
            String prodSeqExt = APIUtil.getCustomBackendName(apiProduct.getUuid().concat("-" + api.getUuid()),
                    endpointType);
            try {
                customSequence = SynapsePolicyAggregator.generateSequenceBackendForAPIProducts(seqExt, prodSeqExt,
                        pathToAchieve, endpointType);
            } catch (IOException e) {
                throw new APIManagementException(e);
            }

            if (StringUtils.isNotEmpty(customSequence)) {
                try {
                    OMElement omElement = APIUtil.buildOMElement(new ByteArrayInputStream(customSequence.getBytes()));
                    if (omElement != null) {
                        if (omElement.getAttribute(new QName("name")) != null) {
                            omElement.getAttribute(new QName("name")).setAttributeValue(prodSeqExt);
                        }
                        customBackendSequenceContentDto.setName(prodSeqExt);
                        customBackendSequenceContentDto.setContent(APIUtil.convertOMtoString(omElement));
                        return customBackendSequenceContentDto;
                    }
                } catch (Exception e) {
                    throw new APIManagementException(e);
                }
            }
        }

        return null;
    }

    private static GatewayContentDTO retrieveCustomBackendSequence(API api, String endpointType, String pathToAchieve)
            throws APIManagementException {
        ApiMgtDAO apiMgtDAO = ApiMgtDAO.getInstance();
        GatewayContentDTO customBackendSequenceContentDto = new GatewayContentDTO();
        String customSequence = null;
        SequenceBackendData data = apiMgtDAO.getCustomBackendByAPIUUID(api.getUuid(), endpointType);
        if (data != null) {
            String seqExt = data.getName();
            String apiSeqName = APIUtil.getCustomBackendName(api.getUuid(), endpointType);
            try {
                customSequence = SynapsePolicyAggregator.generateBackendSequenceForCustomSequence(seqExt, pathToAchieve,
                        endpointType, apiSeqName);
            } catch (IOException e) {
                throw new APIManagementException(e);
            }

            if (StringUtils.isNotEmpty(customSequence)) {
                try {
                    OMElement omElement = APIUtil.buildOMElement(new ByteArrayInputStream(customSequence.getBytes()));
                    if (omElement != null) {
                        if (omElement.getAttribute(new QName("name")) != null) {
                            omElement.getAttribute(new QName("name")).setAttributeValue(apiSeqName);
                        }
                        customBackendSequenceContentDto.setName(apiSeqName);
                        customBackendSequenceContentDto.setContent(APIUtil.convertOMtoString(omElement));
                        return customBackendSequenceContentDto;
                    }
                } catch (Exception e) {
                    throw new APIManagementException(e);
                }
            }
        }
        return null;
    }

    private static GatewayContentDTO retrieveOperationPolicySequenceForProducts(APIProduct apiProduct, API api,
                                                                                String extractedLocation, String flow)
            throws APIManagementException {

        Set<URITemplate> applicableURITemplates = new HashSet<>();
        for (APIProductResource productResource : apiProduct.getProductResources()) {
            if (productResource.getApiIdentifier().equals(api.getId())) {
                applicableURITemplates.add(productResource.getUriTemplate());
            }
        }

        String policySequence = null;
        APIProductIdentifier apiProductIdentifier = apiProduct.getId();
        String seqExt = APIUtil.getSequenceExtensionName(apiProductIdentifier.getName(),
                apiProductIdentifier.getVersion())
                .concat("--").concat(api.getUuid()).concat(SynapsePolicyAggregator.getSequenceExtensionFlow(flow));
        try {
            policySequence = SynapsePolicyAggregator.generatePolicySequenceForUriTemplateSet(applicableURITemplates,
                    null, seqExt, flow, extractedLocation);
        } catch (IOException e) {
            throw new APIManagementException(e);
        }

        GatewayContentDTO operationPolicySequenceContentDto = new GatewayContentDTO();
        if (StringUtils.isNotEmpty(policySequence)) {
            try {
                OMElement omElement = APIUtil.buildOMElement(new ByteArrayInputStream(policySequence.getBytes()));
                if (omElement != null) {
                    if (omElement.getAttribute(new QName("name")) != null) {
                        omElement.getAttribute(new QName("name")).setAttributeValue(seqExt);
                    }
                    operationPolicySequenceContentDto.setName(seqExt);
                    operationPolicySequenceContentDto.setContent(APIUtil.convertOMtoString(omElement));

                    for (APIProductResource productResource : apiProduct.getProductResources()) {
                        if (productResource.getApiIdentifier().equals(api.getId())) {
                            switch (flow) {
                                case APIConstants.OPERATION_SEQUENCE_TYPE_REQUEST:
                                    productResource.setInSequenceName(seqExt);
                                    break;
                                case APIConstants.OPERATION_SEQUENCE_TYPE_RESPONSE:
                                    productResource.setOutSequenceName(seqExt);
                                    break;
                                case APIConstants.OPERATION_SEQUENCE_TYPE_FAULT:
                                    productResource.setFaultSequenceName(seqExt);
                                    break;
                            }
                        }
                    }

                    return operationPolicySequenceContentDto;
                }
            } catch (Exception e) {
                throw new APIManagementException(e);
            }
        }
        return null;
    }

    private static Map<String, APIDTO> retrieveAssociatedApis(String extractedPath) throws APIManagementException {

        Map<String, APIDTO> apidtoMap = new HashMap();
        String apisDirectoryPath = extractedPath + File.separator + ImportExportConstants.APIS_DIRECTORY;
        File apisDirectory = new File(apisDirectoryPath);
        File[] apisDirectoryListing = apisDirectory.listFiles();
        if (apisDirectoryListing != null) {
            for (File file : apisDirectoryListing) {
                try {
                    APIDTO apidto = ImportUtils.retrievedAPIDto(file.getAbsolutePath());
                    apidtoMap.put(file.getAbsolutePath(), apidto);
                } catch (IOException e) {
                    throw new APIManagementException("Error while reading api", e);
                }
            }
        }
        return apidtoMap;
    }

    private static Map<String, APIDTO> convertAPIIdToDto(Collection<APIDTO> apidtoSet) {

        Map<String, APIDTO> apidtoMap = new HashMap<>();
        for (APIDTO apidto : apidtoSet) {
            apidtoMap.put(apidto.getId(), apidto);
        }
        return apidtoMap;
    }

    /**
     * Construct the timeout, suspendOnFailure, markForSuspension to add suspend.
     * configuration to the websocket endpoint (Simply assign config values according to the endpoint-template)
     *
     * @param api
     * @param urlType - Whether production or sandbox
     * @return timeout, suspendOnFailure, markForSuspension which will use to construct the endpoint configuration
     */
    private static String[] websocketEndpointConfig(API api, String urlType) throws JSONException {

        org.json.JSONObject obj = new org.json.JSONObject(api.getEndpointConfig());
        org.json.JSONObject endpointObj = null;

        if (ENDPOINT_PRODUCTION.equalsIgnoreCase(urlType)) {
            org.json.JSONObject prodEP = obj.getJSONObject(APIConstants.API_DATA_PRODUCTION_ENDPOINTS);
            if (prodEP.has("config") && prodEP.get("config") instanceof org.json.JSONObject) {
                //if config is not a JSONObject(happens when save the api without changing enpoint config at very
                // first time)
                endpointObj = prodEP.getJSONObject("config");
            } else {
                return new String[]{"", "", ""};
            }
        } else if (ENDPOINT_SANDBOX.equalsIgnoreCase(urlType)) {
            org.json.JSONObject sandEP = obj.getJSONObject(APIConstants.API_DATA_SANDBOX_ENDPOINTS);
            if (sandEP.has("config") && sandEP.get("config") instanceof org.json.JSONObject) {
                //if config is not a JSONObject(happens when save the api without changing enpoint config at very
                // first time)
                endpointObj = sandEP.getJSONObject("config");
            } else {
                return new String[]{"", "", ""};
            }
        }
        String duration = validateJSONObjKey("actionDuration", endpointObj) ? "\t\t<duration>" +
                endpointObj.get("actionDuration") + "</duration>\n" : "";
        String responseAction = validateJSONObjKey("actionSelect", endpointObj) ? "\t\t<responseAction>" +
                endpointObj.get("actionSelect") + "</responseAction>\n" : "";
        String timeout = duration + "\n" + responseAction;
        String retryErrorCode;
        String suspendErrorCode;

        if (validateJSONObjKey("suspendDuration", endpointObj)) {
            //Avoid suspending the endpoint when suspend duration is zero
            if (Integer.parseInt(endpointObj.get("suspendDuration").toString()) == 0) {
                String suspendOnFailure = "\t\t<errorCodes>-1</errorCodes>\n" +
                        "\t\t<initialDuration>0</initialDuration>\n" +
                        "\t\t<progressionFactor>1.0</progressionFactor>\n" +
                        "\t\t<maximumDuration>0</maximumDuration>";
                String markForSuspension = "\t\t<errorCodes>-1</errorCodes>";
                return new String[]{timeout, suspendOnFailure, markForSuspension};
            }
        }
        suspendErrorCode = parseWsEndpointConfigErrorCodes(endpointObj, "suspendErrorCode");
        String suspendDuration = validateJSONObjKey("suspendDuration", endpointObj) ? "\t\t<initialDuration>" +
                endpointObj.get("suspendDuration").toString() + "</initialDuration>" : "";
        String suspendMaxDuration = validateJSONObjKey("suspendMaxDuration", endpointObj) ?
                "\t\t<maximumDuration>" + endpointObj.get("suspendMaxDuration") + "</maximumDuration>" : "";
        String factor = validateJSONObjKey("factor", endpointObj) ? "\t\t<progressionFactor>" +
                endpointObj.get("factor") + "</progressionFactor>" : "";
        String suspendOnFailure = suspendErrorCode + "\n" + suspendDuration + "\n" + suspendMaxDuration + "\n" + factor;

        retryErrorCode = parseWsEndpointConfigErrorCodes(endpointObj,
                "retryErroCode"); //todo: fix typo retryErroCode from client side
        String retryTimeOut = validateJSONObjKey("retryTimeOut", endpointObj) ? "\t\t<retriesBeforeSuspension>" +
                endpointObj.get("retryTimeOut") + "</retriesBeforeSuspension>" : "";
        String retryDelay = validateJSONObjKey("retryDelay", endpointObj) ? "\t\t<retryDelay>" +
                endpointObj.get("retryDelay") + "</retryDelay>" : "";
        String markForSuspension = retryErrorCode + "\n" + retryTimeOut + "\n" + retryDelay;
        return new String[]{timeout, suspendOnFailure, markForSuspension};
    }

    /**
     * Checks if a given key is available in the endpoint config and if it's value is a valid String.
     *
     * @param key         Key that needs to be validated
     * @param endpointObj Endpoint config JSON object
     * @return True if the given key is available with a valid String value
     */
    private static boolean validateJSONObjKey(String key, org.json.JSONObject endpointObj) {

        return endpointObj.has(key) && endpointObj.get(key) instanceof String &&
                StringUtils.isNotEmpty(endpointObj.getString(key));
    }

    /**
     * Parse the error codes defined in the WebSocket endpoint config.
     *
     * @param endpointObj   WebSocket endpoint config JSONObject
     * @param errorCodeType The error code type (retryErroCode/suspendErrorCode)
     * @return The parsed error codes
     */
    private static String parseWsEndpointConfigErrorCodes(org.json.JSONObject endpointObj, String errorCodeType) {

        if (endpointObj.has(errorCodeType)) {
            //When there are/is multiple/single retry error codes
            if (endpointObj.get(errorCodeType) instanceof JSONArray &&
                    ((JSONArray) endpointObj.get(errorCodeType)).length() != 0) {
                StringBuilder codeListBuilder = new StringBuilder();
                for (int i = 0; i < endpointObj.getJSONArray(errorCodeType).length(); i++) {
                    codeListBuilder.append(endpointObj.getJSONArray(errorCodeType).get(i).toString()).append(",");
                }
                String codeList = codeListBuilder.toString();
                return "\t\t<errorCodes>" + codeList.substring(0, codeList.length() - 1) + "</errorCodes>";
            } else if (endpointObj.get(errorCodeType) instanceof String) {
                return "\t\t<errorCodes>" + endpointObj.get(errorCodeType) + "</errorCodes>";
            }
        }
        return "";
    }

    public static JSONObject getModifiedProperties(JSONObject originalProperties) {

        JSONObject modifiedProperties = new JSONObject();
        if (originalProperties.size() > 0) {
            for (Iterator iterator = originalProperties.keySet().iterator(); iterator.hasNext(); ) {
                String key = (String) iterator.next();
                String val = (String) originalProperties.get(key);
                if (key.endsWith("__display")) {
                    modifiedProperties.put(key.replace("__display", ""), val);
                }
                modifiedProperties.put(key, val);
            }
        }
        return modifiedProperties;
    }

    /**
     * This method is update the existing GraphQL API endpoint config with ws endpoint config and
     * existing http endpoint config.
     *
     * @param endpointConfig Current endpoint config
     * @return Updated endpoint config
     * @throws APIManagementException if an error occurs
     */
    public static String populateSubscriptionEndpointConfig(String endpointConfig) throws APIManagementException {

        try {
            JSONObject newEndpointConfigJson = new JSONObject();
            newEndpointConfigJson.put(API_ENDPOINT_CONFIG_PROTOCOL_TYPE,
                    APIConstants.ENDPOINT_TYPE_GRAPHQL);
            JSONObject oldEndpointConfigJson = (JSONObject) new JSONParser().parse(endpointConfig);
            newEndpointConfigJson.put(APIConstants.ENDPOINT_TYPE_HTTP, oldEndpointConfigJson);
            JSONObject wsEndpointConfig = new JSONObject();
            wsEndpointConfig.put(API_ENDPOINT_CONFIG_PROTOCOL_TYPE, APIConstants.WS_PROTOCOL);
            // If production_endpoints exists
            if (oldEndpointConfigJson.get(APIConstants.ENDPOINT_PRODUCTION_ENDPOINTS) != null) {
                JSONObject prodWSEndpointConfig;
                String prodWsEndpoint = "";
                // If load_balanced endpoints get the first prod endpoint url from the list
                if (APIConstants.ENDPOINT_TYPE_LOADBALANCE.equals(
                        oldEndpointConfigJson.get(API_ENDPOINT_CONFIG_PROTOCOL_TYPE))) {
                    // get first load balanced endpoint
                    prodWsEndpoint = (String) ((JSONObject) ((org.json.simple.JSONArray) oldEndpointConfigJson
                            .get(APIConstants.ENDPOINT_PRODUCTION_ENDPOINTS)).get(0)).get(APIConstants.ENDPOINT_URL);

                } else if (((JSONObject) oldEndpointConfigJson.get(APIConstants.ENDPOINT_PRODUCTION_ENDPOINTS))
                        .get(APIConstants.ENDPOINT_URL) != null) {
                    prodWsEndpoint = (String)
                            ((JSONObject) oldEndpointConfigJson.get(APIConstants.ENDPOINT_PRODUCTION_ENDPOINTS))
                                    .get(APIConstants.ENDPOINT_URL);
                }
                //Replace https:// prefix with wss:// and http:// with ws://. Skip for default endpoints
                if (prodWsEndpoint.indexOf(APIConstants.HTTP_PROTOCOL_URL_PREFIX) == 0) {
                    prodWsEndpoint = prodWsEndpoint.replace(APIConstants.HTTP_PROTOCOL_URL_PREFIX,
                            APIConstants.WS_PROTOCOL_URL_PREFIX);
                } else if (prodWsEndpoint.indexOf(APIConstants.HTTPS_PROTOCOL_URL_PREFIX) == 0) {
                    prodWsEndpoint = prodWsEndpoint.replace(APIConstants.HTTPS_PROTOCOL_URL_PREFIX,
                            APIConstants.WSS_PROTOCOL_URL_PREFIX);
                } else if (!APIConstants.ENDPOINT_TYPE_DEFAULT.equals(prodWsEndpoint)) {
                    // supported uri schemes for url are https://, http:// or default
                    throw new APIManagementException("Unsupported URI scheme present for Production endpoint: "
                            + prodWsEndpoint);
                }
                prodWSEndpointConfig = new JSONObject();
                prodWSEndpointConfig.put(APIConstants.ENDPOINT_URL, prodWsEndpoint);
                wsEndpointConfig.put(APIConstants.ENDPOINT_PRODUCTION_ENDPOINTS, prodWSEndpointConfig);
            }
            // If sandbox_endpoints exists
            if (oldEndpointConfigJson.get(APIConstants.ENDPOINT_SANDBOX_ENDPOINTS) != null) {
                JSONObject sandboxWSEndpointConfig;
                String sandboxWsEndpoint = "";
                // If load_balanced endpoints get the first sandbox endpoint url from the list
                if (APIConstants.ENDPOINT_TYPE_LOADBALANCE.equals(
                        oldEndpointConfigJson.get(API_ENDPOINT_CONFIG_PROTOCOL_TYPE))) {
                    // get first load balanced endpoint
                    sandboxWsEndpoint = (String) ((JSONObject) ((org.json.simple.JSONArray) oldEndpointConfigJson
                            .get(APIConstants.ENDPOINT_SANDBOX_ENDPOINTS)).get(0)).get(APIConstants.ENDPOINT_URL);

                } else if (((JSONObject) oldEndpointConfigJson.get(APIConstants.ENDPOINT_SANDBOX_ENDPOINTS))
                        .get(APIConstants.ENDPOINT_URL) != null) {
                    sandboxWsEndpoint = (String)
                            ((JSONObject) oldEndpointConfigJson.get(APIConstants.ENDPOINT_SANDBOX_ENDPOINTS))
                                    .get(APIConstants.ENDPOINT_URL);
                }
                if (sandboxWsEndpoint.indexOf(APIConstants.HTTP_PROTOCOL_URL_PREFIX) == 0) {
                    sandboxWsEndpoint = sandboxWsEndpoint.replace(APIConstants.HTTP_PROTOCOL_URL_PREFIX,
                            APIConstants.WS_PROTOCOL_URL_PREFIX);
                } else if (sandboxWsEndpoint.indexOf(APIConstants.HTTPS_PROTOCOL_URL_PREFIX) == 0) {
                    sandboxWsEndpoint = sandboxWsEndpoint.replace(APIConstants.HTTPS_PROTOCOL_URL_PREFIX,
                            APIConstants.WSS_PROTOCOL_URL_PREFIX);
                } else if (!APIConstants.ENDPOINT_TYPE_DEFAULT.equals(sandboxWsEndpoint)) {
                    throw new APIManagementException("Unsupported URI scheme present for Sandbox endpoint: "
                            + sandboxWsEndpoint);
                }
                sandboxWSEndpointConfig = new JSONObject();
                sandboxWSEndpointConfig.put(APIConstants.ENDPOINT_URL, sandboxWsEndpoint);
                wsEndpointConfig.put(APIConstants.ENDPOINT_SANDBOX_ENDPOINTS, sandboxWSEndpointConfig);
            }
            newEndpointConfigJson.put(APIConstants.WS_PROTOCOL, wsEndpointConfig);
            if (log.isDebugEnabled()) {
                log.debug("Derived endpoint config for GraphQL API with subscriptions: "
                        + newEndpointConfigJson.toJSONString());
            }
            return newEndpointConfigJson.toJSONString();
        } catch (ParseException e) {
            throw new APIManagementException("Error while deriving subscription endpoint from GraphQL API endpoint "
                    + "config: " + endpointConfig, e);
        }
    }

}
