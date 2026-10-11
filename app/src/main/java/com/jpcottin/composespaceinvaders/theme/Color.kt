package com.jpcottin.composespaceinvaders.theme

import androidx.compose.ui.graphics.Color

// The game's palette: deep space, a cyan ship, pale lasers, amber bombs,
// three invader tiers.
val SpaceBlack = Color(0xFF080A17)
val SpaceSurface = Color(0xFF12162B)
val SpaceSurfaceVariant = Color(0xFF1F2544)
val ShipCyan = Color(0xFF4DB8F2)
val ShipCyanLight = Color(0xFFCCF2FF)
val ShipHull = Color(0xFF3A4A66)
val LaserCore = Color(0xFFFFFFCC)
val LaserGlow = Color(0xFF66D9FF)
val BombCore = Color(0xFFFFBF59)
val BombGlow = Color(0xFFFF5926)
val TitleGold = Color(0xFFFFCC4D)
val AutoGreen = Color(0xFF40FF61)
val OnSpaceWhite = Color(0xFFE8ECF8)
val OnSpaceMuted = Color(0xFF8A93B5)
val LaneLine = Color(0xFF66A6F2)
val AlertRed = Color(0xFFFF524D)
val SaucerRed = Color(0xFFFF6659)
val BossMagenta = Color(0xFFFF4D8C)
val StarFar = Color(0xFF8C99C7)
val StarNear = Color(0xFFD9E0FF)

/** Invader colours by tier: squid, crab, octopus (the debris takes the tier's colour). */
val TierColors = listOf(
    Color(0xFFC773FF),   // squid: purple
    Color(0xFF4DF28C),   // crab: green
    Color(0xFFFFB333),   // octopus: amber
)

/** Bonus colours: shield, rapid fire, triple shot. */
val PowerUpColors = listOf(
    Color(0xFF4D8CFF),
    Color(0xFFFFD91A),
    Color(0xFF40FF66),
)

/** Podium colours: gold, silver, bronze. */
val PodiumColors = listOf(Color(0xFFFFD933), Color(0xFFC7D1EB), Color(0xFFB87838))
