#version 150

// Legacy plasma_body.frag, line for line: a metaball density from up to 16 balls in camera space
// (z negated), ray-marched 20 steps of 0.15 from 3 behind the pixel along the view ray, coloured
// between light blue and pink by density.
uniform int BallCount;
uniform vec4 Balls[16];
uniform float Alpha;

in vec3 camspace;

out vec4 fragColor;

float f(vec3 position) {
    float ret = 0.0;
    for (int i = 0; i < BallCount; ++i) {
        float distance = max(0.1, length(position - Balls[i].xyz));
        ret += Alpha * Balls[i].w / (distance * distance);
    }
    return clamp(ret, 0.0, 2.0);
}

vec4 rayMarch(vec3 begin, vec3 dir) {
    dir *= 0.15;
    vec3 pos = begin;
    vec4 accum = vec4(0.0);
    for (int i = 0; i < 20 && accum.a < 1.0; ++i) {
        float density = f(pos);
        float alpha = 0.075 * density;
        vec3 crl = mix(vec3(0.43, 0.74, 1.0), vec3(0.98, 0.51, 0.92), 1.0 - density / 2.0);
        accum.rgb = mix(accum.rgb, crl, alpha / (accum.a + alpha));
        accum.a += alpha;
        pos += dir;
    }
    if (accum.a < 0.2) {
        accum.a = 2.0 * accum.a - 0.2;
    }
    return accum;
}

void main() {
    vec3 cam = camspace;
    cam.z = -cam.z;
    vec3 dir = normalize(cam);
    vec4 rc = rayMarch(cam - dir * 3.0, dir);
    rc.a = clamp(rc.a, 0.0, 1.0) * (0.5 + Alpha * 0.5);
    fragColor = rc;
}
