package com.aritxonly.myhypermodifier

import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import java.lang.reflect.Method
import kotlin.math.abs

/**
 * Derives a "reading down / reading up" signal from whatever list the host is actually showing so
 * the injected dock slides away while the user pages down and returns on the first upward scroll.
 *
 * Tabbed hosts (Alipay, Mi Home) keep one scroller per page, so a single cached target only ever
 * worked for the page that happened to be sampled first. Instead every candidate scroller that
 * currently occupies real screen space is sampled, the biggest visible movement drives the
 * direction, and the largest visible list decides the "at the top" reset.
 *
 * Sampling rides the host's existing pre-draw pass: nothing is registered on the host tree, and
 * reading a few ints costs far less than the backdrop capture already running on that frame.
 */
internal class ScrollDrivenNavigationVisibility(private val root: ViewGroup) {
    private var candidates: List<View> = emptyList()
    private var refreshedAt = 0L
    private var lastSignature = "?"
    private var probed = false
    private val lastOffsets = HashMap<View, Int>()
    private var travel = 0
    private val visibleRect = Rect()

    /** True while the dock should stay tucked away. */
    var hidden: Boolean = false
        private set

    fun onFrame(now: Long): Boolean {
        if (!probed) {
            probed = true
            Log.d(TAG, "scroll probe: available=$available root=${root.width}x${root.height}")
        }
        if (!available) return false
        if (now - refreshedAt >= REFRESH_INTERVAL_MS || candidates.any { !it.isAttachedToWindow }) {
            refresh(now)
        }
        if (candidates.isEmpty()) return hidden
        var moving = 0
        var movingView: View? = null
        for (view in candidates) {
            val offset = offsetMethod.viewInt(view)
            val previous = lastOffsets.put(view, offset)
            if (previous == null) continue
            val delta = offset - previous
            if (abs(delta) > abs(moving)) {
                moving = delta
                movingView = view
            }
        }
        // A page switch lands on a list that has never been sampled; treat its position as truth so
        // switching to a page that is already scrolled does not leave the dock stranded mid-animation.
        val primary = candidates.firstOrNull() ?: return hidden
        if (movingView == null) {
            if (offsetMethod.viewInt(primary) <= 0) {
                travel = 0
                hidden = false
            }
            return hidden
        }
        val offset = offsetMethod.viewInt(movingView)
        if (offset <= 0) {
            travel = 0
            hidden = false
            return hidden
        }
        // A direction flip restarts accumulation so a jittery fling cannot toggle the dock.
        if ((moving > 0) != (travel > 0)) travel = 0
        travel = (travel + moving).coerceIn(-UP_THRESHOLD_PX, DOWN_THRESHOLD_PX)
        if (travel >= DOWN_THRESHOLD_PX) hidden = true
        else if (travel <= -UP_THRESHOLD_PX) hidden = false
        return hidden
    }

    fun reset() {
        travel = 0
        hidden = false
        lastOffsets.clear()
        refreshedAt = 0L
    }

    private fun refresh(now: Long) {
        refreshedAt = now
        candidates = findScrollables()
        val signature = candidates.joinToString("|") { "${it.javaClass.simpleName}:${it.height}" }
        if (signature != lastSignature) {
            lastSignature = signature
            if (LOG_CHANGES) {
                Log.d(
                    TAG,
                    "scroll candidates=${candidates.size}$signature" +
                        if (candidates.isEmpty()) describeScrollers() else "",
                )
            }
        }
        lastOffsets.keys.retainAll(candidates.toSet())
    }

    /** One-off tree dump, only when nothing matched, so the next build can target the right view. */
    private fun describeScrollers(): String {
        val builder = StringBuilder()
        var count = 0
        fun visit(view: View, depth: Int) {
            if (depth > MAX_DEPTH || count >= MAX_CANDIDATES + 4) return
            val range = rangeMethod.viewInt(view)
            val extent = extentMethod.viewInt(view)
            if (range > extent) {
                val visible = runCatching { view.getGlobalVisibleRect(visibleRect) }.getOrDefault(false)
                builder.append("\n  ").append(view.javaClass.name)
                    .append(" h=").append(view.height)
                    .append(" r=").append(range)
                    .append(" e=").append(extent)
                    .append(" vis=").append(if (visible) visibleRect.height() else -1)
                    .append(" shown=").append(view.isShown)
                count++
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index), depth + 1)
            }
        }
        visit(root, 0)
        return builder.toString()
    }

    /**
     * Hosts stack several scrollers (pagers, nested feeds, chat lists). Keep every one that is
     * really on screen and has travel left, longest first, so the biggest visible list wins.
     */
    private fun findScrollables(): List<View> {
        val found = ArrayList<View>(4)
        val minVisibleHeight = (root.height / 4).coerceAtLeast(1)
        fun visit(view: View, depth: Int) {
            if (depth > MAX_DEPTH || view.visibility != View.VISIBLE || !view.isShown) return
            val range = rangeMethod.viewInt(view)
            val extent = extentMethod.viewInt(view)
            if (range > extent && extent > 0) {
                val visible = runCatching { view.getGlobalVisibleRect(visibleRect) }.getOrDefault(false)
                if (visible && visibleRect.height() >= minVisibleHeight) found.add(view)
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index), depth + 1)
            }
        }
        visit(root, 0)
        return found
            .sortedByDescending { rangeMethod.viewInt(it) }
            .take(MAX_CANDIDATES)
    }

    private companion object {
        const val TAG = "MyHyperModifier"
        const val LOG_CHANGES = true

        /** ~6dp of downward travel before the dock leaves; ~3dp is enough to bring it back. */
        const val DOWN_THRESHOLD_PX = 18
        const val UP_THRESHOLD_PX = 9
        const val REFRESH_INTERVAL_MS = 400L
        const val MAX_DEPTH = 48
        const val MAX_CANDIDATES = 8

        val offsetMethod: Method? = viewMetric("computeVerticalScrollOffset")
        val rangeMethod: Method? = viewMetric("computeVerticalScrollRange")
        val extentMethod: Method? = viewMetric("computeVerticalScrollExtent")

        /** All three metrics are required for a direction decision, so they fail together. */
        val available: Boolean = offsetMethod != null && rangeMethod != null && extentMethod != null

        fun viewMetric(name: String): Method? = runCatching {
            View::class.java.getDeclaredMethod(name).apply { isAccessible = true }
        }.getOrNull()

        fun Method?.viewInt(view: View): Int =
            if (this == null) 0 else runCatching { invoke(view) as Int }.getOrDefault(0)
    }
}
