package dev.brokoli.esp;

import dev.boze.api.BozeInstance;
import dev.boze.api.addon.Addon;
import dev.boze.api.addon.AddonModule;
import dev.brokoli.esp.module.ImageEspModule;
import net.fabricmc.api.ClientModInitializer;

public class EspAddon extends Addon implements ClientModInitializer {

    public EspAddon() {
        super("ImageESP", "Image ESP Addon", "1.0.0", "Brokoli5191");
    }

    @Override
    public void onInitializeClient() {
        this.modules.add(new ImageEspModule());
        BozeInstance.INSTANCE.registerAddon(this);
    }
}
