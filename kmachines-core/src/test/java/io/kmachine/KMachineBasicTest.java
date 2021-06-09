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
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kmachine.model.State;
import io.kmachine.model.StateMachine;
import io.kmachine.model.Transition;
import io.kmachine.utils.ClientUtils;
import io.kmachine.utils.JsonSerde;
import io.kmachine.utils.StreamUtils;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.kmachine.KMachine.STATE_KEY;
import static org.junit.Assert.assertEquals;

public class KMachineBasicTest extends AbstractIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(KMachineBasicTest.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    KMachine machine;

    @Test
    public void testSimpleKMachine() throws Exception {
        String suffix = "";
        StreamsBuilder builder = new StreamsBuilder();

        Properties producerConfig = ClientUtils.producerConfig(CLUSTER.bootstrapServers(), LongSerializer.class,
            LongSerializer.class, new Properties()
        );
        ObjectNode node1 = MAPPER.createObjectNode();
        node1.put("type", "toggle");
        ObjectNode node2 = MAPPER.createObjectNode();
        node2.put("type", "toggle");
        KStream<JsonNode, JsonNode> stream =
            StreamUtils.streamFromCollection(builder, producerConfig, "input", 3, (short) 1, new JsonSerde(), new JsonSerde(),
                List.of(
                    new KeyValue<>(new IntNode(123), node1),
                    new KeyValue<>(new IntNode(123), node1),
                    new KeyValue<>(new IntNode(456), node2)
                )
            );

        Topology topology = builder.build();
        Properties props = ClientUtils.streamsConfig("prepare-" + suffix, "prepare-client-" + suffix,
            CLUSTER.bootstrapServers(), JsonSerde.class, JsonSerde.class);
        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();

        List<State> states = List.of(
            new State("on", "turnOn", null),
            new State("off", "turnOff", null)
        );
        List<Transition> transitions = List.of(
            new Transition("toggle", "on", "off", null, null),
            new Transition("toggle", "off", "on", null, null)
        );
        Map<String, String> functions = Map.of(
            "turnOn", "(ctx, key, value, data) => { data.onEnter = 'turning on'; console.log('turning on') }",
            "turnOff", "(ctx, key, value, data) => { data.onEnter = 'turning off'; console.log('turning off') } "
        );
        StateMachine stateMachine = new StateMachine("mymachine", "off", states, transitions, null, functions);

        machine = new KMachine(null, suffix, CLUSTER.bootstrapServers(), "input", stateMachine);
        streamsConfiguration = ClientUtils.streamsConfig("run-" + suffix, "run-client-" + suffix,
            CLUSTER.bootstrapServers(), JsonSerde.class, JsonSerde.class);
        streams = machine.configure(new StreamsBuilder(), streamsConfiguration).streams();

        Thread.sleep(5000);

        Map<JsonNode, Map<String, Object>> map = StreamUtils.mapFromStore(streams, "kmachine-" + suffix);
        log.debug("result: {}", map);

        Map<JsonNode, Map<String, Object>> expectedResult = new HashMap<>();
        Map<String, Object> data1 = new HashMap<>();
        data1.put(STATE_KEY, "off");
        data1.put("onEnter", "turning off");
        Map<String, Object> data2 = new HashMap<>();
        data2.put(STATE_KEY, "on");
        data2.put("onEnter", "turning on");
        expectedResult.put(new IntNode(123), data1);
        expectedResult.put(new IntNode(456), data2);
        assertEquals(expectedResult, map);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFunctions() throws Exception {
        String suffix = "invalid";
        Map<String, String> functions = Map.of(
            "fun1", "'missing quote",
            "fun2", "functio foo() { console.log }"
        );
        StateMachine stateMachine = new StateMachine("mymachine", "off", null, null, null, functions);

        machine = new KMachine(null, suffix, CLUSTER.bootstrapServers(), "input", stateMachine);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMissingFunctions() throws Exception {
        String suffix = "missing";
        List<State> states = List.of(
            new State("on", "turnOn", null),
            new State("off", "turnOff", null)
        );
        List<Transition> transitions = List.of(
            new Transition("toggle", "on", "off", null, null),
            new Transition("toggle", "off", "on", null, null)
        );
        StateMachine stateMachine = new StateMachine("mymachine", "off", states, transitions, null, null);

        machine = new KMachine(null, suffix, CLUSTER.bootstrapServers(), "input", stateMachine);
    }

    @After
    public void tearDown() throws Exception {
        if (machine != null) {
            machine.close();
        }
    }
}
