package com.yishenghuang.heartext.ui.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yishenghuang.heartext.R
import com.yishenghuang.heartext.data.PageTurnEffect
import com.yishenghuang.heartext.data.ReaderFontFamily
import com.yishenghuang.heartext.data.ReaderSettings
import com.yishenghuang.heartext.data.ReaderThemeMode
import com.yishenghuang.heartext.data.ReaderTypefaces
import com.yishenghuang.heartext.ui.theme.HearPurple
import com.yishenghuang.heartext.ui.theme.HearReaderDark
import com.yishenghuang.heartext.ui.theme.HearReaderLight
import com.yishenghuang.heartext.ui.theme.HearReaderSepia
import java.io.File

private const val PREVIEW_TITLE = "Chapter 2 · The Quiet Library"
private const val PREVIEW_BODY =
    "Reading should feel effortless. Change font, size, and spacing — this preview updates as you adjust the controls."
private const val PREVIEW_CJK = "天地玄黄，宇宙洪荒。宋体楷体仿宋黑体，一眼可辨。"
private const val PREVIEW_SECOND =
    "A second paragraph shows how paragraph spacing looks with your current settings."

private data class SettingsPalette(
    val title: Color,
    val label: Color,
    val muted: Color,
    val chipBg: Color,
    val chipBorder: Color,
    val chipText: Color,
    val chipSelectedBg: Color,
    val chipSelectedBorder: Color,
    val chipSelectedText: Color,
    val previewBorder: Color,
    val link: Color
)

private fun settingsPalette(mode: ReaderThemeMode): SettingsPalette = when (mode) {
    ReaderThemeMode.DARK -> SettingsPalette(
        title = Color(0xFFF2F2F7),
        label = Color(0xFFE4E4EA),
        muted = Color(0xFFA8A8B8),
        chipBg = Color(0xFF3A3944),
        chipBorder = Color(0xFF55545F),
        chipText = Color(0xFFF2F2F7),
        chipSelectedBg = HearPurple.copy(alpha = 0.32f),
        chipSelectedBorder = HearPurple,
        chipSelectedText = Color.White,
        previewBorder = Color(0xFF55545F),
        link = Color(0xFFB8B2FF)
    )
    ReaderThemeMode.SEPIA -> SettingsPalette(
        title = Color(0xFF3D2F1F),
        label = Color(0xFF4A3728),
        muted = Color(0xFF8A735A),
        chipBg = Color(0xFFEDE3D0),
        chipBorder = Color(0xFFD9C9AE),
        chipText = Color(0xFF3D2F1F),
        chipSelectedBg = HearPurple.copy(alpha = 0.12f),
        chipSelectedBorder = HearPurple,
        chipSelectedText = HearPurple,
        previewBorder = Color(0xFFD9C9AE),
        link = HearPurple
    )
    ReaderThemeMode.LIGHT -> SettingsPalette(
        title = Color(0xFF1A1A2E),
        label = Color(0xFF1A1A2E),
        muted = Color(0xFF6B6B80),
        chipBg = Color(0xFFF2F2F7),
        chipBorder = Color(0xFFE5E5EA),
        chipText = Color(0xFF1A1A2E),
        chipSelectedBg = HearPurple.copy(alpha = 0.08f),
        chipSelectedBorder = HearPurple,
        chipSelectedText = HearPurple,
        previewBorder = Color(0xFFE5E5EA),
        link = HearPurple
    )
}

