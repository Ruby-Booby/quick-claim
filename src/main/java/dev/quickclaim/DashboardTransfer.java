package dev.quickclaim;

import net.minecraft.nbt.*;

import java.util.*;

public final class DashboardTransfer {
    private static final int ENTRIES_PER_PACKET = 128;
    private static final List<String> LISTS =
            List.of("Claims", "Members", "Trusted", "Invitations");
    private CompoundTag pending;

    public static List<CompoundTag> packets(CompoundTag snapshot) {
        var packets = new ArrayList<CompoundTag>();
        var header = snapshot.copy();
        LISTS.forEach(header::remove);
        header.putString("Kind", "dashboardStart");
        packets.add(header);
        for (String key : LISTS) {
            var list = (ListTag) snapshot.get(key);
            if (list == null) continue;
            for (int start = 0; start < list.size(); start += ENTRIES_PER_PACKET) {
                var part = new CompoundTag();
                part.putString("Kind", "dashboardPart");
                part.putString("List", key);
                var entries = new ListTag();
                int end = Math.min(start + ENTRIES_PER_PACKET, list.size());
                for (int i = start; i < end; i++) entries.add(list.get(i).copy());
                part.put("Entries", entries);
                packets.add(part);
            }
        }
        var end = new CompoundTag();
        end.putString("Kind", "dashboardEnd");
        packets.add(end);
        return packets;
    }

    public CompoundTag accept(CompoundTag packet) {
        switch (packet.getString("Kind")) {
            case "dashboardStart" -> {
                pending = packet.copy();
                LISTS.forEach(key -> pending.put(key, new ListTag()));
            }
            case "dashboardPart" -> {
                String key = packet.getString("List");
                if (pending != null
                        && LISTS.contains(key)
                        && packet.get("Entries") instanceof ListTag entries) {
                    var list = (ListTag) pending.get(key);
                    entries.forEach(entry -> list.add(entry.copy()));
                }
            }
            case "dashboardEnd" -> {
                var result = pending;
                pending = null;
                if (result != null) result.putString("Kind", "dashboard");
                return result;
            }
            default -> {}
        }
        return null;
    }

    public void clear() {
        pending = null;
    }
}
