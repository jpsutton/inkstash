package io.github.jpsutton.inkstash.mesh

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) blocks connecting to, and listening for, devices on the local network (Wi-Fi) unless the
 * app holds this runtime permission. Older versions have no such permission and always allow it.
 */
object LocalNetworkPermission {
    const val NAME = "android.permission.ACCESS_LOCAL_NETWORK"

    private const val FIRST_API = 37

    /** Whether this version of Android asks for the permission at all. */
    val needed: Boolean get() = Build.VERSION.SDK_INT >= FIRST_API

    fun granted(context: Context): Boolean =
        !needed || ContextCompat.checkSelfPermission(context, NAME) == PackageManager.PERMISSION_GRANTED
}
