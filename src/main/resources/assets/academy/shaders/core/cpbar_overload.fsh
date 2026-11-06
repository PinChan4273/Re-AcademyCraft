#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// 原作 cpbar_overload.frag: 過負荷の棒のテクスチャを横へずらし（extra）、マスクのある所だけ見せる。
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord0;
in vec4 vertexColor;
flat in float extra;

out vec4 fragColor;

void main() {
    vec4 colorTex = vertexColor * ColorModulator * texture(Sampler0, texCoord0 + vec2(extra, 0.0));
    float colorMask = texture(Sampler1, texCoord0).a;
    fragColor = vec4(colorTex.rgb, colorMask * colorTex.a);
}
