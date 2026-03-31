package com.kreativekoala.riddleverse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp

/**
 * Tutorial screen for Math Estimation puzzles
 */
@Composable
fun MathEstimationTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val tutorialManager = remember { MathEstimationTutorialManager() }
    val steps = tutorialManager.getTutorialSteps()
    var currentStepIndex by remember { mutableStateOf(0) }
    var tutorialState by remember { mutableStateOf(TutorialState.ACTIVE) }

    // Tutorial data - simple estimation problem
    val tutorialDataPoints = remember {
        listOf(
            ChartDataPoint(value = 12.5, yPosition = 0.25f, index = 0),
            ChartDataPoint(value = 8.7, yPosition = 0.17f, index = 1),
            ChartDataPoint(value = 15.2, yPosition = 0.30f, index = 2),
            ChartDataPoint(value = 6.1, yPosition = 0.12f, index = 3)
        )
    }
    val tutorialCorrectSum = 42.5
    val tutorialMinValue = 0.0
    val tutorialMaxValue = 50.0

    // Tutorial interaction state
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var currentEstimate by remember { mutableStateOf<Double?>(null) }
    var userHasDragged by remember { mutableStateOf(false) }
    var userHasSubmitted by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val chartHeight = 350.dp
    val chartWidth = 300.dp

    // Handle tutorial completion
    LaunchedEffect(tutorialState) {
        when (tutorialState) {
            TutorialState.COMPLETED -> onTutorialComplete()
            TutorialState.SKIPPED -> onTutorialSkipped()
            else -> {}
        }
    }

    fun advanceStep() {
        if (currentStepIndex < steps.size - 1) {
            currentStepIndex++
        } else {
            tutorialState = TutorialState.COMPLETED
        }
    }

    fun skipTutorial() {
        tutorialState = TutorialState.SKIPPED
    }

    fun handleTutorialAction(action: String) {
        val step = steps.getOrNull(currentStepIndex)
        if (step?.interactionRequired == true && step.expectedAction == action) {
            when (action) {
                "drag_estimate" -> {
                    if (userHasDragged) {
                        // Don't auto-advance, let user manually continue
                    }
                }
                "submit_estimate" -> {
                    userHasSubmitted = true
                    // Don't auto-advance, let user manually continue
                }
            }
        }
    }

    fun handleDrag(offset: Offset) {
        dragPosition = offset
        userHasDragged = true

        // Calculate estimate (simplified version of main screen logic)
        val padding = with(density) { 50.dp.toPx() }
        val chartRect = androidx.compose.ui.geometry.Rect(
            offset = Offset(padding, padding),
            size = androidx.compose.ui.geometry.Size(
                with(density) { (chartWidth + 80.dp).toPx() } - padding * 2,
                with(density) { (chartHeight + 60.dp).toPx() } - padding * 2
            )
        )

        if (offset.x >= chartRect.left && offset.x <= chartRect.right &&
            offset.y >= chartRect.top && offset.y <= chartRect.bottom) {

            val relativeY = (chartRect.bottom - offset.y) / chartRect.height
            val estimated = tutorialMinValue + (tutorialMaxValue - tutorialMinValue) * relativeY
            currentEstimate = estimated
        }

        handleTutorialAction("drag_estimate")
    }

    fun handleSubmit() {
        handleTutorialAction("submit_estimate")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tutorial Math Estimation content
        TutorialMathEstimationContent(
            currentStep = steps.getOrNull(currentStepIndex),
            dataPoints = tutorialDataPoints,
            dragPosition = dragPosition,
            currentEstimate = currentEstimate,
            minValue = tutorialMinValue,
            maxValue = tutorialMaxValue,
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            onDrag = ::handleDrag,
            onSubmit = ::handleSubmit,
            onBack = onBack
        )

        // Tutorial overlay
        if (tutorialState == TutorialState.ACTIVE && currentStepIndex < steps.size) {
            val currentStep = steps[currentStepIndex]

            if (currentStep.interactionRequired) {
                // Custom overlay for interaction steps
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Light background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f))
                    )

                    // Tutorial card at bottom
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentStep.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF333333)
                                    )
                                    Text(
                                        text = "Step ${currentStepIndex + 1} of ${steps.size}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }

                                IconButton(onClick = { skipTutorial() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.skip_tutorial),
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Progress bar
                            LinearProgressIndicator(
                                progress = { (currentStepIndex + 1).toFloat() / steps.size.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = Color(0xFF7B1FA2),
                                trackColor = Color(0xFFE1BEE7)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = currentStep.description,
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { skipTutorial() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                                ) {
                                    Text(stringResource(R.string.skip), fontSize = 12.sp)
                                }

                                when (currentStep.expectedAction) {
                                    "drag_estimate" -> {
                                        if (userHasDragged) {
                                            Button(
                                                onClick = { advanceStep() },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Text(stringResource(R.string.next), color = Color.White, fontSize = 12.sp)
                                            }
                                        } else {
                                            Text(
                                                text = "👆 Drag on the chart above",
                                                fontSize = 12.sp,
                                                color = Color(0xFF7B1FA2),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    "submit_estimate" -> {
                                        if (userHasSubmitted) {
                                            Button(
                                                onClick = { advanceStep() },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Text(stringResource(R.string.next), color = Color.White, fontSize = 12.sp)
                                            }
                                        } else {
                                            Text(
                                                text = "👆 Tap the submit button above",
                                                fontSize = 12.sp,
                                                color = Color(0xFF7B1FA2),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Use standard overlay for non-interactive steps
                SmartTutorialOverlay(
                    currentStep = currentStep,
                    totalSteps = steps.size,
                    currentStepNumber = currentStepIndex + 1,
                    onNext = { advanceStep() },
                    onSkip = { skipTutorial() }
                )
            }
        }
    }
}

/**
 * Tutorial-specific math estimation content
 */
@Composable
fun TutorialMathEstimationContent(
    currentStep: TutorialStep?,
    dataPoints: List<ChartDataPoint>,
    dragPosition: Offset?,
    currentEstimate: Double?,
    minValue: Double,
    maxValue: Double,
    chartWidth: androidx.compose.ui.unit.Dp,
    chartHeight: androidx.compose.ui.unit.Dp,
    onDrag: (Offset) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            // Top Bar with highlighting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color(0xFF333333),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "TUTORIAL",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )

                Text(
                    text = "2:00",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Chart area with highlighting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight + 60.dp)
                    .then(
                        if (currentStep?.targetComponent == "chart") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                TutorialInteractiveChart(
                    dataPoints = dataPoints,
                    dragPosition = dragPosition,
                    minValue = minValue,
                    maxValue = maxValue,
                    chartWidth = chartWidth,
                    chartHeight = chartHeight,
                    onDrag = onDrag,
                    modifier = Modifier.size(chartWidth + 80.dp, chartHeight + 60.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Instructions with highlighting
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .then(
                        if (currentStep?.targetComponent == "instructions") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8E4F3)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Up Arrow",
                            tint = Color(0xFF9C27B0),
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "Drag finger to estimate the sum",
                            fontSize = 16.sp,
                            color = Color(0xFF9C27B0),
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Down Arrow",
                            tint = Color(0xFF9C27B0),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Submit section with highlighting
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Current estimate display with highlighting
                Text(
                    text = if (currentEstimate != null) {
                        "Current Estimate: ${String.format("%.1f", currentEstimate)}"
                    } else {
                        "Drag on chart to set estimate"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentEstimate != null) Color(0xFF1976D2) else Color(0xFF666666),
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .then(
                            if (currentStep?.targetComponent == "estimate_display") {
                                Modifier.tutorialHighlight()
                            } else Modifier
                        )
                )

                // Submit button with highlighting
                Button(
                    onClick = onSubmit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .then(
                            if (currentStep?.targetComponent == "submit") {
                                Modifier.tutorialHighlight()
                            } else Modifier
                        )
                ) {
                    Text(
                        text = if (currentEstimate != null) {
                            "SUBMIT ESTIMATE"
                        } else {
                            "SUBMIT (will use middle value)"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Tutorial version of interactive chart
 */
@Composable
fun TutorialInteractiveChart(
    dataPoints: List<ChartDataPoint>,
    dragPosition: Offset?,
    minValue: Double,
    maxValue: Double,
    chartWidth: androidx.compose.ui.unit.Dp,
    chartHeight: androidx.compose.ui.unit.Dp,
    onDrag: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    var chartRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val newPosition = Offset(change.position.x, change.position.y)
                    onDrag(newPosition)
                }
            }
    ) {
        val padding = 50.dp.toPx()
        chartRect = androidx.compose.ui.geometry.Rect(
            offset = Offset(padding, padding),
            size = androidx.compose.ui.geometry.Size(
                size.width - padding * 2,
                size.height - padding * 2
            )
        )

        drawTutorialChart(
            dataPoints = dataPoints,
            dragPosition = dragPosition,
            chartRect = chartRect,
            minValue = minValue,
            maxValue = maxValue
        )
    }
}

/**
 * Simplified chart drawing for tutorial
 */
fun DrawScope.drawTutorialChart(
    dataPoints: List<ChartDataPoint>,
    dragPosition: Offset?,
    chartRect: androidx.compose.ui.geometry.Rect,
    minValue: Double,
    maxValue: Double
) {
    // Draw Y-axis
    drawLine(
        color = Color(0xFF9C27B0),
        start = Offset(chartRect.left, chartRect.top),
        end = Offset(chartRect.left, chartRect.bottom),
        strokeWidth = 3.dp.toPx()
    )

    // Draw simplified tick marks and labels
    val step = 10.0
    var currentValue = step
    while (currentValue <= maxValue) {
        val yPos = chartRect.bottom - (currentValue / maxValue).toFloat() * chartRect.height

        // Draw tick mark
        drawRect(
            color = Color(0xFF9C27B0),
            topLeft = Offset(chartRect.left - 12.dp.toPx(), yPos - 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(18.dp.toPx(), 4.dp.toPx())
        )

        // Draw label
        drawContext.canvas.nativeCanvas.drawText(
            "${currentValue.toInt()}",
            chartRect.left - 35.dp.toPx(),
            yPos + 6.dp.toPx(),
            android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#9C27B0")
                textSize = 16.sp.toPx()
                isAntiAlias = true
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.RIGHT
            }
        )

        currentValue += step
    }

    // Draw data points
    dataPoints.forEachIndexed { index, point ->
        val xPos = chartRect.left + chartRect.width * 0.6f
        val yPos = chartRect.bottom - point.yPosition * chartRect.height

        // Draw data point square
        drawRect(
            color = Color.White,
            topLeft = Offset(xPos - 7.dp.toPx(), yPos - 7.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(14.dp.toPx(), 14.dp.toPx())
        )
        drawRect(
            color = Color(0xFF9C27B0),
            topLeft = Offset(xPos - 5.dp.toPx(), yPos - 5.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(10.dp.toPx(), 10.dp.toPx())
        )

        // Draw value text
        drawContext.canvas.nativeCanvas.drawText(
            String.format("%.1f", point.value),
            xPos + 25.dp.toPx(),
            yPos + 6.dp.toPx(),
            android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#333333")
                textSize = 18.sp.toPx()
                isAntiAlias = true
                isFakeBoldText = true
            }
        )
    }

    // Draw drag indicator
    dragPosition?.let { position ->
        if (position.x >= chartRect.left && position.x <= chartRect.right &&
            position.y >= chartRect.top && position.y <= chartRect.bottom) {

            // Draw horizontal line
            drawLine(
                color = Color(0xFF4CAF50),
                start = Offset(chartRect.left, position.y),
                end = Offset(chartRect.right, position.y),
                strokeWidth = 4.dp.toPx()
            )

            // Draw drag handle
            drawCircle(
                color = Color(0xFF4CAF50),
                radius = 10.dp.toPx(),
                center = Offset(chartRect.left, position.y)
            )
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(chartRect.left, position.y)
            )

            // Calculate and show estimate
            val relativeY = (chartRect.bottom - position.y) / chartRect.height
            val estimated = minValue + (maxValue - minValue) * relativeY

            drawContext.canvas.nativeCanvas.drawText(
                String.format("%.1f", estimated),
                chartRect.right + 15.dp.toPx(),
                position.y + 6.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#4CAF50")
                    textSize = 22.sp.toPx()
                    isFakeBoldText = true
                    isAntiAlias = true
                }
            )
        }
    }
}
