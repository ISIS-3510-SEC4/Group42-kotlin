package com.example.espoti.perf

import android.os.Trace
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// ============================================================================
// RECOMPOSITION TRACER (debug / benchmark builds only, Sprint 4 evidence)
// ----------------------------------------------------------------------------
// Same hook Android Studio's composition tracing uses (Composer.setTracer):
// the Compose compiler calls traceEventStart only when a composable's body
// really runs (not when it is skipped). For each call we
//  - count it per composable ("UpcomingMeetingCard (MeetingsComponents.kt:32)")
//  - open an atrace section, so every composable is a slice in Perfetto.
// Only our own composables are counted (package com.example.espoti).
// ============================================================================
@OptIn(InternalComposeTracingApi::class)
object RecompositionTracer : CompositionTracer {

    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private val names = ConcurrentHashMap<String, String>()

    // Only the main thread composes; a plain stack is enough.
    private val open = ArrayDeque<Boolean>()

    fun install() = Composer.setTracer(this)

    fun reset() = counts.clear()

    /** "name count" lines, most executed first. */
    fun dump(): List<String> =
        counts.entries.sortedByDescending { it.value.get() }.map { "${it.value.get()}\t${it.key}" }

    override fun isTraceInProgress(): Boolean = true

    override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
        val ours = info.startsWith(APP_PACKAGE)
        open.addLast(ours)
        if (!ours) return
        val name = names.getOrPut(info) { shortName(info) }
        counts.getOrPut(name) { AtomicInteger() }.incrementAndGet()
        Trace.beginSection(name)
    }

    override fun traceEventEnd() {
        if (open.removeLastOrNull() == true) Trace.endSection()
    }

    /** "com.example.espoti.ui.components.UpcomingMeetingCard (MeetingsComponents.kt:32)" -> "UpcomingMeetingCard (MeetingsComponents.kt:32)". */
    private fun shortName(info: String): String {
        val function = info.substringBefore(" (")
        val location = info.substringAfter(" (", "").substringBefore(")")
        val simple = function.removePrefix(APP_PACKAGE).split('.')
            .filterNot { it.startsWith("ComposableSingletons") || it == "<anonymous>" }
            .takeLast(2).joinToString(".")
        return "$simple ($location)".take(MAX_SECTION)
    }

    private const val APP_PACKAGE = "com.example.espoti."
    private const val MAX_SECTION = 120
}
