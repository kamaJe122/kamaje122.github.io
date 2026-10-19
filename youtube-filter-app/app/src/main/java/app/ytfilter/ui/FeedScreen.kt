package app.ytfilter.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ytfilter.core.BlockReason
import app.ytfilter.core.VideoItem
import coil.compose.AsyncImage

fun BlockReason.describe(): String = when (this) {
    is BlockReason.Keyword -> "關鍵字「$rule」（$field）"
    is BlockReason.Channel -> "封鎖頻道：$channelTitle"
    is BlockReason.Face -> "封面認出：$personName（相似度 ${"%.2f".format(similarity)}）"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    vm: FeedViewModel,
    onSignIn: () -> Unit,
    onOpenRules: () -> Unit,
    onPlay: (VideoItem) -> Unit,
) {
    val s by vm.state.collectAsState()
    var showBlocked by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(s.query) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("淨化 YouTube") },
                actions = {
                    if (!s.signedIn) IconButton(onClick = onSignIn) { Icon(Icons.AutoMirrored.Filled.Login, "登入") }
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "重新整理") }
                    IconButton(onClick = onOpenRules) { Icon(Icons.Default.Shield, "封鎖規則") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(selectedTabIndex = s.source.ordinal) {
                FeedSource.entries.forEach { src ->
                    Tab(selected = s.source == src, onClick = { vm.select(src) }, text = { Text(src.label) })
                }
            }
            if (s.source == FeedSource.Search) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    singleLine = true,
                    placeholder = { Text("搜尋（每次扣 100 點 API 額度）") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
                )
            }
            if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
            if (s.blocked.isNotEmpty()) {
                AssistChip(
                    onClick = { showBlocked = true },
                    label = { Text("🛡️ 已擋下 ${s.blocked.size} 支影片") },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.shown, key = { it.id }) { v ->
                    VideoCard(v, onClick = { onPlay(v) }, onBlockChannel = { vm.blockChannel(v) })
                }
            }
        }
    }

    if (showBlocked) {
        AlertDialog(
            onDismissRequest = { showBlocked = false },
            confirmButton = { TextButton(onClick = { showBlocked = false }) { Text("關閉") } },
            title = { Text("被擋下的影片") },
            text = {
                LazyColumn {
                    items(s.blocked, key = { it.first.id }) { (v, reason) ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(reason.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoCard(v: VideoItem, onClick: () -> Unit, onBlockChannel: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true })) {
            AsyncImage(
                model = v.thumbnailUrl,
                contentDescription = v.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Column {
                    Text(v.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(v.channelTitle, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("封鎖頻道「${v.channelTitle}」") }, onClick = { menu = false; onBlockChannel() })
        }
    }
}
