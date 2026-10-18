package com.example.oregolems.entity.variant;

import com.example.oregolems.entity.AbstractOreGolemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.EnumSet;

/**
 * Coal Golem (coal_golem) - Smelter & Torch Placer.
 * Scans a 10-block radius for Furnaces, Blast Furnaces, or Smokers that are actively smelting
 * or have items in the input slot (slot 0) with 0 fuel, and automatically adds fuel burn time.
 * Also places torches when the local block light level drops below 4.
 * Includes a 2-second (40-tick) timeout so unreachable furnaces are skipped cleanly.
 */
public class CoalGolemEntity extends AbstractOreGolemEntity {
    private static Field DATA_ACCESS_FIELD;

    static {
        try {
            DATA_ACCESS_FIELD = AbstractFurnaceBlockEntity.class.getDeclaredField("dataAccess");
            DATA_ACCESS_FIELD.setAccessible(true);
        } catch (Exception ignored) {
            DATA_ACCESS_FIELD = null;
        }
    }

    public CoalGolemEntity(EntityType<? extends CoalGolemEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseGolemAttributes()
                .add(Attributes.MAX_HEALTH, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new PlaceTorchInDarkGoal(this));
        this.goalSelector.addGoal(2, new FuelNearbyFurnacesGoal(this, 10));
    }

    @Override
    public String getGolemTextureName() {
        return "coal_golem";
    }

    public static int getFurnaceLitTime(AbstractFurnaceBlockEntity furnace) {
        if (DATA_ACCESS_FIELD != null) {
            try {
                ContainerData data = (ContainerData) DATA_ACCESS_FIELD.get(furnace);
                if (data != null) {
                    return data.get(AbstractFurnaceBlockEntity.DATA_LIT_TIME);
                }
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    public static void addFurnaceFuelTime(Level level, BlockPos pos, AbstractFurnaceBlockEntity furnace, int burnTicks) {
        boolean updatedViaData = false;
        if (DATA_ACCESS_FIELD != null) {
            try {
                ContainerData data = (ContainerData) DATA_ACCESS_FIELD.get(furnace);
                if (data != null) {
                    int current = data.get(AbstractFurnaceBlockEntity.DATA_LIT_TIME);
                    int next = Math.min(current + burnTicks, 3200);
                    data.set(AbstractFurnaceBlockEntity.DATA_LIT_TIME, next);
                    data.set(AbstractFurnaceBlockEntity.DATA_LIT_DURATION, Math.max(burnTicks, next));
                    updatedViaData = true;
                }
            } catch (Exception ignored) {
            }
        }

        if (!updatedViaData && furnace.getItem(1).isEmpty()) {
            furnace.setItem(1, new ItemStack(Items.COAL, 1));
        }

        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(AbstractFurnaceBlock.LIT) && !state.getValue(AbstractFurnaceBlock.LIT)) {
            level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, true), 3);
        }
        furnace.setChanged();
    }

    /**
     * AI Goal: Places a torch at the golem's logical position if local block light level drops below 4.
     */
    public static class PlaceTorchInDarkGoal extends Goal {
        private final CoalGolemEntity golem;
        private int cooldown;

        public PlaceTorchInDarkGoal(CoalGolemEntity golem) {
            this.golem = golem;
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            if (this.golem.level().isClientSide() || !this.golem.onGround()) {
                return false;
            }
            BlockPos pos = this.golem.getLogicalBlockPos();
            Level level = this.golem.level();
            int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
            if (blockLight >= 4) {
                return false;
            }
            BlockState current = level.getBlockState(pos);
            BlockState torchState = Blocks.TORCH.defaultBlockState();
            return current.isAir() && torchState.canSurvive(level, pos);
        }

