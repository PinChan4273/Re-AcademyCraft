#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// 原作 plasma_body.vert: カメラ空間の位置をフラグメントへ渡す。
in vec3 Position;
#ifdef ACADEMY_FILL_TARGET
// シェーダーパックの使用中だけ（ShaderPackDraw.plasmaBody）: 本体の四角の中の位置（0〜1）で、専用の描画先いっぱいに描く。
in vec2 UV0;
#endif

out vec3 camspace;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
#ifdef ACADEMY_FILL_TARGET
    gl_Position = vec4(UV0 * 2.0 - 1.0, 0.0, 1.0);
#else
    gl_Position = ProjMat * view;
#endif
    camspace = view.xyz / view.w;
}
