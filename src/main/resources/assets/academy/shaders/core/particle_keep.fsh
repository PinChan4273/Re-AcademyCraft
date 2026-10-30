#version 150

#moj_import <fog.glsl>

// Minecraft's particle shader, but dropping only fully clear fragments: AcademyCraft 1.12.2 drew this smoke with the
// alpha test off (glDisable(GL_ALPHA_TEST)), where the vanilla shader drops everything under alpha 0.1.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
