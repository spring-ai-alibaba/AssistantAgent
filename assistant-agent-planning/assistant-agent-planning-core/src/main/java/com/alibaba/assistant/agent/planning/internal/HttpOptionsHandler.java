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
package com.alibaba.assistant.agent.planning.internal;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.exception.OptionsSourceException;
import com.alibaba.assistant.agent.planning.model.HttpOptionsConfig;
import com.alibaba.assistant.agent.planning.model.OptionsSourceConfig;
import com.jayway.jsonpath.JsonPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handler for HTTP API-based option sources.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
class HttpOptionsHandler implements OptionsSourceHandler {

    private static final Logger logger = LoggerFactory.getLogger(HttpOptionsHandler.class);

    private final RestTemplate restTemplate;
    private final int defaultTimeout;

    public HttpOptionsHandler(RestTemplate restTemplate, int defaultTimeout) {
        this.restTemplate = restTemplate;
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    public List<OptionItem> handle(String systemId, Object specificConfig) {
        HttpOptionsConfig config = (HttpOptionsConfig) specificConfig;

        try {
            // Execute HTTP request
            ResponseEntity<String> response = executeHttpRequest(config);

            // Extract data using JSONPath
            return extractOptions(response.getBody(), config.getLabelPath(), config.getValuePath());
        } catch (RestClientException e) {
            logger.error("HttpOptionsHandler#handle - HTTP request failed: url={}, error={}",
                    config.getUrl(), e.getMessage(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("HttpOptionsHandler#handle - Failed to extract options: url={}, error={}",
                    config.getUrl(), e.getMessage(), e);
            throw new OptionsSourceException("Failed to extract options from HTTP response", e);
        }
    }

    private ResponseEntity<String> executeHttpRequest(HttpOptionsConfig config) {
        HttpHeaders headers = new HttpHeaders();
        if (config.getHeaders() != null) {
            config.getHeaders().forEach(headers::set);
        }

        HttpEntity<String> entity = new HttpEntity<>(config.getBody(), headers);
        HttpMethod method = HttpMethod.valueOf(config.getMethod().toUpperCase());

        return restTemplate.exchange(config.getUrl(), method, entity, String.class);
    }

    private List<OptionItem> extractOptions(String jsonBody, String labelPath, String valuePath) {
        try {
            List<String> labels = JsonPath.read(jsonBody, labelPath);
            List<String> values = JsonPath.read(jsonBody, valuePath);

            if (labels.size() != values.size()) {
                throw new OptionsSourceException(
                        "Label and value arrays have different sizes: " + labels.size() + " vs " + values.size());
            }

            List<OptionItem> options = new ArrayList<>();
            for (int i = 0; i < labels.size(); i++) {
                options.add(new OptionItem(labels.get(i), values.get(i)));
            }

            return options;
        } catch (Exception e) {
            throw new OptionsSourceException("Failed to extract data via JSONPath", e);
        }
    }

    @Override
    public OptionsSourceConfig.SourceType supportedType() {
        return OptionsSourceConfig.SourceType.HTTP;
    }
}
