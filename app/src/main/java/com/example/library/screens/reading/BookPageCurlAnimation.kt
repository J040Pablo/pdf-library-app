package com.example.library.screens.reading

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

fun interface PageBitmapProvider {
    suspend fun getPage(pageIndex: Int, widthPx: Int, heightPx: Int): ImageBitmap?
}

private enum class CurlDirection { FORWARD, BACKWARD }

private const val SETTLE_MS_MIN = 380
private const val SETTLE_MS_MAX = 560
private const val COMPLETE_THRESHOLD = 0.36f
private const val DRAG_SLOP_PX = 6f
private const val CORNER_SNAP_FRACTION = 0.22f

private val PaperBack = Color(0xFFF3EEE4)
private val PaperEdge = Color(0xFFE4D9C8)

/**
 * Same layout math as [ContentScale.Fit]: uniform scale, centered in [container].
 */
internal fun fittedContentRect(
    contentWidth: Float,
    contentHeight: Float,
    containerWidth: Float,
    containerHeight: Float
): Rect {
    if (contentWidth <= 0f || contentHeight <= 0f ||
        containerWidth <= 0f || containerHeight <= 0f
    ) {
        return Rect(0f, 0f, containerWidth.coerceAtLeast(0f), containerHeight.coerceAtLeast(0f))
    }
    val contentAspect = contentWidth / contentHeight
    val containerAspect = containerWidth / containerHeight
    val drawW: Float
    val drawH: Float
    if (contentAspect > containerAspect) {
        drawW = containerWidth
        drawH = containerWidth / contentAspect
    } else {
        drawH = containerHeight
        drawW = containerHeight * contentAspect
    }
    val left = (containerWidth - drawW) / 2f
    val top = (containerHeight - drawH) / 2f
    return Rect(left, top, left + drawW, top + drawH)
}

/**
 * Realistic flexible-sheet page curl.
 *
 * - Drag from anywhere (center or edges); origin sits on the left/right edge
 *   at the finger's Y (corners snap when near top/bottom).
 * - Tip follows the finger; release past the threshold finishes the full turn
 *   before the page index changes.
 * - Curl flap is opaque paper underside only (no mirrored front content).
 * - Page sizing matches Slide mode: native bitmap aspect + ContentScale.Fit.
 */
