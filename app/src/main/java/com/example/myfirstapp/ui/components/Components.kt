package com.example.myfirstapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.myfirstapp.domain.MoneyFormatter
import com.example.myfirstapp.domain.TransactionType
import com.example.myfirstapp.ui.theme.AppDimens
import com.example.myfirstapp.ui.theme.ExpenseRed
import com.example.myfirstapp.ui.theme.ExpenseRedDark
import com.example.myfirstapp.ui.theme.IncomeGreen
import com.example.myfirstapp.ui.theme.IncomeGreenDark
import com.example.myfirstapp.ui.theme.minTouchTarget
import com.example.myfirstapp.ui.theme.onColorFor

/** 统一卡片描边：低对比细线比 1dp 阴影更干净，也避免深色下的脏灰边 */
@Composable
private fun appCardBorder() = androidx.compose.foundation.BorderStroke(
    1.dp,
    MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
)

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = appCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space4),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space3)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    Surface(
                        shape = RoundedCornerShape(AppDimens.ChipRadius),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(AppDimens.Space2)
                        )
                    }
                    Spacer(modifier = Modifier.width(AppDimens.Space3))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trailing()
            }
            content()
        }
    }
}

@Composable
fun StatCard(
    label: String,
    amountCents: Long,
    highlightColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = appCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space4),
            verticalArrangement = Arrangement.spacedBy(AppDimens.Space2)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = highlightColor,
                        modifier = Modifier.size(AppDimens.IconSmall)
                    )
                    Spacer(modifier = Modifier.width(AppDimens.Space2))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = MoneyFormatter.formatCents(amountCents),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = highlightColor
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector = Icons.Default.Info,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    /** 紧凑变体：给上方已有概览卡的页面用，避免首屏只露出半截标题 */
    compact: Boolean = false,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                vertical = if (compact) AppDimens.Space3 else AppDimens.Space8,
                horizontal = AppDimens.Space6
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) AppDimens.Space2 else AppDimens.Space3)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                modifier = Modifier.size(if (compact) 56.dp else 72.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(if (compact) AppDimens.Space6 else AppDimens.Space8)
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        action?.let {
            Spacer(modifier = Modifier.height(AppDimens.Space1))
            it()
        }
    }
}

/**
 * 页内标题/副标题组件。AppRoot 顶栏按设计不显示任何文字（只留菜单/返回图标），
 * 页面确实需要一行说明性标题时用这个组件，各页不要重复手写大标题。
 */
@Composable
fun AppScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space4, vertical = AppDimens.Space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action()
    }
}

/** 统一分区标题：替代 Todo / TaskBoard / Repo 三份几乎重复的实现 */
@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space4, vertical = AppDimens.Space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(AppDimens.Space2)
        ) {}
        Spacer(modifier = Modifier.width(AppDimens.Space2))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppDimens.Space2, vertical = AppDimens.Space1)
                )
            }
        }
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.minTouchTarget()) {
                Text(actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 统一卡片：shape / 描边 / 内边距全 App 一致 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    val body: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.Space4),
            content = content
        )
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = appCardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = body
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = appCardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            content = body
        )
    }
}

/** 统一加载态 */
@Composable
fun AppLoading(label: String = "加载中…", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppDimens.Space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimens.Space3)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 统一行内消息条：替代各页常驻不消失的裸 Text，带图标、可关闭 */
@Composable
fun AppInlineMessage(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val foreground = if (isError) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Space4, vertical = AppDimens.Space2),
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = foreground
    ) {
        Row(
            modifier = Modifier.padding(
                start = AppDimens.Space4,
                top = AppDimens.Space2,
                bottom = AppDimens.Space2,
                end = AppDimens.Space1
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.Info else Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(AppDimens.IconSmall)
            )
            Spacer(modifier = Modifier.width(AppDimens.Space2))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.minTouchTarget()) {
                    Icon(Icons.Default.Close, contentDescription = "关闭提示")
                }
            }
        }
    }
}

/**
 * 统一选色圆点：48dp 触摸区 + 勾选 + selected 语义。
 * 以前色点只有 28~34dp 且纯靠颜色/边框表示选中，读屏用户完全无法感知。
 */
@Composable
fun AppColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "颜色"
) {
    Box(
        modifier = modifier
            .minTouchTarget()
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.onSurface,
                    CircleShape
                ) else Modifier
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选择$contentDescription",
                tint = onColorFor(color),
                modifier = Modifier.size(AppDimens.IconSmall)
            )
        }
    }
}

@Composable
fun typeColor(type: TransactionType): Color =
    if (type == TransactionType.INCOME) incomeColor() else expenseColor()

/** 由当前 colorScheme 推导明暗，避免与 Theme 的动态/手动主题脱节 */
@Composable
private fun schemeIsDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

@Composable
fun incomeColor(): Color = if (schemeIsDark()) IncomeGreenDark else IncomeGreen

@Composable
fun expenseColor(): Color = if (schemeIsDark()) ExpenseRedDark else ExpenseRed
