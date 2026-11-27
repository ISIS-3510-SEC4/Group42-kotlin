package com.example.espoti.perf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Debug
import android.os.Trace
import android.util.Log
import java.io.File
import com.example.espoti.model.domain.ActivityType
import com.example.espoti.model.domain.MeetingEvent
import com.example.espoti.model.domain.MeetingLifecycle
import com.example.espoti.model.domain.Place
import com.example.espoti.model.domain.StatusMeeting
import com.example.espoti.model.toUiMeeting
import com.example.espoti.ui.components.formatDateTime

// ============================================================================
// MEETING TIME MICRO-BENCHMARK (debug builds only, Sprint 4 evidence)
// ----------------------------------------------------------------------------
// Runs on the phone (ART + JIT). Measures the per-meeting work the Meetings /
// Home screens do on every refresh and every card:
//   parseMeetingTime  (MeetingLifecycle)
//   toUiMeeting       (effective status + start time of one meeting)
//   formatDateTime    (date label of a card)
// Logs median ns/op and allocations/op (tag EspotiBench) and wraps each case
// in a trace section ("bench:<name>") so it also shows up in Perfetto.
// Same data and same calls before and after the optimization.
//   adb shell am broadcast -n com.example.espoti/.perf.BenchmarkReceiver
// Recomposition counts (RecompositionTracer): --es cmd trace-on | counts-reset | counts-dump
//   adb logcat -s EspotiBench   (also appended to <external files>/bench.txt)
// ============================================================================
class BenchmarkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val dir = context.getExternalFilesDir(null)
        when (intent.getStringExtra("cmd") ?: "bench") {
            // Composition counting + per-composable trace sections.
            "trace-on" -> RecompositionTracer.install()
            "counts-reset" -> RecompositionTracer.reset()
            "counts-dump" -> File(dir, "recompositions.txt")
                .writeText(RecompositionTracer.dump().joinToString("\n", postfix = "\n"))
            else -> Thread({
                runCatching { MeetingTimeBenchmark(File(dir, "bench.txt")).run() }
                    .onFailure { Log.e(TAG, "failed", it) }
            }, "bench").start()
        }
    }

    companion object {
        const val TAG = "EspotiBench"
    }
}

private class MeetingTimeBenchmark(private val out: File) {

    private fun report(line: String) {
        Log.i(BenchmarkReceiver.TAG, line)
        out.appendText(line + "\n")
    }

    private val now = System.currentTimeMillis()
    private val day = 24 * 60 * 60 * 1000L

    /** 100 meetings: 80 % picker times ("HH:mm"), 10 % legacy text, 10 % without time. */
    private val events: List<MeetingEvent> = List(100) { i ->
        val time = when (i % 10) {
            8 -> "6:30 pm"
            9 -> ""
            else -> "%02d:%02d".format(8 + i % 12, (i * 5) % 60)
        }
        MeetingEvent(
            id = "m$i",
            participantIds = listOf("me", "a$i"),
            status = if (i % 7 == 0) StatusMeeting.VOTING else StatusMeeting.UPCOMING,
            placeId = "p$i",
            activity = ActivityType.entries[i % ActivityType.entries.size],
            date = (now / day + (i % 30) - 15) * day,
            time = time,
            votingDeadline = now + day,
            votes = mapOf("me" to "p$i")
        )
    }
    private val times = events.map { it.time }
    private val place = Place(id = "p", name = "Cafe Central")
    private val starts = events.mapNotNull { MeetingLifecycle.startsAt(it.date, it.time) }

    fun run() {
        report("start ${java.time.Instant.ofEpochMilli(now)} (${events.size} meetings)")
        measure("parseMeetingTime", times.size) {
            var sink = 0
            for (t in times) sink += MeetingLifecycle.parseMeetingTime(t)?.hour ?: 0
            sink
        }
        measure("toUiMeeting", events.size) {
            var sink = 0
            for (e in events) sink += e.toUiMeeting("me", place, now).participantCount
            sink
        }
        measure("formatDateTime", starts.size) {
            var sink = 0
            for (s in starts) sink += formatDateTime(s).length
            sink
        }
        // Same process, same moment: copies of the code before Sprint 4 (A/B).
        measure("legacy parse", times.size) {
            var sink = 0
            for (t in times) sink += Legacy.parseMeetingTime(t)?.hour ?: 0
            sink
        }
        measure("legacy format", starts.size) {
            var sink = 0
            for (s in starts) sink += Legacy.formatDateTime(s).length
            sink
        }
        report("done")
    }

    /**
     * Warm-up (lets the JIT compile), then [RUNS] timed runs of [ITERATIONS]
     * passes; median ns per item and allocations per item.
     */
    @Suppress("DEPRECATION")
    private fun measure(name: String, itemsPerRun: Int, block: () -> Int) {
        var sink = 0
        repeat(WARMUP) { sink += block() }
        val nanos = LongArray(RUNS)
        Debug.startAllocCounting()
        Debug.resetThreadAllocCount()
        Trace.beginSection("bench:$name")
        try {
            for (r in 0 until RUNS) {
                val start = System.nanoTime()
                repeat(ITERATIONS) { sink += block() }
                nanos[r] = System.nanoTime() - start
            }
        } finally {
            Trace.endSection()
        }
        val allocs = Debug.getThreadAllocCount()
        Debug.stopAllocCounting()
        nanos.sort()
        val ops = ITERATIONS.toLong() * itemsPerRun
        report(
            "%-17s median %,7d ns/op | p90 %,7d ns/op | %5.1f allocs/op | sink %d".format(
                name, nanos[RUNS / 2] / ops, nanos[RUNS * 9 / 10] / ops,
                allocs.toDouble() / (ops * RUNS), sink and 1
            )
        )
    }

    private companion object {
        const val WARMUP = 300
        const val RUNS = 15
        const val ITERATIONS = 100
    }
}

/** The implementations before the Sprint 4 micro-optimization, for A/B runs. */
private object Legacy {
    private val TIME_PATTERNS = listOf("H:mm", "h:mm a", "h a", "ha", "h:mma")
        .map { java.time.format.DateTimeFormatter.ofPattern(it, java.util.Locale.US) }

    fun parseMeetingTime(text: String): java.time.LocalTime? {
        val normalized = text.trim().uppercase(java.util.Locale.US)
        if (normalized.isEmpty()) return null
        return TIME_PATTERNS.firstNotNullOfOrNull { pattern ->
            runCatching { java.time.LocalTime.parse(normalized, pattern) }.getOrNull()
        }
    }

    fun formatDateTime(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm", java.util.Locale.US))
}
