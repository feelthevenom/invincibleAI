package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Number,
    suffix: String? = null
) {
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(label, style = Typography.bodySmall, color = OnSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            Spacer(modifier = Modifier.height(4.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(SurfaceContainer, RoundedCornerShape(12.dp))
                .border(1.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = Typography.bodyLarge.copy(color = OnSurface, fontSize = 18.sp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    cursorBrush = SolidColor(Primary),
                    modifier = Modifier.weight(1f)
                )
                suffix?.let {
                    Text(it, style = Typography.bodyMedium, color = OnSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(label, style = Typography.bodySmall, color = OnSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        Spacer(modifier = Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            Box(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(SurfaceContainer, RoundedCornerShape(12.dp))
                    .border(1.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(value, style = Typography.bodyMedium, color = OnSurface)
                    Icon(Icons.Default.ArrowDropDown, null, tint = OnSurfaceVariant)
                }
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceContainerHigh)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = OnSurface) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingInfoCard(title: String, value: String, subtitle: String? = null, highlight: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlight) Primary.copy(0.08f) else Color.White.copy(0.05f),
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                if (highlight) Primary.copy(0.3f) else OutlineVariant.copy(0.3f),
                RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Text(title, style = Typography.labelMedium, color = if (highlight) Primary else OnSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = Typography.headlineMedium, color = if (highlight) Primary else OnSurface)
        subtitle?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(it, style = Typography.bodySmall, color = OnSurfaceVariant)
        }
    }
}

@Composable
fun GoalCard(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainer, RoundedCornerShape(12.dp))
            .border(1.dp, if (isSelected) Primary else OutlineVariant.copy(0.3f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = Typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
        if (isSelected) {
            Box(
                Modifier
                    .size(24.dp)
                    .background(Primary, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = OnPrimary, modifier = Modifier.size(16.dp))
            }
        } else {
            Box(Modifier.size(24.dp).border(2.dp, OutlineVariant, RoundedCornerShape(50)))
        }
    }
}

@Composable
fun MacroEditRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String = "g"
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = Typography.bodyMedium, color = OnSurface, modifier = Modifier.width(72.dp))
        OnboardingTextField(
            label = "",
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            suffix = unit
        )
    }
}
