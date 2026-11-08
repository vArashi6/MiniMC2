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
        @Override
        public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                              RandomSource random, Predicate<Direction> cullTest) {
            if (!(level.getBlockEntityRenderData(pos) instanceof MiniBitsBlockEntity.Snapshot snap)) return;
            var models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();

            // Licht: das hellste Licht der Umgebung als Mindestwert, damit Flächen im Block nicht dunkler sind als nötig
            int sky = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
            int blk = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
            for (Direction nd : DIRS) {
                BlockPos np = pos.relative(nd);
                sky = Math.max(sky, level.getBrightness(net.minecraft.world.level.LightLayer.SKY, np));
                blk = Math.max(blk, level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, np));
            }
            int packedLight = (blk << 4) | (sky << 20);

            // Angrenzende Mini-Blöcke: Flächen über die Blockgrenze hinweg weglassen, wenn sie verdeckt sind
            MiniBitsBlockEntity.Snapshot[] neighbors = new MiniBitsBlockEntity.Snapshot[6];
            for (int i = 0; i < 6; i++) {
                if (level.getBlockEntityRenderData(pos.relative(DIRS[i])) instanceof MiniBitsBlockEntity.Snapshot ns) {
                    neighbors[i] = ns;
                }
            }

            // Pro Material einmal nachschlagen statt pro Fläche
            int pn = snap.palette().size() + 1;
            net.minecraft.client.resources.model.sprite.Material.Baked[] sprites = new net.minecraft.client.resources.model.sprite.Material.Baked[pn];
            boolean[] glowing = new boolean[pn];
            boolean[] doubleSided = new boolean[pn];
            boolean[] water = new boolean[pn];
            for (int i = 1; i < pn; i++) {
                BlockState m = snap.palette().get(i - 1);
                sprites[i] = models.get(m).particleMaterial();
                glowing[i] = m.getLightEmission() > 0;
                doubleSided[i] = !m.canOcclude(); // Gitter, Blätter, Glas: auch von innen/hinten sichtbar
                water[i] = m.is(net.minecraft.world.level.block.Blocks.WATER);
            }

            for (GreedyMesher.Face f : snap.faces(neighbors)) {
                Direction dir = DIRS[f.dir()];
                int mi = f.material();
                if (mi <= 0 || mi >= pn) continue;

                float p = f.plane() / 16f;
                float a0 = f.a0() / 16f, a1 = (f.a0() + f.w()) / 16f;
                float b0 = f.b0() / 16f, b1 = (f.b0() + f.h()) / 16f;
                float[][] v = corners(f.dir(), p, a0, a1, b0, b1);
                Direction cull = isOnBoundary(f) ? dir : null; // nur am Blockrand nach außen culling erlauben

                emitQuad(emitter, v, f.dir(), false, dir, cull, sprites[mi], glowing[mi], packedLight, water[mi]);
                if (doubleSided[mi]) {
                    // Gegenseite: Eckpunkte umgekehrt, damit man die Fläche auch von hinten sieht (wie beim Originalblock)
                    emitQuad(emitter, v, f.dir(), true, dir.getOpposite(), cull, sprites[mi], glowing[mi], packedLight, water[mi]);
                }
            }
        }

        private static void emitQuad(QuadEmitter emitter, float[][] v, int dirIdx, boolean reversed, Direction nominal, Direction cull,
                                     net.minecraft.client.resources.model.sprite.Material.Baked sprite, boolean glow, int packedLight, boolean water) {
            for (int k = 0; k < 4; k++) {
                float[] c = v[reversed ? 3 - k : k];
                emitter.pos(k, c[0], c[1], c[2]);
                float fu, fv;
                switch (dirIdx) {
                    case 0, 1 -> { fu = c[0]; fv = c[2]; }
                    case 2, 3 -> { fu = c[0]; fv = 1f - c[1]; }
                    default -> { fu = c[2]; fv = 1f - c[1]; }
                }
                emitter.uv(k, fu, fv);
            }
            emitter.materialBake(sprite, MutableQuadView.BAKE_NORMALIZED);
            emitter.nominalFace(nominal);
            if (cull != null) emitter.cullFace(cull);
            emitter.ambientOcclusion(net.fabricmc.fabric.api.util.TriState.FALSE);
            emitter.minLightmap(packedLight);
            emitter.emissive(glow);
            if (water) {
                emitter.chunkLayer(net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT);
                int c = 0xFF3F76E4; // Standard-Wasserfarbe
                emitter.color(c, c, c, c);
            } else {
                emitter.color(-1, -1, -1, -1);
            }
            emitter.emit();
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
