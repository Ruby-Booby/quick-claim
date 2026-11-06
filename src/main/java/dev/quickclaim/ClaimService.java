package dev.quickclaim;

import dev.quickclaim.core.*;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;

import java.util.*;

public final class ClaimService {

    static Component claimTitle(ClaimData data, ClaimData.Claim claim) {
        var title = Component.literal(claim.name);
        var faction = claim.faction ? data.factions.get(claim.owner) : null;
        if (faction != null) {
            var color = TextColor.parseColor(faction.color);
            if (color != null) title.withStyle(style -> style.withColor(color));
        }
        return title;
    }

    public record Selection(String dimension, BlockPos first, BlockPos second) {}

    static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    static final Set<UUID> BYPASS = new HashSet<>();

    private ClaimService() {}

    public static String dimension(ServerPlayer p) {
        return p.level().dimension().location().toString();
    }

    public static void tell(ServerPlayer p, String message) {
        p.sendSystemMessage(Component.literal(message));
        if (Management.valid(p)) {
            var packet = new net.minecraft.nbt.CompoundTag();
            packet.putString("Kind", "feedback");
            packet.putString("Message", message);
            Network.send(p, packet);
        }
    }

    public static boolean bypass(ServerPlayer p) {
        return p.hasPermissions(2) && BYPASS.contains(p.getUUID());
    }

    public static void corner(ServerPlayer player, BlockPos pos, boolean second) {
        var owner = player.getUUID();
        String dimension = dimension(player);
        if (!second) {
            SELECTIONS.put(owner, new Selection(dimension, pos.immutable(), null));
            tell(
                    player,
                    "First corner at "
                            + pos.toShortString()
                            + ". Crouch and use the wand on the opposite corner.");
            return;
        }

        Selection selection = SELECTIONS.get(owner);
        if (selection == null || !selection.dimension().equals(dimension)) {
            tell(player, "Use the wand without crouching to start a selection in this dimension.");
            return;
        }
        SELECTIONS.put(owner, new Selection(dimension, selection.first(), pos.immutable()));
        tell(
                player,
                "Second corner at "
                        + pos.toShortString()
                        + ". Open your claim lectern to use this selection.");
    }

    public static int create(ServerPlayer p, boolean faction) {
        return create(p, faction, false);
    }

    public static int create(ServerPlayer p, boolean faction, boolean serverClaim) {
        if (serverClaim && !p.hasPermissions(2))
            return fail(p, "Only operators can create server claims.");
        if (serverClaim) faction = false;
        ClaimData d = ClaimData.get(p.server);
        Selection s = SELECTIONS.get(p.getUUID());
        if (s == null || s.second == null || !s.dimension.equals(dimension(p)))
            return fail(p, "Select both corners in this dimension first.");
        ClaimData.Faction f = faction ? d.factionOf(p.getUUID()) : null;
        if (faction && (f == null || !f.manages(p.getUUID())))
            return fail(p, "Only faction managers can add faction claims.");
        if (faction && !f.canExpand(System.currentTimeMillis()))
            return fail(
                    p,
                    "Expansion and protection are suspended: pay the overdue upkeep in your"
                            + " treasury.");
        RegionBox b =
                RegionBox.between(
                        s.first.getX(),
                        s.first.getY(),
                        s.first.getZ(),
                        s.second.getX(),
                        s.second.getY(),
                        s.second.getZ());
        if (b.minY() < p.level().getMinBuildHeight()
                || b.maxY() >= p.level().getMaxBuildHeight()
                || !p.level().getWorldBorder().isWithinBounds(s.first)
                || !p.level().getWorldBorder().isWithinBounds(s.second))
            return fail(p, "Selection is outside the buildable world.");
        UUID owner = serverClaim ? new UUID(0, 0) : faction ? f.id : p.getUUID();
        if (!serverClaim && d.areaWith(owner, faction, s.dimension, b) > d.allowance(f))
            return fail(
                    p,
                    "Area allowance exceeded. Used "
                            + d.usedArea(owner, faction)
                            + ", selected "
                            + b.area()
                            + ", allowed "
                            + d.allowance(f)
                            + " horizontal blocks.");
        if (d.claims.values().stream()
                .anyMatch(c -> c.dimension.equals(s.dimension) && c.box.intersects(b)))
            return fail(p, "That region overlaps an existing claim.");
        ClaimData.Claim c =
                new ClaimData.Claim(
                        owner,
                        faction,
                        s.dimension,
                        serverClaim
                                ? "Server claim"
                                : faction ? f.name : p.getGameProfile().getName() + "'s claim",
                        b);
        c.serverClaim = serverClaim;
        d.claims.put(c.id, c);
        if (faction && f.home == null) f.home = c.id;
        d.setDirty();
        tell(
                p,
                "Claim created: "
                        + b.area()
                        + " horizontal blocks, "
                        + (b.maxY() - b.minY() + 1)
                        + " blocks tall.");
        return 1;
    }

