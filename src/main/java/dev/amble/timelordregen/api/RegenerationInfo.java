package dev.amble.timelordregen.api;

import dev.amble.lib.client.bedrock.BedrockAnimationReference;
import dev.amble.lib.skin.SkinData;
import dev.amble.timelordregen.RegenerationMod;
import dev.amble.timelordregen.animation.AnimationSet;
import dev.amble.timelordregen.animation.AnimationTemplate;
import dev.amble.timelordregen.animation.RegenAnimRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.lib.animation.AnimatedEntity;
import dev.amble.lib.animation.AnimationTracker;
import dev.amble.timelordregen.data.Attachments;
import dev.drtheo.scheduler.api.TimeUnit;
import dev.drtheo.scheduler.api.common.Scheduler;
import dev.drtheo.scheduler.api.common.TaskStage;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RegenerationInfo {
    public static final Identifier SYNC_PACKET = RegenerationMod.id("sync_info");
    public static final Identifier UPDATE_SKIN_PACKET = RegenerationMod.id("update_skin_setting");
    public static final Identifier RESET_SKIN_PACKET = RegenerationMod.id("reset_skin");

    public static void init() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            List<Entity> entities = new ArrayList<>(server.getPlayerManager().getPlayerList());
            server.getWorlds().forEach(world -> world.iterateEntities().forEach(entities::add));
            entities.forEach(entity -> {
                if (!(entity instanceof RegenerationCapable regen) || !(entity instanceof LivingEntity living)) return;
                RegenerationInfo info = regen.getRegenerationInfo();
                if (info != null && info.isRegenerating()) {
                    info.finish(living);
                }
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity entity = handler.getPlayer();
            if (!(entity instanceof RegenerationCapable regen)) return;
            RegenerationInfo info = regen.getRegenerationInfo();
            if (info != null && info.isRegenerating()) {
                info.setRegenQueued(true);
                info.stopRegeneration(entity);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity entity = handler.getPlayer();
            if (!(entity instanceof RegenerationCapable regen)) return;
            RegenerationInfo info = regen.getRegenerationInfo();
            if (info != null && info.isRegenQueued()) {
                if (!info.start(entity)) {
                    info.setRegenQueued(false);
                    info.markDirty();
                }
            }
        });

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            RegenerationInfo info = RegenerationInfo.get(entity);
            if (info == null) return true;
            if (!info.isActive() && info.getUsesLeft() > 0) {
                return !info.tryStart(entity);
            }
            return true;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            RegenerationInfo info = RegenerationInfo.get(player);
            if (info != null && info.getDelay().hasEvent()) {
                if (!world.getBlockState(pos).isIn(BlockTags.SNOW)) return ActionResult.PASS;
                info.tryStopDelayEvent(player);
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, player.getSoundCategory(), 0.25F, 1.0F);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            RegenerationInfo info = RegenerationInfo.get(player);
            if (info != null && info.isSkinReset()) {
                clearPlayerSkin(player);
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(UPDATE_SKIN_PACKET, (server, player, handler, buf, responseSender) -> {
            boolean changeSkin = buf.readBoolean();
            server.execute(() -> {
                RegenerationInfo info = RegenerationInfo.get(player);
                if (info != null) {
                    info.setChangeSkinOnRegen(changeSkin);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RESET_SKIN_PACKET, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                RegenerationInfo info = RegenerationInfo.get(player);
                if (info != null) {
                    info.setSkinReset(true);
                }
                clearPlayerSkin(player);
                player.sendMessage(Text.translatable("gui.regen.settings.skin_reset"), false);
            });
        });
    }

    private static void clearPlayerSkin(ServerPlayerEntity player) {
        player.getGameProfile().getProperties().removeAll("textures");

        var packet = new net.minecraft.network.packet.s2c.play.PlayerListS2CPacket(
                net.minecraft.network.packet.s2c.play.PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME,
                player
        );
        player.getWorld().getServer().getPlayerManager().sendToAll(packet);

        boolean isAlex = (player.getUuid().hashCode() & 1) == 1;
        try {
            SkinData.usernameUpload(isAlex ? "Alex" : "Steve", player.getUuid());
        } catch (Exception ignored) {}
    }

    public static final Codec<RegenerationInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("usesLeft").forGetter(RegenerationInfo::getUsesLeft),
            Codec.BOOL.fieldOf("isRegenerating").forGetter(RegenerationInfo::isRegenerating),
            Codec.BOOL.fieldOf("regenQueued").forGetter(RegenerationInfo::isRegenQueued),
            Identifier.CODEC.fieldOf("animation").forGetter(RegenerationInfo::getAnimationId),
            Delay.CODEC.fieldOf("delay").forGetter(info -> info.delay),
            Codec.FLOAT.fieldOf("colorR").forGetter(info -> info.particleColor.x()),
            Codec.FLOAT.fieldOf("colorG").forGetter(info -> info.particleColor.y()),
            Codec.FLOAT.fieldOf("colorB").forGetter(info -> info.particleColor.z()),
            Codec.LONG.optionalFieldOf("invulnerableUntil", -1L).forGetter(RegenerationInfo::getInvulnerableUntil),
            Codec.LONG.optionalFieldOf("confusedUntil", -1L).forGetter(RegenerationInfo::getConfusedUntil),
            Codec.BOOL.optionalFieldOf("changeSkinOnRegen", true).forGetter(RegenerationInfo::isChangeSkinOnRegen),
            Codec.BOOL.optionalFieldOf("skinReset", false).forGetter(RegenerationInfo::isSkinReset)
    ).apply(instance, (usesLeft, isRegenerating, regenQueued, animationId, delay, r, g, b, invulnUntil, confUntil, changeSkin, skinReset) -> {
        RegenerationInfo info = new RegenerationInfo(usesLeft, isRegenerating, regenQueued, animationId, delay);
        info.particleColor.set(r, g, b);
        info.invulnerableUntil = invulnUntil;
        info.confusedUntil = confUntil;
        info.changeSkinOnRegen = changeSkin;
        info.skinReset = skinReset;
        return info;
    }));

    public static final int MAX_REGENERATIONS = 12;
    private static final int INVULNERABLE_DURATION = 24000;
    private static final int CONFUSION_MIN_TICKS = 1200;
    private static final int CONFUSION_MAX_EXTRA_TICKS = 3601;
    private static final int CONFUSION_EFFECT_INTERVAL_MIN = 100;
    private static final int CONFUSION_EFFECT_INTERVAL_MAX = 300;
    private static final float MIN_DAMAGE_CAP = 0.1f;
    private static final float REGEN_BOOST_MULTIPLIER = 3.0f;

    private int usesLeft;
    private boolean isRegenerating;
    private boolean regenQueued;
    private AnimationTemplate animation;
    private final Delay delay;
    private boolean dirty;
    private final Vector3f particleColor;
    @Nullable
    private AnimationSet currentAnimationSet;

    private long invulnerableUntil;
    private long confusedUntil;
    private int confusionEffectTimer;
    private int regenBoostTimer;

    private boolean changeSkinOnRegen = true;
    private boolean skinReset = false;

    private RegenerationInfo(int usesLeft, boolean isRegenerating, boolean regenQueued, Identifier animation, Delay delay) {
        this.usesLeft = usesLeft;
        this.isRegenerating = isRegenerating;
        this.regenQueued = regenQueued;
        this.animation = RegenAnimRegistry.getInstance().getOrFallback(animation);
        this.delay = delay;
        this.particleColor = new Vector3f(1.0f, 1.0f, 1.0f);
        this.invulnerableUntil = -1;
        this.confusedUntil = -1;
        this.confusionEffectTimer = 0;
        this.regenBoostTimer = 0;
    }

    public RegenerationInfo() {
        this(0, false, false, RegenAnimRegistry.getInstance().getRandom().id(), new Delay());
    }

    public int getUsesLeft() { return usesLeft; }
    public void setUsesLeft(int usesLeft) {
        this.usesLeft = MathHelper.clamp(usesLeft, 0, MAX_REGENERATIONS);
        this.markDirty();
    }

    public boolean isRegenerating() { return isRegenerating; }
    public void setRegenerating(boolean regenerating) {
        isRegenerating = regenerating;
        this.markDirty();
    }

    public boolean isRegenQueued() { return regenQueued; }
    public void setRegenQueued(boolean regenQueued) {
        this.regenQueued = regenQueued;
        this.markDirty();
    }

    public AnimationTemplate getAnimation() {
        if (this.animation == null) {
            this.animation = RegenAnimRegistry.getInstance().getRandom();
        }
        return animation;
    }

    public void setAnimation(AnimationTemplate animation) {
        this.animation = animation;
        this.markDirty();
    }

    public Delay getDelay() { return delay; }

    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }

    public Vector3f getParticleColor() { return particleColor; }
    public void setParticleColor(Vector3f color) {
        this.particleColor.set(color);
        this.markDirty();
    }

    public long getInvulnerableUntil() { return invulnerableUntil; }
    public long getConfusedUntil() { return confusedUntil; }

    public boolean isInvulnerable() { return this.invulnerableUntil > 0; }
    public boolean isConfused() { return this.confusedUntil > 0; }

    public boolean isChangeSkinOnRegen() { return changeSkinOnRegen; }
    public void setChangeSkinOnRegen(boolean value) {
        this.changeSkinOnRegen = value;
        this.markDirty();
    }

    public boolean isSkinReset() { return skinReset; }
    public void setSkinReset(boolean value) {
        this.skinReset = value;
        this.markDirty();
    }

    public void decrement() {
        this.setUsesLeft(this.getUsesLeft() - 1);
    }

    public void tick(LivingEntity entity) {
        if (entity.getWorld().isClient) return;

        if (this.isDirty()) {
            this.setDirty(false);
            for (ServerPlayerEntity player : entity.getWorld().getServer().getPlayerManager().getPlayerList()) {
                this.sync(player, entity.getUuid());
            }
        }

        long worldTime = entity.getWorld().getTime();

        this.tickInvulnerability(entity, worldTime);
        this.tickConfusion(entity, worldTime);

        if (delay.isRunning()) {
            if (this.getUsesLeft() <= 0) {
                delay.stop();
                this.markDirty();
                return;
            }
            Delay.Result result = delay.tick(entity.age);
            switch (result) {
                case REGENERATE -> {
                    this.setRegenQueued(true);
                    delay.stop();
                    this.markDirty();
                }
                case EVENT -> {
                    RegenerationEvents.DELAY_EVENT.invoker().onEvent(entity, this);
                    this.markDirty();
                }
                case NONE -> {}
            }
        }

        if (isRegenQueued()) {
            if (!this.start(entity)) {
                this.setRegenQueued(false);
                this.markDirty();
                RegenerationMod.LOGGER.warn("Regeneration start failed for {}, clearing queued state", entity.getUuid());
            }
        }
    }

    private void tickInvulnerability(LivingEntity entity, long worldTime) {
        if (this.invulnerableUntil <= 0) return;

        if (worldTime >= this.invulnerableUntil) {
            this.invulnerableUntil = -1;
            this.markDirty();
            RegenerationMod.LOGGER.info("Invulnerability ended for {}", entity.getUuid());
            return;
        }

        this.tickRegenBoost(entity);
    }

    private void tickRegenBoost(LivingEntity entity) {
        if (entity.getHealth() >= entity.getMaxHealth()) return;
        if (!(entity instanceof PlayerEntity)) return;

        this.regenBoostTimer++;
        int boostedInterval = (int) (80 / REGEN_BOOST_MULTIPLIER);
        if (this.regenBoostTimer >= boostedInterval) {
            this.regenBoostTimer = 0;
            PlayerEntity player = (PlayerEntity) entity;
            if (player.getHungerManager().getFoodLevel() > 0 || player.getWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.NATURAL_REGENERATION)) {
                entity.heal(1.0f);
                if (player.getHungerManager().getFoodLevel() > 0) {
                    player.getHungerManager().addExhaustion(3.0f);
                }
            }
        }
    }

    public float applyDamageReduction(LivingEntity entity, net.minecraft.entity.damage.DamageSource source, float amount) {
        if (!this.isInvulnerable()) return amount;

        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount;
        }

        float healthPercent = entity.getHealth() / entity.getMaxHealth();
        float reductionFactor = 1.0f - (healthPercent * 0.9f);
        float reducedAmount = MathHelper.lerp(reductionFactor, amount, MIN_DAMAGE_CAP);
        reducedAmount = Math.max(reducedAmount, MIN_DAMAGE_CAP);

        return reducedAmount;
    }

    private void tickConfusion(LivingEntity entity, long worldTime) {
        if (this.confusedUntil <= 0) return;

        if (worldTime >= this.confusedUntil) {
            this.confusedUntil = -1;
            this.confusionEffectTimer = 0;
            this.markDirty();
            RegenerationMod.LOGGER.info("Confusion ended for {}", entity.getUuid());
            return;
        }

        if (entity.getWorld().isClient) return;

        this.confusionEffectTimer--;
        if (this.confusionEffectTimer > 0) return;

        this.confusionEffectTimer = CONFUSION_EFFECT_INTERVAL_MIN +
                RegenerationMod.RANDOM.nextInt(CONFUSION_EFFECT_INTERVAL_MAX - CONFUSION_EFFECT_INTERVAL_MIN + 1);

        int effectRoll = RegenerationMod.RANDOM.nextInt(100);
        StatusEffectInstance effect = null;

        if (effectRoll < 30) {
            effect = new StatusEffectInstance(
                    StatusEffects.NAUSEA,
                    100 + RegenerationMod.RANDOM.nextInt(100),
                    0, false, false, true
            );
        } else if (effectRoll < 55) {
            effect = new StatusEffectInstance(
                    StatusEffects.DARKNESS,
                    60 + RegenerationMod.RANDOM.nextInt(80),
                    0, false, false, true
            );
        } else if (effectRoll < 75) {
            effect = new StatusEffectInstance(
                    StatusEffects.SLOWNESS,
                    60 + RegenerationMod.RANDOM.nextInt(80),
                    RegenerationMod.RANDOM.nextInt(2), false, false, true
            );
        } else if (effectRoll < 90) {
            effect = new StatusEffectInstance(
                    StatusEffects.WEAKNESS,
                    60 + RegenerationMod.RANDOM.nextInt(60),
                    0, false, false, true
            );
        } else {
            effect = new StatusEffectInstance(
                    StatusEffects.HUNGER,
                    20 + RegenerationMod.RANDOM.nextInt(40),
                    2 + RegenerationMod.RANDOM.nextInt(3), false, false, true
            );
        }

        if (effect != null) {
            entity.addStatusEffect(effect);
        }

        if (RegenerationMod.RANDOM.nextFloat() < 0.3f) {
            float randomYaw = (RegenerationMod.RANDOM.nextFloat() - 0.5f) * 90f;
            entity.setYaw(entity.getYaw() + randomYaw);
            if (entity instanceof ServerPlayerEntity player) {
                player.networkHandler.requestTeleport(
                        player.getX(), player.getY(), player.getZ(),
                        player.getYaw(), player.getPitch()
                );
            }
        }
    }

    public boolean tryStart(LivingEntity entity) {
        if (this.isActive() || this.usesLeft <= 0) return false;
        if (!entity.isAlive()) return false;
        this.delay.start(entity.age);
        this.markDirty();
        entity.setHealth(entity.getMaxHealth());
        if (entity instanceof AnimatedEntity animated) {
            animated.playAnimation(BedrockAnimationReference.parse(Identifier.of("start", RegenerationMod.RANDOM.nextBoolean() ? "right" : "left")));
        }
        RegenerationMod.LOGGER.info("Delay started for {}, will regenerate after {} ticks", entity.getUuid(), Delay.MAX_DURATION);
        return true;
    }

    private boolean start(LivingEntity entity) {
        if (this.isRegenerating() || this.usesLeft <= 0) return false;
        if (!entity.isAlive()) return false;

        this.setRegenQueued(false);
        this.decrement();
        this.setRegenerating(true);
        entity.setHealth(entity.getMaxHealth());

        if (entity instanceof AnimatedEntity animated) {
            AnimationTemplate template = RegenAnimRegistry.getInstance().getRandom();

            boolean changeSkin = this.changeSkinOnRegen;

            AnimationSet set = template.instantiate(changeSkin, this.skinReset);
            this.currentAnimationSet = set;

            set.finish(() -> {
                RegenerationMod.LOGGER.info("Animation finish callback for {}", entity.getUuid());
                this.finish(entity);
            });
            set.start(animated);
            for (AnimationTemplate.Stage stage : AnimationTemplate.Stage.values()) {
                set.callback(stage, s -> {
                    RegenerationEvents.CHANGE_STAGE.invoker().onStateChange(entity, this, s);
                });
            }
            RegenerationMod.LOGGER.info("Started regeneration animation for {}", entity.getUuid());
        } else {
            Scheduler.get().runTaskLater(() -> {
                RegenerationMod.LOGGER.info("Non-animated entity regeneration finish for {}", entity.getUuid());
                this.finish(entity);
            }, TaskStage.END_SERVER_TICK, TimeUnit.SECONDS, 5);
        }

        RegenerationEvents.START.invoker().onStart(entity, this);
        this.markDirty();
        return true;
    }

    private void finish(LivingEntity entity) {
        RegenerationMod.LOGGER.info("finish() called for {}", entity.getUuid());

        this.resetAnimationState(entity);
        this.stopRegeneration(entity);

        long worldTime = entity.getWorld().getTime();
        this.invulnerableUntil = worldTime + INVULNERABLE_DURATION;
        int confusionDuration = CONFUSION_MIN_TICKS + RegenerationMod.RANDOM.nextInt(CONFUSION_MAX_EXTRA_TICKS);
        this.confusedUntil = worldTime + confusionDuration;
        this.confusionEffectTimer = 0;
        this.regenBoostTimer = 0;

        RegenerationEvents.FINISH.invoker().onFinish(entity, this);
        this.setAnimation(RegenAnimRegistry.getInstance().getRandom());
        this.markDirty();

        entity.setNoGravity(false);
        entity.setVelocity(entity.getVelocity().multiply(0.5));
        entity.updatePosition(entity.getX(), entity.getY(), entity.getZ());

        RegenerationMod.LOGGER.info(
                "Regeneration finished for {}. Dynamic damage reduction + boosted regen active for {} ticks, confused for {} ticks",
                entity.getUuid(), INVULNERABLE_DURATION, confusionDuration
        );
    }

    private void resetAnimationState(LivingEntity entity) {
        if (!(entity instanceof AnimatedEntity animated)) return;

        try {
            animated.getAnimationState().stop();
            AnimationTracker.getInstance().remove(animated.getUuid());
            RegenerationMod.LOGGER.debug("Animation state reset for {}", entity.getUuid());
        } catch (Exception e) {
            RegenerationMod.LOGGER.error("Failed to reset animation state for {}", entity.getUuid(), e);
        }

        this.currentAnimationSet = null;
    }

    private Identifier getAnimationId() {
        return this.getAnimation().id();
    }

    public void stopRegeneration(@Nullable LivingEntity entity) {
        if (this.currentAnimationSet != null) {
            this.currentAnimationSet.cancel();
            this.currentAnimationSet = null;
        }

        if (entity instanceof AnimatedEntity) {
            this.resetAnimationState(entity);
        }

        this.invulnerableUntil = -1;
        this.confusedUntil = -1;
        this.confusionEffectTimer = 0;
        this.regenBoostTimer = 0;

        this.setRegenerating(false);
        this.delay.stop();
        this.markDirty();
        RegenerationMod.LOGGER.debug("Regeneration stopped for (state reset)");
    }

    @Deprecated
    public void stopRegeneration() {
        this.stopRegeneration(null);
    }

    public boolean tryStopDelayEvent(@Nullable LivingEntity entity) {
        if (!this.delay.hasEvent()) return false;
        this.delay.stopEvent();
        this.markDirty();
        RegenerationEvents.DELAY_FURTHER.invoker().onEvent(entity, this);
        return true;
    }

    public boolean isActive() {
        return this.isRegenerating() || this.delay.isRunning() || this.isRegenQueued();
    }

    public void markDirty() {
        this.setDirty(true);
    }

    private void sync(ServerPlayerEntity target, UUID sourceId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(sourceId);
        buf.encodeAsJson(CODEC, this);
        ServerPlayNetworking.send(target, SYNC_PACKET, buf);
    }

    @Environment(EnvType.CLIENT)
    public static void receive(PacketByteBuf buf) {
        UUID playerId = buf.readUuid();
        RegenerationInfo info = buf.decodeAsJson(CODEC);
        if (info == null) {
            RegenerationMod.LOGGER.warn("Received null RegenerationInfo from server for player {}", playerId);
            return;
        }
        if (net.minecraft.client.MinecraftClient.getInstance().world == null) {
            RegenerationMod.LOGGER.warn("Received RegenerationInfo from server for player {}, but client world is null", playerId);
            return;
        }
        PlayerEntity entity = net.minecraft.client.MinecraftClient.getInstance().world.getPlayerByUuid(playerId);
        if (entity == null) {
            RegenerationMod.LOGGER.warn("Received RegenerationInfo from server for player {}, but could not find player in client world", playerId);
            return;
        }
        if (!(entity instanceof RegenerationCapable)) {
            RegenerationMod.LOGGER.warn("Received RegenerationInfo from server for player {}, but player is not RegenerationCapable", playerId);
            return;
        }

        entity.setAttached(Attachments.REGENERATION, info);
        entity.setAttached(Attachments.IS_TIMELORD, true);

        if (info.isSkinReset() && entity instanceof net.minecraft.client.network.AbstractClientPlayerEntity clientPlayer) {
            try {
                // 无参 clear
                SkinData.clear();
            } catch (Exception ignored) {}
        }

        RegenerationMod.LOGGER.debug("RegenerationInfo synced to client for {}", playerId);
    }

    public static RegenerationInfo get(LivingEntity entity) {
        if (!(entity instanceof RegenerationCapable capability)) return null;
        return capability.getRegenerationInfo();
    }

    public static class Delay {
        public static final Codec<Delay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("start").forGetter(delay -> delay.start),
                Codec.INT.fieldOf("lastEvent").forGetter(delay -> delay.lastEvent)
        ).apply(instance, Delay::new));

        private static final int MAX_DURATION = 6000;
        private static final int TIME_TO_STOP = 300;
        private static final float EVENT_CHANCE = 0.05f;

        private int start;
        private int lastEvent;

        public Delay(int start, int lastEvent) {
            this.start = start;
            this.lastEvent = lastEvent;
        }

        public Delay(int start) {
            this(start, -1);
        }

        public Delay() {
            this(-1, -1);
        }

        public boolean isRunning() {
            return this.start >= 0;
        }

        public boolean hasEvent() {
            return this.lastEvent >= 0;
        }

        public float getProgress(float current) {
            if (this.start < 0) return 0;
            float duration = current - this.start;
            if (duration <= 0) return 0;
            if (duration >= MAX_DURATION) return 1;
            return duration / MAX_DURATION;
        }

        public float getEventProgress(float current) {
            if (this.lastEvent < 0) return 0;
            float duration = current - this.lastEvent;
            if (duration <= 0) return 0;
            if (duration >= TIME_TO_STOP) return 1;
            return duration / TIME_TO_STOP;
        }

        public void stopEvent() {
            this.lastEvent = -1;
        }

        public void stop() {
            this.start = -1;
            this.lastEvent = -1;
        }

        public void start(int current) {
            this.start = current;
        }

        public Result tick(int current) {
            if (this.start < 0) return Result.NONE;
            if (current < this.start) {
                this.stop();
                return Result.NONE;
            }
            if (current - this.start >= MAX_DURATION) {
                this.stop();
                return Result.REGENERATE;
            }
            if (this.lastEvent > 0 && current - this.lastEvent >= TIME_TO_STOP) {
                this.stop();
                return Result.REGENERATE;
            }
            if (this.lastEvent < 0) {
                float progress = this.getProgress(current);
                float probability = EVENT_CHANCE * progress;
                if (Math.random() < probability) {
                    this.lastEvent = current;
                    return Result.EVENT;
                }
            }
            return Result.NONE;
        }

        public enum Result {
            REGENERATE,
            EVENT,
            NONE
        }
    }
}