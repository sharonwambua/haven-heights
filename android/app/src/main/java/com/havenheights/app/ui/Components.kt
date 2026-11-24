@file:OptIn(ExperimentalMaterial3Api::class)

package com.havenheights.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.havenheights.app.ui.theme.HavenColors

@Composable
fun HavenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = HavenColors.Cream,
            titleContentColor = HavenColors.Cocoa,
            navigationIconContentColor = HavenColors.Brown,
            actionIconContentColor = HavenColors.Brown,
        ),
    )
}

@Composable
fun HavenCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = HavenColors.Paper,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val colors = CardDefaults.cardColors(containerColor = container, contentColor = HavenColors.Cocoa)
    val border = BorderStroke(1.dp, HavenColors.Nude)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color) {
    Text(
        text,
        color = content,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun UrgencyPill(urgency: String) = when (urgency) {
    "HIGH" -> Pill(urgencyLabel(urgency), HavenColors.Burgundy, HavenColors.Cream)
    "MEDIUM" -> Pill(urgencyLabel(urgency), HavenColors.Nude, HavenColors.Cocoa)
    else -> Pill(urgencyLabel(urgency), HavenColors.SageMist, HavenColors.SageDeep)
}

@Composable
fun StatusPill(status: String) = when (status) {
    "OPEN" -> Pill(statusLabel(status), HavenColors.BurgundyMist, HavenColors.Burgundy)
    "IN_PROGRESS" -> Pill(statusLabel(status), HavenColors.Nude, HavenColors.Brown)
    else -> Pill(statusLabel(status), HavenColors.SageMist, HavenColors.SageDeep)
}

@Composable
fun HavenField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false,
    error: String? = null,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        isError = error != null,
        supportingText = (error ?: hint)?.let { text -> @Composable { Text(text) } },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HavenColors.Burgundy,
            focusedLabelColor = HavenColors.Burgundy,
            cursorColor = HavenColors.Burgundy,
            unfocusedBorderColor = HavenColors.NudeDeep,
            unfocusedLabelColor = HavenColors.Brown,
            focusedContainerColor = HavenColors.Paper,
            unfocusedContainerColor = HavenColors.Paper,
        ),
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = HavenColors.Burgundy,
            contentColor = HavenColors.Cream,
            disabledContainerColor = HavenColors.Nude,
            disabledContentColor = HavenColors.NudeDeep,
        ),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = HavenColors.Cream, strokeWidth = 2.dp)
        else Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Sage button for calm, secondary actions such as recording a reading. */
@Composable
fun SageButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = HavenColors.SageDeep,
            contentColor = Color.White,
            disabledContainerColor = HavenColors.Nude,
            disabledContentColor = HavenColors.NudeDeep,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (enabled) HavenColors.NudeDeep else HavenColors.Nude),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HavenColors.Brown, disabledContentColor = HavenColors.NudeDeep),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun havenChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = HavenColors.Burgundy,
    selectedLabelColor = HavenColors.Cream,
    containerColor = HavenColors.Paper,
    labelColor = HavenColors.Brown,
)

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = HavenColors.Brown, textAlign = TextAlign.Center)
    }
}

/** Dialog content that scrolls, so buttons stay reachable on small screens and with the keyboard open. */
@Composable
fun DialogBody(spacing: androidx.compose.ui.unit.Dp = 10.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier)

/** A block of "units" in the palette. The estate as a mark. */
@Composable
fun EstateMark(modifier: Modifier = Modifier) {
    val palette = listOf(HavenColors.Sage, HavenColors.Nude, HavenColors.NudeDeep, HavenColors.SageMist)
    Canvas(modifier.aspectRatio(5f / 3f)) {
        val cols = 5
        val rows = 3
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (cols - 1)) / cols
        val h = (size.height - gap * (rows - 1)) / rows
        for (r in 0 until rows) for (c in 0 until cols) {
            val lit = r == 1 && c == 2   // one lit window: someone is home
            drawRoundRect(
                color = if (lit) HavenColors.Cream else palette[(r * 2 + c * 3) % palette.size],
                topLeft = Offset(c * (w + gap), r * (h + gap)),
                size = Size(w, h),
                cornerRadius = CornerRadius(5.dp.toPx()),
            )
        }
    }
}
