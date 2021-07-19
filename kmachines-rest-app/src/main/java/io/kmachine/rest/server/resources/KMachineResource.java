package io.kmachine.rest.server.resources;

import com.fasterxml.jackson.databind.JsonNode;
import io.kmachine.KMachine;
import io.kmachine.model.StateMachine;
import io.kmachine.rest.server.KMachineManager;
import io.kmachine.rest.server.streams.DataResult;
import io.kmachine.rest.server.streams.InteractiveQueries;
import io.kmachine.utils.ClientUtils;
import io.kmachine.utils.JsonSerde;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.wildfly.common.net.HostName;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

@ApplicationScoped
@Path("/kmachines")
public class KMachineResource {

    @Inject
    KMachineManager manager;

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @ConfigProperty(name = "quarkus.http.ssl-port")
    int sslPort;

    @POST
    @Consumes("text/yaml")
    @Produces(MediaType.APPLICATION_JSON)
    public Response createKMachine(StateMachine stateMachine) {
        try {
            String id = stateMachine.getName();
            KMachine machine = manager.create(stateMachine.getName(), stateMachine);
            Properties streamsConfiguration = ClientUtils.streamsConfig(id, "client-" + id,
                manager.bootstrapServers(), JsonSerde.class, JsonSerde.class);
            streamsConfiguration.put(StreamsConfig.APPLICATION_SERVER_CONFIG, getApplicationServer());
            machine.configure(new StreamsBuilder(), streamsConfiguration);
            return Response.ok(stateMachine).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.CONFLICT.getStatusCode(), e.getMessage()).build();
        }
    }

    private String getApplicationServer() {
        return HostName.getQualifiedHostName() + ":" + port;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getKMachines() {
        return Response.ok(manager.list()).build();
    }

    @POST
    @Path("/{id}/state")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getKMachineState(@PathParam("id") String id, JsonNode key) {
        KMachine machine = manager.get(id);
        if (machine == null) {
            return Response.status(Status.NOT_FOUND.getStatusCode(), "No kmachine found for " + id).build();
        }
        InteractiveQueries interactiveQueries =
            new InteractiveQueries(machine.getStreams(), machine.getStoreName(), port);
        DataResult result = interactiveQueries.getData(key);
        if (result.getData().isPresent()) {
            return Response.ok(result.getData().get()).build();
        } else if (result.getHost().isPresent()) {
            URI otherUri = getOtherUri(result.getHost().get(), result.getPort().getAsInt(), id);
            return Response.seeOther(otherUri).build();
        } else {
            return Response.status(Status.NOT_FOUND.getStatusCode(), "No data found for kmachine " + id).build();
        }
    }

    @GET
    @Path("/{id}/meta-data")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMetaData(@PathParam("id") String id) {
        KMachine machine = manager.get(id);
        if (machine == null) {
            return Response.status(Status.NOT_FOUND.getStatusCode(), "No kmachine found for " + id).build();
        }
        InteractiveQueries interactiveQueries =
            new InteractiveQueries(machine.getStreams(), machine.getStoreName(), port);
        return Response.ok(interactiveQueries.getMetaData()).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteKMachine(@PathParam("id") String id) {
        KMachine machine = manager.remove(id);
        machine.close();
        return Response.noContent().build();
    }

    private URI getOtherUri(String host, int port, String id) {
        try {
            String scheme = (port == sslPort) ? "https" : "http";
            return new URI(scheme + "://" + host + ":" + port + "/kmachines/" + id + "/state");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
