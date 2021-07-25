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
package io.kmachine.rest.server;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "kmachine")
public interface KMachineConfig {

    // The host name used in leader election. Make sure to set this if running with multiple nodes.
    @WithName("host.name")
    Optional<String> hostName();

    // The group ID used for leader election.
    @WithName("cluster.group.id")
    @WithDefault("kmachine")
    String clusterGroupId();

    // If true, this node can participate in leader election. In a multi-colo setup, turn this off
    // for clusters in the replica data center.
    @WithName("leader.eligibility")
    @WithDefault("true")
    boolean isLeaderEligible();

    // Configuration properties for KCache.
    @WithName("kafkacache")
    Map<String, String> kafkaCacheConfig();
}
