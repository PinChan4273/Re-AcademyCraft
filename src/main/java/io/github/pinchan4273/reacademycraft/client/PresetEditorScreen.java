package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.AcademyNetwork;
import io.github.pinchan4273.reacademycraft.network.PresetEdit;
import io.github.pinchan4273.reacademycraft.skill.PlayerAbilityData;
import io.github.pinchan4273.reacademycraft.skill.SkillCatalog;
import io.github.pinchan4273.reacademycraft.skill.SkillTreeLayout;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 原作PresetEditUI（cn/academy/client/auxgui/PresetEditUI.java、guis/preset_edit.xml）、WeAthFolD:
 * 0.7の暗幕の上に4つのプリセットを125間隔で横に並べる。編集中のものは中央に等倍で、他はアルファ0.8と0.3。
 * 別のページをクリックすると0.35秒でそこへ滑る。中央のプリセットのスロットをクリックすると、マウスの位置に、
 * そのプリセットにまだ無い習得済みの操作可能な技能の選択欄を、原作の「外す」×印に続けて開く。選ぶとサーバーへ
 * スロットの設定を要求し、サーバーが従来どおり確かめる。原作にプリセットを使うボタンは無く、プリセットキーで切り替える。
 */
public final class PresetEditorScreen extends Screen {
    private static ResourceLocation tex(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/guis/" + path + ".png"); }
    static final ResourceLocation BACK = tex("preset_settings/back"), SELECTED = tex("preset_settings/selected"), CANCEL = tex("preset_settings/cancel");
    // preset_edit.xmlのテンプレート（その単位）: ページ、その上のタイトル、4つのスロット。
    static final float PAGE_W = 116.25f, PAGE_H = 141.5f, STEP = 125, SLOT_X = (PAGE_W - 110) / 2 + .5f, SLOT_W = 110, SLOT_H = 34.75f,
            TITLE_X = (PAGE_W - 35) / 2 + .5f, TITLE_Y = -15;
    static final float[] SLOT_Y = {1.5f, 36.25f, 71, 105.75f};
    static final double TRANSIT_TIME = .35;
    static final float MAX_ALPHA = 1, MIN_ALPHA = .3f, MAX_SCALE = 1, MIN_SCALE = .8f;
    // 選択欄: 1行4つ、15四方、間隔18、端から2.5内側。
    static final int MAX_PER_ROW = 4;
    static final float MARGIN = 2.5f, SIZE = 15, SEL_STEP = SIZE + 3;

    // lastActiveは切り替え前のプリセット。
    private int lastActive, active;
    private boolean transiting;
    private double transitStart, transitProgress;
    @Nullable private Selector selector;
    private double mouseX, mouseY;

    public PresetEditorScreen() { super(Component.translatable("academy.gui.preset_edit.name")); }

    @Nullable private PlayerAbilityData data() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
    }
    static double now() { return Util.getMillis() / 1000.0; }

    // ---- ページの配置 ----
    private float xFor(int page, int centre) { return STEP * (page - centre); }
    /** このフレームでページをどこにどう描くか: 中央からのずれ、倍率、アルファ。 */
    private float[] place(int page) {
        if (!transiting) return new float[] {xFor(page, active), page == active ? MAX_SCALE : MIN_SCALE, page == active ? MAX_ALPHA : MIN_ALPHA};
        float p = (float) transitProgress;
        float dx = Mth.lerp(p, xFor(page, lastActive), xFor(page, active));
        if (page == lastActive) return new float[] {dx, Mth.lerp(p, MAX_SCALE, MIN_SCALE), Mth.lerp(p, MAX_ALPHA, MIN_ALPHA)};
        if (page == active) return new float[] {dx, Mth.lerp(p, MIN_SCALE, MAX_SCALE), Mth.lerp(p, MIN_ALPHA, MAX_ALPHA)};
        return new float[] {dx, MIN_SCALE, MIN_ALPHA};
    }
    /** CGuiの中央揃えウィジェット: 拡大縮小したページを画面中央に置き、ずれの分だけ動かす。 */
    private float pageLeft(float[] place) { return (width - PAGE_W * place[1]) / 2 + place[0]; }
    private float pageTop(float[] place) { return (height - PAGE_H * place[1]) / 2; }

    private void startTransit(int to) {
        lastActive = active; active = to;
        transiting = true; transitStart = now(); transitProgress = 0;
    }
    private void updateTransit() {
        if (!transiting) return;
        transitProgress = Math.min(1, (now() - transitStart) / TRANSIT_TIME);
        if (transitProgress >= 1) transiting = false;
    }

