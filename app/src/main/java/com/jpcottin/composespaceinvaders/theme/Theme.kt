package com.jpcottin.composespaceinvaders.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SpaceInvadersColorScheme = darkColorScheme(
    primary = ShipCyan,
    onPrimary = SpaceBlack,
    primaryContainer = SpaceSurfaceVariant,
    onPrimaryContainer = ShipCyanLight,
    secondary = TitleGold,
    onSecondary = SpaceBlack,
    tertiary = AutoGreen,
    onTertiary = SpaceBlack,
    background = SpaceBlack,
    onBackground = OnSpaceWhite,
    surface = SpaceSurface,
    onSurface = OnSpaceWhite,
    surfaceVariant = SpaceSurfaceVariant,
    onSurfaceVariant = OnSpaceMuted,
    outline = LaneLine,
    error = AlertRed,
)

/** The game is dark on every device, whatever the system setting. */
@Composable
fun SpaceInvadersTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpaceInvadersColorScheme,
        typography = SpaceInvadersTypography,
        content = content,
    )
}
