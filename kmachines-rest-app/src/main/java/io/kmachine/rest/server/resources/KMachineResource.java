package io.kmachine.rest.server.resources;

import com.fasterxml.jackson.databind.JsonNode;
import io.kmachine.KMachine;
import io.kmachine.model.StateMachine;
import io.kmachine.rest.KMachineInterface;
import io.kmachine.rest.server.KMachineManager;
import io.kmachine.rest.server.leader.KMachineLeaderElector;
import io.kmachine.rest.server.streams.DataResult;
import io.kmachine.rest.server.streams.InteractiveQueries;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

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
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.net.URISyntaxException;

@ApplicationScoped
@Path("/kmachines")
public class KMachineResource implements KMachineInterface {

    @Inject
    KMachineManager manager;

    @Inject
    KMachineLeaderElector elector;

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @ConfigProperty(name = "quarkus.http.ssl-port")
    int sslPort;

    @POST
    @Consumes("text/yaml")
    @Produces(MediaType.APPLICATION_JSON)
    public Response createKMachine(StateMachine stateMachine) {
        try {
            manager.create(stateMachine.getName(), stateMachine);
            return Response.ok(stateMachine).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.CONFLICT.getStatusCode(), e.getMessage()).build();
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
            new InteractiveQueries(machine.getStreams(), machine.getStoreName(), port);
        DataResult result = interactiveQueries.getData(key);
        if (result.getData().isPresent()) {
            return Response.ok(result.getData().get()).build();
        } else if (result.getHost().isPresent()) {
            URI otherUri = getOtherUri(result.getHost().get(), result.getPort().getAsInt(), id);
            if (redirect) {
                return Response.seeOther(otherUri).build();
            } else {
                Client client = ClientBuilder.newClient();
                WebTarget target = client.target(otherUri);
                ResteasyWebTarget rtarget = (ResteasyWebTarget) target;
                KMachineInterface kmachine = rtarget.proxy(KMachineInterface.class);
                return kmachine.getKMachineState(id, redirect, key);
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
            new InteractiveQueries(machine.getStreams(), machine.getStoreName(), port);
        return Response.ok(interactiveQueries.getMetaData()).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteKMachine(@PathParam("id") String id) {
        manager.remove(id);
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
