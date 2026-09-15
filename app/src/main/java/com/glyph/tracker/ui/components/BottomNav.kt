package com.glyph.tracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GlyphTab(val label: String, val icon: ImageVector, val tag: String) {
    SCREEN_TIME("Screen time", Icons.Outlined.HourglassBottom, "tab_screen_time"),
    DATA_USAGE("Data usage", Icons.Outlined.DataUsage, "tab_data_usage")
}

private data class TabBounds(val offsetX: Dp, val width: Dp, val height: Dp)

@Composable
fun GlyphBottomNav(
    selectedTab: GlyphTab = GlyphTab.SCREEN_TIME,
    currentTab: GlyphTab = selectedTab,
    onTabSelected: (GlyphTab) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeTab = if (currentTab != selectedTab && selectedTab != GlyphTab.SCREEN_TIME) selectedTab else currentTab

    val density = LocalDensity.current
    // Measured position/size of each tab item, used to slide the pill indicator smoothly between them
    val tabBounds = remember { mutableStateMapOf<GlyphTab, TabBounds>() }
    val activeBounds = tabBounds[activeTab]

    val animatedOffset by animateDpAsState(
        targetValue = activeBounds?.offsetX ?: 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "pillOffset"
    )
    val animatedWidth by animateDpAsState(
        targetValue = activeBounds?.width ?: 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "pillWidth"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .padding(6.dp)
        ) {
            // Sliding pill indicator — drawn behind the tab labels, animates on tap AND on pager swipe
            if (activeBounds != null) {
                Box(
                    modifier = Modifier
                        .offset(x = animatedOffset)
                        .width(animatedWidth)
                        .height(activeBounds.height)
                        .clip(RoundedCornerShape(26.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                GlyphTab.values().forEach { tab ->
                    val isSelected = tab == activeTab
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "pillContentColor"
                    )
                    Row(
                        modifier = Modifier
                            .testTag(tab.tag)
                            .onGloballyPositioned { coordinates ->
                                tabBounds[tab] = TabBounds(
                                    offsetX = with(density) { coordinates.positionInParent().x.toDp() },
                                    width = with(density) { coordinates.size.width.toDp() },
                                    height = with(density) { coordinates.size.height.toDp() }
                                )
                            }
                            .clip(RoundedCornerShape(26.dp))
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = contentColor,
                            modifier = Modifier.width(20.dp).height(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}
