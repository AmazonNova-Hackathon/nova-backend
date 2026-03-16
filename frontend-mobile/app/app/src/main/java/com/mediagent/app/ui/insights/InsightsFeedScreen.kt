package com.mediagent.app.ui.insights

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mediagent.app.data.model.FollowUp
import com.mediagent.app.data.model.InsightCard
import com.mediagent.app.presentation.viewmodel.InsightsViewModel
import com.mediagent.app.ui.components.InsightCardComponent
import com.mediagent.app.ui.theme.*

private enum class FeedTab(val label: String) {
    Insights("Insights"),
    FollowUps("Follow-ups"),
}

private enum class SeverityFilter(val label: String) {
    All("All"),
    Urgent("Urgent"),
    Attention("Attention"),
    Info("Info"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsFeedScreen(
    insightsViewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by insightsViewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(FeedTab.Insights) }
    var selectedFilter by remember { mutableStateOf(SeverityFilter.All) }

    val unreadCount = remember(state.insights) {
        state.insights.count { !it.read }
    }

    val filteredInsights = remember(state.insights, selectedFilter) {
        when (selectedFilter) {
            SeverityFilter.All -> state.insights
            SeverityFilter.Urgent -> state.insights.filter { it.severity == "urgent" }
            SeverityFilter.Attention -> state.insights.filter { it.severity == "attention" }
            SeverityFilter.Info -> state.insights.filter { it.severity == "informational" }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Insights",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandDeep,
                        )
                        if (unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "$unreadCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(UrgentText)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SurfacePrimary,
                ),
            )
        },
        containerColor = SurfaceSecondary,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // --- Secondary Tab Row ---
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = SurfacePrimary,
                contentColor = BrandPurple,
                indicator = { tabPositions ->
                    SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = BrandPurple,
                    )
                },
            ) {
                FeedTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.label,
                                fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedTab == tab) BrandPurple else TextTertiary,
                            )
                        },
                    )
                }
            }

            if (state.error.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error,
                            fontSize = 14.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { insightsViewModel.refresh() }) {
                            Text("Retry", color = BrandPurple)
                        }
                    }
                }
            } else {
                when (selectedTab) {
                    FeedTab.Insights -> InsightsTabContent(
                        insights = filteredInsights,
                        unreadCount = unreadCount,
                        selectedFilter = selectedFilter,
                        onFilterSelected = { selectedFilter = it },
                        onMarkRead = insightsViewModel::markRead,
                        loading = state.loading,
                    )

                    FeedTab.FollowUps -> FollowUpsTabContent(
                        followUps = state.followUps,
                        onAccept = { insightsViewModel.updateFollowUp(it, "accepted") },
                        onDismiss = { insightsViewModel.updateFollowUp(it, "dismissed") },
                        loading = state.loading,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightsTabContent(
    insights: List<InsightCard>,
    unreadCount: Int,
    selectedFilter: SeverityFilter,
    onFilterSelected: (SeverityFilter) -> Unit,
    onMarkRead: (String) -> Unit,
    loading: Boolean,
) {
    if (loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = BrandPurple)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        // --- Filter Chip Row ---
        item {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeverityFilter.entries.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    val bgColor by animateColorAsState(
                        if (isSelected) BrandPurple else SurfaceTertiary,
                        label = "filterBg",
                    )
                    val textColor by animateColorAsState(
                        if (isSelected) Color.White else TextSecondary,
                        label = "filterText",
                    )
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterSelected(filter) },
                        label = {
                            Text(
                                text = filter.label,
                                fontSize = 13.sp,
                                color = textColor,
                            )
                        },
                        shape = RoundedCornerShape(100.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandPurple,
                            containerColor = SurfaceTertiary,
                        ),
                        border = null,
                    )
                }
            }
        }

        // --- Section Header ---
        item {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "\u00B7 $unreadCount unread",
                    fontSize = 13.sp,
                    color = TextTertiary,
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        // --- Insight Cards ---
        if (insights.isEmpty()) {
            item {
                EmptyState(message = "Upload a report to generate insights.")
            }
        } else {
            items(insights, key = { it.id }) { insight ->
                InsightCardComponent(
                    insight = insight,
                    onMarkRead = onMarkRead,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FollowUpsTabContent(
    followUps: List<FollowUp>,
    onAccept: (String) -> Unit,
    onDismiss: (String) -> Unit,
    loading: Boolean,
) {
    if (loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = BrandPurple)
        }
        return
    }

    if (followUps.isEmpty()) {
        EmptyState(message = "No pending follow-ups.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(followUps, key = { it.id }) { followUp ->
            FollowUpCardComponent(
                followUp = followUp,
                onAccept = onAccept,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            fontSize = 14.sp,
            color = TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}
