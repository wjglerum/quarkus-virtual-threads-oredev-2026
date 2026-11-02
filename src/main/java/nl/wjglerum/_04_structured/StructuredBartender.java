package nl.wjglerum._04_structured;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.wjglerum.client.CoffeeMachineClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class StructuredBartender {

    @Inject
    @RestClient
    CoffeeMachineClient coffeeMachine;

    public StructuredBeverage get() {
        Log.info("Warming up the structured coffee machine");
        var start = System.currentTimeMillis();
        try {
            var response = coffeeMachine.brew();
            return new StructuredBeverage("Structured " + response.name());
        } catch (RuntimeException e) {
            if (Cancellation.isCancellation(e)) {
                Log.infof("Cancelled after %d ms, stopped brewing", System.currentTimeMillis() - start);
            }
            throw e;
        }
    }

    public StructuredBeverage race() {
        Log.info("Racing to brew a structured coffee");
        var start = System.currentTimeMillis();
        try {
            var response = coffeeMachine.brewRandom();
            Log.infof("Served a structured coffee after %d ms", System.currentTimeMillis() - start);
            return new StructuredBeverage("Structured " + response.name());
        } catch (RuntimeException e) {
            if (Cancellation.isCancellation(e)) {
                Log.infof("Cancelled after %d ms, stopped brewing", System.currentTimeMillis() - start);
            }
            throw e;
        }
    }
}
