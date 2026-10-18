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
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * Emerald Golem (emerald_golem) - Automated Farmer.
 * Scans for fully grown crops (CropBlock, NetherWart, SweetBerryBush at max age),
 * harvests them, replants age 0 automatically, and deposits harvested crops
 * into its paired Copper Chest or nearest Chest/Barrel.
 * Includes a 2-second (40-tick) timeout: if any crop or chest cannot be reached within 2 seconds,
 * the golem cancels that order, temporarily ignores that block, and picks another task.
 */
public class EmeraldGolemEntity extends AbstractOreGolemEntity {
    private final List<ItemStack> carriedHarvest = new ArrayList<>();

    public EmeraldGolemEntity(EntityType<? extends EmeraldGolemEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseGolemAttributes()
                .add(Attributes.MAX_HEALTH, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new DepositHarvestGoal(this));
        this.goalSelector.addGoal(2, new HarvestMatureCropsGoal(this, 6));
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
     * AI Goal: Scans for fully grown crops, harvests them, and replants age 0 automatically.
     * Cancels and blacklists the target if not reached within 2 seconds (40 ticks).
     */
    public static class HarvestMatureCropsGoal extends Goal {
        private final EmeraldGolemEntity golem;
        private final int halfExtent;
        private BlockPos targetCropPos;
        private int scanCooldown;
        private int stuckTicks;
        private double bestDistSqr;

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
            this.scanCooldown = 10;
            this.targetCropPos = findNearestMatureCrop();
            return this.targetCropPos != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.targetCropPos != null
                    && this.stuckTicks < MAX_TASK_STUCK_TICKS
                    && isMatureCrop(this.golem.level().getBlockState(this.targetCropPos));
        }

        @Override
        public void start() {
            this.stuckTicks = 0;
            this.bestDistSqr = Double.MAX_VALUE;
            if (this.targetCropPos != null) {
                this.golem.setWorking(true);
                this.golem.moveTowardsBlock(this.targetCropPos, 1.05D);
            }
        }

        @Override
        public void tick() {
            if (this.targetCropPos == null) {
                return;
            }
            Vec3 cropCenter = Vec3.atCenterOf(this.targetCropPos);
            this.golem.getLookControl().setLookAt(cropCenter.x, cropCenter.y, cropCenter.z);

            double distSqr = this.golem.distanceToSqr(cropCenter);
            if (distSqr + 0.08D < this.bestDistSqr) {
                this.bestDistSqr = distSqr;
                this.stuckTicks = 0;
            } else {
                this.stuckTicks++;
            }

            if (this.stuckTicks % 8 == 0) {
                this.golem.moveTowardsBlock(this.targetCropPos, 1.05D);
            }

            // Harvest when within ~2.5 blocks (accounts for raised farmland and adjacent water channels)
            if (distSqr <= 6.5D) {
                if (this.golem.level() instanceof ServerLevel serverLevel) {
                    harvestAndReplant(serverLevel, this.targetCropPos);
                }
                this.targetCropPos = null;
                this.scanCooldown = 5;
                return;
            }

            // If stuck for >2 seconds (40 ticks), cancel this crop order, ignore it temporarily, and move on
            if (this.stuckTicks >= MAX_TASK_STUCK_TICKS) {
                this.golem.ignoreBlockTemporarily(this.targetCropPos, 100);
                this.golem.getNavigation().stop();
                this.targetCropPos = null;
                this.scanCooldown = 5;
            }
        }

        private void harvestAndReplant(ServerLevel serverLevel, BlockPos pos) {
            BlockState state = serverLevel.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
                List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, null);
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

                serverLevel.setBlock(pos, crop.getStateForAge(0), 3);
                playHarvestEffects(serverLevel, pos);
            } else if (state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= 3) {
                List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, null);
                boolean consumedWart = false;
                for (ItemStack drop : drops) {
                    if (!consumedWart && !drop.isEmpty()) {
                        drop.shrink(1);
                        consumedWart = true;
                    }
                    if (!drop.isEmpty()) {
                        this.golem.addHarvestDrop(drop);
                    }
                }
                serverLevel.setBlock(pos, state.setValue(NetherWartBlock.AGE, 0), 3);
                playHarvestEffects(serverLevel, pos);
            } else if (state.getBlock() instanceof SweetBerryBushBlock && state.getValue(SweetBerryBushBlock.AGE) >= 3) {
                List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, null);
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        this.golem.addHarvestDrop(drop);
                    }
                }
                serverLevel.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), 3);
                playHarvestEffects(serverLevel, pos);
            }
        }

        private void playHarvestEffects(ServerLevel serverLevel, BlockPos pos) {
            serverLevel.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.9F, 1.0F);
            serverLevel.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8F, 1.1F);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.4D,
                    pos.getZ() + 0.5D,
                    6, 0.25D, 0.2D, 0.25D, 0.02D);
        }

        @Override
        public void stop() {
            this.targetCropPos = null;
            this.stuckTicks = 0;
            this.golem.setWorking(false);
        }

        private BlockPos findNearestMatureCrop() {
            BlockPos origin = this.golem.getLogicalBlockPos();
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(
                    origin.offset(-this.halfExtent, -2, -this.halfExtent),
                    origin.offset(this.halfExtent, 2, this.halfExtent))) {
                if (this.golem.isBlockIgnored(pos)) {
                    continue;
                }
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
            if (state.getBlock() instanceof CropBlock crop) {
                return crop.isMaxAge(state);
            }
            if (state.getBlock() instanceof NetherWartBlock) {
                return state.getValue(NetherWartBlock.AGE) >= 3;
            }
            if (state.getBlock() instanceof SweetBerryBushBlock) {
                return state.getValue(SweetBerryBushBlock.AGE) >= 3;
            }
            return false;
        }
    }

    /**
     * AI Goal: Deposits harvested crops into the nearest Chest or Barrel.
     * If the chest cannot be physically reached within 2 seconds (40 ticks), deposits remotely or moves on.
     */
    public static class DepositHarvestGoal extends Goal {
        private final EmeraldGolemEntity golem;
        private BlockPos targetChestPos;
        private int stuckTicks;

        public DepositHarvestGoal(EmeraldGolemEntity golem) {
            this.golem = golem;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.golem.getCarriedHarvest().isEmpty()) {
                return false;
            }
            this.targetChestPos = this.golem.findNearestChestOrBarrel(this.golem.getLogicalBlockPos(), 10);
            if (this.targetChestPos == null && this.golem.level() instanceof ServerLevel serverLevel) {
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
            return !this.golem.getCarriedHarvest().isEmpty()
                    && this.targetChestPos != null
                    && this.stuckTicks <= MAX_TASK_STUCK_TICKS;
        }

        @Override
        public void start() {
            this.stuckTicks = 0;
            if (this.targetChestPos != null) {
                this.golem.setWorking(true);
                this.golem.moveTowardsBlock(this.targetChestPos, 1.1D);
            }
        }

        @Override
        public void tick() {
            if (this.targetChestPos == null) {
                return;
            }
            Vec3 chestCenter = Vec3.atCenterOf(this.targetChestPos);
            this.golem.getLookControl().setLookAt(chestCenter.x, chestCenter.y, chestCenter.z);

            this.stuckTicks++;
            if (this.stuckTicks % 8 == 0) {
                this.golem.moveTowardsBlock(this.targetChestPos, 1.1D);
            }

            double distSqr = this.golem.distanceToSqr(chestCenter);
            // Deposit when within ~2.75 blocks of chest center, OR if 2 seconds (40 ticks) elapsed trying to reach it
            if (distSqr <= 7.5D || this.stuckTicks >= MAX_TASK_STUCK_TICKS) {
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
                } else {
                    this.golem.ignoreBlockTemporarily(this.targetChestPos, 100);
                }
                this.targetChestPos = null;
            }
        }

        @Override
        public void stop() {
            this.targetChestPos = null;
            this.stuckTicks = 0;
            this.golem.setWorking(false);
        }
    }
}
