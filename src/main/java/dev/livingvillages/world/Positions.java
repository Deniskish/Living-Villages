package dev.livingvillages.world;
import dev.livingvillages.domain.Model.Pos;
import net.minecraft.core.BlockPos;
public final class Positions {
    private Positions() {}
    public static Pos of(BlockPos p) { return new Pos(p.getX(),p.getY(),p.getZ()); }
    public static BlockPos block(Pos p) { return new BlockPos(p.x(),p.y(),p.z()); }
}
