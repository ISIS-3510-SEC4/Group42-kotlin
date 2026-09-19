package com.example.espoti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.espoti.R
import com.example.espoti.ui.components.EspotiPrimaryButton
import com.example.espoti.ui.theme.BrandBrown
import com.example.espoti.ui.theme.BrandOrange
import com.example.espoti.ui.theme.EspotiTheme
import com.example.espoti.ui.theme.SurfacePeach
import com.example.espoti.ui.components.EspotiBottomNav
import com.example.espoti.ui.components.BottomNavItem

// ============================================================================
// INICIO (Home) SCREEN
// ----------------------------------------------------------------------------
// Layout, top to bottom (matches the Figma frame):
//   1. Header: small logo (left) + "Hello, Where do we meet?" greeting,
//      hamburger menu icon (right)
//   2. "Create new meeting" button (full width, brown pill)
//   3. "Next Meetings" -> horizontally scrolling row of meeting cards
//   4. "Invite us a coffee" donation card
//   5. "Meeting History" / "Previous Events" -> row of image placeholders
//   6. Bottom nav bar with a floating "+" button (EspotiBottomNav, below)
// ============================================================================

private data class Meeting(
    val place: String,
    val time: String,
    val distance: String,
    val peopleLabel: String
)

private val sampleMeetings = listOf(
    Meeting("Restaurant", "2:00 pm", "2 km", "Ana and two more"),
    Meeting("Park", "4:00 pm", "3 km", "Juan and two more")
)

@Composable
fun HomeScreen(onLogoutClick: () -> Unit, onMeetingsClick: () -> Unit) {
    Scaffold(
        bottomBar = {
            // "Logout" isn't a button in the Figma file - in a real app it
            // would live behind the hamburger menu or the Profile tab.
            // Wired here to the Profile tab for now so the prototype flow
            // (Login/Registro -> Inicio -> back to Bienvenida) still works.
            EspotiBottomNav(selectedItem = BottomNavItem.HOME, onMeetingsClick = onMeetingsClick, onProfileClick = onLogoutClick )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            HomeHeader()

            Spacer(modifier = Modifier.height(20.dp))

            EspotiPrimaryButton(
                text = "Create new meeting",
                onClick = { /* not wired up in this prototype */ }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Next Meetings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sampleMeetings.forEach { meeting -> MeetingCard(meeting) }
            }

            Spacer(modifier = Modifier.height(20.dp))

            CoffeeCard()

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Meeting History",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Previous Events",
                style = MaterialTheme.typography.bodyMedium,
                color = BrandOrange
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) { HistoryPlaceholder() }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.logoicon),
                contentDescription = "Espoti logo",
                modifier = Modifier
                    .size(40.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Hello,",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Where do we meet?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // ICON SPOT: hamburger menu. Replace with an IconButton +
        // Icons.Default.Menu (from "material-icons-extended") once you add
        // a real drawer/menu; plain text keeps this dependency-free for now.
        Text(
            text = "☰",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun MeetingCard(meeting: Meeting) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfacePeach)
            .padding(12.dp)
    ) {
        // ICON SPOT: location pin icon before the place name.
        Text(
            text = meeting.place,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = BrandBrown
        )
        Text(
            text = "${meeting.time}  •  ${meeting.distance}",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBrown
        )

        Spacer(modifier = Modifier.height(12.dp))

        AvatarStack(count = 3)

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = meeting.peopleLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBrown
        )
    }
}

@Composable
private fun AvatarStack(count: Int) {
    Row {
        // ICON SPOT: swap each circle for a real profile picture, e.g.
        //   Image(painter = painterResource(R.drawable.avatar1), ...)
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .offset(x = (-8 * index).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(BrandBrown)
            )
        }
    }
}

@Composable
private fun CoffeeCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfacePeach)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Invite us a coffee",
                style = MaterialTheme.typography.titleMedium,
                color = BrandBrown
            )
            // ICON SPOT: coffee cup icon.
            Text("☕", fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Help us grow, make a contribution.",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandBrown
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            EspotiPrimaryButton(
                text = "Support",
                onClick = { /* not wired up in this prototype */ },
                modifier = Modifier
                    .width(110.dp)
                    .height(40.dp)
            )
        }
    }
}

@Composable
private fun HistoryPlaceholder() {
    // ICON SPOT: replace with the actual meeting photo, e.g.
    //   Image(painter = painterResource(R.drawable.meeting1), ..., contentScale = ContentScale.Crop)
    Box(
        modifier = Modifier
            .size(width = 160.dp, height = 110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfacePeach)
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    EspotiTheme {
        HomeScreen(onLogoutClick = {},
            onMeetingsClick = {})
    }
}
