package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
      primary = VanGoghStarGold,
      onPrimary = VanGoghDarkSpace,
      secondary = VanGoghAccentCyan,
      onSecondary = VanGoghDarkSpace,
      tertiary = VanGoghSwirlBlue,
      background = VanGoghDarkSpace,
      onBackground = VanGoghLightCloud,
      surface = VanGoghNightBlue,
      onSurface = VanGoghLightCloud,
      surfaceVariant = Color(0xFF1E3A6E),
      onSurfaceVariant = Color(0xFFE2E8F0)
  )

@Composable
fun MyApplicationTheme(
  // Always use the dark Starry Night theme for this app's style
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
