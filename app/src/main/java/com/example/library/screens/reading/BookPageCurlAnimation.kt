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
import androidx.compose.foundation.layout.padding
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
 * Realistic flexible-sheet page curl.
 *
 * - Drag from anywhere (center or edges); origin sits on the left/right edge
 *   at the finger's Y (corners snap when near top/bottom).
 * - Tip follows the finger; release past the threshold finishes the full turn
 *   before the page index changes.
 * - Curl flap is opaque paper underside only (no mirrored front content).
 */
@Composable
fun BookPageCurlAnimation(
    currentPage: Int,
    pageCount: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pageProvider: PageBitmapProvider,
    curlEnabled: Boolean = true,
    pageMargin: Dp = 20.dp,
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
    var origin by remember { mutableStateOf(Offset.Zero) }
    var tip by remember { mutableStateOf(Offset.Zero) }
    val tipAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    val pageRect = remember(viewportSize, marginPx) {
        val w = viewportSize.width.toFloat()
        val h = viewportSize.height.toFloat()
        if (w <= 0f || h <= 0f) Rect.Zero
        else Rect(marginPx, marginPx, w - marginPx, h - marginPx)
    }

    val pageWidthPx = pageRect.width.toInt().coerceAtLeast(1)
    val pageHeightPx = pageRect.height.toInt().coerceAtLeast(1)

    val latestProvider = rememberUpdatedState(pageProvider)
    val latestOnPageChanged = rememberUpdatedState(onPageChanged)

    LaunchedEffect(currentPage, pageWidthPx, pageHeightPx) {
        if (pageWidthPx <= 1 || pageHeightPx <= 1) return@LaunchedEffect
        // Reset curl state when the active page changes externally.
        settleJob?.cancel()
        isDragging = false
        isSettling = false
        tip = Offset.Zero
        origin = Offset.Zero

        val provider = latestProvider.value
        provider.getPage(currentPage, pageWidthPx, pageHeightPx)?.let { currentBitmap = it }
        nextBitmap = if (currentPage + 1 < pageCount) {
            provider.getPage(currentPage + 1, pageWidthPx, pageHeightPx)
        } else null
        prevBitmap = if (currentPage - 1 >= 0) {
            provider.getPage(currentPage - 1, pageWidthPx, pageHeightPx)
        } else null
    }

    /** Edge origin at finger Y; snap to a corner when near top/bottom. */
    fun pickOrigin(page: Rect, touch: Offset, dir: CurlDirection): Offset {
        val cornerBand = page.height * CORNER_SNAP_FRACTION
        val y = when {
            touch.y <= page.top + cornerBand -> page.top
            touch.y >= page.bottom - cornerBand -> page.bottom
            else -> touch.y.coerceIn(page.top + 1f, page.bottom - 1f)
        }
        return when (dir) {
            CurlDirection.FORWARD -> Offset(page.right, y)
            CurlDirection.BACKWARD -> Offset(page.left, y)
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
        // Keep tip from crossing too far past the origin (invalid fold).
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

    /**
     * Tip that places the fold exactly on the opposite edge — page fully peeled,
     * geometry still valid (mid on the far edge).
     */
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
            // Snap before flipping isSettling so the first settle frame is correct.
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
                // Hold the fully-peeled frame: show destination as the new current
                // surface, then clear curl, then notify — index changes last.
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
                                    // Drag from anywhere: nearer vertical half decides.
                                    start.x >= pageRect.center.x && currentPage + 1 < pageCount ->
                                        CurlDirection.FORWARD
                                    start.x < pageRect.center.x && currentPage - 1 >= 0 ->
                                        CurlDirection.BACKWARD
                                    currentPage + 1 < pageCount -> CurlDirection.FORWARD
                                    currentPage - 1 >= 0 -> CurlDirection.BACKWARD
                                    else -> null
                                } ?: break

                                direction = decided
                                // Origin fixed for this gesture (stable fold, no mid-drag jumps).
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
        val active = curlActive

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(pageMargin)
        ) {
            if (cb == null) return@Canvas
            val localPage = Rect(0f, 0f, size.width, size.height)
            val o = Offset(localOrigin.x - pageRect.left, localOrigin.y - pageRect.top)
            val t = Offset(localTip.x - pageRect.left, localTip.y - pageRect.top)
            val dst = IntSize(
                size.width.toInt().coerceAtLeast(1),
                size.height.toInt().coerceAtLeast(1)
            )

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = max(size.width, size.height) * 0.72f
                ),
                topLeft = Offset(-8f, -8f),
                size = Size(size.width + 16f, size.height + 16f)
            )

            if (!active) {
                drawImage(cb, dstSize = dst)
                drawRect(
                    color = Color.Black.copy(alpha = 0.18f),
                    topLeft = Offset(size.width - 3f, 0f),
                    size = Size(3f, size.height)
                )
                return@Canvas
            }

            val fold = computeFold(o, t, localPage)
            if (fold == null) {
                // Fully peeled (or tiny motion): show destination only.
                if (under != null) drawImage(under, dstSize = dst)
                else drawImage(cb, dstSize = dst)
                return@Canvas
            }

            // 1) Destination underneath — drawn once.
            if (under != null) drawImage(under, dstSize = dst)
            else drawRect(PaperBack)

            drawFoldContactShadow(fold, localPage)

            // 2) Remaining flat front of the current page (peeled region removed).
            if (!fold.fullyPeeled) {
                clipPath(fold.flatPath) {
                    drawImage(cb, dstSize = dst)
                }
            }

            // 3) Opaque paper underside of the turning sheet — never redraw front art.
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

