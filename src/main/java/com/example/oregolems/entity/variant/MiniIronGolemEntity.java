package com.example.oregolems.entity.variant;

import com.example.oregolems.entity.AbstractOreGolemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Iron Golem Mini (mini_iron_golem) - Base Guard & Item Magnet.
 * Attacks nearby hostile mobs (higher speed, lower health than vanilla Iron Golem)
 * and pulls dropped items within an 8-block radius into adjacent/nearby Chests or Barrels.
 */
public class MiniIronGolemEntity extends AbstractOreGolemEntity {
    public MiniIronGolemEntity(EntityType<? extends MiniIronGolemEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Vanilla Iron Golem has 100.0 health and 0.25 movement speed; Mini Iron Golem has 40.0 health and 0.36 speed
        return createBaseGolemAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.36D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(2, new ItemMagnetGoal(this, 8.0D));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this,
                Mob.class,
                5,
                true,
                false,
                (target, serverLevel) -> target instanceof Enemy && !(target instanceof Creeper)
        ));
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        this.setWorking(true);
        boolean hurt = super.doHurtTarget(level, target);
        if (hurt) {
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(motion.x, motion.y + 0.3D, motion.z);
            this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 1.3F);
        }
        return hurt;
    }

    @Override
    public String getGolemTextureName() {
        return "mini_iron_golem";
    }

    /**
     * AI Goal: Pulls dropped ItemEntities within an 8-block radius and deposits them into nearby Chests or Barrels.
     */
    public static class ItemMagnetGoal extends Goal {
        private final MiniIronGolemEntity golem;
        private final double radius;
        private List<ItemEntity> nearbyItems;

        public ItemMagnetGoal(MiniIronGolemEntity golem, double radius) {
            this.golem = golem;
            this.radius = radius;
        }

        @Override
        public boolean canUse() {
            if (this.golem.level().isClientSide() || this.golem.getTarget() != null) {
                return false;
            }
            AABB searchBox = this.golem.getBoundingBox().inflate(this.radius);
            this.nearbyItems = this.golem.level().getEntitiesOfClass(
                    ItemEntity.class,
                    searchBox,
                    item -> item.isAlive() && !item.getItem().isEmpty()
            );
            return !this.nearbyItems.isEmpty();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            this.golem.setWorking(true);
        }

        @Override
        public void tick() {
            if (this.nearbyItems == null || this.nearbyItems.isEmpty()) {
                return;
            }

            BlockPos chestPos = this.golem.findNearestChestOrBarrel(this.golem.blockPosition(), 8);
            Container targetContainer = null;
            if (chestPos != null) {
                BlockEntity be = this.golem.level().getBlockEntity(chestPos);
                if (be instanceof Container c) {
                    targetContainer = c;
                }
            }

            Vec3 attractTarget = chestPos != null
                    ? Vec3.atCenterOf(chestPos)
                    : this.golem.position().add(0.0D, 0.4D, 0.0D);

            for (ItemEntity itemEntity : this.nearbyItems) {
                if (!itemEntity.isAlive()) {
                    continue;
                }
                Vec3 diff = attractTarget.subtract(itemEntity.position());
                double dist = diff.length();
                if (dist > 0.1D) {
                    Vec3 pull = diff.normalize().scale(0.18D);
                    itemEntity.setDeltaMovement(itemEntity.getDeltaMovement().scale(0.6D).add(pull));
                    itemEntity.hurtMarked = true;
                }

                // Deposit into adjacent/nearby Chest or Barrel when close to the golem or the container
                if (targetContainer != null && (dist <= 2.2D || itemEntity.distanceToSqr(this.golem) <= 4.0D)) {
                    ItemStack remaining = depositIntoContainer(targetContainer, itemEntity.getItem());
                    if (remaining.isEmpty()) {
                        itemEntity.discard();
                        this.golem.level().playSound(null, chestPos, SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.NEUTRAL, 0.6F, 1.2F);
                        if (this.golem.level() instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                                    chestPos.getX() + 0.5D, chestPos.getY() + 0.7D, chestPos.getZ() + 0.5D,
                                    5, 0.2D, 0.2D, 0.2D, 0.02D);
                        }
                    } else {
                        itemEntity.setItem(remaining);
                    }
                }
            }
        }

        @Override
        public void stop() {
            this.nearbyItems = null;
            this.golem.setWorking(false);
        }
    }
}
