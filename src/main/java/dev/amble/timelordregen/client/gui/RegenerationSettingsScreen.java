package dev.amble.timelordregen.client.gui;

import dev.amble.timelordregen.api.RegenerationInfo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.joml.Vector3f;

public class RegenerationSettingsScreen extends Screen {
    private final PlayerEntity player;
    private final RegenerationInfo info;
    private ColorSlider redSlider;
    private ColorSlider greenSlider;
    private ColorSlider blueSlider;
    private Vector3f color;

    public RegenerationSettingsScreen(PlayerEntity player) {
        super(Text.translatable("gui.regen.settings.title"));
        this.player = player;
        this.info = RegenerationInfo.get(player);

        // FIX: 空安全处理 - 如果不是时间领主，使用默认白色
        if (this.info != null) {
            Vector3f loaded = this.info.getParticleColor();
            this.color = loaded != null ? new Vector3f(loaded) : new Vector3f(1.0f, 1.0f, 1.0f);
        } else {
            this.color = new Vector3f(1.0f, 1.0f, 1.0f);
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        redSlider = new ColorSlider(centerX - 100, centerY - 40, 200, 20,
                Text.translatable("gui.regen.settings.red"), color.x(), this::onColorChange);
        greenSlider = new ColorSlider(centerX - 100, centerY - 10, 200, 20,
                Text.translatable("gui.regen.settings.green"), color.y(), this::onColorChange);
        blueSlider = new ColorSlider(centerX - 100, centerY + 20, 200, 20,
                Text.translatable("gui.regen.settings.blue"), color.z(), this::onColorChange);

        this.addDrawableChild(redSlider);
        this.addDrawableChild(greenSlider);
        this.addDrawableChild(blueSlider);

        // FIX: 本地化按钮文本
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.regen.settings.save_close"), button -> this.saveAndClose())
                .position(centerX - 50, centerY + 60)
                .size(100, 20)
                .build()
        );
    }

    private void onColorChange(float value) {
        color.set(redSlider.getSliderValue(), greenSlider.getSliderValue(), blueSlider.getSliderValue());
        // FIX: 空安全检查后再保存
        if (info != null) {
            info.setParticleColor(color);
        }
    }

    private void saveAndClose() {
        onColorChange(0);
        this.close();
    }

    private class ColorSlider extends SliderWidget {
        private final java.util.function.Consumer<Float> onChange;
        private final String labelKey;

        public ColorSlider(int x, int y, int width, int height, Text label, double value, java.util.function.Consumer<Float> onChange) {
            super(x, y, width, height, label, value);
            this.onChange = onChange;
            this.labelKey = label.getString(); // 存储翻译键
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            // FIX: 使用翻译键 + 动态数值
            this.setMessage(Text.translatable("gui.regen.settings.color_value",
                    Text.translatable(labelKey), (int) (this.value * 255)));
        }

        @Override
        protected void applyValue() {
            onChange.accept((float) this.value);
        }

        public float getSliderValue() {
            return (float) this.value;
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        this.renderBackground(drawContext);

        // FIX: 空安全 + 本地化
        int remaining = info != null ? info.getUsesLeft() : 0;
        drawContext.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("gui.regen.settings.remaining", remaining),
                this.width / 2, this.height / 2 - 70, 0xFFFFFF);

        // FIX: 如果不是时间领主，显示提示
        if (info == null) {
            drawContext.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("gui.regen.settings.not_timelord"),
                    this.width / 2, this.height / 2 - 55, 0xFF5555);
        }

        super.render(drawContext, mouseX, mouseY, delta);
    }
}