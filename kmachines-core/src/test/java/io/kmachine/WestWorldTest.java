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
import org.apache.kafka.common.serialization.LongSerializer;
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

    KMachine machine;

    @Test
    public void testWestWorld() throws Exception {
        String suffix = "";
        StreamsBuilder builder = new StreamsBuilder();

        Properties producerConfig = ClientUtils.producerConfig(CLUSTER.bootstrapServers(), LongSerializer.class,
            LongSerializer.class, new Properties()
        );
        ObjectNode node1 = MAPPER.createObjectNode();
        node1.put("type", "stayHome");
        KStream<JsonNode, JsonNode> stream =
            StreamUtils.streamFromCollection(builder, producerConfig, "input", 3, (short) 1, new JsonSerde(), new JsonSerde(),
                List.of(
                    new KeyValue<>(new TextNode("Bob"), node1)
                )
            );

        Topology topology = builder.build();
        Properties props = ClientUtils.streamsConfig("prepare-" + suffix, "prepare-client-" + suffix,
            CLUSTER.bootstrapServers(), JsonSerde.class, JsonSerde.class);
        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();

        Path path = Paths.get(getClass().getResource("westworld.yml").toURI());
        String text = Files.readString(path, StandardCharsets.UTF_8);
        StateMachine stateMachine = new ObjectMapper(new YAMLFactory()).readValue(text, StateMachine.class);

        machine = new KMachine(null, suffix, CLUSTER.bootstrapServers(), "input", stateMachine);
        streamsConfiguration = ClientUtils.streamsConfig("run-" + suffix, "run-client-" + suffix,
            CLUSTER.bootstrapServers(), JsonSerde.class, JsonSerde.class);
        streams = machine.configure(new StreamsBuilder(), streamsConfiguration).streams();

        Thread.sleep(30000);

        Map<JsonNode, Map<String, Object>> map = StreamUtils.mapFromStore(streams, "kmachine-" + suffix);
        log.debug("result: {}", map);

        assertEquals(Set.of(new TextNode("Bob")), map.keySet());
    }
}
