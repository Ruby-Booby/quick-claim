package dev.quickclaim.client;

import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;

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
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = QuickClaim.ID, value = Dist.CLIENT)
public final class AtlasOverlay {
    private static final String SCREEN = "pepjebs.mapatlases.client.screen.AtlasOverviewScreen";
    private static final List<CompoundTag> pending = new ArrayList<>();
    private static final Map<Class<?>, SliceAccess> access = new HashMap<>();
    private static List<CompoundTag> claims = List.of();
    private static boolean failed;
    private static int ticks;
    private static String hovered;

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
        hovered = null;
        ticks = 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || Minecraft.getInstance().player == null
                || !ModList.get().isLoaded("map_atlases")) return;
        if (ticks-- <= 0) {
            ticks = 99;
            Network.CHANNEL.sendToServer(new Network.Action(6));
        }
    }

    @SubscribeEvent
    public static void beforeScreen(ScreenEvent.Render.Pre event) {
        hovered = null;
    }

    @SubscribeEvent
    public static void afterScreen(ScreenEvent.Render.Post event) {
        if (hovered != null && event.getScreen().getClass().getName().equals(SCREEN))
            event.getGuiGraphics()
                    .renderTooltip(
                            Minecraft.getInstance().font,
                            Component.literal(hovered),
                            event.getMouseX(),
                            event.getMouseY());
    }

    public static void draw(
            Object widget,
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            double centerX,
            double centerZ,
            double scale,
            float rotation) {
        if (failed || claims.isEmpty() || !Double.isFinite(scale) || scale <= 0) return;
        try {
            var adapter = access.get(widget.getClass());
            if (adapter == null) {
                adapter = new SliceAccess(widget.getClass());
                access.put(widget.getClass(), adapter);
            }
            Object slice = adapter.slice(widget);
            if (slice == null) return;
            String dimension =
                    ((ResourceKey<?>) adapter.dimension.invoke(slice)).location().toString();
            Integer mapHeight = (Integer) adapter.height.invoke(slice);
            render(
                    graphics,
                    x,
                    y,
                    width,
                    height,
                    centerX,
                    centerZ,
                    scale,
                    rotation,
                    dimension,
                    mapHeight,
                    adapter.worldMap);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            failed = true;
            LogUtils.getLogger().warn("Unable to draw claims on this Atlas version", exception);
            var player = Minecraft.getInstance().player;
            if (player != null)
                player.displayClientMessage(
                        Component.literal(
                                "Quick Claim: claim borders are unavailable for this Atlas"
                                    + " version."),
                        false);
        }
    }

    private static void render(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            double centerX,
            double centerZ,
            double scale,
            float rotation,
            String dimension,
            Integer mapHeight,
            boolean worldMap) {
        var minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        float mouseX =
                (float)
                        (minecraft.mouseHandler.xpos()
                                * window.getGuiScaledWidth()
                                / window.getScreenWidth());
        float mouseY =
                (float)
                        (minecraft.mouseHandler.ypos()
                                * window.getGuiScaledHeight()
                                / window.getScreenHeight());
        var mouse =
                new Matrix4f(graphics.pose().last().pose())
                        .invert()
                        .transformPosition(new Vector3f(mouseX, mouseY, 0));
        boolean inside =
                worldMap
                        && mouse.x >= x
                        && mouse.x < x + width
                        && mouse.y >= y
                        && mouse.y < y + height;
        graphics.pose().pushPose();
        graphics.pose().translate(x + width / 2f, y + height / 2f, 10);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        mouse =
                new Matrix4f(graphics.pose().last().pose())
                        .invert()
                        .transformPosition(new Vector3f(mouseX, mouseY, 0));
        double radius = Math.hypot(width, height) / (2 * scale);
        try {
            for (var claim : claims) {
                int[] b = claim.getIntArray("Bounds");
                if (b.length != 6
                        || !dimension.equals(claim.getString("Dimension"))
                        || (mapHeight != null && (mapHeight < b[1] || mapHeight > b[4]))
                        || b[3] + 1d < centerX - radius
                        || b[0] > centerX + radius
                        || b[5] + 1d < centerZ - radius
                        || b[2] > centerZ + radius) continue;
                int x1 = pixel((b[0] - centerX) * scale), y1 = pixel((b[2] - centerZ) * scale);
                int x2 = Math.max(x1 + 1, pixel(Math.ceil((b[3] + 1d - centerX) * scale)));
                int y2 = Math.max(y1 + 1, pixel(Math.ceil((b[5] + 1d - centerZ) * scale)));
                int color = claim.getBoolean("Protected") ? claim.getInt("Color") : 0xFF999999;
                graphics.fill(x1, y1, x2, y2, (color & 0xFFFFFF) | 0x22000000);
                graphics.fill(x1, y1, x2, y1 + 1, color);
                graphics.fill(x1, y2 - 1, x2, y2, color);
                graphics.fill(x1, y1, x1 + 1, y2, color);
                graphics.fill(x2 - 1, y1, x2, y2, color);
                if (claim.getBoolean("Home")) {
                    int cx = pixel(((b[0] + (double) b[3] + 1) / 2 - centerX) * scale);
                    int cy = pixel(((b[2] + (double) b[5] + 1) / 2 - centerZ) * scale);
                    graphics.pose().pushPose();
                    graphics.pose().translate(cx, cy, 1);
                    graphics.pose().mulPose(Axis.ZP.rotationDegrees(-rotation));
                    graphics.fill(-3, -2, 4, 4, 0xFF29232F);
                    graphics.fill(-2, -1, 3, 3, color);
                    graphics.fill(-1, -4, 2, -3, color);
                    graphics.fill(-2, -3, 3, -2, color);
                    graphics.pose().popPose();
                }
                if (inside && mouse.x >= x1 && mouse.x < x2 && mouse.y >= y1 && mouse.y < y2)
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
        }
    }

    private static int pixel(double value) {
        return (int) Math.floor(Math.max(-1_000_000, Math.min(1_000_000, value)));
    }

    private static final class SliceAccess {
        private final Field parent;
        private final Method selected, dimension, height;
        private final boolean worldMap;

        SliceAccess(Class<?> widget) throws ReflectiveOperationException {
            Field source = findField(widget, "mapScreen");
            worldMap = source != null;
            parent = worldMap ? source : findField(widget, "currentMapKey");
            if (parent == null) throw new NoSuchFieldException(widget.getName() + " map context");
            parent.setAccessible(true);
            selected = parent.getType().getMethod(worldMap ? "getSelectedSlice" : "slice");
            dimension = selected.getReturnType().getMethod("dimension");
            height = selected.getReturnType().getMethod("height");
        }

        private static Field findField(Class<?> type, String name) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            return null;
        }

        Object slice(Object widget) throws ReflectiveOperationException {
            var value = parent.get(widget);
            return value == null ? null : selected.invoke(value);
        }
    }
}