@Composable
fun BookPageCurlAnimation(
    currentPage: Int,
    pageCount: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pageProvider: PageBitmapProvider,
    curlEnabled: Boolean = true,
    /** Extra inset around the Fit area. Prefer 0 to match Slide mode margins. */
    pageMargin: Dp = 0.dp,
    bookSurfaceColor: Color = Color(0xFF0E0E0E)
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val marginPx = with(density) { pageMargin.toPx() }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var currentBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var nextBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var prevBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var direction by remember { mutableStateOf(CurlDirection.FORWARD) }
    var isCornerFold by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var tip by remember { mutableStateOf(Offset.Zero) }
    val tipAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    // Available area after optional margin (Slide uses the full viewport).
    val availableRect = remember(viewportSize, marginPx) {
        val w = viewportSize.width.toFloat()
        val h = viewportSize.height.toFloat()
        if (w <= 0f || h <= 0f) Rect.Zero
        else Rect(marginPx, marginPx, w - marginPx, h - marginPx)
    }

    // Gesture + fold bounds = fitted page rect (same as Slide ContentScale.Fit).
    val pageRect = remember(currentBitmap, availableRect) {
        val bmp = currentBitmap
        if (bmp == null || availableRect == Rect.Zero) availableRect
        else fittedContentRect(
            contentWidth = bmp.width.toFloat(),
            contentHeight = bmp.height.toFloat(),
            containerWidth = availableRect.width,
            containerHeight = availableRect.height
        ).translate(availableRect.left, availableRect.top)
    }

    val latestProvider = rememberUpdatedState(pageProvider)
    val latestOnPageChanged = rememberUpdatedState(onPageChanged)

    // Load at native aspect (width/height 0) — same as Slide; Fit handles display.
    LaunchedEffect(currentPage, viewportSize) {
        if (viewportSize.width <= 0 || viewportSize.height <= 0) return@LaunchedEffect
        settleJob?.cancel()
        isDragging = false
        isSettling = false
        isCornerFold = false
        tip = Offset.Zero
        origin = Offset.Zero

        val provider = latestProvider.value
        provider.getPage(currentPage, 0, 0)?.let { currentBitmap = it }
        nextBitmap = if (currentPage + 1 < pageCount) {
            provider.getPage(currentPage + 1, 0, 0)
        } else null
        prevBitmap = if (currentPage - 1 >= 0) {
            provider.getPage(currentPage - 1, 0, 0)
        } else null
    }

    fun pickOrigin(page: Rect, touch: Offset, dir: CurlDirection): Offset {
        val activeEdgeX = if (dir == CurlDirection.FORWARD) page.right else page.left
        val dTop = hypot(touch.x - activeEdgeX, touch.y - page.top)
        val dBottom = hypot(touch.x - activeEdgeX, touch.y - page.bottom)
        val radius = min(page.width, page.height) * 0.22f
        return when {
            dTop < radius -> Offset(activeEdgeX, page.top)
            dBottom < radius -> Offset(activeEdgeX, page.bottom)
            else -> Offset(activeEdgeX, touch.y.coerceIn(page.top + 5f, page.bottom - 5f))
        }
    }

    fun constrainTip(page: Rect, o: Offset, raw: Offset, dir: CurlDirection): Offset {
        val pad = page.width * 0.08f
        var x = raw.x.coerceIn(page.left - page.width * 0.15f, page.right + page.width * 0.15f)
        var y = raw.y.coerceIn(page.top - pad, page.bottom + pad)
        when (dir) {
            CurlDirection.FORWARD -> x = min(x, page.right - 1f)
            CurlDirection.BACKWARD -> x = max(x, page.left + 1f)
        }
        val maxDist = page.width * 1.05f
        val dx = x - o.x
        val dy = y - o.y
        val dist = hypot(dx, dy)
        if (dist > maxDist && dist > 0f) {
            val s = maxDist / dist
            x = o.x + dx * s
            y = o.y + dy * s
        }
        return Offset(x, y)
    }

    fun progressOf(page: Rect, o: Offset, t: Offset, dir: CurlDirection): Float {
        val full = page.width.coerceAtLeast(1f)
        return when (dir) {
            CurlDirection.FORWARD -> ((o.x - t.x) / full).coerceIn(0f, 1.15f)
            CurlDirection.BACKWARD -> ((t.x - o.x) / full).coerceIn(0f, 1.15f)
        }
    }

    fun completeTip(page: Rect, o: Offset, dir: CurlDirection): Offset {
        return when (dir) {
            CurlDirection.FORWARD -> Offset(2f * page.left - o.x, o.y)
            CurlDirection.BACKWARD -> Offset(2f * page.right - o.x, o.y)
        }
    }

    val drawTip = if (isSettling) tipAnim.value else tip
    val curlActive = (isDragging || isSettling) &&
        hypot(drawTip.x - origin.x, drawTip.y - origin.y) > 4f

    fun beginSettle(
        page: Rect,
        o: Offset,
        fromTip: Offset,
        dir: CurlDirection,
        canComplete: Boolean,
        destPage: Int
    ) {
        settleJob?.cancel()
        settleJob = scope.launch {
            tipAnim.snapTo(fromTip)
            tip = fromTip
            isDragging = false
            isSettling = true

            val target = if (canComplete) completeTip(page, o, dir) else o
            val remaining = hypot(target.x - fromTip.x, target.y - fromTip.y) /
                page.width.coerceAtLeast(1f)
            val duration = (
                SETTLE_MS_MIN +
                    (SETTLE_MS_MAX - SETTLE_MS_MIN) * remaining.coerceIn(0.4f, 1f)
                ).toInt()

            tipAnim.animateTo(target, tween(duration, easing = FastOutSlowInEasing))

            if (canComplete) {
                when (dir) {
                    CurlDirection.FORWARD -> nextBitmap?.let { currentBitmap = it }
                    CurlDirection.BACKWARD -> prevBitmap?.let { currentBitmap = it }
                }
                tip = o
                tipAnim.snapTo(o)
                isSettling = false
                latestOnPageChanged.value(destPage)
            } else {
                tip = o
                tipAnim.snapTo(o)
                isSettling = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bookSurfaceColor)
            .onSizeChanged { viewportSize = it }
            .graphicsLayer()
            .pointerInput(pageRect, currentPage, pageCount, curlEnabled) {
                if (!curlEnabled || pageRect.width <= 0f) return@pointerInput
                awaitEachGesture {
                    if (isSettling) return@awaitEachGesture
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (currentEvent.changes.size > 1) return@awaitEachGesture

                    val start = down.position
                    if (!pageRect.contains(start)) return@awaitEachGesture

                    var dragging = false
                    var lastVelocity = Offset.Zero

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1) {
                                if (dragging) {
                                    isDragging = false
                                    tip = origin
                                }
                                break
                            }
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                if (dragging) {
                                    val finalTip = constrainTip(pageRect, origin, tip, direction)
                                    tip = finalTip
                                    val prog = progressOf(pageRect, origin, finalTip, direction)
                                    val flingBoost = when (direction) {
                                        CurlDirection.FORWARD -> lastVelocity.x < -2.0f
                                        CurlDirection.BACKWARD -> lastVelocity.x > 2.0f
                                    }
                                    val shouldComplete =
                                        prog >= COMPLETE_THRESHOLD || (prog >= 0.18f && flingBoost)
                                    val destPage = when (direction) {
                                        CurlDirection.FORWARD -> currentPage + 1
                                        CurlDirection.BACKWARD -> currentPage - 1
                                    }
                                    val canComplete = shouldComplete && destPage in 0 until pageCount
                                    beginSettle(
                                        page = pageRect,
                                        o = origin,
                                        fromTip = finalTip,
                                        dir = direction,
                                        canComplete = canComplete,
                                        destPage = destPage
                                    )
                                }
                                break
                            }

                            val pos = change.position
                            val delta = change.positionChange()
                            lastVelocity = delta

                            if (!dragging) {
                                if (hypot(delta.x, delta.y) < DRAG_SLOP_PX) continue
                                val decided = when {
                                    abs(delta.x) >= abs(delta.y) * 0.5f && delta.x < 0f &&
                                        currentPage + 1 < pageCount -> CurlDirection.FORWARD
                                    abs(delta.x) >= abs(delta.y) * 0.5f && delta.x > 0f &&
                                        currentPage - 1 >= 0 -> CurlDirection.BACKWARD
                                    start.x >= pageRect.center.x && currentPage + 1 < pageCount ->
                                        CurlDirection.FORWARD
                                    start.x < pageRect.center.x && currentPage - 1 >= 0 ->
                                        CurlDirection.BACKWARD
                                    currentPage + 1 < pageCount -> CurlDirection.FORWARD
                                    currentPage - 1 >= 0 -> CurlDirection.BACKWARD
                                    else -> null
                                } ?: break

                                direction = decided
                                origin = pickOrigin(pageRect, start, direction)
                                tip = constrainTip(pageRect, origin, start, direction)
                                isDragging = true
                                isSettling = false
                                dragging = true
                            }

                            tip = constrainTip(pageRect, origin, pos, direction)
                            change.consume()
                        }
                    } catch (_: Exception) {
                        isDragging = false
                        isSettling = false
                        tip = origin
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val cb = currentBitmap
        val under = when {
            !curlActive -> null
            direction == CurlDirection.FORWARD -> nextBitmap
            else -> prevBitmap
        }
        val localOrigin = origin
        val localTip = drawTip

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            if (cb == null) return@Canvas

            drawRect(Color.White)

            fun drawPageFitted(image: ImageBitmap) {
                val fit = fittedContentRect(
                    contentWidth = image.width.toFloat(),
                    contentHeight = image.height.toFloat(),
                    containerWidth = availableRect.width,
                    containerHeight = availableRect.height
                ).translate(availableRect.left, availableRect.top)
                val dstOffset = IntOffset(
                    fit.left.toInt(),
                    fit.top.toInt()
                )
                val dstSize = IntSize(
                    fit.width.toInt().coerceAtLeast(1),
                    fit.height.toInt().coerceAtLeast(1)
                )
                drawImage(image, dstOffset = dstOffset, dstSize = dstSize)
            }

            val localPage = if (pageRect.width > 0f && pageRect.height > 0f) {
                pageRect
            } else {
                fittedContentRect(
                    contentWidth = cb.width.toFloat(),
                    contentHeight = cb.height.toFloat(),
                    containerWidth = availableRect.width,
                    containerHeight = availableRect.height
                ).translate(availableRect.left, availableRect.top)
            }
            val o = localOrigin
            val t = localTip

            if (!curlActive) {
                drawPageFitted(cb)
                return@Canvas
            }

            val fold = computeFold(o, t, localPage)
            if (fold == null) {
                if (under != null) drawPageFitted(under)
                else drawPageFitted(cb)
                return@Canvas
            }

            if (under != null) drawPageFitted(under)
            else drawRect(PaperBack, topLeft = localPage.topLeft, size = localPage.size)

            drawFoldContactShadow(fold, localPage)

            if (!fold.fullyPeeled) {
                clipPath(fold.flatPath) {
                    drawPageFitted(cb)
                }
            }

            if (!fold.fullyPeeled) {
                drawCurlFlap(fold)
            }

            if (!fold.fullyPeeled) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.28f),
                    start = fold.edgeA,
                    end = fold.edgeB,
                    strokeWidth = 3.2f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = fold.edgeA,
                    end = fold.edgeB,
                    strokeWidth = 1.6f
                )
            }
        }
    }
}

