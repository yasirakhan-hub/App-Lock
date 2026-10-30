package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.AppLockPreferences
import com.example.ui.common.NeoBrandTitle
import com.example.ui.common.NeoButton
import com.example.ui.common.NeoCard
import com.example.ui.common.NeoGoldPadlockEmblem
import com.example.ui.common.NeoIconBadge
import com.example.ui.common.NeoInsetBox
import com.example.ui.common.NeoPalette
import com.example.ui.common.NeoShapes
import com.example.ui.common.PinDots
import com.example.ui.common.PinKeypad
import com.example.util.BiometricHelper

private enum class SetupStep {
    WELCOME,
    CREATE_PIN,
    CONFIRM_PIN,
    SECURITY_QUESTION,
    BIOMETRIC_SETUP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSetupScreen(
    preferences: AppLockPreferences,
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(SetupStep.WELCOME) }
    var selectedPinLength by remember { mutableIntStateOf(4) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinMismatchError by remember { mutableStateOf(false) }

    val defaultQuestions = listOf(
        "What was the name of your first pet?",
        "What city were you born in?",
        "What was the model of your first car?",
        "What is your mother's maiden name?",
        "What was the name of your elementary school?"
    )
    var selectedQuestion by remember { mutableStateOf(defaultQuestions[0]) }
    var isQuestionDropdownExpanded by remember { mutableStateOf(false) }
    var customAnswer by remember { mutableStateOf("") }
    var answerError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = NeoPalette.BackgroundBase,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1A1405),
                            NeoPalette.BackgroundBase,
                            Color(0xFF120F06)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "SetupStepTransition"
            ) { step ->
                when (step) {
                    SetupStep.WELCOME -> {
                        WelcomeStep(
                            onGetStarted = { currentStep = SetupStep.CREATE_PIN }
                        )
                    }

                    SetupStep.CREATE_PIN -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                NeoGoldPadlockEmblem(
                                    badgeSize = 76.dp,
                                    isOpenShackle = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Create Master PIN",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoPalette.TextPrimary
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Choose a $selectedPinLength-digit PIN to unlock your secure space",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NeoPalette.TextSecondary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Neomorphic PIN Length Selector
                                NeoInsetBox(
                                    shape = NeoShapes.FullPill,
                                    contentPadding = PaddingValues(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .padding(bottom = 16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(4 to "4 Digits", 6 to "6 Digits").forEach { (len, label) ->
                                            val isSelected = selectedPinLength == len
                                            NeoButton(
                                                onClick = {
                                                    selectedPinLength = len
                                                    firstPin = ""
                                                },
                                                isPrimaryGlowing = isSelected,
                                                shape = NeoShapes.FullPill,
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) NeoPalette.TextInverse else NeoPalette.TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }

                                PinDots(
                                    pinLength = selectedPinLength,
                                    enteredLength = firstPin.length,
                                    isError = false
                                )
                            }

                            PinKeypad(
                                onDigitClick = { digit ->
                                    if (firstPin.length < selectedPinLength) {
                                        val newPin = firstPin + digit
                                        firstPin = newPin
                                        if (newPin.length == selectedPinLength) {
                                            currentStep = SetupStep.CONFIRM_PIN
                                        }
                                    }
                                },
                                onDeleteClick = {
                                    if (firstPin.isNotEmpty()) {
                                        firstPin = firstPin.dropLast(1)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    SetupStep.CONFIRM_PIN -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                NeoIconBadge(
                                    icon = Icons.Default.Check,
                                    size = 72.dp,
                                    iconSize = 34.dp,
                                    isGlowing = true
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Confirm Your PIN",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NeoPalette.TextPrimary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Re-enter your $selectedPinLength-digit PIN to ensure it was typed correctly.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NeoPalette.TextSecondary,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                PinDots(
                                    pinLength = selectedPinLength,
                                    enteredLength = confirmPin.length,
                                    isError = pinMismatchError
                                )

                                if (pinMismatchError) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "PINs do not match. Please try again.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = NeoPalette.ErrorRed,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            PinKeypad(
                                onDigitClick = { digit ->
                                    if (confirmPin.length < selectedPinLength) {
                                        val newPin = confirmPin + digit
                                        confirmPin = newPin
                                        pinMismatchError = false

                                        if (newPin.length == selectedPinLength) {
                                            if (newPin == firstPin) {
                                                currentStep = SetupStep.SECURITY_QUESTION
                                            } else {
                                                pinMismatchError = true
                                                confirmPin = ""
                                            }
                                        }
                                    }
                                },
                                onDeleteClick = {
                                    if (confirmPin.isNotEmpty()) {
                                        confirmPin = confirmPin.dropLast(1)
                                        pinMismatchError = false
                                    }
                                },
                                extraLeftAction = {
                                    NeoButton(
                                        text = "Back",
                                        shape = NeoShapes.FullPill,
                                        onClick = {
                                            firstPin = ""
                                            confirmPin = ""
                                            currentStep = SetupStep.CREATE_PIN
                                        }
                                    )
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    SetupStep.SECURITY_QUESTION -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            NeoIconBadge(
                                icon = Icons.Default.Security,
                                size = 72.dp,
                                iconSize = 34.dp,
                                isGlowing = true
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "PIN Recovery Setup",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = NeoPalette.TextPrimary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Set a security question so you can securely unlock or reset your PIN if forgotten, without storing plain text passwords.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeoPalette.TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            ExposedDropdownMenuBox(
                                expanded = isQuestionDropdownExpanded,
                                onExpandedChange = { isQuestionDropdownExpanded = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedQuestion,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Security Question") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isQuestionDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                )
                                ExposedDropdownMenu(
                                    expanded = isQuestionDropdownExpanded,
                                    onDismissRequest = { isQuestionDropdownExpanded = false }
                                ) {
                                    defaultQuestions.forEach { question ->
                                        DropdownMenuItem(
                                            text = { Text(question) },
                                            onClick = {
                                                selectedQuestion = question
                                                isQuestionDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = customAnswer,
                                onValueChange = {
                                    customAnswer = it
                                    answerError = null
                                },
                                label = { Text("Your Secret Answer") },
                                placeholder = { Text("e.g. Charlie") },
                                singleLine = true,
                                isError = answerError != null,
                                supportingText = {
                                    if (answerError != null) {
                                        Text(answerError ?: "", color = NeoPalette.ErrorRed)
                                    } else {
                                        Text("Answers are case-insensitive and stored as a cryptographic hash.")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("security_answer_input")
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            NeoButton(
                                text = "Continue",
                                isPrimaryGlowing = true,
                                shape = NeoShapes.FullPill,
                                onClick = {
                                    if (customAnswer.trim().length < 2) {
                                        answerError = "Please enter an answer with at least 2 characters."
                                        return@NeoButton
                                    }

                                    preferences.savePin(firstPin)
                                    preferences.saveRecoveryQuestion(selectedQuestion, customAnswer)
                                    preferences.setProtectionEnabled(true)
                                    if (BiometricHelper.isBiometricAvailable(context)) {
                                        currentStep = SetupStep.BIOMETRIC_SETUP
                                    } else {
                                        onSetupComplete()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("complete_setup_button")
                            )
                        }
                    }

                    SetupStep.BIOMETRIC_SETUP -> {
                        BiometricSetupStep(
                            onEnable = {
                                preferences.setBiometricEnabled(true)
                                onSetupComplete()
                            },
                            onSkip = {
                                preferences.setBiometricEnabled(false)
                                onSetupComplete()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Panel 1: Splash / Welcome screen with large golden open-shackle padlock badge,
 * "Smart AppLock" two-tone title, and "Your Privacy / Our Priority" subtitle.
 */
@Composable
private fun WelcomeStep(
    onGetStarted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(28.dp))

            NeoGoldPadlockEmblem(
                badgeSize = 148.dp,
                isOpenShackle = true
            )

            Spacer(modifier = Modifier.height(28.dp))

            NeoBrandTitle(fontSize = 30.sp)

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Your Privacy\nOur Priority",
                style = MaterialTheme.typography.titleMedium,
                color = NeoPalette.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            NeoCard(
                shape = NeoShapes.LargeCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FeatureRow(
                        title = "Real App Protection",
                        description = "Locks individual apps (banking, gallery, messaging) with a master PIN."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FeatureRow(
                        title = "AES-256-GCM File Vault",
                        description = "Encrypts private photos, videos, and documents inside hardware-backed storage."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FeatureRow(
                        title = "Salted Cryptographic Hashing",
                        description = "PINs are protected using salted SHA-256 with rate-limited brute-force defense."
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        NeoButton(
            text = "Set Up AppLock Now",
            onClick = onGetStarted,
            isPrimaryGlowing = true,
            shape = NeoShapes.FullPill,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("get_started_button")
        )
    }
}

@Composable
private fun FeatureRow(
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(NeoPalette.PrimaryGold)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NeoPalette.TextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = NeoPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun BiometricSetupStep(
    onEnable: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(36.dp))

            NeoIconBadge(
                icon = Icons.Default.Fingerprint,
                size = 88.dp,
                iconSize = 46.dp,
                isGlowing = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Enable Fingerprint Unlock?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NeoPalette.TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your device supports biometric authentication. Enable fingerprint unlock to open protected apps and your encrypted file vault instantly.",
                style = MaterialTheme.typography.bodyMedium,
                color = NeoPalette.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            NeoCard(
                shape = NeoShapes.LargeCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FeatureRow(
                        title = "Fast & Frictionless",
                        description = "Touch your sensor to unlock protected apps in milliseconds."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FeatureRow(
                        title = "PIN Remains Your Secure Fallback",
                        description = "If biometric fails or sensor is dirty, your master PIN is always ready."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FeatureRow(
                        title = "Private & Local",
                        description = "Biometrics are processed strictly by Android OS hardware."
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NeoButton(
                text = "Enable Fingerprint Unlock",
                onClick = onEnable,
                isPrimaryGlowing = true,
                shape = NeoShapes.FullPill,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("enable_biometrics_button")
            )

            Spacer(modifier = Modifier.height(12.dp))

            NeoButton(
                text = "Skip & Use PIN Only",
                onClick = onSkip,
                shape = NeoShapes.FullPill,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("skip_biometrics_button")
            )
        }
    }
}
