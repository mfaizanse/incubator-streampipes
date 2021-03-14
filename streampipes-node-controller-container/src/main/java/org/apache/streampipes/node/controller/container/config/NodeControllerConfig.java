/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.streampipes.node.controller.container.config;

import org.apache.streampipes.config.SpConfig;
import org.apache.streampipes.model.node.resources.fielddevice.FieldDeviceAccessResource;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum NodeControllerConfig {
    INSTANCE;

    private SpConfig config;

    private static final String SLASH = "/";

    private static final String NODE_SERVICE_ID = "node/org.apache.streampipes.node.controller";

    // Default hosts: accessible host (IP/DNS) for physical host, node controller, core
    private static final String DEFAULT_NODE_HOST = "host.docker.internal";
    private static final String DEFAULT_NODE_CONTROLLER_HOST = "streampipes-node-controller";
    private static final String DEFAULT_NODE_BROKER_HOST = "streampipes-node-broker";
    private static final String DEFAULT_BACKEND_HOST = "host.docker.internal";

    // Default ports: service endpoint ports
    private static final int DEFAULT_NODE_CONTROLLER_PORT = 7077;
    private static final int DEFAULT_NODE_BROKER_PORT = 1883;
    private static final int DEFAULT_BACKEND_PORT = 8030;

    // Default configurations: Node controller configs
    private static final int DEFAULT_DOCKER_PRUNING_FREQ_SECS = 3600;
    private static final int DEFAULT_NODE_RESOURCE_UPDATE_FREQ_SECS = 30;
    private static final int DEFAULT_EVENT_BUFFER_SIZE = 1000;
    private static final String DEFAULT_NODE_CONTROLLER_ID = "nodectlr";
    private static final String DEFAULT_NODE_TYPE = "edge";

    NodeControllerConfig() {
        config = SpConfig.getSpConfig(makeServiceId());

        config.register(ConfigKeys.NODE_HOST,
                checkSpNodeControllerUrlOrElseDefault(ConfigKeys.NODE_HOST, DEFAULT_NODE_HOST, String.class),
                "node host name");
        config.register(ConfigKeys.NODE_TYPE, DEFAULT_NODE_TYPE,
                "node type");
        config.register(ConfigKeys.NODE_CONTROLLER_ID, DEFAULT_NODE_CONTROLLER_ID,
                "node controller id");
        config.register(ConfigKeys.NODE_CONTROLLER_CONTAINER_HOST,
                checkSpNodeControllerUrlOrElseDefault(ConfigKeys.NODE_CONTROLLER_CONTAINER_HOST, DEFAULT_NODE_CONTROLLER_HOST, String.class),
                "node controller container host");
        config.register(ConfigKeys.NODE_CONTROLLER_CONTAINER_PORT,
                checkSpNodeControllerUrlOrElseDefault(ConfigKeys.NODE_CONTROLLER_CONTAINER_PORT, DEFAULT_NODE_CONTROLLER_PORT, Integer.class),
                "node controller port");
        config.register(ConfigKeys.NODE_BROKER_CONTAINER_HOST,
                checkSpNodeControllerUrlOrElseDefault(ConfigKeys.NODE_BROKER_CONTAINER_HOST, DEFAULT_NODE_BROKER_HOST, String.class),
                "node broker host");
        config.register(ConfigKeys.NODE_BROKER_CONTAINER_PORT,
                DEFAULT_NODE_BROKER_PORT,
                "node broker port");
        // currently used for connect adapter registration
        config.register(ConfigKeys.BACKEND_HOST,
                checkSpUrlOrElseDefault(ConfigKeys.BACKEND_HOST, DEFAULT_BACKEND_HOST, String.class),
                "backend host");
        config.register(ConfigKeys.BACKEND_PORT,
                checkSpUrlOrElseDefault(ConfigKeys.BACKEND_PORT, DEFAULT_BACKEND_PORT, Integer.class),
                "backend port");
    }

    private String makeServiceId() {
        return NODE_SERVICE_ID + SLASH + checkSpNodeControllerUrlOrElseDefault(ConfigKeys.NODE_CONTROLLER_CONTAINER_HOST,
                DEFAULT_NODE_CONTROLLER_HOST, String.class);
    }

    private String checkForNodeControllerIdPrefixOrUseDefault() {
        String prefix = getEnvOrDefault(ConfigKeys.NODE_CONTROLLER_ID, DEFAULT_NODE_CONTROLLER_ID, String.class);
        return prefix + "-" + generateSixDigitShortUUID();
    }

    public String getNodeServiceId() {
        return NODE_SERVICE_ID;
    }

    public String getSpVersion() {
        return getEnvOrDefault(ConfigKeys.SP_VERSION, "", String.class);
    }

    public String getNodeHost(){
        return checkSpNodeControllerUrlOrElseDefault(ConfigKeys.NODE_HOST, DEFAULT_NODE_HOST, String.class);
    }

    public String getNodeControllerId() {
        return getEnvOrDefault(
                ConfigKeys.NODE_CONTROLLER_ID,
                DEFAULT_NODE_CONTROLLER_ID, String.class);
    }

    public int getNodeControllerPort(){
        return checkSpNodeControllerUrlOrElseDefault(ConfigKeys.NODE_CONTROLLER_CONTAINER_PORT,
                DEFAULT_NODE_CONTROLLER_PORT, Integer.class);
    }

    public String getNodeBrokerHost() {
        return checkSpNodeControllerUrlOrElseDefault(ConfigKeys.NODE_BROKER_CONTAINER_HOST, DEFAULT_NODE_BROKER_HOST,
                String.class);
    }

    public int getNodeBrokerPort() {
        return getEnvOrDefault(
                ConfigKeys.NODE_BROKER_CONTAINER_PORT,
                DEFAULT_NODE_BROKER_PORT, Integer.class);
    }

    public List<String> getNodeLocations() {
        return System.getenv().entrySet().stream()
                .filter(e -> e.getKey().contains(ConfigKeys.NODE_LOCATION))
                .map(v -> v.getValue().split(";"))
                .flatMap(Stream::of)
                .collect(Collectors.toList());
    }

    public List<String> getSupportedPipelineElements() {
        return System.getenv()
                .entrySet()
                .stream()
                .filter(e -> (e.getKey().contains(ConfigKeys.NODE_SUPPORTED_PE_APP_ID)))
                .map(x -> x.getValue())
                .collect(Collectors.toList());
    }

    public List<FieldDeviceAccessResource> getFieldDeviceAccessResources(){
        return System.getenv()
                .entrySet()
                .stream()
                .filter(e -> (e.getKey().contains(ConfigKeys.NODE_ACCESSIBLE_FIELD_DEVICE)))
                .map(x -> {
                    FieldDeviceAccessResource a = new FieldDeviceAccessResource();
                    a.setDeviceName(x.getValue().split(";")[0]);
                    a.setDeviceType(x.getValue().split(";")[1]);
                    a.setConnectionType(x.getValue().split(";")[2]);
                    a.setConnectionString(x.getValue().split(";")[3]);
                    return a;
                })
                .collect(Collectors.toList());
    }

    public boolean hasNodeGpu(){
        return getEnvOrDefault(
                ConfigKeys.NODE_HAS_GPU,
                false,
                Boolean.class);
    }

    public int getGpuCores() {
        return getEnvOrDefault(
                ConfigKeys.NODE_GPU_CUDA_CORES,
                0,
                Integer.class);
    }

    public String getGpuType() {
        return getEnvOrDefault(
                ConfigKeys.NODE_GPU_TYPE,
                "n/a",
                String.class);
    }

    public int getPruningFreq() {
        return getEnvOrDefault(
                ConfigKeys.DOCKER_PRUNING_FREQ_SECS,
                DEFAULT_DOCKER_PRUNING_FREQ_SECS,
                Integer.class);
    }

    public int getNodeResourceUpdateFreqSecs() {
        return getEnvOrDefault(
                ConfigKeys.RESOURCE_UPDATE_FREQ_SECS,
                DEFAULT_NODE_RESOURCE_UPDATE_FREQ_SECS,
                Integer.class);
    }

    public int getEventBufferSize() {
        return getEnvOrDefault(
                ConfigKeys.EVENT_BUFFER_SIZE,
                DEFAULT_EVENT_BUFFER_SIZE,
                Integer.class);
    }

    public String getNodeType() {
        return getEnvOrDefault(
                ConfigKeys.NODE_TYPE,
                DEFAULT_NODE_TYPE,
                String.class);
    }

    public String getApiKey() {
        String apiKey = getEnvOrDefault(
                ConfigKeys.SP_API_KEY,
                "",
                String.class);
        if (!apiKey.isEmpty()) {
            return apiKey;
        }
        throw new RuntimeException("StreamPipes API key not provided");
    }

    public String consulLocation() {
        return checkSpUrlOrElseDefault("CONSUL_LOCATION", "consul",
                String.class);
    }

    public String backendLocation() {
        return checkSpUrlOrElseDefault(ConfigKeys.BACKEND_HOST, DEFAULT_BACKEND_HOST, String.class);
    }

    public int backendPort() {
        return checkSpUrlOrElseDefault(ConfigKeys.BACKEND_PORT, DEFAULT_BACKEND_PORT, Integer.class);
    }

    // Helpers

    public <T>T checkSpNodeControllerUrlOrElseDefault(String key, T defaultValue, Class<T> type) {
        return checkEnvOrElseDefault(ConfigKeys.SP_NODE_CONTROLLER_URL, key, defaultValue, type);
    }

    public <T>T checkSpUrlOrElseDefault(String key, T defaultValue, Class<T> type) {
        return checkEnvOrElseDefault(ConfigKeys.SP_URL, key, defaultValue, type);
    }

    private <T>T checkEnvOrElseDefault(String envUrl, String defaultKey, T defaultValue, Class<T> type) {
        T result = null;
        if (System.getenv(envUrl) != null) {
            try {
                URL url = new URL(System.getenv(envUrl));
                if (type.equals(String.class)) {
                    result = (T) url.getHost();
                } else if (type.equals(Integer.class)) {
                    result = (T) Integer.valueOf(url.getPort());
                }
            } catch (MalformedURLException e) {
                throw new RuntimeException("Could not parse provide URL:", e);
            }
        } else {
            result = getEnvOrDefault(defaultKey, defaultValue, type);
        }
        return result;
    }

    private <T> T getEnvOrDefault(String k, T defaultValue, Class<T> type) {
        if(type.equals(Integer.class)) {
            return System.getenv(k) != null ? (T) Integer.valueOf(System.getenv(k)) : defaultValue;
        } else if(type.equals(Boolean.class)) {
            return System.getenv(k) != null ? (T) Boolean.valueOf(System.getenv(k)) : defaultValue;
        } else {
            return System.getenv(k) != null ? type.cast(System.getenv(k)) : defaultValue;
        }
    }

    private String generateSixDigitShortUUID() {
        String uuid = UUID.randomUUID().toString();
        return  uuid.substring(uuid.lastIndexOf('-') + 6);
    }
}
