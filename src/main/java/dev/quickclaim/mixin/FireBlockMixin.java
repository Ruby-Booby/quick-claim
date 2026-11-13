package dev.quickclaim.mixin;

import dev.quickclaim.ProtectionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/level/block/FireBlock;tryCatchFire(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;ILnet/minecraft/core/Direction;)V",
                            ordinal = 0,
                            remap = false),
            cancellable = true)
    private void stopSpread(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random,
            CallbackInfo callback) {
        if (!ProtectionEvents.fireSpreadAllowed(level, pos)) callback.cancel();
    }

    @Inject(method = "tryCatchFire", at = @At("HEAD"), cancellable = true, remap = false)
    private void protectBlocks(
            Level level,
            BlockPos pos,
            int chance,
            RandomSource random,
            int age,
            Direction face,
            CallbackInfo callback) {
        if (!ProtectionEvents.fireSpreadAllowed(level, pos)) callback.cancel();
    }

    @Inject(
            method =
                    "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"),
            cancellable = true)
    private void protectAir(
            LevelReader level, BlockPos pos, CallbackInfoReturnable<Integer> callback) {
        if (level instanceof Level world && !ProtectionEvents.fireSpreadAllowed(world, pos))
            callback.setReturnValue(0);
    }
}
