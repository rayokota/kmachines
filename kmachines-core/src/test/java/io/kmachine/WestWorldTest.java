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

package io.kmachine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kmachine.model.StateMachine;
import io.kmachine.utils.ClientUtils;
import io.kmachine.utils.JsonSerde;
import io.kmachine.utils.StreamUtils;
import org.apache.kafka.connect.json.JsonSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class WestWorldTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(WestWorldTest.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testWestWorld() throws Exception {
        String suffix = "";
        StreamsBuilder builder = new StreamsBuilder();

        Properties producerConfig = ClientUtils.producerConfig(CLUSTER.bootstrapServers(), JsonSerializer.class,
            JsonSerializer.class, new Properties()
        );
        ObjectNode node1 = MAPPER.createObjectNode();
        node1.put("type", "stayHome");
        KStream<JsonNode, JsonNode> stream =
            StreamUtils.streamFromCollection(builder, producerConfig, "miner", 3, (short) 1, new JsonSerde(), new JsonSerde(),
                List.of(
                    new KeyValue<>(new TextNode("Bob"), node1)
                )
            );

        Topology topology = builder.build();
        Properties props = ClientUtils.streamsConfig("prepare-" + suffix, "prepare-client-" + suffix,
            CLUSTER.bootstrapServers(), JsonSerde.class, JsonSerde.class);
        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();

        Path path = Paths.get(getClass().getResource("miner.yml").toURI());
        String text = Files.readString(path, StandardCharsets.UTF_8);
        StateMachine stateMachine = new ObjectMapper(new YAMLFactory()).readValue(text, StateMachine.class);

        KMachine machine = new KMachine(null, suffix, CLUSTER.bootstrapServers(), "miner", stateMachine);
        streamsConfiguration = ClientUtils.streamsConfig("run-" + suffix, "run-client-" + suffix,
            CLUSTER.bootstrapServers(), JsonSerde.class, JsonSerde.class);
        streams = machine.configure(new StreamsBuilder(), streamsConfiguration);

        Thread.sleep(30000);

        Map<JsonNode, Map<String, Object>> map = StreamUtils.mapFromStore(streams, "kmachine-" + suffix);
        log.debug("result: {}", map);

        assertEquals(Set.of(new TextNode("Bob")), map.keySet());
    }

    @Test
    public void testWestWorldMessaging() throws Exception {
        String suffix1 = "1";
        String suffix2 = "2";
        StreamsBuilder builder = new StreamsBuilder();

        Properties producerConfig = ClientUtils.producerConfig(CLUSTER.bootstrapServers(), JsonSerializer.class,
            JsonSerializer.class, new Properties()
        );

        ObjectNode node1 = MAPPER.createObjectNode();
        node1.put("type", "stayHome");
        node1.put("wife", "Elsa");
        KStream<JsonNode, JsonNode> stream1 =
            StreamUtils.streamFromCollection(builder, producerConfig, "miner", 3, (short) 1, new JsonSerde(), new JsonSerde(),
                List.of(
                    new KeyValue<>(new TextNode("Bob"), node1)
                )
            );

        Topology topology = builder.build();
        Properties props = ClientUtils.streamsConfig("prepare-" + suffix1, "prepare-client-" + suffix1,
            CLUSTER.bootstrapServers(), JsonSerde.class, JsonSerde.class);
        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();

        builder = new StreamsBuilder();
        ObjectNode node2 = MAPPER.createObjectNode();
        node2.put("type", "continueHouseWork");
        node2.put("husband", "Bob");
        KStream<JsonNode, JsonNode> stream2 =
            StreamUtils.streamFromCollection(builder, producerConfig, "miners_wife", 3, (short) 1, new JsonSerde(), new JsonSerde(),
                List.of(
                    new KeyValue<>(new TextNode("Elsa"), node2)
                )
            );

        topology = builder.build();
        props = ClientUtils.streamsConfig("prepare-" + suffix2, "prepare-client-" + suffix2,
            CLUSTER.bootstrapServers(), JsonSerde.class, JsonSerde.class);
        streams = new KafkaStreams(topology, props);
        streams.start();

        Path path = Paths.get(getClass().getResource("miner_messaging.yml").toURI());
        String text = Files.readString(path, StandardCharsets.UTF_8);
        StateMachine stateMachine1 = new ObjectMapper(new YAMLFactory()).readValue(text, StateMachine.class);

        KMachine machine1 = new KMachine(null, suffix1, CLUSTER.bootstrapServers(), "miner", stateMachine1);
        streamsConfiguration = ClientUtils.streamsConfig("run-" + suffix1, "run-client-" + suffix1,
            CLUSTER.bootstrapServers(), JsonSerde.class, JsonSerde.class);
        machine1.configure(new StreamsBuilder(), streamsConfiguration);

        path = Paths.get(getClass().getResource("miners_wife_messaging.yml").toURI());
        text = Files.readString(path, StandardCharsets.UTF_8);
        StateMachine stateMachine2 = new ObjectMapper(new YAMLFactory()).readValue(text, StateMachine.class);

        KMachine machine2 = new KMachine(null, suffix2, CLUSTER.bootstrapServers(), "miners_wife", stateMachine2);
        streamsConfiguration = ClientUtils.streamsConfig("run-" + suffix2, "run-client-" + suffix2,
            CLUSTER.bootstrapServers(), JsonSerde.class, JsonSerde.class);
        streams = machine2.configure(new StreamsBuilder(), streamsConfiguration);

        Thread.sleep(30000);

        Map<JsonNode, Map<String, Object>> map = StreamUtils.mapFromStore(streams, "kmachine-" + suffix2);
        log.debug("result: {}", map);

        assertEquals(Set.of(new TextNode("Elsa")), map.keySet());
    }
}
