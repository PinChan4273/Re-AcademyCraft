package io.github.pinchan4273.reacademycraft.client;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * 原作の入力欄がすべて使うLambdaLib2のTextBoxの編集: 入力はキャレット位置に入り、矢印キーでキャレットを動かし、
 * Backspaceで前の1文字を消し、Deleteで欄を空にし、Ctrl+Vでクリップボードをキャレット位置へ入れ、Ctrl+Cで全文をコピーする。
 * 長さの上限は移植版独自のもので、サーバーでも確かめる。
 */
final class TextEdit {
    private String text;
    private int caret;
    private final int limit;

    TextEdit(String text, int limit) {
        this.limit = limit;
        this.text = text.length() > limit ? text.substring(0, limit) : text;
        caret = this.text.length();
    }

    String text() { return text; }
    int caret() { return caret; }

    /** 原作ChatAllowedCharacters.isAllowedCharacterを満たす文字を、キャレット位置へ入れる。 */
    boolean type(char c) {
        if (!SharedConstants.isAllowedChatCharacter(c)) return false;
        insert(String.valueOf(c));
        return true;
    }

    /** TextBoxのキーだったときtrue。 */
    boolean key(int key) {
        if (key == GLFW.GLFW_KEY_RIGHT) caret = Math.min(text.length(), caret + 1);
        else if (key == GLFW.GLFW_KEY_LEFT) caret = Math.max(0, caret - 1);
        else if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (caret > 0) { text = text.substring(0, caret - 1) + text.substring(caret); caret--; }
        } else if (key == GLFW.GLFW_KEY_DELETE) { text = ""; caret = 0; }
        else if (Screen.isPaste(key)) insert(SharedConstants.filterText(Minecraft.getInstance().keyboardHandler.getClipboard()));
        else if (Screen.isCopy(key)) Minecraft.getInstance().keyboardHandler.setClipboard(text);
        else return false;
        return true;
    }

    /** キャレット（原作の"|"）を位置に入れた文字列。 */
    String shown(boolean hidden) {
        String visible = hidden ? "*".repeat(text.length()) : text;
        return visible.substring(0, caret) + "|" + visible.substring(caret);
    }

    private void insert(String piece) {
        int room = limit - text.length();
        if (room <= 0 || piece.isEmpty()) return;
        if (piece.length() > room) piece = piece.substring(0, room);
        text = text.substring(0, caret) + piece + text.substring(caret);
        caret += piece.length();
    }
}
