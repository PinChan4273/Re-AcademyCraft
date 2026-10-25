package io.github.pinchan4273.academyexample;

import io.github.pinchan4273.reacademycraft.api.ActivationOutcome;
import io.github.pinchan4273.reacademycraft.api.ActorKind;
import io.github.pinchan4273.reacademycraft.api.mc.AbilityActivation;
import io.github.pinchan4273.reacademycraft.api.mc.AbilityActor;
import io.github.pinchan4273.reacademycraft.api.mc.ResourcePolicy;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Kinetic Charge（機械の主体の例）: 置いた人を持ち主として覚え、赤石の信号が入った瞬間に、外部の主体（{@code ActorKind.MACHINE}）として
 * 技能Burstを{@link AbilityActivation#activate}で発動する。Burstの爆発は{@code AcademySafety.explode}を通す。
 * 発動の瞬間に、チャージは自分のブロックを燃料として消す（素の爆発のベッドなどと同じ。ブロックを残したまま中心で爆発させると、どの相手からの
 * 光線もチャージ自身に遮られ、当たりが最小になる）。発動が断られたら（設定で無効・妨害・クールダウンなど）、ブロックを元に戻す。
 * 周りのブロックを壊すかどうかは、安全のサービスが判定する。
 * <ul>
 * <li>帰属先は、持ち主がログインしていればその人。いなければ帰属先は無い（Re:AcademyCraftは、帰属先の無い機械の地形の変更と
 * プレイヤーへの攻撃を断る）。コマンドで置いたときは持ち主が無い（ブロックのデータ{@code Owner}で持ち主を指定できる）。</li>
 * <li>機械には能力者のCPが無いので、資源の方針は{@link ResourcePolicy#FREE}を明示して渡す。</li>
 * </ul>
 */
public final class KineticChargeBlock extends BaseEntityBlock {
    public KineticChargeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.POWERED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BlockStateProperties.POWERED); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new KineticChargeBlockEntity(pos, state); }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof KineticChargeBlockEntity charge) charge.setOwner(player.getUUID());
    }

    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (!(level instanceof ServerLevel server)) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(BlockStateProperties.POWERED)) return;
        level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), Block.UPDATE_CLIENTS);
        // 信号が入った瞬間だけ発動する。
        if (powered && level.getBlockEntity(pos) instanceof KineticChargeBlockEntity charge) fire(server, pos, charge);
    }

    private static void fire(ServerLevel level, BlockPos pos, KineticChargeBlockEntity charge) {
        var owner = charge.owner() == null ? null : level.getServer().getPlayerList().getPlayer(charge.owner());
        var actor = new Machine(charge.actorId(), level, pos, owner);
        var state = level.getBlockState(pos);
        var saved = charge.saveWithoutMetadata();
        level.removeBlock(pos, false);
        ActivationOutcome outcome = AbilityActivation.activate(actor, AcademyExample.BURST, ResourcePolicy.FREE);
        if (outcome != ActivationOutcome.PERFORMED && level.getBlockState(pos).isAir()) {
            // 断られたら、燃料を使っていないので元に戻す（持ち主と主体の識別子も）。
            level.setBlock(pos, state, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof KineticChargeBlockEntity restored) restored.load(saved);
        }
        AcademyExample.LOGGER.info("Kinetic Charge at {} fired by {}: {}", pos, owner == null ? "no owner" : owner.getGameProfile().getName(), outcome);
    }

    /** 機械の主体。位置はブロックの中心、実体は無い（1回きりの発動なので、ブロックを消した後も、その発動の間は使える）。 */
    private record Machine(UUID id, ServerLevel level, BlockPos pos, @Nullable ServerPlayer attributedPlayer) implements AbilityActor {
        @Override public ActorKind kind() { return ActorKind.MACHINE; }
        @Override public Vec3 position() { return Vec3.atCenterOf(pos); }
        @Override public Vec3 look() { return new Vec3(0, 1, 0); }
        @Override public @Nullable Entity entity() { return null; }
    }
}