    // ---- 描画 ----
    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        mouseX = mx; mouseY = my;
        updateTransit();
        var pose = g.pose();
        RenderSystem.enableBlend();
        // RenderUtils.drawBlackout: 画面全体を0.7の黒で覆う。
        DeveloperScreen.rect(pose, 0, 0, width, height, 0, 0, 0, .7f);
        // "background"ウィジェット: ページの名前を(5, 5)に高さ12で描く。
        text(g, I18n.get("academy.gui.preset_edit.name"), 5, 5, 12, 0, 1, 0xffffff);
        var data = data();
        for (int i = 0; i < 4; i++) drawPage(g, i, data, mx, my);
        if (selector != null && !transiting) selector.draw(g, mx, my);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    private void drawPage(GuiGraphics g, int page, @Nullable PlayerAbilityData data, int mx, int my) {
        var place = place(page);
        float left = pageLeft(place), top = pageTop(place), s = place[1], alpha = place[2];
        var pose = g.pose();
        pose.pushPose();
        pose.translate(left, top, 0); pose.scale(s, s, 1);
        DeveloperScreen.quad(pose, BACK, 0, 0, PAGE_W, PAGE_H, 1, 1, 1, alpha);
        // タイトルはページの直接の子で、原作のAlphaAssignはこれを飛ばすため、常に不透明。
        text(g, I18n.get("academy.gui.preset_edit.tag") + (page + 1), TITLE_X, TITLE_Y, 10, 15, 1, 0xffffff);
        for (int slot = 0; slot < 4; slot++) {
            float y = SLOT_Y[slot];
            // HintHandler: 強調表示はマウスの下に、中央のページでだけ出す。
            boolean hover = !transiting && page == active && selector == null
                    && inside((mx - left) / s, (my - top) / s, SLOT_X, y, SLOT_W, SLOT_H);
            if (hover) DeveloperScreen.quad(pose, SELECTED, SLOT_X, y, SLOT_W, SLOT_H, 1, 1, 1, alpha);
            var skill = data == null ? null : data.getSlot(page, slot);
            var node = skill == null ? null : SkillTreeLayout.node(skill);
            if (node != null) DeveloperScreen.quad(pose, node.icon(), SLOT_X + 2.25f, y + 3.75f, 26.5f, 26.5f, 1, 1, 1, alpha);
            if (skill != null) text(g, name(skill), SLOT_X + 36.25f, y + 10, 10, 15, alpha, 0xffffff);
        }
        pose.popPose();
    }
    private static String name(ResourceLocation skill) {
        var definition = SkillCatalog.find(skill);
        return definition == null ? skill.toString() : I18n.get(definition.translation());
    }
    /** 原作のフォントを指定の大きさで左揃えに描く。高さを与えた枠では縦に中央揃えする。 */
    private void text(GuiGraphics g, String s, float x, float y, float size, float box, float alpha, int rgb) {
        var pose = g.pose();
        pose.pushPose();
        float scale = size / 9f;
        pose.translate(x, y + (box > 0 ? (box - 9 * scale) / 2 : 0), 0);
        pose.scale(scale, scale, 1);
        int a = Math.max(4, (int) (255 * alpha));
        g.drawString(font, s, 0, 0, a << 24 | rgb, false);
        pose.popPose();
    }
    static boolean inside(double x, double y, float bx, float by, float w, float h) { return x >= bx && x < bx + w && y >= by && y < by + h; }

