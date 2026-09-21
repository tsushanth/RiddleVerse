package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.LocalContext

@Composable
fun MatchScreen(
    puzzleId: String,
    question: String,
    difficulty: String = "Easy",
    options: List<String>,
    correctAnswer: String,
    selectedOption: String?,
    timerSeconds: Int = 90,
    questionNumber: Int = 1,
    totalQuestions: Int = 1,
    onOptionSelected: (String) -> Unit,
    onContinue: (String?) -> Unit, // Pass selected answer
    onCorrectAnswer: (Boolean) -> Unit,
    onHint: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var remainingTime by remember { mutableIntStateOf(timerSeconds) }
    var currentSelectedOption by remember { mutableStateOf(selectedOption) }
    var isAnswered by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }

    // Countdown timer
    LaunchedEffect(Unit) {
        while (remainingTime > 0 && !isAnswered) {
            delay(1000L)
            remainingTime--
        }
    }

    // Selecting an option submits it straight away (same behaviour as before, shared by every layout).
    val selectOption: (String) -> Unit = { option ->
        if (!isAnswered) {
            currentSelectedOption = option
            onOptionSelected(option)

            // Auto-submit after selection
            isAnswered = true
            isCorrect = option == correctAnswer
            showResult = true

            if (isCorrect) {
                onCorrectAnswer(remainingTime > 0)
            }

            // Auto-continue after 2 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                onContinue(option)
            }, 2000)
        }
    }

    val submitSelection: () -> Unit = {
        if (currentSelectedOption != null) {
            isAnswered = true
            isCorrect = currentSelectedOption == correctAnswer
            showResult = true

            if (isCorrect) {
                onCorrectAnswer(remainingTime > 0)
            }

            Handler(Looper.getMainLooper()).postDelayed({
                onContinue(currentSelectedOption)
            }, 2000)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
        val wide = maxWidth > maxHeight || maxWidth >= 600.dp

        // Question card: the dominant element. Its header line doubles as the result banner.
        val questionCard: @Composable (Modifier) -> Unit = { m ->
            GroupEFontCap {
                Box(
                    modifier = m
                        .testTag("match_question")
                        .clip(RoundedCornerShape(16.dp))
                        .background(RvSurface)
                        .border(2.dp, RvOutline, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when {
                                showResult && isCorrect -> "\uD83C\uDF89 Correct!"
                                showResult -> "\u274C Wrong! Answer: $correctAnswer"
                                else -> "Match the Answer"
                            },
                            color = when {
                                showResult && isCorrect -> RvInk
                                showResult -> RvErrorEdge
                                else -> RvInkSoft
                            },
                            fontSize = 16.sp,
                            fontWeight = if (showResult) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = question,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Options: one column for <= 3, two columns otherwise. Rows share the height, each >= 56dp.
        val optionsArea: @Composable (Modifier) -> Unit = { m ->
            val columns = if (options.size <= 3) 1 else 2
            val rows = options.chunked(columns)
            Column(modifier = m, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { rowOptions ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .heightIn(min = 56.dp, max = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowOptions.forEach { option ->
                            GroupEVerbOption(
                                option = option,
                                isSelected = currentSelectedOption == option,
                                isCorrect = option == correctAnswer,
                                showResult = showResult,
                                onSelect = { selectOption(option) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        if (rowOptions.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Action bar: one primary action (Submit) + secondary Hint, pinned at the bottom.
        val actionBar: @Composable (Modifier) -> Unit = { m ->
            Row(
                modifier = m,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onHint,
                    enabled = !isAnswered,
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .weight(if (showResult) 1f else 0.6f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(2.dp, if (isAnswered) RvDisabled else RvViolet),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RvVioletEdge,
                        disabledContentColor = RvInkSoft
                    )
                ) {
                    Text("\uD83D\uDCA1 Hint", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                if (!showResult) {
                    GroupEPrimaryButton(
                        text = stringResource(R.string.submit),
                        onClick = submitSelection,
                        enabled = currentSelectedOption != null && !isAnswered,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        ) {
            GroupECompactHud(
                timer = String.format("%d:%02d", remainingTime / 60, remainingTime % 60),
                onBack = onBack,
                subtitle = "Question $questionNumber of $totalQuestions \u2022 $difficulty",
                urgent = remainingTime <= 10
            )

            Spacer(Modifier.height(8.dp))

            if (wide) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    questionCard(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (options.isNotEmpty()) optionsArea(Modifier.weight(1f).fillMaxWidth())
                        actionBar(Modifier.fillMaxWidth())
                    }
                }
            } else {
                questionCard(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                if (options.isNotEmpty()) optionsArea(Modifier.weight(1f).fillMaxWidth()) else Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(8.dp))
                actionBar(Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Answer option: rounded button (>= 56dp), text wraps, selected / correct / wrong each get their own
 * outline weight and a mark (dot, check, cross), so state is never colour-only.
 */
@Composable
private fun GroupEVerbOption(
    option: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    showResult: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    val correctShown = showResult && isCorrect
    val wrongShown = showResult && isSelected && !isCorrect
    val border = when {
        correctShown -> RvSuccessEdge
        wrongShown -> RvErrorEdge
        isSelected -> RvViolet
        else -> RvOutline
    }
    val fill = when {
        correctShown -> RvSuccess.copy(alpha = 0.25f)
        wrongShown -> RvError.copy(alpha = 0.2f)
        isSelected -> RvViolet.copy(alpha = 0.12f)
        else -> RvSurfaceRaised
    }
    val mark = when {
        correctShown -> "\u2713"
        wrongShown -> "\u2717"
        isSelected -> "\u25CF"
        else -> ""
    }
    GroupEFontCap(max = 1.5f) {
        Row(
            modifier = modifier
                .testTag("match_option")
                .clip(shape)
                .background(fill, shape)
                .border(if (isSelected || correctShown) 3.dp else 2.dp, border, shape)
                .clickable { onSelect() }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (mark.isNotEmpty()) {
                Text(text = mark, color = RvInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = option,
                color = RvInk,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OptionCircle(
    option: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    showResult: Boolean,
    onSelect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    when {
                        showResult && isCorrect -> RvSuccess
                        showResult && isSelected && !isCorrect -> RvError
                        isSelected -> RvSuccess
                        else -> RvError
                    }
                )
                .clickable { onSelect() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = option,
                color = RvInk,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .background(
                    when {
                        showResult && isCorrect -> RvSuccess
                        showResult && isSelected && !isCorrect -> RvError
                        isSelected -> RvSuccess
                        else -> RvError
                    }
                )
        )
    }
}