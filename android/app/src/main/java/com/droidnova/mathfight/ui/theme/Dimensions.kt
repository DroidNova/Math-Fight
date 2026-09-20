package com.droidnova.mathfight.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object GameDimensions {
    val screenPadding = 16.dp
    val sectionSpacing = 22.dp
    val itemSpacing = 12.dp
    val compactSpacing = 8.dp
    val touchTarget = 48.dp
    val buttonHeight = 54.dp
    val iconSize = 28.dp
    val cardRadius = 20.dp
    const val quickMotionMillis = 160
    const val standardMotionMillis = 240
}

val MathFightShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)
