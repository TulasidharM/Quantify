package com.quantify.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ——— Theme registry: each entry has a label, a light scheme, and a dark scheme ———

data class ThemeEntry(
    val key: String,
    val label: String,
    val light: androidx.compose.material3.ColorScheme,
    val dark: androidx.compose.material3.ColorScheme,
    val swatchLight: Color,
    val swatchDark: Color
)

val THEMES = listOf<ThemeEntry>(
    // 1 · Forest (default — green-tinted)
    ThemeEntry(
        key = "forest",
        label = "Forest",
        light = lightColorScheme(
            primary = Color(0xFF4C662B), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFC4EEA6), onPrimaryContainer = Color(0xFF122000),
            secondary = Color(0xFF586249), onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF386663), onTertiary = Color(0xFFFFFFFF),
            surface = Color(0xFFF9FAEF), onSurface = Color(0xFF1A1C16),
            surfaceVariant = Color(0xFFE0E4D6), onSurfaceVariant = Color(0xFF44483E),
            surfaceContainer = Color(0xFFEBEEE3), surfaceContainerHighest = Color(0xFFE5E9DC),
            error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
            outline = Color(0xFF75796F)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFB3D18D), onPrimary = Color(0xFF1F3701),
            primaryContainer = Color(0xFF354E16), onPrimaryContainer = Color(0xFFC4EEA6),
            secondary = Color(0xFFBFC9AB), onSecondary = Color(0xFF2A341E),
            tertiary = Color(0xFFA0D0CB), onTertiary = Color(0xFF003735),
            surface = Color(0xFF1A1C16), onSurface = Color(0xFFE2E3D9),
            surfaceVariant = Color(0xFF44483E), onSurfaceVariant = Color(0xFFC4C8BA),
            surfaceContainer = Color(0xFF24271F), surfaceContainerHighest = Color(0xFF2F3229),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            outline = Color(0xFF8E9287)
        ),
        swatchLight = Color(0xFF4C662B),
        swatchDark = Color(0xFF1A1C16)
    ),

    // 2 · Ocean (blue-tinted)
    ThemeEntry(
        key = "ocean",
        label = "Ocean",
        light = lightColorScheme(
            primary = Color(0xFF0061A4), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD1E4FF), onPrimaryContainer = Color(0xFF001D36),
            secondary = Color(0xFF4F6073), onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF68577A), onTertiary = Color(0xFFFFFFFF),
            surface = Color(0xFFF8FDFF), onSurface = Color(0xFF0A1C2C),
            surfaceVariant = Color(0xFFDFE2EB), onSurfaceVariant = Color(0xFF43474E),
            surfaceContainer = Color(0xFFDDE2EB), surfaceContainerHighest = Color(0xFFD8DCE5),
            error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
            outline = Color(0xFF73777F)
        ),
        dark = darkColorScheme(
            primary = Color(0xFF9DCBFF), onPrimary = Color(0xFF00325A),
            primaryContainer = Color(0xFF00497F), onPrimaryContainer = Color(0xFFD1E4FF),
            secondary = Color(0xFFB7C8DC), onSecondary = Color(0xFF213243),
            tertiary = Color(0xFFD3BFE0), onTertiary = Color(0xFF39294B),
            surface = Color(0xFF0A1C2C), onSurface = Color(0xFFD9E2EC),
            surfaceVariant = Color(0xFF43474E), onSurfaceVariant = Color(0xFFC3C7CF),
            surfaceContainer = Color(0xFF15293A), surfaceContainerHighest = Color(0xFF1F3346),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            outline = Color(0xFF8D9199)
        ),
        swatchLight = Color(0xFF0061A4),
        swatchDark = Color(0xFF0A1C2C)
    ),

    // 3 · Lavender (purple-tinted)
    ThemeEntry(
        key = "lavender",
        label = "Lavender",
        light = lightColorScheme(
            primary = Color(0xFF6750A4), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE9DEFF), onPrimaryContainer = Color(0xFF21005D),
            secondary = Color(0xFF625B71), onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF7D5260), onTertiary = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFBFF), onSurface = Color(0xFF1C1B1F),
            surfaceVariant = Color(0xFFE7E0EC), onSurfaceVariant = Color(0xFF49454F),
            surfaceContainer = Color(0xFFE1D9E8), surfaceContainerHighest = Color(0xFFDBD3E4),
            error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
            outline = Color(0xFF7A7580)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFCFBCFF), onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFE9DEFF),
            secondary = Color(0xFFCBC2DB), onSecondary = Color(0xFF332D41),
            tertiary = Color(0xFFEBB7C9), onTertiary = Color(0xFF4A2532),
            surface = Color(0xFF1C1B1F), onSurface = Color(0xFFE5E0E6),
            surfaceVariant = Color(0xFF49454F), onSurfaceVariant = Color(0xFFCAC4D0),
            surfaceContainer = Color(0xFF28262C), surfaceContainerHighest = Color(0xFF333038),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            outline = Color(0xFF928E99)
        ),
        swatchLight = Color(0xFF6750A4),
        swatchDark = Color(0xFF1C1B1F)
    ),

    // 4 · Rose (warm pink/red)
    ThemeEntry(
        key = "rose",
        label = "Rose",
        light = lightColorScheme(
            primary = Color(0xFF984061), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFD9E2), onPrimaryContainer = Color(0xFF3E0021),
            secondary = Color(0xFF75565F), onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF7D5540), onTertiary = Color(0xFFFFFFFF),
            surface = Color(0xFFFFF8F6), onSurface = Color(0xFF221A18),
            surfaceVariant = Color(0xFFF2DDE1), onSurfaceVariant = Color(0xFF514347),
            surfaceContainer = Color(0xFFF5DDE0), surfaceContainerHighest = Color(0xFFF0D7DB),
            error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
            outline = Color(0xFF837377)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFFB0C8), onPrimary = Color(0xFF5E193A),
            primaryContainer = Color(0xFF7B2949), onPrimaryContainer = Color(0xFFFFD9E2),
            secondary = Color(0xFFE2BDC6), onSecondary = Color(0xFF422931),
            tertiary = Color(0xFFEEB99E), onTertiary = Color(0xFF482614),
            surface = Color(0xFF221A18), onSurface = Color(0xFFECDFDD),
            surfaceVariant = Color(0xFF514347), onSurfaceVariant = Color(0xFFD6C2C6),
            surfaceContainer = Color(0xFF2C2321), surfaceContainerHighest = Color(0xFF382D2B),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            outline = Color(0xFF9C8C90)
        ),
        swatchLight = Color(0xFF984061),
        swatchDark = Color(0xFF221A18)
    ),

    // 5 · Amber (warm gold)
    ThemeEntry(
        key = "amber",
        label = "Amber",
        light = lightColorScheme(
            primary = Color(0xFF815E00), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDE99), onPrimaryContainer = Color(0xFF281800),
            secondary = Color(0xFF6C5D40), onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF4A6654), onTertiary = Color(0xFFFFFFFF),
            surface = Color(0xFFFFF8F0), onSurface = Color(0xFF1E1B0F),
            surfaceVariant = Color(0xFFEDDFC7), onSurfaceVariant = Color(0xFF4D4637),
            surfaceContainer = Color(0xFFF1DFD0), surfaceContainerHighest = Color(0xFFEBD9CA),
            error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
            outline = Color(0xFF7F7665)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFF2BD40), onPrimary = Color(0xFF442D00),
            primaryContainer = Color(0xFF634500), onPrimaryContainer = Color(0xFFFFDE99),
            secondary = Color(0xFFD8C4A2), onSecondary = Color(0xFF3B3018),
            tertiary = Color(0xFFB1CDB8), onTertiary = Color(0xFF1C3828),
            surface = Color(0xFF1E1B0F), onSurface = Color(0xFFE8E0D0),
            surfaceVariant = Color(0xFF4D4637), onSurfaceVariant = Color(0xFFCFC2AE),
            surfaceContainer = Color(0xFF282418), surfaceContainerHighest = Color(0xFF342F21),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            outline = Color(0xFF978E7D)
        ),
        swatchLight = Color(0xFF815E00),
        swatchDark = Color(0xFF1E1B0F)
    ),

    // 6 · Teal (green-blue, fresh)
    ThemeEntry(
        key = "teal",
        label = "Teal",
        light = lightColorScheme(
            primary = Color(0xFF006A63), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF6FF3E8), onPrimaryContainer = Color(0xFF00201D),
            secondary = Color(0xFF4A635F), onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF456078), onTertiary = Color(0xFFFFFFFF),
            surface = Color(0xFFF7FAF8), onSurface = Color(0xFF161D1C),
            surfaceVariant = Color(0xFFDDE5E1), onSurfaceVariant = Color(0xFF414946),
            surfaceContainer = Color(0xFFD9E3DE), surfaceContainerHighest = Color(0xFFD4DED9),
            error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
            outline = Color(0xFF717976)
        ),
        dark = darkColorScheme(
            primary = Color(0xFF4DDAD0), onPrimary = Color(0xFF003733),
            primaryContainer = Color(0xFF00504A), onPrimaryContainer = Color(0xFF6FF3E8),
            secondary = Color(0xFFB0CCC7), onSecondary = Color(0xFF1B3430),
            tertiary = Color(0xFFC6D3EF), onTertiary = Color(0xFF153247),
            surface = Color(0xFF161D1C), onSurface = Color(0xFFDEE5E1),
            surfaceVariant = Color(0xFF414946), onSurfaceVariant = Color(0xFFC0C8C3),
            surfaceContainer = Color(0xFF202726), surfaceContainerHighest = Color(0xFF2A3230),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            outline = Color(0xFF89918E)
        ),
        swatchLight = Color(0xFF006A63),
        swatchDark = Color(0xFF161D1C)
    ),

    // 7 · Midnight (electric blue on near-black)
    ThemeEntry(
        key = "midnight",
        label = "Midnight",
        light = lightColorScheme(
            primary = Color(0xFF0055FF), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFDBE3FF), onPrimaryContainer = Color(0xFF001A51),
            secondary = Color(0xFF3E5874), onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF24608A), onTertiary = Color(0xFFFFFFFF),
            surface = Color(0xFFF8F9FF), onSurface = Color(0xFF0B0E14),
            surfaceVariant = Color(0xFFDFE3EB), onSurfaceVariant = Color(0xFF404850),
            surfaceContainer = Color(0xFFD9DDE5), surfaceContainerHighest = Color(0xFFD4D8E1),
            error = Color(0xFFDC2626), onError = Color(0xFFFFFFFF),
            outline = Color(0xFF707880)
        ),
        dark = darkColorScheme(
            primary = Color(0xFF4080FF), onPrimary = Color(0xFF001A51),
            primaryContainer = Color(0xFF0042CC), onPrimaryContainer = Color(0xFFDBE3FF),
            secondary = Color(0xFFB7C8DC), onSecondary = Color(0xFF1D344C),
            tertiary = Color(0xFF8CC8E8), onTertiary = Color(0xFF003149),
            surface = Color(0xFF0B0E14), onSurface = Color(0xFFE1E2E8),
            surfaceVariant = Color(0xFF404850), onSurfaceVariant = Color(0xFFC0C7CF),
            surfaceContainer = Color(0xFF151920), surfaceContainerHighest = Color(0xFF1F242C),
            error = Color(0xFFFF5449), onError = Color(0xFF5C1010),
            outline = Color(0xFF8A929A)
        ),
        swatchLight = Color(0xFF0055FF),
        swatchDark = Color(0xFF0B0E14)
    ),

    // 8 · Sunset (vivid orange-red)
    ThemeEntry(
        key = "sunset",
        label = "Sunset",
        light = lightColorScheme(
            primary = Color(0xFFFF4D00), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDBCD), onPrimaryContainer = Color(0xFF441100),
            secondary = Color(0xFF7A5649), onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF6B5E2E), onTertiary = Color(0xFFFFFFFF),
            surface = Color(0xFFFFF8F6), onSurface = Color(0xFF1C1110),
            surfaceVariant = Color(0xFFFADDE9), onSurfaceVariant = Color(0xFF534340),
            surfaceContainer = Color(0xFFF9D8D6), surfaceContainerHighest = Color(0xFFF6D1CE),
            error = Color(0xFFDC2626), onError = Color(0xFFFFFFFF),
            outline = Color(0xFF85736F)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFF7A40), onPrimary = Color(0xFF441100),
            primaryContainer = Color(0xFFCC3D00), onPrimaryContainer = Color(0xFFFFDBCD),
            secondary = Color(0xFFE6BDAE), onSecondary = Color(0xFF45291E),
            tertiary = Color(0xFFD8C68D), onTertiary = Color(0xFF3A3005),
            surface = Color(0xFF1C1110), onSurface = Color(0xFFECDFDC),
            surfaceVariant = Color(0xFF534340), onSurfaceVariant = Color(0xFFD6C2BE),
            surfaceContainer = Color(0xFF261D1B), surfaceContainerHighest = Color(0xFF322825),
            error = Color(0xFFFF5449), onError = Color(0xFF5C1010),
            outline = Color(0xFF9E8C88)
        ),
        swatchLight = Color(0xFFFF4D00),
        swatchDark = Color(0xFF1C1110)
    ),

    // 9 · Neon Lime (high-contrast lime on black)
    ThemeEntry(
        key = "neon",
        label = "Neon Lime",
        light = lightColorScheme(
            primary = Color(0xFF3C9000), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFB7F687), onPrimaryContainer = Color(0xFF0E2100),
            secondary = Color(0xFF586249), onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF386663), onTertiary = Color(0xFFFFFFFF),
            surface = Color(0xFFF9FAEF), onSurface = Color(0xFF141710),
            surfaceVariant = Color(0xFFE0E4D6), onSurfaceVariant = Color(0xFF44483E),
            surfaceContainer = Color(0xFFEBEEE3), surfaceContainerHighest = Color(0xFFE5E9DC),
            error = Color(0xFFDC2626), onError = Color(0xFFFFFFFF),
            outline = Color(0xFF74796E)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFA8E63A), onPrimary = Color(0xFF132400),
            primaryContainer = Color(0xFF287000), onPrimaryContainer = Color(0xFFB7F687),
            secondary = Color(0xFFBFC9AB), onSecondary = Color(0xFF29331D),
            tertiary = Color(0xFFA0D0CB), onTertiary = Color(0xFF003735),
            surface = Color(0xFF10120D), onSurface = Color(0xFFDCDFD3),
            surfaceVariant = Color(0xFF3A3E32), onSurfaceVariant = Color(0xFFB9BEAE),
            surfaceContainer = Color(0xFF1A1D17), surfaceContainerHighest = Color(0xFF252921),
            error = Color(0xFFFF5449), onError = Color(0xFF5C1010),
            outline = Color(0xFF8A9082)
        ),
        swatchLight = Color(0xFF3C9000),
        swatchDark = Color(0xFF10120D)
    ),

    // 10 · Crimson (bold red)
    ThemeEntry(
        key = "crimson",
        label = "Crimson",
        light = lightColorScheme(
            primary = Color(0xFFD50000), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDAD5), onPrimaryContainer = Color(0xFF410002),
            secondary = Color(0xFF775652), onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF705C40), onTertiary = Color(0xFFFFFFFF),
            surface = Color(0xFFFFF8F6), onSurface = Color(0xFF1C1110),
            surfaceVariant = Color(0xFFF5DDDA), onSurfaceVariant = Color(0xFF544340),
            surfaceContainer = Color(0xFFF2D6D2), surfaceContainerHighest = Color(0xFFEDCFCA),
            error = Color(0xFFDC2626), onError = Color(0xFFFFFFFF),
            outline = Color(0xFF86716E)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFF5449), onPrimary = Color(0xFF690005),
            primaryContainer = Color(0xFFB0000A), onPrimaryContainer = Color(0xFFFFDAD5),
            secondary = Color(0xFFE6BDB7), onSecondary = Color(0xFF442926),
            tertiary = Color(0xFFDCC29E), onTertiary = Color(0xFF3E2C10),
            surface = Color(0xFF181211), onSurface = Color(0xFFECDFDC),
            surfaceVariant = Color(0xFF544340), onSurfaceVariant = Color(0xFFD5C2BE),
            surfaceContainer = Color(0xFF221A18), surfaceContainerHighest = Color(0xFF2E2321),
            error = Color(0xFFFF5449), onError = Color(0xFF5C1010),
            outline = Color(0xFF9F8986)
        ),
        swatchLight = Color(0xFFD50000),
        swatchDark = Color(0xFF181211)
    ),

    // 11 · Electric Violet (saturated purple)
    ThemeEntry(
        key = "electric",
        label = "Electric Violet",
        light = lightColorScheme(
            primary = Color(0xFF7C13FC), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF31006D),
            secondary = Color(0xFF6B5778), onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF8A5350), onTertiary = Color(0xFFFFFFFF),
            surface = Color(0xFFFFF8FB), onSurface = Color(0xFF1A111A),
            surfaceVariant = Color(0xFFF0DFF5), onSurfaceVariant = Color(0xFF4E434E),
            surfaceContainer = Color(0xFFECDFF0), surfaceContainerHighest = Color(0xFFE7D7EA),
            error = Color(0xFFDC2626), onError = Color(0xFFFFFFFF),
            outline = Color(0xFF7F727F)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFB866FF), onPrimary = Color(0xFF3D0075),
            primaryContainer = Color(0xFF5E00DF), onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFD6BFE0), onSecondary = Color(0xFF3A2846),
            tertiary = Color(0xFFF0B2AE), onTertiary = Color(0xFF542623),
            surface = Color(0xFF140E16), onSurface = Color(0xFFE6DFE6),
            surfaceVariant = Color(0xFF4E434E), onSurfaceVariant = Color(0xFFCFC3CF),
            surfaceContainer = Color(0xFF1E1720), surfaceContainerHighest = Color(0xFF2A222E),
            error = Color(0xFFFF5449), onError = Color(0xFF5C1010),
            outline = Color(0xFF988A99)
        ),
        swatchLight = Color(0xFF7C13FC),
        swatchDark = Color(0xFF140E16)
    ),

    // 12 · Cobalt (intense royal blue)
    ThemeEntry(
        key = "cobalt",
        label = "Cobalt",
        light = lightColorScheme(
            primary = Color(0xFF003596), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD6E3FF), onPrimaryContainer = Color(0xFF001A51),
            secondary = Color(0xFF535F70), onSecondary = Color(0xFFFFFFFF),
            tertiary = Color(0xFF6B5778), onTertiary = Color(0xFFFFFFFF),
            surface = Color(0xFFFBF8FF), onSurface = Color(0xFF13191C),
            surfaceVariant = Color(0xFFDFE3EB), onSurfaceVariant = Color(0xFF42484E),
            surfaceContainer = Color(0xFFD9DDE5), surfaceContainerHighest = Color(0xFFD4D8E1),
            error = Color(0xFFDC2626), onError = Color(0xFFFFFFFF),
            outline = Color(0xFF727980)
        ),
        dark = darkColorScheme(
            primary = Color(0xFF6C9AFF), onPrimary = Color(0xFF002874),
            primaryContainer = Color(0xFF002F82), onPrimaryContainer = Color(0xFFD6E3FF),
            secondary = Color(0xFFBBC7DB), onSecondary = Color(0xFF2B3847),
            tertiary = Color(0xFFD5BFE0), onTertiary = Color(0xFF3D2B4B),
            surface = Color(0xFF0C1216), onSurface = Color(0xFFE1E2E8),
            surfaceVariant = Color(0xFF42484E), onSurfaceVariant = Color(0xFFC2C7CF),
            surfaceContainer = Color(0xFF161C22), surfaceContainerHighest = Color(0xFF20262E),
            error = Color(0xFFFF5449), onError = Color(0xFF5C1010),
            outline = Color(0xFF8A929A)
        ),
        swatchLight = Color(0xFF003596),
        swatchDark = Color(0xFF0C1216)
    )
)

