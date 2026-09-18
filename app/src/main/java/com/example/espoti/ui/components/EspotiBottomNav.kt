package com.example.espoti.ui.components

import androidx.annotation.DrawableRes
import com.example.espoti.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.SurfacePeach
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.espoti.ui.theme.BrandBrown


@Composable
private fun NavItem(
    label: String,
    @DrawableRes iconRes: Int,
    active: Boolean = false,
    onClick: () -> Unit = {}
) {
    val tint = if (active) BrandOrange else SurfacePeach
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .padding(bottom = 2.dp)
        )
        Text(text = label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Bottom nav bar: 4 regular tabs plus a floating "+" button that overlaps
 * the bar, matching the Figma frame. Tab icons are plain letters as
 * placeholders (see the "ICON SPOT" comment on each item) - the active
 * tab is colored orange, inactive tabs cream/tan on the brown bar.
 */
@Composable
fun EspotiBottomNav(onProfileClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandBrown, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NavItem(label = "Home", iconRes = R.drawable.homeicon, active = true)
            NavItem(label = "Meetings", iconRes = R.drawable.meetingicon)
            Spacer(modifier = Modifier.width(56.dp)) // room for the floating "+" button
            NavItem(label = "Friends", iconRes = R.drawable.frinendsicon)
            NavItem(label = "Profile", iconRes = R.drawable.profileicon, onClick = onProfileClick)
        }

        // ICON SPOT: floating "+" action button, e.g. "create new meeting".
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-18).dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(BrandOrange),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = androidx.compose.ui.graphics.Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}