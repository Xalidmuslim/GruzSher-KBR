package ru.kbr.cargoshare

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.unit.TextUnit

/**
 * App-controlled font-size preference, observed directly by every text node.
 * A Compose Dialog or bottom sheet may own another Android window and capture
 * a different LocalDensity, so relying ONLY on LocalDensity cannot guarantee
 * instant changes throughout the application. This explicitly scales our text
 * sizes in the current composition, including open sheets and dialogs.
 *
 * dp, icon sizes, map geometry and touch targets remain unchanged.
 */
internal val LocalAppFontScale = staticCompositionLocalOf { 1f }

private fun TextUnit.atAppScale(scale: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * scale

internal fun scaledAppTypography(scale: Float): Typography {
    val base = Typography()
    fun scaleStyle(style: TextStyle): TextStyle = style.copy(
        fontSize = style.fontSize.atAppScale(scale),
        lineHeight = style.lineHeight.atAppScale(scale),
        letterSpacing = style.letterSpacing.atAppScale(scale)
    )
    return Typography(
        displayLarge = scaleStyle(base.displayLarge),
        displayMedium = scaleStyle(base.displayMedium),
        displaySmall = scaleStyle(base.displaySmall),
        headlineLarge = scaleStyle(base.headlineLarge),
        headlineMedium = scaleStyle(base.headlineMedium),
        headlineSmall = scaleStyle(base.headlineSmall),
        titleLarge = scaleStyle(base.titleLarge),
        titleMedium = scaleStyle(base.titleMedium),
        titleSmall = scaleStyle(base.titleSmall),
        bodyLarge = scaleStyle(base.bodyLarge),
        bodyMedium = scaleStyle(base.bodyMedium),
        bodySmall = scaleStyle(base.bodySmall),
        labelLarge = scaleStyle(base.labelLarge),
        labelMedium = scaleStyle(base.labelMedium),
        labelSmall = scaleStyle(base.labelSmall)
    )
}

/**
 * Same public signature as Material3 Text. The package-level function is used
 * by our native screens; it explicitly scales sp sizes and line heights.
 * Material3 typography styles are scaled once at the application root.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    val scale = LocalAppFontScale.current
    androidx.compose.material3.Text(
        text = text, modifier = modifier, color = color,
        fontSize = fontSize.atAppScale(scale), fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing.atAppScale(scale),
        textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight.atAppScale(scale), overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        onTextLayout = onTextLayout, style = style
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val scale = LocalAppFontScale.current
    androidx.compose.material3.Text(
        text = text, modifier = modifier, color = color,
        fontSize = fontSize.atAppScale(scale), fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing.atAppScale(scale),
        textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight.atAppScale(scale), overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        inlineContent = inlineContent, onTextLayout = onTextLayout, style = style
    )
}
