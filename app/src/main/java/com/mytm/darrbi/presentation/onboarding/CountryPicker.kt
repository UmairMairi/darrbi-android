package com.mytm.darrbi.presentation.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytm.darrbi.R
import com.mytm.darrbi.core.designsystem.DarrbiTheme
import com.mytm.darrbi.core.designsystem.components.DarrbiTextField

data class Country(
    val name: String,
    val iso: String,
    val dialCode: String,
    /** Expected national (significant) number length, excluding the dial code. */
    val phoneLength: Int,
)

/** Unicode flag from an ISO-3166 alpha-2 code (two regional-indicator symbols). */
fun countryFlag(iso: String): String {
    if (iso.length != 2) return ""
    val base = 0x1F1E6
    val first = base + (iso[0].uppercaseChar() - 'A')
    val second = base + (iso[1].uppercaseChar() - 'A')
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    onDismiss: () -> Unit,
    onSelect: (Country) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            countries
        } else {
            countries.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.dialCode.contains(q) ||
                    it.iso.contains(q, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarrbiTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.country_picker_title),
                style = DarrbiTheme.typography.title,
                color = DarrbiTheme.colors.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            DarrbiTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.search_hint),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                items(filtered, key = { it.iso }) { country ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(country) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = countryFlag(country.iso), fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = country.name,
                            style = DarrbiTheme.typography.body,
                            color = DarrbiTheme.colors.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = country.dialCode,
                            style = DarrbiTheme.typography.bodyMedium,
                            color = DarrbiTheme.colors.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(color = DarrbiTheme.colors.outline)
                }
            }
        }
    }
}

val countries: List<Country> = listOf(
    Country("Saudi Arabia", "SA", "+966", 9),
    Country("United Arab Emirates", "AE", "+971", 9),
    Country("Kuwait", "KW", "+965", 8),
    Country("Qatar", "QA", "+974", 8),
    Country("Bahrain", "BH", "+973", 8),
    Country("Oman", "OM", "+968", 8),
    Country("Yemen", "YE", "+967", 9),
    Country("Egypt", "EG", "+20", 10),
    Country("Jordan", "JO", "+962", 9),
    Country("Lebanon", "LB", "+961", 8),
    Country("Iraq", "IQ", "+964", 10),
    Country("Syria", "SY", "+963", 9),
    Country("Palestine", "PS", "+970", 9),
    Country("Morocco", "MA", "+212", 9),
    Country("Algeria", "DZ", "+213", 9),
    Country("Tunisia", "TN", "+216", 8),
    Country("Libya", "LY", "+218", 9),
    Country("Sudan", "SD", "+249", 9),
    Country("Pakistan", "PK", "+92", 10),
    Country("India", "IN", "+91", 10),
    Country("Bangladesh", "BD", "+880", 10),
    Country("Sri Lanka", "LK", "+94", 9),
    Country("Nepal", "NP", "+977", 10),
    Country("Afghanistan", "AF", "+93", 9),
    Country("Iran", "IR", "+98", 10),
    Country("Turkey", "TR", "+90", 10),
    Country("Philippines", "PH", "+63", 10),
    Country("Indonesia", "ID", "+62", 11),
    Country("Malaysia", "MY", "+60", 9),
    Country("Singapore", "SG", "+65", 8),
    Country("Thailand", "TH", "+66", 9),
    Country("China", "CN", "+86", 11),
    Country("Japan", "JP", "+81", 10),
    Country("South Korea", "KR", "+82", 10),
    Country("United States", "US", "+1", 10),
    Country("United Kingdom", "GB", "+44", 10),
    Country("Canada", "CA", "+1", 10),
    Country("Germany", "DE", "+49", 11),
    Country("France", "FR", "+33", 9),
    Country("Spain", "ES", "+34", 9),
    Country("Italy", "IT", "+39", 10),
    Country("Netherlands", "NL", "+31", 9),
    Country("Sweden", "SE", "+46", 9),
    Country("Norway", "NO", "+47", 8),
    Country("Russia", "RU", "+7", 10),
    Country("Australia", "AU", "+61", 9),
    Country("South Africa", "ZA", "+27", 9),
    Country("Nigeria", "NG", "+234", 10),
    Country("Kenya", "KE", "+254", 9),
    Country("Ethiopia", "ET", "+251", 9),
    Country("Brazil", "BR", "+55", 11),
    Country("Mexico", "MX", "+52", 10),
)
