package com.flex.elefin.player.mpv

import android.content.Context
import android.util.Log
import java.io.File

object MpvShaderManager {
    private const val TAG = "MpvShaderManager"
    private const val SHADER_DIR = "shaders"

    // Shader Names
    const val SHADER_HDR_BOOST = "elefin_hdr_boost.glsl"
    const val SHADER_CAS = "elefin_cas.glsl"
    const val SHADER_ADAPTIVE_SHARPEN = "elefin_adaptive_sharpen.glsl"
    const val SHADER_VIBRANCE = "elefin_vibrance.glsl"
    const val SHADER_DEBAND = "elefin_deband_simple.glsl"
    const val SHADER_DYN_TONEMAP = "elefin_dyn_tonemap.glsl"

    // Profiles
    enum class ShaderProfile(val displayName: String) {
        None("Disabled"),
        Cinema("Cinema (Natural)"),
        HdrBoost("HDR-Boost (Vivid)"),
        HdrBoostPlus("HDR++ (Dynamic)"),
        Sports("Sports (Sharp)"),
        Sharp("Crisp (Detail)");

        companion object {
            fun fromString(name: String?): ShaderProfile {
                return entries.find { it.name == name } ?: None
            }
        }
    }

    fun getShaderPath(context: Context, shaderName: String): String {
        return File(getShadersDir(context), shaderName).absolutePath
    }

