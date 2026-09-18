import android.os.Environment
package com.nkiridown.app.ui.screens.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nkiridown.app.AppPreferences
import com.nkiridown.app.EpisodeItem
import com.nkiridown.app.ManagedDownloadStatus
import com.nkiridown.app.ManagedDownloads
import com.nkiridown.app.SearchItem
import com.nkiridown.app.SourceItem
import com.nkiridown.app.UiState
import com.nkiridown.app.ui.components.*
import com.nkiridown.app.ui.theme.*

@Composable
fun TitleDetailsScreen(
    state: UiState,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onSeason: (Int) -> Unit,
    onPlay: (SourceItem) -> Unit,
    onDownload: (SourceItem) -> Unit,
    onDownloadSeason: (Int) -> Unit,
    onOpenTitle: (SearchItem) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val title = state.title ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(440.dp)) {
                AsyncImage(
                    model = title.poster,
                    contentDescription = title.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.45f),
                                NkiriBackground
                            )
                        )
                    )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(10.dp).align(Alignment.TopStart)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NkiriText
                    )
                }
                Column(
                    Modifier.align(Alignment.BottomStart).padding(18.dp)
                ) {
                    Text(
                        title.title,
                        color = NkiriText,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    MetadataLine(
                        listOfNotNull(
                            title.year?.toString(),
                            title.rating?.let { "★ $it" },
                            title.type.replaceFirstChar { it.uppercase() },
                            title.genre.takeIf { it.isNotBlank() },
                            title.country?.takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val firstDirect = state.sources.firstOrNull { !it.external && !it.url.isNullOrBlank() }
                    PremiumButton(
                        text = "Play",
                        onClick = { firstDirect?.let(onPlay) },
                        icon = Icons.Default.PlayArrow,
                        enabled = firstDirect != null || title.type == "series",
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onFavorite,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = NkiriShapes.medium,
                        border = BorderStroke(1.dp, NkiriOutline)
                    ) {
                        Icon(
                            if (state.isCurrentFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (state.isCurrentFavorite) NkiriViolet else NkiriText
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isCurrentFavorite) "Saved" else "Library", color = NkiriText)
                    }
                }

                title.trailer?.takeIf { it.isNotBlank() }?.let { trailer ->
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onOpenUrl(trailer) },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, NkiriOutline),
                        shape = NkiriShapes.medium
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = NkiriViolet)
                        Spacer(Modifier.width(6.dp))
                        Text("Watch trailer", color = NkiriText)
                    }
                }

                if (title.description.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Text(title.description, color = NkiriMuted, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (title.type == "series") {
            item { SectionHeader("Seasons", "Choose an episode or save a full season") }

            if (state.loading && title.seasons.isEmpty()) {
                items(3) {
                    LoadingSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        height = 78
                    )
                }
            }
            items(title.seasons, key = { it.season }) { season ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = NkiriCard,
                    shape = NkiriShapes.medium,
                    border = BorderStroke(1.dp, NkiriOutline)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Season ${season.season}", color = NkiriText, fontWeight = FontWeight.Bold)
                                Text("${season.maxEp} episodes", color = NkiriMuted)
                            }
                            PremiumButton("Episodes", { onSeason(season.season) })
                        }
                        TextButton(onClick = { onSeason(season.season) }) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = NkiriViolet)
                            Spacer(Modifier.width(6.dp))
                            Text("Choose episodes", color = NkiriViolet)
                        }
                    }
                }
            }
        } else {
            item { SectionHeader("Watch or download", "Choose a source and quality") }

            if (state.loading && state.sources.isEmpty()) {
                items(2) {
                    LoadingSkeleton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        height = 126
                    )
                }
            }

            items(state.sources.sortedByDescending { it.quality }) { source ->
                SourceCard(source, onPlay, onDownload)
            }
        }

        if (state.recommendations.isNotEmpty()) {
            item {
                MovieRail(
                    title = "More like this",
                    items = state.recommendations,
                    onOpen = onOpenTitle
                )
            }
        }
    }
}

