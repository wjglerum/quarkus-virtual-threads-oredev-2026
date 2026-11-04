package nl.wjglerum._03_virtual;

import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Path("/beverage/virtual")
@RunOnVirtualThread
public class VirtualBeverageResource {

    @Inject
    VirtualBartender bartender;

    @Inject
    VirtualBeverageRepository repository;

    @Inject
    @VirtualThreads
    ExecutorService executor;

    @GET
    @Transactional
    public VirtualBeverage getBeverage() {
        Log.info("Going to get virtual beverage");
        var beverage = bartender.get();
        repository.save(beverage);
        return beverage;
    }

    @GET
    @Path("/sequential")
    @Transactional
    public List<VirtualBeverage> getBeveragesSequential() {
        Log.info("Going to get virtual beverages sequential");
        var beverage1 = bartender.get();
        var beverage2 = bartender.get();
        var beverage3 = bartender.get();
        var beverages = List.of(beverage1, beverage2, beverage3);
        repository.save(beverages);
        return beverages;
    }

    @GET
    @Path("/parallel")
    @Transactional
    public List<VirtualBeverage> getBeveragesParallel() {
        Log.info("Going to get virtual beverages parallel");
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

    @GET
    @Path("/custom")
    @Transactional
    public List<VirtualBeverage> getBeveragesCustom() {
        Log.info("Going to get virtual beverages custom");
        var name = Thread.currentThread().getName();
        var threadFactory = Thread.ofVirtual().name(name + "-virtual-", 0).factory();
        try(var executor = Executors.newThreadPerTaskExecutor(threadFactory)) {
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
