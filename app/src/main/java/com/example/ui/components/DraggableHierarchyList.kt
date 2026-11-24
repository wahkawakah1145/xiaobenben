package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key as composeKey
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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.ui.theme.AppHapticAction
import com.example.ui.theme.LocalAppHaptics
import kotlinx.coroutines.delay

internal data class HierarchyItemBounds(
    val top: Float,
    val height: Float
) {
    val bottom: Float get() = top + height
    val center: Float get() = top + height / 2f
}

internal sealed interface DragHitDecision {
    data object None : DragHitDecision
    data class HoverForNest(val targetIndex: Int) : DragHitDecision
    data class SwapToIndex(val newIndex: Int) : DragHitDecision
}

/**
 * Pure layout-math helper for evaluating drag crossing vs dwell-to-nest zones.
 * Kept internal for direct unit testing.
 */
internal object DraggableHierarchyMath {
    const val DEFAULT_NEST_DWELL_MS: Long = 450L

    fun evaluateDragPosition(
        draggedIdx: Int,
        dragCenterY: Float,
        boundsInOrder: List<HierarchyItemBounds?>,
        enableNesting: Boolean,
        canNestAtIndex: (Int) -> Boolean,
        activeNestTargetIdx: Int?
    ): DragHitDecision {
        if (draggedIdx !in boundsInOrder.indices) return DragHitDecision.None

        // 1. Check if the dragged item's center is inside a candidate's nest dwell zone.
        if (enableNesting) {
            for (idx in boundsInOrder.indices) {
                if (idx == draggedIdx) continue
                val b = boundsInOrder[idx] ?: continue
                if (b.height <= 1f) continue
                if (dragCenterY in b.top..b.bottom) {
                    val fraction = ((dragCenterY - b.top) / b.height).coerceIn(0f, 1f)
                    if (fraction in 0.18f..0.82f && canNestAtIndex(idx)) {
                        return DragHitDecision.HoverForNest(idx)
                    }
                    break
                }
            }
        }

        // If user is currently locked into an active highlighted nest target and hasn't exited it, keep it.
        if (enableNesting && activeNestTargetIdx != null && activeNestTargetIdx in boundsInOrder.indices && activeNestTargetIdx != draggedIdx) {
            val activeBounds = boundsInOrder[activeNestTargetIdx]
            if (activeBounds != null && dragCenterY in activeBounds.top..activeBounds.bottom && canNestAtIndex(activeNestTargetIdx)) {
                val fraction = ((dragCenterY - activeBounds.top) / activeBounds.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                if (fraction in 0.15f..0.85f) {
                    return DragHitDecision.HoverForNest(activeNestTargetIdx)
                }
            }
        }

        // 2. Otherwise evaluate crossing past items downward or upward (handles both slow drags & fast swipes across multiple items).
        var targetIdx = draggedIdx
        // Check downward crossing
        for (idx in (draggedIdx + 1) until boundsInOrder.size) {
            val b = boundsInOrder[idx] ?: continue
            val nestable = enableNesting && canNestAtIndex(idx)
            val threshold = if (nestable) b.top + b.height * 0.78f else b.center
            if (dragCenterY >= threshold) {
                targetIdx = idx
            } else {
                break
            }
        }
        if (targetIdx != draggedIdx) {
            return DragHitDecision.SwapToIndex(targetIdx)
        }

        // Check upward crossing
        for (idx in (draggedIdx - 1) downTo 0) {
            val b = boundsInOrder[idx] ?: continue
            val nestable = enableNesting && canNestAtIndex(idx)
            val threshold = if (nestable) b.top + b.height * 0.22f else b.center
            if (dragCenterY <= threshold) {
                targetIdx = idx
            } else {
                break
            }
        }
        return if (targetIdx != draggedIdx) {
            DragHitDecision.SwapToIndex(targetIdx)
        } else {
            DragHitDecision.None
        }
    }
}

/**
 * Reusable vertical list supporting:
 * 1) Long-press drag to reorder freely across any number of items (even varying heights) with
 *    optional parent LazyColumn edge auto-scrolling.
 * 2) Delayed dwell-to-nest: dragging A onto B and pausing for [nestDwellMillis] (~450ms) highlights B
 *    (`isNestTarget = true`); releasing assigns A into B. Moving smoothly past B without pausing
 *    crosses and reorders without triggering nesting.
 */
@Composable
fun <T> DraggableHierarchyColumn(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    dragEnabled: Boolean = true,
    enableNesting: Boolean = true,
    nestDwellMillis: Long = DraggableHierarchyMath.DEFAULT_NEST_DWELL_MS,
    lazyListState: LazyListState? = null,
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
    val density = LocalDensity.current

    var workingItems by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var dragVisualTop by remember { mutableFloatStateOf(0f) }
    var dragItemHeight by remember { mutableFloatStateOf(0f) }
    var hoverCandidateKey by remember { mutableStateOf<Any?>(null) }
    var nestTargetKey by remember { mutableStateOf<Any?>(null) }
    var orderChanged by remember { mutableStateOf(false) }

    // Container window top & viewport bounds for optional LazyColumn edge auto-scrolling
    var columnWindowTop by remember { mutableFloatStateOf(0f) }

    val itemBounds = remember { mutableStateMapOf<Any, HierarchyItemBounds>() }

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
    val latestNestDwellMillis by rememberUpdatedState(nestDwellMillis)

    // Dwell timer: only highlight item B after A hovers steadily over B for `nestDwellMillis`.
    LaunchedEffect(draggingKey, hoverCandidateKey, latestEnableNesting) {
        val activeDrag = draggingKey
        val candidate = hoverCandidateKey
        if (activeDrag == null || candidate == null || candidate == activeDrag || !latestEnableNesting) {
            nestTargetKey = null
            return@LaunchedEffect
        }
        if (nestTargetKey == candidate) return@LaunchedEffect
        nestTargetKey = null
        delay(latestNestDwellMillis.coerceAtLeast(120L))
        if (draggingKey == activeDrag && hoverCandidateKey == candidate && latestEnableNesting) {
            nestTargetKey = candidate
            haptics?.perform(AppHapticAction.DRAG_REORDER)
        }
    }

    // Optional edge auto-scroll when dragging near top/bottom of an enclosing LazyColumn.
    LaunchedEffect(draggingKey, lazyListState) {
        val listState = lazyListState ?: return@LaunchedEffect
        if (draggingKey == null) return@LaunchedEffect
        val edgeZonePx = with(density) { 80.dp.toPx() }
        val maxStepPx = with(density) { 14.dp.toPx() }
        while (draggingKey != null) {
            val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
            if (viewportHeight > edgeZonePx * 2f) {
                val dragCenterWindowY = columnWindowTop + dragVisualTop + dragItemHeight / 2f
                val scrollDelta = when {
                    dragCenterWindowY < edgeZonePx -> -maxStepPx
                    dragCenterWindowY > viewportHeight - edgeZonePx -> maxStepPx
                    else -> 0f
                }
                if (scrollDelta != 0f) {
                    val consumed = listState.scrollBy(scrollDelta)
                    if (consumed != 0f) {
                        dragVisualTop += consumed
                    }
                }
            }
            delay(16L)
        }
    }

    fun evaluateCurrentDrag(itemKey: Any) {
        val currentList = workingItems
        val draggedIdx = currentList.indexOfFirst { key(it) == itemKey }
        if (draggedIdx < 0) return
        val draggedItem = currentList[draggedIdx]
        val dragCenterY = dragVisualTop + dragItemHeight / 2f
        val boundsInOrder = currentList.map { itemBounds[key(it)] }
        val activeNestIdx = nestTargetKey?.let { activeKey ->
            currentList.indexOfFirst { key(it) == activeKey }.takeIf { it >= 0 }
        }

        val decision = DraggableHierarchyMath.evaluateDragPosition(
            draggedIdx = draggedIdx,
            dragCenterY = dragCenterY,
            boundsInOrder = boundsInOrder,
            enableNesting = latestEnableNesting,
            canNestAtIndex = { idx -> latestCanNest(draggedItem, currentList[idx]) },
            activeNestTargetIdx = activeNestIdx
        )

        when (decision) {
            is DragHitDecision.HoverForNest -> {
                val targetKey = key(currentList[decision.targetIndex])
                if (hoverCandidateKey != targetKey) {
                    hoverCandidateKey = targetKey
                    if (nestTargetKey != targetKey) {
                        nestTargetKey = null
                    }
                }
            }
            is DragHitDecision.SwapToIndex -> {
                hoverCandidateKey = null
                nestTargetKey = null
                val updated = currentList.toMutableList()
                val moved = updated.removeAt(draggedIdx)
                val clampedTarget = decision.newIndex.coerceIn(0, updated.size)
                updated.add(clampedTarget, moved)
                workingItems = updated
                orderChanged = true
                haptics?.perform(AppHapticAction.DRAG_REORDER)
            }
            DragHitDecision.None -> {
                hoverCandidateKey = null
                nestTargetKey = null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                columnWindowTop = coords.positionInWindow().y
            },
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        workingItems.forEach { item ->
            val itemKey = key(item)
            composeKey(itemKey) {
                val isDragging = draggingKey == itemKey
                val isNestTarget = nestTargetKey == itemKey && draggingKey != null && draggingKey != itemKey
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 8.dp else 0.dp,
                    label = "drag_elevation"
                )

                // Key pointerInput ONLY by stable itemKey so swaps NEVER cancel the active drag gesture!
                val gestureModifier = Modifier.pointerInput(itemKey) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            if (!latestDragEnabled) return@detectDragGesturesAfterLongPress
                            val bounds = itemBounds[itemKey] ?: return@detectDragGesturesAfterLongPress
                            draggingKey = itemKey
                            dragVisualTop = bounds.top
                            dragItemHeight = bounds.height.coerceAtLeast(1f)
                            hoverCandidateKey = null
                            nestTargetKey = null
                            orderChanged = false
                            haptics?.perform(AppHapticAction.LONG_PRESS)
                        },
                        onDrag = { change, dragAmount ->
                            if (draggingKey != itemKey) return@detectDragGesturesAfterLongPress
                            change.consume()
                            dragVisualTop += dragAmount.y
                            evaluateCurrentDrag(itemKey)
                        },
                        onDragEnd = {
                            if (draggingKey != itemKey) return@detectDragGesturesAfterLongPress
                            val activeDragKey = draggingKey
                            val activeNestKey = nestTargetKey
                            val finalList = workingItems
                            val didReorder = orderChanged

                            draggingKey = null
                            hoverCandidateKey = null
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
                            if (draggingKey != itemKey) return@detectDragGesturesAfterLongPress
                            draggingKey = null
                            hoverCandidateKey = null
                            nestTargetKey = null
                            orderChanged = false
                            workingItems = items
                        }
                    )
                }

                // Measure pure un-translated layout position on outer Box; apply visual translation on inner Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 10f else 0f)
                        .onGloballyPositioned { coords ->
                            itemBounds[itemKey] = HierarchyItemBounds(
                                top = coords.positionInParent().y,
                                height = coords.size.height.toFloat()
                            )
                        }
                ) {
                    val layoutTop = itemBounds[itemKey]?.top ?: dragVisualTop
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragVisualTop - layoutTop
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                    shadowElevation = elevation.toPx()
                                }
                            }
                    ) {
                        itemContent(item, isDragging, isNestTarget, if (dragEnabled) gestureModifier else Modifier)
                    }
                }
            }
        }
    }
}
