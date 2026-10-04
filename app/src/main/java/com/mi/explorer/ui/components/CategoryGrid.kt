package com.mi.explorer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mi.explorer.data.model.FileCategory
import com.mi.explorer.ui.theme.*

data class MiCategory(
    val title: String,
    val icon: ImageVector,
    val iconColor: Color,
    val bgColor: Color,
    val category: FileCategory?,
    val isTools: Boolean = false,
    val isSocial: Boolean = false
)

/**
 * Compact, modern 4x2 Category Grid inspired by Xiaomi MIUI / HyperOS and Google Files.
 * Uses a balanced 4-column layout that cuts vertical screen consumption in half,
 * allowing immediate visibility of recent files and folder contents.
 */
@Composable
fun CategoryGrid(
    onCategoryClick: (FileCategory, String) -> Unit,
    onToolsClick: () -> Unit,
    onSocialClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val row1 = listOf(
        MiCategory("Images", Icons.Default.Image, Color.White, MiBlue, FileCategory.IMAGE),
        MiCategory("Videos", Icons.Default.Movie, Color.White, MiPurple, FileCategory.VIDEO),
        MiCategory("Music", Icons.Default.Audiotrack, Color.White, MiRed, FileCategory.AUDIO),
        MiCategory("Docs", Icons.Default.Description, Color.White, MiYellow, FileCategory.DOCUMENT)
    )

    val row2 = listOf(
        MiCategory("App Installer", Icons.Default.InstallMobile, Color.White, MiGreen, FileCategory.APK),
        MiCategory("Downloads", Icons.Default.Download, Color.White, MiCyan, null),
        MiCategory("Archives", Icons.Default.FolderZip, Color.White, MiAmber, FileCategory.ARCHIVE),
        MiCategory("Social", Icons.Default.Chat, Color.White, Color(0xFF25D366), null, isSocial = true)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mi_category_grid")
    ) {
        // Row 1: Images, Videos, Music, Docs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            row1.forEach { cat ->
                CategoryTile(
                    category = cat,
                    onClick = {
                        handleCategoryClick(cat, onToolsClick, onSocialClick, onCategoryClick)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2: APKs, Downloads, Archives, Social
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            row2.forEach { cat ->
                CategoryTile(
                    category = cat,
                    onClick = {
                        handleCategoryClick(cat, onToolsClick, onSocialClick, onCategoryClick)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun handleCategoryClick(
    cat: MiCategory,
    onToolsClick: () -> Unit,
    onSocialClick: () -> Unit,
    onCategoryClick: (FileCategory, String) -> Unit
) {
    if (cat.isTools) {
        onToolsClick()
    } else if (cat.isSocial) {
        onSocialClick()
    } else if (cat.category != null) {
        onCategoryClick(cat.category, cat.title)
    } else if (cat.title == "Downloads") {
        onCategoryClick(FileCategory.UNKNOWN, "Downloads")
    }
}

@Composable
private fun CategoryTile(
    category: MiCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("category_tile_${category.title}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Compact 42dp Squircle Icon Badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(category.bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = category.title,
                tint = category.iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = category.title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
