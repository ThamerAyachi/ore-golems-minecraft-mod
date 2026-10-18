package com.example.oregolems.entity.variant;

import com.example.oregolems.entity.AbstractOreGolemEntity;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Gold Golem (gold_golem) - Redstone & Barter Assistant.
 * Periodically presses nearby Stone/Wooden Buttons and flips Levers (classic Copper Golem style).
 * If given a Gold Ingot, produces a random Piglin bartering drop after 5 seconds (100 ticks).
 * Includes a 2-second (40-tick) timeout so unreachable buttons/levers are cancelled and skipped.
 */
public class GoldGolemEntity extends AbstractOreGolemEntity {
    private int barterTicksRemaining = 0;

    public GoldGolemEntity(EntityType<? extends GoldGolemEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseGolemAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new BarterWithGoldGoal(this));
        this.goalSelector.addGoal(2, new PressButtonsAndLeversGoal(this, 8));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.GOLD_INGOT) && this.barterTicksRemaining <= 0) {
            if (!this.level().isClientSide()) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                this.barterTicksRemaining = 100; // 5 seconds (100 ticks)
                this.setWorking(true);
                this.playSound(SoundEvents.PIGLIN_ADMIRING_ITEM, 1.0F, 1.2F);
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            this.getX(), this.getY() + 0.8D, this.getZ(),
                            10, 0.3D, 0.3D, 0.3D, 0.05D);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    public int getBarterTicksRemaining() {
        return this.barterTicksRemaining;
    }

    public void setBarterTicksRemaining(int ticks) {
        this.barterTicksRemaining = ticks;
    }

    public void spawnBarterDrops(ServerLevel serverLevel) {
        LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.PIGLIN_BARTERING);
        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.THIS_ENTITY, this)
                .create(LootContextParamSets.PIGLIN_BARTER);
        ObjectArrayList<ItemStack> drops = lootTable.getRandomItems(params);

        if (drops.isEmpty()) {
            ItemStack[] fallback = new ItemStack[]{
                    new ItemStack(Items.ENDER_PEARL, 2),
                    new ItemStack(Items.OBSIDIAN, 1),
                    new ItemStack(Items.CRYING_OBSIDIAN, 1),
                    new ItemStack(Items.QUARTZ, 8),
                    new ItemStack(Items.SPECTRAL_ARROW, 8),
                    new ItemStack(Items.FIRE_CHARGE, 1),
                    new ItemStack(Items.LEATHER, 3)
            };
            this.spawnAtLocation(serverLevel, fallback[this.random.nextInt(fallback.length)].copy());
        } else {
            for (ItemStack drop : drops) {
                this.spawnAtLocation(serverLevel, drop.copy());
            }
        }

        this.playSound(SoundEvents.PIGLIN_CELEBRATE, 1.0F, 1.2F);
        serverLevel.sendParticles(ParticleTypes.WAX_ON,
                this.getX(), this.getY() + 0.7D, this.getZ(),
                15, 0.3D, 0.3D, 0.3D, 0.05D);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("BarterTicksRemaining", this.barterTicksRemaining);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.barterTicksRemaining = input.getIntOr("BarterTicksRemaining", 0);
    }

    @Override
    public String getGolemTextureName() {
        return "gold_golem";
    }

    /**
     * AI Goal: Handles the 5-second (100-tick) Piglin bartering timer after receiving a Gold Ingot.
     */
    public static class BarterWithGoldGoal extends Goal {
        private final GoldGolemEntity golem;

        public BarterWithGoldGoal(GoldGolemEntity golem) {
            this.golem = golem;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.golem.getBarterTicksRemaining() > 0;
        }

        @Override
        public boolean canContinueToUse() {
            return this.golem.getBarterTicksRemaining() > 0;
        }

        @Override
        public void start() {
            this.golem.getNavigation().stop();
            this.golem.setWorking(true);
        }

        @Override
        public void tick() {
            this.golem.getNavigation().stop();
            int remaining = this.golem.getBarterTicksRemaining() - 1;
            this.golem.setBarterTicksRemaining(remaining);

            if (remaining % 20 == 0 && this.golem.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        this.golem.getX(), this.golem.getY() + 0.85D, this.golem.getZ(),
                        4, 0.2D, 0.2D, 0.2D, 0.02D);
            }

            if (remaining <= 0 && this.golem.level() instanceof ServerLevel serverLevel) {
                this.golem.spawnBarterDrops(serverLevel);
                this.golem.setWorking(false);
            }
        }

        @Override
        public void stop() {
            this.golem.setWorking(false);
        }
    }

    /**
     * AI Goal: Periodically seeks and presses nearby Stone/Wooden Buttons and flips Levers.
     * Cancels and blacklists unreachable buttons/levers after 2 seconds (40 ticks).
     */
    public static class PressButtonsAndLeversGoal extends Goal {
        private final GoldGolemEntity golem;
        private final int radius;
        private BlockPos targetPos;
        private int cooldown;
        private int stuckTicks;

        public PressButtonsAndLeversGoal(GoldGolemEntity golem, int radius) {
            this.golem = golem;
            this.radius = radius;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.golem.getBarterTicksRemaining() > 0) {
                return false;
            }
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            this.cooldown = 25 + this.golem.getRandom().nextInt(25);
            this.targetPos = findRandomButtonOrLever();
            return this.targetPos != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.targetPos != null
                    && this.stuckTicks < MAX_TASK_STUCK_TICKS
                    && this.golem.getBarterTicksRemaining() <= 0
                    && isButtonOrLever(this.golem.level().getBlockState(this.targetPos));
        }

        @Override
        public void start() {
            this.stuckTicks = 0;
            if (this.targetPos != null) {
                this.golem.setWorking(true);
                this.golem.moveTowardsBlock(this.targetPos, 1.05D);
            }
        }

        @Override
        public void tick() {
            if (this.targetPos == null) {
                return;
            }
            Vec3 center = Vec3.atCenterOf(this.targetPos);
            this.golem.getLookControl().setLookAt(center.x, center.y, center.z);

            this.stuckTicks++;
            if (this.stuckTicks % 8 == 0) {
                this.golem.moveTowardsBlock(this.targetPos, 1.05D);
            }

            double distSqr = this.golem.distanceToSqr(center);
            if (distSqr <= 7.5D) {
                activateTargetControl();
                this.targetPos = null;
                this.cooldown = 40 + this.golem.getRandom().nextInt(40);
            } else if (this.stuckTicks >= MAX_TASK_STUCK_TICKS) {
                this.golem.ignoreBlockTemporarily(this.targetPos, 100);
                this.golem.getNavigation().stop();
                this.targetPos = null;
                this.cooldown = 15;
            }
        }

        private void activateTargetControl() {
            Level level = this.golem.level();
            BlockState state = level.getBlockState(this.targetPos);
            if (state.getBlock() instanceof ButtonBlock button) {
                if (!state.getValue(ButtonBlock.POWERED)) {
                    button.press(state, level, this.targetPos, null);
                    this.golem.playSound(SoundEvents.COPPER_GOLEM_SPIN, 0.8F, 1.2F);
                }
            } else if (state.getBlock() instanceof LeverBlock lever) {
                lever.pull(state, level, this.targetPos, null);
                level.playSound(null, this.targetPos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 1.0F);
                this.golem.playSound(SoundEvents.COPPER_GOLEM_SPIN, 0.8F, 1.2F);
            }
        }

        @Override
        public void stop() {
            this.targetPos = null;
            this.stuckTicks = 0;
            this.golem.setWorking(false);
        }

        private BlockPos findRandomButtonOrLever() {
            BlockPos origin = this.golem.getLogicalBlockPos();
            List<BlockPos> candidates = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(
                    origin.offset(-this.radius, -2, -this.radius),
                    origin.offset(this.radius, 2, this.radius))) {
                if (!this.golem.isBlockIgnored(pos) && isButtonOrLever(this.golem.level().getBlockState(pos))) {
                    candidates.add(pos.immutable());
                }
            }
            if (candidates.isEmpty()) {
                return null;
            }
            return candidates.get(this.golem.getRandom().nextInt(candidates.size()));
        }

        private boolean isButtonOrLever(BlockState state) {
            return state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof LeverBlock;
        }
    }
}
