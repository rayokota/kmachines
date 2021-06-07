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
public class Transition {

    private String type;
    private String from;
    // If to is null, it is an internal transition
    private String to;
    private String guard;
    private String onTransition;

    public Transition(@JsonProperty("type") String type,
                      @JsonProperty("from") String from,
                      @JsonProperty("to") String to,
                      @JsonProperty("guard") String guard,
                      @JsonProperty("onTransition") String onTransition) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.guard = guard;
        this.onTransition = onTransition;
    }

    public Transition() {
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    @JsonProperty("from")
    public String getFrom() {
        return from;
    }

    @JsonProperty("from")
    public void setFrom(String from) {
        this.from = from;
    }

    @JsonProperty("to")
    public String getTo() {
        return to;
    }

    @JsonProperty("to")
    public void setTo(String to) {
        this.to = to;
    }

    @JsonProperty("guard")
    public String getGuard() {
        return guard;
    }

    @JsonProperty("guard")
    public void setGuard(String guard) {
        this.guard = guard;
    }

    @JsonProperty("onTransition")
    public String getOnTransition() {
        return onTransition;
    }

    @JsonProperty("onTransition")
    public void setOnTransition(String onTransition) {
        this.onTransition = onTransition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transition that = (Transition) o;
        return Objects.equals(type, that.type)
            && Objects.equals(from, that.from)
            && Objects.equals(to, that.to)
            && Objects.equals(guard, that.guard)
            && Objects.equals(onTransition, that.onTransition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, from, to, guard, onTransition);
    }
}
