package nl.wjglerum._03_virtual;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.wjglerum.client.CoffeeMachineClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class VirtualBartender {

    @Inject
    @RestClient
    CoffeeMachineClient coffeeMachine;

    public VirtualBeverage get() {
        Log.info("Warming up the virtual coffee machine");
        var response = coffeeMachine.brew();
        return new VirtualBeverage("Virtual " + response.name());
    }

    public VirtualBeverage race() {
        Log.info("Racing to brew a virtual coffee");
        var response = coffeeMachine.brewRandom();
        Log.info("Served a virtual coffee");
        return new VirtualBeverage("Virtual " + response.name());
    }
}
