package de.minibits;

import de.minibits.block.MiniBitsBlock;
import de.minibits.block.MiniBitsBlockEntity;
import de.minibits.core.ShapePreset;
import de.minibits.item.BitItem;
import de.minibits.item.MiniBlockItem;
import de.minibits.net.CommandPayload;
import de.minibits.net.StatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MiniBits implements ModInitializer {
    public static final String MOD_ID = "minibits";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static MiniBitsBlock MINI_BLOCK;
    public static BlockEntityType<MiniBitsBlockEntity> MINI_BLOCK_ENTITY;
    public static BitItem BIT_ITEM;
    public static MiniBlockItem MINI_BLOCK_ITEM;
    public static DataComponentType<BlockState> BIT_STATE;
    public static DataComponentType<MiniData> MINI_DATA;
    public static DataComponentType<java.util.List<Integer>> MINI_ICON;

    /** Pro Spieler (Server): {Preset, Drehung}. Wird vom Client per Paket gesetzt. */
    private static final Map<UUID, int[]> SETTINGS = new ConcurrentHashMap<>();

    public static int[] settingsOf(Player player) {
        if (player == null) return new int[] {0, 0};
        return SETTINGS.getOrDefault(player.getUUID(), new int[] {0, 0});
    }

    /** Viertelumdrehungen aus der Blickrichtung: Norden = 0, Osten = 1, Süden = 2, Westen = 3. */
    public static int facingRotation(Player player) {
        if (player == null) return 0;
        return (player.getDirection().get2DDataValue() + 2) & 3;
    }

    public static void setSettings(Player player, int[] st) {
        SETTINGS.put(player.getUUID(), st);
    }

    /** 0 = nächste Form, 1 = vorherige Form, 2 = um 90° drehen, 3 = zurück auf 1x1x1 ohne Drehung, 4 = rückgängig, 5 = wiederholen. Schickt den neuen Zustand an den Spieler. */
    public static void applyCommand(net.minecraft.server.level.ServerPlayer player, int cmd) {
        if (cmd == 4) { UndoHistory.undo(player); return; }
        if (cmd == 5) { UndoHistory.redo(player); return; }
        int[] st = settingsOf(player).clone();
        int n = ShapePreset.count();
        if (cmd == 0) st[0] = (st[0] + 1) % n;
        else if (cmd == 1) st[0] = (st[0] + n - 1) % n;
        else if (cmd == 2) st[1] = (st[1] + 1) & 3;
        else if (cmd == 3) { st[0] = 0; st[1] = 0; }
        SETTINGS.put(player.getUUID(), st);
        ServerPlayNetworking.send(player, new StatePayload(st[0], st[1]));
    }

    @Override
    public void onInitialize() {
        BIT_STATE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("bit_state"),
            DataComponentType.<BlockState>builder()
                .persistent(BlockState.CODEC)
                .networkSynchronized(ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY))
                .build());

        MINI_DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("mini_data"),
            DataComponentType.<MiniData>builder().persistent(MiniData.CODEC).build());

        // Vorschau-Bild (16x16 Pixel, Vorderansicht) eines kopierten Mini-Blocks, wird an den Client übertragen
        com.mojang.serialization.Codec<java.util.List<Integer>> iconCodec = com.mojang.serialization.Codec.INT.listOf();
        MINI_ICON = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("icon"),
            DataComponentType.<java.util.List<Integer>>builder()
                .persistent(iconCodec)
                .networkSynchronized(ByteBufCodecs.fromCodec(iconCodec))
                .build());

        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id("mini_block"));
        MINI_BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey,
            new MiniBitsBlock(Block.Properties.of().setId(blockKey).noOcclusion().strength(1.0f)
                .lightLevel(state -> state.getValue(MiniBitsBlock.LIGHT))));

        MINI_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("mini_block"),
            FabricBlockEntityTypeBuilder.create(MiniBitsBlockEntity::new, MINI_BLOCK).build());

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id("bit"));
        BIT_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
            new BitItem(new Item.Properties().setId(itemKey).stacksTo(64)));

        ResourceKey<Item> blockItemKey = ResourceKey.create(Registries.ITEM, id("mini_block"));
        MINI_BLOCK_ITEM = Registry.register(BuiltInRegistries.ITEM, blockItemKey,
            new MiniBlockItem(MINI_BLOCK, new Item.Properties().setId(blockItemKey).useBlockDescriptionPrefix().stacksTo(1)));

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"),
            FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.minibits.main"))
                .icon(() -> bitStack(Blocks.STONE.defaultBlockState()))
                .displayItems((params, output) -> {
                    for (Block block : BuiltInRegistries.BLOCK) {
                        BlockState s = block.defaultBlockState();
                        if (block == MINI_BLOCK || block.asItem() == Items.AIR || !BitItem.isUsable(s)) continue;
                        output.accept(bitStack(s));
                    }
                })
                .build());

        // Netzwerk: Client schickt Kommandos (Form wechseln / drehen), Server antwortet mit dem aktuellen Zustand
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CommandPayload.TYPE, CommandPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CommandPayload.TYPE,
            (payload, context) -> applyCommand(context.player(), payload.cmd()));

        // Vanilla-Abbau: den ganzen Block nur zerstören lassen, wenn keine Bits mehr drin sind.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
            !(blockEntity instanceof MiniBitsBlockEntity mini) || mini.grid().isEmpty());

        // Einzelne Bits entfernen: der Client schickt die Position des Treffers.
        PayloadTypeRegistry.serverboundPlay().register(de.minibits.net.RemovePayload.TYPE, de.minibits.net.RemovePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(de.minibits.net.RemovePayload.TYPE, (payload, context) -> {
            var player = context.player();
            var level = player.level();
            net.minecraft.core.BlockPos pos = payload.pos();
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return;
            if (!(level.getBlockEntity(pos) instanceof MiniBitsBlockEntity mini)) return;
            int[] st = settingsOf(player);
            int rot = (st[1] + facingRotation(player)) & 3;
            int[] box = payload.all() ? new int[] {0, 0, 0, 15, 15, 15}
                : ShapePreset.box(st[0], rot, clampBit(payload.bx()), clampBit(payload.by()), clampBit(payload.bz()));
            BlockState hitMaterial = mini.stateAt(mini.grid().get(clampBit(payload.bx()), clampBit(payload.by()), clampBit(payload.bz())));

            java.util.List<UndoHistory.Cell> log = new java.util.ArrayList<>();
            for (int x = box[0]; x <= box[3]; x++)
                for (int y = box[1]; y <= box[4]; y++)
                    for (int z = box[2]; z <= box[5]; z++) {
                        short v = mini.grid().get(x, y, z);
                        if (v == 0) continue;
                        log.add(new UndoHistory.Cell(pos, UndoHistory.index(x, y, z), mini.stateAt(v), null));
                        mini.grid().set(x, y, z, (short) 0);
                    }
            UndoHistory.push(player, log);
            if (hitMaterial != null) {
                net.minecraft.world.level.block.SoundType sound = hitMaterial.getSoundType();
                level.playSound(null, pos, sound.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
            }
            if (mini.grid().isEmpty()) level.removeBlock(pos, false);
            else mini.changed();
        });
    }

    private static int clampBit(double v) {
        return Math.max(0, Math.min(15, (int) Math.floor(v)));
    }

    public static ItemStack bitStack(BlockState state) {
        ItemStack stack = new ItemStack(BIT_ITEM);
        stack.set(BIT_STATE, state);
        // Inventar-Symbol: das Item-Modell des jeweiligen Blocks
        stack.set(DataComponents.ITEM_MODEL, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        return stack;
    }
}
