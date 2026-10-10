package io.github.pinchan4273.reacademycraft.config;

import io.github.pinchan4273.reacademycraft.network.AcademyRulesSnapshot;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * このクライアントが参加しているサーバーの規則（そのサーバーが最後に送ったもの）。論理クライアントでだけ読み
 * （論理サーバーはAcademyConfigを読む）、クライアント自身のacademy-common.tomlからは決して読まない。それは参加先の
 * サーバーでは何も決めない。シングルプレイも同じ流れで、統合サーバーが送る。サーバーを離れると消え、サーバーの規則が
 * 届くまでは原作の既定値が使われる。
 */
public final class SyncedAcademyRules {
    private static volatile AcademyRulesSnapshot current;
    private SyncedAcademyRules() { }

    public static void accept(AcademyRulesSnapshot rules) { current = Objects.requireNonNull(rules); }
    public static void clear() { current = null; }
    public static boolean received() { return current != null; }

    /** 原作LearningHelperのisEnabled。サーバーの設定による。 */
    public static boolean skillEnabled(ResourceLocation id) {
        var rules = current;
        return rules == null || id == null || !"academy".equals(id.getNamespace()) || !rules.disabledSkills().contains(id.getPath());
    }
    public static float initCp(int level) {
        var rules = current;
        return rules == null ? AcademyConfig.LEGACY_INIT_CP.get(band(level)).floatValue() : rules.initCp().get(band(level));
    }
    public static float initOverload(int level) {
        var rules = current;
        return rules == null ? AcademyConfig.LEGACY_INIT_OVERLOAD.get(band(level)).floatValue() : rules.initOverload().get(band(level));
    }
    private static int band(int level) { return Math.max(0, Math.min(AcademyRulesSnapshot.LEVELS - 1, level)); }
}
