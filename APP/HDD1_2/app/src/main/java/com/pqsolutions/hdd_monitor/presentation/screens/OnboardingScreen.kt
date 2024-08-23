package com.pqsolutions.hdd_monitor.presentation.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pqsolutions.hdd_monitor.R
import com.pqsolutions.hdd_monitor.presentation.components.HddButton
import com.pqsolutions.hdd_monitor.presentation.util.Dimensions

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = listOf(
        OnboardingPage(
            title = "Bienvenido a HDD Monitor",
            description = "Monitorea y gestiona tus paneles de incendio de manera fácil y eficiente.",
            imageRes = R.drawable.onboarding_image
        ),
        OnboardingPage(
            title = "Alertas en tiempo real",
            description = "Recibe notificaciones instantáneas sobre el estado de tus paneles.",
            imageRes = R.drawable.onboarding_image
        ),
        OnboardingPage(
            title = "Gestión simplificada",
            description = "Administra usuarios, paneles y eventos desde una sola aplicación.",
            imageRes = R.drawable.onboarding_image
        )
    )

    val pagerState = rememberPagerState { pages.size }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            OnboardingPageContent(pages[page])
        }

        Row(
            Modifier.padding(Dimensions.paddingMedium),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages.size) { iteration ->
                val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(8.dp)
                )
            }
        }

        HddButton(
            onClick = onFinish,
            text = if (pagerState.currentPage == pages.lastIndex) "Empezar" else "Siguiente",
            modifier = Modifier
                .padding(Dimensions.paddingMedium)
                .fillMaxWidth()
        )
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.paddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = page.imageRes),
            contentDescription = null,
            modifier = Modifier.size(200.dp)
        )
        Spacer(modifier = Modifier.height(Dimensions.spacingLarge))
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimensions.spacingMedium))
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

data class OnboardingPage(
    val title: String,
    val description: String,
    val imageRes: Int
)