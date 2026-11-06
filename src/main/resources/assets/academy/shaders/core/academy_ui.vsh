#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// HUD と画面の、頂点ごとの値を 1 つ持つシェーダー（skill_progbar の Progress、cpbar_overload の TexOffset）。
// 26.2 はゆるい uniform が無いので、値は UV1 の x（AcademyPipelines.EXTRA_SCALE 倍の整数）で渡す。
in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV1;

out vec2 texCoord0;
out vec4 vertexColor;
flat out float extra;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    extra = float(UV1.x) / 30000.0;
}
