package dev.quickclaim;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class PaymentMenu extends AbstractContainerMenu {

    public final CompoundTag settings;

    public ItemStack sample;

    public int amount, days;

    public final long revision;

    public PaymentMenu(int id, Inventory inventory, CompoundTag tag) {
        super(QuickClaim.PAYMENT_MENU.get(), id);
        settings = tag == null ? new CompoundTag() : tag;
        sample = ItemStack.of(settings.getCompound("Sample"));
        amount = settings.getInt("Amount");
        days = settings.getInt("Days");
        revision = settings.getLong("Revision");
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 9; x++)
                addSlot(new Slot(inventory, x + y * 9 + 9, 34 + x * 18, 136 + y * 18));
        for (int x = 0; x < 9; x++) addSlot(new Slot(inventory, x, 34 + x * 18, 194));
    }

    public static CompoundTag snapshot(ClaimData d) {
        CompoundTag tag = new CompoundTag();
        tag.put("Sample", new ItemStack(d.currencyItem()).save(new CompoundTag()));
        tag.putInt("Amount", d.baseCost);
        tag.putInt("Days", d.intervalDays);
        tag.putLong("Revision", d.revision);
        return tag;
    }

    boolean save(ServerPlayer player, int amount, int days, long expectedRevision) {
        if (player.containerMenu != this || !stillValid(player) || sample.isEmpty()) return false;
        if (amount < 1 || amount > 10000 || days < 1 || days > 365) return false;
        var data = ClaimData.get(player.server);
        if (revision != data.revision || expectedRevision != data.revision) {
            ClaimService.tell(player, "Another admin changed payments. Reopen the editor.");
            return false;
        }
        long now = System.currentTimeMillis();
        for (var faction : data.factions.values()) faction.collect(now);
        data.currency =
                net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getKey(sample.getItem())
                        .toString();
        data.baseCost = amount;
        data.intervalDays = days;
        data.revision++;
        data.setDirty();
        return true;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player p) {
        if (!p.hasPermissions(2)) return;
        if (slot >= 0
                && slot < slots.size()
                && type == ClickType.PICKUP
                && !slots.get(slot).getItem().isEmpty())
            sample = slots.get(slot).getItem().copyWithCount(1);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player p) {
        return p.hasPermissions(2)
                && (!(p instanceof ServerPlayer serverPlayer) || Management.valid(serverPlayer));
    }
}
