package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.AppRule
import com.example.data.model.GuardianSettings
import com.example.ui.components.RuleEditDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppPickerScreen(
    rules: Map<String, AppRule>,
    settings: GuardianSettings,
    onToggleAppRestricted: (packageName: String, appName: String, isRestricted: Boolean) -> Unit,
    onUpdateRule: (AppRule) -> Unit,
    onUnblockApp: (String) -> Unit
) {
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var filterRestrictedOnly by remember { mutableStateOf(false) }
    var editingRuleApp by remember { mutableStateOf<AppRule?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val launchablePackages = pm.queryIntentActivities(mainIntent, 0)
                .map { it.activityInfo.packageName }
                .toSet()

            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != context.packageName }
                .map { appInfo ->
                    val isLaunchable = launchablePackages.contains(appInfo.packageName)
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val icon = try {
                        pm.getApplicationIcon(appInfo)
                    } catch (_: Exception) {
                        null
                    }
                    InstalledAppItem(
                        packageName = appInfo.packageName,
                        appName = name,
                        isSystemApp = isSystem && !isLaunchable,
                        icon = icon
                    )
                }
                .filter { !it.isSystemApp || launchablePackages.contains(it.packageName) }
                .sortedBy { it.appName.lowercase() }

            withContext(Dispatchers.Main) {
                installedApps = apps
                isLoading = false
            }
        }
    }

    val filteredApps = installedApps.filter { app ->
        val matchesSearch = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)

        val isRestricted = rules[app.packageName]?.isRestricted == true
        val matchesFilter = !filterRestrictedOnly || isRestricted

        matchesSearch && matchesFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_search_field"),
            placeholder = { Text("Search installed games and apps...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search")
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter & Quick-add Chips
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = filterRestrictedOnly,
                onClick = { filterRestrictedOnly = !filterRestrictedOnly },
                label = { Text("Restricted (${rules.values.count { it.isRestricted }})") },
                leadingIcon = if (filterRestrictedOnly) {
                    { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                } else null
            )

            // eFootball Quick chip
            val eFootballPkg = "com.konami.pesam"
            val isEFootballRestricted = rules[eFootballPkg]?.isRestricted == true
            FilterChip(
                selected = isEFootballRestricted,
                onClick = {
                    val appName = installedApps.find { it.packageName == eFootballPkg }?.appName ?: "eFootball"
                    onToggleAppRestricted(eFootballPkg, appName, !isEFootballRestricted)
                },
                label = { Text("eFootball (PES)") },
                leadingIcon = {
                    Icon(Icons.Default.SportsSoccer, contentDescription = null, Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading installed apps...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "No apps found matching '$searchQuery'" else "No apps found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { appItem ->
                    val rule = rules[appItem.packageName]
                    val isRestricted = rule?.isRestricted == true
                    val isBlocked = rule?.isBlocked == true

                    AppItemCard(
                        appItem = appItem,
                        rule = rule,
                        isRestricted = isRestricted,
                        isBlocked = isBlocked,
                        onToggle = { enabled ->
                            onToggleAppRestricted(appItem.packageName, appItem.appName, enabled)
                        },
                        onConfigure = {
                            val activeRule = rule ?: AppRule(
                                packageName = appItem.packageName,
                                appName = appItem.appName,
                                isRestricted = true,
                                timeLimitSeconds = 60
                            )
                            editingRuleApp = activeRule
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Per-app rule edit dialog
    editingRuleApp?.let { rule ->
        RuleEditDialog(
            rule = rule,
            defaultMessage = settings.defaultWarningMessage,
            presetMessages = settings.presetMessages,
            onDismiss = { editingRuleApp = null },
            onSave = { updatedRule ->
                onUpdateRule(updatedRule)
            },
            onUnblock = {
                onUnblockApp(rule.packageName)
            }
        )
    }
}

@Composable
private fun AppItemCard(
    appItem: InstalledAppItem,
    rule: AppRule?,
    isRestricted: Boolean,
    isBlocked: Boolean,
    onToggle: (Boolean) -> Unit,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onConfigure)
            .testTag("app_item_${appItem.packageName}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            else if (isRestricted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            val icon = appItem.icon
            if (icon != null) {
                val bitmap = remember(icon) {
                    try {
                        icon.toBitmap(width = 96, height = 96).asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = appItem.appName,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    FallbackAppIcon(appItem.appName)
                }
            } else {
                FallbackAppIcon(appItem.appName)
            }

            Spacer(modifier = Modifier.width(14.dp))

            // App details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appItem.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (isBlocked) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = "BLOCKED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isRestricted) {
                        val limitLabel = when {
                            rule?.isInstantBlock == true -> "Instant"
                            rule != null && rule.timeLimitSeconds < 60 -> "${rule.timeLimitSeconds}s"
                            rule != null -> "${rule.timeLimitSeconds / 60}m limit"
                            else -> "1m limit"
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = limitLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Text(
                        text = appItem.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            // Configure button & Toggle switch
            IconButton(onClick = onConfigure) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Edit Rule",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Switch(
                checked = isRestricted,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("toggle_${appItem.packageName}")
            )
        }
    }
}

@Composable
private fun FallbackAppIcon(appName: String) {
    Surface(
        modifier = Modifier.size(46.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = appName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
