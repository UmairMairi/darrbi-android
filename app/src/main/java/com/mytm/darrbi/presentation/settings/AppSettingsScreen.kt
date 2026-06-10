package com.mytm.darrbi.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiSwitch

@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var faceIdEnabled by remember { mutableStateOf(false) }
    var showChangeLanguage by remember { mutableStateOf(false) }

    if (showChangeLanguage) {
        ChangeLanguageSheet(onDismiss = { showChangeLanguage = false })
    }
    Surface(modifier = Modifier.fillMaxSize(), color = DarrbiTheme.colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarrbiTheme.colors.surface)
                    .border(1.dp, DarrbiTheme.colors.outline, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cd_back), tint = DarrbiTheme.colors.onSurface)
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_settings_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = DarrbiTheme.colors.surface,
                border = BorderStroke(1.dp, DarrbiTheme.colors.outline),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showChangeLanguage = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_change_language),
                            style = DarrbiTheme.typography.bodyMedium,
                            color = DarrbiTheme.colors.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = DarrbiTheme.colors.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = DarrbiTheme.colors.outline)
                    ToggleRow(
                        label = stringResource(R.string.settings_notification),
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it },
                    )
                    HorizontalDivider(color = DarrbiTheme.colors.outline)
                    ToggleRow(
                        label = stringResource(R.string.settings_face_id),
                        checked = faceIdEnabled,
                        onCheckedChange = { faceIdEnabled = it },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
        Spacer(Modifier.weight(1f))
        DarrbiSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
