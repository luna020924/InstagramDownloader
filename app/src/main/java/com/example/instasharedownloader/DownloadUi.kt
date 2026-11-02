@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.instasharedownloader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

@Composable
internal fun HomeScreen(
    input: TextFieldState,
    connected: Boolean,
    clipboardText: () -> String,
    onResolve: (String) -> Unit,
    onLogin: () -> Unit,
    onOpenService: (String) -> Unit,
) {
    var invalid by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    LaunchedEffect(input.text.toString()) { invalid = false }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    fun submit() {
        val value = input.text.toString()
        invalid = MediaExtractor.extractSupportedUrl(value) == null
        if (!invalid) { focus.clearFocus(); onResolve(value) }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val expanded = maxWidth >= 700.dp && LocalDensity.current.fontScale < 1.4f
            Column(Modifier.fillMaxSize().widthIn(max = 1120.dp).align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = if (expanded) 40.dp else 24.dp)) {
                Row(Modifier.padding(top = if (keyboardOpen) 8.dp else 20.dp,
                    bottom = if (keyboardOpen) 24.dp else if (expanded) 60.dp else 44.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Download, null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text("공유 저장", Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Column(Modifier.weight(1f)) {
                        if (!keyboardOpen) {
                            Text("링크로 저장", style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("Instagram · X", Modifier.padding(top = 12.dp, bottom = 28.dp),
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(state = input, modifier = Modifier.fillMaxWidth().testTag("link-input"),
                            label = { Text("게시물 또는 스토리 링크") },
                            placeholder = { Text("https://…") }, isError = invalid,
                            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 3),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            onKeyboardAction = { submit() },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val text = clipboardText()
                                    if (text.isNotBlank()) { input.setTextAndPlaceCursorAtEnd(text); invalid = false }
                                }) { Icon(Icons.Outlined.ContentPaste, "링크 붙여넣기") }
                            },
                            supportingText = if (invalid) ({ Text("Instagram 또는 X의 게시물 링크를 입력해 주세요.") }) else null,
                            shape = RoundedCornerShape(16.dp))
                        Button(onClick = { submit() }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                            .heightIn(min = 56.dp).testTag("resolve-link"), shape = RoundedCornerShape(16.dp)) {
                            Text("링크 불러오기", style = MaterialTheme.typography.titleSmall)
                        }
                        FlowRow(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            TextButton(onClick = { onOpenService("https://www.instagram.com/") }) {
                                Text("Instagram 열기"); Icon(Icons.AutoMirrored.Outlined.OpenInNew, null,
                                    Modifier.padding(start = 6.dp).size(16.dp))
                            }
                            TextButton(onClick = { onOpenService("https://x.com/") }) {
                                Text("X 열기"); Icon(Icons.AutoMirrored.Outlined.OpenInNew, null,
                                    Modifier.padding(start = 6.dp).size(16.dp))
                            }
                        }
                        if (!expanded) HomeDetails(connected, onLogin, Modifier.padding(top = 36.dp))
                    }
                    if (expanded) HomeDetails(connected, onLogin, Modifier.width(248.dp).padding(top = 4.dp))
                }
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun HomeDetails(connected: Boolean, onLogin: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("공유 메뉴에서 바로 저장", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Instagram이나 X에서 공유를 누른 뒤\nInsta Share Downloader를 선택하세요.",
            Modifier.padding(top = 10.dp, bottom = 28.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().clickable(onClick = onLogin).padding(vertical = 20.dp)
            .semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AccountCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Instagram 계정", style = MaterialTheme.typography.titleSmall)
                Text(if (connected) "연결됨 · 로그인 관리" else "스토리 저장을 위해 로그인", Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                Text("저장 위치", style = MaterialTheme.typography.labelLarge)
                Text("갤러리의 InstaShareDownloader 앨범", Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun WorkScreen(
    state: DownloadState, onClose: () -> Unit, onToggle: (Int) -> Unit, onSelectAll: () -> Unit,
    onSave: () -> Unit, onSharedStory: () -> Unit, onAllStories: () -> Unit,
    onRetry: () -> Unit, onLogin: () -> Unit, onOpenSaved: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(state.sourceName.ifBlank { "공유 저장" }) },
            navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "돌아가기") } })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            WorkPanel(state, Modifier.fillMaxHeight().widthIn(max = 920.dp).fillMaxWidth(), onToggle,
                onSelectAll, onSave, onSharedStory, onAllStories, onRetry, onLogin, onOpenSaved, onClose)
        }
    }
}

@Composable
internal fun SharePanel(
    state: DownloadState, onDismiss: () -> Unit, onToggle: (Int) -> Unit, onSelectAll: () -> Unit,
    onSave: () -> Unit, onSharedStory: () -> Unit, onAllStories: () -> Unit,
    onRetry: () -> Unit, onLogin: () -> Unit, onOpenSaved: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = if (state.phase == DownloadPhase.SELECTING) maxHeight * .88f
            else (maxHeight * .72f).coerceAtMost(480.dp)
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, sheetMaxWidth = 640.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = null, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.height(height).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(state.sourceName.ifBlank { "공유 저장" }, Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onDismiss) { Icon(Icons.Outlined.Close, "닫기") }
                }
                WorkPanel(state, Modifier.weight(1f), onToggle, onSelectAll, onSave, onSharedStory,
                    onAllStories, onRetry, onLogin, onOpenSaved, onDismiss)
            }
        }
    }
}

