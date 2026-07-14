package dev.amble.timelordregen.client;

import dev.amble.timelordregen.api.RegenerationInfo;
import dev.amble.timelordregen.client.gui.RegenerationSettingsScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public class ClientNetworking {
    public static final Identifier OPEN_GUI_PACKET = new Identifier("timelordregen", "open_gui");

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(OPEN_GUI_PACKET, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                // FIX: 空安全检查 - 不是时间领主时不打开设置界面
                var player = MinecraftClient.getInstance().player;
                if (player == null) return;

                RegenerationInfo info = RegenerationInfo.get(player);
                if (info == null) {
                    // 不是时间领主，可以显示提示或静默忽略
                    // 也可以打开一个提示界面
                    return;
                }

                MinecraftClient.getInstance().setScreen(new RegenerationSettingsScreen(player));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(RegenerationInfo.SYNC_PACKET, (client, handler, buf, responseSender) -> {
            RegenerationInfo.receive(buf);
        });
    }
}