@Composable
fun EpisodesScreen(
    state: UiState,
    onBack: () -> Unit,
    onEpisode: (EpisodeItem) -> Unit,
    onDownloadEpisodes: (List<EpisodeItem>) -> Unit
) {
    val context = LocalContext.current
    val managedDownloads by
        ManagedDownloads.state(context)
            .collectAsStateWithLifecycle()

    val freeBytes =
        context.getExternalFilesDir(
            Environment.DIRECTORY_MOVIES
        )?.usableSpace
            ?: 0L

    val orderedEpisodes =
        remember(state.episodes) {
            state.episodes
                .sortedBy {
                    it.episode
                }
        }

    val selected =
        remember(state.season) {
            mutableStateListOf<Int>()
        }

    var rangeFrom by
        remember(state.season) {
            mutableStateOf("")
        }

    var rangeTo by
        remember(state.season) {
            mutableStateOf("")
        }

    val selectedEpisodes =
        orderedEpisodes.filter {
            selected.contains(
                it.episode
            )
        }

    val knownBytes =
        selectedEpisodes.sumOf { episode ->
            state.episodeSources[
                episode.episode
            ]?.size
                ?.coerceAtLeast(0L)
                ?: 0L
        }

    val unknownCount =
        selectedEpisodes.count { episode ->
            val source =
                state.episodeSources[
                    episode.episode
                ]

            source == null ||
            source.size <= 0L
        }

    fun toggleEpisode(
        episodeNumber: Int
    ) {
        if (
            selected.contains(
                episodeNumber
            )
        ) {
            selected.remove(
                episodeNumber
            )
        } else {
            selected.add(
                episodeNumber
            )
        }
    }

    fun applyRange() {
        val from =
            rangeFrom.toIntOrNull()

        val to =
            rangeTo.toIntOrNull()

        if (
            from == null ||
            to == null
        ) {
            return
        }

        val first =
            minOf(
                from,
                to
            )

        val last =
            maxOf(
                from,
                to
            )

        orderedEpisodes
            .filter {
                it.episode in first..last
            }
            .forEach { episode ->
                if (
                    !selected.contains(
                        episode.episode
                    )
                ) {
                    selected.add(
                        episode.episode
                    )
                }
            }
    }

    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NkiriText
                    )
                }

                Column(
                    Modifier.weight(1f)
                ) {
                    Text(
                        "Episodes",
                        color = NkiriText,
                        style =
                            MaterialTheme.typography.headlineSmall
                    )

                    state.season?.let {
                        Text(
                            "Season $it",
                            color = NkiriMuted
                        )
                    }
                }
            }
        }

        if (
            orderedEpisodes.isNotEmpty()
        ) {
            item {
                Surface(
                    modifier =
                        Modifier.fillMaxWidth(),
                    color = NkiriCard,
                    shape = NkiriShapes.medium,
                    border =
                        BorderStroke(
                            1.dp,
                            NkiriOutline
                        )
                ) {
                    Column(
                        modifier =
                            Modifier.padding(14.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selected.clear()
                                    selected.addAll(
                                        orderedEpisodes
                                            .map {
                                                it.episode
                                            }
                                    )
                                },
                                modifier =
                                    Modifier.weight(1f)
                            ) {
                                Text("Select all")
                            }

                            OutlinedButton(
                                onClick = {
                                    selected.clear()
                                },
                                modifier =
                                    Modifier.weight(1f)
                            ) {
                                Text("Clear")
                            }
                        }

                        Text(
                            "Select a range",
                            color = NkiriText,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = rangeFrom,
                                onValueChange = {
                                    rangeFrom =
                                        it.filter(
                                            Char::isDigit
                                        ).take(3)
                                },
                                label = {
                                    Text("From")
                                },
                                singleLine = true,
                                modifier =
                                    Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = rangeTo,
                                onValueChange = {
                                    rangeTo =
                                        it.filter(
                                            Char::isDigit
                                        ).take(3)
                                },
                                label = {
                                    Text("To")
                                },
                                singleLine = true,
                                modifier =
                                    Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    applyRange()
                                }
                            ) {
                                Text("Mark")
                            }
                        }

                        if (
                            selectedEpisodes
                                .isNotEmpty()
                        ) {
                            HorizontalDivider(
                                color =
                                    NkiriOutline
                            )

                            Text(
                                "${selectedEpisodes.size} episode(s) selected",
                                color = NkiriText,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            val totalLabel =
                                when {
                                    knownBytes > 0L &&
                                    unknownCount > 0 ->
                                        "${formatEpisodeBytes(knownBytes)} + $unknownCount unknown"

                                    knownBytes > 0L ->
                                        formatEpisodeBytes(
                                            knownBytes
                                        )

                                    else ->
                                        "Calculating…"
                                }

                            Text(
                                "Total size: $totalLabel",
                                color = NkiriViolet,
                                fontWeight =
                                    FontWeight.Black
                            )

                            if (freeBytes > 0L) {
                                Text(
                                    "Free space: ${formatEpisodeBytes(freeBytes)}",
                                    color =
                                        if (
                                            knownBytes > freeBytes
                                        ) {
                                            NkiriDanger
                                        } else {
                                            NkiriMuted
                                        }
                                )
                            }

                            if (
                                freeBytes > 0L &&
                                knownBytes > freeBytes
                            ) {
                                Text(
                                    "Not enough free space for the selected episodes.",
                                    color = NkiriDanger,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    onDownloadEpisodes(
                                        selectedEpisodes
                                    )
                                },
                                enabled =
                                    freeBytes <= 0L ||
                                    knownBytes <= 0L ||
                                    knownBytes <= freeBytes,
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null
                                )

                                Spacer(
                                    Modifier.width(7.dp)
                                )

                                Text(
                                    "Download selected"
                                )
                            }
                        } else {
                            Text(
                                "Tick episodes below or mark a range such as 7 to 9.",
                                color = NkiriMuted
                            )
                        }
                    }
                }
            }
        }

        if (
            state.loading &&
            orderedEpisodes.isEmpty()
        ) {
            items(4) {
                LoadingSkeleton(
                    modifier =
                        Modifier.fillMaxWidth(),
                    height = 72
                )
            }
        }

        items(
            orderedEpisodes,
            key = {
                it.episode
            }
        ) { episode ->
            val checked =
                selected.contains(
                    episode.episode
                )

            val source =
                state.episodeSources[
                    episode.episode
                ]

            val downloaded =
                managedDownloads.any {
                    it.episodeLabel == episode.label &&
                    it.status == ManagedDownloadStatus.COMPLETED
                }

            val watched =
                state.history.any {
                    it.mediaId == state.title?.id &&
                    it.season == episode.season &&
                    it.episode == episode.episode &&
                    it.completed
                }

            val sizeLabel =
                when {
                    source == null ->
                        "Checking size…"

                    source.size > 0L ->
                        formatEpisodeBytes(
                            source.size
                        )

                    source.sizeText
                        .isNotBlank() ->
                        source.sizeText

                    else ->
                        "Size unknown"
                }

            Surface(
                modifier =
                    Modifier.fillMaxWidth(),
                color = NkiriCard,
                shape = NkiriShapes.medium,
                border =
                    BorderStroke(
                        1.dp,
                        if (checked) {
                            NkiriViolet
                        } else {
                            NkiriOutline
                        }
                    )
            ) {
                Row(
                    modifier =
                        Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 8.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            toggleEpisode(
                                episode.episode
                            )
                        }
                    )

                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clickable {
                                    onEpisode(
                                        episode
                                    )
                                }
                                .padding(
                                    vertical = 8.dp
                                )
                    ) {
                        Text(
                            episode.label,
                            color = NkiriText,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            listOfNotNull(
                                sizeLabel,
                                "Downloaded".takeIf { downloaded },
                                "Watched".takeIf { watched }
                            ).joinToString(" • "),
                            color =
                                when {
                                    downloaded -> NkiriSuccess
                                    watched -> NkiriViolet
                                    (source?.size ?: 0L) > 0L -> NkiriViolet
                                    else -> NkiriMuted
                                }
                        )
                    }

                    IconButton(
                        onClick = {
                            onEpisode(
                                episode
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription =
                                "Open episode",
                            tint = NkiriViolet
                        )
                    }
                }
            }
        }
    }
}

