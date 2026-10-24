@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package id.bambang.catatku

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: NotesVm by viewModels()
    private val shared = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        SyncWorker.schedule(this)
        vm.sync(silent = true)
        setContent { CatatkuTheme { App(vm, shared) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type?.startsWith("text/") == true) {
            val subject = i.getStringExtra(Intent.EXTRA_SUBJECT)
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            shared.value = if (subject != null && !text.contains(subject)) "$subject\n$text" else text
            i.action = null
        }
    }
}

// ---------- theme ----------

private val Green = Color(0xFF0F7B5F)

@Composable
fun CatatkuTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF4FD1A5), onPrimary = Color(0xFF00382A),
        primaryContainer = Color(0xFF1F4D40), onPrimaryContainer = Color(0xFFE6F5EF),
        secondaryContainer = Color(0xFF26332F),
    ) else lightColorScheme(
        primary = Green, onPrimary = Color.White,
        primaryContainer = Color(0xFFD9FDD3), onPrimaryContainer = Color(0xFF0B1F18),
        secondaryContainer = Color(0xFFE3EEEA),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------- screen ----------

@Composable
fun App(vm: NotesVm, shared: MutableState<String?>) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    val notes by vm.notes.collectAsState()
    val tags by vm.tags.collectAsState()
    val filter by vm.filter.collectAsState()
    val query by vm.query.collectAsState()
    val syncing by vm.syncing.collectAsState()

    var input by remember { mutableStateOf(TextFieldValue("")) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var searching by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(!vm.prefs.configured) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        vm.toast.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(shared.value) {
        shared.value?.let {
            input = TextFieldValue(it + " ", TextRange(it.length + 1))
            shared.value = null
        }
    }
    LaunchedEffect(notes.size) { if (notes.isNotEmpty()) listState.animateScrollToItem(0) }

    BackHandler(enabled = searching || filter != null || editing != null) {
        when {
            editing != null -> { editing = null; input = TextFieldValue("") }
            searching -> { searching = false; vm.query.value = "" }
            else -> vm.filter.value = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        if (searching) {
                            TextField(
                                value = query, onValueChange = { vm.query.value = it },
                                placeholder = { Text("Cari…") }, singleLine = true,
                                colors = clearFieldColors(), modifier = Modifier.fillMaxWidth(),
                            )
                        } else Text("Catatku", fontWeight = FontWeight.SemiBold)
                    },
                    actions = {
                        IconButton(onClick = {
                            searching = !searching
                            if (!searching) vm.query.value = ""
                        }) { Icon(if (searching) Icons.Default.Close else Icons.Default.Search, "Cari") }
                        IconButton(onClick = { vm.sync() }) {
                            if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Sync, "Sync")
                        }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Pengaturan") }
                    },
                )
                FilterRow(tags, filter) { vm.filter.value = if (vm.filter.value == it) null else it }
            }
        },
        bottomBar = {
            InputBar(
                value = input, onChange = { input = it }, tags = tags, editing = editing,
                onCancelEdit = { editing = null; input = TextFieldValue("") },
                onSend = {
                    vm.save(input.text, editing)
                    editing = null
                    input = TextFieldValue("")
                },
            )
        },
    ) { pad ->
        Box(
            Modifier.fillMaxSize().padding(pad)
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
        ) {
            if (notes.isEmpty()) {
                Text(
                    if (filter != null || query.isNotBlank()) "Tidak ada yang cocok"
                    else "Belum ada catatan.\nKetik ide atau tempel link di bawah.\nPakai #tag untuk kategori.",
                    Modifier.align(Alignment.Center).padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            val rev = notes.asReversed()
            LazyColumn(
                state = listState, reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                rev.forEachIndexed { i, n ->
                    item(key = n.id) {
                        Bubble(
                            n,
                            onTag = { vm.filter.value = it },
                            onEdit = {
                                editing = n
                                input = TextFieldValue(n.text, TextRange(n.text.length))
                            },
                            onCopy = {
                                clipboard.setText(AnnotatedString(n.text))
                                Toast.makeText(ctx, "Disalin", Toast.LENGTH_SHORT).show()
                            },
                            onPin = { vm.togglePin(n) },
                            onShare = {
                                val s = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, n.text)
                                ctx.startActivity(Intent.createChooser(s, "Bagikan"))
                            },
                            onDelete = {
                                vm.delete(n)
                                scope.launch {
                                    val r = snack.showSnackbar("Catatan dihapus", "Batal", duration = SnackbarDuration.Short)
                                    if (r == SnackbarResult.ActionPerformed) vm.restore(n)
                                }
                            },
                        )
                    }
                    val older = rev.getOrNull(i + 1)
                    if (older == null || dayKey(older.createdAt) != dayKey(n.createdAt)) {
                        item(key = "d-${dayKey(n.createdAt)}-${n.id}") { DayHeader(n.createdAt) }
                    }
                }
            }
        }
    }

    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
private fun clearFieldColors() = TextFieldDefaults.colors(
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
)

@Composable
fun FilterRow(tags: List<String>, selected: String?, onPick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        item { FilterChip(selected = selected == F_PINNED, onClick = { onPick(F_PINNED) }, label = { Text("📌 Pin") }) }
        item { FilterChip(selected = selected == F_LINK, onClick = { onPick(F_LINK) }, label = { Text("🔗 Link") }) }
        items(tags, key = { it }) { t ->
            FilterChip(selected = selected == t, onClick = { onPick(t) }, label = { Text("#$t") })
        }
    }
}

