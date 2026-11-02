package de.minibits.client;

import de.minibits.core.ShapePreset;
import de.minibits.item.BitItem;
import de.minibits.item.MiniBlockItem;
import de.minibits.net.SettingsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;

public class MiniBitsClient implements ClientModInitializer {
    private static KeyMapping MENU, NEXT, ROTATE;
    private static int preset = 0;
    private static int rotation = 0;

    private static KeyMapping key(String name, int code) {
        KeyMapping k = new KeyMapping(name, code, KeyMapping.Category.MISC);
        KeyMappingHelper.registerKeyMapping(k);
        return k;
    }

    @Override
    public void onInitializeClient() {
        // Registriert den eigenen Blockstate-Modelltyp "minibits:mini_bits"
        MiniBitsModel.register();
        System.out.println("[minibits-build] Version 3 (Presets)");

        MENU = key("key.minibits.menu", 342);
        NEXT = key("key.minibits.next", 86);
        ROTATE = key("key.minibits.rotate", 82);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sendSettings());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            boolean changed = false;
            while (NEXT.consumeClick()) { preset = (preset + 1) % ShapePreset.count(); changed = true; }
            while (ROTATE.consumeClick()) { rotation = (rotation + 1) & 3; changed = true; }
            if (changed) sendSettings();

            Item held = mc.player.getMainHandItem().getItem();
            boolean relevant = held instanceof BitItem || held instanceof MiniBlockItem;
            if (relevant && (MENU.isDown() || changed)) {
            overlay(mc, menu(held instanceof MiniBlockItem), changed);
            }
        });
    }
    private static boolean overlayLogged = false;

    /** Zeigt Text über der Hotbar. Sucht die passende Methode zur Laufzeit, weil sich der Name zwischen Versionen ändert. */
    private static void overlay(Minecraft mc, Component text, boolean changed) {
        try {
            for (var m : mc.gui.getClass().getMethods()) {
                String n = m.getName().toLowerCase();
                if (!overlayLogged && (n.contains("overlay") || n.contains("actionbar"))) {
                    System.out.println("[minibits-api] Gui :: " + m);
                }
                if (!(n.contains("overlay") && n.contains("message"))) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (pt.length >= 1 && pt[0].isAssignableFrom(Component.class)) {
                    if (pt.length == 1) { m.invoke(mc.gui, text); overlayLogged = true; return; }
                    if (pt.length == 2 && pt[1] == boolean.class) { m.invoke(mc.gui, text, false); overlayLogged = true; return; }
                }
            }
        } catch (Throwable t) {
            System.out.println("[minibits-api] overlay-Fehler: " + t);
        }
        overlayLogged = true;
        if (changed) mc.player.sendSystemMessage(text); // Notlösung: Chat
    }
    private static void sendSettings() {
        ClientPlayNetworking.send(new SettingsPayload(preset, rotation));
    }

    private static Component menu(boolean wholeBlock) {
        MutableComponent c = Component.empty();
        if (!wholeBlock) {
            int n = ShapePreset.count();
            String prev = ShapePreset.ALL[(preset + n - 1) % n].name();
            String next = ShapePreset.ALL[(preset + 1) % n].name();
            c.append(Component.literal("< " + prev + "    ").withStyle(ChatFormatting.GRAY));
            c.append(Component.literal("[ " + ShapePreset.ALL[preset].name() + " ]").withStyle(ChatFormatting.YELLOW));
            c.append(Component.literal("    " + next + " >").withStyle(ChatFormatting.GRAY));
            c.append(Component.literal("   |   ").withStyle(ChatFormatting.DARK_GRAY));
        }
        c.append(Component.literal("Drehung " + (rotation * 90) + "°").withStyle(ChatFormatting.AQUA));
        return c;
    }
}