/**
 * Classic page-curl fold: perpendicular bisector of origin→tip is the crease.
 * Peeled region = page ∩ half-plane containing the origin (reveals underneath).
 * Curl region = triangle edgeA–tip–edgeB (paper underside).
 */
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
        // Fold line no longer crosses the page — treat as fully turned.
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

    val peeled = clipRectToHalfPlane(page, mid, origin)
    val pagePath = Path().apply { addRect(page) }
    val flat = Path().apply {
        op(pagePath, peeled, PathOperation.Difference)
    }

    // Curl flap: from crease to tip (the folded-over paper).
    val curlPath = Path().apply {
        moveTo(edgeA.x, edgeA.y)
        lineTo(tip.x, tip.y)
        lineTo(edgeB.x, edgeB.y)
        close()
    }

    // If almost nothing remains flat, mark fully peeled for clean completion frames.
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

/** Page ∩ half-plane on the [origin] side of the fold through [mid]. */
private fun clipRectToHalfPlane(page: Rect, mid: Offset, origin: Offset): Path {
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

    return Path().apply {
        if (out.size >= 3) {
            moveTo(out[0].x, out[0].y)
            for (i in 1 until out.size) lineTo(out[i].x, out[i].y)
            close()
        }
    }
}

private fun intersect(
    a: Offset,
    b: Offset,
    mid: Offset,
    hx: Float,
    hy: Float
): Offset {
    val ax = a.x - mid.x
    val ay = a.y - mid.y
    val bx = b.x - mid.x
    val by = b.y - mid.y
    val sa = ax * hx + ay * hy
    val sb = bx * hx + by * hy
    val t = sa / (sa - sb).let { if (abs(it) < 1e-6f) 1e-6f else it }
    return Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}

private fun intersectPerpBisector(
    page: Rect,
    mid: Offset,
    nx: Float,
    ny: Float
): List<Offset> {
    // Line: nx*(x - mid.x) + ny*(y - mid.y) = 0
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
                colors = listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent),
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
    val ux = towardUnder.x / len * 18f
    val uy = towardUnder.y / len * 18f
    // Fallback if tip≈mid
    val sx = if (len < 2f) -fy / fl * 18f else ux
    val sy = if (len < 2f) fx / fl * 18f else uy
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
            colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent),
            start = fold.mid,
            end = Offset(fold.mid.x + sx, fold.mid.y + sy)
        )
    )
}

/**
 * Opaque paper underside only — never redraws the front-page bitmap
 * (fixes ghost/duplicate content on the folded flap).
 */
private fun DrawScope.drawCurlFlap(fold: FoldGeometry) {
    clipPath(fold.curlPath) {
        drawRect(PaperBack)

        // Soft thickness / curl shading along crease → tip.
        drawPath(
            path = fold.curlPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.14f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.10f)
                ),
                start = fold.edgeA,
                end = fold.tip
            )
        )

        // Specular highlight near the crease (paper catching light).
        drawPath(
            path = fold.curlPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.42f),
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                start = fold.mid,
                end = Offset(
                    fold.mid.x + (fold.tip.x - fold.mid.x) * 0.55f,
                    fold.mid.y + (fold.tip.y - fold.mid.y) * 0.55f
                )
            )
        )
    }

    // Paper edge thickness along the crease.
    drawLine(
        color = PaperEdge,
        start = fold.edgeA,
        end = fold.edgeB,
        strokeWidth = 4.5f
    )
}
