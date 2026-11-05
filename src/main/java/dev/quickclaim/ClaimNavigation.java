package dev.quickclaim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class ClaimNavigation {
    private ClaimNavigation() {}

    static ClaimData.Claim home(ServerPlayer player, boolean factionHome) {
        var data = ClaimData.get(player.server);
        if (factionHome) {
            var faction = data.factionOf(player.getUUID());
            if (faction == null || faction.home == null) return null;
            var claim = data.claims.get(faction.home);
            if (claim == null
                    || !claim.faction
                    || claim.serverClaim
                    || !claim.owner.equals(faction.id)) return null;
            return claim;
        }
        var id = AtlasSync.personalHome(player, data);
        return id == null ? null : data.claims.get(id);
    }

    static List<String> choices(ServerPlayer player) {
        var choices = new ArrayList<String>();
        var personal = home(player, false);
        var faction = home(player, true);
        if (personal != null) {
            choices.add("personal");
            choices.add(personal.name);
        }
        if (faction != null) {
            choices.add("faction");
            choices.add(faction.name);
        }
        return choices;
    }

    static int ping(ServerPlayer player, String name) {
        if (name.equalsIgnoreCase("personal")) return ping(player, false);
        if (name.equalsIgnoreCase("faction")) return ping(player, true);
        var personal = home(player, false);
        var faction = home(player, true);
        boolean matchesPersonal = personal != null && personal.name.equalsIgnoreCase(name);
        boolean matchesFaction = faction != null && faction.name.equalsIgnoreCase(name);
        if (matchesPersonal && matchesFaction)
            return ClaimService.fail(
                    player,
                    "Both main claims have that name. Use /ping personal or /ping faction.");
        if (!matchesPersonal && !matchesFaction)
            return ClaimService.fail(
                    player, "No main claim with that name. Try /ping personal or /ping faction.");
        return send(player, matchesPersonal ? personal : faction);
    }

    static int ping(ServerPlayer player, boolean faction) {
        var claim = home(player, faction);
        if (claim == null)
            return ClaimService.fail(
                    player, "Open the lectern's Claims tab and choose Set main claim first.");
        return send(player, claim);
    }

    private static int send(ServerPlayer player, ClaimData.Claim claim) {
        var packet = new CompoundTag();
        packet.putString("Kind", "ping");
        packet.putString("Name", claim.name);
        packet.putString("Dimension", claim.dimension);
        packet.putInt("X", claim.box.minX() + (claim.box.maxX() - claim.box.minX()) / 2);
        packet.putInt("Y", claim.box.minY() + (claim.box.maxY() - claim.box.minY()) / 2);
        packet.putInt("Z", claim.box.minZ() + (claim.box.maxZ() - claim.box.minZ()) / 2);
        Network.send(player, packet);
        return 1;
    }
}
