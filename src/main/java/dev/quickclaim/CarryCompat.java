package dev.quickclaim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

final class CarryCompat {

    private static Method getter, carrying;

    private static boolean checked;
    private static final String RECOVERY = "QuickClaimCarryRecovery";

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void register() {
        if (!ModList.get().isLoaded("carryon")) return;
        try {
            Class<? extends Event> eventType =
                    Class.forName("tschipp.carryon.events.EntityPickupEvent")
                            .asSubclass(Event.class);
            var playerField = eventType.getField("player");
            var targetField = eventType.getField("target");
            MinecraftForge.EVENT_BUS.addListener(
                    EventPriority.HIGHEST,
                    false,
                    (Class) eventType,
                    (java.util.function.Consumer<Event>)
                            event -> {
                                try {
                                    var player = (ServerPlayer) playerField.get(event);
                                    var target = (Entity) targetField.get(event);
                                    var claim =
                                            ProtectionEvents.claim(
                                                    player.level(), target.blockPosition());
                                    if (!ClaimService.allowed(player, claim, "break")
                                            || (target instanceof Player
                                                    && (ProtectionEvents.pvpDenied(claim)
                                                            || ProtectionEvents.pvpDenied(
                                                                    ProtectionEvents.claim(
                                                                            player.level(),
                                                                            player
                                                                                    .blockPosition())))))
                                        event.setCanceled(true);
                                } catch (ReflectiveOperationException failure) {
                                    event.setCanceled(true);
                                }
                            });
        } catch (ReflectiveOperationException failure) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Carry On pickup protection could not be registered", failure);
        }
    }

    static void prepareRespawn(PlayerEvent.Clone event) {
        if (!event.isWasDeath()
                || !(event.getOriginal() instanceof ServerPlayer original)
                || !isCarrying(original)) return;
        var data = ClaimData.get(original.server);
        var pos = original.blockPosition();
        var near =
                dev.quickclaim.core.RegionBox.between(
                        pos.getX() - 15,
                        pos.getY() - 15,
                        pos.getZ() - 15,
                        pos.getX() + 15,
                        pos.getY() + 15,
                        pos.getZ() + 15);
        boolean protectedNearby =
                data.claims.values().stream()
                        .anyMatch(
                                claim ->
                                        claim.dimension.equals(ClaimService.dimension(original))
                                                && claim.box.intersects(near)
                                                && (!claim.faction
                                                        || (data.factions.containsKey(claim.owner)
                                                                && data.factions
                                                                        .get(claim.owner)
                                                                        .canExpand(
                                                                                System
                                                                                        .currentTimeMillis()))));
        if (!protectedNearby || getter == null || carrying == null) return;
        try {
            var carried = getter.invoke(null, original);
            var tag = ((CompoundTag) carried.getClass().getMethod("getNbt").invoke(carried)).copy();
            if (!tag.getString("type").equals("BLOCK") && !tag.getString("type").equals("ENTITY"))
                return;
            var manager = getter.getDeclaringClass();
            var empty =
                    carried.getClass()
                            .getConstructor(CompoundTag.class)
                            .newInstance(new CompoundTag());
            var setter = manager.getMethod("setCarryData", Player.class, carried.getClass());
            tag.putBoolean("keyPressed", false);
            setter.invoke(null, original, empty);
            event.getEntity().getPersistentData().put(RECOVERY, tag);
        } catch (ReflectiveOperationException failure) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Carry On respawn protection failed", failure);
        }
    }

    static void restoreRespawn(PlayerEvent.Clone event) {
        var persistent = event.getEntity().getPersistentData();
        if (!persistent.contains(RECOVERY) || getter == null) return;
        try {
            var type = getter.getReturnType();
            var tag = persistent.getCompound(RECOVERY).copy();
            var carried = type.getConstructor(CompoundTag.class).newInstance(tag);
            getter.getDeclaringClass()
                    .getMethod("setCarryData", Player.class, type)
                    .invoke(null, event.getEntity(), carried);
            event.getEntity().getInventory().selected =
                    Math.max(0, Math.min(8, tag.getInt("selected")));
            persistent.remove(RECOVERY);
        } catch (ReflectiveOperationException failure) {
            com.mojang.logging.LogUtils.getLogger()
                    .warn("Carry On respawn contents remain in recovery data", failure);
        }
    }

    static boolean isCarrying(Player player) {
        if (!ModList.get().isLoaded("carryon")) return false;
        try {
            if (!checked) {
                checked = true;
                Class<?> manager = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
                getter = manager.getMethod("getCarryData", Player.class);
                carrying = getter.getReturnType().getMethod("isCarrying");
            }
            return getter == null
                    || carrying == null
                    || (boolean) carrying.invoke(getter.invoke(null, player));
        } catch (ReflectiveOperationException ex) {
            return true;
        }
    }
}