@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    listeningEnabled: Boolean = true,
    curlAvailable: Boolean = true,
    onFontScale: (Float) -> Unit,
    onTheme: (ReaderThemeMode) -> Unit,
    onBrightness: (Float) -> Unit,
    onPageTurn: (PageTurnEffect) -> Unit,
    onFontFamily: (ReaderFontFamily) -> Unit,
    onLetterSpacing: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onParagraphSpacing: (Float) -> Unit,
    onChineseMode: (String) -> Unit = {},
    showChineseScript: Boolean = true,
    onVolumeKeyPageTurn: (Boolean) -> Unit = {},
    onImportFont: (Uri) -> Unit = {}
) {
    // Local slider drafts — preview updates live; reader commits on finger up.
    var draftScale by remember { mutableFloatStateOf(settings.fontScale) }
    var draftLetter by remember { mutableFloatStateOf(settings.letterSpacing) }
    var draftLine by remember { mutableFloatStateOf(settings.lineHeight) }
    var draftParagraph by remember { mutableFloatStateOf(settings.paragraphSpacing) }
    var draftBrightness by remember { mutableFloatStateOf(settings.brightness) }
    var draftFamily by remember { mutableStateOf(settings.fontFamily) }

    LaunchedEffect(settings.fontScale) { draftScale = settings.fontScale }
    LaunchedEffect(settings.letterSpacing) { draftLetter = settings.letterSpacing }
    LaunchedEffect(settings.lineHeight) { draftLine = settings.lineHeight }
    LaunchedEffect(settings.paragraphSpacing) { draftParagraph = settings.paragraphSpacing }
    LaunchedEffect(settings.brightness) { draftBrightness = settings.brightness }
    LaunchedEffect(settings.fontFamily) { draftFamily = settings.fontFamily }

    val fontLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportFont(uri)
    }

    val palette = remember(settings.themeMode) { settingsPalette(settings.themeMode) }

    val previewFamily = remember(draftFamily, settings.customFontPath) {
        FontFamily(ReaderTypefaces.typeface(draftFamily, settings.customFontPath))
    }
    val customComposeFamily = remember(settings.customFontPath) {
        val path = settings.customFontPath
        if (path != null && File(path).exists()) {
            runCatching {
                FontFamily(android.graphics.Typeface.createFromFile(path))
            }.getOrDefault(FontFamily.Default)
        } else {
            FontFamily.Default
        }
    }
    val sansFamily = remember {
        FontFamily(ReaderTypefaces.typeface(ReaderFontFamily.SANS))
    }
    val serifFamily = remember {
        FontFamily(ReaderTypefaces.typeface(ReaderFontFamily.SERIF))
    }
    val monoFamily = remember {
        FontFamily(ReaderTypefaces.typeface(ReaderFontFamily.MONO))
    }
    val heitiFamily = remember {
        FontFamily(ReaderTypefaces.typeface(ReaderFontFamily.HEITI))
    }
    val songFamily = remember {
        FontFamily(ReaderTypefaces.typeface(ReaderFontFamily.SONG))
    }
    val kaitiFamily = remember {
        FontFamily(ReaderTypefaces.typeface(ReaderFontFamily.KAITI))
    }
    val fangsongFamily = remember {
        FontFamily(ReaderTypefaces.typeface(ReaderFontFamily.FANGSONG))
    }

    fun selectFamily(family: ReaderFontFamily) {
        draftFamily = family
        onFontFamily(family)
    }

    val (previewBg, previewFg) = when (settings.themeMode) {
        ReaderThemeMode.LIGHT -> HearReaderLight to Color(0xFF2A2A35)
        ReaderThemeMode.SEPIA -> HearReaderSepia to Color(0xFF4A3728)
        ReaderThemeMode.DARK -> HearReaderDark to Color(0xFFCCCCCC)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 40.dp)
    ) {
        Text(
            stringResource(R.string.reader_settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = palette.title
        )
        Spacer(Modifier.height(16.dp))

        TypographyPreview(
            bg = previewBg,
            fg = previewFg,
            border = palette.previewBorder,
            brightness = draftBrightness,
            fontFamily = previewFamily,
            fontScale = draftScale,
            letterSpacing = draftLetter,
            lineHeight = draftLine,
            paragraphSpacing = draftParagraph
        )

        Spacer(Modifier.height(16.dp))

        if (showChineseScript) {
            SectionTitle(stringResource(R.string.reader_script_title), palette.label)
            Spacer(Modifier.height(8.dp))
            ChineseScriptChips(
                mode = settings.chineseMode,
                palette = palette,
                onSelect = onChineseMode
            )
            Spacer(Modifier.height(20.dp))
        }

        SectionTitle(stringResource(R.string.reader_page_turn), palette.label)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TurnChip(
                stringResource(R.string.reader_turn_slide),
                PageTurnEffect.SLIDE,
                settings.pageTurnEffect,
                palette,
                onPageTurn
            )
            TurnChip(
                stringResource(R.string.reader_turn_fade),
                PageTurnEffect.FADE,
                settings.pageTurnEffect,
                palette,
                onPageTurn
            )
            if (curlAvailable) {
                TurnChip(
                    stringResource(R.string.reader_turn_curl),
                    PageTurnEffect.CURL,
                    settings.pageTurnEffect,
                    palette,
                    onPageTurn
                )
            }
        }
        if (settings.pageTurnEffect == PageTurnEffect.CURL) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.reader_curl_hint),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }

        Spacer(Modifier.height(16.dp))
        VolumeKeyRow(
            enabled = settings.volumeKeyPageTurn,
            palette = palette,
            onToggle = onVolumeKeyPageTurn
        )

        Spacer(Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.reader_font), palette.label)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FontChip(
                stringResource(R.string.reader_font_publisher),
                ReaderFontFamily.PUBLISHER,
                draftFamily,
                FontFamily.Default,
                palette,
                ::selectFamily
            )
            FontChip(
                stringResource(R.string.reader_font_sans),
                ReaderFontFamily.SANS,
                draftFamily,
                sansFamily,
                palette,
                ::selectFamily
            )
            FontChip(
                stringResource(R.string.reader_font_serif),
                ReaderFontFamily.SERIF,
                draftFamily,
                serifFamily,
                palette,
                ::selectFamily
            )
            FontChip(
                stringResource(R.string.reader_font_mono),
                ReaderFontFamily.MONO,
                draftFamily,
                monoFamily,
                palette,
                ::selectFamily
            )
            FontChip("黑体", ReaderFontFamily.HEITI, draftFamily, heitiFamily, palette, ::selectFamily)
            FontChip("宋体", ReaderFontFamily.SONG, draftFamily, songFamily, palette, ::selectFamily)
            FontChip("楷体", ReaderFontFamily.KAITI, draftFamily, kaitiFamily, palette, ::selectFamily)
            FontChip("仿宋", ReaderFontFamily.FANGSONG, draftFamily, fangsongFamily, palette, ::selectFamily)
            val hasCustom = !settings.customFontPath.isNullOrBlank() &&
                File(settings.customFontPath!!).exists()
            val customOn = draftFamily == ReaderFontFamily.CUSTOM
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        if (customOn) palette.chipSelectedBorder else palette.chipBorder,
                        RoundedCornerShape(12.dp)
                    )
                    .background(if (customOn) palette.chipSelectedBg else palette.chipBg)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (hasCustom) {
                            selectFamily(ReaderFontFamily.CUSTOM)
                        } else {
                            fontLauncher.launch(
                                arrayOf(
                                    "font/ttf",
                                    "font/otf",
                                    "application/x-font-ttf",
                                    "application/x-font-otf",
                                    "application/octet-stream",
                                    "*/*"
                                )
                            )
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    if (hasCustom) stringResource(R.string.reader_font_custom)
                    else stringResource(R.string.reader_font_import),
                    fontFamily = if (hasCustom) customComposeFamily else FontFamily.Default,
                    color = if (customOn) palette.chipSelectedText else palette.chipText,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        if (!settings.customFontPath.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.reader_font_replace),
                style = MaterialTheme.typography.labelMedium,
                color = palette.link,
                modifier = Modifier.clickable {
                    fontLauncher.launch(
                        arrayOf("font/ttf", "font/otf", "application/octet-stream", "*/*")
                    )
                }
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.reader_size), color = palette.label)
        Slider(
            value = draftScale,
            onValueChange = { draftScale = it },
            onValueChangeFinished = { onFontScale(draftScale) },
            valueRange = 0.8f..2f,
            colors = settingsSliderColors()
        )
        Text(
            "%.0f%%".format(draftScale * 100),
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.reader_letter_spacing), color = palette.label)
        Slider(
            value = draftLetter,
            onValueChange = { draftLetter = it },
            onValueChangeFinished = { onLetterSpacing(draftLetter) },
            valueRange = -0.05f..0.2f,
            colors = settingsSliderColors()
        )
        Text(
            "%.2f em".format(draftLetter),
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.reader_line_height), color = palette.label)
        Slider(
            value = draftLine,
            onValueChange = { draftLine = it },
            onValueChangeFinished = { onLineHeight(draftLine) },
            valueRange = 1.0f..2.2f,
            colors = settingsSliderColors()
        )
        Text(
            "%.2f×".format(draftLine),
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.reader_paragraph_spacing), color = palette.label)
        Slider(
            value = draftParagraph,
            onValueChange = { draftParagraph = it },
            onValueChangeFinished = { onParagraphSpacing(draftParagraph) },
            valueRange = 0f..2f,
            colors = settingsSliderColors()
        )
        Text(
            "%.2f".format(draftParagraph),
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )

        Spacer(Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.reader_theme), palette.label)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ThemeOption(
                stringResource(R.string.reader_theme_light),
                settings.themeMode == ReaderThemeMode.LIGHT,
                HearReaderLight,
                palette
            ) {
                onTheme(ReaderThemeMode.LIGHT)
            }
            ThemeOption(
                stringResource(R.string.reader_theme_sepia),
                settings.themeMode == ReaderThemeMode.SEPIA,
                HearReaderSepia,
                palette
            ) {
                onTheme(ReaderThemeMode.SEPIA)
            }
            ThemeOption(
                stringResource(R.string.reader_theme_dark),
                settings.themeMode == ReaderThemeMode.DARK,
                HearReaderDark,
                palette
            ) {
                onTheme(ReaderThemeMode.DARK)
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.reader_brightness), color = palette.label)
        Slider(
            value = draftBrightness,
            onValueChange = { draftBrightness = it },
            onValueChangeFinished = { onBrightness(draftBrightness) },
            valueRange = 0.3f..1f,
            colors = settingsSliderColors()
        )

        Spacer(Modifier.height(20.dp))
        Text(
            if (listeningEnabled) {
                stringResource(R.string.reader_voice_hint)
            } else {
                stringResource(R.string.reader_pdf_no_listen)
            },
            color = palette.muted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun settingsSliderColors() = SliderDefaults.colors(
    thumbColor = HearPurple,
    activeTrackColor = HearPurple,
    inactiveTrackColor = HearPurple.copy(alpha = 0.25f)
)

@Composable
private fun TypographyPreview(
    bg: Color,
    fg: Color,
    border: Color,
    brightness: Float,
    fontFamily: FontFamily,
    fontScale: Float,
    letterSpacing: Float,
    lineHeight: Float,
    paragraphSpacing: Float
) {
    val baseSize = 16.sp * fontScale
    val bodyStyle = TextStyle(
        color = fg,
        fontSize = baseSize,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing.em,
        lineHeight = (baseSize.value * lineHeight).sp
    )
    val titleStyle = bodyStyle.copy(
        fontSize = (baseSize.value * 1.12f).sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = (baseSize.value * 1.12f * lineHeight).sp
    )
    val paraGap = (8f + paragraphSpacing * 12f).dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .background(bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = PREVIEW_TITLE,
                style = titleStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(paraGap))
            Text(text = PREVIEW_BODY, style = bodyStyle)
            Spacer(Modifier.height((paraGap.value * 0.65f).dp))
            Text(text = PREVIEW_CJK, style = bodyStyle)
            Spacer(Modifier.height(paraGap))
            Text(text = PREVIEW_SECOND, style = bodyStyle)
        }
        // Match reader brightness dimming.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 0.7f)))
        )
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
}

