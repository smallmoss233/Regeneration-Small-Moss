package dev.amble.timelordregen.api;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * 时间领主被动能力系统
 * 包含：饥饿消耗减免、饱食度恢复加成、常驻抗性、生命恢复加速、免疫幻翼
 */
public class TimelordAbilities {

    // ========== 常量配置 ==========
    private static final float HUNGER_EXHAUSTION_MULTIPLIER = 0.65f;   // 饥饿消耗 -35%
    private static final float SATURATION_REGEN_MULTIPLIER = 1.35f;     // 饱食度恢复 +35%
    private static final float RESISTANCE_PERCENT = 0.10f;               // 常驻 10% 伤害减免
    private static final float HEALTH_REGEN_MULTIPLIER = 1.5f;          // 生命恢复 1.5 倍 (比普通快 0.5 倍)

    // 幻翼免疫：每 2 天 (48000 ticks) 重置一次 TIME_SINCE_REST
    // 原版幻翼在 3 天 (72000 ticks) 不睡觉时生成
    // 我们在第 2 天重置，给 1 天缓冲
    private static final int PHANTOM_RESET_INTERVAL = 48000; // 2 天

    // ========== 饥饿相关 ==========

    /**
     * 修改饥饿消耗量
     */
    public static float modifyExhaustion(float original) {
        return original * HUNGER_EXHAUSTION_MULTIPLIER;
    }

    /**
     * 修改饱食度恢复量
     */
    public static float modifySaturationRegen(float original) {
        return original * SATURATION_REGEN_MULTIPLIER;
    }

    // ========== 抗性相关 ==========

    /**
     * 应用常驻抗性减免
     */
    public static float applyResistance(float original) {
        return original * (1.0f - RESISTANCE_PERCENT);
    }

    // ========== 生命恢复相关 ==========

    /**
     * 修改生命恢复间隔
     * 原版约 80 ticks 回 1 血，1.5 倍速 = 约 53 ticks
     */
    public static int modifyHealthRegenInterval(int originalInterval) {
        return MathHelper.ceil(originalInterval / HEALTH_REGEN_MULTIPLIER);
    }

    // ========== 幻翼免疫相关 ==========

    /**
     * 检查是否应该重置幻翼计时器
     * @param player 玩家
     * @param worldTime 当前世界时间
     * @return 是否需要重置
     */
    public static boolean shouldResetPhantomTimer(ServerPlayerEntity player, long worldTime) {
        // 每 2 天重置一次
        return worldTime % PHANTOM_RESET_INTERVAL == 0;
    }

    /**
     * 检查时间领主是否应该生成幻翼
     */
    public static boolean canSpawnPhantoms(PlayerEntity player) {
        RegenerationInfo info = RegenerationInfo.get(player);
        if (info == null) return true;
        return false;
    }
}