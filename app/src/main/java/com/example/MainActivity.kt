package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LeakAdd
import androidx.compose.material.icons.filled.Output
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AssetEntity
import com.example.ui.AssetViewModel
import com.example.ui.components.CameraPreview
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

class MainActivity : ComponentActivity() {
    private val viewModel: AssetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold")
                ) { innerPadding ->
                    AssetScannerApp(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AssetScannerApp(
    viewModel: AssetViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val assets by viewModel.filteredAssets.collectAsState()
    val rawAllAssets by viewModel.allAssets.collectAsState()
    val isScanning by viewModel.isScanningActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedCategoryFilter.collectAsState()
    val selectedSortOrder by viewModel.selectedSortOrder.collectAsState()
    val editingAsset by viewModel.editingAsset.collectAsState()
    val lastScanned by viewModel.lastScannedTag.collectAsState()

    var showManualAddDialog by remember { mutableStateOf(false) }
    var showAllDataDialog by remember { mutableStateOf(false) }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Last scanned notification banner/toast handling
    LaunchedEffect(lastScanned) {
        lastScanned?.let { tag ->
            Toast.makeText(context, "자산 스캔됨: $tag", Toast.LENGTH_SHORT).show()
            viewModel.clearLastScannedTag()
        }
    }

    // Samsung Notes export flow helper
    fun exportToNotes(allText: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, allText)
        }

        // Try specifically targeting Samsung Notes if installed
        val samsungNotesPackage = "com.samsung.android.app.notes"
        val pm = context.packageManager
        val isSamsungNotesInstalled = try {
            pm.getPackageInfo(samsungNotesPackage, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: Exception) {
            false
        }

        if (isSamsungNotesInstalled) {
            intent.setPackage(samsungNotesPackage)
            try {
                context.startActivity(intent)
                Toast.makeText(context, "삼성노트로 전송하는 중...", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                // fallback to general sharing
            }
        }

        // Clip data standard fallback
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Asset Sticky List", allText)
        clipboard.setPrimaryClip(clip)

        // General share Intent chooser
        val chooser = Intent.createChooser(intent, "자산 스티커 목록 내보내기")
        context.startActivity(chooser)
        Toast.makeText(context, "자산 목록이 클립보드에 복사되었으며, 공유 창이 열립니다.", Toast.LENGTH_LONG).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // [HEADER]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "스캐너 로고",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = "스마트 자산 스캐너",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isScanning) "실시간 자동 탐지 가동 중..." else "스캔 일시정지됨",
                        fontSize = 11.sp,
                        color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Pause/Resume Scammer Button
                IconButton(
                    onClick = { viewModel.setScanningActive(!isScanning) },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isScanning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(
                        contentColor = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isScanning) "스캔 정지" else "스캔 시작",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Manual Add Button
                IconButton(
                    onClick = { showManualAddDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "수동 자산 추가",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // [MAIN SCAN SECTION]
        if (cameraPermissionState.status.isGranted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
                    .background(Color.Black)
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    isScanningActive = isScanning,
                    onTagDetected = { tag ->
                        viewModel.onTagScanned(tag) { text ->
                            // Custom feedback already handled by Toast launch effects
                        }
                    }
                )

                // Laser aim guide frame (xx0000000000)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .height(64.dp)
                            .border(
                                2.dp,
                                if (isScanning) MaterialTheme.colorScheme.primaryContainer else Color.Gray.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        // Scan Corner lines
                        val cornerColor = if (isScanning) MaterialTheme.colorScheme.primaryContainer else Color.Gray
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Top Left Corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .size(14.dp)
                                    .border(
                                        width = 3.dp,
                                        color = cornerColor,
                                        shape = RoundedCornerShape(topStart = 8.dp)
                                    )
                                    .padding(top = 3.dp, start = 3.dp)
                            )
                            // Top Right Corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp)
                                    .border(
                                        width = 3.dp,
                                        color = cornerColor,
                                        shape = RoundedCornerShape(topEnd = 8.dp)
                                    )
                            )
                            // Bottom Left Corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .size(14.dp)
                                    .border(
                                        width = 3.dp,
                                        color = cornerColor,
                                        shape = RoundedCornerShape(bottomStart = 8.dp)
                                    )
                            )
                            // Bottom Right Corner
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(14.dp)
                                    .border(
                                        width = 3.dp,
                                        color = cornerColor,
                                        shape = RoundedCornerShape(bottomEnd = 8.dp)
                                    )
                            )

                            // Horizonal Aim light laser
                            if (isScanning) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(Color.Red.copy(alpha = 0.7f))
                                        .align(Alignment.Center)
                                )
                            }
                        }
                    }
                }

                // Subtitle Overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "영문 2자리 + 숫자 10자리 자산스티커 자동 인식",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        } else {
            // Permission Request State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Gray.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "권한 경고",
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "스티커를 스캔하려면 카메라 권한이 필요합니다.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { cameraPermissionState.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("카메라 권한 허용", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }

        // [FILTER & SEARCH INTERFACE]
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search Input Block
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("자산 검색 (이름, 스티커 번호...)", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("search_input"),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "검색",
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "지우기",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(16.dp)
            )

            // Dynamic Category Chips Row
            var expandedCategoryMenu by remember { mutableStateOf(false) }
            val categories = listOf("전체", "미니PC", "단말기", "모니터", "데스크탑", "프린터", "노트북", "OA기타", "미정")
            
            Box(modifier = Modifier.height(52.dp)) {
                Surface(
                    onClick = { expandedCategoryMenu = true },
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = selectedFilter, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "열기", modifier = Modifier.size(12.dp).padding(start = 2.dp))
                    }
                }

                androidx.compose.material3.DropdownMenu(
                    expanded = expandedCategoryMenu,
                    onDismissRequest = { expandedCategoryMenu = false }
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category, fontSize = 13.sp) },
                            onClick = {
                                viewModel.setCategoryFilter(category)
                                expandedCategoryMenu = false
                            }
                        )
                    }
                }
            }
        }

        // [CAPTURE QUEUE & LIST]
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "자산 감지 큐 목록",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(100.dp))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${assets.size}건",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // [SORT OPTIONS ROW]
        val sortScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(sortScrollState)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Sort,
                contentDescription = "정렬 선택",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))

            // 1. 등록순 ▲ ▼
            SortButtonGroup(
                title = "등록순",
                upOrder = AssetViewModel.SortOrder.TIME_DESC,
                downOrder = AssetViewModel.SortOrder.TIME_ASC,
                selectedOrder = selectedSortOrder,
                onSelect = { viewModel.setSortOrder(it) }
            )

            // 2. 일련번호 ▲ ▼
            SortButtonGroup(
                title = "일련번호",
                upOrder = AssetViewModel.SortOrder.TAG_ASC,
                downOrder = AssetViewModel.SortOrder.TAG_DESC,
                selectedOrder = selectedSortOrder,
                onSelect = { viewModel.setSortOrder(it) }
            )

            // 3. 자산분류 ▲ ▼
            SortButtonGroup(
                title = "자산분류",
                upOrder = AssetViewModel.SortOrder.CATEGORY_ASC,
                downOrder = AssetViewModel.SortOrder.CATEGORY_DESC,
                selectedOrder = selectedSortOrder,
                onSelect = { viewModel.setSortOrder(it) }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Grid/List of Scanned Items
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            if (assets.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "비었음",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (rawAllAssets.isEmpty()) "아직 기록된 자산이 없습니다." else "일치하는 검색 결과가 없습니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = if (rawAllAssets.isEmpty()) "스티커를 카메라에 가까이 가져다 대세요!" else "검색어나 필터를 확인해보세요.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(assets, key = { it.id }) { asset ->
                        AssetQueueItem(
                            asset = asset,
                            onEditClick = { viewModel.selectAssetForEdit(asset) }
                        )
                    }
                }
            }
        }

        // [FOOTER]
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "동기화 정보",
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "삼성노트로 스마트 연동 내보내기가 지원됩니다",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // View All Summary Button
                    Button(
                        onClick = { showAllDataDialog = true },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("view_all_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "전체보기 Layout", modifier = Modifier.size(18.dp))
                            Text(
                                "전체목록",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Direct Sync to Samsung Notes Button
                    Button(
                        onClick = { exportToNotes(viewModel.getExportText(rawAllAssets, selectedSortOrder)) },
                        modifier = Modifier
                            .weight(1.8f)
                            .height(48.dp)
                            .testTag("sync_export_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Output, contentDescription = "공유", modifier = Modifier.size(18.dp))
                            Text(
                                "삼성노트로 내보내기",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // [DIALOG - MANUAL ADD]
    // [DIALOG - MANUAL ADD]
    if (showManualAddDialog) {
        var manualTag by remember { mutableStateOf("") }
        var manualMemo by remember { mutableStateOf("") }
        var isTagError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showManualAddDialog = false },
            title = { Text("자산 수동 등록") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = manualTag,
                        onValueChange = {
                            manualTag = it.uppercase()
                            isTagError = !Regex("[A-Za-z]{2}\\d{10}").matches(it.uppercase()) && !isValidSpaceFormat(it.uppercase())
                        },
                        label = { Text("자산스티커 번호 (필수입력)") },
                        isError = isTagError,
                        supportingText = {
                            val formatted = formatAssetTag(manualTag)
                            val isSpaceFormat = isValidSpaceFormat(manualTag)
                            if (isTagError) {
                                  Text("영어 2자리 + 숫자 10자리 형식이 맞지 않습니다", color = MaterialTheme.colorScheme.error)
                            } else if (isSpaceFormat) {
                                  Text("💡 치환 저장 완료 예정: $formatted", color = MaterialTheme.colorScheme.primary)
                            } else {
                                  Text("xx0000000000 규격 준수 (자동 기종 매칭)")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                    )

                    OutlinedTextField(
                        value = manualMemo,
                        onValueChange = { manualMemo = it },
                        label = { Text("상세 메모") },
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalTag = formatAssetTag(manualTag)
                        val success = viewModel.addManualAsset(
                            tag = finalTag,
                            name = "",
                            memo = manualMemo
                        )
                        if (success) {
                            // Reset values for continuous manual registration as requested
                            manualTag = ""
                            manualMemo = ""
                            Toast.makeText(context, "저장 성공! 다른 자산을 이어서 입력하세요.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "일련번호 규격을 확인해주세요.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isTagError && manualTag.isNotEmpty()
                ) {
                    Text("추가/저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualAddDialog = false }) {
                    Text("닫기")
                }
            }
        )
    }

    // [DIALOG - ALTER OR EDIT RAW DETAILS]
    editingAsset?.let { asset ->
        var editName by remember { mutableStateOf(asset.name) }
        var editMemo by remember { mutableStateOf(asset.memo) }

        AlertDialog(
            onDismissRequest = { viewModel.selectAssetForEdit(null) },
            title = { Text("자산 상세 설정") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val automaticCategory = viewModel.getCategoryByTag(asset.tag)
                    Text(
                        text = "스티커 고유 식별: ${asset.tag}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "자동 매칭 기종: $automaticCategory",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("자산 명칭") },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editMemo,
                        onValueChange = { editMemo = it },
                        label = { Text("상세 메모") },
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            viewModel.deleteAsset(asset)
                            Toast.makeText(context, "자산 파기 삭제 완료.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "삭제")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("삭제")
                    }

                    Button(
                        onClick = {
                            viewModel.updateAssetDetails(
                                asset.copy(
                                    name = editName,
                                    category = viewModel.getCategoryByTag(asset.tag),
                                    memo = editMemo
                                )
                            )
                            Toast.makeText(context, "수정되었습니다.", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("저장 및 적용")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.selectAssetForEdit(null) }) {
                    Text("취소")
                }
            }
        )
    }

    // [DIALOG - VIEW RAW EXPORT TEXT]
    if (showAllDataDialog) {
        val exportText = viewModel.getExportText(rawAllAssets, selectedSortOrder)
        AlertDialog(
            onDismissRequest = { showAllDataDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "자산 대장")
                    Text("전체 기기로그 대장 (${selectedSortOrder.label})")
                }
            },
            text = {
                Column(modifier = Modifier.height(280.dp)) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        item {
                            Text(
                                text = exportText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "위 텍스트가 삼성노트에 바로 기입됩니다.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("All Asset Log", exportText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "전체 목록이 복사되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("클립보드 전체 복사")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.deleteAll()
                            showAllDataDialog = false
                            Toast.makeText(context, "자산목록이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("전체 비우기")
                    }
                    TextButton(onClick = { showAllDataDialog = false }) {
                        Text("닫기")
                    }
                }
            }
        )
    }
}

@Composable
fun AssetQueueItem(
    asset: AssetEntity,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = android.text.format.DateFormat.format("HH:mm", asset.timestamp).toString()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEditClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Status icon circle
                val statusColor = when (asset.status) {
                    "정상" -> Color(0xFF0061A4)
                    "점검필요" -> Color(0xFFE2B007)
                    "분실" -> Color(0xFFBA1A1A)
                    else -> Color.Gray
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = asset.status,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Scan text details
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = asset.tag,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Category chip inline
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(asset.category, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = asset.name.ifEmpty { "장치 미지정" },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (asset.memo.isNotEmpty()) {
                        Text(
                            text = asset.memo,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "$dateStr 스캔됨",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                TextButton(
                    onClick = onEditClick,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("수정", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SortButtonGroup(
    title: String,
    upOrder: AssetViewModel.SortOrder,
    downOrder: AssetViewModel.SortOrder,
    selectedOrder: AssetViewModel.SortOrder,
    onSelect: (AssetViewModel.SortOrder) -> Unit
) {
    val isUpSelected = selectedOrder == upOrder
    val isDownSelected = selectedOrder == downOrder

    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isUpSelected || isDownSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            },
            modifier = Modifier.padding(start = 6.dp, end = 2.dp)
        )

        // Up Button ▲
        Surface(
            onClick = { onSelect(upOrder) },
            shape = RoundedCornerShape(8.dp),
            color = if (isUpSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            contentColor = if (isUpSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.height(26.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "▲",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Down Button ▼
        Surface(
            onClick = { onSelect(downOrder) },
            shape = RoundedCornerShape(8.dp),
            color = if (isDownSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            contentColor = if (isDownSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.height(26.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "▼",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun formatAssetTag(input: String): String {
    val trimmed = input.trim()
    val regex = Regex("^([A-Za-z]{2}\\d+)\\s+(\\d+)$")
    val matchResult = regex.matchEntire(trimmed) ?: return trimmed
    
    val part1 = matchResult.groups[1]?.value ?: return trimmed
    val part2 = matchResult.groups[2]?.value ?: return trimmed
    
    val len1 = part1.length - 2 // digits in prefix
    val targetLen2 = 10 - len1
    if (targetLen2 <= 0) return trimmed
    
    val paddedPart2 = part2.padStart(targetLen2, '0')
    return part1 + paddedPart2
}

fun isValidSpaceFormat(input: String): Boolean {
    val trimmed = input.trim()
    val regex = Regex("^([A-Za-z]{2}\\d+)\\s+(\\d+)$")
    val matchResult = regex.matchEntire(trimmed) ?: return false
    val part1 = matchResult.groups[1]?.value ?: return false
    val part2 = matchResult.groups[2]?.value ?: return false
    val len1 = part1.length - 2
    return len1 >= 0 && (10 - len1) >= part2.length
}


