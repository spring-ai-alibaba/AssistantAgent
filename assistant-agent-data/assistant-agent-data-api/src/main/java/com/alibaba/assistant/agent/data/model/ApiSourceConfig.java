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
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * API-based options source configuration.
 * <p>
 * Defines how to fetch parameter options from an HTTP API endpoint.
 * The API response should return a JSON array or object containing
 * the options list. Use JSONPath to extract the list and field names
 * to identify label and value fields.
 * <p>
 * Example usage:
 * <pre>
 * ApiSourceConfig config = new ApiSourceConfig();
 * config.setUrl("https://api.example.com/departments");
 * config.setMethod("GET");
 * config.setResponsePath("$.data");
 * config.setLabelField("name");
 * config.setValueField("id");
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ApiSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The API endpoint URL.
     */
    private String url;

    /**
     * The HTTP method (GET, POST, etc.). Defaults to GET.
     */
    private String method = "GET";

    /**
     * HTTP headers to include in the request.
     */
    private Map<String, String> headers = new HashMap<>();

    /**
     * The request body (for POST/PUT requests).
     * Should be a JSON string or template.
     */
    private String requestBody;

    /**
     * JSONPath expression to extract the options list from the response.
     * Example: "$.data" or "$.result.items"
     */
    private String responsePath;

    /**
     * The field name in each option object containing the display label.
     */
    private String labelField;

    /**
     * The field name in each option object containing the actual value.
     */
    private String valueField;

    /**
     * Gets the API URL.
     *
     * @return the API URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the API URL.
     *
     * @param url the API URL to set
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Gets the HTTP method.
     *
     * @return the HTTP method (GET, POST, etc.)
     */
    public String getMethod() {
        return method;
    }

    /**
     * Sets the HTTP method.
     *
     * @param method the HTTP method to set
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * Gets the HTTP headers.
     *
     * @return the HTTP headers map
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Sets the HTTP headers.
     *
     * @param headers the HTTP headers map to set
     */
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    /**
     * Gets the request body.
     *
     * @return the request body string
     */
    public String getRequestBody() {
        return requestBody;
    }

    /**
     * Sets the request body.
     *
     * @param requestBody the request body string to set
     */
    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    /**
     * Gets the JSONPath expression for extracting the options list.
     *
     * @return the JSONPath expression
     */
    public String getResponsePath() {
        return responsePath;
    }

    /**
     * Sets the JSONPath expression for extracting the options list.
     *
     * @param responsePath the JSONPath expression to set
     */
    public void setResponsePath(String responsePath) {
        this.responsePath = responsePath;
    }

    /**
     * Gets the label field name.
     *
     * @return the label field name
     */
    public String getLabelField() {
        return labelField;
    }

    /**
     * Sets the label field name.
     *
     * @param labelField the label field name to set
     */
    public void setLabelField(String labelField) {
        this.labelField = labelField;
    }

    /**
     * Gets the value field name.
     *
     * @return the value field name
     */
    public String getValueField() {
        return valueField;
    }

    /**
     * Sets the value field name.
     *
     * @param valueField the value field name to set
     */
    public void setValueField(String valueField) {
        this.valueField = valueField;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApiSourceConfig that = (ApiSourceConfig) o;
        return Objects.equals(url, that.url) &&
                Objects.equals(method, that.method) &&
                Objects.equals(headers, that.headers) &&
                Objects.equals(requestBody, that.requestBody) &&
                Objects.equals(responsePath, that.responsePath) &&
                Objects.equals(labelField, that.labelField) &&
                Objects.equals(valueField, that.valueField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method, headers, requestBody, responsePath, labelField, valueField);
    }

    @Override
    public String toString() {
        return "ApiSourceConfig{" +
                "url='" + url + '\'' +
                ", method='" + method + '\'' +
                ", headers=" + headers +
                ", requestBody='" + requestBody + '\'' +
                ", responsePath='" + responsePath + '\'' +
                ", labelField='" + labelField + '\'' +
                ", valueField='" + valueField + '\'' +
                '}';
    }
}
