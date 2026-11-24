package com.havenheights.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Haven Heights palette: nude brown, burgundy, sage green. */
object HavenColors {
    // Nude browns
    val Cream = Color(0xFFF7EFE7)      // app background
    val Paper = Color(0xFFFFFAF5)      // card surface
    val Nude = Color(0xFFE6D3C2)       // chips, dividers, nav indicator
    val NudeDeep = Color(0xFFB89377)   // borders, secondary icons
    val Brown = Color(0xFF7A5740)      // secondary text
    val Cocoa = Color(0xFF3A2A22)      // primary text

    // Burgundy: primary actions, urgency, attention
    val Burgundy = Color(0xFF6B1E2E)
    val BurgundyDark = Color(0xFF4A1420)
    val BurgundyMist = Color(0xFFF0D9DC)

    // Sage green: calm, resolved, secondary actions
    val Sage = Color(0xFF9DB08C)
    val SageDeep = Color(0xFF55694B)
    val SageMist = Color(0xFFE1E8D9)
}

private val Colors = lightColorScheme(
    primary = HavenColors.Burgundy,
    onPrimary = HavenColors.Cream,
    primaryContainer = HavenColors.BurgundyMist,
    onPrimaryContainer = HavenColors.BurgundyDark,
    secondary = HavenColors.SageDeep,
    onSecondary = Color.White,
    secondaryContainer = HavenColors.SageMist,
    onSecondaryContainer = HavenColors.SageDeep,
    tertiary = HavenColors.Brown,
    onTertiary = Color.White,
    tertiaryContainer = HavenColors.Nude,
    onTertiaryContainer = HavenColors.Cocoa,
    background = HavenColors.Cream,
    onBackground = HavenColors.Cocoa,
    surface = HavenColors.Cream,
    onSurface = HavenColors.Cocoa,
    surfaceVariant = HavenColors.Nude,
    onSurfaceVariant = HavenColors.Brown,
    outline = HavenColors.NudeDeep,
    outlineVariant = HavenColors.Nude,
    error = Color(0xFF9B2335),
    onError = Color.White,
)

// Serif for headings gives the app a warm, printed-ledger feel; sans for reading and forms.
private val Heading = FontFamily.Serif
private val Typo = Typography(
    headlineLarge = TextStyle(fontFamily = Heading, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Heading, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Heading, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Heading, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun HavenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typo, content = content)
}
