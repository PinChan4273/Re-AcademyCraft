package io.github.pinchan4273.reacademycraft.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** 未元物質の六翼のメッシュ（左右×上中下、各翼は根元・中節・先端の3節）。 art-src/dark_matter/generate_models.py が作る。手で書き換えない。 */
public final class DarkWingsMesh {
    private DarkWingsMesh() { }
    private static final float DEG = (float) (Math.PI / 180);
    public static LayerDefinition create() {
        var mesh = new MeshDefinition();
        PartDefinition p1 = mesh.getRoot().addOrReplaceChild("wings", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 3f, 3f, 0f * DEG, 0f * DEG, 0f * DEG));
        part0(p1);
        part7(p1);
        return LayerDefinition.create(mesh, 256, 128);
    }
    private static void part0(PartDefinition parent) {
        PartDefinition p2 = parent.addOrReplaceChild("mount_left", CubeListBuilder.create(), PartPose.offsetAndRotation(1.5f, 0f, 0f, 0f * DEG, 0f * DEG, 0f * DEG));
        part1(p2);
        part3(p2);
        part5(p2);
    }
    private static void part1(PartDefinition parent) {
        PartDefinition p3 = parent.addOrReplaceChild("left_upper", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, -2f, 0f, 0f * DEG, -20f * DEG, -36f * DEG));
        p3.addOrReplaceChild("left_upper_root_f0", CubeListBuilder.create().texOffs(0, 0).addBox(0f, -3.5f, 0f, 21.5f, 7f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p3.addOrReplaceChild("left_upper_root_f1", CubeListBuilder.create().texOffs(23, 0).addBox(0f, -3.375f, 0f, 20.75f, 6.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.9286f, 0f, 0f, 0f * DEG, 1.5f * DEG, 12f * DEG));
        p3.addOrReplaceChild("left_upper_root_f2", CubeListBuilder.create().texOffs(45, 0).addBox(0f, -3.25f, 0f, 20f, 6.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3.8571f, 0f, 0f, 4f * DEG, -1.5f * DEG, -9f * DEG));
        p3.addOrReplaceChild("left_upper_root_f3", CubeListBuilder.create().texOffs(66, 0).addBox(0f, -3.125f, 0f, 19.25f, 6.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(5.7857f, 0f, 0f, -4f * DEG, 1.5f * DEG, 22f * DEG));
        p3.addOrReplaceChild("left_upper_root_f4", CubeListBuilder.create().texOffs(87, 0).addBox(0f, -3f, 0f, 18.5f, 6f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(7.7143f, 0f, 0f, 0f * DEG, -1.5f * DEG, 5f * DEG));
        p3.addOrReplaceChild("left_upper_root_f5", CubeListBuilder.create().texOffs(107, 0).addBox(0f, -3f, 0f, 17.75f, 6f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(9.6429f, 0f, 0f, 4f * DEG, 1.5f * DEG, 32f * DEG));
        p3.addOrReplaceChild("left_upper_root_f6", CubeListBuilder.create().texOffs(126, 0).addBox(0f, -2.875f, 0f, 17f, 5.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(11.5714f, 0f, 0f, -4f * DEG, -1.5f * DEG, -14f * DEG));
        p3.addOrReplaceChild("left_upper_root_f7", CubeListBuilder.create().texOffs(144, 0).addBox(0f, -2.75f, 0f, 16.25f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(13.5f, 0f, 0f, 0f * DEG, 1.5f * DEG, 17f * DEG));
        part2(p3);
    }
    private static void part2(PartDefinition parent) {
        PartDefinition p12 = parent.addOrReplaceChild("left_upper_mid", CubeListBuilder.create(), PartPose.offsetAndRotation(13.5f, 0f, 0f, 0f * DEG, 0f * DEG, 7f * DEG));
        p12.addOrReplaceChild("left_upper_mid_f0", CubeListBuilder.create().texOffs(162, 0).addBox(0f, -2.875f, 0f, 28f, 5.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p12.addOrReplaceChild("left_upper_mid_f1", CubeListBuilder.create().texOffs(191, 0).addBox(0f, -2.75f, 0f, 26.5f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.9286f, 0f, 0f, 0f * DEG, 1.5f * DEG, 7f * DEG));
        p12.addOrReplaceChild("left_upper_mid_f2", CubeListBuilder.create().texOffs(219, 0).addBox(0f, -2.75f, 0f, 25.25f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3.8571f, 0f, 0f, 4f * DEG, -1.5f * DEG, -6f * DEG));
        p12.addOrReplaceChild("left_upper_mid_f3", CubeListBuilder.create().texOffs(0, 8).addBox(0f, -2.625f, 0f, 24f, 5.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(5.7857f, 0f, 0f, -4f * DEG, 1.5f * DEG, 14f * DEG));
        p12.addOrReplaceChild("left_upper_mid_f4", CubeListBuilder.create().texOffs(25, 8).addBox(0f, -2.5f, 0f, 22.75f, 5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(7.7143f, 0f, 0f, 0f * DEG, -1.5f * DEG, 3f * DEG));
        p12.addOrReplaceChild("left_upper_mid_f5", CubeListBuilder.create().texOffs(49, 8).addBox(0f, -2.5f, 0f, 21.5f, 5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(9.6429f, 0f, 0f, 4f * DEG, 1.5f * DEG, 20f * DEG));
        p12.addOrReplaceChild("left_upper_mid_f6", CubeListBuilder.create().texOffs(72, 8).addBox(0f, -2.375f, 0f, 20.25f, 4.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(11.5714f, 0f, 0f, -4f * DEG, -1.5f * DEG, -9f * DEG));
        p12.addOrReplaceChild("left_upper_mid_f7", CubeListBuilder.create().texOffs(94, 8).addBox(0f, -2.25f, 0f, 19f, 4.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(13.5f, 0f, 0f, 0f * DEG, 1.5f * DEG, 10f * DEG));
        PartDefinition p21 = p12.addOrReplaceChild("left_upper_tip", CubeListBuilder.create(), PartPose.offsetAndRotation(13.5f, 0f, 0f, 0f * DEG, 0f * DEG, 7f * DEG));
        p21.addOrReplaceChild("left_upper_tip_f0", CubeListBuilder.create().texOffs(114, 8).addBox(0f, -2.25f, 0f, 37.75f, 4.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p21.addOrReplaceChild("left_upper_tip_f1", CubeListBuilder.create().texOffs(153, 8).addBox(0f, -2.125f, 0f, 35f, 4.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.5f, 0f, 0f, 0f * DEG, 1.5f * DEG, 3f * DEG));
        p21.addOrReplaceChild("left_upper_tip_f2", CubeListBuilder.create().texOffs(189, 8).addBox(0f, -2f, 0f, 32f, 4f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3f, 0f, 0f, 4f * DEG, -1.5f * DEG, -3f * DEG));
        p21.addOrReplaceChild("left_upper_tip_f3", CubeListBuilder.create().texOffs(222, 8).addBox(0f, -2f, 0f, 29.25f, 4f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(4.5f, 0f, 0f, -4f * DEG, 1.5f * DEG, 7f * DEG));
        p21.addOrReplaceChild("left_upper_tip_f4", CubeListBuilder.create().texOffs(0, 15).addBox(0f, -1.875f, 0f, 26.5f, 3.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6f, 0f, 0f, 0f * DEG, -1.5f * DEG, 1.5f * DEG));
        p21.addOrReplaceChild("left_upper_tip_f5", CubeListBuilder.create().texOffs(28, 15).addBox(0f, -1.75f, 0f, 23.5f, 3.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(7.5f, 0f, 0f, 4f * DEG, 1.5f * DEG, 10f * DEG));
        p21.addOrReplaceChild("left_upper_tip_f6", CubeListBuilder.create().texOffs(53, 15).addBox(0f, -1.75f, 0f, 20.75f, 3.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(9f, 0f, 0f, -4f * DEG, -1.5f * DEG, -5f * DEG));
    }
    private static void part3(PartDefinition parent) {
        PartDefinition p29 = parent.addOrReplaceChild("left_middle", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 1f, 0f, 0f * DEG, -20f * DEG, -4f * DEG));
        p29.addOrReplaceChild("left_middle_root_f0", CubeListBuilder.create().texOffs(75, 15).addBox(0f, -3.5f, 0f, 24f, 7f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p29.addOrReplaceChild("left_middle_root_f1", CubeListBuilder.create().texOffs(100, 15).addBox(0f, -3.375f, 0f, 23.25f, 6.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(2.1429f, 0f, 0f, 0f * DEG, 1.5f * DEG, 12f * DEG));
        p29.addOrReplaceChild("left_middle_root_f2", CubeListBuilder.create().texOffs(125, 15).addBox(0f, -3.25f, 0f, 22.25f, 6.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(4.2857f, 0f, 0f, 4f * DEG, -1.5f * DEG, -9f * DEG));
        p29.addOrReplaceChild("left_middle_root_f3", CubeListBuilder.create().texOffs(149, 15).addBox(0f, -3.125f, 0f, 21.5f, 6.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6.4286f, 0f, 0f, -4f * DEG, 1.5f * DEG, 22f * DEG));
        p29.addOrReplaceChild("left_middle_root_f4", CubeListBuilder.create().texOffs(172, 15).addBox(0f, -3f, 0f, 20.5f, 6f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(8.5714f, 0f, 0f, 0f * DEG, -1.5f * DEG, 5f * DEG));
        p29.addOrReplaceChild("left_middle_root_f5", CubeListBuilder.create().texOffs(194, 15).addBox(0f, -3f, 0f, 19.75f, 6f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(10.7143f, 0f, 0f, 4f * DEG, 1.5f * DEG, 32f * DEG));
        p29.addOrReplaceChild("left_middle_root_f6", CubeListBuilder.create().texOffs(215, 15).addBox(0f, -2.875f, 0f, 18.75f, 5.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(12.8571f, 0f, 0f, -4f * DEG, -1.5f * DEG, -14f * DEG));
        p29.addOrReplaceChild("left_middle_root_f7", CubeListBuilder.create().texOffs(235, 15).addBox(0f, -2.75f, 0f, 18f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(15f, 0f, 0f, 0f * DEG, 1.5f * DEG, 17f * DEG));
        part4(p29);
    }
    private static void part4(PartDefinition parent) {
        PartDefinition p38 = parent.addOrReplaceChild("left_middle_mid", CubeListBuilder.create(), PartPose.offsetAndRotation(15f, 0f, 0f, 0f * DEG, 0f * DEG, 4f * DEG));
        p38.addOrReplaceChild("left_middle_mid_f0", CubeListBuilder.create().texOffs(0, 23).addBox(0f, -2.875f, 0f, 31f, 5.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p38.addOrReplaceChild("left_middle_mid_f1", CubeListBuilder.create().texOffs(32, 23).addBox(0f, -2.75f, 0f, 29.5f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(2.1429f, 0f, 0f, 0f * DEG, 1.5f * DEG, 7f * DEG));
        p38.addOrReplaceChild("left_middle_mid_f2", CubeListBuilder.create().texOffs(63, 23).addBox(0f, -2.75f, 0f, 28.25f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(4.2857f, 0f, 0f, 4f * DEG, -1.5f * DEG, -6f * DEG));
        p38.addOrReplaceChild("left_middle_mid_f3", CubeListBuilder.create().texOffs(93, 23).addBox(0f, -2.625f, 0f, 26.75f, 5.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6.4286f, 0f, 0f, -4f * DEG, 1.5f * DEG, 14f * DEG));
        p38.addOrReplaceChild("left_middle_mid_f4", CubeListBuilder.create().texOffs(121, 23).addBox(0f, -2.5f, 0f, 25.25f, 5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(8.5714f, 0f, 0f, 0f * DEG, -1.5f * DEG, 3f * DEG));
        p38.addOrReplaceChild("left_middle_mid_f5", CubeListBuilder.create().texOffs(148, 23).addBox(0f, -2.5f, 0f, 23.75f, 5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(10.7143f, 0f, 0f, 4f * DEG, 1.5f * DEG, 20f * DEG));
        p38.addOrReplaceChild("left_middle_mid_f6", CubeListBuilder.create().texOffs(173, 23).addBox(0f, -2.375f, 0f, 22.5f, 4.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(12.8571f, 0f, 0f, -4f * DEG, -1.5f * DEG, -9f * DEG));
        p38.addOrReplaceChild("left_middle_mid_f7", CubeListBuilder.create().texOffs(197, 23).addBox(0f, -2.25f, 0f, 21f, 4.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(15f, 0f, 0f, 0f * DEG, 1.5f * DEG, 10f * DEG));
        PartDefinition p47 = p38.addOrReplaceChild("left_middle_tip", CubeListBuilder.create(), PartPose.offsetAndRotation(15f, 0f, 0f, 0f * DEG, 0f * DEG, 4f * DEG));
        p47.addOrReplaceChild("left_middle_tip_f0", CubeListBuilder.create().texOffs(0, 30).addBox(0f, -2.25f, 0f, 42f, 4.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p47.addOrReplaceChild("left_middle_tip_f1", CubeListBuilder.create().texOffs(43, 30).addBox(0f, -2.125f, 0f, 38.75f, 4.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.6667f, 0f, 0f, 0f * DEG, 1.5f * DEG, 3f * DEG));
        p47.addOrReplaceChild("left_middle_tip_f2", CubeListBuilder.create().texOffs(83, 30).addBox(0f, -2f, 0f, 35.75f, 4f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3.3333f, 0f, 0f, 4f * DEG, -1.5f * DEG, -3f * DEG));
        p47.addOrReplaceChild("left_middle_tip_f3", CubeListBuilder.create().texOffs(120, 30).addBox(0f, -2f, 0f, 32.5f, 4f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(5f, 0f, 0f, -4f * DEG, 1.5f * DEG, 7f * DEG));
        p47.addOrReplaceChild("left_middle_tip_f4", CubeListBuilder.create().texOffs(154, 30).addBox(0f, -1.875f, 0f, 29.25f, 3.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6.6667f, 0f, 0f, 0f * DEG, -1.5f * DEG, 1.5f * DEG));
        p47.addOrReplaceChild("left_middle_tip_f5", CubeListBuilder.create().texOffs(185, 30).addBox(0f, -1.75f, 0f, 26.25f, 3.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(8.3333f, 0f, 0f, 4f * DEG, 1.5f * DEG, 10f * DEG));
        p47.addOrReplaceChild("left_middle_tip_f6", CubeListBuilder.create().texOffs(213, 30).addBox(0f, -1.75f, 0f, 23f, 3.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(10f, 0f, 0f, -4f * DEG, -1.5f * DEG, -5f * DEG));
    }
    private static void part5(PartDefinition parent) {
        PartDefinition p55 = parent.addOrReplaceChild("left_lower", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 4f, 0f, 0f * DEG, -20f * DEG, 27f * DEG));
        p55.addOrReplaceChild("left_lower_root_f0", CubeListBuilder.create().texOffs(0, 36).addBox(0f, -3.5f, 0f, 18.25f, 7f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p55.addOrReplaceChild("left_lower_root_f1", CubeListBuilder.create().texOffs(20, 36).addBox(0f, -3.375f, 0f, 17.5f, 6.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.6286f, 0f, 0f, 0f * DEG, 1.5f * DEG, 12f * DEG));
        p55.addOrReplaceChild("left_lower_root_f2", CubeListBuilder.create().texOffs(39, 36).addBox(0f, -3.25f, 0f, 17f, 6.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3.2571f, 0f, 0f, 4f * DEG, -1.5f * DEG, -9f * DEG));
        p55.addOrReplaceChild("left_lower_root_f3", CubeListBuilder.create().texOffs(57, 36).addBox(0f, -3.125f, 0f, 16.25f, 6.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(4.8857f, 0f, 0f, -4f * DEG, 1.5f * DEG, 22f * DEG));
        p55.addOrReplaceChild("left_lower_root_f4", CubeListBuilder.create().texOffs(75, 36).addBox(0f, -3f, 0f, 15.75f, 6f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6.5143f, 0f, 0f, 0f * DEG, -1.5f * DEG, 5f * DEG));
        p55.addOrReplaceChild("left_lower_root_f5", CubeListBuilder.create().texOffs(92, 36).addBox(0f, -3f, 0f, 15f, 6f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(8.1429f, 0f, 0f, 4f * DEG, 1.5f * DEG, 32f * DEG));
        p55.addOrReplaceChild("left_lower_root_f6", CubeListBuilder.create().texOffs(108, 36).addBox(0f, -2.875f, 0f, 14.25f, 5.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(9.7714f, 0f, 0f, -4f * DEG, -1.5f * DEG, -14f * DEG));
        p55.addOrReplaceChild("left_lower_root_f7", CubeListBuilder.create().texOffs(124, 36).addBox(0f, -2.75f, 0f, 13.75f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(11.4f, 0f, 0f, 0f * DEG, 1.5f * DEG, 17f * DEG));
        part6(p55);
    }
    private static void part6(PartDefinition parent) {
        PartDefinition p64 = parent.addOrReplaceChild("left_lower_mid", CubeListBuilder.create(), PartPose.offsetAndRotation(11.4f, 0f, 0f, 0f * DEG, 0f * DEG, -3f * DEG));
        p64.addOrReplaceChild("left_lower_mid_f0", CubeListBuilder.create().texOffs(139, 36).addBox(0f, -2.875f, 0f, 23.5f, 5.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p64.addOrReplaceChild("left_lower_mid_f1", CubeListBuilder.create().texOffs(164, 36).addBox(0f, -2.75f, 0f, 22.5f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.6286f, 0f, 0f, 0f * DEG, 1.5f * DEG, 7f * DEG));
        p64.addOrReplaceChild("left_lower_mid_f2", CubeListBuilder.create().texOffs(188, 36).addBox(0f, -2.75f, 0f, 21.5f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3.2571f, 0f, 0f, 4f * DEG, -1.5f * DEG, -6f * DEG));
        p64.addOrReplaceChild("left_lower_mid_f3", CubeListBuilder.create().texOffs(211, 36).addBox(0f, -2.625f, 0f, 20.25f, 5.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(4.8857f, 0f, 0f, -4f * DEG, 1.5f * DEG, 14f * DEG));
        p64.addOrReplaceChild("left_lower_mid_f4", CubeListBuilder.create().texOffs(233, 36).addBox(0f, -2.5f, 0f, 19.25f, 5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6.5143f, 0f, 0f, 0f * DEG, -1.5f * DEG, 3f * DEG));
        p64.addOrReplaceChild("left_lower_mid_f5", CubeListBuilder.create().texOffs(0, 44).addBox(0f, -2.5f, 0f, 18.25f, 5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(8.1429f, 0f, 0f, 4f * DEG, 1.5f * DEG, 20f * DEG));
        p64.addOrReplaceChild("left_lower_mid_f6", CubeListBuilder.create().texOffs(20, 44).addBox(0f, -2.375f, 0f, 17f, 4.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(9.7714f, 0f, 0f, -4f * DEG, -1.5f * DEG, -9f * DEG));
        p64.addOrReplaceChild("left_lower_mid_f7", CubeListBuilder.create().texOffs(38, 44).addBox(0f, -2.25f, 0f, 16f, 4.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(11.4f, 0f, 0f, 0f * DEG, 1.5f * DEG, 10f * DEG));
        PartDefinition p73 = p64.addOrReplaceChild("left_lower_tip", CubeListBuilder.create(), PartPose.offsetAndRotation(11.4f, 0f, 0f, 0f * DEG, 0f * DEG, -3f * DEG));
        p73.addOrReplaceChild("left_lower_tip_f0", CubeListBuilder.create().texOffs(55, 44).addBox(0f, -2.25f, 0f, 32f, 4.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p73.addOrReplaceChild("left_lower_tip_f1", CubeListBuilder.create().texOffs(88, 44).addBox(0f, -2.125f, 0f, 29.5f, 4.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.2667f, 0f, 0f, 0f * DEG, 1.5f * DEG, 3f * DEG));
        p73.addOrReplaceChild("left_lower_tip_f2", CubeListBuilder.create().texOffs(119, 44).addBox(0f, -2f, 0f, 27f, 4f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(2.5333f, 0f, 0f, 4f * DEG, -1.5f * DEG, -3f * DEG));
        p73.addOrReplaceChild("left_lower_tip_f3", CubeListBuilder.create().texOffs(147, 44).addBox(0f, -2f, 0f, 24.75f, 4f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3.8f, 0f, 0f, -4f * DEG, 1.5f * DEG, 7f * DEG));
        p73.addOrReplaceChild("left_lower_tip_f4", CubeListBuilder.create().texOffs(173, 44).addBox(0f, -1.875f, 0f, 22.25f, 3.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(5.0667f, 0f, 0f, 0f * DEG, -1.5f * DEG, 1.5f * DEG));
        p73.addOrReplaceChild("left_lower_tip_f5", CubeListBuilder.create().texOffs(197, 44).addBox(0f, -1.75f, 0f, 20f, 3.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6.3333f, 0f, 0f, 4f * DEG, 1.5f * DEG, 10f * DEG));
        p73.addOrReplaceChild("left_lower_tip_f6", CubeListBuilder.create().texOffs(218, 44).addBox(0f, -1.75f, 0f, 17.5f, 3.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(7.6f, 0f, 0f, -4f * DEG, -1.5f * DEG, -5f * DEG));
    }
    private static void part7(PartDefinition parent) {
        PartDefinition p81 = parent.addOrReplaceChild("mount_right", CubeListBuilder.create(), PartPose.offsetAndRotation(-1.5f, 0f, 0f, 0f * DEG, 180f * DEG, 0f * DEG));
        part8(p81);
        part10(p81);
        part12(p81);
    }
    private static void part8(PartDefinition parent) {
        PartDefinition p82 = parent.addOrReplaceChild("right_upper", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, -2f, 0f, 0f * DEG, 20f * DEG, -36f * DEG));
        p82.addOrReplaceChild("right_upper_root_f0", CubeListBuilder.create().texOffs(0, 0).addBox(0f, -3.5f, 0f, 21.5f, 7f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p82.addOrReplaceChild("right_upper_root_f1", CubeListBuilder.create().texOffs(23, 0).addBox(0f, -3.375f, 0f, 20.75f, 6.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.9286f, 0f, 0f, 0f * DEG, 1.5f * DEG, 12f * DEG));
        p82.addOrReplaceChild("right_upper_root_f2", CubeListBuilder.create().texOffs(45, 0).addBox(0f, -3.25f, 0f, 20f, 6.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3.8571f, 0f, 0f, 4f * DEG, -1.5f * DEG, -9f * DEG));
        p82.addOrReplaceChild("right_upper_root_f3", CubeListBuilder.create().texOffs(66, 0).addBox(0f, -3.125f, 0f, 19.25f, 6.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(5.7857f, 0f, 0f, -4f * DEG, 1.5f * DEG, 22f * DEG));
        p82.addOrReplaceChild("right_upper_root_f4", CubeListBuilder.create().texOffs(87, 0).addBox(0f, -3f, 0f, 18.5f, 6f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(7.7143f, 0f, 0f, 0f * DEG, -1.5f * DEG, 5f * DEG));
        p82.addOrReplaceChild("right_upper_root_f5", CubeListBuilder.create().texOffs(107, 0).addBox(0f, -3f, 0f, 17.75f, 6f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(9.6429f, 0f, 0f, 4f * DEG, 1.5f * DEG, 32f * DEG));
        p82.addOrReplaceChild("right_upper_root_f6", CubeListBuilder.create().texOffs(126, 0).addBox(0f, -2.875f, 0f, 17f, 5.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(11.5714f, 0f, 0f, -4f * DEG, -1.5f * DEG, -14f * DEG));
        p82.addOrReplaceChild("right_upper_root_f7", CubeListBuilder.create().texOffs(144, 0).addBox(0f, -2.75f, 0f, 16.25f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(13.5f, 0f, 0f, 0f * DEG, 1.5f * DEG, 17f * DEG));
        part9(p82);
    }
    private static void part9(PartDefinition parent) {
        PartDefinition p91 = parent.addOrReplaceChild("right_upper_mid", CubeListBuilder.create(), PartPose.offsetAndRotation(13.5f, 0f, 0f, 0f * DEG, 0f * DEG, 7f * DEG));
        p91.addOrReplaceChild("right_upper_mid_f0", CubeListBuilder.create().texOffs(162, 0).addBox(0f, -2.875f, 0f, 28f, 5.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p91.addOrReplaceChild("right_upper_mid_f1", CubeListBuilder.create().texOffs(191, 0).addBox(0f, -2.75f, 0f, 26.5f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.9286f, 0f, 0f, 0f * DEG, 1.5f * DEG, 7f * DEG));
        p91.addOrReplaceChild("right_upper_mid_f2", CubeListBuilder.create().texOffs(219, 0).addBox(0f, -2.75f, 0f, 25.25f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3.8571f, 0f, 0f, 4f * DEG, -1.5f * DEG, -6f * DEG));
        p91.addOrReplaceChild("right_upper_mid_f3", CubeListBuilder.create().texOffs(0, 8).addBox(0f, -2.625f, 0f, 24f, 5.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(5.7857f, 0f, 0f, -4f * DEG, 1.5f * DEG, 14f * DEG));
        p91.addOrReplaceChild("right_upper_mid_f4", CubeListBuilder.create().texOffs(25, 8).addBox(0f, -2.5f, 0f, 22.75f, 5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(7.7143f, 0f, 0f, 0f * DEG, -1.5f * DEG, 3f * DEG));
        p91.addOrReplaceChild("right_upper_mid_f5", CubeListBuilder.create().texOffs(49, 8).addBox(0f, -2.5f, 0f, 21.5f, 5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(9.6429f, 0f, 0f, 4f * DEG, 1.5f * DEG, 20f * DEG));
        p91.addOrReplaceChild("right_upper_mid_f6", CubeListBuilder.create().texOffs(72, 8).addBox(0f, -2.375f, 0f, 20.25f, 4.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(11.5714f, 0f, 0f, -4f * DEG, -1.5f * DEG, -9f * DEG));
        p91.addOrReplaceChild("right_upper_mid_f7", CubeListBuilder.create().texOffs(94, 8).addBox(0f, -2.25f, 0f, 19f, 4.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(13.5f, 0f, 0f, 0f * DEG, 1.5f * DEG, 10f * DEG));
        PartDefinition p100 = p91.addOrReplaceChild("right_upper_tip", CubeListBuilder.create(), PartPose.offsetAndRotation(13.5f, 0f, 0f, 0f * DEG, 0f * DEG, 7f * DEG));
        p100.addOrReplaceChild("right_upper_tip_f0", CubeListBuilder.create().texOffs(114, 8).addBox(0f, -2.25f, 0f, 37.75f, 4.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p100.addOrReplaceChild("right_upper_tip_f1", CubeListBuilder.create().texOffs(153, 8).addBox(0f, -2.125f, 0f, 35f, 4.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.5f, 0f, 0f, 0f * DEG, 1.5f * DEG, 3f * DEG));
        p100.addOrReplaceChild("right_upper_tip_f2", CubeListBuilder.create().texOffs(189, 8).addBox(0f, -2f, 0f, 32f, 4f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3f, 0f, 0f, 4f * DEG, -1.5f * DEG, -3f * DEG));
        p100.addOrReplaceChild("right_upper_tip_f3", CubeListBuilder.create().texOffs(222, 8).addBox(0f, -2f, 0f, 29.25f, 4f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(4.5f, 0f, 0f, -4f * DEG, 1.5f * DEG, 7f * DEG));
        p100.addOrReplaceChild("right_upper_tip_f4", CubeListBuilder.create().texOffs(0, 15).addBox(0f, -1.875f, 0f, 26.5f, 3.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6f, 0f, 0f, 0f * DEG, -1.5f * DEG, 1.5f * DEG));
        p100.addOrReplaceChild("right_upper_tip_f5", CubeListBuilder.create().texOffs(28, 15).addBox(0f, -1.75f, 0f, 23.5f, 3.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(7.5f, 0f, 0f, 4f * DEG, 1.5f * DEG, 10f * DEG));
        p100.addOrReplaceChild("right_upper_tip_f6", CubeListBuilder.create().texOffs(53, 15).addBox(0f, -1.75f, 0f, 20.75f, 3.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(9f, 0f, 0f, -4f * DEG, -1.5f * DEG, -5f * DEG));
    }
    private static void part10(PartDefinition parent) {
        PartDefinition p108 = parent.addOrReplaceChild("right_middle", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 1f, 0f, 0f * DEG, 20f * DEG, -4f * DEG));
        p108.addOrReplaceChild("right_middle_root_f0", CubeListBuilder.create().texOffs(75, 15).addBox(0f, -3.5f, 0f, 24f, 7f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p108.addOrReplaceChild("right_middle_root_f1", CubeListBuilder.create().texOffs(100, 15).addBox(0f, -3.375f, 0f, 23.25f, 6.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(2.1429f, 0f, 0f, 0f * DEG, 1.5f * DEG, 12f * DEG));
        p108.addOrReplaceChild("right_middle_root_f2", CubeListBuilder.create().texOffs(125, 15).addBox(0f, -3.25f, 0f, 22.25f, 6.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(4.2857f, 0f, 0f, 4f * DEG, -1.5f * DEG, -9f * DEG));
        p108.addOrReplaceChild("right_middle_root_f3", CubeListBuilder.create().texOffs(149, 15).addBox(0f, -3.125f, 0f, 21.5f, 6.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6.4286f, 0f, 0f, -4f * DEG, 1.5f * DEG, 22f * DEG));
        p108.addOrReplaceChild("right_middle_root_f4", CubeListBuilder.create().texOffs(172, 15).addBox(0f, -3f, 0f, 20.5f, 6f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(8.5714f, 0f, 0f, 0f * DEG, -1.5f * DEG, 5f * DEG));
        p108.addOrReplaceChild("right_middle_root_f5", CubeListBuilder.create().texOffs(194, 15).addBox(0f, -3f, 0f, 19.75f, 6f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(10.7143f, 0f, 0f, 4f * DEG, 1.5f * DEG, 32f * DEG));
        p108.addOrReplaceChild("right_middle_root_f6", CubeListBuilder.create().texOffs(215, 15).addBox(0f, -2.875f, 0f, 18.75f, 5.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(12.8571f, 0f, 0f, -4f * DEG, -1.5f * DEG, -14f * DEG));
        p108.addOrReplaceChild("right_middle_root_f7", CubeListBuilder.create().texOffs(235, 15).addBox(0f, -2.75f, 0f, 18f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(15f, 0f, 0f, 0f * DEG, 1.5f * DEG, 17f * DEG));
        part11(p108);
    }
    private static void part11(PartDefinition parent) {
        PartDefinition p117 = parent.addOrReplaceChild("right_middle_mid", CubeListBuilder.create(), PartPose.offsetAndRotation(15f, 0f, 0f, 0f * DEG, 0f * DEG, 4f * DEG));
        p117.addOrReplaceChild("right_middle_mid_f0", CubeListBuilder.create().texOffs(0, 23).addBox(0f, -2.875f, 0f, 31f, 5.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p117.addOrReplaceChild("right_middle_mid_f1", CubeListBuilder.create().texOffs(32, 23).addBox(0f, -2.75f, 0f, 29.5f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(2.1429f, 0f, 0f, 0f * DEG, 1.5f * DEG, 7f * DEG));
        p117.addOrReplaceChild("right_middle_mid_f2", CubeListBuilder.create().texOffs(63, 23).addBox(0f, -2.75f, 0f, 28.25f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(4.2857f, 0f, 0f, 4f * DEG, -1.5f * DEG, -6f * DEG));
        p117.addOrReplaceChild("right_middle_mid_f3", CubeListBuilder.create().texOffs(93, 23).addBox(0f, -2.625f, 0f, 26.75f, 5.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6.4286f, 0f, 0f, -4f * DEG, 1.5f * DEG, 14f * DEG));
        p117.addOrReplaceChild("right_middle_mid_f4", CubeListBuilder.create().texOffs(121, 23).addBox(0f, -2.5f, 0f, 25.25f, 5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(8.5714f, 0f, 0f, 0f * DEG, -1.5f * DEG, 3f * DEG));
        p117.addOrReplaceChild("right_middle_mid_f5", CubeListBuilder.create().texOffs(148, 23).addBox(0f, -2.5f, 0f, 23.75f, 5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(10.7143f, 0f, 0f, 4f * DEG, 1.5f * DEG, 20f * DEG));
        p117.addOrReplaceChild("right_middle_mid_f6", CubeListBuilder.create().texOffs(173, 23).addBox(0f, -2.375f, 0f, 22.5f, 4.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(12.8571f, 0f, 0f, -4f * DEG, -1.5f * DEG, -9f * DEG));
        p117.addOrReplaceChild("right_middle_mid_f7", CubeListBuilder.create().texOffs(197, 23).addBox(0f, -2.25f, 0f, 21f, 4.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(15f, 0f, 0f, 0f * DEG, 1.5f * DEG, 10f * DEG));
        PartDefinition p126 = p117.addOrReplaceChild("right_middle_tip", CubeListBuilder.create(), PartPose.offsetAndRotation(15f, 0f, 0f, 0f * DEG, 0f * DEG, 4f * DEG));
        p126.addOrReplaceChild("right_middle_tip_f0", CubeListBuilder.create().texOffs(0, 30).addBox(0f, -2.25f, 0f, 42f, 4.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p126.addOrReplaceChild("right_middle_tip_f1", CubeListBuilder.create().texOffs(43, 30).addBox(0f, -2.125f, 0f, 38.75f, 4.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.6667f, 0f, 0f, 0f * DEG, 1.5f * DEG, 3f * DEG));
        p126.addOrReplaceChild("right_middle_tip_f2", CubeListBuilder.create().texOffs(83, 30).addBox(0f, -2f, 0f, 35.75f, 4f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3.3333f, 0f, 0f, 4f * DEG, -1.5f * DEG, -3f * DEG));
        p126.addOrReplaceChild("right_middle_tip_f3", CubeListBuilder.create().texOffs(120, 30).addBox(0f, -2f, 0f, 32.5f, 4f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(5f, 0f, 0f, -4f * DEG, 1.5f * DEG, 7f * DEG));
        p126.addOrReplaceChild("right_middle_tip_f4", CubeListBuilder.create().texOffs(154, 30).addBox(0f, -1.875f, 0f, 29.25f, 3.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6.6667f, 0f, 0f, 0f * DEG, -1.5f * DEG, 1.5f * DEG));
        p126.addOrReplaceChild("right_middle_tip_f5", CubeListBuilder.create().texOffs(185, 30).addBox(0f, -1.75f, 0f, 26.25f, 3.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(8.3333f, 0f, 0f, 4f * DEG, 1.5f * DEG, 10f * DEG));
        p126.addOrReplaceChild("right_middle_tip_f6", CubeListBuilder.create().texOffs(213, 30).addBox(0f, -1.75f, 0f, 23f, 3.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(10f, 0f, 0f, -4f * DEG, -1.5f * DEG, -5f * DEG));
    }
    private static void part12(PartDefinition parent) {
        PartDefinition p134 = parent.addOrReplaceChild("right_lower", CubeListBuilder.create(), PartPose.offsetAndRotation(0f, 4f, 0f, 0f * DEG, 20f * DEG, 27f * DEG));
        p134.addOrReplaceChild("right_lower_root_f0", CubeListBuilder.create().texOffs(0, 36).addBox(0f, -3.5f, 0f, 18.25f, 7f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p134.addOrReplaceChild("right_lower_root_f1", CubeListBuilder.create().texOffs(20, 36).addBox(0f, -3.375f, 0f, 17.5f, 6.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.6286f, 0f, 0f, 0f * DEG, 1.5f * DEG, 12f * DEG));
        p134.addOrReplaceChild("right_lower_root_f2", CubeListBuilder.create().texOffs(39, 36).addBox(0f, -3.25f, 0f, 17f, 6.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3.2571f, 0f, 0f, 4f * DEG, -1.5f * DEG, -9f * DEG));
        p134.addOrReplaceChild("right_lower_root_f3", CubeListBuilder.create().texOffs(57, 36).addBox(0f, -3.125f, 0f, 16.25f, 6.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(4.8857f, 0f, 0f, -4f * DEG, 1.5f * DEG, 22f * DEG));
        p134.addOrReplaceChild("right_lower_root_f4", CubeListBuilder.create().texOffs(75, 36).addBox(0f, -3f, 0f, 15.75f, 6f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6.5143f, 0f, 0f, 0f * DEG, -1.5f * DEG, 5f * DEG));
        p134.addOrReplaceChild("right_lower_root_f5", CubeListBuilder.create().texOffs(92, 36).addBox(0f, -3f, 0f, 15f, 6f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(8.1429f, 0f, 0f, 4f * DEG, 1.5f * DEG, 32f * DEG));
        p134.addOrReplaceChild("right_lower_root_f6", CubeListBuilder.create().texOffs(108, 36).addBox(0f, -2.875f, 0f, 14.25f, 5.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(9.7714f, 0f, 0f, -4f * DEG, -1.5f * DEG, -14f * DEG));
        p134.addOrReplaceChild("right_lower_root_f7", CubeListBuilder.create().texOffs(124, 36).addBox(0f, -2.75f, 0f, 13.75f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(11.4f, 0f, 0f, 0f * DEG, 1.5f * DEG, 17f * DEG));
        part13(p134);
    }
    private static void part13(PartDefinition parent) {
        PartDefinition p143 = parent.addOrReplaceChild("right_lower_mid", CubeListBuilder.create(), PartPose.offsetAndRotation(11.4f, 0f, 0f, 0f * DEG, 0f * DEG, -3f * DEG));
        p143.addOrReplaceChild("right_lower_mid_f0", CubeListBuilder.create().texOffs(139, 36).addBox(0f, -2.875f, 0f, 23.5f, 5.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p143.addOrReplaceChild("right_lower_mid_f1", CubeListBuilder.create().texOffs(164, 36).addBox(0f, -2.75f, 0f, 22.5f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.6286f, 0f, 0f, 0f * DEG, 1.5f * DEG, 7f * DEG));
        p143.addOrReplaceChild("right_lower_mid_f2", CubeListBuilder.create().texOffs(188, 36).addBox(0f, -2.75f, 0f, 21.5f, 5.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3.2571f, 0f, 0f, 4f * DEG, -1.5f * DEG, -6f * DEG));
        p143.addOrReplaceChild("right_lower_mid_f3", CubeListBuilder.create().texOffs(211, 36).addBox(0f, -2.625f, 0f, 20.25f, 5.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(4.8857f, 0f, 0f, -4f * DEG, 1.5f * DEG, 14f * DEG));
        p143.addOrReplaceChild("right_lower_mid_f4", CubeListBuilder.create().texOffs(233, 36).addBox(0f, -2.5f, 0f, 19.25f, 5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6.5143f, 0f, 0f, 0f * DEG, -1.5f * DEG, 3f * DEG));
        p143.addOrReplaceChild("right_lower_mid_f5", CubeListBuilder.create().texOffs(0, 44).addBox(0f, -2.5f, 0f, 18.25f, 5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(8.1429f, 0f, 0f, 4f * DEG, 1.5f * DEG, 20f * DEG));
        p143.addOrReplaceChild("right_lower_mid_f6", CubeListBuilder.create().texOffs(20, 44).addBox(0f, -2.375f, 0f, 17f, 4.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(9.7714f, 0f, 0f, -4f * DEG, -1.5f * DEG, -9f * DEG));
        p143.addOrReplaceChild("right_lower_mid_f7", CubeListBuilder.create().texOffs(38, 44).addBox(0f, -2.25f, 0f, 16f, 4.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(11.4f, 0f, 0f, 0f * DEG, 1.5f * DEG, 10f * DEG));
        PartDefinition p152 = p143.addOrReplaceChild("right_lower_tip", CubeListBuilder.create(), PartPose.offsetAndRotation(11.4f, 0f, 0f, 0f * DEG, 0f * DEG, -3f * DEG));
        p152.addOrReplaceChild("right_lower_tip_f0", CubeListBuilder.create().texOffs(55, 44).addBox(0f, -2.25f, 0f, 32f, 4.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(0f, 0f, 0f, -4f * DEG, -1.5f * DEG, 0f * DEG));
        p152.addOrReplaceChild("right_lower_tip_f1", CubeListBuilder.create().texOffs(88, 44).addBox(0f, -2.125f, 0f, 29.5f, 4.25f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(1.2667f, 0f, 0f, 0f * DEG, 1.5f * DEG, 3f * DEG));
        p152.addOrReplaceChild("right_lower_tip_f2", CubeListBuilder.create().texOffs(119, 44).addBox(0f, -2f, 0f, 27f, 4f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(2.5333f, 0f, 0f, 4f * DEG, -1.5f * DEG, -3f * DEG));
        p152.addOrReplaceChild("right_lower_tip_f3", CubeListBuilder.create().texOffs(147, 44).addBox(0f, -2f, 0f, 24.75f, 4f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(3.8f, 0f, 0f, -4f * DEG, 1.5f * DEG, 7f * DEG));
        p152.addOrReplaceChild("right_lower_tip_f4", CubeListBuilder.create().texOffs(173, 44).addBox(0f, -1.875f, 0f, 22.25f, 3.75f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(5.0667f, 0f, 0f, 0f * DEG, -1.5f * DEG, 1.5f * DEG));
        p152.addOrReplaceChild("right_lower_tip_f5", CubeListBuilder.create().texOffs(197, 44).addBox(0f, -1.75f, 0f, 20f, 3.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(6.3333f, 0f, 0f, 4f * DEG, 1.5f * DEG, 10f * DEG));
        p152.addOrReplaceChild("right_lower_tip_f6", CubeListBuilder.create().texOffs(218, 44).addBox(0f, -1.75f, 0f, 17.5f, 3.5f, 0f, CubeDeformation.NONE, 1f, 1f), PartPose.offsetAndRotation(7.6f, 0f, 0f, -4f * DEG, -1.5f * DEG, -5f * DEG));
    }
}
