package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCard
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.NeoStatusBadge
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Interactive Lock Simulator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("test_lock_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(8.dp, CircleShape, spotColor = NeoPalette.PrimaryCyan)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(NeoPalette.SurfaceElevated, NeoPalette.SurfaceDark)
                        )
                    )
                    .border(1.5.dp, NeoPalette.PrimaryCyan.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = "Test Simulator",
                    tint = NeoPalette.PrimaryCyan,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Lock Verification Lab",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Simulate foreground app opening to test the actual LockActivity, incorrect PIN rate-limiting, and recovery flows without needing external third-party apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Select Simulation Profile:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Test profiles list
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                testProfiles.forEach { profile ->
                    val isSelected = selectedProfile.id == profile.id
                    NeoCard(
                        onClick = { selectedProfile = profile },
                        shape = NeoShapes.MediumCard,
                        accentBorder = if (isSelected) NeoPalette.PrimaryCyan else null,
                        elevation = if (isSelected) 6.dp else 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_profile_${profile.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) NeoPalette.PrimaryCyan else NeoPalette.SurfaceLight,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color.White.copy(alpha = 0.4f) else NeoPalette.SpecularBorder
                                ),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = profile.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else NeoPalette.PrimaryCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = profile.category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                NeoStatusBadge(
                                    text = "Active Target",
                                    color = NeoPalette.PrimaryCyan
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

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
                shape = NeoShapes.MediumCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("launch_simulation_button")
            )
        }
    }
}
