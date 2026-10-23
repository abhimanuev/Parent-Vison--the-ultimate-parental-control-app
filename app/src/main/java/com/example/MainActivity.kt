package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GuardianRepository
import com.example.ui.components.ChangePinDialog
import com.example.ui.components.PinEntryDialog
import com.example.ui.screens.AppPickerScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val repository: GuardianRepository
        get() = (application as GuardianApplication).repository

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (repository.settingsFlow.value.masterEnabled) {
            com.example.service.GuardianNotificationManager.showActiveNotification(applicationContext)
        }

        setContent {
            MyApplicationTheme {
                val rules by repository.rulesFlow.collectAsStateWithLifecycle()
                val settings by repository.settingsFlow.collectAsStateWithLifecycle()

                var currentTab by remember { mutableIntStateOf(0) }
                var isParentUnlocked by remember { mutableStateOf(false) }
                var showPinDialog by remember { mutableStateOf(false) }
                var showChangePinDialog by remember { mutableStateOf(false) }
                var pendingActionAfterPin by remember { mutableStateOf<(() -> Unit)?>(null) }

                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                fun requireParentAuth(onAuthenticated: () -> Unit) {
                    if (isParentUnlocked) {
                        onAuthenticated()
                    } else {
                        pendingActionAfterPin = onAuthenticated
                        showPinDialog = true
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ScreenTime Guardian",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        if (isParentUnlocked) {
                                            isParentUnlocked = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Parent mode locked")
                                            }
                                        } else {
                                            requireParentAuth {
                                                isParentUnlocked = true
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Parent mode unlocked")
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("pin_lock_action")
                                ) {
                                    Icon(
                                        imageVector = if (isParentUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = if (isParentUnlocked) "Lock Parent Mode" else "Unlock Parent Mode",
                                        tint = if (isParentUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .testTag("main_navigation_bar"),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            NavigationBarItem(
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == 0) Icons.Default.Dashboard else Icons.Outlined.Dashboard,
                                        contentDescription = "Dashboard"
                                    )
                                },
                                label = { Text("Dashboard") },
                                modifier = Modifier.testTag("nav_tab_dashboard")
                            )

                            val monitoredCount = rules.values.count { it.isRestricted }
                            NavigationBarItem(
                                selected = currentTab == 1,
                                onClick = {
                                    requireParentAuth {
                                        currentTab = 1
                                    }
                                },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (monitoredCount > 0) {
                                                Badge { Text(monitoredCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (currentTab == 1) Icons.Default.Apps else Icons.Outlined.Apps,
                                            contentDescription = "Apps & Games"
                                        )
                                    }
                                },
                                label = { Text("App Rules") },
                                modifier = Modifier.testTag("nav_tab_apps")
                            )

                            NavigationBarItem(
                                selected = currentTab == 2,
                                onClick = {
                                    requireParentAuth {
                                        currentTab = 2
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (currentTab == 2) Icons.Default.Settings else Icons.Outlined.Settings,
                                        contentDescription = "Settings"
                                    )
                                },
                                label = { Text("Settings") },
                                modifier = Modifier.testTag("nav_tab_settings")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Crossfade(
                            targetState = currentTab,
                            label = "tab_transition"
                        ) { tab ->
                            when (tab) {
                                0 -> DashboardScreen(
                                    settings = settings,
                                    rules = rules,
                                    onToggleMaster = { enabled ->
                                        requireParentAuth {
                                            repository.setMasterEnabled(enabled)
                                            if (enabled) {
                                                com.example.service.GuardianNotificationManager.showActiveNotification(applicationContext)
                                            } else {
                                                com.example.service.GuardianNotificationManager.dismissNotification(applicationContext)
                                            }
                                        }
                                    },
                                    onUnblockApp = { pkg ->
                                        requireParentAuth {
                                            repository.unblockApp(pkg)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("App unblocked")
                                            }
                                        }
                                    },
                                    onResetAll = {
                                        requireParentAuth {
                                            repository.resetAllBlocked()
                                            scope.launch {
                                                snackbarHostState.showSnackbar("All sessions reset")
                                            }
                                        }
                                    },
                                    onNavigateToApps = {
                                        requireParentAuth {
                                            currentTab = 1
                                        }
                                    }
                                )

                                1 -> AppPickerScreen(
                                    rules = rules,
                                    settings = settings,
                                    onToggleAppRestricted = { pkg, name, restricted ->
                                        repository.setAppRestricted(pkg, name, restricted)
                                    },
                                    onUpdateRule = { updatedRule ->
                                        repository.updateRule(updatedRule)
                                    },
                                    onUnblockApp = { pkg ->
                                        repository.unblockApp(pkg)
                                    }
                                )

                                2 -> SettingsScreen(
                                    settings = settings,
                                    onUpdateDefaultMessage = { newMsg ->
                                        repository.updateDefaultMessage(newMsg)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Warning message updated")
                                        }
                                    },
                                    onToggleDailyReset = { enabled ->
                                        repository.setDailyAutoReset(enabled)
                                    },
                                    onChangePinRequest = {
                                        showChangePinDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                // PIN Entry Dialog
                if (showPinDialog) {
                    PinEntryDialog(
                        title = "Parent Authentication",
                        subtitle = "Enter 4-digit PIN to manage restrictions",
                        onDismiss = {
                            showPinDialog = false
                            pendingActionAfterPin = null
                        },
                        onSuccess = {
                            isParentUnlocked = true
                            showPinDialog = false
                            val action = pendingActionAfterPin
                            pendingActionAfterPin = null
                            action?.invoke()
                        },
                        verifyPin = { pin ->
                            repository.verifyPin(pin)
                        }
                    )
                }

                // Change PIN Dialog
                if (showChangePinDialog) {
                    ChangePinDialog(
                        currentPin = settings.pinHash,
                        onDismiss = { showChangePinDialog = false },
                        onPinChanged = { newPin ->
                            repository.setPin(newPin)
                            scope.launch {
                                snackbarHostState.showSnackbar("Parent PIN changed successfully")
                            }
                        }
                    )
                }
            }
        }
    }
}