// ---------- bubble ----------

private val TOKEN_RE = Regex("(https?://\\S+|www\\.\\S+|#[\\p{L}\\p{N}_-]+)", RegexOption.IGNORE_CASE)

private fun richText(text: String, linkColor: Color, tagColor: Color, onTag: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        var last = 0
        TOKEN_RE.findAll(text).forEach { m ->
            append(text.substring(last, m.range.first))
            val v = m.value
            if (v.startsWith("#")) {
                val tag = v.drop(1).lowercase()
                withLink(
                    LinkAnnotation.Clickable(
                        tag,
                        TextLinkStyles(SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold)),
                        LinkInteractionListener { onTag(tag) },
                    )
                ) { append(v) }
            } else {
                val clean = v.trimEnd('.', ',', ')', '!', '?', ';', ':', '"', '\'')
                val url = if (clean.startsWith("http", ignoreCase = true)) clean else "https://$clean"
                withLink(
                    LinkAnnotation.Url(
                        url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    )
                ) { append(clean) }
                append(v.substring(clean.length))
            }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }

@Composable
fun Bubble(
    n: Note,
    onTag: (String) -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val rich = remember(n.text, cs.primary) { richText(n.text, Color(0xFF1A73E8), cs.primary, onTag) }

    Box(Modifier.fillMaxWidth().padding(start = 48.dp, end = 10.dp, top = 3.dp, bottom = 3.dp), contentAlignment = Alignment.CenterEnd) {
        Box {
            Surface(
                color = cs.primaryContainer,
                shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                shadowElevation = 1.dp,
                modifier = Modifier.widthIn(min = 80.dp)
                    .combinedClickable(onClick = {}, onLongClick = { menu = true }),
            ) {
                Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp)) {
                    Text(rich, color = cs.onPrimaryContainer, fontSize = 15.sp, lineHeight = 21.sp)
                    Row(Modifier.align(Alignment.End).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (n.pinned) Icon(Icons.Default.PushPin, null, Modifier.size(12.dp), tint = cs.primary)
                        if (n.updatedAt - n.createdAt > 60_000) Text(" diedit", fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Text(" " + hhmm(n.createdAt), fontSize = 11.sp, color = cs.onSurfaceVariant)
                        Text(if (n.dirty) " ⏱" else " ✓", fontSize = 11.sp, color = if (n.dirty) cs.onSurfaceVariant else cs.primary)
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Salin") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { menu = false; onCopy() })
                DropdownMenuItem(text = { Text(if (n.pinned) "Lepas pin" else "Pin") }, leadingIcon = { Icon(Icons.Default.PushPin, null) }, onClick = { menu = false; onPin() })
                DropdownMenuItem(text = { Text("Bagikan") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text("Hapus") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
fun DayHeader(ts: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), shadowElevation = 1.dp) {
            Text(dayLabel(ts), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- input ----------

@Composable
fun InputBar(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    tags: List<String>,
    editing: Note?,
    onCancelEdit: () -> Unit,
    onSend: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val cursor = value.selection.start.coerceIn(0, value.text.length)
    val token = value.text.substring(0, cursor).takeLastWhile { !it.isWhitespace() }
    val suggestions = if (token.startsWith("#")) {
        val q = token.drop(1).lowercase()
        tags.filter { it.startsWith(q) && it != q }.take(12)
    } else emptyList()

    fun insertTag(t: String) {
        val start = cursor - token.length
        val newText = value.text.substring(0, start) + "#$t " + value.text.substring(cursor)
        onChange(TextFieldValue(newText, TextRange(start + t.length + 2)))
    }

    Column(Modifier.background(cs.surface).navigationBarsPadding().imePadding()) {
        if (editing != null) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = cs.primary)
                Text("  Edit catatan", Modifier.weight(1f), color = cs.primary, fontSize = 13.sp)
                IconButton(onClick = onCancelEdit) { Icon(Icons.Default.Close, "Batal") }
            }
        }
        if (suggestions.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                items(suggestions, key = { it }) { t -> AssistChip(onClick = { insertTag(t) }, label = { Text("#$t") }) }
            }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Bottom) {
            TextField(
                value = value, onValueChange = onChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Catat ide / tempel link…  #tag") },
                maxLines = 6,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                leadingIcon = {
                    IconButton(onClick = {
                        val pre = if (cursor > 0 && !value.text[cursor - 1].isWhitespace()) " #" else "#"
                        onChange(TextFieldValue(value.text.substring(0, cursor) + pre + value.text.substring(cursor), TextRange(cursor + pre.length)))
                    }) { Icon(Icons.Default.Tag, "Tag", tint = cs.primary) }
                },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = cs.secondaryContainer, unfocusedContainerColor = cs.secondaryContainer,
                ),
            )
            Spacer(Modifier.width(6.dp))
            FilledIconButton(onClick = onSend, enabled = value.text.isNotBlank(), modifier = Modifier.size(54.dp)) {
                Icon(if (editing != null) Icons.Default.Check else Icons.AutoMirrored.Filled.Send, "Kirim")
            }
        }
    }
}

// ---------- settings ----------

@Composable
fun SettingsDialog(vm: NotesVm, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(vm.prefs.url) }
    var token by remember { mutableStateOf(vm.prefs.token) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Sync Cloud") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("URL Worker") },
                    placeholder = { Text("https://catatku-api.xxx.workers.dev") }, singleLine = true)
                OutlinedTextField(token, { token = it }, label = { Text("Token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                TextButton(onClick = {
                    scope.launch {
                        val json = vm.exportJson()
                        val s = Intent(Intent.ACTION_SEND).setType("application/json")
                            .putExtra(Intent.EXTRA_SUBJECT, "catatku-backup.json")
                            .putExtra(Intent.EXTRA_TEXT, json)
                        ctx.startActivity(Intent.createChooser(s, "Export"))
                    }
                }) { Text("Export semua catatan (JSON)") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.prefs.url = url
                vm.prefs.token = token
                vm.prefs.lastSync = 0 // tarik ulang semua
                vm.sync()
                onClose()
            }) { Text("Simpan & Sync") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Nanti") } },
    )
}

// ---------- date utils ----------

private val ID = Locale("id", "ID")
private fun hhmm(ts: Long) = SimpleDateFormat("HH:mm", ID).format(Date(ts))
private fun dayKey(ts: Long) = SimpleDateFormat("yyyyMMdd", ID).format(Date(ts))
private fun dayLabel(ts: Long): String {
    val c = Calendar.getInstance()
    val today = dayKey(c.timeInMillis)
    c.add(Calendar.DAY_OF_YEAR, -1)
    val yest = dayKey(c.timeInMillis)
    return when (dayKey(ts)) {
        today -> "Hari ini"
        yest -> "Kemarin"
        else -> SimpleDateFormat("EEEE, d MMM yyyy", ID).format(Date(ts))
    }
}
