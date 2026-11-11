package com.example.myfirstapp.ui.idea

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myfirstapp.ui.components.AppColorSwatch
import java.time.ZoneId

@Composable
fun IdeaEditorScreen(
    ideaId: Long,
    onBack: () -> Unit
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: IdeaEditorViewModel = viewModel(
        factory = IdeaEditorViewModel.factory(application, ideaId),
        key = "ideaEditor:$ideaId"
    )
    val title by viewModel.title.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val colorIndex by viewModel.colorIndex.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val updatedAt by viewModel.updatedAt.collectAsStateWithLifecycle()

    val dark = isSystemInDarkTheme()
    val bg = ideaNoteColor(colorIndex, dark)
    val fg = ideaTextColor(dark)
    val zone = ZoneId.systemDefault()

    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            TextField(
                value = title,
                onValueChange = viewModel::updateTitle,
                placeholder = { Text("标题（可选）", color = fg.copy(alpha = 0.92f)) },
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold, color = fg
                ),
                colors = transparentField(fg),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = content,
                onValueChange = viewModel::updateContent,
                placeholder = { Text("把想法写在这里，可用 #标签 归类…", color = fg.copy(alpha = 0.92f)) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5f),
                colors = transparentField(fg),
                minLines = 6,
                modifier = Modifier.fillMaxWidth()
            )

            if (updatedAt > 0L) {
                Text(
                    text = "更新于 ${relativeTime(updatedAt, zone)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.92f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until IDEA_COLOR_COUNT) {
                    AppColorSwatch(
                        color = ideaNoteColor(i, dark),
                        selected = i == colorIndex,
                        onClick = { viewModel.setColor(i) },
                        contentDescription = "便签颜色"
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已自动保存",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::togglePin) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = if (pinned) "取消置顶" else "置顶",
                        tint = if (pinned) fg else fg.copy(alpha = 0.9f)
                    )
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "删除",
                        tint = fg.copy(alpha = 0.92f)
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条灵感？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAndBack(onBack)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun transparentField(fg: Color) = TextFieldDefaults.colors(
    focusedTextColor = fg,
    unfocusedTextColor = fg,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = fg
)
