package io.kmachine.rest.server;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.SseEventSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;

@QuarkusTest
class HelloResourceTest {

    private static final String HELLO_ENDPOINT = "http://localhost:8081/kmachines/hello";

    @Test
    void testHello() {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(HELLO_ENDPOINT);

        List<Double> received = new CopyOnWriteArrayList<>();

        Response response = target.request().get();
        String value = response.readEntity(String.class);
        response.close();  // You should close connections!

        System.out.println("** value " + value);
    }
    /*
    private static final String PRICES_SSE_ENDPOINT = "http://localhost:8081/prices/stream";

    @Test
    void testPricesEventStream() {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(PRICES_SSE_ENDPOINT);

        List<Double> received = new CopyOnWriteArrayList<>();

        SseEventSource source = SseEventSource.target(target).build();
        source.register(inboundSseEvent -> received.add(Double.valueOf(inboundSseEvent.readData())));
        source.open();
        await().atMost(100000, MILLISECONDS).until(() -> received.size() == 3);
        source.close();
    }

     */
}