    // ---- 入力 ----
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || transiting) return true;
        if (selector != null && selector.click(mx, my)) return true;
        for (int page = 0; page < 4; page++) {
            var place = place(page);
            float left = pageLeft(place), top = pageTop(place), s = place[1];
            for (int slot = 0; slot < 4; slot++) {
                if (!inside((mx - left) / s, (my - top) / s, SLOT_X, SLOT_Y[slot], SLOT_W, SLOT_H)) continue;
                if (selector != null) selector = null;
                else if (page == active) selector = new Selector(slot, (float) mx, (float) my);
                else startTransit(page);
                return true;
            }
        }
        return true;
    }
    // 原作のPresetEditUIはシングルプレイのゲームを一時停止した（1.12の既定）が、クライアント自身のデータをすぐ書き換えていた。
    // ここではサーバーが編集を一つずつ確定し、画面は確定した内容を表示する。一時停止した統合サーバーはこれを行えないので、
    // この画面は一時停止しない。
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (AbilityControls.EDITOR.matches(keyCode, scanCode)) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public void tick() {
        var client = Minecraft.getInstance();
        if (client.player == null || !client.player.isAlive()) onClose();
    }
    @Override public void onClose() {
        AbilityControls.discardClicks();
        super.onClose();
    }

    /** 原作onEdit: 中央のプリセットのスロットを設定するか空にする。決めるのはサーバー。 */
    private void edit(int slot, @Nullable ResourceLocation skill) {
        AcademyNetwork.CHANNEL.sendToServer(PresetEdit.assign(active, slot, skill));
    }

    /**
     * 原作Selector: マウスの位置に、白い光に囲まれた49,49,49の箱を出し、外す×印、続けて技能を1行4つ並べる。
     * マウスの下のものの名前を上に出す（無ければ"Select Skill"）。
     */
    final class Selector {
        final int slot;
        final float x, y, w, h;
        // nullは原作の「外す」項目。
        final List<ResourceLocation> choices = new ArrayList<>();
        Selector(int slot, float x, float y) {
            this.slot = slot; this.x = x; this.y = y;
            choices.add(null);
            var data = data();
            List<ResourceLocation> available = new ArrayList<>();
            if (data != null) for (var skill : SkillCatalog.CONTROLLABLE)
                if (skill.offeredTo(data.getAbility()) && data.hasLearned(skill.id()) && !inPreset(data, active, skill.id())) available.add(skill.id());
            choices.addAll(available);
            int rows = (choices.size() + MAX_PER_ROW - 1) / MAX_PER_ROW;
            h = MARGIN * 2 + SIZE + SEL_STEP * (rows - 1);
            // 原作は幅を技能の数で決め、×印を含めた項目数では決めない。
            w = available.size() < MAX_PER_ROW ? MARGIN * 2 + SIZE + SEL_STEP * (choices.size() - 1) : MARGIN * 2 + SIZE + SEL_STEP * (MAX_PER_ROW - 1);
        }
        float[] cell(int i) { return new float[] {x + MARGIN + (i % MAX_PER_ROW) * SEL_STEP, y + MARGIN + (i / MAX_PER_ROW) * SEL_STEP}; }
        int at(double mx, double my) {
            for (int i = 0; i < choices.size(); i++) { var c = cell(i); if (inside(mx, my, c[0], c[1], SIZE, SIZE)) return i; }
            return -1;
        }
        boolean click(double mx, double my) {
            int i = at(mx, my);
            if (i < 0) return inside(mx, my, x, y, w, h);
            edit(slot, choices.get(i));
            selector = null;
            return true;
        }
        void draw(GuiGraphics g, int mx, int my) {
            var pose = g.pose();
            pose.pushPose(); pose.translate(0, 0, 200);
            KeyHintHud.glow(pose, x, y, w, h, 1, 0xffffff, .6f);
            DeveloperScreen.rect(pose, x, y, w, h, 49 / 255f, 49 / 255f, 49 / 255f, 200 / 255f);
            int hovering = at(mx, my);
            String hint = hovering < 0 ? I18n.get("academy.gui.preset_edit.skill_select")
                    : choices.get(hovering) == null ? I18n.get("academy.gui.preset_edit.skill_remove") : name(choices.get(hovering));
            float len = font.width(hint);
            DeveloperScreen.rect(pose, x, y - 13.5f, len + 6, 11.5f, 49 / 255f, 49 / 255f, 49 / 255f, 200 / 255f);
            KeyHintHud.glow(pose, x, y - 13.5f, len + 6, 11.5f, 1, 0xffffff, .2f);
            text(g, hint, x + 3, y - 12, 9, 0, 1, 0xbbbbbb);
            for (int i = 0; i < choices.size(); i++) {
                var c = cell(i); var skill = choices.get(i);
                var node = skill == null ? null : SkillTreeLayout.node(skill);
                DeveloperScreen.quad(pose, skill == null ? CANCEL : node == null ? CANCEL : node.icon(), c[0], c[1], SIZE, SIZE, 1, 1, 1, 1);
                // Tint: 項目の上に白、マウスの下では0.2。
                if (i == hovering) DeveloperScreen.rect(pose, c[0], c[1], SIZE, SIZE, 1, 1, 1, .2f);
            }
            pose.popPose();
        }
    }
    private static boolean inPreset(PlayerAbilityData data, int preset, ResourceLocation skill) {
        for (int slot = 0; slot < 4; slot++) if (skill.equals(data.getSlot(preset, slot))) return true;
        return false;
    }

    // ---- テスト用の入口: プレイヤーがクリックする位置 ----
    public int viewedPreset() { return active; }
    public boolean transiting() { return transiting; }
    /** 0.35秒経ったものとして、滑りをすぐ終える。 */
    public void settleForTest() { if (transiting) { transitProgress = 1; transiting = false; } }
    public float[] pageCentre(int page) {
        var place = place(page); float s = place[1];
        return new float[] {pageLeft(place) + (SLOT_X + SLOT_W / 2) * s, pageTop(place) + (SLOT_Y[0] + SLOT_H / 2) * s};
    }
    public float[] slotCentre(int slot) {
        var place = place(active); float s = place[1];
        return new float[] {pageLeft(place) + (SLOT_X + SLOT_W / 2) * s, pageTop(place) + (SLOT_Y[slot] + SLOT_H / 2) * s};
    }
    public boolean selectorOpen() { return selector != null; }
    /** 選択欄の項目。先頭は「外す」を表すnull。 */
    public List<ResourceLocation> choices() { return selector == null ? List.of() : new ArrayList<>(selector.choices); }
    @Nullable public float[] choiceCentre(@Nullable ResourceLocation skill) {
        if (selector == null) return null;
        int i = selector.choices.indexOf(skill);
        if (i < 0) return null;
        var c = selector.cell(i);
        return new float[] {c[0] + SIZE / 2, c[1] + SIZE / 2};
    }
}
