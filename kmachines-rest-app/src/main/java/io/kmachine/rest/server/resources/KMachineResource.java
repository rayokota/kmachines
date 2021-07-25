package io.kmachine.rest.server.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.jaxrs.yaml.YAMLMediaTypes;
import io.kmachine.KMachine;
import io.kmachine.model.StateMachine;
import io.kmachine.rest.KMachineInterface;
import io.kmachine.rest.server.KMachineManager;
import io.kmachine.rest.server.leader.KMachineLeaderElector;
import io.kmachine.rest.server.streams.DataResult;
import io.kmachine.rest.server.streams.InteractiveQueries;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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

@ApplicationScoped
@Path("/kmachines")
public class KMachineResource implements KMachineInterface {

    private static final Logger LOG = Logger.getLogger(KMachineResource.class);

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    @Inject
    KMachineManager manager;

    @Inject
    KMachineLeaderElector elector;

    @ConfigProperty(name = "quarkus.http.ssl-port")
    int sslPort;

    @POST
    @Consumes(YAMLMediaTypes.TEXT_JACKSON_YAML)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createKMachine(StateMachine stateMachine) {
        if (elector.isLeader()) {
            try {
                LOG.info("Creating machine " + stateMachine.getName());
                manager.create(stateMachine.getName(), stateMachine);
                return Response.ok(stateMachine).build();
            } catch (IllegalArgumentException e) {
                return Response.status(Status.CONFLICT.getStatusCode(), e.getMessage()).build();
            }
        } else {
            try (Response response = getClient(getLeaderUri()).createKMachine(stateMachine)) {
                response.bufferEntity();
                return Response.fromResponse(response).build();
            }
        }
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
    public Response getKMachineState(@PathParam("id") String id,
                                     @QueryParam("redirect") boolean redirect,
                                     JsonNode key) {
        KMachine machine = manager.get(id);
        if (machine == null) {
            return Response.status(Status.NOT_FOUND.getStatusCode(), "No kmachine found for " + id).build();
        }
        InteractiveQueries interactiveQueries =
            new InteractiveQueries(machine.getStreams(), machine.getStoreName(), manager.uri());
        DataResult result = interactiveQueries.getData(key);
        if (result.getData().isPresent()) {
            return Response.ok(result.getData().get()).build();
        } else if (result.getHost().isPresent()) {
            URI otherUri = getOtherUri(result.getHost().get(), result.getPort().getAsInt());
            if (redirect) {
                return Response.seeOther(otherUri.resolve("kmachines/" + id + "/state")).build();
            } else {
                try (Response response = getClient(otherUri).getKMachineState(id, redirect, key)) {
                    response.bufferEntity();
                    return Response.fromResponse(response).build();
                }
            }
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
            new InteractiveQueries(machine.getStreams(), machine.getStoreName(), manager.uri());
        return Response.ok(interactiveQueries.getMetaData()).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteKMachine(@PathParam("id") String id) {
        if (elector.isLeader()) {
            LOG.info("Deleting machine " + id);
            manager.remove(id);
            return Response.noContent().build();
        } else {
            try (Response response = getClient(getLeaderUri()).deleteKMachine(id)) {
                response.bufferEntity();
                return Response.fromResponse(response).build();
            }
        }
    }

    private KMachineInterface getClient(URI uri) {
        return RestClientBuilder.newBuilder()
            .baseUri(uri)
            .build(KMachineInterface.class);
    }

    private URI getLeaderUri() {
        try {
            return new URI(elector.getLeader().getUrl());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private URI getOtherUri(String host, int port) {
        try {
            String scheme = (port == sslPort) ? "https" : "http";
            return new URI(scheme + "://" + host + ":" + port + "/");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