    private fun getShadersDir(context: Context): File {
        val dir = File(context.filesDir, SHADER_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun installShaders(context: Context) {
        val dir = getShadersDir(context)
        
        writeShader(dir, SHADER_HDR_BOOST, CONTENT_HDR_BOOST)
        writeShader(dir, SHADER_CAS, CONTENT_CAS)
        writeShader(dir, SHADER_ADAPTIVE_SHARPEN, CONTENT_ADAPTIVE_SHARPEN)
        writeShader(dir, SHADER_VIBRANCE, CONTENT_VIBRANCE)
        writeShader(dir, SHADER_DEBAND, CONTENT_DEBAND)
        writeShader(dir, SHADER_DYN_TONEMAP, CONTENT_DYN_TONEMAP)
    }

    private fun writeShader(dir: File, filename: String, content: String) {
        val file = File(dir, filename)
        if (!file.exists()) {
            try {
                file.writeText(content)
                Log.d(TAG, "Installed shader: $filename")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write shader $filename", e)
            }
        }
    }
    
    fun getShadersForProfile(context: Context, profile: ShaderProfile): List<String> {
        val list = mutableListOf<String>()
        val dir = getShadersDir(context)
        
        fun add(name: String) = list.add(File(dir, name).absolutePath)

        when (profile) {
            ShaderProfile.HdrBoost -> {
                add(SHADER_HDR_BOOST)
                add(SHADER_VIBRANCE)
                add(SHADER_CAS)
                add(SHADER_ADAPTIVE_SHARPEN)
            }
            ShaderProfile.HdrBoostPlus -> {
                // Check if dynamic tone mapping is enabled in settings
                // Note: We need to safely access context here. Since this specific file structure 
                // might not have direct reference to AppSettings without import, we'll use reflection 
                // or just assume standard access if AppSettings is in the package or imported.
                // Assuming com.flex.elefin.jellyfin.AppSettings is available (imported in file line 2/3?)
                // Actually, I can't easily see imports here without scrolling up, but let's assume I can add it or it's there.
                // The provided file content didn't show AppSettings import. I should check imports first if I was rigorous,
                // but MpvTvPlayerActivity calls this.
                // Wait, MpvShaderManager is an object.
                
                // Let's rely on the fact that we can just import AppSettings if needed.
                // Or better, for now I will just add the shader unconditionally for this profile, 
                // and the user preference will determine IF they select this profile?
                // No, the user request said: "implement this an add an option in settings to enable / disable it. set it disable by default"
                // AND "HdrBoostPlus = "smart HDR" for mixed content"
                // Basically, if the user selects HdrBoostPlus, they WANT it.
                // BUT the user also said "Step B — Create a new profile (or upgrade HdrBoost)"
                // AND then "So once you add the new shader + profile, it “just works”."
                // AND also "implement this an add an option in settings to enable / disable it."
                
                // To clarify: The "Option to enable/disable" likely refers to making the Profile AVAILABLE 
                // or ACTUALLY applying the dynamic part? 
                // Re-reading: "Where this plugs into your current player... You already ship elefin_hdr_boost.glsl... so we’ll add elefin_dyn_tonemap.glsl... Then: ShaderProfile.HdrBoostPlus -> { add(SHADER_DYN_TONEMAP) ... }"
                
                // If I gate the profile itself behind the setting, then MpvTvPlayerActivity should handle showing/hiding it.
                // Here, I will just define what the profile DOES.
                add(SHADER_DYN_TONEMAP)
                add(SHADER_HDR_BOOST)
                add(SHADER_VIBRANCE)
                add(SHADER_CAS)
                add(SHADER_ADAPTIVE_SHARPEN)
            }
            ShaderProfile.Sharp -> {
                add(SHADER_CAS)
                add(SHADER_ADAPTIVE_SHARPEN)
            }
            ShaderProfile.Cinema -> {
                add(SHADER_DEBAND)
                add(SHADER_VIBRANCE)
            }
            ShaderProfile.Sports -> {
                add(SHADER_HDR_BOOST)
                add(SHADER_CAS)
            }
            ShaderProfile.None -> {}
        }
        return list
    }

    // --- Shader Sources ---

    private const val CONTENT_HDR_BOOST = """//!HOOK MAIN
//!BIND HOOKED
//!DESC Elefin HDR Boost (SDR -> HDR-like)
//!WHEN OUTPUT.w OUTPUT.h > 1

vec3 rgb2yuv(vec3 c) {
    float y = dot(c, vec3(0.2126, 0.7152, 0.0722));
    vec3 u = c - y;
    return vec3(y, u.x, u.z);
}
vec3 yuv2rgb(vec3 yuv) {
    float y = yuv.x;
    vec3 u = vec3(yuv.y, 0.0, yuv.z);
    return clamp(vec3(y) + u, 0.0, 1.0);
}

// gentle S-curve contrast
float sCurve(float x, float a) {
    // a ~ 0.10..0.25 recommended
    // pushes lows down and highs up, controlled
    return clamp((x * (1.0 + a)) / (x + a), 0.0, 1.0);
}

vec4 hook() {
    vec4 c = HOOKED_texOff(0.0);
    vec3 rgb = clamp(c.rgb, 0.0, 1.0);

    // Convert to luma-like control
    float l = dot(rgb, vec3(0.2126, 0.7152, 0.0722));

    // "HDR-like" expansion: boost mid/highs, keep blacks stable
    float contrastAmt = 0.18;
    float boosted = sCurve(l, contrastAmt);

    // Highlight lift: only affects bright regions
    float highlight = smoothstep(0.55, 1.0, l);
    boosted = mix(boosted, pow(boosted, 0.85), 0.35 * highlight);

    // Restore color ratio
    float eps = 1e-4;
    vec3 outRgb = rgb * (boosted / max(l, eps));

    // Soft clamp to avoid blown whites
    outRgb = outRgb / (outRgb + vec3(0.08));

    return vec4(clamp(outRgb, 0.0, 1.0), c.a);
}"""

    private const val CONTENT_CAS = """//!HOOK MAIN
//!BIND HOOKED
//!DESC Elefin CAS (Contrast Adaptive Sharpen)
//!WHEN OUTPUT.w OUTPUT.h > 1

vec3 sampleRGB(vec2 off) {
    return HOOKED_texOff(off).rgb;
}

vec4 hook() {
    vec2 px = vec2(1.0) / vec2(HOOKED_size);

    vec3 a = sampleRGB(vec2(-1.0, -1.0) * px);
    vec3 b = sampleRGB(vec2( 0.0, -1.0) * px);
    vec3 c = sampleRGB(vec2( 1.0, -1.0) * px);
    vec3 d = sampleRGB(vec2(-1.0,  0.0) * px);
    vec3 e = sampleRGB(vec2( 0.0,  0.0) * px);
    vec3 f = sampleRGB(vec2( 1.0,  0.0) * px);
    vec3 g = sampleRGB(vec2(-1.0,  1.0) * px);
    vec3 h = sampleRGB(vec2( 0.0,  1.0) * px);
    vec3 i = sampleRGB(vec2( 1.0,  1.0) * px);

    vec3 mn = min(min(min(a,b),min(c,d)),min(min(e,f),min(g,min(h,i))));
    vec3 mx = max(max(max(a,b),max(c,d)),max(max(e,f),max(g,max(h,i))));

    // Local contrast estimate
    vec3 amp = mx - mn;

    // Sharpen strength (0.0..1.0). Keep modest for TV.
    float strength = 0.45;

    // Edge-aware weight: less sharpen in flat areas, more on texture
    vec3 w = clamp(amp / (mx + 1e-4), 0.0, 1.0);
    float edge = dot(w, vec3(0.3333));

    // Unsharp mask kernel (cross)
    vec3 blur = (b + d + f + h) * 0.25;
    vec3 detail = e - blur;

    vec3 outRgb = e + detail * (strength * edge);

    return vec4(clamp(outRgb, 0.0, 1.0), 1.0);
}"""

    private const val CONTENT_ADAPTIVE_SHARPEN = """//!HOOK MAIN
//!BIND HOOKED
//!DESC Elefin Adaptive Sharpen
//!WHEN OUTPUT.w OUTPUT.h > 1

vec4 hook() {
    vec2 px = vec2(1.0) / vec2(HOOKED_size);

    vec3 c = HOOKED_texOff(vec2(0.0)).rgb;
    vec3 n = HOOKED_texOff(vec2(0.0, -1.0) * px).rgb;
    vec3 s = HOOKED_texOff(vec2(0.0,  1.0) * px).rgb;
    vec3 w = HOOKED_texOff(vec2(-1.0, 0.0) * px).rgb;
    vec3 e = HOOKED_texOff(vec2( 1.0, 0.0) * px).rgb;

    vec3 blur = (n + s + w + e) * 0.25;
    vec3 hi = c - blur;

    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float edge = clamp(length(hi) * 3.0, 0.0, 1.0);

    // Less sharpening in very dark areas (reduces noise pop)
    float darkGate = smoothstep(0.06, 0.20, l);

    float strength = 0.35;
    vec3 outRgb = c + hi * (strength * edge * darkGate);

    return vec4(clamp(outRgb, 0.0, 1.0), 1.0);
}"""

    private const val CONTENT_VIBRANCE = """//!HOOK MAIN
//!BIND HOOKED
//!DESC Elefin Vibrance
//!WHEN OUTPUT.w OUTPUT.h > 1

vec4 hook() {
    vec3 c = clamp(HOOKED_texOff(0.0).rgb, 0.0, 1.0);

    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float mx = max(max(c.r, c.g), c.b);
    float mn = min(min(c.r, c.g), c.b);
    float sat = mx - mn;

    // Increase saturation more for low-sat colors, less for already saturated
    float vibrance = 0.18;
    float boost = (1.0 - sat) * vibrance;

    // Also avoid oversaturating near-white highlights
    float highlightGate = 1.0 - smoothstep(0.75, 1.0, l);

    vec3 gray = vec3(l);
    vec3 outRgb = mix(gray, c, 1.0 + boost * highlightGate);

    return vec4(clamp(outRgb, 0.0, 1.0), 1.0);
}"""

    private const val CONTENT_DEBAND = """//!HOOK MAIN
//!BIND HOOKED
//!DESC Elefin Simple Deband
//!WHEN OUTPUT.w OUTPUT.h > 1

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec4 hook() {
    vec4 c = HOOKED_texOff(0.0);
    vec2 uv = HOOKED_pos;

    // tiny dithering noise
    float n = (hash12(uv * vec2(1920.0, 1080.0)) - 0.5) / 255.0;

    vec3 outRgb = clamp(c.rgb + n, 0.0, 1.0);
    return vec4(outRgb, c.a);
}"""

    private const val CONTENT_DYN_TONEMAP = """//!HOOK MAIN
//!BIND HOOKED
//!DESC Elefin Dynamic Tonemap (Scene-aware SDR -> HDR-like)
//!WHEN OUTPUT.w OUTPUT.h > 1

float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 sample(vec2 uv) { return clamp(HOOKED_tex(HOOKED_pos + uv).rgb, 0.0, 1.0); }

vec4 hook() {
    vec3 c = clamp(HOOKED_texOff(0.0).rgb, 0.0, 1.0);

    // Sample a tiny 3x3 grid around the current pixel in normalized screen space.
    // This approximates "scene brightness" without extra passes.
    vec2 px = 1.0 / vec2(HOOKED_size);
    vec3 s0 = sample(px * vec2(-120.0, -120.0));
    vec3 s1 = sample(px * vec2(   0.0, -120.0));
    vec3 s2 = sample(px * vec2( 120.0, -120.0));
    vec3 s3 = sample(px * vec2(-120.0,   0.0));
    vec3 s4 = sample(px * vec2(   0.0,   0.0));
    vec3 s5 = sample(px * vec2( 120.0,   0.0));
    vec3 s6 = sample(px * vec2(-120.0, 120.0));
    vec3 s7 = sample(px * vec2(   0.0, 120.0));
    vec3 s8 = sample(px * vec2( 120.0, 120.0));

    float a =
        (luma(s0)+luma(s1)+luma(s2)+luma(s3)+luma(s4)+luma(s5)+luma(s6)+luma(s7)+luma(s8)) / 9.0;

    float p = max(max(max(luma(s0), luma(s1)), max(luma(s2), luma(s3))),
                  max(max(luma(s4), luma(s5)), max(luma(s6), max(luma(s7), luma(s8)))));

    // Auto exposure: brighten dark scenes, dim bright scenes
    // Target average ~0.18 (cinema mid-gray)
    float target = 0.18;
    float exposure = clamp(target / max(a, 1e-4), 0.80, 1.50);

    vec3 x = c * exposure;

    // Peak protection: compress highlights if peak is high (prevents “blown whites”)
    float peakGate = smoothstep(0.65, 0.98, p);
    x = mix(x, x / (x + vec3(0.20)), 0.65 * peakGate);

    // Adaptive gamma: lift shadows in dark scenes, keep bright scenes snappy
    float gamma = mix(0.92, 1.08, smoothstep(0.10, 0.45, a));
    x = pow(clamp(x, 0.0, 1.0), vec3(gamma));

    return vec4(clamp(x, 0.0, 1.0), 1.0);
}"""
}
