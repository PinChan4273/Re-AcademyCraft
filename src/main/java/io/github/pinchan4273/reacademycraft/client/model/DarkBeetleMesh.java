package io.github.pinchan4273.reacademycraft.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** 未元物質の甲虫の骨（面は dark_matter_beetle.facets）。 art-src/dark_matter/generate_models.py が作る。手で書き換えない。 */
public final class DarkBeetleMesh {
    private DarkBeetleMesh() { }
    private static final float DEG = (float) (Math.PI / 180);
    public static LayerDefinition create() {
        var mesh = new MeshDefinition();
        PartDefinition p1 = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 21.0323f, -5.871f, 0f * DEG, 0f * DEG, 0f * DEG));
        part0(p1);
        return LayerDefinition.create(mesh, 16, 16);
    }
    private static void part0(PartDefinition parent) {
        PartDefinition p2 = parent.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, -10.0645f, 0f, 0f * DEG, 0f * DEG, 0f * DEG));
        p2.addOrReplaceChild("abdomen", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 0f, 2.3226f, 0f * DEG, 0f * DEG, 0f * DEG));
        p2.addOrReplaceChild("elytron_left", CubeListBuilder.create(), PartPose.offsetAndRotation(0.2323f, -5.4194f, 2.3226f, 0f * DEG, 0f * DEG, 0f * DEG));
        p2.addOrReplaceChild("elytron_right", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.2323f, -5.4194f, 2.3226f, 0f * DEG, 0f * DEG, 0f * DEG));
        p2.addOrReplaceChild("flight_wing_left", CubeListBuilder.create(), PartPose.offsetAndRotation(0.7742f, -4.6452f, 4.6452f, 0f * DEG, 0f * DEG, 0f * DEG));
        p2.addOrReplaceChild("flight_wing_right", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.7742f, -4.6452f, 4.6452f, 0f * DEG, 0f * DEG, 0f * DEG));
        PartDefinition p8 = p2.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 0.7742f, -9.2903f, 0f * DEG, 0f * DEG, 0f * DEG));
        PartDefinition p9 = p8.addOrReplaceChild("horn_main", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, -1.5484f, -5.4194f, -42f * DEG, 0f * DEG, 0f * DEG));
        PartDefinition p10 = p9.addOrReplaceChild("horn_main_mid", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 0f, -7.8503f, 0f * DEG, 0f * DEG, 0f * DEG));
        PartDefinition p11 = p10.addOrReplaceChild("horn_main_end", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 0f, -8.4542f, 0f * DEG, 0f * DEG, 0f * DEG));
        PartDefinition p12 = p11.addOrReplaceChild("horn_plate", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 1.8116f, -0.6039f, -40f * DEG, 0f * DEG, 0f * DEG));
        p12.addOrReplaceChild("muzzle", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 2.5665f, -8.0617f, 0f * DEG, 0f * DEG, 0f * DEG));
        p2.addOrReplaceChild("horn_rear", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, -7.8968f, -6.1935f, 0f * DEG, 0f * DEG, 0f * DEG));
        PartDefinition p15 = p2.addOrReplaceChild("leg_front_left", CubeListBuilder.create(), PartPose.offsetAndRotation(7.7419f, 3.0968f, -6.1935f, 0f * DEG, 42f * DEG, 0f * DEG));
        PartDefinition p16 = p15.addOrReplaceChild("leg_front_left_femur", CubeListBuilder.create(), PartPose.offsetAndRotation(2.3226f, 0f, 0f, 0f * DEG, 0f * DEG, -15f * DEG));
        PartDefinition p17 = p16.addOrReplaceChild("leg_front_left_tibia", CubeListBuilder.create(), PartPose.offsetAndRotation(7.7419f, 0f, 0f, 0f * DEG, 0f * DEG, 70f * DEG));
        p17.addOrReplaceChild("leg_front_left_tarsus", CubeListBuilder.create(), PartPose.offsetAndRotation(10.0645f, 0f, 0f, 0f * DEG, 0f * DEG, -40f * DEG));
        PartDefinition p19 = p2.addOrReplaceChild("leg_middle_left", CubeListBuilder.create(), PartPose.offsetAndRotation(7.7419f, 3.0968f, 3.0968f, 0f * DEG, 6f * DEG, 0f * DEG));
        PartDefinition p20 = p19.addOrReplaceChild("leg_middle_left_femur", CubeListBuilder.create(), PartPose.offsetAndRotation(2.3226f, 0f, 0f, 0f * DEG, 0f * DEG, -15f * DEG));
        PartDefinition p21 = p20.addOrReplaceChild("leg_middle_left_tibia", CubeListBuilder.create(), PartPose.offsetAndRotation(7.7419f, 0f, 0f, 0f * DEG, 0f * DEG, 70f * DEG));
        p21.addOrReplaceChild("leg_middle_left_tarsus", CubeListBuilder.create(), PartPose.offsetAndRotation(10.0645f, 0f, 0f, 0f * DEG, 0f * DEG, -40f * DEG));
        PartDefinition p23 = p2.addOrReplaceChild("leg_rear_left", CubeListBuilder.create(), PartPose.offsetAndRotation(7.7419f, 3.0968f, 13.1613f, 0f * DEG, -34f * DEG, 0f * DEG));
        PartDefinition p24 = p23.addOrReplaceChild("leg_rear_left_femur", CubeListBuilder.create(), PartPose.offsetAndRotation(2.3226f, 0f, 0f, 0f * DEG, 0f * DEG, -15f * DEG));
        PartDefinition p25 = p24.addOrReplaceChild("leg_rear_left_tibia", CubeListBuilder.create(), PartPose.offsetAndRotation(7.7419f, 0f, 0f, 0f * DEG, 0f * DEG, 70f * DEG));
        p25.addOrReplaceChild("leg_rear_left_tarsus", CubeListBuilder.create(), PartPose.offsetAndRotation(10.0645f, 0f, 0f, 0f * DEG, 0f * DEG, -40f * DEG));
        PartDefinition p27 = p2.addOrReplaceChild("leg_front_right", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.7419f, 3.0968f, -6.1935f, 0f * DEG, -42f * DEG, 0f * DEG));
        PartDefinition p28 = p27.addOrReplaceChild("leg_front_right_femur", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.3226f, 0f, 0f, 0f * DEG, 0f * DEG, 15f * DEG));
        PartDefinition p29 = p28.addOrReplaceChild("leg_front_right_tibia", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.7419f, 0f, 0f, 0f * DEG, 0f * DEG, -70f * DEG));
        p29.addOrReplaceChild("leg_front_right_tarsus", CubeListBuilder.create(), PartPose.offsetAndRotation(-10.0645f, 0f, 0f, 0f * DEG, 0f * DEG, 40f * DEG));
        PartDefinition p31 = p2.addOrReplaceChild("leg_middle_right", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.7419f, 3.0968f, 3.0968f, 0f * DEG, -6f * DEG, 0f * DEG));
        PartDefinition p32 = p31.addOrReplaceChild("leg_middle_right_femur", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.3226f, 0f, 0f, 0f * DEG, 0f * DEG, 15f * DEG));
        PartDefinition p33 = p32.addOrReplaceChild("leg_middle_right_tibia", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.7419f, 0f, 0f, 0f * DEG, 0f * DEG, -70f * DEG));
        p33.addOrReplaceChild("leg_middle_right_tarsus", CubeListBuilder.create(), PartPose.offsetAndRotation(-10.0645f, 0f, 0f, 0f * DEG, 0f * DEG, 40f * DEG));
        PartDefinition p35 = p2.addOrReplaceChild("leg_rear_right", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.7419f, 3.0968f, 13.1613f, 0f * DEG, 34f * DEG, 0f * DEG));
        PartDefinition p36 = p35.addOrReplaceChild("leg_rear_right_femur", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.3226f, 0f, 0f, 0f * DEG, 0f * DEG, 15f * DEG));
        PartDefinition p37 = p36.addOrReplaceChild("leg_rear_right_tibia", CubeListBuilder.create(), PartPose.offsetAndRotation(-7.7419f, 0f, 0f, 0f * DEG, 0f * DEG, -70f * DEG));
        p37.addOrReplaceChild("leg_rear_right_tarsus", CubeListBuilder.create(), PartPose.offsetAndRotation(-10.0645f, 0f, 0f, 0f * DEG, 0f * DEG, 40f * DEG));
    }
}
