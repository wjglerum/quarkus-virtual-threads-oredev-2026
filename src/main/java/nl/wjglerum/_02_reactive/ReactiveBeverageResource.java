package nl.wjglerum._02_reactive;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.List;

@Path("/beverage/reactive")
public class ReactiveBeverageResource {

    @Inject
    ReactiveBartender bartender;

    @Inject
    ReactiveBeverageRepository repository;

    @GET
    @Transactional
    public Uni<ReactiveBeverage> getBeverage() {
        Log.info("Going to get reactive beverage");
        return bartender.get().flatMap(beverage -> repository.save(beverage));
    }

    @GET
    @Path("/sequential")
    @Transactional
    public Uni<List<ReactiveBeverage>> getBeverageSequential() {
        Log.info("Going to get reactive beverages sequential");
        return bartender.get().flatMap(b1 ->
                bartender.get().flatMap(b2 ->
                        bartender.get().flatMap(b3 -> {
                                    var beverages = List.of(b1, b2, b3);
                                    return repository.save(beverages);
                                }
                        )
                )
        );
    }

    @GET
    @Path("/parallel")
    @Transactional
    public Uni<List<ReactiveBeverage>> getBeveragesParallel() {
        Log.info("Going to get reactive beverages parallel");
        var b1 = bartender.get();
        var b2 = bartender.get();
        var b3 = bartender.get();
        return Uni.join().all(b1, b2, b3).andCollectFailures()
                .flatMap(beverages -> repository.save(beverages));
    }
}
