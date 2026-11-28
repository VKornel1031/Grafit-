package hu.grafit.ui

import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import hu.grafit.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun RoomFinderScreen(vm: GrafitViewModel) {
    val floors = vm.extras.floors
    var floorId by rememberSaveable { mutableStateOf(floors.firstOrNull()?.id) }
    var query by rememberSaveable { mutableStateOf("") }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val activity = androidx.activity.compose.LocalActivity.current
    LaunchedEffect(Unit) { activity?.intent?.getStringExtra("room")?.let { query = it; activity.intent.removeExtra("room") } }
    LaunchedEffect(query, floors) {
        if (query.isNotBlank()) floors.firstOrNull { floor -> floor.pins.any { searchKey(it.room).trim() == searchKey(query).trim() } }?.let { floorId = it.id }
    }
    val floor = floors.firstOrNull { it.id == floorId } ?: floors.firstOrNull()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importToolFile(uri) { file ->
            if (!file.mime.startsWith("image/")) { vm.discardToolFile(file.id); message = "Alaprajzhoz válassz képet." }
            else {
                val item = SchoolFloor(UUID.randomUUID().toString(), "Alaprajz ${floors.size + 1}", file.id, file.mime)
                vm.updateExtras(vm.extras.copy(floors = vm.extras.floors + item)); floorId = item.id
            }
        }
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Teremkereső", style = MaterialTheme.typography.headlineSmall)
            Text("Add hozzá az iskolád alaprajzát, majd hosszan érintsd meg a tantermek helyét. Az alaprajzot te jelölöd, nincs automatikus iskolai térkép.")
            OutlinedTextField(query, { query = it }, label = { Text("Terem keresése") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !vm.extrasBusy && floors.size < 10) { Text("Alaprajz hozzáadása · max. 8 MB") }
            if (vm.extrasBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        items(floors.chunked(2)) { pair -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { pair.forEach { item -> FilterChip(item.id == floor?.id, { floorId = item.id }, label = { Text(item.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, modifier = Modifier.weight(1f)) } } }
        floor?.let { plan ->
            item {
                Row { TextButton(onClick = { rename = true }) { Text("Alaprajz átnevezése") }; TextButton(onClick = { delete = true }) { Text("Törlés") } }
                key(vm.activeAccount, plan.id) { FloorCanvas(vm, plan, query) { pins -> vm.updateExtras(vm.extras.copy(floors = vm.extras.floors.map { if (it.id == plan.id) it.copy(pins = pins) else it })) } }
            }
            val pins = plan.pins.filter { searchKey(it.room).contains(searchKey(query)) }
            if (pins.isEmpty()) item { Text(if (query.isBlank()) "Még nincs megjelölt terem. Hosszan érintsd meg a helyét a képen." else "Ezen az alaprajzon nincs ilyen megjelölt terem.") }
            items(pins, key = { it.room }) { pin ->
                GrafitCard(Modifier.fillMaxWidth(), onClick = { query = pin.room }) { Row(Modifier.padding(12.dp)) {
                    Text("${plan.pins.indexOf(pin) + 1}. ${pin.room}", Modifier.weight(1f))
                    TextButton(onClick = { vm.updateExtras(vm.extras.copy(floors = vm.extras.floors.map { if (it.id == plan.id) it.copy(pins = it.pins - pin) else it })) }) { Text("Jelölés törlése") }
                } }
            }
        }
    }
    if (rename && floor != null) {
        var name by remember(floor.id) { mutableStateOf(floor.name) }
        AlertDialog(onDismissRequest = { rename = false }, title = { Text("Alaprajz neve") }, text = { OutlinedTextField(name, { if (it.length <= 60) name = it }, label = { Text("Például: Földszint") }) },
            confirmButton = { TextButton(onClick = { vm.updateExtras(vm.extras.copy(floors = floors.map { if (it.id == floor.id) it.copy(name = name.trim()) else it })); rename = false }, enabled = name.isNotBlank()) { Text("Mentés") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Mégse") } })
    }
    if (delete && floor != null) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Alaprajz törlése?") }, text = { Text("Az alaprajz és a hozzá tartozó teremjelölések törlődnek.") },
        confirmButton = { TextButton(onClick = { vm.updateExtras(vm.extras.copy(floors = floors.filterNot { it.id == floor.id })); delete = false }) { Text("Törlés") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Mégse") } })
}
@Composable
private fun FloorCanvas(vm: GrafitViewModel, floor: SchoolFloor, query: String, onPins: (List<RoomPin>) -> Unit) {
    val account = vm.activeAccount
    var error by remember { mutableStateOf<String?>(null) }
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, account, floor.attachment) {
        value = withContext(Dispatchers.IO) { runCatching { account?.let { vm.extrasStore.image(it, floor.attachment).asImageBitmap() } }.getOrElse { error = "Nem olvasható az alaprajz. Töltsd be újra a képet."; null } }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var location by remember { mutableStateOf<Offset?>(null) }
    var room by remember { mutableStateOf("") }
    val color = MaterialTheme.colorScheme.primary
    val onColor = MaterialTheme.colorScheme.onPrimary
    val latestPins by rememberUpdatedState(floor.pins)
    val bitmap = image
    if (bitmap == null) { Text(error ?: "Alaprajz betöltése…"); return }
    Text("Két ujjal nagyíthatsz; húzással mozgathatod. Új teremhez tartsd nyomva a helyét.", style = MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(340.dp).semantics { contentDescription = "${floor.name}, ${floor.pins.size} teremjelölés. A megjelölt termek a kép alatti listából is kiválaszthatók." }
        .pointerInput(bitmap) { detectTransformGestures { _, offset, factor, _ ->
            zoom = (zoom * factor).coerceIn(1f, 5f)
            pan = Offset((pan.x + offset.x).coerceIn(-size.width * 2f, size.width * 2f), (pan.y + offset.y).coerceIn(-size.height * 2f, size.height * 2f))
        } }
        .pointerInput(bitmap) { detectTapGestures(onLongPress = { tap ->
            val scale = minOf(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height) * zoom
            val origin = Offset((size.width - bitmap.width * scale) / 2f, (size.height - bitmap.height * scale) / 2f) + pan
            val point = Offset((tap.x - origin.x) / (bitmap.width * scale), (tap.y - origin.y) / (bitmap.height * scale))
            if (point.x in 0f..1f && point.y in 0f..1f) { location = point; room = query }
        }) }) {
        val scale = minOf(size.width / bitmap.width, size.height / bitmap.height) * zoom
        val width = bitmap.width * scale; val height = bitmap.height * scale
        val origin = Offset((size.width - width) / 2f, (size.height - height) / 2f) + pan
        clipRect {
            drawImage(bitmap, dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()), dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = onColor.toArgb(); textSize = 12.dp.toPx(); textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
            floor.pins.forEachIndexed { i, pin ->
                val point = origin + Offset(pin.x * width, pin.y * height)
                val selected = query.isNotBlank() && searchKey(pin.room) == searchKey(query)
                drawCircle(color, (if (selected) 18 else 13).dp.toPx(), point)
                drawContext.canvas.nativeCanvas.drawText("${i + 1}", point.x, point.y - (paint.ascent() + paint.descent()) / 2, paint)
            }
        }
    }
    Row {
        TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Alaphelyzet") }
        TextButton(onClick = { location = Offset(.5f, .5f); room = query }) { Text("Teremjelölés hozzáadása") }
    }
    location?.let { point ->
        AlertDialog(onDismissRequest = { location = null }, title = { Text("Terem megjelölése") }, text = { Column {
                OutlinedTextField(room, { if (it.length <= 40) room = it }, label = { Text("Terem neve / száma") })
                Text("Vízszintes helyzet")
                Slider(location?.x ?: point.x, { location = Offset(it, location?.y ?: point.y) })
                Text("Függőleges helyzet")
                Slider(location?.y ?: point.y, { location = Offset(location?.x ?: point.x, it) })
            } },
            confirmButton = { TextButton(onClick = { onPins(latestPins.filterNot { searchKey(it.room) == searchKey(room.trim()) } + RoomPin(room.trim(), location?.x ?: point.x, location?.y ?: point.y)); location = null }, enabled = room.isNotBlank()) { Text("Mentés") } },
            dismissButton = { TextButton(onClick = { location = null }) { Text("Mégse") } })
    }
}
