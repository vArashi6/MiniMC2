package de.minibits;

import com.mojang.serialization.Codec;
import de.minibits.block.MiniBitsBlock;
import de.minibits.block.MiniBitsBlockEntity;
import de.minibits.item.BitItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MiniBits implements ModInitializer {
    public static final String MOD_ID = "minibits";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static MiniBitsBlock MINI_BLOCK;
    public static BlockEntityType<MiniBitsBlockEntity> MINI_BLOCK_ENTITY;
    public static BitItem BIT_ITEM;
    public static DataComponentType<BlockState> BIT_STATE;

    @Override
    public void onInitialize() {
        // Datenkomponente: welches Material ein Bit-Item hat
        BIT_STATE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id("bit_state"),
            DataComponentType.<BlockState>builder()
                .persistent(BlockState.CODEC)
                .networkSynchronized(ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY))
                .build());

        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id("mini_block"));
        MINI_BLOCK = Registry.register(BuiltInRegistries.BLOCK, blockKey,
            new MiniBitsBlock(Block.Properties.of().setId(blockKey).noOcclusion().strength(1.0f)));

        MINI_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("mini_block"),
            FabricBlockEntityTypeBuilder.create(MiniBitsBlockEntity::new, MINI_BLOCK).build());

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id("bit"));
        BIT_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
            new BitItem(new Item.Properties().setId(itemKey).stacksTo(64)));

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"),
            FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.minibits.main"))
                .icon(() -> bitStack(Blocks.STONE.defaultBlockState()))
                .displayItems((params, output) -> {
                    // Alle Vollblöcke aus der Block-Registry als Bit-Item anbieten
                    for (Block block : BuiltInRegistries.BLOCK) {
                        BlockState s = block.defaultBlockState();
                        if (block == MINI_BLOCK || !BitItem.isUsable(s)) continue;
                        output.accept(bitStack(s));
                    }
                })
                .build());
    }

    public static ItemStack bitStack(BlockState state) {
        ItemStack stack = new ItemStack(BIT_ITEM);
        stack.set(BIT_STATE, state);
        return stack;
    }
}
