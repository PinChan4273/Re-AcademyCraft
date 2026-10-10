package io.github.pinchan4273.reacademycraft.client;

import io.github.pinchan4273.reacademycraft.capability.AbilityCapabilities;
import io.github.pinchan4273.reacademycraft.network.GroundshockEffect;
import io.github.pinchan4273.reacademycraft.skill.Groundshock;
import io.github.pinchan4273.reacademycraft.world.AcademyParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 原作Groundshockのclient側。
 * - キーを押している間（l_tick）、術者の視点が上がる: 最初の4tickは1tickあたり0.2×t/4度、tick 20まで0.2度、
 *   tick 25までに0へ弱まる。
 * - MSG_PERFORMを受けたとき（c_perform）: 術者の視点を4tickのあいだ1tickあたり3.4度下へ振る。
 *   衝撃が通った各ブロックの上に破片を4〜7個飛ばし、その半分に煙を付ける。
 */
@Mod.EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClientGroundshock {
    private static int heldTicks, slashTicks;
    private static long received, smokes, fragments;
    private static int lastBlocks;
    private ClientGroundshock() { }
    /** 原作l_tickの、押し始めからのtickごとのピッチの変化。 */
    static float lift(int t) {
        float delta = t < 4 ? t / 4f : t <= 20 ? 1 : t <= 25 ? 1 - (t - 20) / 5f : 0;
        return delta * .2f;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null) { heldTicks = 0; slashTicks = 0; return; }
        boolean held = false;
        var data = player.getCapability(AbilityCapabilities.PLAYER_ABILITY).orElse(null);
        if (data != null) for (int i = 0; i < 4; i++)
            if (Groundshock.ID.equals(data.getSlot(data.getCurrentPreset(), i)) && AbilityControls.held(i)) held = true;
        if (held) player.setXRot(Math.max(-90, player.getXRot() - lift(++heldTicks)));
        else heldTicks = 0;
        if (slashTicks > 0) { slashTicks--; player.setXRot(Math.min(90, player.getXRot() + 3.4f)); }
    }
    public static void receive(GroundshockEffect effect) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null) return;
        received++; lastBlocks = effect.blocks().size();
        if (client.player != null && client.player.getId() == effect.caster()) slashTicks = 4;
        var random = level.random;
        for (var pos : effect.blocks()) {
            var state = level.getBlockState(pos);
            int count = 4 + random.nextInt(4);
            if (!state.isAir()) for (int i = 0; i < count; i++) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + random.nextDouble(), pos.getY() + 1 + random.nextDouble() * .5 + .2, pos.getZ() + random.nextDouble(),
                        -.2 + random.nextDouble() * .4, .1 + random.nextDouble() * .2, -.2 + random.nextDouble() * .4);
                fragments++;
            }
            if (random.nextFloat() < .5f) {
                level.addAlwaysVisibleParticle(AcademyParticles.SMOKE.get(), true,
                        pos.getX() + .5 + (random.nextDouble() * .6 - .3), pos.getY() + 1 + random.nextDouble() * .2, pos.getZ() + .5 + (random.nextDouble() * .6 - .3),
                        random.nextDouble() * .06 - .03, .03 + random.nextDouble() * .03, random.nextDouble() * .06 - .03);
                smokes++;
            }
        }
    }
    // テスト用。
    public static long received() { return received; }
    public static long smokes() { return smokes; }
    public static long fragments() { return fragments; }
    public static int heldTicks() { return heldTicks; }
    public static int lastBlocks() { return lastBlocks; }
}
