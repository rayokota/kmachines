/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.kmachine.rest.server;

import com.fasterxml.jackson.databind.JsonNode;
import io.kcache.CacheUpdateHandler;
import io.kcache.KafkaCache;
import io.kcache.KafkaCacheConfig;
import io.kcache.utils.InMemoryCache;
import io.kmachine.KMachine;
import io.kmachine.model.StateMachine;
import io.kmachine.utils.ClientUtils;
import io.kmachine.utils.JsonSerde;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class KMachineManager {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @ConfigProperty(name = "quarkus.http.ssl-port")
    int sslPort;

    @ConfigProperty(name = "quarkus.http.insecure-requests")
    String insecureRequests;

    @Inject
    KMachineConfig config;

    private KafkaCacheConfig cacheConfig;
    private KafkaCache<String, JsonNode> cache;
    private final Map<String, KMachine> machines = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        String topic = "_kmachines";
        String groupId = "kmachine-1";
        Map<String, String> configs = config.kafkaCacheConfig().entrySet().stream()
            .collect(Collectors.toMap(e -> "kafkacache." + e.getKey(), Map.Entry::getValue));
        configs.put(KafkaCacheConfig.KAFKACACHE_BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(KafkaCacheConfig.KAFKACACHE_TOPIC_CONFIG, topic);
        configs.put(KafkaCacheConfig.KAFKACACHE_GROUP_ID_CONFIG, groupId);
        configs.put(KafkaCacheConfig.KAFKACACHE_CLIENT_ID_CONFIG, groupId + "-" + topic);
        cacheConfig = new KafkaCacheConfig(configs);
        KMachineUpdateHandler updateHandler = new KMachineUpdateHandler();
        cache = new KafkaCache<>(cacheConfig, Serdes.String(), new JsonSerde(),
            updateHandler, new InMemoryCache<>());
        cache.init();
    }

    public URI uri() {
        try {
            String scheme = "enabled".equalsIgnoreCase(insecureRequests) ? "http" : "https";
            return new URI(scheme + "://" + host() + ":" + port());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public String applicationServer() {
        return host() + ":" + port();
    }

    public String host() {
        return config.hostName().orElse(getDefaultHost());
    }

    public int port() {
        return "enabled".equalsIgnoreCase(insecureRequests) ? this.port : this.sslPort;
    }

    private static String getDefaultHost() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            throw new ConfigException("Unknown local hostname", e);
        }
    }

    public String bootstrapServers() {
        return bootstrapServers;
    }

    public KafkaCacheConfig cacheConfig() {
        return cacheConfig;
    }

    public void sync() {
        cache.sync();
    }

    public void create(String id, StateMachine stateMachine) {
        if (cache.containsKey(id)) {
            throw new IllegalArgumentException("KMachine already exists with id: " + id);
        }
        cache.put(id, stateMachine.toJsonNode());
    }

    public Set<String> list() {
        return cache.keySet();
    }

    public KMachine get(String id) {
        return machines.get(id);
    }

    public void remove(String id) {
        cache.remove(id);
    }

    class KMachineUpdateHandler implements CacheUpdateHandler<String, JsonNode> {

        @Override
        public void handleUpdate(String key, JsonNode value, JsonNode oldValue,
                                 TopicPartition tp, long offset, long timestamp) {
            if (value == null) {
                KMachine machine = machines.remove(key);
                if (machine != null) {
                    machine.close();
                }
            } else {
                StateMachine stateMachine = StateMachine.fromJsonNode(value);
                String id = stateMachine.getName();
                KMachine machine = new KMachine(id, bootstrapServers(), stateMachine);
                machines.put(id, machine);
                Properties streamsConfiguration = ClientUtils.streamsConfig(id, "client-" + id,
                    bootstrapServers(), JsonSerde.class, JsonSerde.class);
                streamsConfiguration.put(StreamsConfig.APPLICATION_SERVER_CONFIG, applicationServer());
                machine.configure(new StreamsBuilder(), streamsConfiguration);
            }
        }
    }
}
