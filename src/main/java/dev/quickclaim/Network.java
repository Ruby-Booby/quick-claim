package dev.quickclaim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.*;
import java.util.function.Supplier;

public final class Network {

    static final String VERSION = "4";

    public static final SimpleChannel CHANNEL =
            NetworkRegistry.newSimpleChannel(
                    ResourceLocation.fromNamespaceAndPath(QuickClaim.ID, "main"),
                    () -> VERSION,
                    VERSION::equals,
                    VERSION::equals);

    private static final Map<UUID, Integer> LAST_ACTION = new HashMap<>();
    private static final Map<UUID, Integer> LAST_ATLAS = new HashMap<>();

    public static void init() {
        CHANNEL.registerMessage(
                0,
                Action.class,
                Action::encode,
                Action::decode,
                Action::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                1,
                View.class,
                View::encode,
                View::decode,
                View::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(
                2,
                SavePayments.class,
                SavePayments::encode,
                SavePayments::decode,
                SavePayments::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(
                3,
                Manage.class,
                Manage::encode,
                Manage::decode,
                Manage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    static boolean accept(ServerPlayer p) {
        if (p == null) return false;
        int now = p.server.getTickCount();
        Integer old = LAST_ACTION.get(p.getUUID());
        if (old != null && now >= old && now - old < 2) return false;
        LAST_ACTION.put(p.getUUID(), now);
        return true;
    }

    static void forget(ServerPlayer player) {
        LAST_ACTION.remove(player.getUUID());
        LAST_ATLAS.remove(player.getUUID());
    }

    public record Action(int action) {

        static void encode(Action p, FriendlyByteBuf b) {
            b.writeVarInt(p.action);
        }

        static Action decode(FriendlyByteBuf b) {
            return new Action(b.readVarInt());
        }

        static void handle(Action packet, Supplier<NetworkEvent.Context> supplier) {
            var ctx = supplier.get();
            ctx.enqueueWork(
                    () -> {
                        ServerPlayer p = ctx.getSender();
                        if (!accept(p)) return;
                        switch (packet.action) {
                            case 0 -> openDashboard(p);
                            case 1 -> Management.act(p, "personal", "", "");
                            case 2 -> Management.act(p, "factionClaim", "", "");
                            case 3 -> Management.act(p, "payments", "", "");
                            case 4 -> ClaimNavigation.ping(p, false);
                            case 5 -> ClaimNavigation.ping(p, true);
                            case 6 -> {
                                int now = p.server.getTickCount();
                                var last = LAST_ATLAS.get(p.getUUID());
                                if (last == null || now < last || now - last >= 100) {
                                    LAST_ATLAS.put(p.getUUID(), now);
                                    AtlasSync.send(p);
                                }
                            }
                            default -> {}
                        }
                    });
            ctx.setPacketHandled(true);
        }
    }

    public record Manage(String action, String target, String value) {

        static void encode(Manage p, FriendlyByteBuf b) {
            b.writeUtf(p.action, 32);
            b.writeUtf(p.target, 64);
            b.writeUtf(p.value, 128);
        }

        static Manage decode(FriendlyByteBuf b) {
            return new Manage(b.readUtf(32), b.readUtf(64), b.readUtf(128));
        }

        static void handle(Manage packet, Supplier<NetworkEvent.Context> supplier) {
            var ctx = supplier.get();
            ctx.enqueueWork(
                    () -> {
                        var player = ctx.getSender();
                        if (accept(player))
                            Management.act(player, packet.action, packet.target, packet.value);
                    });
            ctx.setPacketHandled(true);
        }
    }

    public record SavePayments(int menuId, int amount, int days, long revision) {

        static void encode(SavePayments p, FriendlyByteBuf b) {
            b.writeVarInt(p.menuId);
            b.writeVarInt(p.amount);
            b.writeVarInt(p.days);
            b.writeLong(p.revision);
        }

        static SavePayments decode(FriendlyByteBuf b) {
            return new SavePayments(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readLong());
        }

        static void handle(SavePayments packet, Supplier<NetworkEvent.Context> supplier) {
            var ctx = supplier.get();
            ctx.enqueueWork(
                    () -> {
                        ServerPlayer p = ctx.getSender();
                        if (p == null
                                || !(p.containerMenu instanceof PaymentMenu menu)
                                || menu.containerId != packet.menuId) return;
                        if (!menu.save(p, packet.amount, packet.days, packet.revision)) return;
                        var d = ClaimData.get(p.server);
                        p.closeContainer();
                        ClaimService.tell(
                                p,
                                "Saved: "
                                        + d.baseCost
                                        + " "
                                        + d.currencyName()
                                        + " every "
                                        + d.intervalDays
                                        + " real days. Existing due dates are retained.");
                    });
            ctx.setPacketHandled(true);
        }
    }

    public record View(CompoundTag data) {

        static void encode(View p, FriendlyByteBuf b) {
            b.writeNbt(p.data);
        }

        static View decode(FriendlyByteBuf b) {
            return new View(b.readNbt());
        }

        static void handle(View packet, Supplier<NetworkEvent.Context> supplier) {
            var ctx = supplier.get();
            ctx.enqueueWork(
                    () ->
                            DistExecutor.unsafeRunWhenOn(
                                    Dist.CLIENT,
                                    () ->
                                            () ->
                                                    dev.quickclaim.client.ClientEvents.receive(
                                                            packet.data)));
            ctx.setPacketHandled(true);
        }
    }

    static void send(ServerPlayer p, CompoundTag data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new View(data));
    }

    public static void openDashboard(ServerPlayer p) {
        if (!Management.valid(p)) {
            ClaimService.tell(p, "Right-click a claim block to open the management UI.");
            return;
        }
        var d = ClaimData.get(p.server);
        var f = d.factionOf(p.getUUID());
        CompoundTag t = new CompoundTag();
        t.putString("Kind", "dashboard");
        t.putBoolean("Admin", p.hasPermissions(2));
        t.putString(
                "Personal",
                d.usedArea(p.getUUID(), false) + " / " + d.personalArea + " blocks of area");
        t.putString(
                "Trust", (1 + d.trusts.getOrDefault(p.getUUID(), Set.of()).size()) + " / 5 people");
        t.putString(
                "Faction",
                f == null ? "No faction" : f.name + " (" + f.members.size() + " members)");
        t.putString(
                "FactionArea",
                f == null
                        ? "Create a faction in the Faction tab"
                        : d.usedArea(f.id, true) + " / " + d.allowance(f) + " blocks of area");
        t.putString(
                "Status",
                f == null
                        ? "Personal claims have no upkeep"
                        : ClaimService.status(f, System.currentTimeMillis()));
        t.putString(
                "Payment",
                f == null
                        ? "Creation: " + d.baseCost + " " + d.currencyName()
                        : f.cost() + " " + d.currencyName() + " / " + d.intervalDays + " days");
        if (f != null) t.putLong("DueAt", f.nextDue);
        var s = ClaimService.SELECTIONS.get(p.getUUID());
        t.putString(
                "Selection",
                s == null
                        ? "Select two corners with the wand"
                        : s.first().toShortString()
                                + " to "
                                + (s.second() == null ? "?" : s.second().toShortString()));
        t.putBoolean("FactionOwner", f != null && f.owner.equals(p.getUUID()));
        Management.populate(p, t);
        for (var packet : DashboardTransfer.packets(t)) send(p, packet);
    }
}
