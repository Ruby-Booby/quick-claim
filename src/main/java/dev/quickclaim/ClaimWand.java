package dev.quickclaim;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class ClaimWand extends Item {

    public ClaimWand(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        if (c.getPlayer() instanceof ServerPlayer p)
            ClaimService.corner(p, c.getClickedPos(), p.isShiftKeyDown());
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }
}
