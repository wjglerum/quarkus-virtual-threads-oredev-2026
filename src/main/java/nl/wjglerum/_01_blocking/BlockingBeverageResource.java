package nl.wjglerum._01_blocking;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Path("/beverage/blocking")
public class BlockingBeverageResource {

    @Inject
    BlockingBartender bartender;

    @Inject
    BlockingBeverageRepository repository;

    @Inject
    ManagedExecutor executor;

    @GET
    @Transactional
    public BlockingBeverage getBeverage() {
        Log.info("Going to get blocking beverage");
        var beverage = bartender.get();
        repository.save(beverage);
        return beverage;
    }

    @GET
    @Path("/sequential")
    @Transactional
    public List<BlockingBeverage> getBeveragesSequential() {
        Log.info("Going to get blocking beverages sequential");
        var b1 = bartender.get();
        var b2 = bartender.get();
        var b3 = bartender.get();
        var beverages = List.of(b1, b2, b3);
        repository.save(beverages);
        return beverages;
    }

    @GET
    @Path("/parallel")
    @Transactional
    public List<BlockingBeverage> getBeveragesParallel() {
        Log.info("Going to get blocking beverages parallel");
        try {
            var b1 = executor.submit(bartender::get);
            var b2 = executor.submit(bartender::get);
            var b3 = executor.submit(bartender::get);
            var beverages = List.of(b1.get(), b2.get(), b3.get());
            repository.save(beverages);
            return beverages;
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
