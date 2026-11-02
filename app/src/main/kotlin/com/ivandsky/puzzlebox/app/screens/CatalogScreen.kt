package com.ivandsky.puzzlebox.app.screens

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.TokenCatalogScreen

// Ticket design-previews-08: the "Showkase" button opens the custom flat dark browser
// from :app's debug source set — still launched by class name so :app main keeps no
// compile-time reference to debug-only code, and the FLAG_DEBUGGABLE gate keeps the
// button out of release. The stock AAR activity stays merged but is no longer the
// target; it needs no intent extras from here (that requirement was stock-activity-only).
private const val SHOWKASE_BROWSER_ACTIVITY =
    "com.ivandsky.puzzlebox.app.debug.ShowkaseBrowserActivity"

@Composable
internal fun CatalogScreenWithShowkaseTrial() {
    val context = LocalContext.current
    val isDebuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    Box(Modifier.fillMaxSize()) {
        TokenCatalogScreen()
        if (isDebuggable) {
            Button(
                onClick = {
                    context.startActivity(
                        Intent().setClassName(context.packageName, SHOWKASE_BROWSER_ACTIVITY),
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Text("Showkase")
            }
        }
    }
}
