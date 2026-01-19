/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.planning.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for HTTP API-based option sources.
 * <p>
 * Enables fetching parameter options from external HTTP APIs with:
 * <ul>
 *   <li>Full HTTP request configuration (URL, method, headers, body, timeout)</li>
 *   <li>JSONPath extraction for label and value fields</li>
 *   <li>Authentication support (BASIC, BEARER, API_KEY)</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>
 * HttpOptionsConfig config = new HttpOptionsConfig();
 * config.setUrl("https://api.example.com/users");
 * config.setMethod("GET");
 * config.setLabelPath("$.data[*].name");
 * config.setValuePath("$.data[*].id");
 * config.setTimeout(3000);
 *
 * HttpOptionsConfig.AuthConfig auth = new HttpOptionsConfig.AuthConfig();
 * auth.setType("BEARER");
 * auth.setToken("my-secret-token");
 * config.setAuthentication(auth);
 * </pre>
 *
 * @author Assistant Agent Framework
 * @since 0.1.1
 */
public class HttpOptionsConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * HTTP endpoint URL to fetch options from.
     * <p>
     * Example: "https://api.example.com/users"
     */
    private String url;

    /**
     * HTTP method to use.
     * <p>
     * Default: "GET"
     * <p>
     * Common values: "GET", "POST", "PUT", "PATCH"
     */
    private String method = "GET";

    /**
     * HTTP headers to include in the request.
     * <p>
     * Example: {"Authorization": "Bearer token", "Content-Type": "application/json"}
     */
    private Map<String, String> headers;

    /**
     * Request body for POST/PUT/PATCH requests.
     * <p>
     * Usually JSON string: "{\"query\": \"active users\"}"
     */
    private String body;

    /**
     * Request timeout in milliseconds.
     * <p>
     * Default: 5000 (5 seconds)
     */
    private int timeout = 5000;

    /**
     * JSONPath expression to extract label field from response.
     * <p>
     * Example: "$.data[*].name" or "$.users[*].displayName"
     */
    private String labelPath;

    /**
     * JSONPath expression to extract value field from response.
     * <p>
     * Example: "$.data[*].id" or "$.users[*].userId"
     */
    private String valuePath;

    /**
     * Authentication configuration for the HTTP request.
     * <p>
     * Supports BASIC, BEARER, and API_KEY authentication types.
     */
    private AuthConfig authentication;

    /**
     * Authentication configuration for HTTP requests.
     * <p>
     * Supports multiple authentication types:
     * <ul>
     *   <li>BASIC: username + password</li>
     *   <li>BEARER: token</li>
     *   <li>API_KEY: key name + key value + location (header/query)</li>
     * </ul>
     */
    public static class AuthConfig implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * Authentication type.
         * <p>
         * Common values: "BASIC", "BEARER", "API_KEY"
         */
        private String type;

        /**
         * Username for BASIC authentication.
         */
        private String username;

        /**
         * Password for BASIC authentication.
         */
        private String password;

        /**
         * Token for BEARER authentication.
         */
        private String token;

        /**
         * API key name for API_KEY authentication.
         * <p>
         * Example: "X-API-Key"
         */
        private String keyName;

        /**
         * API key value for API_KEY authentication.
         */
        private String keyValue;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getKeyName() {
            return keyName;
        }

        public void setKeyName(String keyName) {
            this.keyName = keyName;
        }

        public String getKeyValue() {
            return keyValue;
        }

        public void setKeyValue(String keyValue) {
            this.keyValue = keyValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            AuthConfig that = (AuthConfig) o;
            return Objects.equals(type, that.type) && Objects.equals(username, that.username)
                    && Objects.equals(password, that.password) && Objects.equals(token, that.token)
                    && Objects.equals(keyName, that.keyName) && Objects.equals(keyValue, that.keyValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, username, password, token, keyName, keyValue);
        }

        @Override
        public String toString() {
            return "AuthConfig{" + "type='" + type + '\'' + ", username='" + username + '\'' + ", password='***'"
                    + ", token='***'" + ", keyName='" + keyName + '\'' + ", keyValue='***'" + '}';
        }

    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public String getLabelPath() {
        return labelPath;
    }

    public void setLabelPath(String labelPath) {
        this.labelPath = labelPath;
    }

    public String getValuePath() {
        return valuePath;
    }

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

    public AuthConfig getAuthentication() {
        return authentication;
    }

    public void setAuthentication(AuthConfig authentication) {
        this.authentication = authentication;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        HttpOptionsConfig that = (HttpOptionsConfig) o;
        return timeout == that.timeout && Objects.equals(url, that.url) && Objects.equals(method, that.method)
                && Objects.equals(headers, that.headers) && Objects.equals(body, that.body)
                && Objects.equals(labelPath, that.labelPath) && Objects.equals(valuePath, that.valuePath)
                && Objects.equals(authentication, that.authentication);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method, headers, body, timeout, labelPath, valuePath, authentication);
    }

    @Override
    public String toString() {
        return "HttpOptionsConfig{" + "url='" + url + '\'' + ", method='" + method + '\'' + ", headers=" + headers
                + ", body='" + body + '\'' + ", timeout=" + timeout + ", labelPath='" + labelPath + '\''
                + ", valuePath='" + valuePath + '\'' + ", authentication=" + authentication + '}';
    }

}
