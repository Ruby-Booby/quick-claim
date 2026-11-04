package dev.quickclaim;

import dev.quickclaim.core.RegionBox;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public final class Management {

    private record Session(String dimension, BlockPos block) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private Management() {}

    static boolean canUseSilently(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64
                || !player.level().getBlockState(pos).is(QuickClaim.CLAIM_BLOCK.get()))
            return false;
        var data = ClaimData.get(player.server);
        data.reconcileTreasuries();
        if (player.level().getBlockEntity(pos) instanceof ClaimBlock.Entity entity
                && entity.faction != null) {
            var faction = data.factions.get(entity.faction);
            if (faction != null
                    && pos.equals(faction.treasuryPos)
                    && ClaimService.dimension(player).equals(faction.treasuryDimension))
                return faction.manages(player.getUUID()) || ClaimService.bypass(player);
            entity.faction = null;
            entity.setChanged();
        }
        var claim = data.at(ClaimService.dimension(player), pos);
        return claim == null
                || ClaimService.owns(data, claim, player)
                || (!claim.faction
                        && !claim.serverClaim
                        && data.trusted(claim.owner, player.getUUID()));
    }

    static boolean canUse(ServerPlayer player, BlockPos pos) {
        boolean allowed = canUseSilently(player, pos);
        if (!allowed)
            ClaimService.tell(
                    player,
                    "Only the owner, trusted players, or authorized faction managers can use this"
                            + " claim block.");
        return allowed;
    }

    static void open(ServerPlayer player, BlockPos pos) {
        SESSIONS.put(
                player.getUUID(), new Session(ClaimService.dimension(player), pos.immutable()));
        Network.openDashboard(player);
    }

    static boolean valid(ServerPlayer player) {
        var session = SESSIONS.get(player.getUUID());
        return session != null
                && session.dimension.equals(ClaimService.dimension(player))
                && canUseSilently(player, session.block);
    }

    static boolean vaultOnly(ServerPlayer player) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null) return false;
        var data = ClaimData.get(player.server);
        var claim = data.at(session.dimension, session.block);
        return claim != null && !ClaimService.owns(data, claim, player);
    }

    static void close(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    static String name(ServerPlayer player, UUID id) {
        if (id.equals(player.getUUID())) return player.getGameProfile().getName();
        var players = player.server.getPlayerList();
        var online = players == null ? null : players.getPlayer(id);
        if (online != null) return online.getGameProfile().getName();
        var profiles = player.server.getProfileCache();
        if (profiles == null) return id.toString();
        return profiles.get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString());
    }

    private static UUID playerId(ServerPlayer player, String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
        }
        var online = player.server.getPlayerList().getPlayerByName(input);
        if (online != null) return online.getUUID();
        return null;
    }

    public static void act(ServerPlayer player, String action, String target, String value) {
        if (!valid(player)) {
            ClaimService.tell(player, "Use a nearby claim block to manage claims.");
            return;
        }
        var data = ClaimData.get(player.server);
        if (vaultOnly(player) && !action.equals("vault")) {
            ClaimService.tell(player, "Trust grants vault access, not claim management.");
            return;
        }
        switch (action) {
            case "serverClaim" -> ClaimService.create(player, false, true);
            case "vault" -> {
                ClaimBlock.openVault(player, SESSIONS.get(player.getUUID()).block);
                return;
            }
            case "personal" -> ClaimService.create(player, false);
            case "factionClaim" -> ClaimService.create(player, true);
            case "storage" -> {
                ClaimBlock.openStorage(player, SESSIONS.get(player.getUUID()).block);
                return;
            }
            case "payments" -> {
                ClaimCommands.openPayments(player);
                return;
            }
            case "createFaction" -> ClaimService.newFaction(player, value);
            case "join" -> ClaimCommands.join(player, value);
            case "leave" -> ClaimCommands.leave(player);
            case "title" -> ClaimCommands.title(player, value);
            case "color" -> {
                var faction = ClaimService.ownerFaction(player);
                if (faction != null && value.matches("#[0-9a-fA-F]{6}")) {
                    faction.color = value;
                    data.setDirty();
                    ClaimService.refreshNames(player);
                }
            }
            case "trust", "untrust", "invite", "kick", "promote", "demote", "transfer" ->
                    personAction(player, action, value);
            case "bypass" -> {
                if (player.hasPermissions(2)) {
                    if (!ClaimService.BYPASS.remove(player.getUUID()))
                        ClaimService.BYPASS.add(player.getUUID());
                }
            }
            case "area" -> {
                if (player.hasPermissions(2)) {
                    String[] numbers = value.split(",");
                    try {
                        if (numbers.length != 3) return;
                        int personal = Integer.parseInt(numbers[0]),
                                base = Integer.parseInt(numbers[1]),
                                member = Integer.parseInt(numbers[2]);
                        if (personal < 1
                                || base < 1
                                || member < 0
                                || personal > 10000000
                                || base > 10000000
                                || member > 1000000) return;
                        data.personalArea = personal;
                        data.factionBaseArea = base;
                        data.memberArea = member;
                        data.setDirty();
                    } catch (NumberFormatException ignored) {
                        ClaimService.tell(player, "Enter whole numbers for area allowances.");
                    }
                }
            }
            case "flag", "rename", "home", "remove", "replace", "add", "subtract", "adminPvp" ->
                    claimAction(player, action, target, value);
            default -> {}
        }
        if (valid(player)) Network.openDashboard(player);
    }

    static void personAction(ServerPlayer player, String action, String value) {
        UUID other = playerId(player, value);
        if (other == null) {
            ClaimService.tell(
                    player, "Choose a listed player, enter an online name, or enter a UUID.");
            return;
        }
        if (action.equals("trust") || action.equals("untrust")) {
            ClaimService.trust(player, other, action.equals("trust"));
            return;
        }
        var data = ClaimData.get(player.server);
        var faction = ClaimService.ownerFaction(player);
        if (faction == null) return;
        switch (action) {
            case "invite" -> {
                if (data.factionOf(other) != null) {
                    ClaimService.tell(player, "That player already belongs to a faction.");
                    return;
                }
                faction.invites.add(other);
                var invited = player.server.getPlayerList().getPlayer(other);
                if (invited != null)
                    ClaimService.tell(
                            invited,
                            "You were invited to "
                                    + faction.name
                                    + ". Accept at an accessible claim block.");
            }
            case "kick" -> {
                ClaimCommands.kick(player, other);
                return;
            }
            case "promote" -> {
                if (!faction.members.contains(other)) {
                    ClaimService.tell(player, "Invite the player and have them join first.");
                    return;
                }
                if (faction.managers.size() >= 5 && !faction.managers.contains(other)) {
                    ClaimService.tell(
                            player, "All five manager slots are occupied, including the owner.");
                    return;
                }
                faction.managers.add(other);
            }
            case "demote" -> {
                if (!faction.owner.equals(other)) faction.managers.remove(other);
            }
            case "transfer" -> {
                if (!faction.members.contains(other)) return;
                faction.managers.remove(faction.owner);
                faction.owner = other;
                faction.managers.add(other);
            }
            default -> {}
        }
        data.setDirty();
        ClaimService.refreshNames(player);
    }

    static void claimAction(ServerPlayer player, String action, String target, String value) {
        var data = ClaimData.get(player.server);
        UUID id;
        try {
            id = UUID.fromString(target);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        var claim = data.claims.get(id);
        if (claim == null) return;
        if (action.equals("adminPvp")) {
            if (!player.hasPermissions(2)) return;
            int setting =
                    switch (value) {
                        case "on" -> 1;
                        case "off" -> 0;
                        default -> -1;
                    };
            for (var part : data.claims.values())
                if (part.group.equals(claim.group)) part.adminPvp = setting;
            data.setDirty();
            return;
        }
        if (!ClaimService.owns(data, claim, player)) return;
        var parts =
                data.claims.values().stream()
                        .filter(part -> part.group.equals(claim.group))
                        .toList();
        switch (action) {
            case "rename" -> {
                if (!value.isBlank() && value.length() <= 32)
                    parts.forEach(part -> part.name = value.strip());
            }
            case "home" -> {
                if (claim.faction) data.factions.get(claim.owner).home = claim.group;
                else if (claim.owner.equals(player.getUUID()))
                    player.getPersistentData().putUUID("QuickClaimHome", claim.group);
            }
            case "remove" -> data.removeGroup(claim.group);
            case "flag" -> {
                for (var part : parts)
                    switch (value) {
                        case "interact" -> part.publicInteract = !part.publicInteract;
                        case "break" -> part.publicBreak = !part.publicBreak;
                        case "place" -> part.publicPlace = !part.publicPlace;
                        case "pvp" -> part.pvp = !part.pvp;
                        case "mobGrief" -> part.mobGrief = !part.mobGrief;
                        case "hostiles" -> part.hostileSpawns = !part.hostileSpawns;
                        default -> {}
                    }
            }
            case "replace", "add", "subtract" -> editBounds(player, claim, parts, action);
            default -> {}
        }
        data.setDirty();
    }

    private static void editBounds(
            ServerPlayer player,
            ClaimData.Claim original,
            List<ClaimData.Claim> parts,
            String operation) {
        var selection = ClaimService.SELECTIONS.get(player.getUUID());
        if (selection == null
                || selection.second() == null
                || !selection.dimension().equals(original.dimension)) {
            ClaimService.tell(
                    player, "Use the wand to select two corners in the claim's dimension first.");
            return;
        }
        var a = selection.first();
        var b = selection.second();
        var selected =
                RegionBox.between(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        var level =
                player.server.getLevel(
                        net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION,
                                net.minecraft.resources.ResourceLocation.tryParse(
                                        original.dimension)));
        if (level == null
                || selected.minY() < level.getMinBuildHeight()
                || selected.maxY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(a)
                || !level.getWorldBorder().isWithinBounds(b)) {
            ClaimService.tell(player, "Selection is outside the buildable world.");
            return;
        }
        var data = ClaimData.get(player.server);
        List<RegionBox> boxes = new ArrayList<>();
        if (operation.equals("subtract")) {
            for (var part : parts) boxes.addAll(part.box.subtract(selected));
        } else if (operation.equals("replace")) boxes.add(selected);
        else {
            boxes.addAll(parts.stream().map(part -> part.box).toList());
            List<RegionBox> additions = new ArrayList<>(List.of(selected));
            for (var part : parts) {
                var next = new ArrayList<RegionBox>();
                for (var box : additions) next.addAll(box.subtract(part.box));
                additions = next;
            }
            boxes.addAll(additions);
        }
        if (boxes.size() > 256) {
            ClaimService.tell(
                    player, "This edit would exceed 256 pieces. Simplify the claim first.");
            return;
        }
        var faction = original.faction ? data.factions.get(original.owner) : null;
        if (faction != null && !faction.canExpand(System.currentTimeMillis())) {
            for (var box : boxes) {
                List<RegionBox> uncovered = new ArrayList<>(List.of(box));
                for (var part : parts) {
                    var next = new ArrayList<RegionBox>();
                    for (var region : uncovered) next.addAll(region.subtract(part.box));
                    uncovered = next;
                }
                if (!uncovered.isEmpty()) {
                    ClaimService.tell(
                            player,
                            "Pay overdue upkeep before expanding. You can still subtract land.");
                    return;
                }
            }
        }
        for (var box : boxes)
            for (var other : data.claims.values()) {
                if (!other.group.equals(original.group)
                        && other.dimension.equals(original.dimension)
                        && other.box.intersects(box)) {
                    ClaimService.tell(player, "The edited claim would overlap another claim.");
                    return;
                }
            }
        var dimensionBoxes = new HashMap<String, List<RegionBox>>();
        for (var other : data.claims.values())
            if (other.owner.equals(original.owner)
                    && other.faction == original.faction
                    && !other.group.equals(original.group))
                dimensionBoxes
                        .computeIfAbsent(other.dimension, key -> new ArrayList<>())
                        .add(other.box);
        dimensionBoxes.computeIfAbsent(original.dimension, key -> new ArrayList<>()).addAll(boxes);
        long area = dimensionBoxes.values().stream().mapToLong(RegionBox::projectedArea).sum();
        if (!original.serverClaim
                && !operation.equals("subtract")
                && area > data.allowance(faction)
                && area > data.usedArea(original.owner, original.faction)) {
            ClaimService.tell(player, "This edit exceeds the claim area allowance.");
            return;
        }
        parts.forEach(part -> data.claims.remove(part.id));
        if (boxes.isEmpty()) data.removeGroup(original.group);
        boolean first = true;
        for (var box : boxes) {
            var copy = ClaimData.Claim.load(original.save());
            copy.box = box;
            copy.id = first ? original.group : UUID.randomUUID();
            first = false;
            data.claims.put(copy.id, copy);
        }
        data.reconcileTreasuries();
        data.setDirty();
        ClaimService.tell(player, "Claim updated: " + boxes.size() + " region pieces.");
    }

    static void populate(ServerPlayer player, CompoundTag result) {
        var data = ClaimData.get(player.server);
        var faction = data.factionOf(player.getUUID());
        result.putBoolean("Bypass", ClaimService.bypass(player));
        result.putBoolean("VaultOnly", vaultOnly(player));
        var session = SESSIONS.get(player.getUUID());
        var context = session == null ? null : data.at(session.dimension, session.block);
        result.putBoolean(
                "VaultAccess", context != null && ClaimBlock.vaultAllowed(player, context));
        result.putBoolean(
                "TreasuryAccess",
                context != null && context.faction && ClaimBlock.vaultAllowed(player, context));
        result.putString(
                "VaultName",
                context == null ? "Claim this block's location to use a vault" : context.name);
        if (context != null && context.faction) {
            var contextFaction = data.factions.get(context.owner);
            if (contextFaction != null) {
                long now = System.currentTimeMillis();
                contextFaction.collect(now);
                result.putString(
                        "VaultStatus",
                        now < contextFaction.nextDue
                                ? "Paid"
                                : contextFaction.canExpand(now) ? "Grace" : "Overdue");
                result.putString(
                        "VaultPayment",
                        contextFaction.cost()
                                + " "
                                + data.currencyName()
                                + " / "
                                + data.intervalDays
                                + " real days");
                result.putInt("VaultBalance", contextFaction.balance());
            }
        }
        result.putInt("PersonalLimit", data.personalArea);
        result.putInt("FactionLimit", data.factionBaseArea);
        result.putInt("MemberLimit", data.memberArea);
        result.putBoolean("FactionManager", faction != null && faction.manages(player.getUUID()));
        result.putBoolean(
                "FactionOwner", faction != null && faction.owner.equals(player.getUUID()));
        if (faction != null) {
            result.putString("FactionTitle", faction.name);
            result.putString("Color", faction.color);
            result.putLong("Owed", faction.debt(System.currentTimeMillis()));
            result.putInt("Balance", faction.balance());
        }
        ListTag claims = new ListTag();
        Set<UUID> groups = new HashSet<>();
        for (var claim : data.claims.values()) {
            if (!groups.add(claim.group)) continue;
            if (!ClaimService.owns(data, claim, player) && !player.hasPermissions(2)) continue;
            CompoundTag entry = claim.save();
            entry.putBoolean("Editable", ClaimService.owns(data, claim, player));
            entry.putInt(
                    "Pieces",
                    (int)
                            data.claims.values().stream()
                                    .filter(part -> part.group.equals(claim.group))
                                    .count());
            claims.add(entry);
        }
        result.put("Claims", claims);
        result.put(
                "Trusted",
                people(player, data.trusts.getOrDefault(player.getUUID(), Set.of()), null));
        result.put(
                "Members", people(player, faction == null ? Set.of() : faction.members, faction));
        ListTag invites = new ListTag();
        for (var other : data.factions.values())
            if (other.invites.contains(player.getUUID()))
                invites.add(StringTag.valueOf(other.name));
        result.put("Invitations", invites);
    }

    private static ListTag people(
            ServerPlayer player, Collection<UUID> ids, ClaimData.Faction faction) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", id.toString());
            entry.putString("Name", name(player, id));
            entry.putBoolean("Manager", faction != null && faction.manages(id));
            list.add(entry);
        }
        return list;
    }
}
