//!HOOK MAIN
//!BIND HOOKED
//!DESC Elefin Cinema light edge-preserving denoise
vec4 hook() {
    vec4 center = HOOKED_tex(HOOKED_pos);
    vec3 total = center.rgb;
    float weightSum = 1.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            if (x == 0 && y == 0) continue;
            vec3 sampleColor = HOOKED_tex(HOOKED_pos + vec2(float(x), float(y)) * HOOKED_pt).rgb;
            vec3 delta = sampleColor - center.rgb;
            float spatial = (x != 0 && y != 0) ? 0.35 : 0.5;
            float weight = spatial * exp(-dot(delta, delta) / 0.0012);
            total += sampleColor * weight;
            weightSum += weight;
        }
    }
    return vec4(mix(center.rgb, total / weightSum, 0.45), center.a);
}
