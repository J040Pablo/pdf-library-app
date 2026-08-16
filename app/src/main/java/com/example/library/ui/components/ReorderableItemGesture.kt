package com.example.library.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull

enum class ReorderGestureState {
    Idle,
    Pressing,
    Selected,
    DragReady,
    Dragging
}

/**
 * Custom gesture modifier for reorderable items handling a stable 2-stage gesture state machine:
 *
 * ESTÁGIO 1 — SELEÇÃO:
 * - Hold ~450ms without moving -> Selected (onLongPress)
 * - Release before Stage 2 -> item remains selected, position unchanged.
 *
 * ESTÁGIO 2 — DRAG ARMING / READY TO DRAG:
 * - Continue holding ~250ms additional -> DragReady (onDragReady)
 * - Move finger -> Dragging (onDragStart, onDrag, onDragEnd)
 *
 * `pointerInput(itemId)` is keyed ONLY by `itemId` so recompositions (e.g. from selectedBookIds updating)
 * DO NOT restart or cancel the gesture coroutine while the finger is pressed.
 */
@Composable
fun Modifier.reorderableItemGesture(
    itemId: String,
    isSelectedModeActive: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragReady: (itemId: String) -> Unit = {},
    onDragStart: (itemId: String) -> Unit,
    onDrag: (dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit
): Modifier {
    val currentSelectedModeActive by rememberUpdatedState(isSelectedModeActive)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnDragReady by rememberUpdatedState(onDragReady)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    return this.pointerInput(itemId) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            val touchSlop = viewConfiguration.touchSlop
            val initialPosition = down.position

            var gestureState = ReorderGestureState.Pressing

            // Stage 1: Wait up to ~450ms for movement or release to trigger selection
            var selectTimeoutEvent: PointerInputChange? = null
            withTimeoutOrNull(450L) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change == null || !change.pressed) {
                        selectTimeoutEvent = change
                        break
                    }
                    if ((change.position - initialPosition).getDistance() > touchSlop) {
                        selectTimeoutEvent = change
                        break
                    }
                }
            }

            if (selectTimeoutEvent != null) {
                val change = selectTimeoutEvent!!
                if (!change.pressed) {
                    // Quick tap
                    change.consume()
                    currentOnTap()
                } else {
                    // Moved beyond touch slop before long press select timeout
                    if (currentSelectedModeActive) {
                        // Item was ALREADY selected before this touch -> Start dragging immediately
                        gestureState = ReorderGestureState.Dragging
                        currentOnDragStart(itemId)

                        val dragAmount = change.positionChange()
                        change.consume()
                        currentOnDrag(dragAmount)

                        while (true) {
                            val event = awaitPointerEvent()
                            val nextChange = event.changes.firstOrNull { it.id == pointerId }
                            if (nextChange == null || !nextChange.pressed) {
                                currentOnDragEnd()
                                break
                            }
                            val nextDragAmount = nextChange.positionChange()
                            nextChange.consume()
                            currentOnDrag(nextDragAmount)
                        }
                    }
                    // If not in selection mode, do NOT consume change -> let LazyRow/Grid/Column scroll!
                }
                return@awaitEachGesture
            }

            // --- STAGE 1 REACHED (~450ms holding still) ---
            gestureState = ReorderGestureState.Selected
            currentOnLongPress() // Selects the item

            // Stage 2: Wait an additional ~250ms for DragArming delay
            var armTimeoutEvent: PointerInputChange? = null
            withTimeoutOrNull(250L) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change == null || !change.pressed) {
                        armTimeoutEvent = change
                        break
                    }
                    if ((change.position - initialPosition).getDistance() > touchSlop) {
                        armTimeoutEvent = change
                        break
                    }
                }
            }

            if (armTimeoutEvent != null) {
                // Released or moved before 250ms arming delay finished -> Selection preserved, no drag!
                return@awaitEachGesture
            }

            // --- STAGE 2 REACHED (~700ms total holding still: 450ms + 250ms) ---
            gestureState = ReorderGestureState.DragReady
            currentOnDragReady(itemId) // Visual feedback for DragReady!

            // Stage 3: Listen for movement or release in DragReady state
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null || !change.pressed) {
                    if (gestureState == ReorderGestureState.Dragging) {
                        currentOnDragEnd()
                    }
                    currentOnDragReady("") // Clear DragReady state
                    break
                }

                val distance = (change.position - initialPosition).getDistance()
                if (distance > touchSlop || gestureState == ReorderGestureState.Dragging) {
                    if (gestureState != ReorderGestureState.Dragging) {
                        gestureState = ReorderGestureState.Dragging
                        currentOnDragStart(itemId)
                    }
                    val dragAmount = change.positionChange()
                    change.consume()
                    currentOnDrag(dragAmount)
                }
            }
        }
    }
}
