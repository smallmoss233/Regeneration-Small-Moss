package dev.amble.timelordregen.api;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * 时间领主被动能力系统
 */
public class TimelordAbilities {

    private static final float HUNGER_EXHAUSTION_MULTIPLIER = 0.65f;
    private static final float RESISTANCE_PERCENT = 0.10f;
    private static final float HEALTH_REGEN_MULTIPLIER = 1.5f;
    private static final int PHANTOM_RESET_INTERVAL = 48000;

    public static float modifyExhaustion(float original) {
        return original * HUNGER_EXHAUSTION_MULTIPLIER;
    }

    public static float applyResistance(float original) {
        return original * (1.0f - RESISTANCE_PERCENT);
    }

    public static int modifyHealthRegenInterval(int originalInterval) {
        return MathHelper.ceil(originalInterval / HEALTH_REGEN_MULTIPLIER);
    }

    public static boolean shouldResetPhantomTimer(ServerPlayerEntity player, long worldTime) {
        return worldTime % PHANTOM_RESET_INTERVAL == 0;
    }

    // 氧气消耗：75% 速度，每 4 tick 跳过 1 次
    public static boolean shouldDecreaseAirSupply(int tickCount) {
        return tickCount % 4 != 0;
    }
}