    public static boolean member(ClaimData d, ClaimData.Claim c, UUID player) {
        if (c.serverClaim) return false;
        if (!c.faction) return c.owner.equals(player);
        ClaimData.Faction f = d.factions.get(c.owner);
        return f != null && f.members.contains(player);
    }

    public static boolean owns(ClaimData d, ClaimData.Claim c, ServerPlayer p) {
        if (c.serverClaim) return p.hasPermissions(2);
        ClaimData.Faction f = c.faction ? d.factions.get(c.owner) : null;
        return bypass(p)
                || (!c.faction && c.owner.equals(p.getUUID()))
                || (f != null && f.manages(p.getUUID()));
    }

    public static boolean allowed(ServerPlayer p, ClaimData.Claim c, String action) {
        if (c == null || bypass(p)) return true;
        ClaimData d = ClaimData.get(p.server);
        if (action.equals("drop")) return false;
        if (c.serverClaim && p.hasPermissions(2)) return true;
        if (member(d, c, p.getUUID())) return true;
        return switch (action) {
            case "interact" ->
                    c.publicInteract
                            || (!c.serverClaim && !c.faction && d.trusted(c.owner, p.getUUID()));
            case "break" -> c.publicBreak;
            case "place" -> c.publicPlace;
            default -> false;
        };
    }

    public static int fail(ServerPlayer p, String message) {
        tell(p, message);
        return 0;
    }

    public static int trust(ServerPlayer p, UUID other, boolean add) {
        ClaimData d = ClaimData.get(p.server);
        Set<UUID> group = d.trusts.computeIfAbsent(p.getUUID(), id -> new HashSet<>());
        if (other.equals(p.getUUID())) return fail(p, "You already have access.");
        if (add && !group.contains(other) && group.size() >= 4)
            return fail(p, "Trust group is full: owner plus four trusted players.");
        if (add) group.add(other);
        else group.remove(other);
        d.setDirty();
        tell(p, "Trust updated.");
        return 1;
    }

    public static int newFaction(ServerPlayer p, String name) {
        ClaimData d = ClaimData.get(p.server);
        name = name.strip();
        if (name.isEmpty() || name.length() > 24)
            return fail(p, "Use a faction name of 1–24 characters.");
        if (d.factionOf(p.getUUID()) != null) return fail(p, "You already belong to a faction.");
        final String wanted = name;
        if (d.factions.values().stream().anyMatch(f -> f.name.equalsIgnoreCase(wanted)))
            return fail(p, "That faction name is taken.");
        if (d.currencyItem() == Items.AIR)
            return fail(
                    p, "The configured payment item is missing. Ask an admin to choose another.");
        int available = 0;
        for (ItemStack stack : p.getInventory().items)
            if (stack.is(d.currencyItem())) available += stack.getCount();
        if (available < d.baseCost)
            return fail(
                    p,
                    "Creation costs "
                            + d.baseCost
                            + " "
                            + d.currencyName()
                            + " from your inventory.");
        int left = d.baseCost;
        for (ItemStack stack : p.getInventory().items)
            if (stack.is(d.currencyItem())) {
                int n = Math.min(left, stack.getCount());
                stack.shrink(n);
                left -= n;
            }
        ClaimData.Faction f =
                d
                .new Faction(
                        p.getUUID(),
                        name,
                        System.currentTimeMillis() + d.intervalDays * Billing.DAY);
        d.factions.put(f.id, f);
        d.setDirty();
        refreshNames(p);
        tell(
                p,
                "Created "
                        + name
                        + ". Use the Vault tab in your faction's claim lectern to fund upkeep.");
        return 1;
    }

    static void refreshNames(ServerPlayer p) {
        p.server
                .getPlayerList()
                .getPlayers()
                .forEach(
                        player -> {
                            player.refreshDisplayName();
                            player.refreshTabListName();
                        });
    }

    public static ClaimData.Faction ownerFaction(ServerPlayer p) {
        ClaimData.Faction f = ClaimData.get(p.server).factionOf(p.getUUID());
        if (f == null || !f.owner.equals(p.getUUID())) {
            tell(p, "Only your faction owner can do this.");
            return null;
        }
        return f;
    }

    public static String status(ClaimData.Faction f, long now) {
        f.collect(now);
        if (now < f.nextDue) return "Paid";
        return f.canExpand(now)
                ? "7-day grace; protection and expansion active"
                : "Overdue: protection OFF; expansion frozen";
    }
}
