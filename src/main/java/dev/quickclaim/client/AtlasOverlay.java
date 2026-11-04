package dev.quickclaim.client;

import com.mojang.logging.LogUtils;

import dev.quickclaim.Network;
import dev.quickclaim.QuickClaim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.joml.Vector4d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = QuickClaim.ID, value = Dist.CLIENT)
public final class AtlasOverlay {
    private static final String SCREEN = "pepjebs.mapatlases.client.screen.AtlasOverviewScreen";
    private static final List<CompoundTag> pending = new ArrayList<>();
    private static List<CompoundTag> claims = List.of();
    private static Access access;
    private static boolean failed;
    private static int ticks;
    private static Object previousScreen;

    private AtlasOverlay() {}

    public static void receive(CompoundTag packet) {
        if (packet.getBoolean("First")) pending.clear();
        for (Tag tag : packet.getList("Claims", Tag.TAG_COMPOUND)) pending.add((CompoundTag) tag);
        if (packet.getBoolean("Last")) {
            claims = List.copyOf(pending);
            pending.clear();
        }
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        pending.clear();
        claims = List.of();
        previousScreen = null;
        ticks = 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var screen = Minecraft.getInstance().screen;
        if (event.phase != TickEvent.Phase.END) return;
        if (screen == null || !screen.getClass().getName().equals(SCREEN)) {
            previousScreen = null;
            return;
        }
        if (screen != previousScreen || ++ticks >= 100) {
            previousScreen = screen;
            ticks = 0;
            Network.CHANNEL.sendToServer(new Network.Action(6));
        }
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post event) {
        var screen = event.getScreen();
        if (failed || !screen.getClass().getName().equals(SCREEN)) return;
        try {
            if (access == null) access = new Access(screen.getClass());
            access.draw(screen, event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            failed = true;
            LogUtils.getLogger()
                    .warn(
                            "Atlas claim overlay is unavailable for this Map Atlases version",
                            exception);
            var player = Minecraft.getInstance().player;
            if (player != null)
                player.displayClientMessage(
                        Component.literal(
                                "Quick Claim: this Atlas version does not support the claim"
                                        + " overlay."),
                        false);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                var field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static final class Access {
        private final Field widget, x, y, width, height, centerX, centerZ, zoom, blocks;
        private final Method transform, slice, dimension, sliceHeight, editing;

        Access(Class<?> screen) throws ReflectiveOperationException {
            widget = field(screen, "mapWidget");
            var type = widget.getType();
            x = field(type, "x");
            y = field(type, "y");
            width = field(type, "width");
            height = field(type, "height");
            centerX = field(type, "currentXCenter");
            centerZ = field(type, "currentZCenter");
            zoom = field(type, "zoomLevel");
            blocks = field(type, "mapBlocksSize");
            transform = screen.getMethod("transformPos", double.class, double.class);
            slice = screen.getMethod("getSelectedSlice");
            dimension = slice.getReturnType().getMethod("dimension");
            sliceHeight = slice.getReturnType().getMethod("height");
            editing = screen.getMethod("isEditingText");
        }

        void draw(Object screen, GuiGraphics graphics, int mouseX, int mouseY)
                throws ReflectiveOperationException {
            var map = widget.get(screen);
            if (map == null || (boolean) editing.invoke(screen)) return;
            var selectedSlice = slice.invoke(screen);
            String dimensionId =
                    ((ResourceKey<?>) dimension.invoke(selectedSlice)).location().toString();
            Integer mapHeight = (Integer) sliceHeight.invoke(selectedSlice);
            double left = x.getInt(map),
                    top = y.getInt(map),
                    w = width.getInt(map),
                    h = height.getInt(map);
            double scale = w / (zoom.getFloat(map) * blocks.getInt(map));
            if (!Double.isFinite(scale) || scale <= 0) return;
            var start = (Vector4d) transform.invoke(screen, left, top);
            var end = (Vector4d) transform.invoke(screen, left + w, top + h);
            String hovered = null;
            graphics.enableScissor((int) start.x, (int) start.y, (int) end.x, (int) end.y);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400);
            try {
                for (var claim : claims) {
                    if (!claim.getString("Dimension").equals(dimensionId)) continue;
                    int[] b = claim.getIntArray("Bounds");
                    if (b.length != 6
                            || (mapHeight != null && (mapHeight < b[1] || mapHeight > b[4])))
                        continue;
                    var a =
                            (Vector4d)
                                    transform.invoke(
                                            screen,
                                            left + w / 2 + (b[0] - centerX.getDouble(map)) * scale,
                                            top + h / 2 + (b[2] - centerZ.getDouble(map)) * scale);
                    var z =
                            (Vector4d)
                                    transform.invoke(
                                            screen,
                                            left
                                                    + w / 2
                                                    + (b[3] + 1d - centerX.getDouble(map)) * scale,
                                            top
                                                    + h / 2
                                                    + (b[5] + 1d - centerZ.getDouble(map)) * scale);
                    if (z.x < start.x || z.y < start.y || a.x > end.x || a.y > end.y) continue;
                    int x1 = (int) Math.floor(a.x),
                            y1 = (int) Math.floor(a.y),
                            x2 = Math.max(x1 + 1, (int) Math.ceil(z.x)),
                            y2 = Math.max(y1 + 1, (int) Math.ceil(z.y));
                    int color = claim.getBoolean("Protected") ? claim.getInt("Color") : 0xFF999999;
                    graphics.fill(x1, y1, x2, y2, (color & 0xFFFFFF) | 0x22000000);
                    graphics.fill(x1, y1, x2, y1 + 1, color);
                    graphics.fill(x1, y2 - 1, x2, y2, color);
                    graphics.fill(x1, y1, x1 + 1, y2, color);
                    graphics.fill(x2 - 1, y1, x2, y2, color);
                    if (claim.getBoolean("Home")) {
                        int cx = (x1 + x2) / 2, cy = (y1 + y2) / 2;
                        graphics.fill(cx - 3, cy - 2, cx + 4, cy + 4, 0xFF29232F);
                        graphics.fill(cx - 2, cy - 1, cx + 3, cy + 3, color);
                        graphics.fill(cx - 1, cy - 4, cx + 2, cy - 3, color);
                        graphics.fill(cx - 2, cy - 3, cx + 3, cy - 2, color);
                    }
                    if (mouseX >= Math.max(a.x, start.x)
                            && mouseX < Math.min(z.x, end.x)
                            && mouseY >= Math.max(a.y, start.y)
                            && mouseY < Math.min(z.y, end.y))
                        hovered =
                                claim.getString("Name")
                                        + " | Y "
                                        + b[1]
                                        + " to "
                                        + b[4]
                                        + (claim.getBoolean("Home") ? " | Home" : "")
                                        + (claim.getBoolean("Protected") ? "" : " | Unprotected");
                }
            } finally {
                graphics.pose().popPose();
                graphics.disableScissor();
            }
            if (hovered != null)
                graphics.renderTooltip(
                        Minecraft.getInstance().font, Component.literal(hovered), mouseX, mouseY);
        }
    }
}
