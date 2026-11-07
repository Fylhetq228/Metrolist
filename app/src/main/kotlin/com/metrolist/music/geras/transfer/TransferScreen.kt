package com.metrolist.music.geras.transfer

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.geras.settings.GerasTopBar
import com.metrolist.music.geras.ui.gerasAppear
import com.metrolist.music.geras.ui.gerasBounce
import com.metrolist.music.geras.ui.gerasGlass
import com.metrolist.music.geras.ui.gerasRaised
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch

const val TransferRoute = "geras/transfer?url={url}"
fun transferRoute(url: String? = null) = "geras/transfer?url=" + android.net.Uri.encode(url.orEmpty())

private val ColorFound = Color(0xFF5BC98A)
private val ColorDoubt = Color(0xFFF0B44C)
private val ColorMissing = Color(0xFFE5645A)

private fun statusColor(s: MatchStatus) = when (s) {
    MatchStatus.FOUND -> ColorFound
    MatchStatus.DOUBTFUL -> ColorDoubt
    MatchStatus.NOT_FOUND -> ColorMissing
    MatchStatus.PENDING -> Color.Gray
}

private fun fmt(sec: Int?) = sec?.takeIf { it > 0 }?.let { "%d:%02d".format(it / 60, it % 60) }.orEmpty()

@Composable
fun TransferScreen(navController: NavController, initialUrl: String?) {
    val vm: TransferViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank() && state.phase == TransferState.Phase.INPUT && state.playlist == null) vm.importLink(initialUrl)
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = when (state.phase) {
                TransferState.Phase.INPUT -> 0
                TransferState.Phase.LOADING -> 1
                else -> 2
            },
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(spring(dampingRatio = 0.85f, stiffness = 320f), initialScale = 0.95f)) togetherWith fadeOut(tween(160))
            },
            label = "transferPhase",
        ) { phase ->
            when (phase) {
                0 -> InputPane(state.error, vm::importLink, vm::importFile)
                1 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.geras_transfer_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> ResultPane(state, vm, navController)
            }
        }
        GerasTopBar(title = stringResource(R.string.geras_transfer_title)) {
            if (state.phase == TransferState.Phase.INPUT) navController.navigateUp() else vm.reset()
        }
    }
}

@Composable
private fun InputPane(error: String?, onLink: (String) -> Unit, onFile: (android.net.Uri) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var link by rememberSaveable { mutableStateOf("") }
    val valid = link.isNotBlank()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onFile) }
    val bottom = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 100.dp, bottom = bottom + 24.dp),
    ) {
        Text(
            stringResource(R.string.geras_transfer_headline),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 34.sp),
            color = scheme.onSurface,
            modifier = Modifier.gerasAppear(0),
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.geras_transfer_sub), color = scheme.onSurfaceVariant, fontSize = 15.sp, modifier = Modifier.gerasAppear(1))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.gerasAppear(2)) {
            SourceBadge("Spotify", Color(0xFF1ED760), Modifier.weight(1f))
            SourceBadge("YouTube Music", Color(0xFFFF3D3D), Modifier.weight(1f))
            SourceBadge("SoundCloud", Color(0xFFFF7A1A), Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .gerasAppear(3)
                .fillMaxWidth()
                .height(56.dp)
                .gerasRaised(RoundedCornerShape(50), scheme.surfaceContainerHigh, elevation = 6.dp)
                .padding(start = 20.dp, end = 6.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (link.isEmpty()) Text(stringResource(R.string.geras_transfer_hint), color = scheme.onSurfaceVariant, fontSize = 15.sp, maxLines = 1)
                BasicTextField(
                    value = link,
                    onValueChange = { link = it },
                    singleLine = true,
                    textStyle = TextStyle(color = scheme.onSurface, fontSize = 15.sp, fontFamily = MaterialTheme.typography.bodyLarge.fontFamily),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (valid) onLink(link) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .gerasBounce(0.9f)
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { clipboard.getText()?.text?.let { link = it.trim() } },
            ) {
                Icon(painterResource(R.drawable.geras_paste), null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedVisibility(error != null) {
            Text(error.orEmpty(), color = ColorMissing, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, top = 10.dp))
        }
        Spacer(Modifier.height(14.dp))
        val bg by animateColorAsState(if (valid) scheme.onSurface else scheme.onSurface.copy(alpha = 0.15f), label = "go")
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .gerasAppear(4)
                .gerasBounce(0.95f)
                .fillMaxWidth()
                .height(54.dp)
                .gerasRaised(RoundedCornerShape(50), bg, elevation = if (valid) 12.dp else 0.dp)
                .clickable(enabled = valid) { onLink(link) },
        ) {
            Text(stringResource(R.string.geras_transfer_go), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = if (valid) scheme.surface else scheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .gerasAppear(5)
                .gerasBounce(0.95f)
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, scheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(50))
                .clickable { picker.launch(arrayOf("text/*", "application/json", "application/octet-stream")) },
        ) {
            Icon(painterResource(R.drawable.upload), null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.geras_transfer_file), fontWeight = FontWeight.Bold, color = scheme.onSurface)
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.geras_transfer_note), color = scheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.gerasAppear(6))
    }
}

