package app.ytfilter.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(vm: FeedViewModel, onBack: () -> Unit) {
    val rules by vm.rules.collectAsState()
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var personName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val name = personName.trim()
        status = "分析照片中…"
        scope.launch {
            val n = vm.addPerson(name, uris)
            status = if (n == 0) "照片裡找不到臉，換幾張清楚的正面照試試" else "已加入「$name」（$n 張臉）"
            if (n > 0) personName = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("封鎖規則") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            status?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
            item { Section("① 關鍵字", "比對標題、頻道名、標籤、說明。用 re: 開頭可寫正規表達式，例如 re:開箱.*實測") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(keyword, { keyword = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("新增關鍵字") })
                    Button(
                        onClick = { if (vm.addKeyword(keyword)) keyword = "" else status = "正規表達式寫錯了：$keyword" },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("加入") }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rules.keywords.forEach { k ->
                        InputChip(
                            selected = false,
                            onClick = { vm.removeKeyword(k) },
                            label = { Text(k) },
                            trailingIcon = { Icon(Icons.Default.Close, "移除") },
                        )
                    }
                }
            }

            item { HorizontalDivider() }
            item { Section("② 封鎖的頻道", "在首頁長按影片即可封鎖該頻道") }
            rules.channels.forEach { (id, name) ->
                item(key = "ch-$id") {
                    ListItem(
                        headlineContent = { Text(name) },
                        trailingContent = { IconButton(onClick = { vm.unblockChannel(id) }) { Icon(Icons.Default.Close, "解除") } },
                    )
                }
            }

            item { HorizontalDivider() }
            item {
                Section(
                    "③ AI 封面認人",
                    if (vm.faceModelAvailable) "給 3~5 張這個人的清楚照片，App 會在手機上記住他的臉部特徵，照片本身不會被儲存或上傳"
                    else "⚠️ 尚未放入 mobilefacenet.tflite 模型，這個功能目前停用（見 README）",
                )
            }
            if (vm.faceModelAvailable) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(personName, { personName = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("名字（自己看得懂就好）") })
                        Button(
                            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = personName.isNotBlank(),
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("選照片") }
                    }
                }
                rules.people.forEach { p ->
                    item(key = "p-${p.name}") {
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text("${p.references.size} 張參考臉") },
                            trailingContent = { IconButton(onClick = { vm.removePerson(p.name) }) { Icon(Icons.Default.Close, "移除") } },
                        )
                    }
                }
                item {
                    // Only persist when the finger lifts; dragging would otherwise re-filter on every frame.
                    var dragging by remember { mutableStateOf<Float?>(null) }
                    val value = dragging ?: rules.faceThreshold
                    Column {
                        Text("靈敏度：${"%.2f".format(value)}（越低擋越多，也越容易誤擋）")
                        Slider(
                            value = value,
                            onValueChange = { dragging = it },
                            onValueChangeFinished = { dragging?.let(vm::setThreshold); dragging = null },
                            valueRange = 0.4f..0.9f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, hint: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(hint, style = MaterialTheme.typography.bodySmall)
    }
}
