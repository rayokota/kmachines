package io.kmachine.rest.server.resources;

import io.kmachine.KMachine;
import io.kmachine.model.StateMachine;
import io.kmachine.rest.server.KMachineManager;
import io.kmachine.utils.ClientUtils;
import io.kmachine.utils.JsonSerde;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;

@ApplicationScoped
@Path("/kmachines")
public class KMachineResource {

    @Inject
    KMachineManager manager;
    /*
    @Inject
    InteractiveQueries interactiveQueries;

     */

    /*
    @ConfigProperty(name = "quarkus.http.ssl-port")
    int sslPort;

     */

    @GET
    @Path("/hello")
    @Produces("html/text")
    public Response getHello() {
        return Response.ok("Hello", "html/text").build();
    }

    @POST
    @Consumes("application/yaml")
    @Produces(MediaType.APPLICATION_JSON)
    public Response createKMachine(StateMachine stateMachine) {
        try {
            String id = stateMachine.getName();
            KMachine machine = manager.create(stateMachine.getName(), stateMachine);
            Properties streamsConfiguration = ClientUtils.streamsConfig(id, "client-" + id,
                manager.bootstrapServers(), JsonSerde.class, JsonSerde.class);
            machine.configure(new StreamsBuilder(), streamsConfiguration);
            return Response.ok().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.CONFLICT.getStatusCode(), e.getMessage()).build();
        }
    }

    /*
    @GET
    @Path("/data/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWeatherStationData(@PathParam("id") int id) {
        GetWeatherStationDataResult result = interactiveQueries.getWeatherStationData(id);

        if (result.getResult().isPresent()) {
            return Response.ok(result.getResult().get()).build();
        } else if (result.getHost().isPresent()) {
            URI otherUri = getOtherUri(result.getHost().get(), result.getPort().getAsInt(), id);
            return Response.seeOther(otherUri).build();
        } else {
            return Response.status(Status.NOT_FOUND.getStatusCode(), "No data found for weather station " + id).build();
        }
    }

    @GET
    @Path("/meta-data")
    @Produces(MediaType.APPLICATION_JSON)
    public List<PipelineMetadata> getMetaData() {
        return interactiveQueries.getMetaData();
    }

    private URI getOtherUri(String host, int port, int id) {
        try {
            String scheme = (port == sslPort) ? "https" : "http";
            return new URI(scheme + "://" + host + ":" + port + "/weather-stations/data/" + id);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

     */
}
