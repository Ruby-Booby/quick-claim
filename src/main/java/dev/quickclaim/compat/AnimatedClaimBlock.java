package dev.quickclaim.compat;

import dev.quickclaim.ClaimBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class AnimatedClaimBlock extends ClaimBlock.Entity implements GeoBlockEntity {
    private static final RawAnimation OPEN_BOOK =
            RawAnimation.begin().thenPlayAndHold("book_open");
    private static final RawAnimation CLOSE_BOOK =
            RawAnimation.begin().thenPlayAndHold("book_close");
    private static final RawAnimation OPEN_DRAWER =
            RawAnimation.begin().thenPlayAndHold("drawer_open");
    private static final RawAnimation CLOSE_DRAWER =
            RawAnimation.begin().thenPlayAndHold("drawer_close");
    private static final RawAnimation BOOK_REST = RawAnimation.begin().thenLoop("book_closed");
    private static final RawAnimation DRAWER_REST = RawAnimation.begin().thenLoop("drawer_closed");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean bookOpened;
    private boolean drawerOpened;

    public AnimatedClaimBlock(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(
                new AnimationController<>(
                        this,
                        "book",
                        4,
                        state -> {
                            boolean nearby =
                                    level != null
                                            && level.getNearestPlayer(
                                                            worldPosition.getX() + 0.5,
                                                            worldPosition.getY() + 0.5,
                                                            worldPosition.getZ() + 0.5,
                                                            3,
                                                            player -> !player.isSpectator())
                                                    != null;
                            if (nearby) bookOpened = true;
                            return state.setAndContinue(
                                    nearby ? OPEN_BOOK : bookOpened ? CLOSE_BOOK : BOOK_REST);
                        }));
        controllers.add(
                new AnimationController<>(
                        this,
                        "drawer",
                        2,
                        state -> {
                            if (isDrawerOpen()) drawerOpened = true;
                            return state.setAndContinue(
                                    isDrawerOpen()
                                            ? OPEN_DRAWER
                                            : drawerOpened ? CLOSE_DRAWER : DRAWER_REST);
                        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
