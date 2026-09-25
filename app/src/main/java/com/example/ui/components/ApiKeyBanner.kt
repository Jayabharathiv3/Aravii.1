package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.GeminiClient

@Composable
fun ApiKeyBanner(
    customApiKey: String,
    onSaveKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val resolvedKey = GeminiClient.getResolvedApiKey(customApiKey)
    var isExpanded by remember { mutableStateOf(false) }
    var tempKey by remember(customApiKey) { mutableStateOf(customApiKey) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (resolvedKey.isNotBlank()) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (resolvedKey.isNotBlank()) Icons.Default.Key else Icons.Default.Warning,
                        contentDescription = "API Key Status",
                        tint = if (resolvedKey.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (resolvedKey.isNotBlank()) "Gemini 2.5 Flash API: Active" else "Gemini API Key Required",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (resolvedKey.isNotBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("api_key_toggle_button")
                ) {
                    Text(if (isExpanded) "Hide" else "Configure")
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "Enter your Google Gemini API key or set it in AI Studio Secrets panel:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tempKey,
                            onValueChange = { tempKey = it },
                            placeholder = { Text("Paste AIzaSy... key", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("api_key_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSaveKey(tempKey)
                                isExpanded = false
                            },
                            modifier = Modifier.testTag("save_api_key_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save Key")
                        }
                    }
                }
            }
        }
    }
}
