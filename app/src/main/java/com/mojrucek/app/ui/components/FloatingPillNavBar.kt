package com.mojrucek.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape
import com.mojrucek.app.ui.theme.Spacing

/**
 * One destination in the [FloatingPillNavBar]. [label] is not drawn; it is the
 * content description TalkBack reads. [selectedIcon] is the filled variant shown
 * when active (spec item 14: filled only in active states).
 */
@Immutable
data class PillNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
)

/**
 * Spec item 1: a 64dp greenContainer pill floating 16dp from the screen sides and
 * bottom (plus the [windowInsets], navigation bar by default), with a soft 4dp
 * shadow at 8% black. Each item is a 48dp circle: inactive = transparent with a
 * darkOlive line icon, active = darkOlive fill with an onDarkOlive filled icon.
 */
@Composable
fun FloatingPillNavBar(
    items: List<PillNavItem>,
    selectedRoute: String?,
    onSelect: (PillNavItem) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.navigationBars
) {
    val colors = MojRucekTheme.colors
    val shadowColor = Color.Black.copy(alpha = 0.08f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .padding(start = Spacing.navBarMargin, end = Spacing.navBarMargin, bottom = Spacing.navBarMargin)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.navBarHeight)
                .shadow(elevation = 4.dp, shape = PillShape, ambientColor = shadowColor, spotColor = shadowColor)
                .clip(PillShape)
                .background(colors.greenContainer)
                .padding(horizontal = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.darkOlive else Color.Transparent)
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(item) },
                            role = Role.Tab,
                            interactionSource = null,
                            indication = ripple()
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label,
                        tint = if (selected) colors.onDarkOlive else colors.darkOlive,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun FloatingPillNavBarPreview() {
    val items = listOf(
        PillNavItem("home", "Почетна", Icons.Outlined.Home, Icons.Filled.Home),
        PillNavItem("ingredients", "Намирници", Icons.Outlined.Kitchen, Icons.Filled.Kitchen),
        PillNavItem("recipes", "Рецепти", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Filled.MenuBook),
        PillNavItem("planner", "Планер", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
        PillNavItem("shopping", "Пазарење", Icons.Outlined.ShoppingCart, Icons.Filled.ShoppingCart)
    )
    PreviewSurface {
        var selected by remember { mutableStateOf("home") }
        FloatingPillNavBar(items, selectedRoute = selected, onSelect = { selected = it.route }, windowInsets = WindowInsets(0))
    }
}
