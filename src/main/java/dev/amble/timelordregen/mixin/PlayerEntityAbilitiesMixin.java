package dev.amble.timelordregen.mixin;

import dev.amble.timelordregen.api.RegenerationInfo;
import dev.amble.timelordregen.api.TimelordAbilities;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 时间领主被动能力 Mixin
 * - 饥饿消耗 -35%
 * - 饱食度恢复 +35%
 * - 常驻 10% 抗性
 * - 生命恢复 1.5 倍
 * - 免疫幻翼（每 2 天重置一次）
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityAbilitiesMixin {

    @Shadow
    public abstract HungerManager getHungerManager();

    @Unique
    private int timelord$regenBoostTimer = 0;

    // ========== 饥饿消耗 -35% ==========
    @ModifyVariable(method = "addExhaustion", at = @At("HEAD"), argsOnly = true)
    private float timelord$modifyExhaustion(float exhaustion) {
        PlayerEntity player = (PlayerEntity)(Object) this;
        if (RegenerationInfo.get(player) != null) {
            return TimelordAbilities.modifyExhaustion(exhaustion);
        }
        return exhaustion;
    }

    // ========== 常驻 10% 抗性 ==========
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float timelord$applyResistance(float amount, DamageSource source) {
        LivingEntity entity = (LivingEntity)(Object) this;
        RegenerationInfo info = RegenerationInfo.get(entity);
        if (info == null) return amount;

        // 常态抗性（非无敌期）
        if (!info.isInvulnerable()) {
            return TimelordAbilities.applyResistance(amount);
        }
        return amount;
    }

    // ========== 生命恢复 1.5x + 幻翼免疫 ==========
    @Inject(method = "tick", at = @At("TAIL"))
    private void timelord$tickAbilities(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity)(Object) this;
        RegenerationInfo info = RegenerationInfo.get(player);
        if (info == null) return;

        // --- 生命恢复加速 1.5x ---
        if (player.getHealth() < player.getMaxHealth()) {
            this.timelord$regenBoostTimer++;
            int boostedInterval = TimelordAbilities.modifyHealthRegenInterval(80);
            if (this.timelord$regenBoostTimer >= boostedInterval) {
                this.timelord$regenBoostTimer = 0;
                // 饥饿度足够时才回血（和原版一致）
                if (player.getHungerManager().getFoodLevel() > 0 ||
                        player.getWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.NATURAL_REGENERATION)) {
                    player.heal(1.0f);
                    if (player.getHungerManager().getFoodLevel() > 0) {
                        player.getHungerManager().addExhaustion(3.0f);
                    }
                }
            }
        } else {
            this.timelord$regenBoostTimer = 0;
        }

        // --- 幻翼免疫：每 2 天重置一次 TIME_SINCE_REST ---
        if (player instanceof ServerPlayerEntity serverPlayer) {
            long worldTime = player.getWorld().getTime();
            if (TimelordAbilities.shouldResetPhantomTimer(serverPlayer, worldTime)) {
                try {
                    Stat<?> stat = Stats.CUSTOM.getOrCreateStat(Stats.TIME_SINCE_REST);
                    serverPlayer.getStatHandler().setStat(serverPlayer, stat, 0);
                } catch (Exception ignored) {}
            }
        }
    }
}