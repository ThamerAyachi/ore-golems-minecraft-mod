package com.example.oregolems.entity;

import com.example.oregolems.OreGolemsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Base abstract class for all miniature Copper-Golem-style Ore Golems.
 * Every Ore Golem spawns with its own paired Chest (just like the vanilla Copper Golem).
 */
public abstract class AbstractOreGolemEntity extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> DATA_WORKING =
            SynchedEntityData.defineId(AbstractOreGolemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ANTENNA_TICKS =
            SynchedEntityData.defineId(AbstractOreGolemEntity.class, EntityDataSerializers.INT);

    private BlockPos homeChestPos;

    protected AbstractOreGolemEntity(EntityType<? extends AbstractOreGolemEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createBaseGolemAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WORKING, false);
        builder.define(DATA_ANTENNA_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor levelAccessor,
            DifficultyInstance difficulty,
            EntitySpawnReason spawnReason,
            SpawnGroupData spawnData
    ) {
        SpawnGroupData data = super.finalizeSpawn(levelAccessor, difficulty, spawnReason, spawnData);
        if (spawnReason == EntitySpawnReason.SPAWN_ITEM_USE && levelAccessor instanceof ServerLevel serverLevel) {
            BlockPos spawnPos = this.blockPosition();
            if (serverLevel.getBlockState(spawnPos).isAir()) {
                Direction facing = this.getDirection();
                BlockState chestState = CopperChestBlock.getFromCopperBlock(Blocks.COPPER_BLOCK, facing, serverLevel, spawnPos);
                serverLevel.setBlock(spawnPos, chestState, 3);
                this.setHomeChestPos(spawnPos);
                this.snapTo(spawnPos.getX() + 0.5D, spawnPos.getY() + 1.0D, spawnPos.getZ() + 0.5D, this.getYRot(), this.getXRot());
            }
        }
        return data;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isWorking()) {
            this.entityData.set(DATA_ANTENNA_TICKS, this.getAntennaTicks() + 1);
        }
    }

    public boolean isWorking() {
        return this.entityData.get(DATA_WORKING);
    }

    public void setWorking(boolean working) {
        this.entityData.set(DATA_WORKING, working);
    }

    public int getAntennaTicks() {
        return this.entityData.get(DATA_ANTENNA_TICKS);
    }

    public BlockPos getHomeChestPos() {
        return this.homeChestPos;
    }

    public void setHomeChestPos(BlockPos pos) {
        this.homeChestPos = pos != null ? pos.immutable() : null;
    }

    public abstract String getGolemTextureName();

    public Identifier getTextureIdentifier() {
        return Identifier.fromNamespaceAndPath(OreGolemsMod.MODID, "textures/entity/" + getGolemTextureName() + ".png");
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (this.homeChestPos != null) {
            output.putInt("HomeChestX", this.homeChestPos.getX());
            output.putInt("HomeChestY", this.homeChestPos.getY());
            output.putInt("HomeChestZ", this.homeChestPos.getZ());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        if (input.getInt("HomeChestX").isPresent()) {
            this.homeChestPos = new BlockPos(
                    input.getIntOr("HomeChestX", 0),
                    input.getIntOr("HomeChestY", 0),
                    input.getIntOr("HomeChestZ", 0)
            );
        }
    }

    @Override
    protected int decreaseAirSupply(int air) {
        return air;
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.COPPER_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COPPER_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.COPPER_GOLEM_STEP, 0.5F, 1.1F);
    }

    /**
     * Returns the golem's own paired home Chest if valid and in range, or scans for the nearest Chest/Barrel.
     */
    public BlockPos findNearestChestOrBarrel(BlockPos origin, int radius) {
        Level level = this.level();
        if (this.homeChestPos != null && origin.distSqr(this.homeChestPos) <= (double) (radius * radius * 4)) {
            BlockState homeState = level.getBlockState(this.homeChestPos);
            if ((homeState.getBlock() instanceof ChestBlock || homeState.getBlock() instanceof BarrelBlock)
                    && level.getBlockEntity(this.homeChestPos) instanceof Container) {
                return this.homeChestPos;
            }
        }

        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        for (BlockPos checkPos : BlockPos.betweenClosed(
                origin.offset(-radius, -2, -radius),
                origin.offset(radius, 2, radius))) {
            BlockState state = level.getBlockState(checkPos);
            if (state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock) {
                BlockEntity be = level.getBlockEntity(checkPos);
                if (be instanceof Container) {
                    double distSq = origin.distSqr(checkPos);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        bestPos = checkPos.immutable();
                    }
                }
            }
        }
        return bestPos;
    }

    /**
     * Attempts to insert an ItemStack into a Chest or Barrel Container, returning any leftover items.
     */
    public static ItemStack depositIntoContainer(Container container, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remaining = stack.copy();

        // First merge into existing matching stacks
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slotStack = container.getItem(i);
            if (!slotStack.isEmpty() && ItemStack.isSameItemSameComponents(slotStack, remaining)) {
                int maxSize = Math.min(container.getMaxStackSize(), slotStack.getMaxStackSize());
                int space = maxSize - slotStack.getCount();
                if (space > 0) {
                    int move = Math.min(space, remaining.getCount());
                    slotStack.grow(move);
                    remaining.shrink(move);
                    container.setChanged();
                    if (remaining.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }

        // Then place into empty slots
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack slotStack = container.getItem(i);
            if (slotStack.isEmpty() && container.canPlaceItem(i, remaining)) {
                int maxSize = Math.min(container.getMaxStackSize(), remaining.getMaxStackSize());
                ItemStack toPlace = remaining.split(Math.min(remaining.getCount(), maxSize));
                container.setItem(i, toPlace);
                container.setChanged();
                if (remaining.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }

        return remaining;
    }
}
