package com.mi.explorer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.ui.theme.MiBlue
import com.mi.explorer.ui.theme.MiGreen
import com.mi.explorer.ui.theme.MiOrange
import com.mi.explorer.ui.viewmodel.ExplorerViewModel
import com.mi.explorer.utils.RootHelper
import com.mi.explorer.utils.RootStatus
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootBrowserScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var currentDir by remember { mutableStateOf(File("/")) }
    var rootStatus by remember { mutableStateOf<RootStatus?>(null) }
    var isCheckingRoot by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<File>>(emptyList()) }
    var permissionDenied by remember { mutableStateOf(false) }

    fun loadDir(dir: File) {
        currentDir = dir
        val list = dir.listFiles()
        if (list == null) {
            permissionDenied = true
            items = emptyList()
        } else {
            permissionDenied = false
            items = list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }

    LaunchedEffect(Unit) {
        isCheckingRoot = true
        rootStatus = RootHelper.checkRootStatus()
        isCheckingRoot = false
        loadDir(File("/"))
    }

    BackHandler {
        val parent = currentDir.parentFile
        if (parent != null && currentDir.path != "/") {
            loadDir(parent)
        } else {
            viewModel.handleBackPress()
        }
    }

    Scaffold(
        modifier = modifier.testTag("root_browser_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Root Directory Browser",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (rootStatus?.isRootGranted == true) MiGreen.copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (rootStatus?.isRootGranted == true) "SU GRANTED" else "SYSTEM RO",
                                    color = if (rootStatus?.isRootGranted == true) Color(0xFF059669) else Color(0xFFDC2626),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = currentDir.absolutePath,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            val parent = currentDir.parentFile
                            if (parent != null && currentDir.path != "/") {
                                loadDir(parent)
                            } else {
                                viewModel.handleBackPress()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadDir(currentDir) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Root Status Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (rootStatus?.isRootGranted == true)
                            Color(0xFF10B981).copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (rootStatus?.isRootGranted == true) Icons.Default.Security else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (rootStatus?.isRootGranted == true) Color(0xFF10B981) else MiOrange,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (rootStatus?.isRootGranted == true) "Superuser Access Active" else "Standard System Inspection",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (rootStatus?.isRootGranted == true)
                                        "UID 0 detected • Full root read/write privileges available."
                                    else
                                        "Browse readable system partitions (/system, /etc, /vendor). SU binary not active.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        isCheckingRoot = true
                                        rootStatus = RootHelper.checkRootStatus()
                                        isCheckingRoot = false
                                        viewModel.showMessage(
                                            if (rootStatus?.isRootGranted == true) "Root access confirmed!" else "Superuser access not granted."
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Test SU", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 2. Quick Partition Chips
            item {
                Column {
                    Text(
                        text = "System Partitions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val partitions = listOf(
                            "/" to "Root (/)",
                            "/system" to "/system",
                            "/vendor" to "/vendor",
                            "/data" to "/data",
                            "/etc" to "/etc",
                            "/apex" to "/apex",
                            "/storage" to "/storage"
                        )
                        items(partitions) { (path, label) ->
                            val f = File(path)
                            val isCurrent = currentDir.absolutePath == path
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) MiBlue else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.clickable {
                                    if (f.exists()) loadDir(f) else viewModel.showMessage("Partition $path not found")
                                }
                            ) {
                                Text(
                                    text = label,
                                    color = if (isCurrent) Color.White else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Permission denied banner
            if (permissionDenied) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Permission Denied for \"${currentDir.name}\". This partition requires active Superuser (su) root privileges to read.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB91C1C)
                            )
                        }
                    }
                }
            }

            // 4. File items list
            if (items.isEmpty() && !permissionDenied) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Empty directory", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(items, key = { it.absolutePath }) { file ->
                    val perms = remember(file) { RootHelper.getFileLinuxPermissions(file) }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (file.isDirectory) {
                                    loadDir(file)
                                } else {
                                    // If file is text/prop/conf/script, open in editor
                                    if (file.extension in listOf("prop", "conf", "sh", "rc", "xml", "txt", "json", "log", "cfg", "ini")) {
                                        viewModel.openTextEditor(file)
                                    } else {
                                        viewModel.showMessage("${file.name} (${FileItem.formatBytes(file.length())})")
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (file.isDirectory) MiBlue.copy(alpha = 0.15f) else Color(0xFF64748B).copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                    contentDescription = null,
                                    tint = if (file.isDirectory) MiBlue else Color(0xFF64748B),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = perms,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                                        color = MiOrange
                                    )
                                    Text(
                                        text = if (file.isDirectory) "Dir" else FileItem.formatBytes(file.length()),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (file.isDirectory) {
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            } else if (file.extension in listOf("prop", "conf", "sh", "rc", "xml", "txt")) {
                                IconButton(onClick = { viewModel.openTextEditor(file) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "View/Edit", tint = MiGreen, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
