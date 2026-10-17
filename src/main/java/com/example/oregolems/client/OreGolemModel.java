package com.example.oregolems.client;

import com.example.oregolems.OreGolemsMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Exact 1:1 Copper Golem 3D geometry and UV layout (64x64) from Minecraft 1.21.11,
 * with an articulated top lightning rod/stem that spins when the golem is working.
 */
public class OreGolemModel extends HierarchicalModel<OreGolemRenderState> {
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(OreGolemsMod.MODID, "ore_golem"), "main");

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rodStem;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public OreGolemModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = this.body.getChild("head");
        this.rodStem = this.head.getChild("rod_stem");
        this.rightArm = this.body.getChild("right_arm");
        this.leftArm = this.body.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition().transformed(pose -> pose.translated(0.0F, 24.0F, 0.0F));
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 15)
                        .addBox(-4.0F, -6.0F, -3.0F, 8.0F, 6.0F, 6.0F, CubeDeformation.NONE),
                PartPose.offset(0.0F, -5.0F, 0.0F)
        );

        PartDefinition head = body.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0F, -5.0F, -5.0F, 8.0F, 5.0F, 10.0F, new CubeDeformation(0.015F))
                        .texOffs(56, 0)
                        .addBox(-1.0F, -2.0F, -6.0F, 2.0F, 3.0F, 2.0F, CubeDeformation.NONE),
                PartPose.offset(0.0F, -6.0F, 0.0F)
        );

        head.addOrReplaceChild(
                "rod_stem",
                CubeListBuilder.create()
                        .texOffs(37, 8)
                        .addBox(-1.0F, -9.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(-0.015F))
                        .texOffs(37, 0)
                        .addBox(-2.0F, -13.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(-0.015F)),
                PartPose.ZERO
        );

        body.addOrReplaceChild(
                "right_arm",
                CubeListBuilder.create()
                        .texOffs(36, 16)
                        .addBox(-3.0F, -1.0F, -2.0F, 3.0F, 10.0F, 4.0F, CubeDeformation.NONE),
                PartPose.offset(-4.0F, -6.0F, 0.0F)
        );

        body.addOrReplaceChild(
                "left_arm",
                CubeListBuilder.create()
                        .texOffs(50, 16)
                        .addBox(0.0F, -1.0F, -2.0F, 3.0F, 10.0F, 4.0F, CubeDeformation.NONE),
                PartPose.offset(4.0F, -6.0F, 0.0F)
        );

        root.addOrReplaceChild(
                "right_leg",
                CubeListBuilder.create()
                        .texOffs(0, 27)
                        .addBox(-4.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F, CubeDeformation.NONE),
                PartPose.offset(0.0F, -5.0F, 0.0F)
        );

        root.addOrReplaceChild(
                "left_leg",
                CubeListBuilder.create()
                        .texOffs(16, 27)
                        .addBox(0.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F, CubeDeformation.NONE),
                PartPose.offset(0.0F, -5.0F, 0.0F)
        );

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(OreGolemRenderState state) {
        super.setupAnim(state);

        this.head.yRot = state.yRot * ((float) Math.PI / 180.0F);
        this.head.xRot = state.xRot * ((float) Math.PI / 180.0F);

        float walkPos = state.walkAnimationPos;
        float walkSpeed = state.walkAnimationSpeed;

        this.rightLeg.xRot = Mth.cos(walkPos * 0.6662F) * 1.4F * walkSpeed;
        this.leftLeg.xRot = Mth.cos(walkPos * 0.6662F + (float) Math.PI) * 1.4F * walkSpeed;

        if (state.working) {
            this.rodStem.yRot = state.antennaTicks * 0.35F;
            this.rightArm.xRot = -0.65F + Mth.sin(state.antennaTicks * 0.4F) * 0.35F;
            this.leftArm.xRot = -0.65F - Mth.sin(state.antennaTicks * 0.4F) * 0.35F;
        } else {
            this.rodStem.yRot = 0.0F;
            this.rightArm.xRot = Mth.cos(walkPos * 0.6662F + (float) Math.PI) * 1.2F * walkSpeed;
            this.leftArm.xRot = Mth.cos(walkPos * 0.6662F) * 1.2F * walkSpeed;
        }
    }
}