fun findTheme(key: String): ThemeEntry = THEMES.firstOrNull { it.key == key } ?: THEMES[0]

/**
 * Returns a distinct color for category index N derived from the current theme's primary color.
 * Uses HSL hue rotation + lightness variation to produce a cohesive palette.
 */
@androidx.compose.runtime.Composable
fun categoryColor(index: Int): Color {
    val primary = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val hsl = rgbToHsl(primary.red, primary.green, primary.blue)
    val h = hsl[0]
    val s = hsl[1]
    val l = hsl[2]
    // Spread hue ±15° around primary, vary lightness for contrast
    val hueShift = (index.toFloat() / 9f * 30f) - 15f  // -15° to +15°
    val newHue = (h + hueShift + 360f) % 360f
    val newLightness = (l + (index % 3 - 1) * 0.12f).coerceIn(0.18f, 0.82f)
    val newSaturation = s.coerceIn(0.5f, 1f)
    val rgb = hslToRgb(newHue, newSaturation, newLightness)
    return Color(rgb[0], rgb[1], rgb[2])
}

/** RGB (0-1) → HSL [hue 0-360, sat 0-1, light 0-1] */
private fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    var h = 0f
    var s = 0f
    if (max != min) {
        val d = max - min
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> ((g - b) / d + if (g < b) 6f else 0f) / 6f
            g -> ((b - r) / d + 2f) / 6f
            else -> ((r - g) / d + 4f) / 6f
        }
        h *= 360f
    }
    return floatArrayOf(h, s, l)
}

/** HSL → RGB [r, g, b] all 0-1 */
private fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return floatArrayOf(r1 + m, g1 + m, b1 + m)
}
