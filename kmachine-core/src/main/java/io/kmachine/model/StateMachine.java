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

package io.kmachine.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.kmachine.KMachineState;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class StateMachine {

    private String name;
    private String init;
    private List<State> states;
    private List<Transition> transitions;
    private Map<String, Object> data;
    private Map<String, String> methods;

    public StateMachine(@JsonProperty("name") String name,
                        @JsonProperty("init") String init,
                        @JsonProperty("states") List<State> states,
                        @JsonProperty("transitions") List<Transition> transitions,
                        @JsonProperty("data") Map<String, Object> data,
                        @JsonProperty("methods") Map<String, String> methods) {
        this.name = name;
        this.init = init;
        this.states = states;
        this.transitions = transitions;
        this.data = data;
        this.methods = methods;
    }

    public StateMachine() {
        this.states = Collections.emptyList();
        this.transitions = Collections.emptyList();
        this.data = Collections.emptyMap();
        this.methods = Collections.emptyMap();
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("init")
    public String getInit() {
        return init;
    }

    @JsonProperty("init")
    public void setInit(String init) {
        this.init = init;
    }

    @JsonProperty("states")
    public List<State> getStates() {
        return states;
    }

    @JsonProperty("states")
    public void setStates(List<State> states) {
        this.states = states;
    }

    @JsonProperty("transitions")
    public List<Transition> getTransitions() {
        return transitions;
    }

    @JsonProperty("transitions")
    public void setTransitions(List<Transition> transitions) {
        this.transitions = transitions;
    }

    @JsonProperty("data")
    public Map<String, Object> getData() {
        return data;
    }

    @JsonProperty("data")
    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    @JsonProperty("methods")
    public Map<String, String> getMethods() {
        return methods;
    }

    @JsonProperty("methods")
    public void setMethods(Map<String, String> methods) {
        this.methods = methods;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StateMachine that = (StateMachine) o;
        return Objects.equals(name, that.name)
            && Objects.equals(init, that.init)
            && Objects.equals(states, that.states)
            && Objects.equals(transitions, that.transitions)
            && Objects.equals(data, that.data)
            && Objects.equals(methods, that.methods);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, init, states, transitions, data, methods);
    }
}
