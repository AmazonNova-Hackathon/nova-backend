package com.mediagent.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediagent.app.data.model.FollowUp
import com.mediagent.app.ui.theme.*
import com.mediagent.app.util.FhirDateFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun FollowUpCardComponent(
    followUp: FollowUp,
    onAccept: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDismissed = followUp.status == "dismissed"
    val isAccepted = followUp.status == "accepted"
    val isPending = followUp.status == "pending"

    val isThisWeek = remember(followUp.suggestedDate) {
        runCatching {
            val date = LocalDate.parse(followUp.suggestedDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val today = LocalDate.now()
            val daysUntil = ChronoUnit.DAYS.between(today, date)
            daysUntil in 0..7
        }.getOrDefault(false)
    }

    val dateColor = if (isThisWeek) AttentionText else TextTertiary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isDismissed) 0.5f else 1f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfacePrimary),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPending) 2.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            // --- Top row: test name + LOINC badge ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = followUp.testName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W500,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = followUp.loincCode,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextTertiary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceTertiary)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }

            Spacer(Modifier.height(4.dp))

            // --- Reason ---
            Text(
                text = followUp.reason,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = TextTertiary,
            )

            Spacer(Modifier.height(6.dp))

            // --- Member name ---
            Text(
                text = followUp.memberName,
                fontSize = 12.sp,
                color = TextSecondary,
            )

            Spacer(Modifier.height(6.dp))

            // --- Suggested date ---
            Text(
                text = "Suggested: ${FhirDateFormatter.toDisplay(followUp.suggestedDate)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = dateColor,
            )

            Spacer(Modifier.height(10.dp))

            // --- Action row ---
            when {
                isPending -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Accept button (teal fill)
                        Button(
                            onClick = { onAccept(followUp.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandForest,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Accept", fontSize = 13.sp)
                        }

                        // Dismiss button (grey outline)
                        OutlinedButton(
                            onClick = { onDismiss(followUp.id) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextSecondary,
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Dismiss", fontSize = 13.sp)
                        }
                    }
                }

                isAccepted -> {
                    Text(
                        text = "\u2713 Accepted",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = BrandForest,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BrandForestLight)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                isDismissed -> {
                    Text(
                        text = "Dismissed",
                        fontSize = 12.sp,
                        color = TextTertiary,
                    )
                }
            }
        }
    }
}
