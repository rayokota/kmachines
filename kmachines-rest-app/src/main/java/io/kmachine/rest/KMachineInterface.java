package io.kmachine.rest;

import com.fasterxml.jackson.databind.JsonNode;
import io.kmachine.model.StateMachine;

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

@Path("/kmachines")
public interface KMachineInterface {

    @POST
    @Consumes("text/yaml")
    @Produces(MediaType.APPLICATION_JSON)
    Response createKMachine(StateMachine stateMachine);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Response getKMachines();

    @POST
    @Path("/{id}/state")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Response getKMachineState(@PathParam("id") String id,
                              @QueryParam("redirect") boolean redirect,
                              JsonNode key);

    @GET
    @Path("/{id}/meta-data")
    @Produces(MediaType.APPLICATION_JSON)
    Response getMetaData(@PathParam("id") String id);

    @DELETE
    @Path("/{id}")
    Response deleteKMachine(@PathParam("id") String id);
}
