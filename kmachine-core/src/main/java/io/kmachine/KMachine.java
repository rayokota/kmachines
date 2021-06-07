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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.oxo42.stateless4j.StateConfiguration;
import com.github.oxo42.stateless4j.StateMachineConfig;
import com.github.oxo42.stateless4j.delegates.Action;
import com.github.oxo42.stateless4j.delegates.FuncBoolean;
import io.kmachine.model.State;
import io.kmachine.model.StateMachine;
import io.kmachine.model.Transition;
import io.kmachine.utils.JsonSerde;
import io.kmachine.utils.KryoSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class KMachine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KMachine.class);

    public static final String STATE_KEY = "__state__";
    public static final String TYPE = "type";
    public static final String WILDCARD_TYPE = "*";

    private static final Action NO_ACTION = () -> {
    };
    private static final FuncBoolean NO_GUARD = () -> true;
    private static final Serde<JsonNode> JSON_SERDE = new JsonSerde();
    private static final Serde<Map<String, Object>> KRYO_SERDE = new KryoSerde<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String hostAndPort;
    private final String applicationId;
    private final String bootstrapServers;
    private final String inputTopic;
    private final String storeName;
    private final StateMachine stateMachine;

    private KStream<JsonNode, JsonNode> input;
    private KafkaStreams streams;

    public KMachine(String hostAndPort,
                    String applicationId,
                    String bootstrapServers,
                    String inputTopic,
                    StateMachine stateMachine) {
        this.hostAndPort = hostAndPort;
        this.applicationId = applicationId;
        this.bootstrapServers = bootstrapServers;
        this.inputTopic = inputTopic;
        this.stateMachine = stateMachine;

        this.storeName = "kmachine-" + applicationId;
    }

    public KMachineState configure(StreamsBuilder builder, Properties streamsConfig) {
        final StoreBuilder<KeyValueStore<JsonNode, Map<String, Object>>> storeBuilder =
            Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(storeName),
                JSON_SERDE, KRYO_SERDE
            );
        builder.addStateStore(storeBuilder);

        this.input = builder
            .stream(inputTopic, Consumed.with(JSON_SERDE, JSON_SERDE))
            .peek((k, v) -> log.trace("input after topic: (" + k + ", " + v + ")"));

        KStream<JsonNode, JsonNode> transformed = input
            .flatTransform(ProcessInput::new, storeName)
            .peek((k, v) -> log.trace("output after process: (" + k + ", " + v + ")"));

        Topology topology = builder.build();
        log.info("Topology description {}", topology.describe());
        streams = new KafkaStreams(topology, streamsConfig);
        streams.start();

        return new KMachineState(streams, KMachineState.State.CREATED);
    }

    @Override
    public void close() {
        if (streams != null) {
            streams.close();
        }
    }

    private final class ProcessInput
        implements Transformer<JsonNode, JsonNode, Iterable<KeyValue<JsonNode, JsonNode>>> {

        private ProcessorContext context;
        private KeyValueStore<JsonNode, Map<String, Object>> store;
        private Engine engine;
        private StateMachineConfig<String, String> config;

        @Override
        public void init(final ProcessorContext context) {
            this.context = context;
            this.store = context.getStateStore(storeName);
            this.engine = Engine.create();
        }

        @Override
        public Iterable<KeyValue<JsonNode, JsonNode>> transform(
            final JsonNode readOnlyKey, final JsonNode value
        ) {
            Map<String, Object> data = store.get(readOnlyKey);
            if (data == null) {
                data = new HashMap<>();
            }
            String init = (String) data.getOrDefault(STATE_KEY, stateMachine.getInit());
            Object proxyKey = toProxy(readOnlyKey);
            Object proxyValue = toProxy(value);
            ProxyObject proxyData = ProxyObject.fromMap(data);
            com.github.oxo42.stateless4j.StateMachine<String, String> impl =
                toImpl(init, toConfig(proxyKey, proxyValue, proxyData));
            String type = null;
            if (value.isObject() && value.has(TYPE)) {
                type = value.get(TYPE).textValue();
            }
            if (type == null && readOnlyKey.isObject() && readOnlyKey.has(TYPE)) {
                type = readOnlyKey.get(TYPE).textValue();
            }
            if (type == null) {
                type = WILDCARD_TYPE;
            }
            impl.fire(type);

            // Save state and data
            data.put(STATE_KEY, impl.getState());
            store.put(readOnlyKey, data);
            return null;
        }

        private StateMachineConfig<String, String> toConfig(
            Object key, Object value, ProxyObject data) {
            StateMachineConfig<String, String> config = new StateMachineConfig<>();

            Map<String, List<Transition>> transitionsByFrom = new HashMap<>();
            for (Transition transition : stateMachine.getTransitions()) {
                List<Transition> transitions =
                    transitionsByFrom.computeIfAbsent(transition.getFrom(), k -> new ArrayList<>());
                transitions.add(transition);
            }
            for (State state : stateMachine.getStates()) {
                StateConfiguration<String, String> stateConfig = config.configure(state.getName());
                if (state.getOnEntry() != null) {
                    Action action = toAction(state.getOnEntry(), key, value, data);
                    stateConfig.onEntry(action);
                }
                if (state.getOnExit() != null) {
                    Action action = toAction(state.getOnExit(), key, value, data);
                    stateConfig.onExit(action);
                }
                List<Transition> transitions = transitionsByFrom.get(state.getName());
                for (Transition transition : transitions) {
                    String type = transition.getType() != null ? transition.getType() : WILDCARD_TYPE;
                    FuncBoolean guard = transition.getGuard() != null
                        ? toGuard(transition.getGuard(), key, value, data)
                        : NO_GUARD;
                    Action action = transition.getOnTransition() != null
                        ? toAction(transition.getOnTransition(), key, value, data)
                        : NO_ACTION;
                    stateConfig.permitIf(type, transition.getTo(), guard, action);
                }
            }
            return config;
        }

        private FuncBoolean toGuard(String guard, Object key, Object value, ProxyObject data) {
            return () -> {
                // TODO add ctx
                Source source = Source.create("js", "var _fn = " + guard + "; _fn(key, value, data);");
                try (Context context = Context.newBuilder().engine(engine).build()) {
                    context.getBindings("js").putMember("key", key);
                    context.getBindings("js").putMember("value", value);
                    context.getBindings("js").putMember("data", data);
                    return context.eval(source).asBoolean();
                }
            };
        }

        private Action toAction(String action, Object key, Object value, ProxyObject data) {
            return () -> {
                // TODO add ctx
                Source source = Source.create("js", "var _fn = " + action + "; _fn(key, value, data);");
                try (Context context = Context.newBuilder().engine(engine).build()) {
                    context.getBindings("js").putMember("key", key);
                    context.getBindings("js").putMember("value", value);
                    context.getBindings("js").putMember("data", data);
                    context.eval(source);
                }
            };
        }

        private com.github.oxo42.stateless4j.StateMachine<String, String> toImpl(
            String init, StateMachineConfig<String, String> config) {
            return new com.github.oxo42.stateless4j.StateMachine<>(init, config);
        }

        private Object toProxy(JsonNode jsonNode) {
            if (jsonNode.isNull()) {
                return null;
            } else if (jsonNode.isNumber()) {
                return jsonNode.numberValue();
            } else if (jsonNode.isBoolean()) {
                return jsonNode.booleanValue();
            } else if (jsonNode.isTextual()) {
                return jsonNode.textValue();
            } else if (jsonNode.isObject()) {
                Map<String, Object> values = MAPPER.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {});
                return ProxyObject.fromMap(values);
            } else if (jsonNode.isArray()) {
                List<Object> values = MAPPER.convertValue(jsonNode, new TypeReference<List<Object>>() {});
                return ProxyArray.fromArray(values);
            } else {
                throw new IllegalArgumentException("Cannot convert node " + jsonNode.asText());
            }
        }

        @Override
        public void close() {
            engine.close();
        }
    }
}
