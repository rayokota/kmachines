package io.kmachine.rest.server.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kmachine.KMachine;
import io.kmachine.model.StateMachine;
import io.kmachine.rest.KMachineInterface;
import io.kmachine.rest.server.KMachineManager;
import io.kmachine.rest.server.leader.KMachineIdentity;
import io.kmachine.rest.server.leader.KMachineLeaderElector;
import io.kmachine.rest.server.streams.DataResult;
import io.kmachine.rest.server.streams.InteractiveQueries;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
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
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;

@ApplicationScoped
@Path("/kmachines")
public class KMachineResource {

    private static final Logger LOG = Logger.getLogger(KMachineResource.class);

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

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
    public Response createKMachine(@Context UriInfo info, StateMachine stateMachine) {
        if (elector.isLeader()) {
            try {
                LOG.info("Creating machine " + stateMachine.getName());
                manager.create(stateMachine.getName(), stateMachine);
                return Response.ok(stateMachine).build();
            } catch (IllegalArgumentException e) {
                return Response.status(Status.CONFLICT.getStatusCode(), e.getMessage()).build();
            }
        } else {
            Response response = null;
            try {
                URI leaderURI = getLeaderUri(elector.getLeader(), info);
                Client client = ClientBuilder.newClient();
                WebTarget target = client.target(leaderURI);
                // Note: couldn't get proxy to work
                /*
                ResteasyWebTarget rtarget = (ResteasyWebTarget) target;
                KMachineInterface proxy = rtarget.proxy(KMachineInterface.class);
                response = proxy.createKMachine(stateMachine);
                return response;
                */
                String text = MAPPER.writeValueAsString(stateMachine);
                response = target.request().post(Entity.entity(text, "text/yaml"));
                response.bufferEntity();
                Response.ResponseBuilder responseBuilder = Response.fromResponse(response);
                return responseBuilder.build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            } finally {
                if (response != null) {
                    response.close();
                }
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
            new InteractiveQueries(machine.getStreams(), machine.getStoreName(), manager.host(), port);
        DataResult result = interactiveQueries.getData(key);
        if (result.getData().isPresent()) {
            return Response.ok(result.getData().get()).build();
        } else if (result.getHost().isPresent()) {
            URI otherUri = getOtherUri(result.getHost().get(), result.getPort().getAsInt(), id);
            if (redirect) {
                return Response.seeOther(otherUri).build();
            } else {
                Response response = null;
                try {
                    Client client = ClientBuilder.newClient();
                    WebTarget target = client.target(otherUri);
                    // Note: couldn't get proxy to work
                    /*
                    ResteasyWebTarget rtarget = (ResteasyWebTarget) target;
                    KMachineInterface proxy = rtarget.proxy(KMachineInterface.class);
                    response = proxy.getKMachineState(id, redirect, key);
                    */
                    response = target.request().post(Entity.entity(key, MediaType.APPLICATION_JSON_TYPE));
                    response.bufferEntity();
                    Response.ResponseBuilder responseBuilder = Response.fromResponse(response);
                    return responseBuilder.build();
                } finally {
                    if (response != null) {
                        response.close();
                    }
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
            new InteractiveQueries(machine.getStreams(), machine.getStoreName(), manager.host(), port);
        return Response.ok(interactiveQueries.getMetaData()).build();
    }

    @DELETE
    @Path("/{id}")
    public Response deleteKMachine(@Context UriInfo info, @PathParam("id") String id) {
        if (elector.isLeader()) {
            LOG.info("Deleting machine " + id);
            manager.remove(id);
            return Response.noContent().build();
        } else {
            Response response = null;
            try {
                URI leaderURI = getLeaderUri(elector.getLeader(), info);
                Client client = ClientBuilder.newClient();
                WebTarget target = client.target(leaderURI);
                // Note: couldn't get proxy to work
                /*
                ResteasyWebTarget rtarget = (ResteasyWebTarget) target;
                KMachineInterface proxy = rtarget.proxy(KMachineInterface.class);
                response = proxy.deleteKMachine(id);
                */
                response = target.request().delete();
                response.bufferEntity();
                Response.ResponseBuilder responseBuilder = Response.fromResponse(response);
                return responseBuilder.build();
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }
    }

    private URI getLeaderUri(KMachineIdentity leader, UriInfo info) {
        URI uri = info.getRequestUri();
        UriBuilder uriBuilder = UriBuilder.fromUri(uri).uri(leader.getUrl());
        return uriBuilder.build();
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
