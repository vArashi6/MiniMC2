package de.minibits.client;

import net.fabricmc.api.ClientModInitializer;

public class MiniBitsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Registriert den eigenen Blockstate-Modelltyp "minibits:mini_bits"
        MiniBitsModel.register();
    }
}
