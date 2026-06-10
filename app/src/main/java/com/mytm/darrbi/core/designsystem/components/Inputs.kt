package com.mytm.darrbi.core.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import com.mytm.darrbi.core.designsystem.DarrbiTheme

/** Rounded outlined field with a floating label; green border when focused, grey otherwise. */
@Composable
fun DarrbiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, style = DarrbiTheme.typography.label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        readOnly = readOnly,
        isError = isError,
        visualTransformation = visualTransformation,
        supportingText = supportingText?.let { text ->
            { Text(text = text, style = DarrbiTheme.typography.caption) }
        },
        textStyle = DarrbiTheme.typography.body,
        leadingIcon = leadingContent,
        trailingIcon = trailingContent,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DarrbiTheme.colors.primary,
            unfocusedBorderColor = DarrbiTheme.colors.inputBorder,
            errorBorderColor = DarrbiTheme.colors.error,
            focusedLabelColor = DarrbiTheme.colors.primary,
            unfocusedLabelColor = DarrbiTheme.colors.onSurfaceVariant,
            errorLabelColor = DarrbiTheme.colors.error,
            cursorColor = DarrbiTheme.colors.primary,
            focusedTextColor = DarrbiTheme.colors.onSurface,
            unfocusedTextColor = DarrbiTheme.colors.onSurface,
            focusedContainerColor = DarrbiTheme.colors.surface,
            unfocusedContainerColor = DarrbiTheme.colors.surface,
            errorContainerColor = DarrbiTheme.colors.surface,
            errorSupportingTextColor = DarrbiTheme.colors.error,
        ),
    )
}

/** A row of [length] single-digit cells backed by one hidden field; the next empty cell is highlighted. */
@Composable
fun DarrbiOtpInput(
    otp: String,
    onOtpChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 4,
) {
    val focusRequester = remember { FocusRequester() }
    BasicTextField(
        value = otp,
        onValueChange = { new -> if (new.length <= length && new.all { it.isDigit() }) onOtpChange(new) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(DarrbiTheme.colors.primary),
        modifier = modifier.focusRequester(focusRequester),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(length) { index ->
                    val char = otp.getOrNull(index)?.toString().orEmpty()
                    val active = index == otp.length
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) DarrbiTheme.colors.primary else DarrbiTheme.colors.inputBorder,
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = char,
                            style = DarrbiTheme.typography.title,
                            color = DarrbiTheme.colors.onSurface,
                        )
                    }
                }
            }
        },
    )
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
}
