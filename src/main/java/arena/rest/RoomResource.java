package arena.rest;

import arena.game.Room;
import arena.game.RoomRegistry;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/room")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomResource {

    @Inject RoomRegistry registry;

    public record CreateRequest(String name) {}
    public record CreateResponse(String code) {}
    public record CheckResponse(boolean exists, int playerCount, String state) {}

    @POST
    public Response create(CreateRequest req) {
        Room room = registry.create();
        return Response.ok(new CreateResponse(room.code)).build();
    }

    @GET
    @Path("/{code}")
    public Response check(@PathParam("code") String code) {
        Room room = registry.get(code);
        if (room == null) {
            return Response.ok(new CheckResponse(false, 0, "")).build();
        }
        return Response.ok(new CheckResponse(true, room.playerCount(), room.state.name())).build();
    }
}
