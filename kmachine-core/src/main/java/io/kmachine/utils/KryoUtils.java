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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;
import de.javakaffee.kryoserializers.CollectionsEmptyListSerializer;
import de.javakaffee.kryoserializers.CollectionsEmptyMapSerializer;
import de.javakaffee.kryoserializers.CollectionsEmptySetSerializer;
import de.javakaffee.kryoserializers.CollectionsSingletonListSerializer;
import de.javakaffee.kryoserializers.CollectionsSingletonMapSerializer;
import de.javakaffee.kryoserializers.CollectionsSingletonSetSerializer;
import de.javakaffee.kryoserializers.SynchronizedCollectionsSerializer;
import de.javakaffee.kryoserializers.UnmodifiableCollectionsSerializer;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

// TODO upgrade Kryo
public class KryoUtils {
    private static final Logger log = LoggerFactory.getLogger(KryoUtils.class);

    private static final List<String> SINGLETON_LIST = Collections.singletonList("");
    private static final Set<String> SINGLETON_SET = Collections.singleton("");
    private static final Map<String, String> SINGLETON_MAP = Collections.singletonMap("", "");

    private static final KryoFactory factory = () -> {
        Kryo kryo = new Kryo();
        try {
            kryo.register(Collections.EMPTY_LIST.getClass(), new CollectionsEmptyListSerializer());
            kryo.register(Collections.EMPTY_MAP.getClass(), new CollectionsEmptyMapSerializer());
            kryo.register(Collections.EMPTY_SET.getClass(), new CollectionsEmptySetSerializer());
            kryo.register(SINGLETON_LIST.getClass(), new CollectionsSingletonListSerializer());
            kryo.register(SINGLETON_SET.getClass(), new CollectionsSingletonSetSerializer());
            kryo.register(SINGLETON_MAP.getClass(), new CollectionsSingletonMapSerializer());
            kryo.setRegistrationRequired(false);

            UnmodifiableCollectionsSerializer.registerSerializers(kryo);
            SynchronizedCollectionsSerializer.registerSerializers(kryo);
            ((Kryo.DefaultInstantiatorStrategy) kryo.getInstantiatorStrategy()).setFallbackInstantiatorStrategy(new
                StdInstantiatorStrategy());
        } catch (Exception e) {
            log.error("Could not initialize Kryo", e);
            throw e;
        }
        return kryo;
    };

    private static final KryoPool pool = new KryoPool.Builder(factory).softReferences().build();

    public static byte[] serialize(final Object obj) {

        return pool.run(kryo -> {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            Output output = new Output(stream);
            kryo.writeClassAndObject(output, obj);
            output.close();
            return stream.toByteArray();
        });
    }

    @SuppressWarnings("unchecked")
    public static <V> V deserialize(final byte[] objectData) {

        return pool.run(kryo -> {
            Input input = new Input(objectData);
            return (V) kryo.readClassAndObject(input);
        });
    }

    public static <V> V deepCopy(final V obj) {

        return pool.run(kryo -> kryo.copy(obj));
    }

}
