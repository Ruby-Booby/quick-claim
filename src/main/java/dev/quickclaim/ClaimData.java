package dev.quickclaim;

import dev.quickclaim.core.*;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public final class ClaimData extends SavedData {

    public final Map<UUID, Claim> claims = new LinkedHashMap<>();

    public final Map<UUID, Faction> factions = new LinkedHashMap<>();

    public final Map<UUID, Set<UUID>> trusts = new HashMap<>();
    private final Map<UUID, SimpleContainer> vaults = new HashMap<>();

    public SimpleContainer vault(UUID owner) {
        return vaults.computeIfAbsent(
                owner,
                key -> {
                    var inventory = new SimpleContainer(27);
                    inventory.addListener(changed -> setDirty());
                    return inventory;
                });
    }

    public String currency = "minecraft:diamond";

    public int baseCost = 200, intervalDays = 30;

    public long revision;

    public int personalArea = 2560, factionBaseArea = 2560, memberArea = 256;

    public static ClaimData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(ClaimData::load, ClaimData::new, "quickclaim");
    }

    public Item currencyItem() {
        var id = ResourceLocation.tryParse(currency);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return item == null ? Items.AIR : item;
    }

    public String currencyName() {
        return new ItemStack(currencyItem()).getHoverName().getString();
    }

    public Faction factionOf(UUID player) {
        return factions.values().stream()
                .filter(f -> f.members.contains(player))
                .findFirst()
                .orElse(null);
    }

    public Claim at(String dim, BlockPos p) {
        return claims.values().stream()
                .filter(
                        c ->
                                c.dimension.equals(dim)
                                        && c.box.contains(p.getX(), p.getY(), p.getZ()))
                .findFirst()
                .orElse(null);
    }

    public Claim protectedAt(String dim, BlockPos p) {
        Claim c = at(dim, p);
        if (c != null && c.faction) {
            Faction f = factions.get(c.owner);
            if (f == null || !f.canExpand(System.currentTimeMillis())) return null;
        }
        return c;
    }

    public boolean trusted(UUID owner, UUID player) {
        return trusts.getOrDefault(owner, Set.of()).contains(player);
    }

    public long usedArea(UUID owner, boolean faction) {
        var byDimension = new HashMap<String, List<RegionBox>>();
        for (var c : claims.values())
            if (c.owner.equals(owner) && c.faction == faction)
                byDimension.computeIfAbsent(c.dimension, key -> new ArrayList<>()).add(c.box);
        return byDimension.values().stream().mapToLong(RegionBox::projectedArea).sum();
    }

    public long allowance(Faction f) {
        return f == null
                ? personalArea
                : factionBaseArea + (long) Math.max(0, f.members.size() - 1) * memberArea;
    }

    public long areaWith(UUID owner, boolean faction, String dimension, RegionBox addition) {
        var regions = new HashMap<String, List<RegionBox>>();
        for (var claim : claims.values()) {
            if (claim.owner.equals(owner) && claim.faction == faction) {
                regions.computeIfAbsent(claim.dimension, key -> new ArrayList<>()).add(claim.box);
            }
        }
        regions.computeIfAbsent(dimension, key -> new ArrayList<>()).add(addition);
        return regions.values().stream().mapToLong(RegionBox::projectedArea).sum();
    }

    public List<Claim> group(UUID group) {
        return claims.values().stream().filter(claim -> claim.group.equals(group)).toList();
    }

    public void removeGroup(UUID group) {
        claims.values().removeIf(claim -> claim.group.equals(group));
        reconcileTreasuries();
        factions.values()
                .forEach(
                        faction -> {
                            if (group.equals(faction.home)) faction.home = null;
                        });
        setDirty();
    }

    public void reconcileTreasuries() {
        for (var faction : factions.values()) {
            if (faction.treasuryPos == null) continue;
            var claim = at(faction.treasuryDimension, faction.treasuryPos);
            if (claim == null || !claim.faction || !claim.owner.equals(faction.id)) {
                faction.treasuryPos = null;
                faction.treasuryDimension = "";
                setDirty();
            }
        }
    }

    public static final class Claim {

        public UUID id = UUID.randomUUID(), group = id, owner;
        public String dimension, name;

        public RegionBox box;

        public boolean serverClaim,
                faction,
                pvp,
                publicInteract,
                publicBreak,
                publicPlace,
                mobGrief,
                fireSpread,
                hostileSpawns = true;

        public int adminPvp = -1;

        public Claim(UUID owner, boolean faction, String dimension, String name, RegionBox box) {
            this.owner = owner;
            this.faction = faction;
            this.dimension = dimension;
            this.name = name;
            this.box = box;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putUUID("Group", group);
            t.putUUID("Owner", owner);
            t.putBoolean("Faction", faction);
            t.putBoolean("ServerClaim", serverClaim);
            t.putString("Dimension", dimension);
            t.putString("Name", name);
            t.putIntArray(
                    "Bounds",
                    new int[] {
                        box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()
                    });
            t.putBoolean("Pvp", pvp);
            t.putInt("AdminPvp", adminPvp);
            t.putBoolean("Interact", publicInteract);
            t.putBoolean("Break", publicBreak);
            t.putBoolean("Place", publicPlace);
            t.putBoolean("MobGrief", mobGrief);
            t.putBoolean("FireSpread", fireSpread);
            t.putBoolean("Hostiles", hostileSpawns);
            return t;
        }

        static Claim load(CompoundTag t) {
            int[] b = t.getIntArray("Bounds");
            if (b.length != 6) throw new IllegalArgumentException("Invalid saved claim bounds");
            Claim c =
                    new Claim(
                            t.getUUID("Owner"),
                            t.getBoolean("Faction"),
                            t.getString("Dimension"),
                            t.getString("Name"),
                            new RegionBox(b[0], b[1], b[2], b[3], b[4], b[5]));
            c.serverClaim = t.getBoolean("ServerClaim");
            c.id = t.getUUID("Id");
            c.group = t.hasUUID("Group") ? t.getUUID("Group") : c.id;
            c.pvp = t.getBoolean("Pvp");
            c.adminPvp = t.contains("AdminPvp") ? t.getInt("AdminPvp") : -1;
            c.publicInteract = t.getBoolean("Interact");
            c.publicBreak = t.getBoolean("Break");
            c.publicPlace = t.getBoolean("Place");
            c.mobGrief = t.getBoolean("MobGrief");
            c.fireSpread = t.getBoolean("FireSpread");
            c.hostileSpawns = t.getBoolean("Hostiles");
            return c;
        }
    }

    public final class Faction {

        public UUID id = UUID.randomUUID(), owner;
        public String name, color = "#55FFFF";
        public Set<UUID> members = new LinkedHashSet<>(),
                invites = new HashSet<>(),
                managers = new LinkedHashSet<>();

        public UUID home;

        public String treasuryDimension = "";

        public BlockPos treasuryPos;

        public long nextDue, nextCharge, outstanding;

        public final SimpleContainer treasury = new SimpleContainer(27);

        public Faction(UUID owner, String name, long nextDue) {
            this.owner = owner;
            this.name = name;
            this.nextDue = nextDue;
            this.nextCharge = nextDue;
            members.add(owner);
            managers.add(owner);
            treasury.addListener(c -> setDirty());
        }

        public int cost() {
            return Billing.cost(baseCost, members.size());
        }

        public boolean manages(UUID player) {
            return owner.equals(player) || managers.contains(player);
        }

        public long debt(long now) {
            return new Billing.Account(nextDue, nextCharge, outstanding)
                    .accrue(now, intervalDays, cost())
                    .owed();
        }

        public boolean canExpand(long now) {
            collect(now);
            return Billing.canExpandAt(now, nextDue);
        }

        public int balance() {
            int count = 0;
            for (int i = 0; i < treasury.getContainerSize(); i++)
                if (treasury.getItem(i).is(currencyItem())) count += treasury.getItem(i).getCount();
            return count;
        }

        public void collect(long now) {
            if (currencyItem() == Items.AIR) return;
            var account =
                    new Billing.Account(nextDue, nextCharge, outstanding)
                            .accrue(now, intervalDays, cost());
            int payment = (int) Math.min(account.owed(), balance());
            if (account.owed() == outstanding && payment == 0) return;
            int remaining = payment;
            for (int i = 0; i < 27 && remaining > 0; i++) {
                ItemStack stack = treasury.getItem(i);
                if (stack.is(currencyItem())) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
            }
            account = account.pay(payment);
            nextDue = account.due();
            nextCharge = account.nextCharge();
            outstanding = account.owed();
            treasury.setChanged();
            setDirty();
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.putUUID("Owner", owner);
            t.putString("Name", name);
            t.putString("Color", color);
            t.put("Members", uuids(members));
            t.put("Invites", uuids(invites));
            t.put("Managers", uuids(managers));
            t.putLong("NextDue", nextDue);
            t.putLong("NextCharge", nextCharge);
            t.putLong("Outstanding", outstanding);
            if (home != null) t.putUUID("Home", home);
            t.putString("TreasuryDimension", treasuryDimension);
            if (treasuryPos != null) t.putLong("TreasuryPos", treasuryPos.asLong());
            ListTag inventory = new ListTag();
            for (int i = 0; i < 27; i++) {
                CompoundTag s = new CompoundTag();
                s.putInt("Slot", i);
                treasury.getItem(i).save(s);
                inventory.add(s);
            }
            t.put("Inventory", inventory);
            return t;
        }
    }

    private static ListTag uuids(Collection<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) list.add(StringTag.valueOf(id.toString()));
        return list;
    }

    private static Set<UUID> readUuids(ListTag list) {
        Set<UUID> result = new LinkedHashSet<>();
        for (Tag t : list) result.add(UUID.fromString(t.getAsString()));
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.putInt("Schema", 2);
        t.putString("Currency", currency);
        t.putInt("BaseCost", baseCost);
        t.putInt("IntervalDays", intervalDays);
        t.putLong("Revision", revision);
        t.putInt("PersonalArea", personalArea);
        t.putInt("FactionBaseArea", factionBaseArea);
        t.putInt("MemberArea", memberArea);
        ListTag c = new ListTag();
        claims.values().forEach(v -> c.add(v.save()));
        t.put("Claims", c);
        ListTag f = new ListTag();
        factions.values().forEach(v -> f.add(v.save()));
        t.put("Factions", f);
        ListTag tr = new ListTag();
        trusts.forEach(
                (id, ids) -> {
                    CompoundTag a = new CompoundTag();
                    a.putUUID("Owner", id);
                    a.put("Players", uuids(ids));
                    tr.add(a);
                });
        t.put("Trusts", tr);
        ListTag vaultList = new ListTag();
        vaults.forEach(
                (owner, inventory) -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putUUID("Owner", owner);
                    var items = new ListTag();
                    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                        if (inventory.getItem(slot).isEmpty()) continue;
                        var item = inventory.getItem(slot).save(new CompoundTag());
                        item.putInt("Slot", slot);
                        items.add(item);
                    }
                    entry.put("Items", items);
                    vaultList.add(entry);
                });
        t.put("Vaults", vaultList);
        return t;
    }

    static ClaimData load(CompoundTag t) {
        ClaimData d = new ClaimData();
        if (t.contains("Currency")) d.currency = t.getString("Currency");
        if (t.contains("BaseCost")) d.baseCost = Math.max(1, Math.min(10000, t.getInt("BaseCost")));
        if (t.contains("IntervalDays"))
            d.intervalDays = Math.max(1, Math.min(365, t.getInt("IntervalDays")));
        d.revision = t.getLong("Revision");
        if (t.contains("PersonalArea")) d.personalArea = t.getInt("PersonalArea");
        if (t.contains("FactionBaseArea")) d.factionBaseArea = t.getInt("FactionBaseArea");
        if (t.contains("MemberArea")) d.memberArea = t.getInt("MemberArea");
        for (Tag tag : t.getList("Claims", Tag.TAG_COMPOUND)) {
            Claim c = Claim.load((CompoundTag) tag);
            d.claims.put(c.id, c);
        }
        for (Tag tag : t.getList("Factions", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) tag;
            Faction f =
                    d.new Faction(a.getUUID("Owner"), a.getString("Name"), a.getLong("NextDue"));
            f.id = a.getUUID("Id");
            f.color = a.getString("Color");
            f.members = readUuids(a.getList("Members", Tag.TAG_STRING));
            f.members.add(f.owner);
            f.managers = readUuids(a.getList("Managers", Tag.TAG_STRING));
            f.managers.retainAll(f.members);
            f.managers.add(f.owner);
            f.nextCharge = a.contains("NextCharge") ? a.getLong("NextCharge") : f.nextDue;
            f.outstanding = a.getLong("Outstanding");
            f.invites = readUuids(a.getList("Invites", Tag.TAG_STRING));
            if (a.hasUUID("Home")) f.home = a.getUUID("Home");
            f.treasuryDimension = a.getString("TreasuryDimension");
            if (a.contains("TreasuryPos")) f.treasuryPos = BlockPos.of(a.getLong("TreasuryPos"));
            for (Tag slot : a.getList("Inventory", Tag.TAG_COMPOUND)) {
                CompoundTag s = (CompoundTag) slot;
                int i = s.getInt("Slot");
                if (i >= 0 && i < 27) f.treasury.setItem(i, ItemStack.of(s));
            }
            d.factions.put(f.id, f);
        }
        for (Tag tag : t.getList("Trusts", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) tag;
            d.trusts.put(a.getUUID("Owner"), readUuids(a.getList("Players", Tag.TAG_STRING)));
        }
        for (Tag tag : t.getList("Vaults", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) tag;
            var inventory = d.vault(entry.getUUID("Owner"));
            for (Tag item : entry.getList("Items", Tag.TAG_COMPOUND)) {
                var saved = (CompoundTag) item;
                int slot = saved.getInt("Slot");
                if (slot >= 0 && slot < inventory.getContainerSize())
                    inventory.setItem(slot, ItemStack.of(saved));
            }
        }
        return d;
    }
}
