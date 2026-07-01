package com.mytm.darrbi.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytm.darrbi.shared.theme.DarrbiTheme

/**
 * Root of the Compose Multiplatform app. The same composable renders on Android and iOS through the shared
 * [DarrbiTheme]. This is the first migrated surface — a branded home dashboard — proving the design system
 * (colors, locale-aware typography, fonts) runs identically on both platforms.
 */
@Composable
fun App() {
    DarrbiTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = DarrbiTheme.colors.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(32.dp))
                BrandWordmark()
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Good afternoon, Saad Al-Otaibi",
                    style = DarrbiTheme.typography.body,
                    color = DarrbiTheme.colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "What do you want today?",
                    style = DarrbiTheme.typography.titleLarge,
                    color = DarrbiTheme.colors.onSurface,
                )
                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ServiceCard(
                        title = "Derrbi Ride",
                        subtitle = "Set your own Fare",
                        icon = Icons.Filled.DirectionsCar,
                        tint = DarrbiTheme.colors.categoryTints[0],
                        modifier = Modifier.weight(1f),
                    )
                    ServiceCard(
                        title = "Car Rental",
                        subtitle = "Self Drive",
                        icon = Icons.Filled.DirectionsCar,
                        tint = DarrbiTheme.colors.categoryTints[1],
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ServiceCard(
                        title = "Cargo Service",
                        subtitle = "Large shipments",
                        icon = Icons.Filled.LocalShipping,
                        tint = DarrbiTheme.colors.categoryTints[2],
                        modifier = Modifier.weight(1f),
                    )
                    ServiceCard(
                        title = "Delivery Service",
                        subtitle = "Fast & Local",
                        icon = Icons.Filled.TwoWheeler,
                        tint = DarrbiTheme.colors.categoryTints[3],
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                WideServiceCard(
                    title = "City to City",
                    subtitle = "Schedule your rides",
                    icon = Icons.Filled.CalendarMonth,
                    tint = DarrbiTheme.colors.categoryTints[4],
                )

                Spacer(Modifier.height(24.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DarrbiTheme.colors.primary,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Compose Multiplatform • running on ${platformName()}",
                        style = DarrbiTheme.typography.bodyMedium,
                        color = DarrbiTheme.colors.onPrimary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun BrandWordmark() {
    val wordmark: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(color = DarrbiTheme.colors.primary, fontWeight = FontWeight.Black)) { append("D") }
        withStyle(SpanStyle(color = DarrbiTheme.colors.onSurface, fontWeight = FontWeight.Black)) { append("ERRBI") }
    }
    Text(text = wordmark, fontSize = 30.sp)
}

@Composable
private fun ServiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = tint, modifier = modifier.aspectRatio(1f)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = DarrbiTheme.colors.onSurface, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun WideServiceCard(title: String, subtitle: String, icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = tint, modifier = Modifier.fillMaxWidth().height(110.dp)) {
        Row(modifier = Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = DarrbiTheme.colors.onSurface, modifier = Modifier.size(40.dp))
            Spacer(Modifier.size(16.dp))
            Column {
                Text(title, style = DarrbiTheme.typography.title, color = DarrbiTheme.colors.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = DarrbiTheme.typography.label, color = DarrbiTheme.colors.onSurfaceVariant)
            }
        }
    }
}
