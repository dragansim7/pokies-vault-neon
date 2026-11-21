package com.aistudio.pokiesvault.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.aistudio.pokiesvault.data.model.VaultThemeId

@Immutable
data class VaultPalette(
    val id: VaultThemeId,
    val bg: Color,
    val panel: Color,
    val panelElevated: Color,
    val fg: Color,
    val muted: Color,
    val faint: Color,
    val accentA: Color,
    val accentB: Color,
    val accentC: Color,
    val ink: Color,
    val border: Color,
    val live: Color = Color(0xFF3DDC97),
    val slow: Color = Color(0xFFE2B657),
    val dead: Color = Color(0xFFFF6C82)
) {
    val brandGradient: Brush
        get() = Brush.linearGradient(listOf(accentA, accentB, accentC))

    fun toColorScheme(): ColorScheme = darkColorScheme(
        primary = accentA,
        onPrimary = ink,
        primaryContainer = accentA.copy(alpha = 0.16f),
        onPrimaryContainer = accentA,
        secondary = accentB,
        onSecondary = fg,
        secondaryContainer = accentB.copy(alpha = 0.18f),
        onSecondaryContainer = fg,
        tertiary = accentC,
        onTertiary = ink,
        tertiaryContainer = accentC.copy(alpha = 0.16f),
        onTertiaryContainer = accentC,
        background = bg,
        onBackground = fg,
        surface = panel,
        onSurface = fg,
        surfaceVariant = panelElevated,
        onSurfaceVariant = muted,
        surfaceContainerLowest = bg,
        surfaceContainerLow = panel,
        surfaceContainer = panel,
        surfaceContainerHigh = panelElevated,
        surfaceContainerHighest = panelElevated.copy(alpha = 0.95f),
        outline = border,
        outlineVariant = border.copy(alpha = 0.55f),
        error = dead,
        onError = ink,
        errorContainer = dead.copy(alpha = 0.16f),
        onErrorContainer = dead
    )
}

object VaultThemes {
    val Vault = VaultPalette(
        id = VaultThemeId.VAULT,
        bg = Color(0xFF050507),
        panel = Color(0xFF0C0C0F),
        panelElevated = Color(0xFF15151B),
        fg = Color(0xFFF5F5F7),
        muted = Color(0xFF9696A4),
        faint = Color(0xFF6B6B78),
        accentA = Color(0xFF00E5FF),
        accentB = Color(0xFF7B5CFF),
        accentC = Color(0xFFFF2FB0),
        ink = Color(0xFF070708),
        border = Color(0x26F5F5F7)
    )

    val Emerald = VaultPalette(
        id = VaultThemeId.EMERALD,
        bg = Color(0xFF06110C),
        panel = Color(0xFF0C1A14),
        panelElevated = Color(0xFF13261E),
        fg = Color(0xFFF3F7F4),
        muted = Color(0xFF8EA398),
        faint = Color(0xFF6B7F73),
        accentA = Color(0xFF3DFFC5),
        accentB = Color(0xFF1FA87A),
        accentC = Color(0xFFD6B25E),
        ink = Color(0xFF04110C),
        border = Color(0x26A8E8CE)
    )

    val Arctic = VaultPalette(
        id = VaultThemeId.ARCTIC,
        bg = Color(0xFF070B12),
        panel = Color(0xFF101722),
        panelElevated = Color(0xFF172235),
        fg = Color(0xFFF4F7FB),
        muted = Color(0xFF93A0B5),
        faint = Color(0xFF6C7A90),
        accentA = Color(0xFF8FD6FF),
        accentB = Color(0xFF6AA6FF),
        accentC = Color(0xFFD7E4F5),
        ink = Color(0xFF05080E),
        border = Color(0x26C4DCFF)
    )

    val Ember = VaultPalette(
        id = VaultThemeId.EMBER,
        bg = Color(0xFF100806),
        panel = Color(0xFF1A100C),
        panelElevated = Color(0xFF261812),
        fg = Color(0xFFF8F3EF),
        muted = Color(0xFFB5A197),
        faint = Color(0xFF88756A),
        accentA = Color(0xFFFFB15A),
        accentB = Color(0xFFFF6A3D),
        accentC = Color(0xFFFF4D6A),
        ink = Color(0xFF0C0604),
        border = Color(0x26FFD4B8)
    )

    val Royal = VaultPalette(
        id = VaultThemeId.ROYAL,
        bg = Color(0xFF0A0812),
        panel = Color(0xFF141022),
        panelElevated = Color(0xFF1D1732),
        fg = Color(0xFFF6F4FB),
        muted = Color(0xFFA39BB8),
        faint = Color(0xFF7B738F),
        accentA = Color(0xFFE6C36A),
        accentB = Color(0xFF9B6BFF),
        accentC = Color(0xFFF0A3FF),
        ink = Color(0xFF07060E),
        border = Color(0x26E4D6FF)
    )

    val all: List<VaultPalette> = listOf(Vault, Emerald, Arctic, Ember, Royal)

    fun paletteFor(id: VaultThemeId): VaultPalette = when (id) {
        VaultThemeId.VAULT -> Vault
        VaultThemeId.EMERALD -> Emerald
        VaultThemeId.ARCTIC -> Arctic
        VaultThemeId.EMBER -> Ember
        VaultThemeId.ROYAL -> Royal
    }
}

val LocalVaultPalette = staticCompositionLocalOf { VaultThemes.Vault }
