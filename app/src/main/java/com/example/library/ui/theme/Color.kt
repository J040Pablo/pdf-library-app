package com.example.library.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// LIGHT THEME — Purple/Indigo identity (Figma source of truth)
// =============================================================================

// Primary
val PrimaryLight = Color(0xFF6259A6)          // Notification button & brand accent
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE8DEF8)
val OnPrimaryContainerLight = Color(0xFF1E1047)

// Secondary
val SecondaryLight = Color(0xFF625B71)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFE8DEF8)
val OnSecondaryContainerLight = Color(0xFF1E192B)

// Tertiary
val TertiaryLight = Color(0xFF7E5260)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFD8E4)
val OnTertiaryContainerLight = Color(0xFF31111D)

// Background & Surface
val BackgroundLight = Color(0xFFFCFCFC)
val OnBackgroundLight = Color(0xFF2D2D2D)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF2D2D2D)
val SurfaceVariantLight = Color(0xFFEEEAF4)
val OnSurfaceVariantLight = Color(0xFF49454F)

// Outline
val OutlineLight = Color(0xFF7F7589)
val OutlineVariantLight = Color(0xFFCBC4D0)

// Error
val ErrorLight = Color(0xFFB3261E)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFF9DEDC)
val OnErrorContainerLight = Color(0xFF410E0B)

// =============================================================================
// DARK THEME — Matching purple/lilac identity
// =============================================================================

// Primary
val PrimaryDark = Color(0xFFB69CFF)           // Vibrant lilac — brand accent in dark
val OnPrimaryDark = Color(0xFF2B1D6B)
val PrimaryContainerDark = Color(0xFF43385F)
val OnPrimaryContainerDark = Color(0xFFE8DEF8)

// Secondary
val SecondaryDark = Color(0xFFCBC2DB)
val OnSecondaryDark = Color(0xFF332D41)
val SecondaryContainerDark = Color(0xFF302848)
val OnSecondaryContainerDark = Color(0xFFE8DEF8)

// Tertiary
val TertiaryDark = Color(0xFFEFB8C8)
val OnTertiaryDark = Color(0xFF4A2532)
val TertiaryContainerDark = Color(0xFF633B48)
val OnTertiaryContainerDark = Color(0xFFFFD8E4)

// Background & Surface
val BackgroundDark = Color(0xFF1C1B1F)
val OnBackgroundDark = Color(0xFFE6E1E5)
val SurfaceDark = Color(0xFF1C1B1F)
val OnSurfaceDark = Color(0xFFE6E1E5)
val SurfaceVariantDark = Color(0xFF2D2B35)
val OnSurfaceVariantDark = Color(0xFFCBC4D0)

// Outline
val OutlineDark = Color(0xFF958E9F)
val OutlineVariantDark = Color(0xFF49454F)

// Error
val ErrorDark = Color(0xFFF2B8B5)
val OnErrorDark = Color(0xFF601410)
val ErrorContainerDark = Color(0xFF8C1D18)
val OnErrorContainerDark = Color(0xFFF9DEDC)

// =============================================================================
// DESIGN TOKENS — UI-specific, not part of MaterialTheme color slots
// =============================================================================

/** Wavy progress bar — active wave color, theme-adaptive.
 *  Light: original Figma purple. Dark: lilac primary.
 *  Resolved in composable via MaterialTheme.colorScheme.primary.
 */
val WavyActiveLight = Color(0xFF6B5DAA)
val WavyActiveDark = Color(0xFFB69CFF)

/** Wavy progress bar — inactive wave color, theme-adaptive. */
val WavyInactiveLight = Color(0xFFE0D9F5)
val WavyInactiveDark = Color(0xFF43385F)

/** Book cover placeholder background.
 *  Light: soft lilac. Dark: resolved via MaterialTheme.colorScheme.secondaryContainer.
 */
val CoverBackgroundLight = Color(0xFFECE6F6)

/** Card background (home / search).
 *  Light: near-white. Dark: resolved via MaterialTheme.colorScheme.surfaceVariant.
 */
val CardBackgroundLight = Color(0xFFF5F5F5)
