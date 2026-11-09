package dev.quickclaim.compat;

import dev.quickclaim.ClaimBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class GeckoCompat {
    private GeckoCompat() {}

    public static ClaimBlock.Entity create(BlockPos pos, BlockState state) {
        return new AnimatedClaimBlock(pos, state);
    }
}