private fun formatEpisodeBytes(
    bytes: Long
): String {
    if (
        bytes <= 0L
    ) {
        return "Unknown"
    }

    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0

    return when {
        bytes >= gb ->
            "%.2f GB".format(
                bytes / gb
            )

        bytes >= mb ->
            "%.1f MB".format(
                bytes / mb
            )

        bytes >= kb ->
            "%.0f KB".format(
                bytes / kb
            )

        else ->
            "$bytes B"
    }
}

@Composable
fun QualityScreen(
    state: UiState,
    onBack: () -> Unit,
    onPlay: (SourceItem) -> Unit,
    onDownload: (SourceItem) -> Unit
) {
    val context = LocalContext.current
    val preferences =
        remember {
            AppPreferences(
                context.applicationContext
            )
        }
    var preferredQuality by
        remember {
            mutableIntStateOf(
                preferences.preferredQuality()
            )
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NkiriText)
                }
                Column {
                    Text(
                        state.episode?.label ?: "Choose quality",
                        color = NkiriText,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text("Play now or save offline", color = NkiriMuted)
                }
            }
        }

        if (state.loading && state.sources.isEmpty()) {
            items(3) {
                LoadingSkeleton(
                    modifier = Modifier.fillMaxWidth(),
                    height = 126
                )
            }
        }

        items(state.sources.sortedByDescending { it.quality }) { source ->
            SourceCard(
                source = source,
                onPlay = onPlay,
                onDownload = onDownload,
                preferred =
                    source.quality > 0 &&
                    source.quality == preferredQuality,
                onRememberQuality = { quality ->
                    preferences.setPreferredQuality(quality)
                    preferredQuality = quality
                }
            )
        }
    }
}

