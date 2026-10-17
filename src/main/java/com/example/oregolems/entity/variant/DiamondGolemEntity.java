package com.example.oregolems.entity.variant;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Diamond Golem (diamond_golem) - Automated Miner.
 * Breaks Stone, Cobblestone, Deepslate, and Ore blocks along a tunnel path when toggled active
 * with a Pickaxe, depositing mined drops into a chest behind its starting position.
 */
public class DiamondGolemEntity extends AbstractMinerGolemEntity {
    public DiamondGolemEntity(EntityType<? extends DiamondGolemEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseGolemAttributes()
                .add(Attributes.MAX_HEALTH, 45.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D)
                .add(Attributes.ARMOR, 8.0D);
    }

    @Override
    public int getTicksPerBlock() {
        return 20; // Standard mining rate (1 block per second)
    }

    @Override
    public String getGolemTextureName() {
        return "diamond_golem";
    }
}
