package de.minibits;

import de.minibits.block.MiniBitsBlock;
import de.minibits.block.MiniBitsBlockEntity;
import de.minibits.core.ShapePreset;
import de.minibits.item.BitItem;
import de.minibits.item.MiniBlockItem;
import de.minibits.net.SettingsPayload;
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

    /** Pro Spieler (Server): {Preset, Drehung}. Wird vom Client per Paket gesetzt. */
    private static final Map<UUID, int[]> SETTINGS = new ConcurrentHashMap<>();

    public static int[] settingsOf(Player player) {
        if (player == null) return new int[] {0, 0};
        return SETTINGS.getOrDefault(player.getUUID(), new int[] {0, 0});
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

        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id("mini_block"));
        MINI_BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey,
            new MiniBitsBlock(Block.Properties.of().setId(blockKey).noOcclusion().strength(1.0f)));

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

        // Netzwerk: Client sendet gewählte Form und Drehung
        PayloadTypeRegistry.serverboundPlay().register(SettingsPayload.TYPE, SettingsPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SettingsPayload.TYPE, (payload, context) -> {
            int preset = Math.floorMod(payload.preset(), ShapePreset.count());
            int rot = Math.floorMod(payload.rotation(), 4);
            SETTINGS.put(context.player().getUUID(), new int[] {preset, rot});
        });

        // Abbauen: nur Bits entfernen (Form = gewähltes Preset). Block verschwindet erst, wenn nichts mehr übrig ist.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (level.isClientSide() || !(blockEntity instanceof MiniBitsBlockEntity mini)) return true;
            if (mini.grid().isEmpty()) return true;
            HitResult hit = player.pick(6.0, 1.0f, false);
            if (!(hit instanceof BlockHitResult bhr) || !bhr.getBlockPos().equals(pos)) return false;

            double eps = 1.0 / 64.0;
            Vec3 p = bhr.getLocation();
            int bx = clampBit((p.x - pos.getX() - bhr.getDirection().getStepX() * eps) * 16);
            int by = clampBit((p.y - pos.getY() - bhr.getDirection().getStepY() * eps) * 16);
            int bz = clampBit((p.z - pos.getZ() - bhr.getDirection().getStepZ() * eps) * 16);

            int[] st = settingsOf(player);
            int[] box = ShapePreset.box(st[0], 0, bx, by, bz);
            int removed = mini.grid().clearBox(box[0], box[1], box[2], box[3], box[4], box[5]);
            if (mini.grid().isEmpty()) return true;
            if (removed > 0) mini.changed();
            return false;
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
