package dev.quickclaim.client;

import dev.quickclaim.Network;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class DashboardScreen extends Screen {

    private static final DateTimeFormatter DUE_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a", Locale.US)
                    .withZone(ZoneId.systemDefault());

    private enum Page {
        HOME,
        CLAIMS,
        FACTION,
        PEOPLE,
        VAULT,
        ADMIN
    }

    private final CompoundTag data;

    private final ListTag claims, members, trusted, invitations;

    private final List<Label> labels = new ArrayList<>();

    private record Label(String text, int x, int y, int color) {}

    private Page page = Page.HOME;

    private int x, y, claimIndex, memberIndex, trustIndex, inviteIndex;

    private boolean boundaries;
    private String feedback = "";
    private long feedbackUntil;

    void feedback(String message) {
        feedback = message;
        feedbackUntil = System.currentTimeMillis() + 8000;
    }

    private EditBox input, color, personalLimit, factionLimit, memberLimit;

    public DashboardScreen(CompoundTag data) {
        super(Component.literal("Claim Ledger"));
        this.data = data;
        claims = data.getList("Claims", Tag.TAG_COMPOUND);
        members = data.getList("Members", Tag.TAG_COMPOUND);
        trusted = data.getList("Trusted", Tag.TAG_COMPOUND);
        invitations = data.getList("Invitations", Tag.TAG_STRING);
        if (data.getBoolean("VaultOnly")) page = Page.VAULT;
    }

    void inherit(DashboardScreen previous) {
        page = data.getBoolean("VaultOnly") ? Page.VAULT : previous.page;
        if (page == Page.ADMIN && !data.getBoolean("Admin")) page = Page.HOME;
        claimIndex = previous.claimIndex;
        memberIndex = previous.memberIndex;
        trustIndex = previous.trustIndex;
        inviteIndex = previous.inviteIndex;
        boundaries = previous.boundaries;
        feedback = previous.feedback;
        feedbackUntil = previous.feedbackUntil;
    }

    @Override
    protected void init() {
        x = (width - 320) / 2;
        y = (height - 230) / 2;
        labels.clear();
        int index = 0;
        for (Page next : Page.values()) {
            if (data.getBoolean("VaultOnly") && next != Page.VAULT) continue;
            if (next == Page.ADMIN && !data.getBoolean("Admin")) continue;
            int buttonX = 8 + index++ * 51;
            button(
                    next.name().charAt(0) + next.name().substring(1).toLowerCase(Locale.ROOT),
                    buttonX,
                    22,
                    49,
                    () -> {
                        page = next;
                        rebuildWidgets();
                    });
        }
        switch (page) {
            case HOME -> home();
            case CLAIMS -> claims();
            case FACTION -> faction();
            case PEOPLE -> people();
            case VAULT -> vault();
            case ADMIN -> admin();
        }
    }

    private void home() {
        label("Personal: " + data.getString("Personal"), 10, 49);
        label("Trusted: " + data.getString("Trust"), 10, 63);
        label(data.getString("Faction"), 10, 80);
        label(data.getString("Status"), 10, 94);
        label("Selected: " + data.getString("Selection"), 10, 111);
        button("Claim personal", 10, 130, 145, () -> send("personal", "", ""));
        button("Claim for faction", 165, 130, 145, () -> send("factionClaim", "", "")).active =
                data.getBoolean("FactionManager");
        button("Ping personal home", 10, 155, 145, () -> ping(false));
        button("Ping faction home", 165, 155, 145, () -> ping(true));
        button(
                "Vault",
                10,
                180,
                145,
                () -> {
                    page = Page.VAULT;
                    rebuildWidgets();
                });
        button("Done", 165, 180, 145, this::onClose);
        label("Wand: use corner 1; sneak-use corner 2.", 10, 215);
    }

    private CompoundTag selected() {
        return claims.isEmpty()
                ? null
                : claims.getCompound(Math.floorMod(claimIndex, claims.size()));
    }

    private String claimId() {
        var selected = selected();
        return selected == null ? "" : selected.getUUID("Id").toString();
    }

    private void claimPicker() {
        button(
                "<",
                10,
                48,
                20,
                () -> {
                    claimIndex--;
                    rebuildWidgets();
                });
        var selected = selected();
        label(selected == null ? "No managed claims" : selected.getString("Name"), 38, 54);
        button(
                ">",
                290,
                48,
                20,
                () -> {
                    claimIndex++;
                    rebuildWidgets();
                });
    }

    private void claims() {
        claimPicker();
        var selected = selected();
        if (selected == null) return;
        boolean editable = selected.getBoolean("Editable");
        button(
                boundaries ? "Permissions" : "Edit boundaries",
                10,
                73,
                145,
                () -> {
                    boundaries = !boundaries;
                    rebuildWidgets();
                });
        button("Set main claim", 165, 73, 145, () -> send("home", claimId(), "")).active =
                editable && !selected.getBoolean("ServerClaim");
        if (boundaries) {
            label(
                    selected.getInt("Pieces") + " pieces • " + selected.getString("Dimension"),
                    10,
                    100);
            label("Wand: " + data.getString("Selection"), 10, 114);
            button("Add selection", 10, 133, 145, () -> send("add", claimId(), "")).active =
                    editable;
            button(
                                    "Subtract selection",
                                    165,
                                    133,
                                    145,
                                    () ->
                                            confirm(
                                                    "Remove the selected area from this claim?",
                                                    () -> send("subtract", claimId(), "")))
                            .active =
                    editable;
            button(
                                    "Replace boundaries",
                                    10,
                                    158,
                                    145,
                                    () ->
                                            confirm(
                                                    "Replace this claim with your wand selection?",
                                                    () -> send("replace", claimId(), "")))
                            .active =
                    editable;
            button(
                                    "Delete claim",
                                    165,
                                    158,
                                    145,
                                    () ->
                                            confirm(
                                                    "Remove this claim and its protection?",
                                                    () -> send("remove", claimId(), "")))
                            .active =
                    editable;
            input = field(10, 190, 200, selected.getString("Name"), 32);
            button("Rename", 220, 190, 90, () -> send("rename", claimId(), input.getValue()))
                            .active =
                    editable;
        } else {
            toggle("Visitors: use", "interact", "Interact", 10, 101, editable);
            toggle("Visitors: break", "break", "Break", 165, 101, editable);
            toggle("Visitors: place", "place", "Place", 10, 127, editable);
            toggle("PvP", "pvp", "Pvp", 165, 127, editable);
            toggle("Mob griefing", "mobGrief", "MobGrief", 10, 153, editable);
            toggle("Hostile spawning", "hostiles", "Hostiles", 165, 153, editable);
            toggle("Fire spread", "fireSpread", "FireSpread", 10, 179, editable);
            label("Members retain build/use access. Passive mobs spawn.", 10, 208);
        }
    }

    private void toggle(String label, String flag, String key, int bx, int by, boolean enabled) {
        button(
                                label + ": " + (selected().getBoolean(key) ? "ON" : "OFF"),
                                bx,
                                by,
                                145,
                                () -> send("flag", claimId(), flag))
                        .active =
                enabled;
    }

    private void faction() {
        boolean hasFaction = data.contains("FactionTitle"), owner = data.getBoolean("FactionOwner");
        if (!hasFaction) {
            label(data.getString("Payment"), 10, 51);
            label("New faction title", 10, 69);
            input = field(10, 83, 210, "", 24);
            button(
                    "Create",
                    230,
                    83,
                    80,
                    () -> {
                        String name = input.getValue();
                        confirm(
                                "Pay the creation cost from your inventory?",
                                () -> send("createFaction", "", name));
                    });
            label("Invitations", 10, 118);
            if (!invitations.isEmpty()) {
                String name = invitations.getString(Math.floorMod(inviteIndex, invitations.size()));
                label(name, 38, 141);
                button(
                        "<",
                        10,
                        135,
                        20,
                        () -> {
                            inviteIndex--;
                            rebuildWidgets();
                        });
                button(
                        ">",
                        290,
                        135,
                        20,
                        () -> {
                            inviteIndex++;
                            rebuildWidgets();
                        });
                button("Accept invitation", 10, 164, 300, () -> send("join", "", name));
            } else label("No pending invitations", 10, 141);
            return;
        }
        label(data.getString("FactionArea"), 10, 49);
        label(data.getString("Payment"), 10, 63);
        label("Due: " + DUE_DATE.format(Instant.ofEpochMilli(data.getLong("DueAt"))), 10, 77);
        label("Stored: " + data.getInt("Balance") + " • Owed: " + data.getLong("Owed"), 10, 91);
        input = field(10, 110, 200, data.getString("FactionTitle"), 24);
        button("Set title", 220, 110, 90, () -> send("title", "", input.getValue())).active = owner;
        color = field(10, 137, 200, data.getString("Color"), 7);
        button("Set color", 220, 137, 90, () -> send("color", "", color.getValue())).active = owner;
        button(
                                "Vault",
                                10,
                                169,
                                145,
                                () -> {
                                    page = Page.VAULT;
                                    rebuildWidgets();
                                })
                        .active =
                data.getBoolean("FactionManager");
        button(
                                "Leave faction",
                                165,
                                169,
                                145,
                                () -> confirm("Leave this faction?", () -> send("leave", "", "")))
                        .active =
                !owner;
        label(
                owner
                        ? "Assign managers or transfer ownership under People."
                        : "Faction identity and membership belong to the owner.",
                10,
                204);
    }

    private void people() {
        label("Online player name or UUID", 10, 49);
        input = field(10, 62, 200, "", 36);
        button("Trust", 220, 62, 90, () -> send("trust", "", input.getValue()));
        if (!trusted.isEmpty()) {
            var person = trusted.getCompound(Math.floorMod(trustIndex, trusted.size()));
            button(
                    "<",
                    10,
                    89,
                    20,
                    () -> {
                        trustIndex--;
                        rebuildWidgets();
                    });
            label("Trusted: " + person.getString("Name"), 38, 95);
            button(
                    ">",
                    190,
                    89,
                    20,
                    () -> {
                        trustIndex++;
                        rebuildWidgets();
                    });
            button("Untrust", 220, 89, 90, () -> send("untrust", "", person.getString("Id")));
        } else label("No trusted players", 10, 96);
        boolean owner = data.getBoolean("FactionOwner");
        button("Invite to faction", 10, 116, 145, () -> send("invite", "", input.getValue()))
                        .active =
                owner;
        label("Managers: " + managerCount() + " / 5", 165, 122);
        if (members.isEmpty()) return;
        var member = members.getCompound(Math.floorMod(memberIndex, members.size()));
        button(
                "<",
                10,
                144,
                20,
                () -> {
                    memberIndex--;
                    rebuildWidgets();
                });
        label(
                member.getString("Name") + (member.getBoolean("Manager") ? " [Manager]" : ""),
                38,
                150);
        button(
                ">",
                290,
                144,
                20,
                () -> {
                    memberIndex++;
                    rebuildWidgets();
                });
        String id = member.getString("Id");
        button(
                                member.getBoolean("Manager") ? "Remove manager" : "Make manager",
                                10,
                                173,
                                145,
                                () ->
                                        send(
                                                member.getBoolean("Manager") ? "demote" : "promote",
                                                "",
                                                id))
                        .active =
                owner;
        button(
                                "Remove member",
                                165,
                                173,
                                145,
                                () ->
                                        confirm(
                                                "Remove "
                                                        + member.getString("Name")
                                                        + " from the faction?",
                                                () -> send("kick", "", id)))
                        .active =
                owner;
        button(
                                "Transfer ownership",
                                10,
                                199,
                                300,
                                () ->
                                        confirm(
                                                "Make "
                                                        + member.getString("Name")
                                                        + " the owner? You lose your manager slot.",
                                                () -> send("transfer", "", id)))
                        .active =
                owner;
    }

    private long managerCount() {
        return members.stream().filter(t -> ((CompoundTag) t).getBoolean("Manager")).count();
    }

    private void vault() {
        label(data.getString("VaultName"), 10, 52);
        label("Storage belongs to this lectern's claim.", 10, 72);
        button("Open vault", 10, 94, 300, () -> send("vault", "", "")).active =
                data.getBoolean("VaultAccess");
        label("Personal: owner and four trusted players.", 10, 124);
        label("Faction: owner and four managers.", 10, 138);
        label(data.getString("VaultPayment"), 10, 164);
        if (data.getBoolean("TreasuryAccess")) {
            String status = data.getString("VaultStatus");
            labels.add(
                    new Label(
                            status,
                            310 - font.width(status),
                            52,
                            status.equals("Paid") ? 0x28733A : 0xA04A16));
            label("Stored: " + data.getInt("VaultBalance"), 10, 185);
        } else label("No upkeep", 10, 185);
    }

    private void admin() {
        button("Payment item / schedule", 10, 48, 195, () -> send("payments", "", ""));
        button(
                "Bypass: " + (data.getBoolean("Bypass") ? "ON" : "OFF"),
                215,
                48,
                95,
                () -> send("bypass", "", ""));
        label("Area: personal / faction base / per member", 10, 81);
        personalLimit = field(10, 96, 94, Integer.toString(data.getInt("PersonalLimit")), 8);
        factionLimit = field(113, 96, 94, Integer.toString(data.getInt("FactionLimit")), 8);
        memberLimit = field(216, 96, 94, Integer.toString(data.getInt("MemberLimit")), 8);
        button(
                "Save allowances",
                10,
                122,
                300,
                () ->
                        send(
                                "area",
                                "",
                                personalLimit.getValue()
                                        + ","
                                        + factionLimit.getValue()
                                        + ","
                                        + memberLimit.getValue()));
        var selected = selected();
        label(
                "PvP: "
                        + (selected == null
                                ? "Select a claim under Claims"
                                : selected.getString("Name")),
                10,
                157);
        button("Force on", 10, 176, 94, () -> send("adminPvp", claimId(), "on")).active =
                selected != null;
        button("Force off", 113, 176, 94, () -> send("adminPvp", claimId(), "off")).active =
                selected != null;
        button("Use claim", 216, 176, 94, () -> send("adminPvp", claimId(), "inherit")).active =
                selected != null;
        button(
                "Create server claim from wand selection",
                10,
                202,
                300,
                () -> send("serverClaim", "", ""));
    }

    private void confirm(String message, Runnable action) {
        minecraft.setScreen(
                new ConfirmScreen(
                        accepted -> {
                            minecraft.setScreen(this);
                            if (accepted) action.run();
                        },
                        Component.literal("Confirm change"),
                        Component.literal(message)));
    }

    private void ping(boolean faction) {
        Network.CHANNEL.sendToServer(new Network.Action(faction ? 5 : 4));
        onClose();
    }

    private void send(String action, String target, String value) {
        feedback = "";
        Network.CHANNEL.sendToServer(new Network.Manage(action, target, value));
    }

    private Button button(String label, int bx, int by, int w, Runnable action) {
        return addRenderableWidget(
                Button.builder(Component.literal(label), b -> action.run())
                        .bounds(x + bx, y + by, w, 20)
                        .build());
    }

    private EditBox field(int bx, int by, int w, String value, int maxLength) {
        var box = new EditBox(font, x + bx, y + by, w, 20, Component.literal("Value"));
        box.setMaxLength(maxLength);
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private void label(String text, int lx, int ly) {
        labels.add(new Label(text, lx, ly, 0x404040));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics);
        VanillaPanel.panel(graphics, x, y, 320, 230);
        graphics.drawString(font, title, x + 10, y + 8, 0x404040, false);
        for (var label : labels)
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(label.text, 310 - label.x),
                    x + label.x,
                    y + label.y,
                    label.color,
                    false);
        super.render(graphics, mouseX, mouseY, partial);
        if (!feedback.isEmpty() && System.currentTimeMillis() < feedbackUntil) {
            var lines = font.split(Component.literal(feedback), 300);
            int feedbackY = Math.min(y + 234, height - lines.size() * 10 - 8);
            graphics.fill(
                    x + 4, feedbackY - 3, x + 316, feedbackY + lines.size() * 10 + 3, 0xEE28222E);
            for (var line : lines) {
                graphics.drawString(font, line, x + 10, feedbackY, 0xFFFFFF, false);
                feedbackY += 10;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