        @Override
        public void start() {
            Level level = this.golem.level();
            BlockPos pos = this.golem.getLogicalBlockPos();
            BlockState torchState = Blocks.TORCH.defaultBlockState();
            if (level.getBlockState(pos).isAir() && torchState.canSurvive(level, pos)) {
                this.golem.setWorking(true);
                level.setBlock(pos, torchState, 3);
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8F, 1.1F);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D,
                            8, 0.15D, 0.15D, 0.15D, 0.01D);
                }
                this.cooldown = 40;
            }
        }

        @Override
        public void stop() {
            this.golem.setWorking(false);
        }
    }

    /**
     * AI Goal: Scans a 10-block radius for Furnaces, Blast Furnaces, or Smokers that are actively smelting
     * or have items in slot 0 with 0 fuel, paths to them, and injects fuel burn time.
     * Cancels and blacklists unreachable furnaces after 2 seconds (40 ticks).
     */
    public static class FuelNearbyFurnacesGoal extends Goal {
        private final CoalGolemEntity golem;
        private final int radius;
        private BlockPos targetFurnacePos;
        private int scanCooldown;
        private int stuckTicks;

        public FuelNearbyFurnacesGoal(CoalGolemEntity golem, int radius) {
            this.golem = golem;
            this.radius = radius;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.scanCooldown > 0) {
                this.scanCooldown--;
                return false;
            }
            this.scanCooldown = 15;
            this.targetFurnacePos = findFurnaceNeedingFuel();
            return this.targetFurnacePos != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.targetFurnacePos != null
                    && this.stuckTicks < MAX_TASK_STUCK_TICKS
                    && isValidTargetFurnace(this.targetFurnacePos);
        }

        @Override
        public void start() {
            this.stuckTicks = 0;
            if (this.targetFurnacePos != null) {
                this.golem.setWorking(true);
                this.golem.moveTowardsBlock(this.targetFurnacePos, 1.1D);
            }
        }

        @Override
        public void tick() {
            if (this.targetFurnacePos == null) {
                return;
            }
            Vec3 furnaceCenter = Vec3.atCenterOf(this.targetFurnacePos);
            this.golem.getLookControl().setLookAt(furnaceCenter.x, furnaceCenter.y, furnaceCenter.z);

            this.stuckTicks++;
            if (this.stuckTicks % 8 == 0) {
                this.golem.moveTowardsBlock(this.targetFurnacePos, 1.1D);
            }

            double distSqr = this.golem.distanceToSqr(furnaceCenter);
            if (distSqr <= 7.5D) {
                fuelTargetFurnace();
                this.targetFurnacePos = null;
                this.scanCooldown = 30;
            } else if (this.stuckTicks >= MAX_TASK_STUCK_TICKS) {
                // If within 6 blocks after 2 seconds, fuel remotely; otherwise ignore this furnace and move on
                if (distSqr <= 36.0D) {
                    fuelTargetFurnace();
                } else {
                    this.golem.ignoreBlockTemporarily(this.targetFurnacePos, 100);
                }
                this.golem.getNavigation().stop();
                this.targetFurnacePos = null;
                this.scanCooldown = 20;
            }
        }

        private void fuelTargetFurnace() {
            BlockEntity be = this.golem.level().getBlockEntity(this.targetFurnacePos);
            if (be instanceof AbstractFurnaceBlockEntity furnace) {
                addFurnaceFuelTime(this.golem.level(), this.targetFurnacePos, furnace, 1600);
                this.golem.level().playSound(null, this.targetFurnacePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
                if (this.golem.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.FLAME,
                            this.targetFurnacePos.getX() + 0.5D,
                            this.targetFurnacePos.getY() + 0.5D,
                            this.targetFurnacePos.getZ() + 0.5D,
                            12, 0.25D, 0.25D, 0.25D, 0.02D);
                }
            }
        }

        @Override
        public void stop() {
            this.targetFurnacePos = null;
            this.stuckTicks = 0;
            this.golem.setWorking(false);
        }

        private BlockPos findFurnaceNeedingFuel() {
            BlockPos origin = this.golem.getLogicalBlockPos();
            BlockPos closest = null;
            double closestDist = Double.MAX_VALUE;

            for (BlockPos pos : BlockPos.betweenClosed(
                    origin.offset(-this.radius, -3, -this.radius),
                    origin.offset(this.radius, 3, this.radius))) {
                if (this.golem.isBlockIgnored(pos)) {
                    continue;
                }
                if (isValidTargetFurnace(pos)) {
                    double d = origin.distSqr(pos);
                    if (d < closestDist) {
                        closestDist = d;
                        closest = pos.immutable();
                    }
                }
            }
            return closest;
        }

        private boolean isValidTargetFurnace(BlockPos pos) {
            BlockEntity be = this.golem.level().getBlockEntity(pos);
            if (!(be instanceof AbstractFurnaceBlockEntity furnace)) {
                return false;
            }
            ItemStack input = furnace.getItem(0);
            if (input.isEmpty()) {
                return false;
            }
            int litTime = getFurnaceLitTime(furnace);
            ItemStack fuel = furnace.getItem(1);
            return (litTime <= 200 && fuel.isEmpty()) || litTime == 0;
        }
    }
}
