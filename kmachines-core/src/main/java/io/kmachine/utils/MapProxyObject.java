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

package io.kmachine.utils;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.List;
import java.util.Map;

public class MapProxyObject implements ProxyObject {
    private final Map<String, Object> values;

    public MapProxyObject(Map<String, Object> values) {
        this.values = values;
    }

    @Override
    public void putMember(String key, Value value) {
        values.put(key, value.as(Object.class));
    }

    @Override
    public boolean hasMember(String key) {
        return values.containsKey(key);
    }

    @Override
    public Object getMemberKeys() {
        return new ProxyArray() {
            private final Object[] keys = values.keySet().toArray();

            public void set(long index, Value value) {
                throw new UnsupportedOperationException();
            }

            public long getSize() {
                return this.keys.length;
            }

            public Object get(long index) {
                if (index >= 0L && index <= 2147483647L) {
                    return this.keys[(int)index];
                } else {
                    throw new ArrayIndexOutOfBoundsException();
                }
            }
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object getMember(String key) {
        Object v = values.get(key);
        if (v instanceof Map) {
            return new MapProxyObject((Map<String, Object>) v);
        } else if (v instanceof List) {
            return new ListProxyArray((List<Object>)v);
        } else {
            return v;
        }
    }

    @Override
    public boolean removeMember(String key) {
        if (values.containsKey(key)) {
            values.remove(key);
            return true;
        } else {
            return false;
        }
    }

    public Map<String, Object> getMap() {
        return values;
    }
}
