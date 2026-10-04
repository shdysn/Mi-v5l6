package com.mi.explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.explorer.data.model.FileItem
import com.mi.explorer.ui.theme.MiOrange
import java.util.zip.Deflater

enum class ZipCompressionLevel(val title: String, val level: Int, val description: String) {
    FAST("Fast", Deflater.BEST_SPEED, "Faster archiving, slightly larger file"),
    STANDARD("Standard", Deflater.DEFAULT_COMPRESSION, "Balanced speed and size (Recommended)"),
    MAXIMUM("Maximum", Deflater.BEST_COMPRESSION, "Smallest file size, takes longer")
}

@Composable
fun ZipCompressDialog(
    selectedItems: List<FileItem>,
    defaultArchiveName: String,
    onDismiss: () -> Unit,
    onCompress: (archiveName: String, compressionLevel: Int) -> Unit
) {
    var archiveName by remember {
        mutableStateOf(
            if (defaultArchiveName.endsWith(".zip", ignoreCase = true)) defaultArchiveName
            else "$defaultArchiveName.zip"
        )
    }
    var selectedLevel by remember { mutableStateOf(ZipCompressionLevel.STANDARD) }

    val totalBytes = remember(selectedItems) {
        selectedItems.sumOf { it.size }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Archive, contentDescription = null, tint = MiOrange)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Compress into ZIP")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${selectedItems.size} items selected (${FileItem.formatBytes(totalBytes)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    label = { Text("Archive Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Compression Level",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ZipCompressionLevel.values().forEach { lvl ->
                        val isSelected = selectedLevel == lvl
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp)),
                            color = if (isSelected) MiOrange else Color.Transparent,
                            onClick = { selectedLevel = lvl }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lvl.title,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = selectedLevel.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (archiveName.endsWith(".zip", ignoreCase = true)) archiveName.trim()
                    else "${archiveName.trim()}.zip"
                    onCompress(finalName, selectedLevel.level)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MiOrange)
            ) {
                Text("Create ZIP")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
