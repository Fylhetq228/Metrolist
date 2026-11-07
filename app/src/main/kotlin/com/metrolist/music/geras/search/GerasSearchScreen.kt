package com.metrolist.music.geras.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.geras.source.ItemKind
import com.metrolist.music.geras.source.MusicSources
import com.metrolist.music.geras.source.OpenContext
import com.metrolist.music.geras.source.SourceItem
import com.metrolist.music.geras.ui.gerasRaised
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import com.metrolist.music.geras.ui.gerasBounce

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GerasSearchScreen(viewModel: GerasSearchViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    val query by viewModel.query.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val kind by viewModel.kind.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val downloadedOnly by viewModel.downloadedOnly.collectAsStateWithLifecycle()
    val link by viewModel.link.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val openContext = remember(navController, playerConnection) { OpenContext(navController, playerConnection) }

    val open: (SourceItem) -> Unit = { item ->
        focusManager.clearFocus()
        if (query.isNotBlank()) viewModel.commitHistory(online.corrected ?: query)
        viewModel.rememberOpened(item)
        MusicSources.byId(item.sourceId)?.open(item, openContext)
    }
    val showMenu: (SourceItem) -> Unit = { item ->
        when (val raw = item.raw) {
            is SongItem -> menuState.show { YouTubeSongMenu(song = raw, onDismiss = menuState::dismiss) }
            is AlbumItem -> menuState.show { YouTubeAlbumMenu(albumItem = raw, onDismiss = menuState::dismiss) }
            is ArtistItem -> menuState.show { YouTubeArtistMenu(artist = raw, onDismiss = menuState::dismiss) }
            is PlaylistItem -> menuState.show { YouTubePlaylistMenu(playlist = raw, coroutineScope = scope, onDismiss = menuState::dismiss) }
            else -> Unit
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken ->
                viewModel.query.value = spoken
                viewModel.commitHistory(spoken)
            }
        }
    }
    val voiceUnavailable = stringResource(R.string.geras_voice_unavailable)
    val startVoice: () -> Unit = {
        try {
            voiceLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.geras_voice_prompt)),
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, voiceUnavailable, Toast.LENGTH_LONG).show()
        }
    }

    // Offer a music link from the clipboard each time the screen comes back.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(450)
            val clip = context.getSystemService(ClipboardManager::class.java)
            val text = runCatching { clip?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull()
            viewModel.inspectLink(text)
        }
    }
    LaunchedEffect(query) {
        if (query.startsWith("http")) viewModel.inspectLink(query)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(listState, kind) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .filter { (last, total) -> kind != null && last != null && last >= total - 4 }
            .collect { viewModel.loadMore() }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
        ) {
            SearchField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                onSubmit = {
                    viewModel.commitHistory(it)
                    focusManager.clearFocus()
                },
                onVoice = startVoice,
                onRecognize = { navController.navigate("recognition") },
                focusRequester = focusRequester,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainer)
                    .padding(4.dp),
            ) {
                Segment(stringResource(R.string.geras_search_online), tab == SearchTab.ONLINE, Modifier.weight(1f)) { viewModel.tab.value = SearchTab.ONLINE }
                Segment(stringResource(R.string.geras_search_library), tab == SearchTab.LIBRARY, Modifier.weight(1f)) { viewModel.tab.value = SearchTab.LIBRARY }
            }
        }

        val bottomPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues()
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 10.dp, bottom = bottomPadding.calculateBottomPadding() + 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            link?.let { item ->
                item(key = "link") {
                    LinkCard(item, onOpen = { open(item); viewModel.dismissLink() }, onDismiss = viewModel::dismissLink)
                }
            }

            when {
                tab == SearchTab.LIBRARY -> libraryContent(library, query, downloadedOnly, viewModel, playerConnection, navController, menuState)
                query.isBlank() -> emptyStateContent(recent, history, open, viewModel)
                else -> onlineContent(online, suggestions, kind, source, viewModel, open, showMenu)
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onVoice: () -> Unit,
    onRecognize: () -> Unit,
    focusRequester: FocusRequester,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .gerasRaised(RoundedCornerShape(50), scheme.surfaceContainerHigh, elevation = 8.dp)
            .padding(start = 16.dp, end = 6.dp),
    ) {
        Icon(painterResource(R.drawable.search), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(stringResource(R.string.geras_search_hint), color = scheme.onSurfaceVariant, fontSize = 16.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = scheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = MaterialTheme.typography.bodyLarge.fontFamily),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit(value) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (value.isNotEmpty()) {
            FieldButton(R.drawable.close) { onValueChange("") }
        }
        FieldButton(R.drawable.mic, onVoice)
        FieldButton(R.drawable.graphic_eq, onRecognize)
    }
}