@Composable
private fun SourceBadge(name: String, color: Color, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.06f))))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(vertical = 14.dp, horizontal = 6.dp),
    ) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.height(8.dp))
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private enum class Filter { ALL, FOUND, DOUBTFUL, NOT_FOUND }

@Composable
private fun ResultPane(state: TransferState, vm: TransferViewModel, navController: NavController) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val playlist = state.playlist ?: return
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var editing by remember { mutableStateOf<Int?>(null) }
    val bottom = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val cookie by rememberPreference(InnerTubeCookieKey, "")
    val loggedIn = remember(cookie) { "SAPISID" in parseCookieString(cookie) }
    val total = state.rows.size
    val matching = state.phase == TransferState.Phase.MATCHING

    val visibleRows = state.rows.filter {
        when (filter) {
            Filter.ALL -> true
            Filter.FOUND -> it.status == MatchStatus.FOUND
            Filter.DOUBTFUL -> it.status == MatchStatus.DOUBTFUL
            Filter.NOT_FOUND -> it.status == MatchStatus.NOT_FOUND
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(top = 92.dp, bottom = bottom + 110.dp)) {
            item(key = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.gerasAppear(0).padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Box(Modifier.size(96.dp).gerasRaised(RoundedCornerShape(20.dp), scheme.surfaceContainerHigh, elevation = 14.dp)) {
                        AsyncImage(playlist.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            playlist.source.label + " · " + stringResource(R.string.geras_sc_tracks_count, total),
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (playlist.truncated) {
                item(key = "truncated") {
                    Text(
                        stringResource(R.string.geras_transfer_truncated),
                        fontSize = 13.sp,
                        color = ColorDoubt,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            item(key = "progress") {
                AnimatedVisibility(matching, enter = fadeIn(), exit = fadeOut() + slideOutVertically { -it / 2 }) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        val p by animateFloatAsState(if (total == 0) 0f else state.done.toFloat() / total, spring(stiffness = 120f), label = "matchProgress")
                        LinearProgressIndicator(
                            progress = { p },
                            color = scheme.primary,
                            trackColor = scheme.onSurface.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.geras_transfer_matching, state.done, total), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "filters") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    FilterPill(stringResource(R.string.geras_transfer_all), total, null, filter == Filter.ALL) { filter = Filter.ALL }
                    FilterPill(stringResource(R.string.geras_transfer_found), state.count(MatchStatus.FOUND), ColorFound, filter == Filter.FOUND) { filter = Filter.FOUND }
                    FilterPill(stringResource(R.string.geras_transfer_doubtful), state.count(MatchStatus.DOUBTFUL), ColorDoubt, filter == Filter.DOUBTFUL) { filter = Filter.DOUBTFUL }
                    FilterPill(stringResource(R.string.geras_transfer_missing), state.count(MatchStatus.NOT_FOUND), ColorMissing, filter == Filter.NOT_FOUND) { filter = Filter.NOT_FOUND }
                }
            }
            if (filter == Filter.DOUBTFUL && state.count(MatchStatus.DOUBTFUL) > 0 && !matching) {
                item(key = "acceptAll") {
                    Text(
                        stringResource(R.string.geras_transfer_accept_all),
                        color = scheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .gerasBounce(0.95f)
                            .clip(RoundedCornerShape(50))
                            .clickable { vm.acceptDoubtful() }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
            }
            items(visibleRows, key = { "r" + it.index }) { row ->
                Box(Modifier.animateItem()) { RowItem(row) { editing = row.index } }
            }
        }

        // Action bar.
        AnimatedVisibility(
            visible = !matching,
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 380f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val found = state.count(MatchStatus.FOUND)
            var menu by remember { mutableStateOf(false) }
            val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                uri?.let { vm.writeExport(it, json = false) { ok -> toast(context, if (ok) R.string.geras_transfer_exported else R.string.geras_transfer_export_failed) } }
            }
            val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                uri?.let { vm.writeExport(it, json = true) { ok -> toast(context, if (ok) R.string.geras_transfer_exported else R.string.geras_transfer_export_failed) } }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = bottom + 8.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .gerasGlass(RoundedCornerShape(50))
                    .padding(6.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .gerasBounce(0.95f)
                        .gerasRaised(RoundedCornerShape(50), scheme.onSurface, elevation = 8.dp)
                        .clickable(enabled = !state.busy && found > 0) {
                            vm.saveLocal(includeDoubtful = false) { id ->
                                if (id != null) {
                                    toast(context, R.string.geras_transfer_saved)
                                    navController.navigate("local_playlist/$id")
                                }
                            }
                        },
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(color = scheme.surface, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    } else {
                        Text(
                            stringResource(R.string.geras_transfer_save, found),
                            color = scheme.surface,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Box {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .gerasBounce(0.9f)
                            .size(52.dp)
                            .clip(RoundedCornerShape(50))
                            .background(scheme.onSurface.copy(alpha = 0.08f))
                            .clickable { menu = true },
                    ) {
                        Icon(painterResource(R.drawable.more_vert), null, tint = scheme.onSurface)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (loggedIn) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.geras_transfer_to_ytm)) },
                                leadingIcon = { Icon(painterResource(R.drawable.backup), null) },
                                onClick = {
                                    menu = false
                                    vm.saveToYouTube(includeDoubtful = false) { added, all ->
                                        Toast.makeText(context, context.getString(R.string.geras_transfer_ytm_done, added, all), Toast.LENGTH_LONG).show()
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.geras_transfer_export_csv)) },
                            leadingIcon = { Icon(painterResource(R.drawable.download), null) },
                            onClick = {
                                menu = false
                                exportCsv.launch(playlist.name + ".csv")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.geras_transfer_export_json)) },
                            leadingIcon = { Icon(painterResource(R.drawable.download), null) },
                            onClick = {
                                menu = false
                                exportJson.launch(playlist.name + ".json")
                            },
                        )
                    }
                }
            }
        }
    }

    editing?.let { index ->
        state.rows.firstOrNull { it.index == index }?.let { row ->
            ReplaceSheet(row, vm, onDismiss = { editing = null })
        }
    }
}

