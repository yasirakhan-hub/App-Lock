package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.security.AppLockPreferences
import com.example.ui.common.PinDots
import com.example.ui.common.PinKeypad

private enum class SetupStep {
    WELCOME,
    CREATE_PIN,
    CONFIRM_PIN,
    SECURITY_QUESTION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSetupScreen(
    preferences: AppLockPreferences,
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableStateOf(SetupStep.WELCOME) }
    var selectedPinLength by remember { mutableIntStateOf(4) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinMismatchError by remember { mutableStateOf(false) }

    // Security question state
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

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Create Master PIN",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Choose a $selectedPinLength-digit PIN to lock and unlock your applications.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // PIN Length Selector
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedPinLength = 4
                                            firstPin = ""
                                        },
                                        shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
                                        colors = if (selectedPinLength == 4) {
                                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                                    ) {
                                        Text("4 Digits")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            selectedPinLength = 6
                                            firstPin = ""
                                        },
                                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                                        colors = if (selectedPinLength == 6) {
                                            androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                                    ) {
                                        Text("6 Digits")
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
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Confirm Your PIN",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Re-enter your $selectedPinLength-digit PIN to ensure it was typed correctly.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        color = MaterialTheme.colorScheme.error,
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
                                                // Advance to recovery question step
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
                                    TextButton(onClick = {
                                        firstPin = ""
                                        confirmPin = ""
                                        currentStep = SetupStep.CREATE_PIN
                                    }) {
                                        Text("Back")
                                    }
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
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "PIN Recovery Setup",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Set a security question so you can securely unlock or reset your PIN if forgotten, without storing plain text passwords.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Dropdown for question
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
                                        Text(answerError ?: "", color = MaterialTheme.colorScheme.error)
                                    } else {
                                        Text("Answers are case-insensitive and stored as a cryptographic hash.")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("security_answer_input")
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = {
                                    if (customAnswer.trim().length < 2) {
                                        answerError = "Please enter an answer with at least 2 characters."
                                        return@Button
                                    }

                                    // Save PIN and recovery question
                                    preferences.savePin(firstPin)
                                    preferences.saveRecoveryQuestion(selectedQuestion, customAnswer)
                                    preferences.setProtectionEnabled(true)
                                    onSetupComplete()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("complete_setup_button")
                            ) {
                                Text("Complete Setup & Start Locking")
                            }
                        }
                    }
                }
            }
        }
    }
}

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
            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_applock_shield_logo),
                        contentDescription = "Shield",
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome to Secure AppLock",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Private, native protection for your sensitive apps and personal data on Android.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Feature Highlights
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    FeatureRow(
                        title = "Real App Protection",
                        description = "Locks individual apps (banking, gallery, messaging) with a master PIN."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FeatureRow(
                        title = "100% Offline & Private",
                        description = "No internet required. No tracking, ads, or plain text password storage."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FeatureRow(
                        title = "Salted Cryptographic Hashing",
                        description = "PINs are protected using PBKDF2-HMAC-SHA256 with rate-limited brute-force defense."
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("get_started_button")
        ) {
            Text("Set Up AppLock Now", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
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
                .padding(top = 2.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
