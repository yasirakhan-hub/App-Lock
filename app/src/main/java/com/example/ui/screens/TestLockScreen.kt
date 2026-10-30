package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.common.MainBottomTab
import com.example.ui.common.NeoBottomNavigationBar
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCard
import com.example.ui.common.NeoCircularButton
import com.example.ui.common.NeoGoldPadlockEmblem
import com.example.ui.common.NeoIconBadge
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.NeoStatusBadge
import com.example.ui.common.PinDots
import com.example.ui.common.PinKeypad
import com.example.ui.lock.LockActivity

data class TestAppProfile(
    val id: String,
    val name: String,
    val packageName: String,
    val category: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestLockScreen(
    onNavigateBack: () -> Unit,
    onNavigateToApps: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val testProfiles = listOf(
        TestAppProfile(
            id = "bank",
            name = "Secure Bank App",
            packageName = "com.sample.banking.secure",
            category = "Finance",
            icon = Icons.Default.AccountBalance
        ),
        TestAppProfile(
            id = "gallery",
            name = "Private Photos & Gallery",
            packageName = "com.sample.gallery.photos",
            category = "Media",
            icon = Icons.Default.PhotoCamera
        ),
        TestAppProfile(
            id = "chat",
            name = "Confidential Messenger",
            packageName = "com.sample.messenger.chat",
            category = "Communication",
            icon = Icons.Default.Chat
        )
    )

    var selectedProfile by remember { mutableStateOf(testProfiles[0]) }
    var simulatedPin by remember { mutableStateOf("12") }
    var simulatedStatus by remember { mutableStateOf("This is a simulated lock screen") }
    var showProfileSelector by remember { mutableStateOf(true) }

    Scaffold(
        containerColor = NeoPalette.BackgroundBase,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Lock Screen Simulator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NeoPalette.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("test_lock_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeoPalette.TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showProfileSelector = !showProfileSelector }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configure Target Profile",
                            tint = NeoPalette.TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NeoPalette.BackgroundBase
                )
            )
        },
        bottomBar = {
            NeoBottomNavigationBar(
                currentTab = MainBottomTab.HOME,
                onSelectTab = { tab ->
                    when (tab) {
                        MainBottomTab.HOME -> onNavigateBack()
                        MainBottomTab.APPS -> onNavigateToApps()
                        MainBottomTab.FILES -> onNavigateToFiles()
                        MainBottomTab.SETTINGS -> onNavigateToSettings()
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Nested Neomorphic Phone-Frame Lock Preview matching Panel 10 of the reference image
            NeoCard(
                shape = NeoShapes.LargeCard,
                accentBorder = NeoPalette.PrimaryGold.copy(alpha = 0.25f),
                elevation = 8.dp,
                contentPadding = PaddingValues(vertical = 22.dp, horizontal = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NeoGoldPadlockEmblem(
                        badgeSize = 68.dp,
                        isOpenShackle = false
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Enter PIN",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NeoPalette.TextPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    PinDots(
                        pinLength = 4,
                        enteredLength = simulatedPin.length,
                        isError = false
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    PinKeypad(
                        buttonSize = 58.dp,
                        onDigitClick = { digit ->
                            if (simulatedPin.length < 4) {
                                val next = simulatedPin + digit
                                simulatedPin = next
                                if (next.length == 4) {
                                    simulatedStatus = "Simulated PIN ($next) entered for ${selectedProfile.name}"
                                }
                            } else {
                                simulatedPin = digit
                                simulatedStatus = "This is a simulated lock screen"
                            }
                        },
                        onDeleteClick = {
                            if (simulatedPin.isNotEmpty()) {
                                simulatedPin = simulatedPin.dropLast(1)
                            }
                        },
                        extraLeftAction = {
                            NeoCircularButton(
                                onClick = {
                                    simulatedStatus = "Simulated Fingerprint verified for ${selectedProfile.name}"
                                },
                                size = 58.dp,
                                isGoldSolid = true
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Simulated Fingerprint",
                                    tint = NeoPalette.TextInverse,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Bottom pill badge matching Panel 10: "This is a simulated lock screen"
                    Surface(
                        shape = NeoShapes.FullPill,
                        color = NeoPalette.SurfaceSunken,
                        border = BorderStroke(1.dp, NeoPalette.BorderSubtle)
                    ) {
                        Text(
                            text = simulatedStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = NeoPalette.TextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Target Simulation Profiles & Real LockActivity Launch Button
            AnimatedVisibility(visible = showProfileSelector) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    testProfiles.forEach { profile ->
                        val isSelected = selectedProfile.id == profile.id
                        NeoCard(
                            onClick = { selectedProfile = profile },
                            shape = NeoShapes.MediumCard,
                            accentBorder = if (isSelected) NeoPalette.PrimaryGold else null,
                            elevation = if (isSelected) 6.dp else 3.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("test_profile_${profile.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NeoIconBadge(
                                    icon = profile.icon,
                                    size = 40.dp,
                                    iconSize = 20.dp,
                                    isGlowing = isSelected
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = NeoPalette.TextPrimary
                                    )
                                    Text(
                                        text = profile.category,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeoPalette.TextSecondary
                                    )
                                }

                                if (isSelected) {
                                    NeoStatusBadge(
                                        text = "Active Target",
                                        color = NeoPalette.PrimaryGold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            NeoButton(
                text = "Launch Lock Screen Simulation",
                icon = Icons.Default.PlayArrow,
                isPrimaryGlowing = true,
                onClick = {
                    val intent = Intent(context, LockActivity::class.java).apply {
                        putExtra(LockActivity.EXTRA_TARGET_PACKAGE, selectedProfile.packageName)
                        putExtra(LockActivity.EXTRA_IS_TEST_MODE, true)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                    }
                    context.startActivity(intent)
                },
                shape = NeoShapes.FullPill,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("launch_simulation_button")
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
