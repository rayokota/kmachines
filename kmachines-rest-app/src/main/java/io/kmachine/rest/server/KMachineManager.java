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

package io.kmachine.rest.server;

import io.kmachine.KMachine;
import io.kmachine.model.StateMachine;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class KMachineManager {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    private Map<String, KMachine> machines = new ConcurrentHashMap<>();

    public String bootstrapServers() {
        return bootstrapServers;
    }

    public KMachine create(String id, StateMachine stateMachine) {
        if (machines.containsKey(id)) {
            throw new IllegalArgumentException("KMachine already exists with id: " + id);
        }
        KMachine m = new KMachine(id, bootstrapServers(), stateMachine);
        machines.put(id, m);
        return m;
    }

    public Set<String> list() {
        return machines.keySet();
    }

    public KMachine get(String id) {
        return machines.get(id);
    }

    public KMachine remove(String id) {
        return machines.remove(id);
    }
}