@Composable
private fun SourceCard(
    source: SourceItem,
    onPlay: (SourceItem) -> Unit,
    onDownload: (SourceItem) -> Unit,
    preferred: Boolean = false,
    onRememberQuality: ((Int) -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = NkiriCard,
        shape = NkiriShapes.medium,
        border = BorderStroke(1.dp, NkiriOutline)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (source.quality > 0) "${source.quality}p" else "Source",
                    color = NkiriViolet,
                    fontWeight = FontWeight.Black
                )
                if (preferred) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Default",
                        color = NkiriSuccess,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            MetadataLine(
                listOf(
                    source.format.uppercase().takeIf { it.isNotBlank() } ?: "",
                    source.sizeText.takeIf { it.isNotBlank() } ?: "",
                    if (source.external) "External provider page" else "Direct source"
                )
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PremiumButton(
                    text = "Play",
                    onClick = { onPlay(source) },
                    icon = Icons.Default.PlayArrow,
                    enabled = !source.external,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { onDownload(source) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    border = BorderStroke(1.dp, NkiriOutline),
                    shape = NkiriShapes.medium
                ) {
                    Icon(
                        if (source.external) Icons.Default.OpenInNew else Icons.Default.Download,
                        contentDescription = null,
                        tint = NkiriText
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (source.external) "Provider" else "Download", color = NkiriText)
                }
            }

            if (
                onRememberQuality != null &&
                source.quality > 0 &&
                !source.external
            ) {
                TextButton(
                    onClick = {
                        onRememberQuality(source.quality)
                    },
                    enabled = !preferred
                ) {
                    Text(
                        if (preferred) {
                            "Preferred quality"
                        } else {
                            "Use ${source.quality}p by default"
                        },
                        color =
                            if (preferred) NkiriSuccess
                            else NkiriViolet
                    )
                }
            }
        }
    }
}
