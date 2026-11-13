package dev.quickclaim;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.*;
import net.minecraftforge.eventbus.api.*;

import java.util.*;

public final class ProtectionEvents {

    private final Map<UUID, UUID> lastClaim = new HashMap<>();

    static ClaimData.Claim claim(Level level, BlockPos pos) {
        return level.isClientSide
                ? null
                : ClaimData.get(level.getServer())
                        .protectedAt(level.dimension().location().toString(), pos);
    }

    static boolean denied(ServerPlayer p, BlockPos pos, String action) {
        return !ClaimService.allowed(p, claim(p.level(), pos), action);
    }

    public static boolean fireSpreadAllowed(Level level, BlockPos pos) {
        var claim = claim(level, pos);
        return claim == null || claim.fireSpread;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void attackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !(event.getTarget() instanceof net.minecraft.world.entity.LivingEntity)
                && denied(player, event.getTarget().blockPosition(), "break"))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void projectile(ProjectileImpactEvent event) {
        var projectile = event.getProjectile();
        if (projectile.level().isClientSide) return;
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && !(hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity)) {
            var claim = claim(projectile.level(), hit.getEntity().blockPosition());
            boolean blocked =
                    projectile.getOwner() instanceof ServerPlayer player
                            ? !ClaimService.allowed(player, claim, "break")
                            : claim != null && !claim.mobGrief;
            if (blocked) {
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
                projectile.discard();
            }
        }
        if (projectile instanceof net.minecraft.world.entity.projectile.ThrownPotion potion
                && projectile.getOwner() instanceof ServerPlayer attacker
                && net.minecraft.world.item.alchemy.PotionUtils.getMobEffects(potion.getItem())
                        .stream()
                        .anyMatch(
                                effect ->
                                        effect.getEffect().getCategory()
                                                == net.minecraft.world.effect.MobEffectCategory
                                                        .HARMFUL)) {
            var impact = event.getRayTraceResult().getLocation();
            boolean blocked = pvpDenied(claim(attacker.level(), attacker.blockPosition()));
            for (var target :
                    projectile
                            .level()
                            .getEntitiesOfClass(
                                    Player.class,
                                    new net.minecraft.world.phys.AABB(impact, impact).inflate(4)))
                blocked |= pvpDenied(claim(target.level(), target.blockPosition()));
            if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                    && hit.getEntity() instanceof Player target)
                blocked |= pvpDenied(claim(target.level(), target.blockPosition()));
            if (blocked) {
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
                projectile.discard();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void tool(BlockEvent.BlockToolModificationEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && denied(player, event.getPos(), "break")) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void fluidBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        boolean fire =
                event.getNewState().getBlock()
                        instanceof net.minecraft.world.level.block.BaseFireBlock;
        boolean blocked =
                fire
                        ? !fireSpreadAllowed(level, event.getPos())
                                || !fireSpreadAllowed(level, event.getLiquidPos())
                        : claim(level, event.getPos()) != null;
        if (blocked) {
            event.setNewState(event.getOriginalState());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void broken(BlockEvent.BreakEvent e) {
        if (e.getPlayer() instanceof ServerPlayer p) {
            if (denied(p, e.getPos(), "break")) e.setCanceled(true);
            else if (e.getState().is(QuickClaim.CLAIM_BLOCK.get())
                    && claim(p.level(), e.getPos()) != null
                    && !Management.canUseSilently(p, e.getPos())) e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void place(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) {
            if (e.getLevel() instanceof Level level) {
                var claim = claim(level, e.getPos());
                if (claim != null && !claim.mobGrief) e.setCanceled(true);
            }
            return;
        }
        if (denied(p, e.getPos(), "place")
                || (CarryCompat.isCarrying(p) && denied(p, e.getPos(), "drop"))) {
            e.setCanceled(true);
            return;
        }
        if (e instanceof BlockEvent.EntityMultiPlaceEvent multi)
            for (var snapshot : multi.getReplacedBlockSnapshots())
                if (denied(p, snapshot.getPos(), "place")) {
                    e.setCanceled(true);
                    return;
                }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent.RightClickBlock e) {
        if (e.getItemStack().is(QuickClaim.WAND.get())) {
            e.setUseBlock(Event.Result.DENY);
            e.setUseItem(Event.Result.ALLOW);
            return;
        }
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (denied(p, e.getPos(), "interact")) {
            e.setCanceled(true);
            return;
        }
        if (CarryCompat.isCarrying(p)
                && (denied(p, p.blockPosition(), "drop")
                        || denied(p, e.getPos(), "drop")
                        || (e.getFace() != null
                                && denied(p, e.getPos().relative(e.getFace()), "drop"))))
            e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void entityInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getEntity() instanceof ServerPlayer p
                && (denied(p, e.getTarget().blockPosition(), "interact")
                        || (CarryCompat.isCarrying(p)
                                && (denied(p, p.blockPosition(), "drop")
                                        || denied(p, e.getTarget().blockPosition(), "drop")))))
            e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void entityInteractAt(PlayerInteractEvent.EntityInteractSpecific e) {
        if (e.getEntity() instanceof ServerPlayer p
                && (denied(p, e.getTarget().blockPosition(), "interact")
                        || (CarryCompat.isCarrying(p)
                                && (denied(p, p.blockPosition(), "drop")
                                        || denied(p, e.getTarget().blockPosition(), "drop")))))
            e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void bucket(FillBucketEvent e) {
        if (e.getEntity() instanceof ServerPlayer p
                && e.getTarget() instanceof net.minecraft.world.phys.BlockHitResult hit
                && (denied(p, hit.getBlockPos(), "break")
                        || denied(p, hit.getBlockPos().relative(hit.getDirection()), "place")))
            e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void toss(ItemTossEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !denied(p, p.blockPosition(), "drop"))
            return;
        ItemStack stack = e.getEntity().getItem().copy();
        CompoundTag data = p.getPersistentData();
        ListTag refunds = data.getList("QuickClaimRefunds", Tag.TAG_COMPOUND);
        refunds.add(stack.save(new CompoundTag()));
        data.put("QuickClaimRefunds", refunds);
        e.setCanceled(true);
        ClaimService.tell(
                p,
                "Item dropping is disabled here. Your item will return when inventory space is"
                        + " available.");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void clonePlayer(PlayerEvent.Clone e) {
        CarryCompat.prepareRespawn(e);
        for (String key : List.of("QuickClaimRefunds", "QuickClaimHome"))
            if (e.getOriginal().getPersistentData().contains(key))
                e.getEntity()
                        .getPersistentData()
                        .put(key, e.getOriginal().getPersistentData().get(key).copy());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void restoreCarried(PlayerEvent.Clone event) {
        CarryCompat.restoreRespawn(event);
    }

    @SubscribeEvent
    public void explosion(ExplosionEvent.Detonate e) {
        if (e.getLevel().isClientSide) return;
        e.getAffectedBlocks().removeIf(pos -> claim(e.getLevel(), pos) != null);
        e.getAffectedEntities()
                .removeIf(entity -> claim(e.getLevel(), entity.blockPosition()) != null);
    }

    @SubscribeEvent
    public void mobGrief(EntityMobGriefingEvent e) {
        if (e.getEntity().level().isClientSide) return;
        var c = claim(e.getEntity().level(), e.getEntity().blockPosition());
        if (c != null && !c.mobGrief) e.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public void trample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        var claim = claim(level, event.getPos());
        if (claim == null) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!ClaimService.allowed(player, claim, "break")) event.setCanceled(true);
        } else if (!claim.mobGrief) event.setCanceled(true);
    }

    @SubscribeEvent
    public void piston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        var source = claim(level, event.getPos());
        var resolver = event.getStructureHelper();
        if (!resolver.resolve()) return;
        var direction =
                event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND
                        ? event.getDirection()
                        : event.getDirection().getOpposite();
        var touched = new ArrayList<BlockPos>();
        touched.add(event.getFaceOffsetPos());
        touched.addAll(resolver.getToDestroy());
        for (var position : resolver.getToPush()) {
            touched.add(position);
            touched.add(position.relative(direction));
        }
        for (var position : touched) {
            var destination = claim(level, position);
            if ((source != null || destination != null)
                    && (source == null
                            || destination == null
                            || !source.group.equals(destination.group))) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public void spawn(MobSpawnEvent.PositionCheck e) {
        if (e.getEntity() instanceof Enemy && e.getLevel() instanceof ServerLevel level) {
            var c = claim(level, e.getEntity().blockPosition());
            if (c != null && !c.hostileSpawns) e.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hurt(LivingAttackEvent e) {
        if (e.getEntity().level().isClientSide) return;
        var target = claim(e.getEntity().level(), e.getEntity().blockPosition());
        if (e.getSource().getEntity() instanceof ServerPlayer attacker) {
            if (e.getEntity() instanceof Player) {
                var origin = claim(attacker.level(), attacker.blockPosition());
                if (pvpDenied(target) || pvpDenied(origin)) e.setCanceled(true);
            } else if (!(e.getEntity() instanceof Enemy)
                    && !ClaimService.allowed(attacker, target, "break")) e.setCanceled(true);
        }
    }

    static boolean pvpDenied(ClaimData.Claim c) {
        return c != null && (c.adminPvp == -1 ? !c.pvp : c.adminPvp == 0);
    }

    @SubscribeEvent
    public void name(PlayerEvent.NameFormat e) {
        if (e.getEntity().level().isClientSide) return;
        var d = ClaimData.get(e.getEntity().getServer());
        var f = d.factionOf(e.getEntity().getUUID());
        if (f != null)
            e.setDisplayname(
                    Component.literal("[" + f.name + "] ")
                            .withStyle(s -> s.withColor(TextColor.parseColor(f.color)))
                            .append(e.getUsername()));
    }

    @SubscribeEvent
    public void tabName(PlayerEvent.TabListNameFormat e) {
        if (e.getEntity().level().isClientSide) return;
        var f = ClaimData.get(e.getEntity().getServer()).factionOf(e.getEntity().getUUID());
        if (f != null)
            e.setDisplayName(
                    Component.literal("[" + f.name + "] ")
                            .withStyle(s -> s.withColor(TextColor.parseColor(f.color)))
                            .append(e.getEntity().getGameProfile().getName()));
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 10 == 0) {
            ListTag refunds = p.getPersistentData().getList("QuickClaimRefunds", Tag.TAG_COMPOUND),
                    remaining = new ListTag();
            for (Tag tag : refunds) {
                ItemStack stack = ItemStack.of((CompoundTag) tag);
                p.getInventory().add(stack);
                if (!stack.isEmpty()) remaining.add(stack.save(new CompoundTag()));
            }
            if (!refunds.isEmpty()) p.getPersistentData().put("QuickClaimRefunds", remaining);
            var c = ClaimData.get(p.server).at(ClaimService.dimension(p), p.blockPosition());
            UUID id = c == null ? null : c.group;
            if (!Objects.equals(lastClaim.get(p.getUUID()), id)) {
                if (c != null) {
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 35, 10));
                    p.connection.send(
                            new ClientboundSetTitleTextPacket(
                                    ClaimService.claimTitle(ClaimData.get(p.server), c)));
                }
                if (id == null) lastClaim.remove(p.getUUID());
                else lastClaim.put(p.getUUID(), id);
            }
        }
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.getServer().getTickCount() % 200 == 0) {
            var d = ClaimData.get(e.getServer());
            long now = System.currentTimeMillis();
            for (var f : d.factions.values()) f.collect(now);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        lastClaim.remove(e.getEntity().getUUID());
        ClaimService.BYPASS.remove(e.getEntity().getUUID());
        ClaimService.SELECTIONS.remove(e.getEntity().getUUID());
        if (e.getEntity() instanceof ServerPlayer p) {
            Management.close(p);
            Network.forget(p);
        }
    }
}
