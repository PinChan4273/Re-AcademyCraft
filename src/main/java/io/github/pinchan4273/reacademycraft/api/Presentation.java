package io.github.pinchan4273.reacademycraft.api;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * 表示の情報（公開API、実験的）。nameKeyは名前の翻訳キー、descriptionKeyは説明の翻訳キー（無ければnull）、iconは画像の資源の名前
 * （{@code namespace:textures/...png}の形。無ければnull）。表示の情報は、サーバーの判定には使わない。
 */
public record Presentation(String nameKey, @Nullable String descriptionKey, @Nullable AbilityId icon) {
    public Presentation {
        Objects.requireNonNull(nameKey, "nameKey");
        if (nameKey.isEmpty() || nameKey.length() > AbilityId.MAX_LENGTH) throw new IllegalArgumentException("Invalid name key");
        if (descriptionKey != null && (descriptionKey.isEmpty() || descriptionKey.length() > AbilityId.MAX_LENGTH))
            throw new IllegalArgumentException("Invalid description key");
    }
}
