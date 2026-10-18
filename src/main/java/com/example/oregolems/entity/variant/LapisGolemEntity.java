package com.example.oregolems.entity.variant;

import com.example.oregolems.entity.AbstractOreGolemEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Lapis Golem (lapis_golem) - XP Collector & Enchanter Helper.
 * Absorbs nearby XP Orbs within a 12-block radius and stores them.
 * When right-clicked with an empty Glass Bottle, consumes stored XP to give the player an experience_bottle.
 * Includes a 2-second (40-tick) timeout so orbs stuck behind blocks are absorbed cleanly.
 */
public class LapisGolemEntity extends AbstractOreGolemEntity {
    public static final int XP_PER_BOTTLE = 7;

    private static final EntityDataAccessor<Integer> DATA_STORED_XP =
            SynchedEntityData.defineId(LapisGolemEntity.class, EntityDataSerializers.INT);

    public LapisGolemEntity(EntityType<? extends LapisGolemEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseGolemAttributes()
                .add(Attributes.MAX_HEALTH, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STORED_XP, 0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new AbsorbNearbyXpOrbsGoal(this, 12.0D));
    }

    public int getStoredXp() {
        return this.entityData.get(DATA_STORED_XP);
    }

    public void setStoredXp(int xp) {
        this.entityData.set(DATA_STORED_XP, Math.max(0, xp));
    }

    public void addStoredXp(int amount) {
        this.setStoredXp(this.getStoredXp() + amount);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.GLASS_BOTTLE)) {
            int currentXp = this.getStoredXp();
            if (currentXp > 0) {
                if (!this.level().isClientSide()) {
                    int consumed = Math.min(currentXp, XP_PER_BOTTLE);
                    this.setStoredXp(currentXp - consumed);

                    ItemStack bottleOfEnchanting = new ItemStack(Items.EXPERIENCE_BOTTLE);
                    ItemStack result = ItemUtils.createFilledResult(stack, player, bottleOfEnchanting);
                    player.setItemInHand(hand, result);

                    this.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9F, 1.25F);
                    if (this.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                                this.getX(), this.getY() + 0.9D, this.getZ(),
                                15, 0.3D, 0.3D, 0.3D, 0.2D);
                    }
                    player.displayClientMessage(
                            Component.literal("Lapis Golem: Filled Bottle o' Enchanting (Stored XP: " + this.getStoredXp() + ")"),
                            true
                    );
                }
                return InteractionResult.SUCCESS;
            } else {
                if (!this.level().isClientSide()) {
                    player.displayClientMessage(Component.literal("Lapis Golem has 0 stored XP!"), true);
                }
                return InteractionResult.CONSUME;
            }
        } else if (hand == InteractionHand.MAIN_HAND && stack.isEmpty()) {
            if (!this.level().isClientSide()) {
                player.displayClientMessage(
                        Component.literal("Lapis Golem Stored XP: " + this.getStoredXp() + " (Right-click with Glass Bottle)"),
                        true
                );
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("StoredXp", this.getStoredXp());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setStoredXp(input.getIntOr("StoredXp", 0));
    }

    @Override
    public String getGolemTextureName() {
        return "lapis_golem";
    }

    /**
     * AI Goal: Pulls in and absorbs ExperienceOrb entities within a 12-block radius.
     * Absorbs orbs directly if stuck behind blocks for >2 seconds (40 ticks).
     */
    public static class AbsorbNearbyXpOrbsGoal extends Goal {
        private final LapisGolemEntity golem;
        private final double radius;
        private List<ExperienceOrb> orbs;
        private int absorbTicks;

        public AbsorbNearbyXpOrbsGoal(LapisGolemEntity golem, double radius) {
            this.golem = golem;
            this.radius = radius;
        }

        @Override
        public boolean canUse() {
            if (this.golem.level().isClientSide()) {
                return false;
            }
            AABB box = this.golem.getBoundingBox().inflate(this.radius);
            this.orbs = this.golem.level().getEntitiesOfClass(
                    ExperienceOrb.class,
                    box,
                    orb -> orb.isAlive() && orb.getValue() > 0
            );
            return !this.orbs.isEmpty();
        }

        @Override
        public boolean canContinueToUse() {
            return this.absorbTicks < MAX_TASK_STUCK_TICKS && canUse();
        }

        @Override
        public void start() {
            this.absorbTicks = 0;
            this.golem.setWorking(true);
        }

        @Override
        public void tick() {
            if (this.orbs == null) {
                return;
            }
            this.absorbTicks++;
            Vec3 center = this.golem.position().add(0.0D, 0.5D, 0.0D);
            for (ExperienceOrb orb : this.orbs) {
                if (!orb.isAlive()) {
                    continue;
                }
                Vec3 diff = center.subtract(orb.position());
                double dist = diff.length();
                if (dist <= 1.8D || this.absorbTicks >= MAX_TASK_STUCK_TICKS - 1) {
                    int value = Math.max(1, orb.getValue());
                    this.golem.addStoredXp(value);
                    orb.discard();
                    this.golem.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4F, 1.3F);
                    if (this.golem.level() instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                                this.golem.getX(), this.golem.getY() + 0.9D, this.golem.getZ(),
                                6, 0.25D, 0.25D, 0.25D, 0.1D);
                    }
                } else {
                    Vec3 pull = diff.normalize().scale(0.24D);
                    orb.setDeltaMovement(orb.getDeltaMovement().scale(0.7D).add(pull));
                    orb.hurtMarked = true;
                }
            }
        }

        @Override
        public void stop() {
            this.orbs = null;
            this.absorbTicks = 0;
            this.golem.setWorking(false);
        }
    }
}
