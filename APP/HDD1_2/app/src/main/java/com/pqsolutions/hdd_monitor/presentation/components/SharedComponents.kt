package com.pqsolutions.hdd_monitor.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.rememberCoroutineScope
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.presentation.util.performHapticFeedback
import com.pqsolutions.hdd_monitor.presentation.util.playSoundEffect
import kotlinx.coroutines.launch

@Composable
fun LogoutButton(onLogoutClick: () -> Unit, context: android.content.Context) {
    val coroutineScope = rememberCoroutineScope()

    Button(
        onClick = {
            coroutineScope.launch {
                performHapticFeedback(context)
                playSoundEffect(context, R.raw.button_click)
                onLogoutClick()
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Text(stringResource(R.string.logout))
    }
}