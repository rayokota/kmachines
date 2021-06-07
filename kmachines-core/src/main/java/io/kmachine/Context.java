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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Context {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ProcessorContext ctx;
    private final ScheduledExecutorService executor;
    private final Producer<JsonNode, JsonNode> producer;

    public Context(ProcessorContext ctx,
                   ScheduledExecutorService executor,
                   Producer<JsonNode, JsonNode> producer) {
        this.ctx = ctx;
        this.executor = executor;
        this.producer = producer;
    }

    @HostAccess.Export
    public String applicationId() {
        return ctx.applicationId();
    }

    @HostAccess.Export
    public String topic() {
        return ctx.topic();
    }

    @HostAccess.Export
    public int partition() {
        return ctx.partition();
    }

    @HostAccess.Export
    public long offset() {
        return ctx.offset();
    }

    @HostAccess.Export
    public long timestamp() {
        return ctx.timestamp();
    }

    @HostAccess.Export
    public void sendMessage(String topic, Value key, Value value, int delayMs) {
        Object keyObj = key.as(Object.class);
        Object valueObj = value.as(Object.class);
        JsonNode keyNode = MAPPER.convertValue(keyObj, JsonNode.class);
        JsonNode valueNode = MAPPER.convertValue(valueObj, JsonNode.class);
        ProducerRecord<JsonNode, JsonNode> record = new ProducerRecord<>(topic, keyNode, valueNode);
        if (delayMs > 0) {
            executor.schedule(() -> producer.send(record), delayMs, TimeUnit.MILLISECONDS);
        } else {
            producer.send(record);
        }
    }
}