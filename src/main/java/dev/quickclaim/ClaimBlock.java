package dev.quickclaim;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

import java.util.UUID;

public final class ClaimBlock extends BaseEntityBlock {

    private static final VoxelShape SHAPE =
            Shapes.or(
                    Block.box(1, 0, 1, 15, 2, 15),
                    Block.box(4, 2, 4, 12, 13, 12),
                    Block.box(0, 12, 0, 16, 16, 16));

    public ClaimBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition
                        .any()
                        .setValue(
                                HorizontalDirectionalBlock.FACING,
                                net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState>
                    builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(
            net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(
                        HorizontalDirectionalBlock.FACING,
                        context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(
                HorizontalDirectionalBlock.FACING,
                rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(
            BlockState state,
            net.minecraft.world.level.BlockGetter level,
            BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (!Management.canUse(serverPlayer, pos)) return InteractionResult.FAIL;
        Management.open(serverPlayer, pos);
        return InteractionResult.CONSUME;
    }

    static void openStorage(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        if (!(level.getBlockEntity(pos) instanceof Entity entity)) return;
        var data = ClaimData.get(player.server);
        var context = data.at(ClaimService.dimension(player), pos);
        var faction = context != null && context.faction ? data.factions.get(context.owner) : null;
        if (faction == null
                || !Management.canUseSilently(player, pos)
                || (entity.faction != null && !entity.faction.equals(faction.id))
                || (!faction.manages(player.getUUID()) && !ClaimService.bypass(player))) {
            ClaimService.tell(
                    player,
                    "Place the lectern inside your faction's claim. Only its five managers can"
                            + " access upkeep.");
            return;
        }
        if (entity.faction == null) {
            var claim = data.at(ClaimService.dimension(player), pos);
            if (claim == null || !claim.faction || !claim.owner.equals(faction.id)) {
                ClaimService.tell(
                        player,
                        "Claim this block's location for your faction before binding its payment"
                                + " storage.");
                return;
            }
            if (faction.treasuryPos != null) {
                ClaimService.tell(
                        player,
                        "Payment storage is bound to "
                                + faction.treasuryPos.toShortString()
                                + ". Remove that block before moving it.");
                return;
            }
            entity.faction = faction.id;
            entity.setChanged();
            faction.treasuryPos = pos.immutable();
            faction.treasuryDimension = ClaimService.dimension(player);
            data.setDirty();
        }
        if (!pos.equals(faction.treasuryPos)
                || !ClaimService.dimension(player).equals(faction.treasuryDimension)) return;
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, who) ->
                                new ChestMenu(
                                        MenuType.GENERIC_9x3, id, inventory, faction.treasury, 3) {

                                    @Override
                                    public boolean stillValid(Player who) {
                                        return who instanceof ServerPlayer p
                                                && p.serverLevel() == level
                                                && Management.canUseSilently(p, pos)
                                                && level.getBlockEntity(pos) == entity
                                                && pos.equals(faction.treasuryPos)
                                                && data.at(ClaimService.dimension(p), pos) != null
                                                && data.at(ClaimService.dimension(p), pos).faction
                                                && data.at(ClaimService.dimension(p), pos)
                                                        .owner
                                                        .equals(faction.id)
                                                && (faction.manages(who.getUUID())
                                                        || ClaimService.bypass(p));
                                    }
                                },
                        Component.literal("Faction vault")));
    }

    static boolean vaultAllowed(ServerPlayer player, ClaimData.Claim claim) {
        var data = ClaimData.get(player.server);
        return ClaimService.owns(data, claim, player)
                || (!claim.serverClaim
                        && !claim.faction
                        && data.trusted(claim.owner, player.getUUID()));
    }

    static void openVault(ServerPlayer player, BlockPos pos) {
        var data = ClaimData.get(player.server);
        var claim = data.at(ClaimService.dimension(player), pos);
        if (claim == null
                || !Management.canUseSilently(player, pos)
                || !vaultAllowed(player, claim)) {
            ClaimService.tell(
                    player,
                    "Only this claim's trusted players or faction managers can open its vault.");
            return;
        }
        if (claim.faction) {
            openStorage(player, pos);
            return;
        }
        var inventory = data.vault(claim.owner);
        var level = player.serverLevel();
        var entity = level.getBlockEntity(pos);
        player.openMenu(
                new SimpleMenuProvider(
                        (id, slots, who) ->
                                new ChestMenu(MenuType.GENERIC_9x3, id, slots, inventory, 3) {
                                    @Override
                                    public boolean stillValid(Player who) {
                                        var current = data.at(ClaimService.dimension(player), pos);
                                        return who instanceof ServerPlayer visitor
                                                && current != null
                                                && current.owner.equals(claim.owner)
                                                && current.faction == claim.faction
                                                && current.serverClaim == claim.serverClaim
                                                && visitor.serverLevel() == level
                                                && level.getBlockEntity(pos) == entity
                                                && Management.canUseSilently(visitor, pos)
                                                && vaultAllowed(visitor, current);
                                    }
                                },
                        Component.literal(claim.name + " - Vault")));
    }

    @Override
    public void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())
                && !level.isClientSide
                && level.getBlockEntity(pos) instanceof Entity entity
                && entity.faction != null) {
            var data = ClaimData.get(level.getServer());
            var faction = data.factions.get(entity.faction);
            if (faction != null
                    && pos.equals(faction.treasuryPos)
                    && level.dimension().location().toString().equals(faction.treasuryDimension)) {
                faction.treasuryPos = null;
                faction.treasuryDimension = "";
                data.setDirty();
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    public static final class Entity extends BlockEntity {

        UUID faction;

        public Entity(BlockPos pos, BlockState state) {
            super(QuickClaim.CLAIM_BLOCK_ENTITY.get(), pos, state);
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            if (faction != null) tag.putUUID("Faction", faction);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            faction = tag.hasUUID("Faction") ? tag.getUUID("Faction") : null;
        }
    }
}
