package io.github.pinchan4273.reacademycraft.mixin;

import java.util.Queue;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「保存して終了」でサーバーが止まる問題（M-03）の回避（W1、2026-10-17。修正候補で、M-03はまだ解決済みではない）。
 *
 * <p>Minecraft 1.21.1の{@code ChunkMap.processUnloads}の後半の
 * {@code while ((hasMoreTime || k > 0) && (runnable = unloadQueue.poll()) != null)}は、
 * 解放の続き（{@code scheduleUnload}の{@code thenRunAsync}）が{@code isReadyForSaving()}でないときに同じ列へ戻るので、
 * 停止のループ（{@code hasMoreTime}がいつもtrue）では、戻った続きを同じ呼び出しの中で取り出し続け、呼び出しから戻らない。
 * そのholderの生成のタスクは、サーバーのスレッドの仕事（実行器の列）を待っているので、参照が0に戻らず、止まったままになる。
 *
 * <p>ここでは、1回の呼び出しで取り出す件数を、その呼び出しで最初に{@code poll()}するときの列の件数までにする。
 * 最初の{@code poll()}は、同じ呼び出しの前半（新しく解放を始める分の{@code scheduleUnload}）の後なので、その呼び出しで新しく入った続きも含む。
 * 取り出している間に列へ戻った続きは、次の呼び出しで取り出す。その間に停止のループの{@code waitUntilNextTick}がサーバーのスレッドの仕事を処理する。
 *
 * <p>列の要素は取り除かず（{@code null}を返してループを終えるだけ）、保存・解放・生成の処理は飛ばさない。
 * 時間の無いとき（{@code hasMoreTime}がfalseで{@code k}が0）は、元から{@code poll()}を呼ばないので変わらない。
 * 挙動は、診断のE1-Fで確かめた{@code m03diag.mixin.ChunkMapW1Mixin}（02ca6e9f）と同じで、計測だけを除いた。
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapUnloadLivelockMixin {
    /** その呼び出しで残っている取り出しの数。-1は、その呼び出しでまだ{@code poll()}していないこと。サーバーのスレッドだけが使う。 */
    @Unique private int academy$unloadPollBudget = -1;

    @Inject(method = "processUnloads(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void academy$beginUnloadPolls(BooleanSupplier hasMoreTime, CallbackInfo ci) {
        this.academy$unloadPollBudget = -1;
    }

    @Redirect(method = "processUnloads(Ljava/util/function/BooleanSupplier;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;"))
    private Object academy$pollUnloadWithinBudget(Queue<?> queue) {
        if (this.academy$unloadPollBudget < 0) this.academy$unloadPollBudget = queue.size();
        if (this.academy$unloadPollBudget == 0) return null;
        this.academy$unloadPollBudget--;
        return queue.poll();
    }
}
