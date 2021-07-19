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
import io.kcache.KafkaCache;
import io.kcache.KafkaCacheConfig;
import io.kcache.utils.InMemoryCache;
import io.kmachine.KMachine;
import io.kmachine.model.StateMachine;
import io.kmachine.utils.JsonSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.annotations.Pos;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class KMachineManager {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Inject
    private KMachineConfig config;

    private KafkaCacheConfig cacheConfig;
    private KafkaCache<String, JsonNode> cache;
    private Map<String, KMachine> machines = new ConcurrentHashMap<>();

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
        cache = new KafkaCache<>(cacheConfig, Serdes.String(), new JsonSerde(), null, new InMemoryCache<>());
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

    public KMachine create(String id, StateMachine stateMachine) {
        if (machines.containsKey(id)) {
            throw new IllegalArgumentException("KMachine already exists with id: " + id);
        }
        KMachine m = new KMachine(id, bootstrapServers(), stateMachine);
        machines.put(id, m);
        return m;
    }

    public Set<String> list() {
        return machines.keySet();
    }

    public KMachine get(String id) {
        return machines.get(id);
    }

    public KMachine remove(String id) {
        return machines.remove(id);
    }
}
