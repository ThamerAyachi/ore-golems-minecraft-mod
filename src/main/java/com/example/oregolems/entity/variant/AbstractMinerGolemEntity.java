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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.common.Tags;

import java.util.EnumSet;
import java.util.List;

/**
 * Shared base class for Diamond and Netherite Automated Miner Golems.
 * Breaks Stone, Cobblestone, Deepslate, and Ore blocks directly in front of it along a tunnel path
 * when toggled active (right-clicked with a Pickaxe), depositing mined blocks into a chest placed
 * behind its starting position.
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
        builder.define(DATA_MINING_ACTIVE, false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new TunnelMinerGoal(this));
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

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ItemTags.PICKAXES)) {
            if (!this.level().isClientSide()) {
                boolean next = !this.isMiningActive();
                this.setMiningActive(next);
                if (next) {
                    this.startPos = this.blockPosition().immutable();
                    this.miningDirection = player.getDirection();
                    this.setYRot(this.miningDirection.toYRot());
                    player.displayClientMessage(
                            Component.literal(this.getName().getString() + ": Tunnel Mining ACTIVE (Direction: " + this.miningDirection.getName() + ")"),
                            true
                    );
                    this.playSound(SoundEvents.ANVIL_USE, 0.7F, 1.4F);
                } else {
                    player.displayClientMessage(
                            Component.literal(this.getName().getString() + ": Tunnel Mining PAUSED"),
                            true
                    );
                    this.playSound(SoundEvents.COPPER_GOLEM_SPIN, 0.8F, 0.9F);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    public static boolean isMineableTunnelBlock(BlockState state) {
        if (state.isAir()) {
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
                || state.is(Blocks.NETHERRACK)
                || state.is(BlockTags.STONE_ORE_REPLACEABLES)
                || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || state.is(Tags.Blocks.STONES)
                || state.is(Tags.Blocks.COBBLESTONES)
                || state.is(Tags.Blocks.ORES);
    }

    public void depositOrDropMinedItems(ServerLevel serverLevel, List<ItemStack> drops) {
        BlockPos origin = this.startPos != null ? this.startPos : this.blockPosition();
        BlockPos behindStart = origin.relative(this.miningDirection.getOpposite());

        Container container = null;
        if (this.getHomeChestPos() != null && serverLevel.getBlockEntity(this.getHomeChestPos()) instanceof Container homeC) {
            container = homeC;
        } else if (serverLevel.getBlockEntity(behindStart) instanceof Container directC) {
            container = directC;
        } else {
            BlockPos nearestChest = this.findNearestChestOrBarrel(behindStart, 4);
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
                ItemEntity itemEntity = new ItemEntity(
                        serverLevel,
                        behindStart.getX() + 0.5D,
                        behindStart.getY() + 0.5D,
                        behindStart.getZ() + 0.5D,
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
        this.setMiningActive(input.getBooleanOr("MiningActive", false));
        String dirName = input.getStringOr("MiningDirection", "north");
        Direction parsed = Direction.byName(dirName);
        this.miningDirection = (parsed != null && parsed.getAxis().isHorizontal()) ? parsed : Direction.NORTH;
        if (input.getInt("StartX").isPresent()) {
            this.startPos = new BlockPos(
                    input.getIntOr("StartX", this.blockPosition().getX()),
                    input.getIntOr("StartY", this.blockPosition().getY()),
                    input.getIntOr("StartZ", this.blockPosition().getZ())
            );
        }
    }

    /**
     * AI Goal: Mines a 1x2 tunnel path directly in front of the golem when toggled active,
     * depositing drops into the chest behind its starting position.
     */
    public static class TunnelMinerGoal extends Goal {
        private final AbstractMinerGolemEntity golem;
        private int breakProgressTicks;
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
            return this.golem.isMiningActive() && this.stepsMined < 64;
        }

        @Override
        public void start() {
            this.breakProgressTicks = 0;
            this.stepsMined = 0;
            this.golem.setWorking(true);
            if (this.golem.startPos == null) {
                this.golem.startPos = this.golem.blockPosition().immutable();
            }
        }

        @Override
        public void tick() {
            if (!(this.golem.level() instanceof ServerLevel serverLevel)) {
                return;
            }
            Direction dir = this.golem.getMiningDirection();
            BlockPos frontFoot = this.golem.blockPosition().relative(dir);
            BlockPos frontHead = frontFoot.above();

            this.golem.getLookControl().setLookAt(
                    frontFoot.getX() + 0.5D,
                    frontFoot.getY() + 0.5D,
                    frontFoot.getZ() + 0.5D
            );

            BlockPos targetToBreak = null;
            if (isMineableTunnelBlock(serverLevel.getBlockState(frontHead))) {
                targetToBreak = frontHead;
            } else if (isMineableTunnelBlock(serverLevel.getBlockState(frontFoot))) {
                targetToBreak = frontFoot;
            }

            if (targetToBreak != null) {
                this.golem.getNavigation().stop();
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
                    List<ItemStack> drops = Block.getDrops(targetState, serverLevel, targetToBreak, serverLevel.getBlockEntity(targetToBreak), this.golem, virtualTool);
                    serverLevel.destroyBlock(targetToBreak, false, this.golem);
                    this.golem.depositOrDropMinedItems(serverLevel, drops);
                    this.breakProgressTicks = 0;
                }
            } else {
                this.breakProgressTicks = 0;
                // Advance one block forward along the tunnel if the space in front is clear
                if (serverLevel.getBlockState(frontFoot).isAir() && serverLevel.getBlockState(frontHead).isAir()) {
                    this.golem.getNavigation().moveTo(
                            frontFoot.getX() + 0.5D,
                            frontFoot.getY(),
                            frontFoot.getZ() + 0.5D,
                            1.0D
                    );
                    if (this.golem.blockPosition().equals(frontFoot)) {
                        this.stepsMined++;
                    }
                } else {
                    // Stop mining if an unbreakable obstacle (e.g., Bedrock or Chest) blocks the tunnel
                    this.golem.setMiningActive(false);
                }
            }
        }

        @Override
        public void stop() {
            this.breakProgressTicks = 0;
            this.golem.setWorking(false);
        }
    }
}
