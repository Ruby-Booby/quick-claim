package dev.quickclaim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class AtlasSync {
    private AtlasSync() {}

    static UUID personalHome(ServerPlayer player, ClaimData data) {
        var saved = player.getPersistentData();
        var home =
                saved.hasUUID("QuickClaimHome")
                        ? data.claims.get(saved.getUUID("QuickClaimHome"))
                        : null;
        if (home != null
                && !home.faction
                && !home.serverClaim
                && home.owner.equals(player.getUUID())) return home.group;
        return data.claims.values().stream()
                .filter(
                        claim ->
                                !claim.faction
                                        && !claim.serverClaim
                                        && claim.owner.equals(player.getUUID()))
                .map(claim -> claim.group)
                .findFirst()
                .orElse(null);
    }

    static CompoundTag entry(
            ServerPlayer player, ClaimData data, ClaimData.Claim claim, UUID personalHome) {
        var tag = new CompoundTag();
        tag.putString("Name", claim.name);
        tag.putString("Dimension", claim.dimension);
        tag.putUUID("Group", claim.group);
        tag.putIntArray(
                "Bounds",
                new int[] {
                    claim.box.minX(),
                    claim.box.minY(),
                    claim.box.minZ(),
                    claim.box.maxX(),
                    claim.box.maxY(),
                    claim.box.maxZ()
                });
        var faction = claim.faction ? data.factions.get(claim.owner) : null;
        int color =
                claim.serverClaim
                        ? 0xFFFFCC55
                        : claim.owner.equals(player.getUUID()) ? 0xFF66DD88 : 0xFFB990EF;
        if (faction != null && faction.color.matches("#[0-9a-fA-F]{6}"))
            color = 0xFF000000 | Integer.parseInt(faction.color.substring(1), 16);
        tag.putInt("Color", color);
        boolean home =
                claim.group.equals(personalHome)
                        || (faction != null
                                && faction.members.contains(player.getUUID())
                                && claim.group.equals(faction.home));
        tag.putBoolean("Home", home && claim.id.equals(claim.group));
        tag.putBoolean(
                "Protected", faction == null || faction.canExpand(System.currentTimeMillis()));
        return tag;
    }

    static void send(ServerPlayer player) {
        var data = ClaimData.get(player.server);
        UUID personalHome = personalHome(player, data);
        var entries = new ListTag();
        boolean first = true;
        for (var claim : data.claims.values()) {
            entries.add(entry(player, data, claim, personalHome));
            if (entries.size() == 128) {
                sendBatch(player, entries, first, false);
                first = false;
                entries = new ListTag();
            }
        }
        sendBatch(player, entries, first, true);
    }

    private static void sendBatch(
            ServerPlayer player, ListTag entries, boolean first, boolean last) {
        var packet = new CompoundTag();
        packet.putString("Kind", "atlas");
        packet.putBoolean("First", first);
        packet.putBoolean("Last", last);
        packet.put("Claims", entries);
        Network.send(player, packet);
    }
}
