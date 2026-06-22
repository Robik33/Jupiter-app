package com.marketia.jupiter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marketia.jupiter.core.oracle.OracleState
import com.marketia.jupiter.ui.theme.*

@Composable
fun OracleStateWidget(
    state: OracleState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = state.connected || state.error.isNotEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(JupiterSurface)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            if (!state.connected && state.error.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(Color(0xFF555555))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "HERMES: ${state.error}",
                        color = JupiterGray,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }
                return@Box
            }

            Column {
                // Header row: BTC + HERMES cycle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (state.tradeAllowed) JupiterGreen else Color(0xFFFF6B35))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "ORACLE",
                            color = JupiterCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                    Text(
                        text = "BTC ${"$%,.0f".format(state.btcPrice)}",
                        color = JupiterWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "E:${state.entropy.toInt()}",
                        color = if (state.entropy > 70) Color(0xFFFF6B35) else JupiterGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Blocker row
                if (state.blocker != "NONE" && state.blocker.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = ">> ${state.blocker}",
                        color = Color(0xFFFF6B35),
                        fontSize = 9.sp,
                        letterSpacing = 1.sp
                    )
                }

                // Wisdom row
                if (state.wisdomSummary.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.wisdomSummary,
                        color = JupiterGray,
                        fontSize = 9.sp
                    )
                }

                // Autopsy row
                if (state.autopsySummary.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = state.autopsySummary,
                        color = JupiterPurple,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color)
    )
}
