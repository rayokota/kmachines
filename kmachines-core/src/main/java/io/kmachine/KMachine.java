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
import com.github.oxo42.stateless4j.delegates.Action3;
import com.github.oxo42.stateless4j.delegates.Func;
import com.github.oxo42.stateless4j.delegates.FuncBoolean;
import io.kmachine.model.State;
import io.kmachine.model.StateMachine;
import io.kmachine.model.Transition;
import io.kmachine.model.Transition.ToType;
import io.kmachine.utils.ClientUtils;
import io.kmachine.utils.JsonSerde;
import io.kmachine.utils.KryoSerde;
import io.kmachine.utils.ListProxyArray;
import io.kmachine.utils.MapProxyObject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.connect.json.JsonSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class KMachine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KMachine.class);

    public static final String STATE_KEY = "__state__";
    public static final String TYPE = "type";
    public static final String WILDCARD_TYPE = "*";

    private static final Action NO_ACTION = () -> {
    };
    private static final FuncBoolean NO_GUARD = () -> true;
    private static final Action3<String, String, Object[]> UNHANDLED_TRIGGER = (state, trigger, args) -> {
        log.debug(
            String.format(
                "No valid leaving transitions are permitted from state '%s' for trigger '%s'. Consider ignoring the trigger.",
                state, trigger)
        );
    };

    private static final Serde<JsonNode> JSON_SERDE = new JsonSerde();
    private static final Serde<Map<String, Object>> KRYO_SERDE = new KryoSerde<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String hostAndPort;
    private final String applicationId;
    private final String bootstrapServers;
    private final String inputTopic;
    private final String storeName;
    private final StateMachine stateMachine;
    private final Engine engine;

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
        this.engine = Engine.create();
        this.storeName = "kmachine-" + applicationId;

        validate();
    }

    private void validate() throws IllegalArgumentException {
        Set<String> stateNames = new HashSet<>();
        for (State state : stateMachine.getStates()) {
            String stateName = state.getName();
            String onEntry = state.getOnEntry();
            String onExit = state.getOnExit();
            if (stateName == null) {
                throw new IllegalArgumentException(
                    String.format("Invalid name '%s' for state", stateName));
            }
            if (onEntry != null && !stateMachine.getFunctions().containsKey(onEntry)) {
                throw new IllegalArgumentException(
                    String.format("Invalid onEntry '%s' for state '%s'", onEntry, stateName));
            }
            if (onExit != null && !stateMachine.getFunctions().containsKey(onExit)) {
                throw new IllegalArgumentException(
                    String.format("Invalid onExit '%s' for state '%s'", onExit, stateName));
            }
            stateNames.add(stateName);
        }
        for (Transition transition : stateMachine.getTransitions()) {
            String type = transition.getType();  // type can be null
            String to = transition.getTo();
            ToType toType = transition.getToType() != null ? transition.getToType() : ToType.State;
            String from = transition.getFrom();
            String guard = transition.getGuard();
            String onTransition = transition.getOnTransition();
            switch (toType) {
                case State:
                    // to can be null for internal transitions
                    if (to != null && !stateNames.contains(to)) {
                        throw new IllegalArgumentException(
                            String.format("Invalid to '%s' of type '%s' for transition '%s'", to, toType, type));
                    }
                    break;
                case Function:
                    if (to == null || !stateMachine.getFunctions().containsKey(to)) {
                        throw new IllegalArgumentException(
                            String.format("Invalid to '%s' of type '%s' for transition '%s'", to, toType, type));
                    }
                    break;
            }
            if (from == null || !stateNames.contains(from)) {
                throw new IllegalArgumentException(
                    String.format("Invalid from '%s' for transition '%s'", from, type));
            }
            if (guard != null && !stateMachine.getFunctions().containsKey(guard)) {
                throw new IllegalArgumentException(
                    String.format("Invalid guard '%s' for transition '%s'", guard, type));
            }
            if (onTransition != null && !stateMachine.getFunctions().containsKey(onTransition)) {
                throw new IllegalArgumentException(
                    String.format("Invalid onTransition '%s' for transition '%s'", onTransition, type));
            }
        }
        String name = stateMachine.getName();
        String init = stateMachine.getInit();
        if (name == null) {
            throw new IllegalArgumentException(
                String.format("Invalid name '%s' for state machine", name));
        }
        if (!stateNames.contains(init)) {
            throw new IllegalArgumentException(
                String.format("Invalid init '%s' for state machine '%s'", init, name));
        }
        try (org.graalvm.polyglot.Context context =
                 org.graalvm.polyglot.Context.newBuilder().engine(engine).build()) {
            for (Map.Entry<String, String> entry : stateMachine.getFunctions().entrySet()) {
                String script = entry.getValue();
                Source source = Source.create("js", script);
                try {
                    context.eval(source);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Could not evaluate script: " + script, e);
                }
            }
        }
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

        input.process(ProcessInput::new, storeName);

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
        engine.close();
    }

    private final class ProcessInput implements Processor<JsonNode, JsonNode> {

        private ProcessorContext context;
        private KeyValueStore<JsonNode, Map<String, Object>> store;
        private Producer<JsonNode, JsonNode> producer;
        private ScheduledExecutorService executor;

        @Override
        public void init(final ProcessorContext context) {
            this.context = context;
            this.store = context.getStateStore(storeName);
            Properties producerConfig = ClientUtils.producerConfig(
                bootstrapServers, JsonSerializer.class, JsonSerializer.class, new Properties()
            );
            this.producer = new KafkaProducer<>(producerConfig);
            this.executor = Executors.newScheduledThreadPool(10);
        }

        @Override
        public void process(final JsonNode readOnlyKey, final JsonNode value) {
            Map<String, Object> data = store.get(readOnlyKey);
            if (data == null) {
                data = new HashMap<>(stateMachine.getData());
            }
            String init = (String) data.getOrDefault(STATE_KEY, stateMachine.getInit());
            Object proxyKey = toProxy(readOnlyKey);
            Object proxyValue = toProxy(value);
            ProxyObject proxyData = new MapProxyObject(data);
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
                if (transitions != null) {
                    for (Transition transition : transitions) {
                        String type = transition.getType() != null ? transition.getType() : WILDCARD_TYPE;
                        FuncBoolean guard = transition.getGuard() != null
                            ? toGuard(transition.getGuard(), key, value, data)
                            : NO_GUARD;
                        Action action = transition.getOnTransition() != null
                            ? toAction(transition.getOnTransition(), key, value, data)
                            : NO_ACTION;
                        String to = transition.getTo();
                        ToType toType = transition.getToType() != null ? transition.getToType() : ToType.State;
                        switch (toType) {
                            case State:
                                if (to != null) {
                                    stateConfig.permitIf(type, to, guard, action);
                                } else {
                                    stateConfig.permitInternalIf(type, guard, action);
                                }
                                break;
                            case Function:
                                Func<String> toSelector = toStringFunc(to, key, value, data);
                                stateConfig.permitDynamicIf(type, toSelector, guard, action);
                                break;
                        }
                    }
                }
            }
            return config;
        }

        private FuncBoolean toGuard(String guard, Object key, Object value, ProxyObject data) {
            String script = stateMachine.getFunctions().get(guard);
            if (script == null) {
                throw new IllegalStateException("No function named " + guard);
            }
            return () -> {
                Source source = Source.create("js", "var _fn = " + script + "; _fn(ctx, key, value, data);");
                try (org.graalvm.polyglot.Context context =
                         org.graalvm.polyglot.Context.newBuilder().engine(engine).build()) {
                    Value jsBindings = context.getBindings("js");
                    jsBindings.putMember("ctx", new Context(this.context, executor, producer));
                    jsBindings.putMember("key", key);
                    jsBindings.putMember("value", value);
                    jsBindings.putMember("data", data);
                    return context.eval(source).asBoolean();
                }
            };
        }

        private Action toAction(String action, Object key, Object value, ProxyObject data) {
            String script = stateMachine.getFunctions().get(action);
            if (script == null) {
                throw new IllegalStateException("No function named " + action);
            }
            return () -> {
                Source source = Source.create("js", "var _fn = " + script + "; _fn(ctx, key, value, data);");
                try (org.graalvm.polyglot.Context context =
                         org.graalvm.polyglot.Context.newBuilder().engine(engine).build()) {
                    Value jsBindings = context.getBindings("js");
                    jsBindings.putMember("ctx", new Context(this.context, executor, producer));
                    jsBindings.putMember("key", key);
                    jsBindings.putMember("value", value);
                    jsBindings.putMember("data", data);
                    context.eval(source);
                }
            };
        }

        private Func<String> toStringFunc(String function, Object key, Object value, ProxyObject data) {
            String script = stateMachine.getFunctions().get(function);
            if (script == null) {
                throw new IllegalStateException("No function named " + function);
            }
            return () -> {
                Source source = Source.create("js", "var _fn = " + script + "; _fn(ctx, key, value, data);");
                try (org.graalvm.polyglot.Context context =
                         org.graalvm.polyglot.Context.newBuilder().engine(engine).build()) {
                    Value jsBindings = context.getBindings("js");
                    jsBindings.putMember("ctx", new Context(this.context, executor, producer));
                    jsBindings.putMember("key", key);
                    jsBindings.putMember("value", value);
                    jsBindings.putMember("data", data);
                    return context.eval(source).asString();
                }
            };
        }

        private com.github.oxo42.stateless4j.StateMachine<String, String> toImpl(
            String init, StateMachineConfig<String, String> config) {
            com.github.oxo42.stateless4j.StateMachine<String, String> sm =
                new com.github.oxo42.stateless4j.StateMachine<>(init, config);
            sm.onUnhandledTrigger(UNHANDLED_TRIGGER);
            return sm;
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
                Map<String, Object> values = MAPPER.convertValue(jsonNode, new TypeReference<>() {});
                return new MapProxyObject(values);
            } else if (jsonNode.isArray()) {
                List<Object> values = MAPPER.convertValue(jsonNode, new TypeReference<>() {});
                return new ListProxyArray(values);
            } else {
                throw new IllegalArgumentException("Cannot convert node " + jsonNode.asText());
            }
        }

        @Override
        public void close() {
        }
    }
}
