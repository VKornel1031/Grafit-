package hu.grafit.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.grafit.data.LocalProfile
import kotlinx.coroutines.launch

@Composable
internal fun ProfileAvatar(profile: LocalProfile, name: String, size: Dp, modifier: Modifier = Modifier) {
    val image = remember(profile.photo) {
        profile.photo?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.take(1).uppercase() }
    Box(modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(
        MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer
    ))), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(initials, style = if (size > 60.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
internal fun ProfileBubble(vm: GrafitViewModel, expanded: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (expanded) .9f else 1f, spring(dampingRatio = .6f), label = "profileBubbleScale")
    val rotation by animateFloatAsState(if (expanded) -8f else 0f, spring(), label = "profileBubbleRotation")
    IconButton(onClick = onClick, modifier = Modifier.padding(end = 8.dp).size(48.dp)
        .semantics { contentDescription = "Profil megnyitása" }
        .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = rotation }) {
        Surface(shape = CircleShape, shadowElevation = 3.dp,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)) {
            ProfileAvatar(vm.profile, vm.displayName, 40.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileMenu(vm: GrafitViewModel, onDismiss: () -> Unit, onSettings: () -> Unit, onLogout: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(vm.profile.displayName) { mutableStateOf(vm.profile.displayName) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.updateProfilePhoto(uri)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ProfileAvatar(vm.profile, vm.displayName, 88.dp)
            Spacer(Modifier.height(16.dp))
            Text(vm.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(if (vm.demo) "Bemutató profil" else vm.data?.school.orEmpty(),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !vm.busy) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Profilkép cseréje")
                }
                if (vm.profile.photo != null) IconButton(onClick = { vm.updateProfilePhoto(null) }, enabled = !vm.busy) {
                    Icon(Icons.Outlined.DeleteOutline, "Profilkép eltávolítása")
                }
            }
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 60) name = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("Megjelenített név") },
                placeholder = { Text(vm.data?.student ?: "A neved") }, singleLine = true,
                supportingText = {
                    val status = if (name.trim() == vm.profile.displayName) "Helyben mentve" else "Nem mentett módosítás"
                    Text("$status · ${name.length}/60")
                })
            Button(onClick = { vm.saveProfileName(name) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = !vm.busy && name.trim() != vm.profile.displayName) {
                Text("Név mentése")
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            vm.error?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
            HorizontalDivider(Modifier.padding(vertical = 20.dp))
            ProfileMenuAction("Beállítások", "Megjelenés, animációk és képfrissítés", Icons.Outlined.Tune) {
                scope.launchAfterHiding(sheetState, onDismiss, onSettings)
            }
            ProfileMenuAction(if (vm.demo) "Kilépés a bemutatóból" else "Kijelentkezés", null,
                Icons.AutoMirrored.Outlined.Logout, enabled = !vm.busy) {
                scope.launchAfterHiding(sheetState, onDismiss, onLogout)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun kotlinx.coroutines.CoroutineScope.launchAfterHiding(state: SheetState, onDismiss: () -> Unit, action: () -> Unit) =
    launch {
        state.hide()
        onDismiss()
        action()
    }

@Composable
private fun ProfileMenuAction(title: String, subtitle: String?, icon: androidx.compose.ui.graphics.vector.ImageVector,
                              enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
    Spacer(Modifier.height(8.dp))
}
