/*
 * Copyright (c) 2024, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
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

package org.wso2.carbon.apimgt.api.model;

public class AIEndpointConfiguration {

    private String authKey;
    private String sandboxAuthValue;
    private String productionAuthValue;
    private String authType;

    public String getAuthKey() {

        return authKey;
    }

    public void setAuthKey(String authKey) {

        this.authKey = authKey;
    }

    public String getSandboxAuthValue() {

        return sandboxAuthValue;
    }

    public void setSandboxAuthValue(String sandboxAuthValue) {

        this.sandboxAuthValue = sandboxAuthValue;
    }

    public String getProductionAuthValue() {

        return productionAuthValue;
    }

    public void setProductionAuthValue(String productionAuthValue) {

        this.productionAuthValue = productionAuthValue;
    }

    public String getAuthType() {

        return authType;
    }

    public void setAuthType(String authType) {

        this.authType = authType;
    }
}
