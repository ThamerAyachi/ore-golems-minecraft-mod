package com.example.oregolems.entity.variant;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Netherite Golem (netherite_golem) - Heavy-Duty Automated Miner.
 * Mines 2x faster than the Diamond Golem (10 ticks per block) and is completely immune to fire and lava.
 */
public class NetheriteGolemEntity extends AbstractMinerGolemEntity {
    public NetheriteGolemEntity(EntityType<? extends NetheriteGolemEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseGolemAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    @Override
    public int getTicksPerBlock() {
        return 10; // 2x faster than Diamond Golem (10 ticks vs 20 ticks)
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public String getGolemTextureName() {
        return "netherite_golem";
    }
}
