package io.kmachine.rest.server.streams;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.json.JsonSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyQueryMetadata;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.jboss.logging.Logger;
import org.wildfly.common.net.HostName;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InteractiveQueries {

    private static final Logger LOG = Logger.getLogger(InteractiveQueries.class);

    private final KafkaStreams streams;
    private final String storeName;
    private final int port;

    public InteractiveQueries(KafkaStreams streams, String storeName, int port) {
        this.streams = streams;
        this.storeName = storeName;
        this.port = port;
    }

    public List<PipelineMetadata> getMetaData() {
        return streams.allMetadataForStore(storeName)
                .stream()
                .map(m -> new PipelineMetadata(
                        m.hostInfo().host() + ":" + m.hostInfo().port(),
                        m.topicPartitions()
                                .stream()
                                .map(TopicPartition::toString)
                                .collect(Collectors.toSet())))
                .collect(Collectors.toList());
    }

    public DataResult getData(JsonNode key) {
        KeyQueryMetadata metadata = streams.queryMetadataForKey(
            storeName,
            key,
            new JsonSerializer());
        if (metadata == null || metadata == KeyQueryMetadata.NOT_AVAILABLE) {
            LOG.warnv("Found no metadata for key {0}", key);
            return DataResult.notFound();
        } else if (metadata.activeHost().host().equals(HostName.getQualifiedHostName())
            && metadata.activeHost().port() == port) {
            LOG.infov("Found data for key {0} locally", key);
            Map<String, Object> data = getDataStore().get(key);
            if (data != null) {
                return DataResult.found(data);
            } else {
                return DataResult.notFound();
            }
        } else {
            LOG.infov("Found data for key {0} on remote host {1}:{2}", key, metadata.activeHost().host(), metadata.activeHost().port());
            return DataResult.foundRemotely(metadata.activeHost().host(), metadata.activeHost().port());
        }
    }

    private ReadOnlyKeyValueStore<JsonNode, Map<String, Object>> getDataStore() {
        while (true) {
            try {
                return streams.store(StoreQueryParameters.fromNameAndType(storeName, QueryableStoreTypes.keyValueStore()));
            } catch (InvalidStateStoreException e) {
                // ignore, store not ready yet
            }
        }
    }
}
