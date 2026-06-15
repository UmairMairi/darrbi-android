package com.mytm.darrbi.presentation.common

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** Whether fine OR coarse location is currently granted. */
fun Context.hasLocationPermission(): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

/** Opens this app's system settings page (so the user can enable a permanently-denied permission). */
fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Reactive location-permission handle: reflects the grant / permanent-denial and can ask the OS for it. */
@Stable
class LocationPermissionState internal constructor(
    isGrantedProvider: () -> Boolean,
    permanentlyDeniedProvider: () -> Boolean,
    private val onRequest: () -> Unit,
) {
    private val isGrantedProvider = isGrantedProvider
    private val permanentlyDeniedProvider = permanentlyDeniedProvider

    /** Reactive: reads here recompose when the grant changes. */
    val isGranted: Boolean get() = isGrantedProvider()

    /** True once the user has denied with "don't ask again" — the system no longer shows the dialog. */
    val isPermanentlyDenied: Boolean get() = permanentlyDeniedProvider()

    /** Shows the system permission dialog only when it isn't already granted. */
    fun requestIfNeeded() {
        if (!isGranted) onRequest()
    }
}

/**
 * Remembers a [LocationPermissionState] backed by an Activity-result launcher. Re-checks the grant on
 * ON_RESUME so returning from system Settings (with permission newly enabled) updates the state.
 */
@Composable
fun rememberLocationPermissionState(): LocationPermissionState {
    val context = LocalContext.current
    val activity = context.findActivity()
    var granted by remember { mutableStateOf(context.hasLocationPermission()) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val ok = result.values.any { it } || context.hasLocationPermission()
        granted = ok
        // A denial with no rationale to show means "don't ask again" (permanently denied).
        permanentlyDenied = !ok && activity != null &&
            LOCATION_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }

    // Returning from Settings (or any resume) re-reads the live grant.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = context.hasLocationPermission()
                granted = now
                if (now) permanentlyDenied = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember {
        LocationPermissionState(
            isGrantedProvider = { granted },
            permanentlyDeniedProvider = { permanentlyDenied },
            onRequest = { launcher.launch(LOCATION_PERMISSIONS) },
        )
    }
}

/** Instructions + "Open Settings" shown when location is permanently denied and must be enabled manually. */
@Composable
fun LocationPermissionDeniedDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit = {}) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = DarrbiTheme.colors.surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = DarrbiTheme.colors.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.location_denied_title),
                    style = DarrbiTheme.typography.title,
                    color = DarrbiTheme.colors.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.location_denied_message),
                    style = DarrbiTheme.typography.body,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                DarrbiPrimaryButton(text = stringResource(R.string.location_open_settings), onClick = onOpenSettings)
            }
        }
    }
}
