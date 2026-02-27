package dev.brokoli.esp;

import dev.boze.api.addon.AddonModule;
import dev.boze.api.BozeAddon;
import dev.brokoli.esp.module.ImageEspModule;
import net.fabricmc.api.ClientModInitializer;

public class EspAddon implements ClientModInitializer, BozeAddon {

    public static final String MOD_ID = "esp-addon";

    @Override
    public void onInitializeClient() {
        // Fabric entrypoint - Boze registration happens via getModules()
    }

    @Override
    public AddonModule[] getModules() {
        return new AddonModule[]{ new ImageEspModule() };
    }
}
