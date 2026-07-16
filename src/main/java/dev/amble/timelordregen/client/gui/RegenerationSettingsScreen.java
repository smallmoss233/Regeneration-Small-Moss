package dev.amble.timelordregen.client.gui;

import dev.amble.lib.skin.SkinData;
import dev.amble.timelordregen.api.RegenerationInfo;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class RegenerationSettingsScreen extends Screen {
    private final PlayerEntity player;
    private final RegenerationInfo info;

    public RegenerationSettingsScreen(PlayerEntity player) {
        super(Text.translatable("gui.regen.settings.title"));
        this.player = player;
        this.info = RegenerationInfo.get(player);
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 重置皮肤按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.regen.settings.reset_skin"),
                button -> {
                    ClientPlayNetworking.send(RegenerationInfo.RESET_SKIN_PACKET, PacketByteBufs.empty());
                    player.sendMessage(Text.translatable("gui.regen.settings.skin_reset"), false);
                }
        ).position(centerX - 100, centerY - 30).size(200, 20).build());

        // 重生换皮开关
        boolean changeSkin = info != null && info.isChangeSkinOnRegen();
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable(changeSkin ? "gui.regen.settings.disable_skin_change" : "gui.regen.settings.enable_skin_change"),
                button -> {
                    if (info == null) return;
                    boolean newValue = !info.isChangeSkinOnRegen();
                    info.setChangeSkinOnRegen(newValue);

                    var buf = PacketByteBufs.create();
                    buf.writeBoolean(newValue);
                    ClientPlayNetworking.send(RegenerationInfo.UPDATE_SKIN_PACKET, buf);

                    button.setMessage(Text.translatable(newValue ? "gui.regen.settings.disable_skin_change" : "gui.regen.settings.enable_skin_change"));
                    player.sendMessage(Text.translatable(newValue ? "gui.regen.settings.skin_change_enabled" : "gui.regen.settings.skin_change_disabled"), false);
                }
        ).position(centerX - 100, centerY).size(200, 20).build());

        // 完成按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.regen.settings.done"),
                button -> this.close()
        ).position(centerX - 50, centerY + 40).size(100, 20).build());
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        this.renderBackground(drawContext);

        int remaining = info != null ? info.getUsesLeft() : 0;
        drawContext.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("gui.regen.settings.remaining", remaining),
                this.width / 2, this.height / 2 - 60, 0xFFFFFF);

        if (info == null) {
            drawContext.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("gui.regen.settings.not_timelord").formatted(Formatting.RED),
                    this.width / 2, this.height / 2 - 45, 0xFFFFFF);
        }

        boolean changeSkin = info != null && info.isChangeSkinOnRegen();
        drawContext.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("gui.regen.settings.skin_change_status",
                        Text.translatable(changeSkin ? "gui.regen.settings.on" : "gui.regen.settings.off")).formatted(Formatting.GRAY),
                this.width / 2, this.height / 2 + 30, 0xFFFFFF);

        super.render(drawContext, mouseX, mouseY, delta);
    }
}