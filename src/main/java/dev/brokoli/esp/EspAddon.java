package dev.brokoli.esp;

import dev.boze.api.BozeInstance;
import dev.boze.api.addon.Addon;
import dev.brokoli.esp.module.ImageEspModule;
import net.fabricmc.api.ClientModInitializer;

public class EspAddon extends Addon implements ClientModInitializer {

    public EspAddon() {
        // Addon(id, name, description, version)
        super("image-esp", "ImageESP", "Renders PNG images above entities as ESP overlay", "1.0.0");
    }

    @Override
    public boolean initialize() {
        this.modules.add(new ImageEspModule());
        return true;
    }

    @Override
    public void onInitializeClient() {
        BozeInstance.INSTANCE.registerAddon(this);
    }
}
