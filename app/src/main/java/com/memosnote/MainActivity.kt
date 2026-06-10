package com.memosnote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memosnote.data.*
import com.memosnote.ui.theme.*
import com.memosnote.util.MarkdownRenderer
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val prefs = context.getSharedPreferences("memos_prefs", Context.MODE_PRIVATE)
            var followSystem by remember { mutableStateOf(prefs.getBoolean("follow_system", true)) }
            var manualDark by remember { mutableStateOf(prefs.getBoolean("manual_dark", false)) }
            val systemDark = isSystemInDarkTheme()
            val isDark = if (followSystem) systemDark else manualDark

            MemosNoteTheme(darkTheme = isDark) {
                MemosNoteApp(
                    isDark = isDark,
                    onToggleTheme = {
                        if (followSystem) {
                            followSystem = false
                            manualDark = !systemDark
                        } else if (manualDark == systemDark) {
                            followSystem = true
                        } else {
                            manualDark = !manualDark
                        }
                        prefs.edit()
                            .putBoolean("follow_system", followSystem)
                            .putBoolean("manual_dark", manualDark)
                            .apply()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemosNoteApp(isDark: Boolean, onToggleTheme: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { MemoRepository(context) }
    val fileHistoryManager = remember { FileHistoryManager(context) }
    val prefs = context.getSharedPreferences("memos_prefs", Context.MODE_PRIVATE)

    var memos by remember { mutableStateOf(listOf<Memo>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var showFileMenu by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showInitDialog by remember { mutableStateOf(false) }
    var currentFileName by remember { mutableStateOf(repository.getCurrentFileName()) }
    var memoToDelete by remember { mutableStateOf<Memo?>(null) }
    var editingMemoId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // 用于触发滚动到顶部的状态
    var scrollToTopTrigger by remember { mutableStateOf(0) }

    // 滚动到顶部的效果
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }

    // 加载笔记
    fun loadMemosFromCurrentFile() {
        memos = repository.loadMemos()
        currentFileName = repository.getCurrentFileName()
    }

    // 首次加载 - 检查是否需要初始化引导
    LaunchedEffect(Unit) {
        if (repository.hasCurrentFile()) {
            loadMemosFromCurrentFile()
        } else {
            showInitDialog = true
        }
    }

    val filteredMemos = remember(memos, searchQuery) {
        if (searchQuery.isBlank()) memos
        else memos.filter { it.content.contains(searchQuery, ignoreCase = true) }
    }

    fun saveMemos(newMemos: List<Memo>, scrollToTop: Boolean = false) {
        memos = newMemos
        repository.saveMemos(newMemos)
        if (scrollToTop) {
            scrollToTopTrigger++ // 只有新增时才触发滚动到顶部
        }
    }

    fun openFile(uri: Uri) {
        repository.setCurrentFile(uri)
        fileHistoryManager.addToHistory(uri.toString(), repository.getCurrentFileName())
        prefs.edit().putString("current_file_uri", uri.toString()).apply()
        loadMemosFromCurrentFile()
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            openFile(it)
        }
    }

    // 创建新文件
    val createFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            openFile(it)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                isDark = isDark,
                isSearchOpen = isSearchOpen,
                searchQuery = searchQuery,
                currentFileName = currentFileName,
                onToggleTheme = onToggleTheme,
                onToggleSearch = {
                    isSearchOpen = !isSearchOpen
                    if (!isSearchOpen) searchQuery = ""
                },
                onSearchQueryChange = { searchQuery = it },
                onShowFileMenu = { showFileMenu = true }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Memo Input
            MemoInput(
                isDark = isDark,
                onSubmit = { content ->
                    val newMemo = Memo(
                        id = Memo.generateId(),
                        content = content,
                        createdAt = Date()
                    )
                    saveMemos(listOf(newMemo) + memos, scrollToTop = true)
                },
                onFocus = {
                    if (isSearchOpen) {
                        isSearchOpen = false
                        searchQuery = ""
                    }
                }
            )

            // Memo List
            if (filteredMemos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "搜索无结果" else "还没有笔记，写点什么吧...",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(filteredMemos, key = { _, memo -> memo.id }) { index, memo ->
                        // 确保相邻卡片颜色不同：基于索引循环选择，避开与前一个相同
                        val colorIndex = index % 10
                        SwipeableMemoCard(
                            memo = memo,
                            colorIndex = colorIndex,
                            isDark = isDark,
                            isEditing = editingMemoId == memo.id,
                            onEditStart = { editingMemoId = memo.id },
                            onEditEnd = { editingMemoId = null },
                            onEdit = { newContent ->
                                val updated = memos.map {
                                    if (it.id == memo.id) it.copy(content = newContent, updatedAt = Date()) else it
                                }
                                saveMemos(updated)
                            },
                            onDeleteRequest = { memoToDelete = memo },
                            onTagClick = { tag ->
                                searchQuery = "#$tag"
                                isSearchOpen = true
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // 删除确认对话框
    memoToDelete?.let { memo ->
        AlertDialog(
            onDismissRequest = { memoToDelete = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条笔记吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        saveMemos(memos.filter { it.id != memo.id })
                        memoToDelete = null
                    }
                ) {
                    Text(
                        "删除",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { memoToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 文件菜单
    if (showFileMenu) {
        FileMenuDialog(
            onDismiss = { showFileMenu = false },
            onOpenFile = {
                showFileMenu = false
                filePickerLauncher.launch(arrayOf("text/markdown", "text/plain", "*/*"))
            },
            onCreateFile = {
                showFileMenu = false
                createFileLauncher.launch("memos.md")
            },
            onShowHistory = {
                showFileMenu = false
                showHistoryDialog = true
            }
        )
    }

    // 历史文件对话框
    if (showHistoryDialog) {
        HistoryDialog(
            fileHistoryManager = fileHistoryManager,
            onDismiss = { showHistoryDialog = false },
            onSelectFile = { recentFile ->
                try {
                    val uri = Uri.parse(recentFile.uri)
                    openFile(uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                showHistoryDialog = false
            }
        )
    }

    // 初始化引导对话框
    if (showInitDialog) {
        InitFileDialog(
            onDismiss = { showInitDialog = false },
            onOpenFile = {
                showInitDialog = false
                filePickerLauncher.launch(arrayOf("text/markdown", "text/plain", "*/*"))
            },
            onCreateFile = {
                showInitDialog = false
                createFileLauncher.launch("memos.md")
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    isDark: Boolean,
    isSearchOpen: Boolean,
    searchQuery: String,
    currentFileName: String,
    onToggleTheme: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onShowFileMenu: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    // 搜索打开时自动聚焦
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            kotlinx.coroutines.delay(100) // 等待动画完成
            focusRequester.requestFocus()
        }
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        title = {
            if (isSearchOpen) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("搜索笔记...", fontSize = 14.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
            } else {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "M",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Memos Note",
                            fontSize = 18.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = currentFileName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 42.dp)
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onShowFileMenu) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = "文件",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleSearch) {
                Icon(
                    if (isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleTheme) {
                Icon(
                    if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    contentDescription = "Theme",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
fun FileMenuDialog(
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit,
    onCreateFile: () -> Unit,
    onShowHistory: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件管理") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("打开文件") },
                    leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenFile() }
                )
                ListItem(
                    headlineContent = { Text("新建文件") },
                    leadingContent = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    modifier = Modifier.clickable { onCreateFile() }
                )
                ListItem(
                    headlineContent = { Text("历史文件") },
                    leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                    modifier = Modifier.clickable { onShowHistory() }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun InitFileDialog(
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit,
    onCreateFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* 不允许点击外部关闭 */ },
        title = { Text("欢迎使用 Memos Note") },
        text = {
            Column {
                Text(
                    "请选择一个 Markdown 文件来存储您的笔记，或创建一个新文件。",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                ListItem(
                    headlineContent = { Text("打开现有文件") },
                    leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenFile() }
                )
                ListItem(
                    headlineContent = { Text("创建新文件") },
                    leadingContent = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    modifier = Modifier.clickable { onCreateFile() }
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
fun HistoryDialog(
    fileHistoryManager: FileHistoryManager,
    onDismiss: () -> Unit,
    onSelectFile: (RecentFile) -> Unit
) {
    val recentFiles = remember { fileHistoryManager.getRecentFiles() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("最近打开的文件") },
        text = {
            if (recentFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("没有历史记录", color = MaterialTheme.colorScheme.tertiary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(recentFiles) { file ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectFile(file) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = file.getFormattedDate(),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
fun MemoInput(isDark: Boolean, onSubmit: (String) -> Unit, onFocus: () -> Unit = {}) {
    var content by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = {
                    Text(
                        "写点什么...",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp, max = 160.dp)
                    .focusRequester(focusRequester),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.03f),
                    unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.02f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }.also { interactionSource ->
                    LaunchedEffect(interactionSource) {
                        interactionSource.interactions.collect { interaction ->
                            if (interaction is androidx.compose.foundation.interaction.FocusInteraction.Focus) {
                                onFocus()
                            }
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "支持 Markdown 语法",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Button(
                    onClick = {
                        if (content.isNotBlank()) {
                            onSubmit(content.trim())
                            content = ""
                        }
                    },
                    enabled = content.isNotBlank(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("发送", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SwipeableMemoCard(
    memo: Memo,
    colorIndex: Int,
    isDark: Boolean,
    isEditing: Boolean,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleteRequest: () -> Unit,
    onTagClick: (String) -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var initialDirection by remember { mutableStateOf(0) } // 0: 未开始, 1: 右滑, -1: 左滑
    var hasChangedDirection by remember { mutableStateOf(false) }

    val cardColors = if (isDark) CardColorsDark else CardColorsLight
    val cardColor = cardColors[colorIndex % cardColors.size]

    // 滑动阈值
    val swipeThreshold = 80f
    val actionThreshold = 150f

    // 动画回到原位
    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "offset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        // 背景层 - 使用 Box + matchParentSize 确保与前景层高度一致
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    when {
                        hasChangedDirection -> Color(0xFF9E9E9E) // 方向改变时显示灰色（取消）
                        offsetX > swipeThreshold -> Color(0xFF4CAF50)
                        offsetX < -swipeThreshold -> MaterialTheme.colorScheme.error
                        else -> Color.Transparent
                    }
                )
        ) {
            // 显示当前状态的图标
            when {
                hasChangedDirection -> {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "取消",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                    )
                }
                offsetX > swipeThreshold -> {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 20.dp)
                            .size(24.dp)
                    )
                }
                offsetX < -swipeThreshold -> {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 20.dp)
                            .size(24.dp)
                    )
                }
            }
        }

        // 前景层 - 卡片内容
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            // 重置状态
                            initialDirection = 0
                            hasChangedDirection = false
                        },
                        onDragEnd = {
                            when {
                                hasChangedDirection -> {
                                    // 方向改变了，取消操作
                                    offsetX = 0f
                                }
                                offsetX > actionThreshold -> {
                                    offsetX = 0f
                                    onEditStart()
                                }
                                offsetX < -actionThreshold -> {
                                    offsetX = 0f
                                    onDeleteRequest()
                                }
                                else -> {
                                    offsetX = 0f
                                }
                            }
                            initialDirection = 0
                            hasChangedDirection = false
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = (offsetX + dragAmount).coerceIn(-200f, 200f)

                            // 检测方向变化
                            if (initialDirection == 0 && abs(newOffset) > 20f) {
                                // 记录初始方向
                                initialDirection = if (newOffset > 0) 1 else -1
                            } else if (initialDirection != 0 && !hasChangedDirection) {
                                // 检查是否改变了方向（跨过了中心点）
                                if (initialDirection == 1 && newOffset < -20f) {
                                    hasChangedDirection = true
                                } else if (initialDirection == -1 && newOffset > 20f) {
                                    hasChangedDirection = true
                                }
                            }

                            offsetX = newOffset
                        }
                    )
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            MemoCardContent(
                memo = memo,
                isDark = isDark,
                isEditing = isEditing,
                onEdit = {
                    onEdit(it)
                    onEditEnd()
                },
                onCancelEdit = onEditEnd,
                onTagClick = onTagClick
            )
        }
    }
}

@Composable
fun MemoCardContent(
    memo: Memo,
    isDark: Boolean,
    isEditing: Boolean,
    onEdit: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onTagClick: (String) -> Unit
) {
    var editContent by remember(memo.id) { mutableStateOf(memo.content) }
    var isExpanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val isLong = memo.content.split("\n").size > 7 || memo.content.length > 400

    Column(modifier = Modifier.padding(14.dp)) {
        // Header: date
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                Memo.formatDate(memo.createdAt),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isEditing) {
            // Edit mode
            OutlinedTextField(
                value = editContent,
                onValueChange = { editContent = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 240.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.7f),
                    unfocusedContainerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.5f)
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            // 修复：按钮在同一行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onCancelEdit,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("取消", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (editContent.isNotBlank()) {
                            onEdit(editContent.trim())
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("保存", fontSize = 13.sp)
                }
            }
        } else {
            // View mode - rendered markdown
            val displayContent = if (!isExpanded && isLong) {
                val lines = memo.content.split("\n")
                if (lines.size > 7) lines.take(7).joinToString("\n") + "..."
                else if (memo.content.length > 400) memo.content.take(400) + "..."
                else memo.content
            } else {
                memo.content
            }

            val renderedLines = MarkdownRenderer.renderMarkdown(displayContent, isDark)

            Column {
                for (line in renderedLines) {
                    when {
                        line.isCodeBlock -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isDark) Color(0xFF2A2826) else Color(0xFFF5F2EE)
                                    )
                                    .padding(10.dp)
                            ) {
                                ClickableText(
                                    text = line.text,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    onClick = {}
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        line.isBlockquote -> {
                            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                AnnotatedTextWithTags(
                                    text = line.text,
                                    isDark = isDark,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    ),
                                    onTagClick = onTagClick,
                                    onUrlClick = { url ->
                                        try { uriHandler.openUri(url) } catch (_: Exception) {}
                                    }
                                )
                            }
                        }
                        line.isTodoChecked != null -> {
                            Row(
                                modifier = Modifier.padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (line.isTodoChecked) Icons.Default.CheckBox
                                    else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (line.isTodoChecked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                AnnotatedTextWithTags(
                                    text = line.text,
                                    isDark = isDark,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    onTagClick = onTagClick,
                                    onUrlClick = { url ->
                                        try { uriHandler.openUri(url) } catch (_: Exception) {}
                                    }
                                )
                            }
                        }
                        line.isListItem -> {
                            Row(
                                modifier = Modifier.padding(vertical = 1.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    "•",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                AnnotatedTextWithTags(
                                    text = line.text,
                                    isDark = isDark,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    onTagClick = onTagClick,
                                    onUrlClick = { url ->
                                        try { uriHandler.openUri(url) } catch (_: Exception) {}
                                    }
                                )
                            }
                        }
                        line.headingLevel > 0 -> {
                            // 标题渲染：根据级别设置不同字体大小
                            val headingFontSize = when (line.headingLevel) {
                                1 -> 24.sp
                                2 -> 22.sp
                                3 -> 20.sp
                                4 -> 18.sp
                                5 -> 16.sp
                                else -> 14.sp
                            }
                            AnnotatedTextWithTags(
                                text = line.text,
                                isDark = isDark,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = headingFontSize,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = headingFontSize * 1.3
                                ),
                                modifier = Modifier.padding(vertical = 4.dp),
                                onTagClick = onTagClick,
                                onUrlClick = { url ->
                                    try { uriHandler.openUri(url) } catch (_: Exception) {}
                                }
                            )
                        }
                        else -> {
                            if (line.text.text.isNotEmpty()) {
                                AnnotatedTextWithTags(
                                    text = line.text,
                                    isDark = isDark,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 22.sp
                                    ),
                                    onTagClick = onTagClick,
                                    onUrlClick = { url ->
                                        try { uriHandler.openUri(url) } catch (_: Exception) {}
                                    }
                                )
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // Expand/Collapse button
            if (isLong) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isExpanded) "收起" else "展开",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { isExpanded = !isExpanded }
                )
            }
        }
    }
}

private fun handleTextClick(
    text: AnnotatedString,
    offset: Int,
    onTagClick: (String) -> Unit,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    text.getStringAnnotations("tag", offset, offset).firstOrNull()?.let {
        onTagClick(it.item)
        return
    }
    text.getStringAnnotations("url", offset, offset).firstOrNull()?.let {
        try { uriHandler.openUri(it.item) } catch (_: Exception) {}
    }
}

/**
 * 渲染带圆角标签的文本行
 * 解析 AnnotatedString 中的 tag 注释，将标签单独渲染为圆角样式
 */
@Composable
fun AnnotatedTextWithTags(
    text: AnnotatedString,
    isDark: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    onTagClick: (String) -> Unit,
    onUrlClick: (String) -> Unit
) {
    val tagAnnotations = text.getStringAnnotations("tag", 0, text.length)
    val urlAnnotations = text.getStringAnnotations("url", 0, text.length)

    if (tagAnnotations.isEmpty() && urlAnnotations.isEmpty()) {
        // 没有标签或链接，直接渲染
        Text(text = text, style = style, modifier = modifier)
        return
    }

    // 合并所有特殊区域并排序
    data class SpanInfo(val start: Int, val end: Int, val type: String, val value: String)
    val spans = mutableListOf<SpanInfo>()
    tagAnnotations.forEach { spans.add(SpanInfo(it.start, it.end, "tag", it.item)) }
    urlAnnotations.forEach { spans.add(SpanInfo(it.start, it.end, "url", it.item)) }
    spans.sortBy { it.start }

    val colors = MarkdownRenderer.getTagColors(isDark)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.wrapContentSize()
    ) {
        var lastEnd = 0
        for (span in spans) {
            // 渲染普通文本
            if (span.start > lastEnd) {
                Text(
                    text = text.subSequence(lastEnd, span.start),
                    style = style
                )
                Spacer(modifier = Modifier.width(2.dp))
            }

            // 渲染标签或链接
            when (span.type) {
                "tag" -> {
                    val colorIdx = MarkdownRenderer.getTagColorIndex(span.value)
                    val (bg, fg) = colors[colorIdx]
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(bg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clickable { onTagClick(span.value) }
                    ) {
                        Text(
                            text = "#${span.value}",
                            fontSize = 12.sp,
                            color = fg
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                }
                "url" -> {
                    Text(
                        text = text.subSequence(span.start, span.end),
                        style = style.copy(
                            color = if (isDark) Color(0xFF8BAACC) else Color(0xFF6B8AAC),
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        ),
                        modifier = Modifier.clickable { onUrlClick(span.value) }
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
            }
            lastEnd = span.end
        }

        // 渲染剩余的普通文本
        if (lastEnd < text.length) {
            Text(
                text = text.subSequence(lastEnd, text.length),
                style = style
            )
        }
    }
}
