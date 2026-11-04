package nl.wjglerum._03_virtual;

import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import nl.wjglerum.ErrorResult;
import nl.wjglerum.FloodResult;

import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

/**
 * Showdown demos for virtual threads: flood the bartender, race bartenders and fail fast
 * without structured concurrency. The basic examples live in {@link VirtualBeverageResource}.
 */
@Path("/beverage/virtual")
@RunOnVirtualThread
public class VirtualShowdownResource {

    @Inject
    VirtualBartender bartender;

    @Inject
    FlakeyVirtualBartender flakeyBartender;

    @Inject
    @VirtualThreads
    ExecutorService executor;

    @GET
    @Path("/flood")
    public FloodResult flood(@QueryParam("count") @DefaultValue("100") int count) {
        Log.infof("Flooding with %d virtual-thread requests", count);
        var succeeded = new AtomicInteger();
        var failed = new AtomicInteger();
        var start = System.currentTimeMillis();
        var futures = IntStream.range(0, count)
                .mapToObj(_ -> executor.submit(() -> {
                    try {
                        bartender.get();
                        succeeded.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                }))
                .toList();
        for (var f : futures) {
            try {
                f.get();
            } catch (ExecutionException ignored) {
                // failures are already counted inside the task
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return new FloodResult(count, succeeded.get(), failed.get(), System.currentTimeMillis() - start);
    }

    @GET
    @Path("/race")
    public VirtualBeverage getBeverageRace() throws InterruptedException, ExecutionException {
        Log.info("Going to race 3 bartenders with CompletableFuture — losers keep brewing");
        var b1 = CompletableFuture.supplyAsync(bartender::race, executor);
        var b2 = CompletableFuture.supplyAsync(bartender::race, executor);
        var b3 = CompletableFuture.supplyAsync(bartender::race, executor);
        var winner = (VirtualBeverage) CompletableFuture.anyOf(b1, b2, b3).get();
        Log.info("Winner served, returning: the other bartenders are still brewing");
        return winner;
    }

    @GET
    @Path("/failfast")
    public Response getBeveragesFailFast() {
        Log.info("Going to get beverages fail-fast (virtual) — no sibling cancellation unlike StructuredTaskScope");
        var b1 = executor.submit(flakeyBartender::get);
        var b2 = executor.submit(flakeyBartender::get);
        var b3 = executor.submit(flakeyBartender::get);
        try {
            return Response.ok(List.of(b1.get(), b2.get(), b3.get())).build();
        } catch (ExecutionException e) {
            return error(SERVICE_UNAVAILABLE, e.getCause().getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(SERVICE_UNAVAILABLE, "interrupted");
        }
    }

    private static Response error(Response.Status status, String message) {
        return Response.status(status).entity(new ErrorResult(message)).build();
    }
}