@Composable
internal fun WorkPanel(
    state: DownloadState, modifier: Modifier = Modifier,
    onToggle: (Int) -> Unit, onSelectAll: () -> Unit, onSave: () -> Unit,
    onSharedStory: () -> Unit, onAllStories: () -> Unit, onRetry: () -> Unit,
    onLogin: () -> Unit, onOpenSaved: () -> Unit, onClose: () -> Unit,
) {
    when (state.phase) {
        DownloadPhase.SELECTING -> SelectionPanel(state, modifier, onToggle, onSelectAll, onSave)
        DownloadPhase.STORY_SCOPE -> StateLayout(modifier) {
            StateIcon(Icons.Outlined.Collections)
            StateTitle("어떤 스토리를 저장할까요?")
            Text("현재 볼 수 있는 스토리 ${state.items.size}개", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(28.dp))
            PrimaryAction("공유한 스토리만 저장", onSharedStory)
            OutlinedButton(onAllStories, Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)) { Text("다른 스토리도 선택") }
        }
        DownloadPhase.RESOLVING, DownloadPhase.RENDERING -> StateLayout(modifier) {
            CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
            StateTitle("항목을 불러오는 중")
            Text("잠시만 기다려 주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DownloadPhase.SAVING -> StateLayout(modifier) {
            StateIcon(Icons.Outlined.Download)
            StateTitle("갤러리에 저장 중")
            Text("${state.savedCount} / ${state.totalCount}개", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(progress = { if (state.totalCount > 0) state.savedCount.toFloat() / state.totalCount else 0f },
                modifier = Modifier.fillMaxWidth())
            TextButton(onClose, Modifier.padding(top = 12.dp)) { Text("저장 중에 닫기") }
        }
        DownloadPhase.DONE -> StateLayout(modifier) {
            StateIcon(Icons.Outlined.Check)
            StateTitle("${state.savedCount}개 저장했어요")
            Text("갤러리의 InstaShareDownloader 앨범에서 볼 수 있어요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            PrimaryAction("저장한 항목 보기", onOpenSaved)
            TextButton(onClose, Modifier.fillMaxWidth()) { Text("완료") }
        }
        DownloadPhase.ERROR -> StateLayout(modifier) {
            StateIcon(Icons.Outlined.ErrorOutline, error = true)
            StateTitle(when {
                state.retrySaving && state.savedCount > 0 -> "${state.savedCount}개 저장 후 중단됐어요"
                state.retrySaving -> "저장하지 못했어요"
                else -> "불러오지 못했어요"
            })
            Text(state.message, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Spacer(Modifier.height(28.dp))
            if (state.needsLogin) PrimaryAction("Instagram 로그인", onLogin)
            if (state.sourceUrl.isNotBlank()) {
                if (state.needsLogin) TextButton(onRetry, Modifier.fillMaxWidth()) { Text("다시 시도") }
                else PrimaryAction(if (state.retrySaving) "남은 ${state.selected.size}개 다시 저장" else "다시 시도", onRetry)
            }
            TextButton(onClose, Modifier.fillMaxWidth()) { Text("돌아가기") }
        }
        DownloadPhase.IDLE -> Unit
    }
}

@Composable
private fun SelectionPanel(state: DownloadState, modifier: Modifier, onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit, onSave: () -> Unit) {
    val gridState = rememberLazyGridState()
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("저장할 항목 선택", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("전체 ${state.items.size}개", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onSelectAll, Modifier.testTag("select-all")) {
                    Text(if (state.selected.size == state.items.size) "선택 해제" else "전체 선택")
                }
            }
        }
        LazyVerticalGrid(columns = GridCells.Adaptive(100.dp), state = gridState,
            modifier = Modifier.weight(1f).testTag("media-grid"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(state.items, key = { index, _ -> index }) { index, item ->
                MediaTile(item, index, index in state.selected) { onToggle(index) }
            }
        }
        Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Button(onSave, enabled = state.selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("save-selected"),
                    shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(20.dp))
                    Text(if (state.selected.isEmpty()) "저장할 항목을 선택하세요" else "${state.selected.size}개 저장",
                        Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, index: Int, selected: Boolean, onToggle: () -> Unit) {
    val kind = if (item.kind == MediaItem.Kind.VIDEO) "동영상" else "사진"
    val context = LocalContext.current
    val request = remember(item.thumbnailUrl) {
        ImageRequest.Builder(context).data(item.thumbnailUrl).httpHeaders(NetworkHeaders.Builder()
            .set("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36")
            .set("Referer", item.referer).build()).build()
    }
    var failed by remember(item.thumbnailUrl) { mutableStateOf(false) }
    Surface(modifier = Modifier.aspectRatio(0.82f).clip(RoundedCornerShape(12.dp))
        .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
        .semantics { contentDescription = "${index + 1}번째 $kind"; this.selected = selected;
            stateDescription = if (selected) "선택됨" else "선택 안 됨" }
        .testTag("media-$index"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
        Box(Modifier.fillMaxSize()) {
            Icon(if (item.kind == MediaItem.Kind.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.Image,
                null, Modifier.align(Alignment.Center).size(28.dp), tint = MaterialTheme.colorScheme.outline)
            AsyncImage(model = request, contentDescription = null, onError = { failed = true },
                onSuccess = { failed = false },
                modifier = Modifier.fillMaxSize().padding(if (selected) 3.dp else 0.dp)
                    .clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Crop)
            if (failed) Text("미리보기 없음", Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp), shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = .5f),
                border = BorderStroke(1.5.dp, Color.White)) {
                if (selected) Icon(Icons.Outlined.Check, null, Modifier.padding(4.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            if (item.kind == MediaItem.Kind.VIDEO) Surface(Modifier.align(Alignment.BottomStart).padding(8.dp),
                shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = .65f)) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.padding(3.dp).size(18.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun StateLayout(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun StateTitle(title: String) {
    Text(title, Modifier.padding(top = 12.dp, bottom = 4.dp),
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StateIcon(icon: ImageVector, error: Boolean = false) {
    Icon(icon, null, Modifier.size(32.dp), tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}
