package com.yourname.aggregator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.aggregator.core.model.SearchFilter
import com.yourname.aggregator.core.model.SortOrder
import com.yourname.aggregator.data.providers.SiteProvider

private data class DurationOption(val label: String, val seconds: Int?)

private val MIN_DURATION_OPTIONS = listOf(
    DurationOption("Any", 0),
    DurationOption("5m+", 5 * 60),
    DurationOption("10m+", 10 * 60),
    DurationOption("20m+", 20 * 60),
    DurationOption("30m+", 30 * 60)
)

private val MAX_DURATION_OPTIONS = listOf(
    DurationOption("Any", null),
    DurationOption("Under 10m", 10 * 60),
    DurationOption("Under 20m", 20 * 60),
    DurationOption("Under 45m", 45 * 60)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    currentFilter: SearchFilter,
    availableProviders: List<SiteProvider>,
    onApplyFilter: (SearchFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedSort by remember { mutableStateOf(currentFilter.sortOrder) }
    var selectedSources by remember { mutableStateOf(currentFilter.enabledSourceIds) }
    var minDuration by remember { mutableIntStateOf(currentFilter.minDurationSeconds) }
    var maxDuration by remember { mutableStateOf(currentFilter.maxDurationSeconds) }
    var excludedTagsInput by remember {
        mutableStateOf(currentFilter.excludedTags.joinToString(", "))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter & Sort",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        selectedSort = SortOrder.RELEVANCE
                        selectedSources = emptySet()
                        minDuration = 0
                        maxDuration = null
                        excludedTagsInput = ""
                    }
                ) {
                    Text("Reset")
                }
            }

            HorizontalDivider()

            // 1. Sort Order
            Text("Sort By", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortOrder.entries.forEach { order ->
                    FilterChip(
                        selected = selectedSort == order,
                        onClick = { selectedSort = order },
                        label = { Text(order.label) }
                    )
                }
            }

            // 2. Active Sites (Empty set = All active)
            if (availableProviders.isNotEmpty()) {
                Text("Sites", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val allSelected = selectedSources.isEmpty()
                    FilterChip(
                        selected = allSelected,
                        onClick = { selectedSources = emptySet() },
                        label = { Text("All Sites") }
                    )
                    availableProviders.forEach { provider ->
                        val isChecked = provider.id in selectedSources
                        FilterChip(
                            selected = isChecked,
                            onClick = {
                                selectedSources = if (isChecked) {
                                    selectedSources - provider.id
                                } else {
                                    val updated = selectedSources + provider.id
                                    if (updated.size == availableProviders.size) emptySet() else updated
                                }
                            },
                            label = { Text(provider.displayName) }
                        )
                    }
                }
            }

            // 3. Minimum Duration
            Text("Minimum Length", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MIN_DURATION_OPTIONS.forEach { option ->
                    val targetSeconds = option.seconds ?: 0
                    FilterChip(
                        selected = minDuration == targetSeconds,
                        onClick = { minDuration = targetSeconds },
                        label = { Text(option.label) }
                    )
                }
            }

            // 4. Maximum Duration
            Text("Maximum Length", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MAX_DURATION_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = maxDuration == option.seconds,
                        onClick = { maxDuration = option.seconds },
                        label = { Text(option.label) }
                    )
                }
            }

            // 5. Excluded Tags
            Text("Exclude Tags", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = excludedTagsInput,
                onValueChange = { excludedTagsInput = it },
                placeholder = { Text("Comma-separated (e.g. compilation, preview)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Apply Button
            Button(
                onClick = {
                    val parsedExclusions = excludedTagsInput
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()

                    onApplyFilter(
                        SearchFilter(
                            enabledSourceIds = selectedSources,
                            minDurationSeconds = minDuration,
                            maxDurationSeconds = maxDuration,
                            excludedTags = parsedExclusions,
                            sortOrder = selectedSort
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Filters")
            }
        }
    }
}
