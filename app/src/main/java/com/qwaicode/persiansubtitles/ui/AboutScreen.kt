package com.qwaicode.persiansubtitles.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qwaicode.persiansubtitles.BuildConfig
import com.qwaicode.persiansubtitles.R
import com.qwaicode.persiansubtitles.ui.components.AppIcons
import com.qwaicode.persiansubtitles.toPersianDigits

/** The brand gradient, taken from the launcher icon: electric blue into violet. */
private val BrandBlue = Color(0xFF0A5CF5)
private val BrandIndigo = Color(0xFF2B3BFF)
private val BrandViolet = Color(0xFF7A1FFF)
private val BrandCyan = Color(0xFF5AD1FF)

@Immutable
private data class Feature(
    val icon: ImageVector,
    val title: Int,
    val desc: Int,
    val accent: Color,
)

private val FEATURES = listOf(
    Feature(Icons.Filled.MenuBook, R.string.about_f_read_title, R.string.about_f_read_desc, Color(0xFF62A8FA)),
    Feature(Icons.Filled.AutoFixHigh, R.string.about_f_tone_title, R.string.about_f_tone_desc, Color(0xFFB794FF)),
    Feature(Icons.Filled.Language, R.string.about_f_lang_title, R.string.about_f_lang_desc, Color(0xFF7FD8CF)),
    Feature(Icons.Filled.Bolt, R.string.about_f_parallel_title, R.string.about_f_parallel_desc, Color(0xFFFFC857)),
    Feature(Icons.Filled.Refresh, R.string.about_f_resume_title, R.string.about_f_resume_desc, Color(0xFF7FE0A8)),
    Feature(AppIcons.FactCheck, R.string.about_f_review_title, R.string.about_f_review_desc, Color(0xFF8FC5FF)),
    Feature(Icons.Filled.Translate, R.string.about_f_glossary_title, R.string.about_f_glossary_desc, Color(0xFFFF9FC6)),
    Feature(Icons.Filled.Palette, R.string.about_f_style_title, R.string.about_f_style_desc, Color(0xFFFFB38A)),
    Feature(Icons.Filled.Edit, R.string.about_f_editor_title, R.string.about_f_editor_desc, Color(0xFF9EE7FF)),
    Feature(Icons.Filled.Sync, R.string.about_f_sync_title, R.string.about_f_sync_desc, Color(0xFFC6F37F)),
    Feature(Icons.Filled.Timer, R.string.about_f_eta_title, R.string.about_f_eta_desc, Color(0xFFFFD98A)),
    Feature(Icons.Filled.Block, R.string.about_f_copyright_title, R.string.about_f_copyright_desc, Color(0xFFFFB4AB)),
    Feature(Icons.Filled.Description, R.string.about_f_formats_title, R.string.about_f_formats_desc, Color(0xFFB6C4DE)),
)

/**
 * About: what the app is, what it can do and who made it.
 *
 * Rebuilt as a small product page instead of a stack of identical cards: a hero in
 * the icon's own gradient, three headline numbers, a feature list where every item
 * has its own icon tile, and a developer card with the profile link. Everything is still a
 * single full-width column, so nothing can overlap on a narrow phone.
 */
@Composable
fun AboutScreen(
    onOpenProfile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HeroCard() }
        item { HighlightsRow() }

        item {
            SectionTitle(stringResource(R.string.about_features_title), stringResource(R.string.about_features_subtitle))
        }
        item {
            AboutCard {
                FEATURES.forEachIndexed { index, feature ->
                    FeatureRow(feature)
                    if (index != FEATURES.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.about_developer_title), null) }
        item { DeveloperCard(onOpenProfile = onOpenProfile) }

        item {
            InfoCard(
                icon = Icons.Filled.Shield,
                accent = Color(0xFF7FE0A8),
                title = stringResource(R.string.about_privacy_title),
                text = stringResource(R.string.about_privacy_desc),
            )
        }
        item {
            InfoCard(
                icon = Icons.Filled.Gavel,
                accent = Color(0xFFB6C4DE),
                title = stringResource(R.string.about_license_title),
                text = stringResource(R.string.about_license_desc),
            )
        }

        item { Footer() }

        // A second, unmissable way back — for people who scrolled to the bottom
        // and do not want to reach for the top bar.
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.about_back))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(BrandBlue, BrandIndigo, BrandViolet)))
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        // A soft light in the corner, like the glow in the icon itself.
        Box(
            modifier = Modifier
                .size(180.dp)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(listOf(BrandCyan.copy(alpha = 0.35f), Color.Transparent)),
                    CircleShape,
                ),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(108.dp)
                    .shadow(elevation = 18.dp, shape = RoundedCornerShape(26.dp), clip = false),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.86f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                GlassChip(stringResource(R.string.about_version, BuildConfig.VERSION_NAME.toPersianDigits()))
                GlassChip(stringResource(R.string.about_chip_gemini))
                GlassChip(stringResource(R.string.about_chip_open_source))
            }
        }
    }
}

/** A translucent white chip that reads well on the gradient. */
@Composable
private fun GlassChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        // Wraps rather than being clipped when the chip is wider than the screen.
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun HighlightsRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Highlight(
            value = stringResource(R.string.about_stat_languages_value),
            caption = stringResource(R.string.about_stat_languages),
            accent = Color(0xFF7FD8CF),
            modifier = Modifier.weight(1f),
        )
        Highlight(
            value = stringResource(R.string.about_stat_tones_value),
            caption = stringResource(R.string.about_stat_tones),
            accent = Color(0xFFB794FF),
            modifier = Modifier.weight(1f),
        )
        Highlight(
            value = stringResource(R.string.about_stat_device_value),
            caption = stringResource(R.string.about_stat_device),
            accent = Color(0xFF7FE0A8),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Highlight(value: String, caption: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, accent.copy(alpha = 0.25f), MaterialTheme.shapes.medium)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = accent,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { content() }
    }
}

/** A tinted rounded tile holding an icon; the visual anchor of every row. */
@Composable
private fun IconTile(icon: ImageVector, accent: Color, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
private fun FeatureRow(feature: Feature) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(feature.icon, feature.accent)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(feature.title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(feature.desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeveloperCard(onOpenProfile: () -> Unit) {
    AboutCard {
        Column(modifier = Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The developer's own logo as the profile picture, cropped into a
                // circle with a thin ring in the brand gradient.
                Image(
                    painter = painterResource(R.drawable.developer_avatar),
                    contentDescription = stringResource(R.string.about_developer_avatar),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(listOf(BrandCyan, BrandBlue, BrandViolet)),
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.about_developer_name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.about_developer_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.about_open_profile))
            }
        }
    }
}

@Composable
private fun InfoCard(icon: ImageVector, accent: Color, title: String, text: String) {
    AboutCard {
        Row(modifier = Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
            IconTile(icon, accent)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Footer() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.about_footer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Filled.Favorite,
            contentDescription = null,
            tint = Color(0xFFFF6B8B),
            modifier = Modifier.size(14.dp),
        )
    }
}
