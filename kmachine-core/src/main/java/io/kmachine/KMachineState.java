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

import org.apache.kafka.streams.KafkaStreams;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class KMachineState {

    private final KafkaStreams streams;
    private final State state;

    public KMachineState(KafkaStreams streams, State state) {
        this.streams = streams;
        this.state = state;
    }

    public KafkaStreams streams() {
        return streams;
    }

    public State state() {
        return state;
    }

    public enum State {
        CREATED(0),
        RUNNING(1),
        COMPLETED(2),
        HALTED(3),
        ERROR(4);

        private static final Map<Integer, State> lookup = new HashMap<>();

        static {
            for (State m : EnumSet.allOf(State.class)) {
                lookup.put(m.code(), m);
            }
        }

        private final int code;

        State(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }

        public static State get(int code) {
            return lookup.get(code);
        }
    }
}
