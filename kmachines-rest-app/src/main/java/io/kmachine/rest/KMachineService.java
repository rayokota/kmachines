package io.kmachine.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.jakarta.rs.yaml.YAMLMediaTypes;
import io.kmachine.model.StateMachine;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


@Path("/kmachines")
public interface KMachineService {

    @POST
    @Consumes(YAMLMediaTypes.TEXT_JACKSON_YAML)
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
