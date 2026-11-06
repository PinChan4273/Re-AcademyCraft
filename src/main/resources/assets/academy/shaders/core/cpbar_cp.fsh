#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// 原作 cpbar_cp.frag: CP の棒のテクスチャから、棒の中のアイコンの位置（964×147 の 857, 43 に 65×65）で、系統のアイコンの形を抜く。
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const vec2 origSize = vec2(964.0, 147.0);
const vec2 iconOffset = vec2(857.0, 43.0);
const vec2 iconMul = vec2(1.0 / 65.0, 1.0 / 65.0);

void main() {
    vec2 temp = ((texCoord0 * origSize) - iconOffset) * iconMul;
    float maskColor;
    if (temp.s < 0.0 || temp.s > 1.0 || temp.t < 0.0 || temp.t > 1.0) {
        maskColor = 1.0;
    } else {
        maskColor = 1.0 - texture(Sampler1, temp.st).a;
    }
    vec4 texColor = texture(Sampler0, texCoord0);
    fragColor = vertexColor * ColorModulator * vec4(texColor.rgb, texColor.a * maskColor);
}
