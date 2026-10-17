package com.example.oregolems.entity.variant;

import com.example.oregolems.entity.AbstractOreGolemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * Emerald Golem (emerald_golem) - Automated Farmer.
 * Scans a 9x9 area (radius 4) for fully grown crops (CropBlock at max age),
 * harvests them, replants the seed automatically, and deposits harvested crops
 * into the nearest Chest or Barrel.
 */
public class EmeraldGolemEntity extends AbstractOreGolemEntity {
    private final List<ItemStack> carriedHarvest = new ArrayList<>();

    public EmeraldGolemEntity(EntityType<? extends EmeraldGolemEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseGolemAttributes()
                .add(Attributes.MAX_HEALTH, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new DepositHarvestGoal(this));
        this.goalSelector.addGoal(2, new HarvestMatureCropsGoal(this, 4)); // 9x9 area (-4..+4)
    }

    public List<ItemStack> getCarriedHarvest() {
        return this.carriedHarvest;
    }

    public void addHarvestDrop(ItemStack stack) {
        if (!stack.isEmpty()) {
            this.carriedHarvest.add(stack.copy());
        }
    }

    @Override
    public String getGolemTextureName() {
        return "emerald_golem";
    }

    /**
     * AI Goal: Scans a 9x9 area for fully grown CropBlocks, harvests them, and replants age 0 automatically.
     */
    public static class HarvestMatureCropsGoal extends Goal {
        private final EmeraldGolemEntity golem;
        private final int halfExtent;
        private BlockPos targetCropPos;
        private int scanCooldown;

        public HarvestMatureCropsGoal(EmeraldGolemEntity golem, int halfExtent) {
            this.golem = golem;
            this.halfExtent = halfExtent;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.scanCooldown > 0) {
                this.scanCooldown--;
                return false;
            }
            this.scanCooldown = 15;
            this.targetCropPos = findNearestMatureCrop();
            return this.targetCropPos != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.targetCropPos != null && isMatureCrop(this.golem.level().getBlockState(this.targetCropPos));
        }

        @Override
        public void start() {
            if (this.targetCropPos != null) {
                this.golem.setWorking(true);
                this.golem.getNavigation().moveTo(
                        this.targetCropPos.getX() + 0.5D,
                        this.targetCropPos.getY(),
                        this.targetCropPos.getZ() + 0.5D,
                        1.0D
                );
            }
        }

        @Override
        public void tick() {
            if (this.targetCropPos == null) {
                return;
            }
            this.golem.getLookControl().setLookAt(
                    this.targetCropPos.getX() + 0.5D,
                    this.targetCropPos.getY() + 0.25D,
                    this.targetCropPos.getZ() + 0.5D
            );
            if (this.golem.blockPosition().distSqr(this.targetCropPos) <= 4.5D) {
                if (this.golem.level() instanceof ServerLevel serverLevel) {
                    BlockState state = serverLevel.getBlockState(this.targetCropPos);
                    if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                        List<ItemStack> drops = Block.getDrops(state, serverLevel, this.targetCropPos, null);
                        boolean consumedSeed = false;

                        for (ItemStack drop : drops) {
                            if (!consumedSeed && drop.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == crop) {
                                drop.shrink(1);
                                consumedSeed = true;
                            }
                            if (!drop.isEmpty()) {
                                this.golem.addHarvestDrop(drop);
                            }
                        }

                        // Automatically replant the crop at age 0
                        serverLevel.setBlock(this.targetCropPos, crop.getStateForAge(0), 3);
                        serverLevel.playSound(null, this.targetCropPos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.9F, 1.0F);
                        serverLevel.playSound(null, this.targetCropPos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8F, 1.1F);
                        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                this.targetCropPos.getX() + 0.5D,
                                this.targetCropPos.getY() + 0.4D,
                                this.targetCropPos.getZ() + 0.5D,
                                6, 0.25D, 0.2D, 0.25D, 0.02D);
                    }
                }
                this.targetCropPos = null;
            }
        }

        @Override
        public void stop() {
            this.targetCropPos = null;
            this.golem.setWorking(false);
        }

        private BlockPos findNearestMatureCrop() {
            BlockPos origin = this.golem.blockPosition();
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(
                    origin.offset(-this.halfExtent, -1, -this.halfExtent),
                    origin.offset(this.halfExtent, 1, this.halfExtent))) {
                if (isMatureCrop(this.golem.level().getBlockState(pos))) {
                    double dist = origin.distSqr(pos);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = pos.immutable();
                    }
                }
            }
            return best;
        }

        private boolean isMatureCrop(BlockState state) {
            return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
        }
    }

    /**
     * AI Goal: Deposits harvested crops into the nearest Chest or Barrel.
     */
    public static class DepositHarvestGoal extends Goal {
        private final EmeraldGolemEntity golem;
        private BlockPos targetChestPos;

        public DepositHarvestGoal(EmeraldGolemEntity golem) {
            this.golem = golem;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.golem.getCarriedHarvest().isEmpty()) {
                return false;
            }
            this.targetChestPos = this.golem.findNearestChestOrBarrel(this.golem.blockPosition(), 10);
            if (this.targetChestPos == null && this.golem.level() instanceof ServerLevel serverLevel) {
                // If no chest or barrel is nearby, drop harvested items cleanly at the golem's position
                for (ItemStack stack : this.golem.getCarriedHarvest()) {
                    if (!stack.isEmpty()) {
                        this.golem.spawnAtLocation(serverLevel, stack);
                    }
                }
                this.golem.getCarriedHarvest().clear();
                return false;
            }
            return this.targetChestPos != null;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.golem.getCarriedHarvest().isEmpty() && this.targetChestPos != null;
        }

        @Override
        public void start() {
            if (this.targetChestPos != null) {
                this.golem.setWorking(true);
                this.golem.getNavigation().moveTo(
                        this.targetChestPos.getX() + 0.5D,
                        this.targetChestPos.getY(),
                        this.targetChestPos.getZ() + 0.5D,
                        1.1D
                );
            }
        }

        @Override
        public void tick() {
            if (this.targetChestPos == null) {
                return;
            }
            this.golem.getLookControl().setLookAt(
                    this.targetChestPos.getX() + 0.5D,
                    this.targetChestPos.getY() + 0.5D,
                    this.targetChestPos.getZ() + 0.5D
            );
            if (this.golem.blockPosition().distSqr(this.targetChestPos) <= 5.5D) {
                BlockEntity be = this.golem.level().getBlockEntity(this.targetChestPos);
                if (be instanceof Container container) {
                    Iterator<ItemStack> it = this.golem.getCarriedHarvest().iterator();
                    while (it.hasNext()) {
                        ItemStack stack = it.next();
                        ItemStack remaining = depositIntoContainer(container, stack);
                        if (remaining.isEmpty()) {
                            it.remove();
                        } else if (this.golem.level() instanceof ServerLevel serverLevel) {
                            this.golem.spawnAtLocation(serverLevel, remaining);
                            it.remove();
                        }
                    }
                    this.golem.level().playSound(null, this.targetChestPos, SoundEvents.COPPER_GOLEM_ITEM_DROP, SoundSource.BLOCKS, 0.8F, 1.1F);
                }
                this.targetChestPos = null;
            }
        }

        @Override
        public void stop() {
            this.targetChestPos = null;
            this.golem.setWorking(false);
        }
    }
}
