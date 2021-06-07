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

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class State {

    private String name;
    private String onEntry;
    private String onExit;

    public State(@JsonProperty("name") String name,
                 @JsonProperty("onEntry") String onEntry,
                 @JsonProperty("onExit") String onExit) {
        this.name = name;
        this.onEntry = onEntry;
        this.onExit = onExit;
    }

    public State() {
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("onEntry")
    public String getOnEntry() {
        return onEntry;
    }

    @JsonProperty("onEntry")
    public void setOnEntry(String onEntry) {
        this.onEntry = onEntry;
    }

    @JsonProperty("onExit")
    public String getOnExit() {
        return onExit;
    }

    @JsonProperty("onExit")
    public void setOnExit(String onExit) {
        this.onExit = onExit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        State state = (State) o;
        return Objects.equals(name, state.name)
            && Objects.equals(onEntry, state.onEntry)
            && Objects.equals(onExit, state.onExit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, onEntry, onExit);
    }
}
