package com.example.oregolems.client;

import com.example.oregolems.entity.AbstractOreGolemEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Client-side EntityRenderer for all Ore Golem variants.
 */
public class OreGolemRenderer extends MobRenderer<AbstractOreGolemEntity, OreGolemRenderState, OreGolemModel> {
    public OreGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new OreGolemModel(context.bakeLayer(OreGolemModel.LAYER_LOCATION)), 0.4F);
    }

    @Override
    public OreGolemRenderState createRenderState() {
        return new OreGolemRenderState();
    }

    @Override
    public void extractRenderState(AbstractOreGolemEntity entity, OreGolemRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.texture = entity.getTextureIdentifier();
        state.working = entity.isWorking();
        state.antennaTicks = entity.getAntennaTicks() + partialTick;
    }

    @Override
    public Identifier getTextureLocation(OreGolemRenderState state) {
        return state.texture;
    }
}
