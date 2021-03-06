/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.kmachine.rest.server.leader;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The identity of a group member.
 */
public class KMachineIdentity {

    public static final int CURRENT_VERSION = 1;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Integer version;
    private String scheme;
    private String host;
    private Integer port;
    private Boolean leaderEligibility;

    public KMachineIdentity(
        @JsonProperty(value = "scheme", defaultValue = "http") String scheme,
        @JsonProperty("host") String host,
        @JsonProperty("port") Integer port,
        @JsonProperty("leader_eligibility") Boolean leaderEligibility
    ) {
        this.version = CURRENT_VERSION;
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.leaderEligibility = leaderEligibility;
    }

    public static KMachineIdentity fromJson(String json) throws IOException {
        return MAPPER.readValue(json, KMachineIdentity.class);
    }

    public static KMachineIdentity fromJson(ByteBuffer json) {
        try {
            byte[] jsonBytes = new byte[json.remaining()];
            json.get(jsonBytes);
            return MAPPER.readValue(jsonBytes, KMachineIdentity.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Error deserializing identity information", e);
        }
    }

    @JsonProperty("version")
    public Integer getVersion() {
        return this.version;
    }

    @JsonProperty("version")
    public void setVersion(Integer version) {
        this.version = version;
    }

    @JsonProperty(value = "scheme", defaultValue = "http")
    public String getScheme() {
        return scheme;
    }

    @JsonProperty(value = "scheme", defaultValue = "http")
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @JsonProperty("host")
    public String getHost() {
        return this.host;
    }

    @JsonProperty("host")
    public void setHost(String host) {
        this.host = host;
    }

    @JsonProperty("port")
    public Integer getPort() {
        return this.port;
    }

    @JsonProperty("port")
    public void setPort(Integer port) {
        this.port = port;
    }

    @JsonProperty("leader_eligibility")
    public boolean getLeaderEligibility() {
        return this.leaderEligibility;
    }

    @JsonProperty("leader_eligibility")
    public void setLeaderEligibility(Boolean eligibility) {
        this.leaderEligibility = eligibility;
    }

    public static int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        KMachineIdentity that = (KMachineIdentity) o;

        if (!this.version.equals(that.version)) {
            return false;
        }
        if (!this.scheme.equals(that.scheme)) {
            return false;
        }
        if (!this.host.equals(that.host)) {
            return false;
        }
        if (!this.port.equals(that.port)) {
            return false;
        }
        if (!this.leaderEligibility.equals(that.leaderEligibility)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = port;
        result = 31 * result + scheme.hashCode();
        result = 31 * result + host.hashCode();
        result = 31 * result + version;
        result = 31 * result + leaderEligibility.hashCode();
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("version=" + this.version + ",");
        sb.append("scheme=" + this.scheme + ",");
        sb.append("host=" + this.host + ",");
        sb.append("port=" + this.port + ",");
        sb.append("leaderEligibility=" + this.leaderEligibility);
        return sb.toString();
    }

    public String toJson() throws IOException {
        return MAPPER.writeValueAsString(this);
    }

    public ByteBuffer toJsonBytes() {
        try {
            return ByteBuffer.wrap(MAPPER.writeValueAsBytes(this));
        } catch (IOException e) {
            throw new IllegalArgumentException("Error serializing identity information", e);
        }
    }

    @JsonIgnore
    public String getUrl() {
        return String.format("%s://%s:%d", scheme, host, port);
    }

    @JsonIgnore
    public boolean isSecure() {
        return "https".equalsIgnoreCase(scheme);
    }
}
