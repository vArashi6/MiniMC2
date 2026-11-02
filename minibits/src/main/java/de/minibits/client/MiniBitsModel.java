package de.minibits.client;

/*
 * ACHTUNG: Diese Datei ist der unsicherste Teil. Sie nutzt die Fabric-Renderer-API für 26.x
 * (FabricBlockStateModel#emitQuads, CustomUnbakedBlockStateModel). Die genauen Signaturen konnte ich
 * hier nicht kompilieren. Bei Compile-Fehlern: Fehlermeldung an Claude schicken, wir passen es an.
 *
 * Idee: Pro Chunk-Build liest emitQuads() den Schnappschuss der Block-Entity, meshed die 16^3 Bits
 * mit dem Greedy-Mesher und gibt pro Rechteck ein Quad aus. Textur = Partikeltextur des Materials.
 */

import com.mojang.serialization.MapCodec;
import de.minibits.MiniBits;
import de.minibits.block.MiniBitsBlockEntity;
import de.minibits.core.GreedyMesher;
import net.fabricmc.fabric.api.client.model.loading.v1.CustomUnbakedBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

public final class MiniBitsModel {
    private static final Direction[] DIRS = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    public static void register() {
        CustomUnbakedBlockStateModel.register(MiniBits.id("mini_bits"), Unbaked.CODEC);
    }

    /** Wird aus blockstates/mini_block.json mit {"type":"minibits:mini_bits"} geladen. */
    public record Unbaked() implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

        @Override public MapCodec<? extends CustomUnbakedBlockStateModel> codec() { return CODEC; }

        @Override
        public BlockStateModel bake(net.minecraft.client.resources.model.ModelBaker baker) {
            return new Baked();
        }

        @Override
        public void resolveDependencies(net.minecraft.client.resources.model.ResolvableModel.Resolver resolver) {}
    }

    public static final class Baked implements BlockStateModel {
    private static final java.util.Set<BlockState> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();
        @Override
        public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                              RandomSource random, Predicate<Direction> cullTest) {
            if (!(level.getBlockEntityRenderData(pos) instanceof MiniBitsBlockEntity.Snapshot snap)) return;
            var models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();

            for (GreedyMesher.Face f : GreedyMesher.mesh(snap.grid())) {
                Direction dir = DIRS[f.dir()];
                BlockState mat = f.material() <= snap.palette().size() ? snap.palette().get(f.material() - 1) : null;
                if (mat == null) continue;
                var sprite = models.get(mat).particleMaterial();
                if (LOGGED.add(mat)) System.out.println("[minibits-render] " + mat + " -> " + sprite);

                float p = f.plane() / 16f;
                float a0 = f.a0() / 16f, a1 = (f.a0() + f.w()) / 16f;
                float b0 = f.b0() / 16f, b1 = (f.b0() + f.h()) / 16f;
                float[][] v = corners(f.dir(), p, a0, a1, b0, b1);
                for (int i = 0; i < 4; i++) emitter.pos(i, v[i][0], v[i][1], v[i][2]);
                emitter.nominalFace(dir);
                for (int i = 0; i < 4; i++) {
    float fu, fv;
    switch (f.dir()) {
        case 0, 1 -> { fu = v[i][0]; fv = v[i][2]; }
        case 2, 3 -> { fu = v[i][0]; fv = 1f - v[i][1]; }
        default -> { fu = v[i][2]; fv = 1f - v[i][1]; }
    }
    emitter.uv(i, fu, fv);
}
emitter.materialBake(sprite, MutableQuadView.BAKE_NORMALIZED);
                if (isOnBoundary(f)) emitter.cullFace(dir);
                emitter.color(-1, -1, -1, -1);
                emitter.emit();
            }
        }

        private static boolean isOnBoundary(GreedyMesher.Face f) {
            return (f.dir() % 2 == 0) ? f.plane() == 0 : f.plane() == 16;
        }

        /** Eckpunkte gegen den Uhrzeigersinn von außen gesehen. */
        private static float[][] corners(int dir, float p, float a0, float a1, float b0, float b1) {
            return switch (dir) {
                case GreedyMesher.DOWN  -> new float[][] {{a0, p, b1}, {a0, p, b0}, {a1, p, b0}, {a1, p, b1}};
                case GreedyMesher.UP    -> new float[][] {{a0, p, b0}, {a0, p, b1}, {a1, p, b1}, {a1, p, b0}};
                case GreedyMesher.NORTH -> new float[][] {{a1, b1, p}, {a1, b0, p}, {a0, b0, p}, {a0, b1, p}};
                case GreedyMesher.SOUTH -> new float[][] {{a0, b1, p}, {a0, b0, p}, {a1, b0, p}, {a1, b1, p}};
                case GreedyMesher.WEST  -> new float[][] {{p, b1, a0}, {p, b0, a0}, {p, b0, a1}, {p, b1, a1}};
                default                 -> new float[][] {{p, b1, a1}, {p, b0, a1}, {p, b0, a0}, {p, b1, a0}};
            };
        }

        @Override public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial() {
            return Minecraft.getInstance().getModelManager().getBlockStateModelSet()
                .get(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()).particleMaterial();
        }

        @Override public int materialFlags() { return 0; }

        @Override
        public void collectParts(RandomSource random, java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> out) {
            // Vanilla-Pfad: ohne Level-Kontext keine Geometrie
        }
    }
}
