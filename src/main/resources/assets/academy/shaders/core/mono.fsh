#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// LambdaLib2 の mono.frag: テクスチャと色の積の赤緑青を平均する。
uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 result = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (result.a == 0.0) discard;
    float c = (result.r + result.g + result.b) / 3.0;
    fragColor = vec4(c, c, c, result.a);
}
