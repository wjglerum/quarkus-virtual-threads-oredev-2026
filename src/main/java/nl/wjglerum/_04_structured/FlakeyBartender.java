package nl.wjglerum._04_structured;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.wjglerum.client.CoffeeMachineClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class FlakeyBartender {

    @Inject
    @RestClient
    CoffeeMachineClient coffeeMachine;

    // Random 1-3 s brews, so a failing bartender can cancel the ones that are still brewing
    public StructuredBeverage get() {
        Log.info("Warming up the flakey coffee machine (50% chance of failure)");
        var start = System.currentTimeMillis();
        try {
            var response = coffeeMachine.brewRandom();
            if (ThreadLocalRandom.current().nextBoolean()) {
                Log.infof("Coffee machine broke after %d ms!", System.currentTimeMillis() - start);
                throw new RuntimeException("Coffee machine broke!");
            }
            Log.infof("Served a flakey coffee after %d ms", System.currentTimeMillis() - start);
            return new StructuredBeverage("Flakey " + response.name());
        } catch (RuntimeException e) {
            if (Cancellation.isCancellation(e)) {
                Log.infof("Cancelled after %d ms, stopped brewing", System.currentTimeMillis() - start);
            }
            throw e;
        }
    }
}
