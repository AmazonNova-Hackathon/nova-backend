package com.mediagent.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)