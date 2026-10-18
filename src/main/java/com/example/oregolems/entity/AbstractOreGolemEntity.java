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
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Base abstract class for all miniature Copper-Golem-style Ore Golems.
 * Every Ore Golem spawns with its own paired Chest (just like the vanilla Copper Golem)
 * and includes a 2-second (40-tick) anti-stuck timeout + temporary block cooldown system.
 */
public abstract class AbstractOreGolemEntity extends PathfinderMob {
    /**
     * Maximum ticks (40 ticks = 2.0 seconds) a golem will attempt a single stuck order
     * before cancelling it, blacklisting the unreachable target temporarily, and moving on.
     */
    public static final int MAX_TASK_STUCK_TICKS = 40;

    private static final EntityDataAccessor<Boolean> DATA_WORKING =
            SynchedEntityData.defineId(AbstractOreGolemEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ANTENNA_TICKS =
            SynchedEntityData.defineId(AbstractOreGolemEntity.class, EntityDataSerializers.INT);

    private BlockPos homeChestPos;
    private final Map<BlockPos, Long> ignoredBlocksUntilTick = new HashMap<>();

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
        // High-frequency stroll (every ~25 ticks) so golems actively move around when between tasks
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D, 25));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    /**
     * Called when summoned via Carved Pumpkin or Spawn Egg with an initial facing direction.
     */
    public void onSummonedWithDirection(Direction direction) {
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
            BlockPos spawnPos = this.getLogicalBlockPos();
            Direction facing = this.getDirection();
            Player nearestPlayer = serverLevel.getNearestPlayer(this, 8.0D);
            if (nearestPlayer != null) {
                facing = nearestPlayer.getDirection();
                this.setYRot(facing.toYRot());
            }

            // Place the paired Copper Chest behind or beside the golem if possible, or at spawnPos
            BlockPos chestPos = findChestPlacementNextToSpawn(serverLevel, spawnPos, facing);
            if (chestPos != null) {
                BlockState chestState = CopperChestBlock.getFromCopperBlock(Blocks.COPPER_BLOCK, facing, serverLevel, chestPos);
                serverLevel.setBlock(chestPos, chestState, 3);
                this.setHomeChestPos(chestPos);

                if (chestPos.equals(spawnPos)) {
                    BlockPos walkPos = findAdjacentWalkablePos(serverLevel, chestPos, facing);
                    this.snapTo(walkPos.getX() + 0.5D, walkPos.getY(), walkPos.getZ() + 0.5D, facing.toYRot(), 0.0F);
                }
            }
            this.onSummonedWithDirection(facing);
        }
        return data;
    }

    public static BlockPos findChestPlacementNextToSpawn(ServerLevel level, BlockPos spawnPos, Direction facing) {
        Direction[] dirs = new Direction[]{
                facing.getOpposite(),
                facing.getCounterClockWise(),
                facing.getClockWise(),
                facing
        };
        for (Direction dir : dirs) {
            BlockPos candidate = spawnPos.relative(dir);
            if (level.getBlockState(candidate).isAir() && !level.getBlockState(candidate.below()).isAir()) {
                return candidate;
            }
        }
        if (level.getBlockState(spawnPos).isAir()) {
            return spawnPos;
        }
        return null;
    }

    public static BlockPos findAdjacentWalkablePos(ServerLevel level, BlockPos chestPos, Direction preferredDir) {
        Direction[] dirs = new Direction[]{
                preferredDir,
                preferredDir.getClockWise(),
                preferredDir.getCounterClockWise(),
                preferredDir.getOpposite()
        };
        for (Direction dir : dirs) {
            BlockPos candidate = chestPos.relative(dir);
            if (level.getBlockState(candidate).isAir() && !level.getBlockState(candidate.below()).isAir()) {
                return candidate;
            }
        }
        return chestPos.above();
    }

    /**
     * Returns the golem's logical block position, accounting for partial-height blocks
     * like Copper Chests (0.875) and Farmland (0.9375) so Y is never truncated down by 1.
     */
    public BlockPos getLogicalBlockPos() {
        return BlockPos.containing(this.getX(), this.getY() + 0.2D, this.getZ());
    }

    /**
     * Temporarily ignores an unreachable or stuck target BlockPos for durationTicks,
     * allowing the golem to cancel its current order after 2 seconds and do something else.
     */
    public void ignoreBlockTemporarily(BlockPos pos, int durationTicks) {
        if (pos != null && this.level() != null) {
            this.ignoredBlocksUntilTick.put(pos.immutable(), this.level().getGameTime() + durationTicks);
        }
    }

    public boolean isBlockIgnored(BlockPos pos) {
        if (pos == null || this.level() == null || this.ignoredBlocksUntilTick.isEmpty()) {
            return false;
        }
        Long until = this.ignoredBlocksUntilTick.get(pos);
        if (until == null) {
            return false;
        }
        if (this.level().getGameTime() >= until) {
            this.ignoredBlocksUntilTick.remove(pos);
            return false;
        }
        return true;
    }

    /**
     * Moves the golem reliably toward a target BlockPos using both PathNavigation (accuracy 0)
     * and direct MoveControl when close, avoiding vanilla accuracy=1 short-circuit stalls.
     */
    public void moveTowardsBlock(BlockPos target, double speed) {
        if (target == null) {
            return;
        }
        double cx = target.getX() + 0.5D;
        double cy = target.getY();
        double cz = target.getZ() + 0.5D;
        double distSqr = this.distanceToSqr(cx, cy, cz);

        if (distSqr <= 9.0D) {
            this.getMoveControl().setWantedPosition(cx, cy, cz, speed);
        }
        Path path = this.getNavigation().createPath(target, 0);
        if (path != null) {
            this.getNavigation().moveTo(path, speed);
        } else {
            this.getMoveControl().setWantedPosition(cx, cy, cz, speed);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isWorking()) {
            this.entityData.set(DATA_ANTENNA_TICKS, this.getAntennaTicks() + 1);
        }

        if (!this.level().isClientSide()) {
            // Periodically prune expired ignored blocks
            if (this.tickCount % 40 == 0 && !this.ignoredBlocksUntilTick.isEmpty()) {
                long now = this.level().getGameTime();
                Iterator<Map.Entry<BlockPos, Long>> it = this.ignoredBlocksUntilTick.entrySet().iterator();
                while (it.hasNext()) {
                    if (now >= it.next().getValue()) {
                        it.remove();
                    }
                }
            }

            // If standing directly on top of a Chest or Barrel, gently step off onto solid ground so pathfinding never stalls
            BlockPos belowFeet = BlockPos.containing(this.getX(), this.getY() - 0.1D, this.getZ());
            BlockState belowState = this.level().getBlockState(belowFeet);
            if (belowState.getBlock() instanceof ChestBlock || belowState.getBlock() instanceof BarrelBlock) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos adj = belowFeet.relative(dir);
                    if (this.level().getBlockState(adj).isAir() && !this.level().getBlockState(adj.below()).isAir()) {
                        this.getMoveControl().setWantedPosition(adj.getX() + 0.5D, adj.getY(), adj.getZ() + 0.5D, 1.05D);
                        break;
                    }
                }
            }
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
     * Returns the golem's own paired home Chest if valid and in range (and not temporarily ignored),
     * or scans for the nearest Chest/Barrel.
     */
    public BlockPos findNearestChestOrBarrel(BlockPos origin, int radius) {
        Level level = this.level();
        if (this.homeChestPos != null
                && !this.isBlockIgnored(this.homeChestPos)
                && origin.distSqr(this.homeChestPos) <= (double) (radius * radius * 4)) {
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
            if (this.isBlockIgnored(checkPos)) {
                continue;
            }
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
