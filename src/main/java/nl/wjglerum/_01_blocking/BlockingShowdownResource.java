package nl.wjglerum._01_blocking;

import io.quarkus.logging.Log;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import nl.wjglerum.ErrorResult;
import nl.wjglerum.FloodResult;

import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

/**
 * Showdown demos for the blocking model: flood the bartender and fail fast.
 * The basic examples live in {@link BlockingBeverageResource}.
 */
@Path("/beverage/blocking")
public class BlockingShowdownResource {

    @Inject
    BlockingBartender bartender;

    @Inject
    FlakeyBlockingBartender flakeyBartender;

    @Inject
    ManagedExecutor executor;

    // A classic fixed-size pool for the flood demo: 50 threads, 25 queued, the rest rejected
    private final ExecutorService floodPool =
            new ThreadPoolExecutor(50, 50, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(25));

    @PreDestroy
    void shutdownFloodPool() {
        floodPool.shutdownNow();
    }

    @GET
    @Path("/flood")
    public FloodResult flood(@QueryParam("count") @DefaultValue("100") int count) {
        Log.infof("Flooding with %d blocking requests", count);
        var succeeded = new AtomicInteger();
        var failed = new AtomicInteger();
        var start = System.currentTimeMillis();
        var futures = new ArrayList<Future<?>>(count);
        for (int i = 0; i < count; i++) {
            try {
                futures.add(floodPool.submit(() -> {
                    try {
                        bartender.get();
                        succeeded.incrementAndGet();
                    } catch (Exception e) {
                        failed.incrementAndGet();
                    }
                }));
            } catch (RejectedExecutionException e) {
                failed.incrementAndGet();
            }
        }
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
    @Path("/failfast")
    public Response getBeveragesFailFast() {
        Log.info("Going to get beverages fail-fast (blocking) — no sibling cancellation unlike StructuredTaskScope");
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
