package io.github.jpsutton.inkstash.scan

import android.util.Log

/** Scan diagnostics, readable with `adb logcat -s inkstash.scan`. Texts come from the user's own cards. */
object ScanLog {
    private const val TAG = "inkstash.scan"

    /** Replaced in tests. */
    var sink: (String) -> Unit = { Log.i(TAG, it) }

    fun i(message: String) = sink(message)
}
