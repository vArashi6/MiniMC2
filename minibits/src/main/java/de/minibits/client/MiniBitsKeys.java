package de.minibits.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.minibits.net.CommandPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/** Tasten für Rückgängig (Standard Z) und Wiederholen (Standard Y). Änderbar unter Optionen > Steuerung. */
public final class MiniBitsKeys {
    private static KeyMapping UNDO, REDO;

    public static void register() {
        UNDO = make("key.minibits.undo", InputConstants.KEY_Z);
        REDO = make("key.minibits.redo", InputConstants.KEY_Y);
        System.out.println("[minibits-input] Tasten registriert: Rückgängig=" + UNDO.getTranslatedKeyMessage().getString()
            + " Wiederholen=" + REDO.getTranslatedKeyMessage().getString());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            while (UNDO.consumeClick()) {
                System.out.println("[minibits-input] Rückgängig gedrückt");
                ClientPlayNetworking.send(new CommandPayload(4));
            }
            while (REDO.consumeClick()) {
                System.out.println("[minibits-input] Wiederholen gedrückt");
                ClientPlayNetworking.send(new CommandPayload(5));
            }
        });
    }

    private static KeyMapping make(String name, int code) {
        // Der Name der Tasten-Art hat sich zwischen Versionen geändert: zur Laufzeit suchen und ins Log schreiben
        InputConstants.Type type = null;
        for (InputConstants.Type t : InputConstants.Type.values()) {
            System.out.println("[minibits-input] Tasten-Art: " + t.name());
            if (type == null && t.name().contains("KEY")) type = t;
        }
        KeyMapping k = type != null
            ? new KeyMapping(name, type, code, KeyMapping.Category.MISC)
            : new KeyMapping(name, code, KeyMapping.Category.MISC);
        KeyMappingHelper.registerKeyMapping(k);
        return k;
    }
}
