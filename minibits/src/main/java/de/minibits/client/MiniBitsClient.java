package de.minibits.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.resources.model.sprite.Material;

public class MiniBitsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Registriert den eigenen Blockstate-Modelltyp "minibits:mini_bits"
        MiniBitsModel.register();
        logApi();
    }

    /** Diagnose: schreibt die verfügbaren Methoden der Fabric-Renderer-API ins Log (Zeilen mit [minibits-api]). */
    private static void logApi() {
        try {
            for (Class<?> c : new Class<?>[] {QuadEmitter.class, MutableQuadView.class, Material.Baked.class}) {
                for (var m : c.getMethods()) {
                    System.out.println("[minibits-api] " + c.getSimpleName() + " :: " + m);
                }
            }
        } catch (Throwable t) {
            System.out.println("[minibits-api] Fehler: " + t);
        }
    }
}