private data class FoldGeometry(
    val edgeA: Offset,
    val edgeB: Offset,
    val tip: Offset,
    val origin: Offset,
    val flatPath: Path,
    val curlPath: Path,
    val mid: Offset,
    val fullyPeeled: Boolean
)

private fun reflectPointAcrossFold(p: Offset, mid: Offset, nx: Float, ny: Float): Offset {
    val dot = (p.x - mid.x) * nx + (p.y - mid.y) * ny
    return Offset(p.x - 2f * dot * nx, p.y - 2f * dot * ny)
}

private fun computeFold(origin: Offset, tip: Offset, page: Rect): FoldGeometry? {
    val dx = tip.x - origin.x
    val dy = tip.y - origin.y
    val dist = hypot(dx, dy)
    if (dist < 2.5f) return null

    val mid = Offset((origin.x + tip.x) / 2f, (origin.y + tip.y) / 2f)
    val nx = dx / dist
    val ny = dy / dist

    val edges = intersectPerpBisector(page, mid, nx, ny)
    if (edges.size < 2) {
        val originFromMid = Offset(origin.x - mid.x, origin.y - mid.y)
        val pageCenter = Offset(page.center.x - mid.x, page.center.y - mid.y)
        val fullyPeeled = originFromMid.x * pageCenter.x + originFromMid.y * pageCenter.y < 0f
        if (fullyPeeled) {
            val empty = Path()
            return FoldGeometry(
                edgeA = Offset(page.left, page.top),
                edgeB = Offset(page.left, page.bottom),
                tip = tip,
                origin = origin,
                flatPath = empty,
                curlPath = empty,
                mid = mid,
                fullyPeeled = true
            )
        }
        return null
    }

    val edgeA = edges[0]
    val edgeB = edges[1]

    val peeledVertices = clipRectToHalfPlaneVertices(page, mid, origin)
    val peeledPath = Path().apply {
        if (peeledVertices.size >= 3) {
            moveTo(peeledVertices[0].x, peeledVertices[0].y)
            for (i in 1 until peeledVertices.size) {
                lineTo(peeledVertices[i].x, peeledVertices[i].y)
            }
            close()
        }
    }

    val pagePath = Path().apply { addRect(page) }
    val flat = Path().apply {
        op(pagePath, peeledPath, PathOperation.Difference)
    }

    val curlPath = Path().apply {
        if (peeledVertices.isNotEmpty()) {
            val firstReflect = reflectPointAcrossFold(peeledVertices[0], mid, nx, ny)
            moveTo(firstReflect.x, firstReflect.y)
            for (i in 1 until peeledVertices.size) {
                val r = reflectPointAcrossFold(peeledVertices[i], mid, nx, ny)
                lineTo(r.x, r.y)
            }
            close()
        }
    }

    val remainingApprox = flat.getBounds().let { it.width * it.height }
    val pageArea = page.width * page.height
    val fullyPeeled = remainingApprox < pageArea * 0.004f

    return FoldGeometry(
        edgeA = edgeA,
        edgeB = edgeB,
        tip = tip,
        origin = origin,
        flatPath = flat,
        curlPath = curlPath,
        mid = mid,
        fullyPeeled = fullyPeeled
    )
}

