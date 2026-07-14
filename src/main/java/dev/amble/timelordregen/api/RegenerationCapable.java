package dev.amble.timelordregen.api;

import dev.amble.timelordregen.data.Attachments;
import net.minecraft.entity.LivingEntity;

import java.util.Optional;

public interface RegenerationCapable {
    default RegenerationInfo getRegenerationInfo() {
        if (!(this instanceof LivingEntity living)) throw new UnsupportedOperationException("This method is only default for LivingEntity instances. Override it and implement it");

        return getLivingInfo(living);
    }

    default Optional<RegenerationInfo> withInfo() {
        return Optional.ofNullable(this.getRegenerationInfo());
    }

    default void tickRegeneration() {
        RegenerationInfo info = this.getRegenerationInfo();
        if (info != null) {
            if (!(this instanceof LivingEntity living)) throw new UnsupportedOperationException("This method is only default for LivingEntity instances. Override it and implement it");

            info.tick(living);
        }
    }

    /**
     * 检查该实体是否为时间领主。
     * 默认实现返回 true（兼容已有实现）。
     * PlayerEntityMixin 会覆盖此方法，通过 NBT 标记判断。
     */
    default boolean isTimelord() {
        return true;
    }

    /**
     * 设置该实体是否为时间领主。
     * 默认实现为空操作（非玩家实体通常不需要切换）。
     * PlayerEntityMixin 会覆盖此方法。
     */
    default void setTimelord(boolean timelord) {
        // 默认空实现，非玩家实体不需要切换时间领主状态
    }

    static RegenerationInfo getLivingInfo(LivingEntity entity) {
        return entity.getAttachedOrCreate(Attachments.REGENERATION);
    }
}