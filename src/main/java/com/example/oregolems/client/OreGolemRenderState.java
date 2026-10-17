package com.example.oregolems.client;

import com.example.oregolems.OreGolemsMod;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Render state for all Ore Golem variants in Minecraft 1.21.11.
 */
public class OreGolemRenderState extends LivingEntityRenderState {
    public Identifier texture = Identifier.fromNamespaceAndPath(OreGolemsMod.MODID, "textures/entity/coal_golem.png");
    public boolean working = false;
    public float antennaTicks = 0.0F;
}
