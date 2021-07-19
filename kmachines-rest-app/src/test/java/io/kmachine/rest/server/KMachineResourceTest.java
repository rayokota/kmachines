package io.kmachine.rest.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.kmachine.model.StateMachine;
import io.kmachine.utils.ClientUtils;
import io.kmachine.utils.JsonSerde;
import io.kmachine.utils.StreamUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.connect.json.JsonSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

@QuarkusTest
class KMachineResourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CREATE_ENDPOINT = "http://localhost:8081/kmachines";

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @Test
    void testWestWorld() throws Exception {
        String suffix1 = "1";
        String suffix2 = "2";
        StreamsBuilder builder = new StreamsBuilder();

        Properties producerConfig = ClientUtils.producerConfig(bootstrapServers, JsonSerializer.class,
            JsonSerializer.class, new Properties()
        );

        ObjectNode node1 = MAPPER.createObjectNode();
        node1.put("type", "stayHome");
        node1.put("wife", "Elsa");
        KStream<JsonNode, JsonNode> stream1 =
            StreamUtils.streamFromCollection(builder, producerConfig, "miner", 3, (short) 1, new JsonSerde(), new JsonSerde(),
                List.of(
                    new KeyValue<>(new TextNode("Bob"), node1)
                )
            );

        Topology topology = builder.build();
        Properties props = ClientUtils.streamsConfig("prepare-" + suffix1, "prepare-client-" + suffix1,
            bootstrapServers, JsonSerde.class, JsonSerde.class);
        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();

        builder = new StreamsBuilder();
        ObjectNode node2 = MAPPER.createObjectNode();
        node2.put("type", "continueHouseWork");
        node2.put("husband", "Bob");
        KStream<JsonNode, JsonNode> stream2 =
            StreamUtils.streamFromCollection(builder, producerConfig, "miners_wife", 3, (short) 1, new JsonSerde(), new JsonSerde(),
                List.of(
                    new KeyValue<>(new TextNode("Elsa"), node2)
                )
            );

        topology = builder.build();
        props = ClientUtils.streamsConfig("prepare-" + suffix2, "prepare-client-" + suffix2,
            bootstrapServers, JsonSerde.class, JsonSerde.class);
        streams = new KafkaStreams(topology, props);
        streams.start();

        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(CREATE_ENDPOINT);

        Path path = Paths.get(getClass().getClassLoader().getResource("miner_messaging.yml").toURI());
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Response response = target.request().post(Entity.entity(text, "text/yaml"));
        StateMachine stateMachine = response.readEntity(StateMachine.class);
        response.close();  // You should close connections!

        path = Paths.get(getClass().getClassLoader().getResource("miners_wife_messaging.yml").toURI());
        text = Files.readString(path, StandardCharsets.UTF_8);
        response = target.request().post(Entity.entity(text, "text/yaml"));
        stateMachine = response.readEntity(StateMachine.class);
        response.close();  // You should close connections!

        Thread.sleep(30000);
    }
}