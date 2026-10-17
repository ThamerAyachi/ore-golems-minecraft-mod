package com.example.oregolems.entity.variant;

import com.example.oregolems.OreGolemsMod;
import com.example.oregolems.block.RedstoneGolemSignalBlock;
import com.example.oregolems.entity.AbstractOreGolemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.EnumSet;

/**
 * Redstone Golem (redstone_golem) - Mobile Redstone Power Source.
 * Emits a Redstone signal (power level 15) to the block directly underneath or adjacent to it
 * when standing still. Can be right-clicked by the player to toggle between
 * "Follow/Wander" and "Stay/Power" modes.
 */
public class RedstoneGolemEntity extends AbstractOreGolemEntity {
    private static final EntityDataAccessor<Boolean> DATA_STAY_POWER_MODE =
            SynchedEntityData.defineId(RedstoneGolemEntity.class, EntityDataSerializers.BOOLEAN);

    private BlockPos activeSignalPos = null;

    public RedstoneGolemEntity(EntityType<? extends RedstoneGolemEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseGolemAttributes()
                .add(Attributes.MAX_HEALTH, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STAY_POWER_MODE, false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new StayAndPowerRedstoneGoal(this));
        this.goalSelector.addGoal(2, new FollowPlayerInWanderModeGoal(this, 1.05D, 4.0F, 12.0F));
    }

    public boolean isStayPowerMode() {
        return this.entityData.get(DATA_STAY_POWER_MODE);
    }

    public void setStayPowerMode(boolean stayPower) {
        this.entityData.set(DATA_STAY_POWER_MODE, stayPower);
        this.setWorking(stayPower);
    }

    public boolean isEmittingRedstoneSignal() {
        return this.isStayPowerMode() || (this.onGround() && this.getDeltaMovement().horizontalDistanceSqr() < 0.0005D && this.getNavigation().isDone());
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            if (!this.level().isClientSide()) {
                boolean nextMode = !this.isStayPowerMode();
                this.setStayPowerMode(nextMode);
                if (nextMode) {
                    this.getNavigation().stop();
                    player.displayClientMessage(Component.literal("Redstone Golem: Stay/Power Mode (Signal 15 ON)"), true);
                    this.playSound(SoundEvents.LEVER_CLICK, 0.9F, 1.2F);
                } else {
                    clearSignalBlock();
                    player.displayClientMessage(Component.literal("Redstone Golem: Follow/Wander Mode"), true);
                    this.playSound(SoundEvents.LEVER_CLICK, 0.9F, 0.8F);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            BlockPos currentPos = this.blockPosition();
            if (this.isEmittingRedstoneSignal()) {
                this.setWorking(true);
                if (this.activeSignalPos != null && !this.activeSignalPos.equals(currentPos)) {
                    clearSignalBlock();
                }
                BlockState stateAtGolem = this.level().getBlockState(currentPos);
                if (stateAtGolem.isAir() && !stateAtGolem.is(OreGolemsMod.REDSTONE_GOLEM_SIGNAL.get())) {
                    this.level().setBlock(currentPos, OreGolemsMod.REDSTONE_GOLEM_SIGNAL.get().defaultBlockState(), 3);
                    RedstoneGolemSignalBlock.notifyAllSurrounding(this.level(), currentPos);
                    this.activeSignalPos = currentPos.immutable();
                } else if (stateAtGolem.is(OreGolemsMod.REDSTONE_GOLEM_SIGNAL.get())) {
                    this.activeSignalPos = currentPos.immutable();
                }
                if (this.tickCount % 10 == 0 && this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(DustParticleOptions.REDSTONE,
                            this.getX(), this.getY() + 1.05D, this.getZ(),
                            4, 0.2D, 0.2D, 0.2D, 0.0D);
                }
            } else {
                this.setWorking(false);
                clearSignalBlock();
            }
        }
    }

    public void clearSignalBlock() {
        if (this.activeSignalPos != null && !this.level().isClientSide()) {
            if (this.level().getBlockState(this.activeSignalPos).is(OreGolemsMod.REDSTONE_GOLEM_SIGNAL.get())) {
                this.level().setBlock(this.activeSignalPos, Blocks.AIR.defaultBlockState(), 3);
                RedstoneGolemSignalBlock.notifyAllSurrounding(this.level(), this.activeSignalPos);
            }
            this.activeSignalPos = null;
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        clearSignalBlock();
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("StayPowerMode", this.isStayPowerMode());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setStayPowerMode(input.getBooleanOr("StayPowerMode", false));
    }

    @Override
    public String getGolemTextureName() {
        return "redstone_golem";
    }

    /**
     * AI Goal: Locks movement while in Stay/Power mode so the golem continuously powers the block below and adjacent to it.
     */
    public static class StayAndPowerRedstoneGoal extends Goal {
        private final RedstoneGolemEntity golem;

        public StayAndPowerRedstoneGoal(RedstoneGolemEntity golem) {
            this.golem = golem;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return this.golem.isStayPowerMode();
        }

        @Override
        public boolean canContinueToUse() {
            return this.golem.isStayPowerMode();
        }

        @Override
        public void start() {
            this.golem.getNavigation().stop();
            this.golem.setWorking(true);
        }

        @Override
        public void tick() {
            this.golem.getNavigation().stop();
        }
    }

    /**
     * AI Goal: Follows nearby players when in Follow/Wander mode.
     */
    public static class FollowPlayerInWanderModeGoal extends Goal {
        private final RedstoneGolemEntity golem;
        private final double speedModifier;
        private final float stopDistance;
        private final float startDistance;
        private Player targetPlayer;

        public FollowPlayerInWanderModeGoal(RedstoneGolemEntity golem, double speedModifier, float stopDistance, float startDistance) {
            this.golem = golem;
            this.speedModifier = speedModifier;
            this.stopDistance = stopDistance;
            this.startDistance = startDistance;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.golem.isStayPowerMode()) {
                return false;
            }
            Player nearest = this.golem.level().getNearestPlayer(this.golem, this.startDistance);
            if (nearest == null || nearest.isSpectator()) {
                return false;
            }
            if (this.golem.distanceToSqr(nearest) < (double) (this.stopDistance * this.stopDistance)) {
                return false;
            }
            this.targetPlayer = nearest;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.golem.isStayPowerMode()
                    && this.targetPlayer != null
                    && this.targetPlayer.isAlive()
                    && !this.golem.getNavigation().isDone()
                    && this.golem.distanceToSqr(this.targetPlayer) > (double) (this.stopDistance * this.stopDistance);
        }

        @Override
        public void tick() {
            if (this.targetPlayer != null) {
                this.golem.getLookControl().setLookAt(this.targetPlayer, 10.0F, (float) this.golem.getMaxHeadXRot());
                this.golem.getNavigation().moveTo(this.targetPlayer, this.speedModifier);
            }
        }

        @Override
        public void stop() {
            this.targetPlayer = null;
            this.golem.getNavigation().stop();
        }
    }
}
