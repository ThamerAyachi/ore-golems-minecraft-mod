package com.example.oregolems.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * HierarchicalModel base class bridging hierarchical ModelPart root models with 1.21.11's EntityModel.
 */
public abstract class HierarchicalModel<S extends LivingEntityRenderState> extends EntityModel<S> {
    protected HierarchicalModel(ModelPart root) {
        super(root);
    }
}
