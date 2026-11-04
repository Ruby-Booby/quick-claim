package dev.quickclaim.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.quickclaim.*;

import net.minecraft.client.*;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(
        modid = QuickClaim.ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientEvents {

    static final KeyMapping MENU =
            new KeyMapping(
                    "key.quickclaim.menu",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_K,
                    "key.categories.quickclaim");

    static final KeyMapping PERSONAL =
            new KeyMapping(
                    "key.quickclaim.personal",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_P,
                    "key.categories.quickclaim");

    static final KeyMapping FACTION =
            new KeyMapping(
                    "key.quickclaim.faction",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_O,
                    "key.categories.quickclaim");

    static CompoundTag ping;

    static long pingUntil;
    private static final DashboardTransfer dashboardTransfer = new DashboardTransfer();

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(
                () -> MenuScreens.register(QuickClaim.PAYMENT_MENU.get(), PaymentScreen::new));
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(MENU);
        e.register(PERSONAL);
        e.register(FACTION);
    }

    public static void receive(CompoundTag data) {
        if (data == null) return;
        if (data.getString("Kind").startsWith("dashboard")
                && !data.getString("Kind").equals("dashboard")) {
            var completed = dashboardTransfer.accept(data);
            if (completed != null) receive(completed);
            return;
        }
        if (data.getString("Kind").equals("feedback")) {
            if (Minecraft.getInstance().screen instanceof DashboardScreen dashboard)
                dashboard.feedback(data.getString("Message"));
            return;
        }
        if (data.getString("Kind").equals("atlas")) {
            AtlasOverlay.receive(data);
            return;
        }
        if (data.getString("Kind").equals("dashboard")) {
            var screen = new DashboardScreen(data);
            if (Minecraft.getInstance().screen instanceof DashboardScreen previous)
                screen.inherit(previous);
            Minecraft.getInstance().setScreen(screen);
        } else if (data.getString("Kind").equals("ping")) {
            ping = data;
            pingUntil = System.currentTimeMillis() + 15000;
        }
    }

    @Mod.EventBusSubscriber(
            modid = QuickClaim.ID,
            value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Game {

        @SubscribeEvent
        public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
            ping = null;
            pingUntil = 0;
            dashboardTransfer.clear();
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            Minecraft mc = Minecraft.getInstance();
            if (e.phase != TickEvent.Phase.END || mc.player == null) return;
            while (MENU.consumeClick()) Network.CHANNEL.sendToServer(new Network.Action(0));
            while (PERSONAL.consumeClick()) Network.CHANNEL.sendToServer(new Network.Action(4));
            while (FACTION.consumeClick()) Network.CHANNEL.sendToServer(new Network.Action(5));
        }

        @SubscribeEvent
        public static void overlay(RenderGuiEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            if (ping == null
                    || System.currentTimeMillis() > pingUntil
                    || mc.player == null
                    || mc.level == null) return;
            String line =
                    ping.getString("Name")
                            + " • "
                            + ping.getInt("X")
                            + ", "
                            + ping.getInt("Y")
                            + ", "
                            + ping.getInt("Z");
            if (!mc.level.dimension().location().toString().equals(ping.getString("Dimension")))
                line += " • " + ping.getString("Dimension");
            else {
                double dx = ping.getInt("X") + 0.5 - mc.player.getX(),
                        dz = ping.getInt("Z") + 0.5 - mc.player.getZ();
                String direction = dev.quickclaim.core.Heading.toward(dx, dz, mc.player.getYRot());
                line += " • " + (int) Math.hypot(dx, dz) + "m • " + direction;
            }
            int center = mc.getWindow().getGuiScaledWidth() / 2, w = mc.font.width(line);
            e.getGuiGraphics().fill(center - w / 2 - 6, 24, center + w / 2 + 6, 42, 0xAA000000);
            e.getGuiGraphics().drawCenteredString(mc.font, line, center, 29, 0xFFFFFF55);
        }
    }
}