private fun clipRectToHalfPlaneVertices(page: Rect, mid: Offset, origin: Offset): List<Offset> {
    val hx = origin.x - mid.x
    val hy = origin.y - mid.y
    val corners = arrayOf(
        Offset(page.left, page.top),
        Offset(page.right, page.top),
        Offset(page.right, page.bottom),
        Offset(page.left, page.bottom)
    )

    fun side(p: Offset): Float = (p.x - mid.x) * hx + (p.y - mid.y) * hy

    val out = ArrayList<Offset>(6)
    for (i in corners.indices) {
        val cur = corners[i]
        val next = corners[(i + 1) % corners.size]
        val sc = side(cur)
        val sn = side(next)
        val curIn = sc >= -0.5f
        val nextIn = sn >= -0.5f
        if (curIn && nextIn) {
            out.add(next)
        } else if (curIn && !nextIn) {
            out.add(intersect(cur, next, mid, hx, hy))
        } else if (!curIn && nextIn) {
            out.add(intersect(cur, next, mid, hx, hy))
            out.add(next)
        }
    }
    return out
}

private fun intersect(a: Offset, b: Offset, mid: Offset, hx: Float, hy: Float): Offset {
    val ax = a.x - mid.x
    val ay = a.y - mid.y
    val bx = b.x - mid.x
    val by = b.y - mid.y
    val sa = ax * hx + ay * hy
    val sb = bx * hx + by * hy
    val t = sa / (sa - sb).let { if (abs(it) < 1e-6f) 1e-6f else it }
    return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}

