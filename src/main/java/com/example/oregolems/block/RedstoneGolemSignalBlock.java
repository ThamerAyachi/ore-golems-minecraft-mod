package com.example.oregolems.block;

import com.example.oregolems.entity.variant.RedstoneGolemEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Invisible, air-like signal emitter block placed at a stationary Redstone Golem's position
 * so that the block directly underneath and all adjacent blocks receive a level 15 Redstone signal.
 */
public class RedstoneGolemSignalBlock extends AirBlock {
    public static final MapCodec<AirBlock> CODEC = simpleCodec(RedstoneGolemSignalBlock::new);

    public RedstoneGolemSignalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 15;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 15;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()) {
            notifyAllSurrounding(level, pos);
            level.scheduleTick(pos, this, 10);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        notifyAllSurrounding(level, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        AABB box = new AABB(pos).inflate(0.75D);
        List<RedstoneGolemEntity> golems = level.getEntitiesOfClass(
                RedstoneGolemEntity.class,
                box,
                g -> g.isAlive() && g.isEmittingRedstoneSignal() && g.blockPosition().equals(pos)
        );
        if (golems.isEmpty()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            notifyAllSurrounding(level, pos);
        } else {
            level.scheduleTick(pos, this, 10);
        }
    }

    public static void notifyAllSurrounding(Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, Blocks.REDSTONE_BLOCK);
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), Blocks.REDSTONE_BLOCK);
        }
    }
}
