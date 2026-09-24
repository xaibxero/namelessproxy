package com.nameless.proxy

import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android profile calculation: UID / 100,000
        val myUid = Process.myUid()
        val profileId = myUid / 100000
        val inboundPort = 10800 + profileId

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    ProxyScaffold(profileId = profileId, inboundPort = inboundPort)
                }
            }
        }
    }
}

@Composable
fun ProxyScaffold(profileId: Int, inboundPort: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Nameless Proxy",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Active Profile: User $profileId",
            fontSize = 18.sp,
            color = Color(0xFF4CAF50)
        )
        Text(
            text = "Assigned Kernel Port: $inboundPort",
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
}