@Composable
private fun FieldButton(icon: Int, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.gerasBounce(0.95f)
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.gerasBounce(0.95f)
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.animation.animateColorAsState(if (selected) scheme.surfaceContainerHighest else Color.Transparent, androidx.compose.animation.core.spring(stiffness = 500f), label = "seg").value)
            .clickable(onClick = onClick),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) scheme.onSurface else scheme.onSurfaceVariant)
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.gerasBounce(0.95f)
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.animation.animateColorAsState(if (selected) scheme.primary else scheme.surfaceContainer, androidx.compose.animation.core.spring(stiffness = 500f), label = "chip").value)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.animation.animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurface, label = "chipText").value)
    }
}

@Composable
private fun SectionHeader(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
    ) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.gerasBounce(0.95f)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun kindLabel(kind: ItemKind): String = stringResource(
    when (kind) {
        ItemKind.TRACK -> R.string.geras_kind_tracks
        ItemKind.ALBUM -> R.string.geras_kind_albums
        ItemKind.ARTIST -> R.string.geras_kind_artists
        ItemKind.PLAYLIST -> R.string.geras_kind_playlists
    },
)

@Composable
private fun kindSingular(kind: ItemKind): String = stringResource(
    when (kind) {
        ItemKind.TRACK -> R.string.geras_kind_track
        ItemKind.ALBUM -> R.string.geras_kind_album
        ItemKind.ARTIST -> R.string.geras_kind_artist
        ItemKind.PLAYLIST -> R.string.geras_kind_playlist
    },
)

private fun formatDuration(sec: Int?): String? = sec?.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) }