private fun toast(context: android.content.Context, res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

@Composable
private fun FilterPill(text: String, count: Int, dot: Color?, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.onSurface else scheme.surfaceContainer, spring(stiffness = 500f), label = "fp")
    val fg by animateColorAsState(if (selected) scheme.surface else scheme.onSurface, label = "fpText")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .gerasBounce(0.93f)
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(dot))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
        Spacer(Modifier.width(6.dp))
        AnimatedContent(count, transitionSpec = { com.metrolist.music.geras.ui.GerasTextSwap }, label = "count") { n ->
            Text(n.toString(), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = fg.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun RowItem(row: TransferRow, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dim = if (row.removed) 0.4f else 1f
    val color by animateColorAsState(statusColor(row.status), label = "status")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .gerasBounce(0.97f)
            .clickable(enabled = row.status != MatchStatus.PENDING, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .graphicsLayer { alpha = dim },
    ) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerHigh)) {
            val thumb = row.match?.thumbnail ?: row.source.thumbnail
            if (thumb != null) AsyncImage(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (row.status == MatchStatus.PENDING) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp).align(Alignment.Center))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val m = row.match
            Text(
                m?.title ?: row.source.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (row.removed) TextDecoration.LineThrough else null,
            )
            Text(
                (m?.artists ?: row.source.artists).joinToString(", ") + (fmt(m?.durationSec ?: row.source.durationSec).takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""),
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (m != null && row.status != MatchStatus.FOUND || (m == null && row.status == MatchStatus.NOT_FOUND)) {
                Text(
                    stringResource(R.string.geras_transfer_was, row.source.title + " — " + row.source.artists.joinToString(", ") + fmt(row.source.durationSec).let { if (it.isEmpty()) "" else " · $it" }),
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplaceSheet(row: TransferRow, vm: TransferViewModel, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var query by remember(row.index) { mutableStateOf((row.source.title + " " + row.source.artists.firstOrNull().orEmpty()).trim()) }
    var results by remember(row.index) { mutableStateOf(row.candidates) }
    var searching by remember { mutableStateOf(false) }
    fun search() {
        searching = true
        scope.launch {
            results = vm.searchManual(query, row)
            searching = false
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = scheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 20.dp).heightIn(max = 620.dp)) {
            Text(stringResource(R.string.geras_transfer_source_track), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            Text(row.source.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                row.source.artists.joinToString(", ") + fmt(row.source.durationSec).let { if (it.isEmpty()) "" else " · $it" },
                fontSize = 14.sp,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHighest)
                    .padding(start = 18.dp, end = 4.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = scheme.onSurface, fontSize = 15.sp),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.gerasBounce(0.9f).size(40.dp).clip(RoundedCornerShape(50)).clickable { search() },
                ) {
                    if (searching) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(painterResource(R.drawable.search), null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(results, key = { it.id }) { c ->
                    val selected = row.match?.id == c.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .gerasBounce(0.97f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable {
                                vm.choose(row.index, c)
                                onDismiss()
                            }
                            .padding(8.dp),
                    ) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(scheme.surfaceContainerHigh)) {
                            AsyncImage(c.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                c.artists.joinToString(", ") + fmt(c.durationSec).let { if (it.isEmpty()) "" else " · $it" },
                                fontSize = 13.sp,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "${(c.score * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor(TransferMatcher.statusFor(row.source, c)),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .gerasBounce(0.95f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, scheme.onSurface.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .clickable {
                        vm.setRemoved(row.index, !row.removed)
                        onDismiss()
                    },
            ) {
                Text(
                    stringResource(if (row.removed) R.string.geras_transfer_restore else R.string.geras_transfer_remove),
                    fontWeight = FontWeight.Bold,
                    color = if (row.removed) scheme.onSurface else ColorMissing,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
