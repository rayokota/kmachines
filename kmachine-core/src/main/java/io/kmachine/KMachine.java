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
import com.github.oxo42.stateless4j.StateConfiguration;
import com.github.oxo42.stateless4j.StateMachineConfig;
import com.github.oxo42.stateless4j.delegates.Action;
import com.github.oxo42.stateless4j.delegates.FuncBoolean;
import io.kmachine.model.State;
import io.kmachine.model.StateMachine;
import io.kmachine.model.Transition;
import io.kmachine.utils.JsonSerde;
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
    public static final String WILDCARD_TYPE = "*";

    private static final Action NO_ACTION = new Action() {
        @Override
        public void doIt() {
        }
    };
    private static final FuncBoolean NO_GUARD = new FuncBoolean() {
        @Override
        public boolean call() {
            return true;
        }
    };
    private static final Serde<JsonNode> SERDE = new JsonSerde();

    private final String hostAndPort;
    private final String applicationId;
    private final String bootstrapServers;
    private final String inputTopic;
    private final String storeName;
    private final StateMachine stateMachine;
    private final StateMachineConfig<String, String> config;

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
        this.config = toConfig(stateMachine);

        this.storeName = "kmachine-" + applicationId;
    }

    public KMachineState configure(StreamsBuilder builder, Properties streamsConfig) {
        final StoreBuilder<KeyValueStore<JsonNode, JsonNode>> storeBuilder =
            Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(storeName),
                SERDE, SERDE
            );
        builder.addStateStore(storeBuilder);

        this.input = builder
            .stream(inputTopic, Consumed.with(SERDE, SERDE))
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

    private StateMachineConfig<String, String> toConfig(StateMachine stateMachine) {
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
                // TODO
                stateConfig.onEntry(NO_ACTION);
            }
            if (state.getOnExit() != null) {
                // TODO
                stateConfig.onExit(NO_ACTION);
            }
            List<Transition> transitions = transitionsByFrom.get(state.getName());
            for (Transition transition : transitions) {
                String type = transition.getType() != null ? transition.getType() : WILDCARD_TYPE;
                // TODO
                FuncBoolean guard = transition.getGuard() != null ? NO_GUARD : NO_GUARD;
                // TODO
                Action action = transition.getOnTransition() != null ? NO_ACTION : NO_ACTION;
                stateConfig.permitIf(type, transition.getTo(), guard, action);
            }
        }
        return config;
    }

    private com.github.oxo42.stateless4j.StateMachine<String, String> toImpl(StateMachineConfig<String, String> config) {
        return new com.github.oxo42.stateless4j.StateMachine<>(stateMachine.getInit(), config);
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
        private KeyValueStore<JsonNode, JsonNode> store;
        private Engine engine;

        @SuppressWarnings("unchecked")
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
            Source source = Source.create("js", "21 + 21");
            try (Context context = Context.newBuilder()
                .engine(engine)
                .build()) {
                int v = context.eval(source).asInt();
                assert v == 42;
            }
            store.put(readOnlyKey, value);
            return null;
        }

        @Override
        public void close() {
            engine.close();
        }
    }
}