@Composable
private fun VolumeKeyRow(
    enabled: Boolean,
    palette: SettingsPalette,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.chipBg)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onToggle(!enabled) }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.reader_volume_keys),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = palette.label
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.reader_volume_keys_sub),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = HearPurple,
                checkedThumbColor = Color.White
            )
        )
    }
}

@Composable
private fun ChineseScriptChips(
    mode: String,
    palette: SettingsPalette,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        "original" to stringResource(R.string.reader_script_original),
        "simplified" to stringResource(R.string.reader_script_simplified),
        "traditional" to stringResource(R.string.reader_script_traditional)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.chipBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (key, label) ->
            val selected = mode == key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) HearPurple else Color.Transparent)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { onSelect(key) }
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (selected) Color.White else palette.chipText,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TurnChip(
    label: String,
    effect: PageTurnEffect,
    selected: PageTurnEffect,
    palette: SettingsPalette,
    onSelect: (PageTurnEffect) -> Unit
) {
    val isOn = selected == effect
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isOn) HearPurple else palette.chipBg)
            .clickable { onSelect(effect) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = if (isOn) Color.White else palette.chipText,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FontChip(
    label: String,
    family: ReaderFontFamily,
    selected: ReaderFontFamily,
    composeFamily: FontFamily,
    palette: SettingsPalette,
    onSelect: (ReaderFontFamily) -> Unit
) {
    val isOn = selected == family
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isOn) palette.chipSelectedBorder else palette.chipBorder,
                RoundedCornerShape(12.dp)
            )
            .background(if (isOn) palette.chipSelectedBg else palette.chipBg)
            .clickable { onSelect(family) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            fontFamily = composeFamily,
            color = if (isOn) palette.chipSelectedText else palette.chipText,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    color: Color,
    palette: SettingsPalette,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) HearPurple else palette.chipBorder,
                    CircleShape
                )
                .clickable(onClick = onClick)
        )
        Text(
            label,
            color = if (selected) palette.link else palette.muted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
