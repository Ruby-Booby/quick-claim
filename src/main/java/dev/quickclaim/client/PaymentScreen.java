package dev.quickclaim.client;

import dev.quickclaim.Network;
import dev.quickclaim.PaymentMenu;
import dev.quickclaim.core.NumberRange;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;

public final class PaymentScreen extends AbstractContainerScreen<PaymentMenu> {
    private static final NumberRange AMOUNT = new NumberRange(1, 10000);
    private static final NumberRange DAYS = new NumberRange(1, 365);

    private ValueSlider amountSlider;
    private ValueSlider daysSlider;
    private ValueSlider draggingSlider;
    private EditBox amountInput;
    private EditBox daysInput;
    private Button saveButton;

    public PaymentScreen(PaymentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 230;
        imageHeight = 222;
        inventoryLabelX = 34;
        inventoryLabelY = 124;
    }

    @Override
    protected void init() {
        super.init();
        draggingSlider = null;
        amountSlider =
                addRenderableWidget(
                        new ValueSlider(
                                leftPos + 40,
                                topPos + 25,
                                120,
                                "Amount",
                                AMOUNT,
                                menu.amount,
                                value -> amountInput.setValue(Integer.toString(value))));
        daysSlider =
                addRenderableWidget(
                        new ValueSlider(
                                leftPos + 40,
                                topPos + 52,
                                120,
                                "Days",
                                DAYS,
                                menu.days,
                                value -> daysInput.setValue(Integer.toString(value))));
        amountInput =
                numberField(
                        topPos + 25,
                        menu.amount,
                        AMOUNT,
                        amountSlider,
                        value -> menu.amount = value);
        daysInput =
                numberField(topPos + 52, menu.days, DAYS, daysSlider, value -> menu.days = value);
        saveButton =
                addRenderableWidget(
                        Button.builder(
                                        Component.literal("Save payments"),
                                        button -> {
                                            var amount = AMOUNT.parse(amountInput.getValue());
                                            var days = DAYS.parse(daysInput.getValue());
                                            if (amount.isPresent() && days.isPresent()) {
                                                Network.CHANNEL.sendToServer(
                                                        new Network.SavePayments(
                                                                menu.containerId,
                                                                amount.getAsInt(),
                                                                days.getAsInt(),
                                                                menu.revision));
                                            }
                                        })
                                .bounds(leftPos + 8, topPos + 99, 214, 20)
                                .build());
        validateInputs();
    }

    private EditBox numberField(
            int y, int current, NumberRange range, ValueSlider slider, IntConsumer changed) {
        var field = new EditBox(font, leftPos + 168, y, 54, 20, Component.literal("Exact value"));
        field.setMaxLength(5);
        field.setFilter(text -> text.matches("[0-9]*"));
        field.setValue(Integer.toString(current));
        field.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "Type a whole number from " + range.min() + " to " + range.max())));
        field.setResponder(
                text -> {
                    var parsed = range.parse(text);
                    field.setTextColor(parsed.isPresent() ? 0xE0E0E0 : 0xFF7777);
                    parsed.ifPresent(
                            value -> {
                                slider.setNumber(value);
                                changed.accept(value);
                            });
                    validateInputs();
                });
        return addRenderableWidget(field);
    }

    private void validateInputs() {
        if (saveButton != null && amountInput != null && daysInput != null) {
            saveButton.active =
                    AMOUNT.parse(amountInput.getValue()).isPresent()
                            && DAYS.parse(daysInput.getValue()).isPresent();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingSlider =
                    amountSlider.isMouseOver(mouseX, mouseY)
                            ? amountSlider
                            : daysSlider.isMouseOver(mouseX, mouseY) ? daysSlider : null;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingSlider != null) {
            return draggingSlider.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        amountInput.tick();
        daysInput.tick();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        VanillaPanel.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        VanillaPanel.slot(graphics, leftPos + 11, topPos + 28);
        if (!menu.sample.isEmpty()) graphics.renderItem(menu.sample, leftPos + 12, topPos + 29);
        for (var slot : menu.slots) {
            VanillaPanel.slot(graphics, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, "Click inventory to choose currency", 8, 82, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partial);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + 11
                && mouseX < leftPos + 29
                && mouseY >= topPos + 28
                && mouseY < topPos + 46
                && !menu.sample.isEmpty()) {
            graphics.renderTooltip(font, menu.sample, mouseX, mouseY);
        }
    }

    static final class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final NumberRange range;
        private final IntConsumer changed;

        ValueSlider(
                int x,
                int y,
                int width,
                String label,
                NumberRange range,
                int current,
                IntConsumer changed) {
            super(x, y, width, 20, Component.empty(), range.fraction(current));
            this.label = label;
            this.range = range;
            this.changed = changed;
            updateMessage();
        }

        void setNumber(int number) {
            value = range.fraction(number);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + range.number(value)));
        }

        @Override
        protected void applyValue() {
            changed.accept(range.number(value));
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
                int step = key == GLFW.GLFW_KEY_LEFT ? -1 : 1;
                setNumber(range.number(value) + step);
                applyValue();
                return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }
    }
}
