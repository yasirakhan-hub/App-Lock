package com.example.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.security.AppLockPreferences
import kotlin.math.hypot

/**
 * Android-style 3×3 Pattern Lock grid in Black & Gold Neomorphism style.
 *
 * Supports continuous touch/pointer drawing across the 3×3 grid with collinear
 * intermediate dot inclusion and always invokes the latest completion callbacks.
 */
@Composable
fun PatternLockGrid(
    onPatternComplete: (String) -> Unit,
    onPatternTooShort: () -> Unit = {},
    onPatternStart: () -> Unit = {},
    enabled: Boolean = true,
    isError: Boolean = false,
    gridSize: Dp = 240.dp,
    showActionButtonsWhenTapped: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var selectedNodes by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentDragOffset by remember { mutableStateOf<Offset?>(null) }

    val currentOnPatternComplete by rememberUpdatedState(onPatternComplete)
    val currentOnPatternTooShort by rememberUpdatedState(onPatternTooShort)
    val currentOnPatternStart by rememberUpdatedState(onPatternStart)

    fun dotCenterForIndex(index: Int, size: IntSize): Offset {
        val row = index / 3
        val col = index % 3
        val cellW = size.width / 3f
        val cellH = size.height / 3f
        return Offset(
            x = col * cellW + cellW / 2f,
            y = row * cellH + cellH / 2f
        )
    }

    fun intermediateNodeBetween(a: Int, b: Int): Int? {
        val r1 = a / 3
        val c1 = a % 3
        val r2 = b / 3
        val c2 = b % 3
        val dr = r2 - r1
        val dc = c2 - c1
        return if (dr % 2 == 0 && dc % 2 == 0) {
            val midR = r1 + dr / 2
            val midC = c1 + dc / 2
            midR * 3 + midC
        } else {
            null
        }
    }

    fun appendNodeWithIntermediates(current: List<Int>, nextNode: Int): List<Int> {
        if (nextNode !in 0..8 || current.contains(nextNode)) return current
        val updated = current.toMutableList()
        val last = updated.lastOrNull()
        if (last != null) {
            val mid = intermediateNodeBetween(last, nextNode)
            if (mid != null && !updated.contains(mid)) {
                updated.add(mid)
            }
        }
        updated.add(nextNode)
        return updated
    }

    fun hitTestNode(point: Offset, size: IntSize, radiusFactor: Float = 0.38f): Int? {
        if (size.width <= 0 || size.height <= 0) return null
        val cellExtent = minOf(size.width, size.height) / 3f
        val hitRadius = cellExtent * radiusFactor
        var closestIndex: Int? = null
        var closestDist = Float.MAX_VALUE
        for (i in 0..8) {
            val center = dotCenterForIndex(i, size)
            val dist = hypot(point.x - center.x, point.y - center.y)
            if (dist <= hitRadius && dist < closestDist) {
                closestDist = dist
                closestIndex = i
            }
        }
        return closestIndex
    }

    fun finalizePattern(nodes: List<Int>) {
        if (nodes.isEmpty()) return
        val distinctOrderedNodes = nodes.distinct().filter { it in 0..8 }
        selectedNodes = emptyList()
        currentDragOffset = null
        if (distinctOrderedNodes.size < AppLockPreferences.MIN_PATTERN_DOTS) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnPatternTooShort()
        } else {
            val canonical = AppLockPreferences.normalizePatternNodes(distinctOrderedNodes)
            currentOnPatternComplete(canonical)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(gridSize)
                .clip(NeoShapes.MediumCard)
                .background(NeoPalette.SurfaceSunken)
                .border(
                    width = 1.dp,
                    color = if (isError) NeoPalette.ErrorRed.copy(alpha = 0.6f)
                    else NeoPalette.PrimaryGold.copy(alpha = 0.22f),
                    shape = NeoShapes.MediumCard
                )
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        val gridIntSize = size
                        currentOnPatternStart()

                        var strokeNodes = emptyList<Int>()
                        val initialHit = hitTestNode(down.position, gridIntSize, radiusFactor = 0.46f)
                        if (initialHit != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            strokeNodes = listOf(initialHit)
                        }
                        selectedNodes = strokeNodes
                        currentDragOffset = down.position

                        var lastPos = down.position
                        var completed = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break

                            val pos = change.position
                            val distance = hypot(pos.x - lastPos.x, pos.y - lastPos.y)
                            val steps = maxOf(1, (distance / 6f).toInt())
                            for (stepIdx in 1..steps) {
                                val fraction = stepIdx.toFloat() / steps.toFloat()
                                val samplePoint = Offset(
                                    x = lastPos.x + (pos.x - lastPos.x) * fraction,
                                    y = lastPos.y + (pos.y - lastPos.y) * fraction
                                )
                                val hit = hitTestNode(samplePoint, gridIntSize, radiusFactor = 0.38f)
                                if (hit != null && !strokeNodes.contains(hit)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    strokeNodes = appendNodeWithIntermediates(strokeNodes, hit)
                                    selectedNodes = strokeNodes
                                }
                            }
                            lastPos = pos

                            if (!change.pressed) {
                                change.consume()
                                completed = true
                                break
                            }

                            currentDragOffset = pos
                            change.consume()
                        }

                        currentDragOffset = null
                        if (completed && strokeNodes.isNotEmpty()) {
                            finalizePattern(strokeNodes)
                        } else {
                            selectedNodes = emptyList()
                        }
                    }
                }
                .testTag("pattern_lock_grid")
        ) {
            val activeColor = if (isError) NeoPalette.ErrorRed else NeoPalette.PrimaryGold
            val lineColor = activeColor.copy(alpha = 0.72f)
            val inactiveDotColor = NeoPalette.TextSecondary.copy(alpha = 0.45f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val intSize = IntSize(size.width.toInt(), size.height.toInt())
                // Draw lines between connected nodes
                if (selectedNodes.size >= 2) {
                    for (idx in 0 until selectedNodes.size - 1) {
                        val startCenter = dotCenterForIndex(selectedNodes[idx], intSize)
                        val endCenter = dotCenterForIndex(selectedNodes[idx + 1], intSize)
                        drawLine(
                            color = lineColor,
                            start = startCenter,
                            end = endCenter,
                            strokeWidth = 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                // Draw trailing drag line
                val lastNode = selectedNodes.lastOrNull()
                val dragPos = currentDragOffset
                if (lastNode != null && dragPos != null) {
                    val lastCenter = dotCenterForIndex(lastNode, intSize)
                    drawLine(
                        color = lineColor.copy(alpha = 0.45f),
                        start = lastCenter,
                        end = dragPos,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Draw the 9 nodes
                for (nodeIndex in 0..8) {
                    val center = dotCenterForIndex(nodeIndex, intSize)
                    val isSelected = selectedNodes.contains(nodeIndex)
                    if (isSelected) {
                        drawCircle(
                            color = activeColor.copy(alpha = 0.22f),
                            radius = 22.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = activeColor,
                            radius = 9.dp.toPx(),
                            center = center
                        )
                    } else {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = 18.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = inactiveDotColor,
                            radius = 6.dp.toPx(),
                            center = center
                        )
                    }
                }
            }

            // Non-intercepting 3x3 overlay targets for testTag lookup
            Column(modifier = Modifier.fillMaxSize()) {
                for (row in 0..2) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        for (col in 0..2) {
                            val nodeIndex = row * 3 + col
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .testTag("pattern_dot_$nodeIndex")
                            ) {}
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (selectedNodes.isNotEmpty()) {
                "${selectedNodes.size} dots connected (min ${AppLockPreferences.MIN_PATTERN_DOTS})"
            } else {
                "Draw pattern across at least ${AppLockPreferences.MIN_PATTERN_DOTS} dots"
            },
            style = MaterialTheme.typography.labelSmall,
            color = NeoPalette.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Neomorphic Password authentication input with show/hide toggle and 1..32 character enforcement.
 */
@Composable
fun PasswordAuthInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    enabled: Boolean = true,
    isError: Boolean = false,
    label: String = "Password",
    placeholder: String = "Enter password (max 32 chars)",
    submitButtonText: String = "Unlock",
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                if (next.length <= AppLockPreferences.MAX_PASSWORD_LENGTH) {
                    onValueChange(next)
                }
            },
            enabled = enabled,
            singleLine = true,
            isError = isError,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = NeoPalette.PrimaryGold
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.testTag("password_visibility_toggle")
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = NeoPalette.PrimaryGold
                    )
                }
            },
            supportingText = {
                Text(
                    text = "${value.length}/${AppLockPreferences.MAX_PASSWORD_LENGTH} characters",
                    color = NeoPalette.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (enabled) onSubmit() }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeoPalette.PrimaryGold,
                unfocusedBorderColor = NeoPalette.PrimaryGold.copy(alpha = 0.28f),
                errorBorderColor = NeoPalette.ErrorRed,
                focusedLabelColor = NeoPalette.PrimaryGold,
                cursorColor = NeoPalette.PrimaryGold
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_input_field")
        )

        Spacer(modifier = Modifier.height(14.dp))

        NeoButton(
            text = submitButtonText,
            isPrimaryGlowing = true,
            enabled = enabled,
            shape = NeoShapes.FullPill,
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_submit_button")
        )
    }
}

/**
 * Reusable Biometric button row for Pattern and Password authentication screens.
 */
@Composable
fun BiometricActionRow(
    onBiometricClick: () -> Unit,
    enabled: Boolean = true,
    testTag: String = "biometric_auth_button",
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onBiometricClick,
        enabled = enabled,
        modifier = modifier.testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.Default.Fingerprint,
            contentDescription = "Unlock with Biometrics",
            tint = NeoPalette.PrimaryGold,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Use Fingerprint / Biometrics",
            color = NeoPalette.PrimaryGold,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
