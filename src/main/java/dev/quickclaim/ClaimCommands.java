package dev.quickclaim;

import com.mojang.brigadier.arguments.*;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.network.NetworkHooks;

import java.time.*;
import java.util.*;

public final class ClaimCommands {

    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher()
                .register(
                        Commands.literal("ping")
                                .executes(
                                        c ->
                                                ClaimNavigation.ping(
                                                        c.getSource().getPlayerOrException(),
                                                        "personal"))
                                .then(
                                        Commands.argument(
                                                        "claim", StringArgumentType.greedyString())
                                                .suggests(
                                                        (c, builder) ->
                                                                net.minecraft.commands
                                                                        .SharedSuggestionProvider
                                                                        .suggest(
                                                                                ClaimNavigation
                                                                                        .choices(
                                                                                                c.getSource()
                                                                                                        .getPlayerOrException()),
                                                                                builder))
                                                .executes(
                                                        c ->
                                                                ClaimNavigation.ping(
                                                                        c.getSource()
                                                                                .getPlayerOrException(),
                                                                        StringArgumentType
                                                                                .getString(
                                                                                        c,
                                                                                        "claim")))));
        e.getDispatcher()
                .register(
                        Commands.literal("claim")
                                .executes(
                                        c -> {
                                            Network.openDashboard(
                                                    c.getSource().getPlayerOrException());
                                            return 1;
                                        })
                                .then(
                                        Commands.literal("wand")
                                                .executes(
                                                        c -> {
                                                            var p =
                                                                    c.getSource()
                                                                            .getPlayerOrException();
                                                            if (!p.getInventory()
                                                                    .add(
                                                                            new net.minecraft.world
                                                                                    .item.ItemStack(
                                                                                    QuickClaim.WAND
                                                                                            .get())))
                                                                return ClaimService.fail(
                                                                        p,
                                                                        "Make room in your"
                                                                            + " inventory first.");
                                                            return 1;
                                                        }))
                                .then(
                                        Commands.literal("pos1")
                                                .executes(
                                                        c -> {
                                                            var p =
                                                                    c.getSource()
                                                                            .getPlayerOrException();
                                                            ClaimService.corner(
                                                                    p, p.blockPosition(), false);
                                                            return 1;
                                                        }))
                                .then(
                                        Commands.literal("pos2")
                                                .executes(
                                                        c -> {
                                                            var p =
                                                                    c.getSource()
                                                                            .getPlayerOrException();
                                                            ClaimService.corner(
                                                                    p, p.blockPosition(), true);
                                                            return 1;
                                                        }))
                                .then(
                                        Commands.literal("create")
                                                .executes(
                                                        c ->
                                                                ClaimService.create(
                                                                        c.getSource()
                                                                                .getPlayerOrException(),
                                                                        false)))
                                .then(
                                        Commands.literal("faction")
                                                .executes(
                                                        c ->
                                                                ClaimService.create(
                                                                        c.getSource()
                                                                                .getPlayerOrException(),
                                                                        true)))
                                .then(
                                        Commands.literal("remove")
                                                .executes(
                                                        c ->
                                                                remove(
                                                                        c.getSource()
                                                                                .getPlayerOrException())))
                                .then(
                                        Commands.literal("trust")
                                                .then(
                                                        Commands.argument(
                                                                        "player",
                                                                        EntityArgument.player())
                                                                .executes(
                                                                        c ->
                                                                                ClaimService.trust(
                                                                                        c.getSource()
                                                                                                .getPlayerOrException(),
                                                                                        EntityArgument
                                                                                                .getPlayer(
                                                                                                        c,
                                                                                                        "player")
                                                                                                .getUUID(),
                                                                                        true))))
                                .then(
                                        Commands.literal("untrust")
                                                .then(
                                                        Commands.argument(
                                                                        "player",
                                                                        EntityArgument.player())
                                                                .executes(
                                                                        c ->
                                                                                ClaimService.trust(
                                                                                        c.getSource()
                                                                                                .getPlayerOrException(),
                                                                                        EntityArgument
                                                                                                .getPlayer(
                                                                                                        c,
                                                                                                        "player")
                                                                                                .getUUID(),
                                                                                        false))))
                                .then(
                                        Commands.literal("home")
                                                .executes(
                                                        c ->
                                                                home(
                                                                        c.getSource()
                                                                                .getPlayerOrException())))
                                .then(
                                        Commands.literal("flag")
                                                .then(
                                                        Commands.argument(
                                                                        "flag",
                                                                        StringArgumentType.word())
                                                                .suggests(
                                                                        (c, b) -> {
                                                                            for (String s :
                                                                                    List.of(
                                                                                            "interact",
                                                                                            "break",
                                                                                            "place",
                                                                                            "pvp",
                                                                                            "mobGrief",
                                                                                            "fireSpread",
                                                                                            "hostiles"))
                                                                                b.suggest(s);
                                                                            return b.buildFuture();
                                                                        })
                                                                .then(
                                                                        Commands.argument(
                                                                                        "enabled",
                                                                                        BoolArgumentType
                                                                                                .bool())
                                                                                .executes(
                                                                                        c ->
                                                                                                flag(
                                                                                                        c.getSource()
                                                                                                                .getPlayerOrException(),
                                                                                                        StringArgumentType
                                                                                                                .getString(
                                                                                                                        c,
                                                                                                                        "flag"),
                                                                                                        BoolArgumentType
                                                                                                                .getBool(
                                                                                                                        c,
                                                                                                                        "enabled"))))))
                                .then(
                                        Commands.literal("admin")
                                                .requires(s -> s.hasPermission(2))
                                                .then(
                                                        Commands.literal("bypass")
                                                                .executes(
                                                                        c -> {
                                                                            var p =
                                                                                    c.getSource()
                                                                                            .getPlayerOrException();
                                                                            boolean enabled =
                                                                                    !ClaimService
                                                                                            .BYPASS
                                                                                            .remove(
                                                                                                    p
                                                                                                            .getUUID());
                                                                            if (enabled)
                                                                                ClaimService.BYPASS
                                                                                        .add(
                                                                                                p
                                                                                                        .getUUID());
                                                                            ClaimService.tell(
                                                                                    p,
                                                                                    "Admin bypass "
                                                                                            + (enabled
                                                                                                    ? "enabled"
                                                                                                    : "disabled"));
                                                                            return 1;
                                                                        }))
                                                .then(
                                                        Commands.literal("payments")
                                                                .executes(
                                                                        c -> {
                                                                            openPayments(
                                                                                    c.getSource()
                                                                                            .getPlayerOrException());
                                                                            return 1;
                                                                        }))
                                                .then(
                                                        Commands.literal("pvp")
                                                                .then(
                                                                        Commands.argument(
                                                                                        "mode",
                                                                                        StringArgumentType
                                                                                                .word())
                                                                                .suggests(
                                                                                        (c, b) -> {
                                                                                            b
                                                                                                    .suggest(
                                                                                                            "on");
                                                                                            b
                                                                                                    .suggest(
                                                                                                            "off");
                                                                                            b
                                                                                                    .suggest(
                                                                                                            "inherit");
                                                                                            return b
                                                                                                    .buildFuture();
                                                                                        })
                                                                                .executes(
                                                                                        c ->
                                                                                                adminPvp(
                                                                                                        c.getSource()
                                                                                                                .getPlayerOrException(),
                                                                                                        StringArgumentType
                                                                                                                .getString(
                                                                                                                        c,
                                                                                                                        "mode")))))
                                                .then(
                                                        Commands.literal("area")
                                                                .then(
                                                                        Commands.argument(
                                                                                        "personal",
                                                                                        IntegerArgumentType
                                                                                                .integer(
                                                                                                        1,
                                                                                                        10000000))
                                                                                .then(
                                                                                        Commands
                                                                                                .argument(
                                                                                                        "factionBase",
                                                                                                        IntegerArgumentType
                                                                                                                .integer(
                                                                                                                        1,
                                                                                                                        10000000))
                                                                                                .then(
                                                                                                        Commands
                                                                                                                .argument(
                                                                                                                        "perMember",
                                                                                                                        IntegerArgumentType
                                                                                                                                .integer(
                                                                                                                                        0,
                                                                                                                                        1000000))
                                                                                                                .executes(
                                                                                                                        c -> {
                                                                                                                            var
                                                                                                                                    p =
                                                                                                                                            c.getSource()
                                                                                                                                                    .getPlayerOrException();
                                                                                                                            var
                                                                                                                                    d =
                                                                                                                                            ClaimData
                                                                                                                                                    .get(
                                                                                                                                                            p.server);
                                                                                                                            d.personalArea =
                                                                                                                                    IntegerArgumentType
                                                                                                                                            .getInteger(
                                                                                                                                                    c,
                                                                                                                                                    "personal");
                                                                                                                            d.factionBaseArea =
                                                                                                                                    IntegerArgumentType
                                                                                                                                            .getInteger(
                                                                                                                                                    c,
                                                                                                                                                    "factionBase");
                                                                                                                            d.memberArea =
                                                                                                                                    IntegerArgumentType
                                                                                                                                            .getInteger(
                                                                                                                                                    c,
                                                                                                                                                    "perMember");
                                                                                                                            d
                                                                                                                                    .setDirty();
                                                                                                                            ClaimService
                                                                                                                                    .tell(
                                                                                                                                            p,
                                                                                                                                            "Area allowances"
                                                                                                                                                + " updated;"
                                                                                                                                                + " existing"
                                                                                                                                                + " claims"
                                                                                                                                                + " retained.");
                                                                                                                            return 1;
                                                                                                                        })))))));
        e.getDispatcher()
                .register(
                        Commands.literal("faction")
                                .executes(c -> info(c.getSource().getPlayerOrException()))
                                .then(
                                        Commands.literal("create")
                                                .then(
                                                        Commands.argument(
                                                                        "name",
                                                                        StringArgumentType
                                                                                .greedyString())
                                                                .executes(
                                                                        c ->
                                                                                ClaimService
                                                                                        .newFaction(
                                                                                                c.getSource()
                                                                                                        .getPlayerOrException(),
                                                                                                StringArgumentType
                                                                                                        .getString(
                                                                                                                c,
                                                                                                                "name")))))
                                .then(
                                        Commands.literal("invite")
                                                .then(
                                                        Commands.argument(
                                                                        "player",
                                                                        EntityArgument.player())
                                                                .executes(
                                                                        c -> {
                                                                            var p =
                                                                                    c.getSource()
                                                                                            .getPlayerOrException();
                                                                            var f =
                                                                                    ClaimService
                                                                                            .ownerFaction(
                                                                                                    p);
                                                                            if (f == null) return 0;
                                                                            var other =
                                                                                    EntityArgument
                                                                                            .getPlayer(
                                                                                                    c,
                                                                                                    "player");
                                                                            if (ClaimData.get(
                                                                                                    p.server)
                                                                                            .factionOf(
                                                                                                    other
                                                                                                            .getUUID())
                                                                                    != null)
                                                                                return ClaimService
                                                                                        .fail(
                                                                                                p,
                                                                                                "That player"
                                                                                                    + " already"
                                                                                                    + " has a"
                                                                                                    + " faction.");
                                                                            f.invites.add(
                                                                                    other
                                                                                            .getUUID());
                                                                            ClaimData.get(p.server)
                                                                                    .setDirty();
                                                                            ClaimService.tell(
                                                                                    other,
                                                                                    "Invitation to "
                                                                                            + f.name
                                                                                            + ". Accept"
                                                                                            + " with"
                                                                                            + " /faction"
                                                                                            + " join"
                                                                                            + " "
                                                                                            + f.name);
                                                                            ClaimService.tell(
                                                                                    p,
                                                                                    "Invitation"
                                                                                        + " sent.");
                                                                            return 1;
                                                                        })))
                                .then(
                                        Commands.literal("join")
                                                .then(
                                                        Commands.argument(
                                                                        "name",
                                                                        StringArgumentType
                                                                                .greedyString())
                                                                .executes(
                                                                        c ->
                                                                                join(
                                                                                        c.getSource()
                                                                                                .getPlayerOrException(),
                                                                                        StringArgumentType
                                                                                                .getString(
                                                                                                        c,
                                                                                                        "name")))))
                                .then(
                                        Commands.literal("kick")
                                                .then(
                                                        Commands.argument(
                                                                        "player",
                                                                        EntityArgument.player())
                                                                .executes(
                                                                        c ->
                                                                                kick(
                                                                                        c.getSource()
                                                                                                .getPlayerOrException(),
                                                                                        EntityArgument
                                                                                                .getPlayer(
                                                                                                        c,
                                                                                                        "player")
                                                                                                .getUUID()))))
                                .then(
                                        Commands.literal("leave")
                                                .executes(
                                                        c ->
                                                                leave(
                                                                        c.getSource()
                                                                                .getPlayerOrException())))
                                .then(
                                        Commands.literal("title")
                                                .then(
                                                        Commands.argument(
                                                                        "title",
                                                                        StringArgumentType
                                                                                .greedyString())
                                                                .executes(
                                                                        c ->
                                                                                title(
                                                                                        c.getSource()
                                                                                                .getPlayerOrException(),
                                                                                        StringArgumentType
                                                                                                .getString(
                                                                                                        c,
                                                                                                        "title")))))
                                .then(
                                        Commands.literal("color")
                                                .then(
                                                        Commands.argument(
                                                                        "hex",
                                                                        StringArgumentType.word())
                                                                .executes(
                                                                        c -> {
                                                                            var p =
                                                                                    c.getSource()
                                                                                            .getPlayerOrException();
                                                                            var f =
                                                                                    ClaimService
                                                                                            .ownerFaction(
                                                                                                    p);
                                                                            if (f == null) return 0;
                                                                            String color =
                                                                                    StringArgumentType
                                                                                            .getString(
                                                                                                    c,
                                                                                                    "hex");
                                                                            if (!color.matches(
                                                                                    "#[0-9a-fA-F]{6}"))
                                                                                return ClaimService
                                                                                        .fail(
                                                                                                p,
                                                                                                "Use a"
                                                                                                    + " hex color"
                                                                                                    + " such"
                                                                                                    + " as #55FFFF"
                                                                                                    + " (quote"
                                                                                                    + " it if"
                                                                                                    + " needed).");
                                                                            f.color = color;
                                                                            ClaimData.get(p.server)
                                                                                    .setDirty();
                                                                            ClaimService
                                                                                    .refreshNames(
                                                                                            p);
                                                                            return 1;
                                                                        }))));
    }

    static int remove(ServerPlayer p) {
        var d = ClaimData.get(p.server);
        var c = d.at(ClaimService.dimension(p), p.blockPosition());
        if (c == null || !ClaimService.owns(d, c, p))
            return ClaimService.fail(p, "Stand inside a claim you own.");
        d.removeGroup(c.group);
        ClaimService.tell(p, "Claim removed.");
        return 1;
    }

    static int home(ServerPlayer p) {
        var d = ClaimData.get(p.server);
        var c = d.at(ClaimService.dimension(p), p.blockPosition());
        if (c == null || !ClaimService.owns(d, c, p))
            return ClaimService.fail(p, "Stand in a claim you own.");
        if (c.faction) d.factions.get(c.owner).home = c.group;
        else if (c.owner.equals(p.getUUID())) {
            p.getPersistentData().putUUID("QuickClaimHome", c.group);
        }
        d.setDirty();
        ClaimService.tell(p, "Main claim selected for home pings.");
        return 1;
    }

    static int flag(ServerPlayer p, String flag, boolean value) {
        var d = ClaimData.get(p.server);
        var c = d.at(ClaimService.dimension(p), p.blockPosition());
        if (c == null || !ClaimService.owns(d, c, p))
            return ClaimService.fail(p, "Stand inside a claim you own.");
        for (var part : d.group(c.group))
            switch (flag) {
                case "interact" -> part.publicInteract = value;
                case "break" -> part.publicBreak = value;
                case "place" -> part.publicPlace = value;
                case "pvp" -> part.pvp = value;
                case "mobGrief" -> part.mobGrief = value;
                case "fireSpread" -> part.fireSpread = value;
                case "hostiles" -> part.hostileSpawns = value;
                default -> {
                    return ClaimService.fail(p, "Unknown flag.");
                }
            }
        d.setDirty();
        ClaimService.tell(
                p,
                flag
                        + " = "
                        + value
                        + ". Interaction/build flags govern visitors; members retain access.");
        return 1;
    }

    static int adminPvp(ServerPlayer p, String mode) {
        var d = ClaimData.get(p.server);
        var c = d.at(ClaimService.dimension(p), p.blockPosition());
        if (c == null) return ClaimService.fail(p, "Stand inside a claim.");
        if (!List.of("on", "off", "inherit").contains(mode))
            return ClaimService.fail(p, "Use on, off or inherit.");
        for (var part : d.group(c.group))
            part.adminPvp = mode.equals("inherit") ? -1 : mode.equals("on") ? 1 : 0;
        d.setDirty();
        return 1;
    }

    public static void openPayments(ServerPlayer p) {
        if (!p.hasPermissions(2) || !Management.valid(p)) return;
        var data = PaymentMenu.snapshot(ClaimData.get(p.server));
        NetworkHooks.openScreen(
                p,
                new SimpleMenuProvider(
                        (id, inv, player) -> new PaymentMenu(id, inv, data),
                        Component.literal("Faction payments")),
                buf -> buf.writeNbt(data));
    }

    static int join(ServerPlayer p, String name) {
        var d = ClaimData.get(p.server);
        if (d.factionOf(p.getUUID()) != null)
            return ClaimService.fail(p, "Leave your current faction first.");
        var f =
                d.factions.values().stream()
                        .filter(
                                v ->
                                        v.name.equalsIgnoreCase(name)
                                                && v.invites.contains(p.getUUID()))
                        .findFirst()
                        .orElse(null);
        if (f == null) return ClaimService.fail(p, "No invitation found for that faction.");
        f.collect(System.currentTimeMillis());
        f.members.add(p.getUUID());
        f.invites.remove(p.getUUID());
        d.setDirty();
        ClaimService.refreshNames(p);
        ClaimService.tell(p, "Joined " + f.name + ".");
        return 1;
    }

    static int kick(ServerPlayer p, UUID other) {
        var f = ClaimService.ownerFaction(p);
        if (f == null) return 0;
        if (f.owner.equals(other)) return ClaimService.fail(p, "The owner cannot be kicked.");
        f.collect(System.currentTimeMillis());
        f.members.remove(other);
        f.managers.remove(other);
        f.invites.remove(other);
        ClaimData.get(p.server).setDirty();
        ClaimService.refreshNames(p);
        return 1;
    }

    static int leave(ServerPlayer p) {
        var d = ClaimData.get(p.server);
        var f = d.factionOf(p.getUUID());
        if (f == null) return 0;
        if (f.owner.equals(p.getUUID()))
            return ClaimService.fail(p, "Transfer ownership before leaving.");
        f.collect(System.currentTimeMillis());
        f.members.remove(p.getUUID());
        f.managers.remove(p.getUUID());
        d.setDirty();
        ClaimService.refreshNames(p);
        return 1;
    }

    static int title(ServerPlayer p, String name) {
        var f = ClaimService.ownerFaction(p);
        if (f == null) return 0;
        name = name.strip();
        if (name.isEmpty() || name.length() > 24)
            return ClaimService.fail(p, "Use 1–24 characters.");
        var d = ClaimData.get(p.server);
        for (var other : d.factions.values())
            if (other != f && other.name.equalsIgnoreCase(name))
                return ClaimService.fail(p, "That name is taken.");
        f.name = name;
        for (var c : d.claims.values()) if (c.faction && c.owner.equals(f.id)) c.name = name;
        d.setDirty();
        ClaimService.refreshNames(p);
        return 1;
    }

    static int info(ServerPlayer p) {
        var d = ClaimData.get(p.server);
        var f = d.factionOf(p.getUUID());
        if (f == null)
            return ClaimService.fail(p, "No faction. Create one with /faction create <name>.");
        ClaimService.tell(
                p,
                f.name
                        + ": "
                        + f.members.size()
                        + " members; "
                        + f.cost()
                        + " "
                        + d.currencyName()
                        + " every "
                        + d.intervalDays
                        + " days. "
                        + ClaimService.status(f, System.currentTimeMillis())
                        + ". Due "
                        + Instant.ofEpochMilli(f.nextDue)
                        + ". Treasury: "
                        + f.balance()
                        + ".");
        return 1;
    }
}
