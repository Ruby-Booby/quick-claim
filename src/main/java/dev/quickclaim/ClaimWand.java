package dev.quickclaim;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class ClaimWand extends Item {

    public ClaimWand(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            ClaimService.corner(player, context.getClickedPos(), player.isShiftKeyDown());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
