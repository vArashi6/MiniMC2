package de.minibits.client;

import de.minibits.item.BitItem;
import de.minibits.net.CommandPayload;
import de.minibits.net.StatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;

public class MiniBitsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Registriert den eigenen Blockstate-Modelltyp "minibits:mini_bits"
        MiniBitsModel.register();
        System.out.println("[minibits-build] Version 6 (Einblendung)");

        // Einblendung der Formen
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, de.minibits.MiniBits.id("shape_hud"), MiniBitsHud::render);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> MiniBitsHud.reset());
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE,
            (payload, context) -> MiniBitsHud.setState(payload.preset(), payload.rotation()));

        // Linksklick auf Mini-Block: nicht vanilla abbauen, sondern nur das Bit unter dem Fadenkreuz entfernen
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide()) return net.minecraft.world.InteractionResult.PASS;
            if (!level.getBlockState(pos).is(de.minibits.MiniBits.MINI_BLOCK)) return net.minecraft.world.InteractionResult.PASS;
            Minecraft mc = Minecraft.getInstance();
            if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr && bhr.getBlockPos().equals(pos)) {
                double eps = 1.0 / 64.0;
                var p = bhr.getLocation();
                var d = bhr.getDirection();
                int bx = bit((p.x - pos.getX() - d.getStepX() * eps) * 16);
                int by = bit((p.y - pos.getY() - d.getStepY() * eps) * 16);
                int bz = bit((p.z - pos.getZ() - d.getStepZ() * eps) * 16);
                ClientPlayNetworking.send(new de.minibits.net.RemovePayload(pos, bx, by, bz, player.isShiftKeyDown()));
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        });

        // Linksklick in die Luft mit Bit in der Hand: um 90° drehen
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (!(player.getMainHandItem().getItem() instanceof BitItem)) return false;
            if (client.hitResult != null && client.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS) return false;
            ClientPlayNetworking.send(new CommandPayload(2));
            return true;
        });
    }

    private static int bit(double v) {
        return Math.max(0, Math.min(15, (int) Math.floor(v)));
    }
}
