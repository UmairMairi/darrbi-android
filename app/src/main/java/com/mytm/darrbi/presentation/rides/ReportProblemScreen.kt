package com.mytm.darrbi.presentation.rides

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiPrimaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiSecondaryButton
import com.mytm.darrbi.core.designsystem.components.DarrbiTextField

private val COMPLAINT_CATEGORIES = listOf(
    R.string.complaint_registration,
    R.string.complaint_trip,
    R.string.complaint_topup,
    R.string.complaint_balance,
    R.string.complaint_refund,
    R.string.complaint_different_car,
    R.string.complaint_other,
)

/** Report a Problem: pick a category, describe it, attach a photo, submit (matches ride-android). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportProblemScreen(onBack: () -> Unit, onReported: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    var description by remember { mutableStateOf("") }
    var showPhotoSheet by remember { mutableStateOf(false) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { pickedUri = uri; pickedBitmap = null }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) { pickedBitmap = bitmap; pickedUri = null }
    }

    if (showPhotoSheet) {
        PhotoSourceSheet(
            onDismiss = { showPhotoSheet = false },
            onTakePicture = {
                showPhotoSheet = false
                cameraLauncher.launch(null)
            },
            onChooseGallery = {
                showPhotoSheet = false
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
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
            Text(stringResource(R.string.report_title), style = DarrbiTheme.typography.titleLarge, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.report_subtitle), style = DarrbiTheme.typography.body, color = DarrbiTheme.colors.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarrbiTheme.colors.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarrbiTheme.colors.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    COMPLAINT_CATEGORIES.forEachIndexed { index, res ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = index }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected == index,
                                onClick = { selected = index },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = DarrbiTheme.colors.primary,
                                    unselectedColor = DarrbiTheme.colors.outline,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(res), style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
                        }
                        if (index < COMPLAINT_CATEGORIES.lastIndex) HorizontalDivider(color = DarrbiTheme.colors.outline)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            DarrbiTextField(
                value = description,
                onValueChange = { description = it },
                label = stringResource(R.string.report_your_problem),
                singleLine = false,
                modifier = Modifier.height(120.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, DarrbiTheme.colors.outline, RoundedCornerShape(12.dp))
                        .clickable { showPhotoSheet = true },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        pickedBitmap != null -> Image(
                            bitmap = pickedBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        pickedUri != null -> AsyncImage(
                            model = pickedUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        else -> Icon(Icons.Filled.Add, null, tint = DarrbiTheme.colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.report_photo_info), style = DarrbiTheme.typography.caption, color = DarrbiTheme.colors.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    DarrbiSecondaryButton(text = stringResource(R.string.report_add_photo), onClick = { showPhotoSheet = true })
                }
            }
            Spacer(Modifier.height(24.dp))
            DarrbiPrimaryButton(
                text = stringResource(R.string.report_submit),
                onClick = onReported,
                enabled = description.isNotBlank(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Bottom sheet to pick a photo source: camera or gallery ("Picture of Your Cargo"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSourceSheet(
    onDismiss: () -> Unit,
    onTakePicture: () -> Unit,
    onChooseGallery: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DarrbiTheme.colors.surface) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.report_photo_sheet_title),
                    style = DarrbiTheme.typography.title,
                    color = DarrbiTheme.colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = DarrbiTheme.colors.onSurface,
                    modifier = Modifier.size(24.dp).clickable(onClick = onDismiss),
                )
            }
            Spacer(Modifier.height(16.dp))
            PhotoSourceRow(Icons.Filled.PhotoCamera, stringResource(R.string.report_take_picture), onTakePicture)
            Spacer(Modifier.height(8.dp))
            PhotoSourceRow(Icons.Filled.Image, stringResource(R.string.report_choose_gallery), onChooseGallery)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PhotoSourceRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = DarrbiTheme.colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(text = label, style = DarrbiTheme.typography.bodyMedium, color = DarrbiTheme.colors.onSurface)
    }
}
