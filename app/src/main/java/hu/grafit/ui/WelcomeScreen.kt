package hu.grafit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.grafit.R

@Composable
internal fun Welcome(vm: GrafitViewModel, onLogin: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center) {
        Surface(shape = CircleShape, color = colors.primaryContainer) {
            Icon(painterResource(R.drawable.ic_grafit_monochrome), null,
                Modifier.size(64.dp).graphicsLayer { scaleX = 1.8f; scaleY = 1.8f }, tint = colors.onPrimaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text("Szia, ez a Grafit.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Egy könnyebb iskolanap itt kezdődik. Minden fontos dolog egy helyen, a saját stílusodban.",
            style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
                colors.primaryContainer.copy(alpha = .45f), colors.tertiaryContainer.copy(alpha = .25f)
            ))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("A napod, átláthatóan.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WelcomeFeature("Órarend", Icons.Outlined.CalendarMonth, Modifier.weight(1f))
                    WelcomeFeature("Jegyek", Icons.Outlined.School, Modifier.weight(1f))
                    WelcomeFeature("Teendők", Icons.Outlined.Checklist, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Bejelentkezés KRÉTA-fiókkal", Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp))
        }
        vm.savedAccounts.forEach { (key, name) ->
            TextButton(onClick = { vm.switchAccount(key) }, enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth()) { Text("Belépés mentett fiókkal: $name") }
        }
        OutlinedButton(onClick = vm::startDemo, enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 52.dp)) {
            Text("Kipróbálom mintaadatokkal")
        }
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
            Text("A KRÉTA saját oldalán lépsz be. A jelszavadat a Grafit nem tárolja.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun WelcomeFeature(title: String, icon: ImageVector, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .7f)) {
            Icon(icon, null, Modifier.padding(10.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.labelMedium)
    }
}
