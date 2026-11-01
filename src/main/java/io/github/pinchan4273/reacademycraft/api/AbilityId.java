package io.github.pinchan4273.reacademycraft.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 能力・技能・設定の名前（名前空間付きのID）。公開API（実験的、{@link AcademyApi}）の型で、Minecraftの型を使わない
 * （26.x系で{@code ResourceLocation}の名前が変わっても、この型は変えない）。
 * <p>
 * 書式は {@code namespace:path}。名前空間は {@code [a-z0-9_.-]}、パスは {@code [a-z0-9_./-]}（Minecraftの資源の名前と同じ文字の集合）。
 * 名前空間 {@code academy} は本体だけが使う（アドオンの登録は断る。登録簿が確かめる）。
 */
public record AbilityId(String namespace, String path) implements Comparable<AbilityId> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+"), PATH = Pattern.compile("[a-z0-9_./-]+");
    /** 本体の名前空間。 */
    public static final String ACADEMY = "academy";
    /** 文字列の長さの上限（通信と保存で使う上限に合わせる）。 */
    public static final int MAX_LENGTH = 256;

    public AbilityId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches() || namespace.length() + 1 + path.length() > MAX_LENGTH)
            throw new IllegalArgumentException("Invalid ability id: " + namespace + ":" + path);
    }

    /** {@code namespace:path}を読む。名前空間の無い文字列や、書式に合わない文字列は例外。 */
    public static AbilityId parse(String text) {
        Objects.requireNonNull(text, "text");
        int colon = text.indexOf(':');
        if (colon <= 0 || colon != text.lastIndexOf(':')) throw new IllegalArgumentException("Invalid ability id: " + text);
        return new AbilityId(text.substring(0, colon), text.substring(colon + 1));
    }

    /** {@link #parse}と同じだが、読めなければnull。 */
    public static AbilityId tryParse(String text) {
        try {
            return text == null ? null : parse(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static AbilityId academy(String path) { return new AbilityId(ACADEMY, path); }

    public boolean isAcademy() { return ACADEMY.equals(namespace); }

    @Override public String toString() { return namespace + ":" + path; }

    @Override public int compareTo(AbilityId other) {
        int c = namespace.compareTo(other.namespace);
        return c != 0 ? c : path.compareTo(other.path);
    }
}
