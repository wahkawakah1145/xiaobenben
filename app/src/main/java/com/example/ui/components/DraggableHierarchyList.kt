package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ui.theme.AppHapticAction
import com.example.ui.theme.LocalAppHaptics

private data class ItemBounds(val top: Float, val height: Float) {
    val bottom: Float get() = top + height
}

/**
 * Reusable vertical list supporting:
 * 1) Long-press drag to reorder items smoothly.
 * 2) Optional drag-onto-target nesting: when item A is dragged over the central 52% band of item B
 *    and [canNest] returns true, item B highlights (`isNestTarget = true`); releasing assigns A into B.
 */
@Composable
fun <T> DraggableHierarchyColumn(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    dragEnabled: Boolean = true,
    enableNesting: Boolean = true,
    canNest: (dragged: T, target: T) -> Boolean = { _, _ -> false },
    onReorder: (List<T>) -> Unit,
    onNestInto: (dragged: T, target: T) -> Unit = { _, _ -> },
    itemContent: @Composable (
        item: T,
        isDragging: Boolean,
        isNestTarget: Boolean,
        dragModifier: Modifier
    ) -> Unit
) {
    val haptics = LocalAppHaptics.current
    var workingItems by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var pointerCenterY by remember { mutableFloatStateOf(0f) }
    var nestTargetKey by remember { mutableStateOf<Any?>(null) }
    var orderChanged by remember { mutableStateOf(false) }

    val itemBounds = remember { mutableStateMapOf<Any, ItemBounds>() }

    LaunchedEffect(items) {
        if (draggingKey == null) {
            workingItems = items
            val validKeys = items.mapTo(HashSet()) { key(it) }
            itemBounds.keys.retainAll(validKeys)
        }
    }

    val latestCanNest by rememberUpdatedState(canNest)
    val latestOnReorder by rememberUpdatedState(onReorder)
    val latestOnNestInto by rememberUpdatedState(onNestInto)
    val latestDragEnabled by rememberUpdatedState(dragEnabled)
    val latestEnableNesting by rememberUpdatedState(enableNesting)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        workingItems.forEach { item ->
            val itemKey = key(item)
            val isDragging = draggingKey == itemKey
            val isNestTarget = nestTargetKey == itemKey && draggingKey != null && draggingKey != itemKey
            val elevation by animateDpAsState(
                targetValue = if (isDragging) 8.dp else 0.dp,
                label = "drag_elevation"
            )

            val gestureModifier = if (dragEnabled) {
                Modifier.pointerInput(itemKey, dragEnabled, enableNesting) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            if (!latestDragEnabled) return@detectDragGesturesAfterLongPress
                            val bounds = itemBounds[itemKey] ?: return@detectDragGesturesAfterLongPress
                            draggingKey = itemKey
                            dragOffsetY = 0f
                            pointerCenterY = bounds.top + bounds.height / 2f
                            nestTargetKey = null
                            orderChanged = false
                            haptics?.perform(AppHapticAction.LONG_PRESS)
                        },
                        onDrag = { change, dragAmount ->
                            if (draggingKey != itemKey) return@detectDragGesturesAfterLongPress
                            change.consume()
                            dragOffsetY += dragAmount.y
                            pointerCenterY += dragAmount.y

                            val currentList = workingItems
                            val draggedIdx = currentList.indexOfFirst { key(it) == itemKey }
                            if (draggedIdx < 0) return@detectDragGesturesAfterLongPress
                            val draggedItem = currentList[draggedIdx]

                            // Find which item's vertical span currently contains pointerCenterY
                            val hoveredEntry = currentList.withIndex().firstOrNull { (idx, candidate) ->
                                if (idx == draggedIdx) return@firstOrNull false
                                val b = itemBounds[key(candidate)] ?: return@firstOrNull false
                                pointerCenterY in b.top..b.bottom
                            }

                            if (hoveredEntry != null) {
                                val targetIdx = hoveredEntry.index
                                val targetItem = hoveredEntry.value
                                val targetKey = key(targetItem)
                                val targetBounds = itemBounds[targetKey]
                                if (targetBounds != null && targetBounds.height > 1f) {
                                    val fraction = ((pointerCenterY - targetBounds.top) / targetBounds.height).coerceIn(0f, 1f)
                                    val inNestZone = latestEnableNesting &&
                                        fraction in 0.24f..0.76f &&
                                        latestCanNest(draggedItem, targetItem)

                                    if (inNestZone) {
                                        if (nestTargetKey != targetKey) {
                                            nestTargetKey = targetKey
                                            haptics?.perform(AppHapticAction.DRAG_REORDER)
                                        }
                                        return@detectDragGesturesAfterLongPress
                                    } else {
                                        nestTargetKey = null
                                        val shouldSwap = (targetIdx > draggedIdx && fraction >= 0.72f) ||
                                            (targetIdx < draggedIdx && fraction <= 0.28f) ||
                                            (!latestEnableNesting && (
                                                (targetIdx > draggedIdx && fraction >= 0.45f) ||
                                                    (targetIdx < draggedIdx && fraction <= 0.55f)
                                                ))
                                        if (shouldSwap) {
                                            val oldBounds = itemBounds[itemKey]
                                            val updated = currentList.toMutableList()
                                            val moved = updated.removeAt(draggedIdx)
                                            updated.add(targetIdx, moved)
                                            workingItems = updated
                                            orderChanged = true
                                            if (oldBounds != null) {
                                                dragOffsetY -= (targetBounds.top - oldBounds.top)
                                            }
                                            haptics?.perform(AppHapticAction.DRAG_REORDER)
                                        }
                                    }
                                }
                            } else {
                                nestTargetKey = null
                            }
                        },
                        onDragEnd = {
                            val activeDragKey = draggingKey
                            val activeNestKey = nestTargetKey
                            val finalList = workingItems
                            val didReorder = orderChanged

                            draggingKey = null
                            dragOffsetY = 0f
                            nestTargetKey = null
                            orderChanged = false

                            if (activeDragKey != null && activeNestKey != null && latestEnableNesting) {
                                val dragged = finalList.firstOrNull { key(it) == activeDragKey }
                                val target = finalList.firstOrNull { key(it) == activeNestKey }
                                if (dragged != null && target != null && latestCanNest(dragged, target)) {
                                    haptics?.perform(AppHapticAction.TOGGLE)
                                    latestOnNestInto(dragged, target)
                                    return@detectDragGesturesAfterLongPress
                                }
                            }
                            if (didReorder) {
                                latestOnReorder(finalList)
                            }
                        },
                        onDragCancel = {
                            draggingKey = null
                            dragOffsetY = 0f
                            nestTargetKey = null
                            orderChanged = false
                            workingItems = items
                        }
                    )
                }
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 10f else 0f)
                    .onGloballyPositioned { coords ->
                        itemBounds[itemKey] = ItemBounds(
                            top = coords.positionInParent().y,
                            height = coords.size.height.toFloat()
                        )
                    }
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffsetY
                            scaleX = 1.02f
                            scaleY = 1.02f
                            shadowElevation = elevation.toPx()
                        }
                    }
            ) {
                itemContent(item, isDragging, isNestTarget, gestureModifier)
            }
        }
    }
}
