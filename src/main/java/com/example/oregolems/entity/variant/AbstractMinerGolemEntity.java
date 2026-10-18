package com.example.oregolems.entity.variant;

import com.example.oregolems.entity.AbstractOreGolemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.common.Tags;

import java.util.EnumSet;
import java.util.List;

/**
 * Shared base class for Diamond and Netherite Automated Miner Golems.
 * Automatically mines a 1x2 tunnel path in front of it (and any exposed ores on the tunnel walls),
 * deposits mined blocks into its paired Copper Chest (or nearest chest), and automatically turns
 * or advances if blocked for more than 2 seconds (40 ticks) so it never gets stuck.
 */
public abstract class AbstractMinerGolemEntity extends AbstractOreGolemEntity {
    private static final EntityDataAccessor<Boolean> DATA_MINING_ACTIVE =
            SynchedEntityData.defineId(AbstractMinerGolemEntity.class, EntityDataSerializers.BOOLEAN);

    private BlockPos startPos;
    private Direction miningDirection = Direction.NORTH;

    protected AbstractMinerGolemEntity(EntityType<? extends AbstractMinerGolemEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MINING_ACTIVE, true);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new TunnelMinerGoal(this));
    }

    @Override
    public void onSummonedWithDirection(Direction direction) {
        if (direction != null && direction.getAxis().isHorizontal()) {
            this.miningDirection = direction;
            this.setYRot(direction.toYRot());
        }
        this.startPos = this.getLogicalBlockPos().immutable();
        this.setMiningActive(true);
    }

    /**
     * Number of ticks required to mine a single block.
     * Diamond Golem = 20 ticks; Netherite Golem = 10 ticks (2x faster).
     */
    public abstract int getTicksPerBlock();

    public boolean isMiningActive() {
        return this.entityData.get(DATA_MINING_ACTIVE);
    }

    public void setMiningActive(boolean active) {
        this.entityData.set(DATA_MINING_ACTIVE, active);
        this.setWorking(active);
    }

    public BlockPos getStartPos() {
        return this.startPos;
    }

    public Direction getMiningDirection() {
        return this.miningDirection;
    }

    public void setMiningDirection(Direction dir) {
        if (dir != null && dir.getAxis().isHorizontal()) {
            this.miningDirection = dir;
            this.setYRot(dir.toYRot());
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return super.mobInteract(player, hand);
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ItemTags.PICKAXES) || stack.isEmpty()) {
            if (!this.level().isClientSide()) {
                boolean next = !this.isMiningActive();
                this.setMiningActive(next);
                if (next) {
                    this.startPos = this.getLogicalBlockPos().immutable();
                    this.setMiningDirection(player.getDirection());
                    player.displayClientMessage(
                            Component.literal(this.getName().getString() + ": Tunnel Mining ACTIVE (Direction: " + this.miningDirection.getName() + ")"),
                            true
                    );
                    this.playSound(SoundEvents.ANVIL_USE, 0.7F, 1.4F);
                } else {
                    player.displayClientMessage(
                            Component.literal(this.getName().getString() + ": Tunnel Mining PAUSED (Right-click to resume)"),
                            true
                    );
                    this.playSound(SoundEvents.COPPER_GOLEM_SPIN, 0.8F, 0.9F);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    public static boolean isMineableTunnelBlock(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        // Never break containers (Chests, Copper Chests, Barrels, Furnaces) or unbreakable blocks (Bedrock)
        if (level.getBlockEntity(pos) != null) {
            return false;
        }
        float destroySpeed = state.getDestroySpeed(level, pos);
        if (destroySpeed < 0.0F || destroySpeed >= 50.0F) {
            return false;
        }
        return state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE)
                || state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SAND)
                || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.NETHERRACK)
                || state.is(Blocks.BASALT)
                || state.is(Blocks.BLACKSTONE)
                || state.is(BlockTags.STONE_ORE_REPLACEABLES)
                || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL)
                || state.is(Tags.Blocks.STONES)
                || state.is(Tags.Blocks.COBBLESTONES)
                || state.is(Tags.Blocks.ORES);
    }

    public static boolean isPassableTunnelSpace(BlockState state, ServerLevel level, BlockPos pos) {
        return state.isAir() || state.getCollisionShape(level, pos).isEmpty();
    }

    public void depositOrDropMinedItems(ServerLevel serverLevel, List<ItemStack> drops) {
        BlockPos origin = this.startPos != null ? this.startPos : this.getLogicalBlockPos();
        BlockPos behindStart = origin.relative(this.miningDirection.getOpposite());

        Container container = null;
        if (this.getHomeChestPos() != null && serverLevel.getBlockEntity(this.getHomeChestPos()) instanceof Container homeC) {
            container = homeC;
        } else if (serverLevel.getBlockEntity(behindStart) instanceof Container directC) {
            container = directC;
        } else {
            BlockPos nearestChest = this.findNearestChestOrBarrel(this.getLogicalBlockPos(), 12);
            if (nearestChest != null && serverLevel.getBlockEntity(nearestChest) instanceof Container c) {
                container = c;
            }
        }

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            ItemStack remaining = container != null ? depositIntoContainer(container, drop) : drop.copy();
            if (!remaining.isEmpty()) {
                BlockPos dropPos = this.getHomeChestPos() != null ? this.getHomeChestPos().above() : this.getLogicalBlockPos();
                ItemEntity itemEntity = new ItemEntity(
                        serverLevel,
                        dropPos.getX() + 0.5D,
                        dropPos.getY() + 0.25D,
                        dropPos.getZ() + 0.5D,
                        remaining
                );
                serverLevel.addFreshEntity(itemEntity);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("MiningActive", this.isMiningActive());
        output.putString("MiningDirection", this.miningDirection.getName());
        if (this.startPos != null) {
            output.putInt("StartX", this.startPos.getX());
            output.putInt("StartY", this.startPos.getY());
            output.putInt("StartZ", this.startPos.getZ());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setMiningActive(input.getBooleanOr("MiningActive", true));
        String dirName = input.getStringOr("MiningDirection", "north");
        Direction parsed = Direction.byName(dirName);
        this.miningDirection = (parsed != null && parsed.getAxis().isHorizontal()) ? parsed : Direction.NORTH;
        if (input.getInt("StartX").isPresent()) {
            this.startPos = new BlockPos(
                    input.getIntOr("StartX", this.getLogicalBlockPos().getX()),
                    input.getIntOr("StartY", this.getLogicalBlockPos().getY()),
                    input.getIntOr("StartZ", this.getLogicalBlockPos().getZ())
            );
        }
    }

    /**
     * AI Goal: Mines a 1x2 tunnel path directly in front of the golem (plus adjacent exposed ores),
     * deposits drops into its paired Copper Chest, and includes a 2-second (40-tick) anti-stuck
     * recovery that automatically steps forward or turns to a new direction if blocked.
     */
    public static class TunnelMinerGoal extends Goal {
        private final AbstractMinerGolemEntity golem;
        private int breakProgressTicks;
        private int advanceStuckTicks;
        private int stepsMined;

        public TunnelMinerGoal(AbstractMinerGolemEntity golem) {
            this.golem = golem;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.golem.isMiningActive();
        }

        @Override
        public boolean canContinueToUse() {
            return this.golem.isMiningActive();
        }

        @Override
        public void start() {
            this.breakProgressTicks = 0;
            this.advanceStuckTicks = 0;
            this.stepsMined = 0;
            this.golem.setWorking(true);
            if (this.golem.startPos == null) {
                this.golem.startPos = this.golem.getLogicalBlockPos().immutable();
            }
        }

        @Override
        public void tick() {
            if (!(this.golem.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            if (this.stepsMined >= 64) {
                // Turn around or pick a fresh vein after 64 blocks so the golem stays near the player's area
                this.stepsMined = 0;
                rotateToBestMiningDirection(serverLevel);
            }

            Direction dir = this.golem.getMiningDirection();
            BlockPos currentPos = this.golem.getLogicalBlockPos();
            BlockPos frontFoot = currentPos.relative(dir);
            BlockPos frontHead = frontFoot.above();

            BlockPos targetToBreak = findNextTargetToBreak(serverLevel, frontFoot, frontHead, dir);

            if (targetToBreak != null) {
                this.advanceStuckTicks = 0;
                this.golem.getNavigation().stop();
                this.golem.getLookControl().setLookAt(
                        targetToBreak.getX() + 0.5D,
                        targetToBreak.getY() + 0.5D,
                        targetToBreak.getZ() + 0.5D
                );

                this.breakProgressTicks++;
                BlockState targetState = serverLevel.getBlockState(targetToBreak);

                if (this.breakProgressTicks % 4 == 0) {
                    this.golem.playSound(targetState.getSoundType().getHitSound(), 0.6F, 1.0F);
                    serverLevel.sendParticles(
                            new BlockParticleOption(ParticleTypes.BLOCK, targetState),
                            targetToBreak.getX() + 0.5D,
                            targetToBreak.getY() + 0.5D,
                            targetToBreak.getZ() + 0.5D,
                            6, 0.2D, 0.2D, 0.2D, 0.05D
                    );
                }

                if (this.breakProgressTicks >= this.golem.getTicksPerBlock()) {
                    ItemStack virtualTool = new ItemStack(Items.NETHERITE_PICKAXE);
                    List<ItemStack> drops = Block.getDrops(
                            targetState, serverLevel, targetToBreak,
                            serverLevel.getBlockEntity(targetToBreak), this.golem, virtualTool
                    );
                    serverLevel.destroyBlock(targetToBreak, false, this.golem);
                    this.golem.depositOrDropMinedItems(serverLevel, drops);
                    this.breakProgressTicks = 0;
                }
                return;
            }

            this.breakProgressTicks = 0;

            // Check if the 1x2 space directly in front is passable
            boolean footPassable = isPassableTunnelSpace(serverLevel.getBlockState(frontFoot), serverLevel, frontFoot);
            boolean headPassable = isPassableTunnelSpace(serverLevel.getBlockState(frontHead), serverLevel, frontHead);

            if (footPassable && headPassable) {
                // Bridge any 1-block gap underneath frontFoot so the golem never gets stuck on a drop
                BlockPos belowFront = frontFoot.below();
                if (serverLevel.getBlockState(belowFront).isAir() || !serverLevel.getBlockState(belowFront).getFluidState().isEmpty()) {
                    serverLevel.setBlock(belowFront, Blocks.COBBLESTONE.defaultBlockState(), 3);
                }

                this.golem.getLookControl().setLookAt(
                        frontFoot.getX() + 0.5D,
                        frontFoot.getY() + 0.5D,
                        frontFoot.getZ() + 0.5D
                );
                this.golem.moveTowardsBlock(frontFoot, 1.0D);
                this.advanceStuckTicks++;

                if (this.golem.getLogicalBlockPos().equals(frontFoot)) {
                    this.stepsMined++;
                    this.advanceStuckTicks = 0;
                } else if (this.advanceStuckTicks >= 25) {
                    // If MoveControl/Navigation was blocked by a corner or partial block for >1.25s, step directly into frontFoot
                    this.golem.snapTo(
                            frontFoot.getX() + 0.5D,
                            frontFoot.getY(),
                            frontFoot.getZ() + 0.5D,
                            dir.toYRot(),
                            0.0F
                    );
                    this.stepsMined++;
                    this.advanceStuckTicks = 0;
                }
            } else {
                // Unbreakable obstacle (e.g., Copper Chest or Bedrock) is in front:
                // After at most 2 seconds (or immediately), rotate to a clear/mineable direction instead of freezing!
                this.advanceStuckTicks++;
                if (this.advanceStuckTicks >= 10) {
                    this.advanceStuckTicks = 0;
                    rotateToBestMiningDirection(serverLevel);
                }
            }
        }

        private BlockPos findNextTargetToBreak(ServerLevel level, BlockPos frontFoot, BlockPos frontHead, Direction dir) {
            if (isMineableTunnelBlock(level.getBlockState(frontHead), level, frontHead)) {
                return frontHead;
            }
            if (isMineableTunnelBlock(level.getBlockState(frontFoot), level, frontFoot)) {
                return frontFoot;
            }
            // Also check exposed ores immediately adjacent to the tunnel slice
            BlockPos[] adjacentOrePositions = new BlockPos[]{
                    frontHead.above(),
                    frontHead.relative(dir.getClockWise()),
                    frontHead.relative(dir.getCounterClockWise()),
                    frontFoot.relative(dir.getClockWise()),
                    frontFoot.relative(dir.getCounterClockWise())
            };
            for (BlockPos oreCheck : adjacentOrePositions) {
                BlockState s = level.getBlockState(oreCheck);
                if (s.is(Tags.Blocks.ORES) && isMineableTunnelBlock(s, level, oreCheck)) {
                    return oreCheck;
                }
            }
            return null;
        }

        private void rotateToBestMiningDirection(ServerLevel level) {
            Direction current = this.golem.getMiningDirection();
            Direction[] candidates = new Direction[]{
                    current.getClockWise(),
                    current.getCounterClockWise(),
                    current.getOpposite()
            };
            BlockPos pos = this.golem.getLogicalBlockPos();

            // Prefer a direction that immediately has mineable stone/ores
            for (Direction cand : candidates) {
                BlockPos fFoot = pos.relative(cand);
                BlockPos fHead = fFoot.above();
                if (isMineableTunnelBlock(level.getBlockState(fHead), level, fHead)
                        || isMineableTunnelBlock(level.getBlockState(fFoot), level, fFoot)) {
                    this.golem.setMiningDirection(cand);
                    return;
                }
            }
            // Otherwise prefer any passable direction that isn't blocked by a Chest/Bedrock
            for (Direction cand : candidates) {
                BlockPos fFoot = pos.relative(cand);
                BlockPos fHead = fFoot.above();
                if (isPassableTunnelSpace(level.getBlockState(fFoot), level, fFoot)
                        && isPassableTunnelSpace(level.getBlockState(fHead), level, fHead)) {
                    this.golem.setMiningDirection(cand);
                    return;
                }
            }
            this.golem.setMiningDirection(current.getClockWise());
        }

        @Override
        public void stop() {
            this.breakProgressTicks = 0;
            this.advanceStuckTicks = 0;
            this.golem.setWorking(false);
        }
    }
}