@Composable
private fun SourceBadge(sourceId: String) {
    val src = MusicSources.byId(sourceId) ?: return
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color(src.badgeColor).copy(alpha = 0.18f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(src.badge, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(src.badgeColor))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultRow(item: SourceItem, onClick: () -> Unit, onMenu: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.gerasBounce(0.95f)
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        val shape = if (item.kind == ItemKind.ARTIST) RoundedCornerShape(50) else RoundedCornerShape(10.dp)
        Box(Modifier.size(52.dp).clip(shape).background(scheme.surfaceContainerHigh)) {
            AsyncImage(model = item.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(item.sourceId)
                Spacer(Modifier.width(6.dp))
                if (item.explicit) {
                    Text("E", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                }
                Text(
                    listOfNotNull(kindSingular(item.kind), item.subtitle.takeIf { it.isNotBlank() }, formatDuration(item.durationSec)).joinToString(" · "),
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.gerasBounce(0.95f)
                .size(40.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onMenu),
        ) {
            Icon(painterResource(R.drawable.more_vert), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopResultCard(item: SourceItem, onClick: () -> Unit, onMenu: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.gerasBounce(0.95f)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .gerasRaised(RoundedCornerShape(22.dp), scheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(12.dp),
    ) {
        val shape = if (item.kind == ItemKind.ARTIST) RoundedCornerShape(50) else RoundedCornerShape(14.dp)
        Box(Modifier.size(84.dp).clip(shape).background(scheme.surfaceContainerHigh)) {
            AsyncImage(model = item.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.geras_top_result), fontSize = 12.sp, color = scheme.primary, fontWeight = FontWeight.Bold)
            Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(item.sourceId)
                Spacer(Modifier.width(6.dp))
                Text(
                    listOfNotNull(kindSingular(item.kind), item.subtitle.takeIf { it.isNotBlank() }).joinToString(" · "),
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.kind == ItemKind.TRACK) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.gerasBounce(0.95f)
                    .size(46.dp)
                    .gerasRaised(RoundedCornerShape(50), scheme.onSurface, elevation = 6.dp)
                    .clickable(onClick = onClick),
            ) {
                Icon(painterResource(R.drawable.play), null, tint = scheme.surface, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun CoverTile(item: SourceItem, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.gerasBounce(0.95f)
            .width(128.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        val shape = if (item.kind == ItemKind.ARTIST) RoundedCornerShape(50) else RoundedCornerShape(12.dp)
        Box(Modifier.size(120.dp).clip(shape).background(scheme.surfaceContainerHigh)) {
            AsyncImage(model = item.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.subtitle.ifBlank { kindSingular(item.kind) }, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LinkCard(item: SourceItem, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.gerasBounce(0.95f)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .gerasRaised(RoundedCornerShape(20.dp), scheme.primaryContainer)
            .clickable(onClick = onOpen)
            .padding(10.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainerHigh)) {
            AsyncImage(model = item.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.geras_link_from_clipboard), fontSize = 12.sp, color = scheme.onPrimaryContainer.copy(alpha = 0.8f))
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FieldButton(R.drawable.close, onDismiss)
    }
}

private fun LazyListScope.emptyStateContent(
    recent: List<SourceItem>,
    history: List<com.metrolist.music.db.entities.SearchHistory>,
    open: (SourceItem) -> Unit,
    viewModel: GerasSearchViewModel,
) {
    if (recent.isNotEmpty()) {
        item(key = "recent_title") {
            SectionHeader(stringResource(R.string.geras_recently_opened), stringResource(R.string.geras_clear)) { viewModel.clearRecent() }
        }
        item(key = "recent_row") {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(recent, key = { it.sourceId + it.id }) { CoverTile(it) { open(it) } }
            }
        }
    }
    if (history.isNotEmpty()) {
        item(key = "history_title") { SectionHeader(stringResource(R.string.geras_search_history)) }
        items(history, key = { "h" + it.id }) { entry ->
            Box(Modifier.animateItem()) { HistoryRow(entry.query, onClick = { viewModel.query.value = entry.query }, onDelete = { viewModel.deleteHistory(entry) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryRow(text: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue != androidx.compose.material3.SwipeToDismissBoxValue.Settled) onDelete()
    }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .background(scheme.errorContainer)
                    .padding(horizontal = 24.dp),
            ) {
                Icon(painterResource(R.drawable.delete), null, tint = scheme.onErrorContainer)
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.gerasBounce(0.95f)
                .fillMaxWidth()
                .background(scheme.background)
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 13.dp),
        ) {
            Icon(painterResource(R.drawable.history), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(text, fontSize = 15.sp, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

private fun LazyListScope.onlineContent(
    online: OnlineState,
    suggestions: List<String>,
    kind: ItemKind?,
    source: String?,
    viewModel: GerasSearchViewModel,
    open: (SourceItem) -> Unit,
    showMenu: (SourceItem) -> Unit,
) {
    item(key = "filters") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            FilterChip(stringResource(R.string.geras_kind_all), kind == null) { viewModel.kind.value = null }
            ItemKind.entries.forEach { k -> FilterChip(kindLabel(k), kind == k) { viewModel.kind.value = k } }
            if (MusicSources.all.size > 1) {
                Spacer(Modifier.width(8.dp))
                FilterChip(stringResource(R.string.geras_all_sources), source == null) { viewModel.source.value = null }
                MusicSources.all.forEach { s -> FilterChip(s.name, source == s.id) { viewModel.source.value = s.id } }
            }
        }
    }
    if (suggestions.isNotEmpty()) {
        item(key = "suggestions") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                suggestions.forEach { s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.gerasBounce(0.95f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                viewModel.query.value = s
                                viewModel.commitHistory(s)
                            }
                            .padding(horizontal = 10.dp),
                    ) {
                        Icon(painterResource(R.drawable.search), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
    if (online.corrected != null) {
        item(key = "correction") {
            val scheme = MaterialTheme.colorScheme
            Column(Modifier.gerasBounce(0.95f).padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(stringResource(R.string.geras_showing_results_for, online.corrected), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.geras_search_instead, online.query),
                    fontSize = 13.sp,
                    color = scheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { viewModel.useOriginalQuery() },
                )
            }
        }
    }
    online.error?.let { err ->
        item(key = "error") {
            Text(err, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        }
    }
    if (online.loading && online.sections.isEmpty()) {
        item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
            }
        }
    }
    if (kind == null) {
        online.top?.let { top -> item(key = "top") { Box(Modifier.animateItem()) { TopResultCard(top, { open(top) }, { showMenu(top) }) } } }
        online.sections.forEach { section ->
            val k = section.kind ?: return@forEach
            item(key = "title_$k") {
                SectionHeader(kindLabel(k), stringResource(R.string.geras_more)) { viewModel.kind.value = k }
            }
            if (k == ItemKind.TRACK) {
                items(section.items.take(6), key = { "t_" + it.sourceId + it.id }) { Box(Modifier.animateItem()) { ResultRow(it, { open(it) }, { showMenu(it) }) } }
            } else {
                item(key = "row_$k") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(section.items, key = { it.sourceId + it.id }) { CoverTile(it) { open(it) } }
                    }
                }
            }
        }
    } else {
        val items = online.sections.flatMap { it.items }
        items(items, key = { "f_" + it.sourceId + it.id }) { Box(Modifier.animateItem()) { ResultRow(it, { open(it) }, { showMenu(it) }) } }
        if (online.loading && items.isNotEmpty()) {
            item(key = "more_loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

private fun LazyListScope.libraryContent(
    library: LibraryState,
    query: String,
    downloadedOnly: Boolean,
    viewModel: GerasSearchViewModel,
    playerConnection: com.metrolist.music.playback.PlayerConnection,
    navController: androidx.navigation.NavController,
    menuState: com.metrolist.music.ui.component.MenuState,
) {
    item(key = "lib_filters") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            FilterChip(stringResource(R.string.geras_everything_saved), !downloadedOnly) { viewModel.downloadedOnly.value = false }
            FilterChip(stringResource(R.string.geras_downloaded_only), downloadedOnly) { viewModel.downloadedOnly.value = true }
        }
    }
    if (query.isBlank()) {
        item(key = "lib_hint") {
            Text(stringResource(R.string.geras_library_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        }
        return
    }
    if (library.songs.isEmpty() && library.artists.isEmpty() && library.albums.isEmpty() && library.playlists.isEmpty()) {
        item(key = "lib_empty") {
            Text(stringResource(R.string.geras_search_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        }
        return
    }
    if (library.songs.isNotEmpty()) {
        item(key = "lib_songs_title") { SectionHeader(stringResource(R.string.geras_kind_tracks)) }
        items(library.songs.size, key = { "ls_" + library.songs[it].id }) { index ->
            val song = library.songs[index]
            val item = SourceItem("local", ItemKind.TRACK, song.id, song.song.title, song.artists.joinToString { it.name }, song.song.thumbnailUrl, song.song.duration)
            ResultRow(
                item,
                onClick = {
                    playerConnection.playQueue(ListQueue(title = query, items = library.songs.map { it.toMediaItem() }, startIndex = index))
                },
                onMenu = { menuState.show { SongMenu(originalSong = song, onDismiss = menuState::dismiss) } },
            )
        }
    }
    fun localRow(key: String, title: Int, tiles: List<SourceItem>, route: (SourceItem) -> String) {
        if (tiles.isEmpty()) return
        item(key = "${key}_title") { SectionHeader(stringResource(title)) }
        item(key = "${key}_row") {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                items(tiles, key = { it.id }) { tile -> CoverTile(tile) { navController.navigate(route(tile)) } }
            }
        }
    }
    localRow("lib_artists", R.string.geras_kind_artists, library.artists.map { SourceItem("local", ItemKind.ARTIST, it.id, it.artist.name, "", it.artist.thumbnailUrl) }) { "artist/${it.id}" }
    localRow("lib_albums", R.string.geras_kind_albums, library.albums.map { SourceItem("local", ItemKind.ALBUM, it.id, it.album.title, it.artists.joinToString { a -> a.name }, it.album.thumbnailUrl) }) { "album/${it.id}" }
    localRow("lib_playlists", R.string.geras_kind_playlists, library.playlists.map { SourceItem("local", ItemKind.PLAYLIST, it.id, it.playlist.name, "", it.thumbnails.firstOrNull()) }) { "local_playlist/${it.id}" }
}
