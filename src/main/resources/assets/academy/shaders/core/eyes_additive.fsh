#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// 1.21.1 の vanilla の rendertype_eyes と同じ: 絵 × 頂点の色を、加算（ONE, ONE）で足す。
// 霧は色を霧の色へ寄せず、遠いほど 0 へ薄める（1.21.1 の linear_fog_fade）。加算で霧の色へ寄せると、遠くの甲虫や翼が霧の色の分だけ明るく浮く。
// 頂点シェーダーは vanilla の core/entity（EMISSIVE・NO_OVERLAY・NO_CARDINAL_LIGHTING）。
uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    float fog = total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);
    fragColor = color * (1.0 - fog * FogColor.a);
}