private fun intersectPerpBisector(page: Rect, mid: Offset, nx: Float, ny: Float): List<Offset> {
    val c = nx * mid.x + ny * mid.y
    val pts = ArrayList<Offset>(4)

    fun addIfValid(p: Offset) {
        if (p.x in (page.left - 1.5f)..(page.right + 1.5f) &&
            p.y in (page.top - 1.5f)..(page.bottom + 1.5f)
        ) {
            val clamped = Offset(
                p.x.coerceIn(page.left, page.right),
                p.y.coerceIn(page.top, page.bottom)
            )
            if (pts.none { hypot(it.x - clamped.x, it.y - clamped.y) < 1.5f }) {
                pts.add(clamped)
            }
        }
    }

    if (abs(nx) > 1e-4f) {
        addIfValid(Offset((c - ny * page.top) / nx, page.top))
        addIfValid(Offset((c - ny * page.bottom) / nx, page.bottom))
    }
    if (abs(ny) > 1e-4f) {
        addIfValid(Offset(page.left, (c - nx * page.left) / ny))
        addIfValid(Offset(page.right, (c - nx * page.right) / ny))
    }

    if (pts.size >= 2) {
        var bestA = pts[0]
        var bestB = pts[1]
        var bestD = -1f
        for (i in pts.indices) {
            for (j in i + 1 until pts.size) {
                val d = hypot(pts[i].x - pts[j].x, pts[i].y - pts[j].y)
                if (d > bestD) {
                    bestD = d
                    bestA = pts[i]
                    bestB = pts[j]
                }
            }
        }
        return listOf(bestA, bestB)
    }
    return pts
}

private fun DrawScope.drawFoldContactShadow(fold: FoldGeometry, page: Rect) {
    if (fold.fullyPeeled) return

    clipPath(fold.curlPath) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent),
                start = fold.mid,
                end = fold.tip
            ),
            topLeft = Offset(page.left, page.top),
            size = Size(page.width, page.height)
        )
    }

    val fx = fold.edgeB.x - fold.edgeA.x
    val fy = fold.edgeB.y - fold.edgeA.y
    val fl = hypot(fx, fy).coerceAtLeast(1f)
    val towardUnder = Offset(fold.mid.x - fold.tip.x, fold.mid.y - fold.tip.y)
    val len = hypot(towardUnder.x, towardUnder.y).coerceAtLeast(1f)
    val ux = towardUnder.x / len * 22f
    val uy = towardUnder.y / len * 22f
    val sx = if (len < 2f) -fy / fl * 22f else ux
    val sy = if (len < 2f) fx / fl * 22f else uy
    val band = Path().apply {
        moveTo(fold.edgeA.x, fold.edgeA.y)
        lineTo(fold.edgeB.x, fold.edgeB.y)
        lineTo(fold.edgeB.x + sx, fold.edgeB.y + sy)
        lineTo(fold.edgeA.x + sx, fold.edgeA.y + sy)
        close()
    }
    drawPath(
        path = band,
        brush = Brush.linearGradient(
            colors = listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent),
            start = fold.mid,
            end = Offset(fold.mid.x + sx, fold.mid.y + sy)
        )
    )
}

private fun DrawScope.drawCurlFlap(fold: FoldGeometry) {
    clipPath(fold.curlPath) {
        drawRect(PaperBack)
        drawPath(
            path = fold.curlPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.16f),
                    Color.Transparent,
                    Color.White.copy(alpha = 0.35f),
                    Color.Black.copy(alpha = 0.12f)
                ),
                start = fold.mid,
                end = fold.tip
            )
        )
    }
    drawLine(
        color = PaperEdge,
        start = fold.edgeA,
        end = fold.edgeB,
        strokeWidth = 4.5f
    )
}
