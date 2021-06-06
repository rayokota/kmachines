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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Map;
import java.util.Properties;

public class KMachine implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(KMachine.class);

    private static final Serde<JsonNode> SERDE = new JsonSerde();

    private final String hostAndPort;
    private final String applicationId;
    private final String bootstrapServers;
    private final String inputTopic;
    private final String storeName;

    private KStream<JsonNode, JsonNode> input;
    private KafkaStreams streams;

    public KMachine(String hostAndPort,
                    String applicationId,
                    String bootstrapServers,
                    String inputTopic,
                    Map<String, ?> configs) {
        this.hostAndPort = hostAndPort;
        this.applicationId = applicationId;
        this.bootstrapServers = bootstrapServers;
        this.inputTopic = inputTopic;

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

        @SuppressWarnings("unchecked")
        @Override
        public void init(final ProcessorContext context) {
            this.context = context;
            this.store = context.getStateStore(storeName);
        }

        @Override
        public Iterable<KeyValue<JsonNode, JsonNode>> transform(
            final JsonNode readOnlyKey, final JsonNode value
        ) {
            store.put(readOnlyKey, value);
            return null;
        }

        @Override
        public void close() {
        }
    }
}
