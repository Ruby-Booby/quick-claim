package dev.quickclaim.client;

import net.minecraft.client.gui.GuiGraphics;

final class VanillaPanel {

    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0xFFC6C6C6);
        g.fill(x + 2, y + 2, x + w - 3, y + 4, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, x + 4, y + h - 3, 0xFFFFFFFF);
        g.fill(x + 3, y + h - 4, x + w - 2, y + h - 2, 0xFF555555);
        g.fill(x + w - 4, y + 3, x + w - 2, y + h - 2, 0xFF555555);
    }

    static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x, y, x + 17, y + 17, 0xFF373737);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }
}
