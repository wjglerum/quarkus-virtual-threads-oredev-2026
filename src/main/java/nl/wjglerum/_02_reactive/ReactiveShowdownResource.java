package nl.wjglerum._02_reactive;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import nl.wjglerum.ErrorResult;
import nl.wjglerum.FloodResult;

import java.util.stream.IntStream;

import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

/**
 * Showdown demos for the reactive model: flood the bartender and fail fast.
 * The basic examples live in {@link ReactiveBeverageResource}.
 */
@Path("/beverage/reactive")
public class ReactiveShowdownResource {

    @Inject
    ReactiveBartender bartender;

    @Inject
    FlakeyReactiveBartender flakeyBartender;

    @GET
    @Path("/flood")
    public Uni<FloodResult> flood(@QueryParam("count") @DefaultValue("100") int count) {
        Log.infof("Flooding with %d reactive requests", count);
        var start = System.currentTimeMillis();
        var unis = IntStream.range(0, count)
                .mapToObj(i -> bartender.get().map(b -> 1).onFailure().recoverWithItem(0))
                .toList();
        return Uni.join().all(unis).andCollectFailures()
                .map(results -> {
                    var succeeded = results.stream().mapToInt(Integer::intValue).sum();
                    return new FloodResult(count, succeeded, count - succeeded, System.currentTimeMillis() - start);
                });
    }

    @GET
    @Path("/failfast")
    public Uni<Response> getBeveragesFailFast() {
        Log.info("Going to get beverages fail-fast (reactive) — first failure cancels the join");
        var b1 = flakeyBartender.get();
        var b2 = flakeyBartender.get();
        var b3 = flakeyBartender.get();
        return Uni.join().all(b1, b2, b3).andFailFast()
                .map(beverages -> Response.ok(beverages).build())
                .onFailure().recoverWithItem(e -> error(SERVICE_UNAVAILABLE, e.getMessage()));
    }

    private static Response error(Response.Status status, String message) {
        return Response.status(status).entity(new ErrorResult(message)).build();
    }
}
