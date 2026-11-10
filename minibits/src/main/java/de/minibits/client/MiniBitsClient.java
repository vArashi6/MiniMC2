package de.minibits.client;

import de.minibits.item.BitItem;
import de.minibits.net.CommandPayload;
import de.minibits.net.StatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;

public class MiniBitsClient implements ClientModInitializer {
    private static long lastRemoveMs = 0;
    private static long lastRotateMs = 0;
    private static boolean prevWasBit = true;

    @Override
    public void onInitializeClient() {
        // Registriert den eigenen Blockstate-Modelltyp "minibits:mini_bits"
        MiniBitsModel.register();
        System.out.println("[minibits-build] Version 13 (Umriss, Shader-Wasser)");

        MiniBitsKeys.register();
        MiniBitsOutline.register();

        // In Wasser-/Lava-Bits: verlangsamen und Fallschaden vermeiden (die Bewegung des Spielers wird im Client berechnet)
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.level == null) return;
            int contact = de.minibits.MiniBits.fluidContact(mc.level, mc.player.getBoundingBox());
            if (contact == 0) return;
            mc.player.resetFallDistance();
            if ((contact & 2) != 0) {
                mc.player.makeStuckInBlock(net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(), new net.minecraft.world.phys.Vec3(0.5, 0.5, 0.5));
            } else {
                mc.player.makeStuckInBlock(net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), new net.minecraft.world.phys.Vec3(0.8, 0.8, 0.8));
            }
        });

        // Von der bloßen Hand (oder einem anderen Item) auf ein Bit wechseln: wieder 1x1x1 ohne Drehung
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) { prevWasBit = true; return; }
            boolean nowBit = mc.player.getMainHandItem().getItem() instanceof BitItem;
            if (nowBit && !prevWasBit) ClientPlayNetworking.send(new CommandPayload(3));
            prevWasBit = nowBit;
        });

        // Einblendung der Formen
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, de.minibits.MiniBits.id("shape_hud"), MiniBitsHud::render);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> MiniBitsHud.reset());
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE,
            (payload, context) -> MiniBitsHud.setState(payload.preset(), payload.rotation()));

        // Inventar-Symbole
        net.fabricmc.fabric.api.client.rendering.v1.ExtractItemDecorationsCallback.EVENT.register((graphics, font, stack, x, y) -> {
            if (stack.getItem() instanceof BitItem) {
                // Bit: links der Originalblock, rechts daneben klein die Mini-Version
                var material = stack.get(de.minibits.MiniBits.BIT_STATE);
                if (material == null) return;
                net.minecraft.world.item.ItemStack blockStack = new net.minecraft.world.item.ItemStack(de.minibits.MiniBits.iconItemOf(material));
                if (blockStack.isEmpty()) return;
                graphics.fill(x, y, x + 16, y + 16, 0xFF2B2B2B);
                var pose = graphics.pose();
                pose.pushMatrix();
                pose.translate(x + 0.5f, y + 2.5f);
                pose.scale(0.62f, 0.62f);
                graphics.item(blockStack, 0, 0);
                pose.popMatrix();
                pose.pushMatrix();
                pose.translate(x + 10.5f, y + 7.5f);
                pose.scale(0.34f, 0.34f);
                graphics.item(blockStack, 0, 0);
                pose.popMatrix();
                return;
            }
            // Kopierter Mini-Block: Vorschaubild mit drei Seiten
            if (!(stack.getItem() instanceof de.minibits.item.MiniBlockItem)) return;
            var icon = stack.get(de.minibits.MiniBits.MINI_ICON);
            if (icon == null || icon.size() < 256) return;
            graphics.fill(x, y, x + 16, y + 16, 0xFF3A3A3A);
            for (int i = 0; i < 256; i++) {
                int c = icon.get(i);
                if ((c >>> 24) == 0) continue;
                int px = i % 16, py = i / 16;
                graphics.fill(x + px, y + py, x + px + 1, y + py + 1, c);
            }
        });

        // Linksklick auf Mini-Block: nicht vanilla abbauen, sondern nur das Bit unter dem Fadenkreuz entfernen
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide()) return net.minecraft.world.InteractionResult.PASS;
            if (!level.getBlockState(pos).is(de.minibits.MiniBits.MINI_BLOCK)) return net.minecraft.world.InteractionResult.PASS;
            Minecraft mc = Minecraft.getInstance();
            long now = System.currentTimeMillis();
            if (now - lastRemoveMs < 250) return net.minecraft.world.InteractionResult.SUCCESS; // Tempo begrenzen
            lastRemoveMs = now;
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
            long now = System.currentTimeMillis();
            if (now - lastRotateMs >= 350) { // kleine Pause, damit gehaltenes Linksklicken nicht zu schnell dreht
                lastRotateMs = now;
                ClientPlayNetworking.send(new CommandPayload(2));
            }
            return true;
        });
    }

    private static int bit(double v) {
        return Math.max(0, Math.min(15, (int) Math.floor(v)));
    }
}
