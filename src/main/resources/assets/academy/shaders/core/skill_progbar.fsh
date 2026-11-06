#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// 原作 skill_progbar.frag: 放射のマスクの赤が進み具合（extra）より小さい所だけ見せる。環は上から時計回りに伸びる。
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord0;
in vec4 vertexColor;
flat in float extra;

out vec4 fragColor;

void main() {
    float threshold = texture(Sampler1, texCoord0).r;
    if (!(extra > threshold)) discard;
    fragColor = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
}
