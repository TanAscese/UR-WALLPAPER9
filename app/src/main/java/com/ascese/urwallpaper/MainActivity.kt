package com.ascese.urwallpaper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val sharedPreferences = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
            var isDarkMode by remember { mutableStateOf(sharedPreferences.getBoolean("dark_mode", true)) }

            MaterialTheme(
                colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WallpaperAppScreen(
                        context = this,
                        isDarkMode = isDarkMode,
                        onThemeChange = { newMode ->
                            isDarkMode = newMode
                            sharedPreferences.edit().putBoolean("dark_mode", newMode).apply()
                        }
                    )
                }
            }
        }
    }
}

data class WallpaperItem(
    val id: String,
    val uriString: String,
    val name: String,
    val playSound: Boolean = false,
    val speed: Float = 1.0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperAppScreen(context: Context, isDarkMode: Boolean, onThemeChange: (Boolean) -> Unit) {
    val sharedPreferences = context.getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
    val haptic = LocalHapticFeedback.current // NEW: Haptic Engine

    val savedVideosList = remember { mutableStateListOf<WallpaperItem>() }
    var activeWallpaperUri by remember { mutableStateOf(sharedPreferences.getString("video_uri", "")) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<WallpaperItem?>(null) }
    var editName by remember { mutableStateOf(TextFieldValue("")) }
    var editSound by remember { mutableStateOf(false) }
    var editSpeed by remember { mutableStateOf(1.0f) }

    fun loadSavedVideos() {
        savedVideosList.clear()
        val jsonString = sharedPreferences.getString("video_list_json", "[]")
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", obj.getString("uri"))
                val name = obj.getString("name")
                val sound = obj.optBoolean("playSound", false)
                val speed = obj.optDouble("speed", 1.0).toFloat()
                savedVideosList.add(WallpaperItem(id, obj.getString("uri"), name, sound, speed))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveFullListToStorage() {
        val jsonArray = JSONArray()
        savedVideosList.forEach {
            val obj = JSONObject()
            obj.put("id", it.id)
            obj.put("uri", it.uriString)
            obj.put("name", it.name)
            obj.put("playSound", it.playSound)
            obj.put("speed", it.speed)
            jsonArray.put(obj)
        }
        sharedPreferences.edit().putString("video_list_json", jsonArray.toString()).apply()
    }

    LaunchedEffect(Unit) { loadSavedVideos() }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            if (savedVideosList.size >= 20) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Toast.makeText(context, "Library Full! Delete an old wallpaper.", Toast.LENGTH_LONG).show()
                return@let
            }
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                var fileName = "Imported Video"
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) fileName = cursor.getString(nameIndex)
                }
                val newItem = WallpaperItem(System.currentTimeMillis().toString(), it.toString(), fileName)
                savedVideosList.add(0, newItem) // Add to top of list
                saveFullListToStorage()
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (e: Exception) {
                Toast.makeText(context, "Permission error", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("App Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Mode", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onThemeChange(it)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Pro Gestures:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("• Double-tap your home screen to instantly mute/unmute your wallpaper!")
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("CLOSE") }
            }
        )
    }

    if (itemToEdit != null) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(itemToEdit) { focusRequester.requestFocus() }

        AlertDialog(
            onDismissRequest = { itemToEdit = null },
            title = { Text("Tune Wallpaper") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        singleLine = true,
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Play Audio")
                        Switch(
                            checked = editSound,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                editSound = it
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Speed: ${String.format("%.1fx", editSpeed)}")
                    Slider(
                        value = editSpeed,
                        onValueChange = { editSpeed = it },
                        valueRange = 0.5f..2.0f,
                        steps = 14
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val index = savedVideosList.indexOfFirst { it.id == itemToEdit!!.id }
                        if (index != -1) {
                            savedVideosList[index] = savedVideosList[index].copy(
                                name = editName.text,
                                playSound = editSound,
                                speed = editSpeed
                            )
                            saveFullListToStorage()
                            if (savedVideosList[index].uriString == activeWallpaperUri) {
                                sharedPreferences.edit()
                                    .putBoolean("play_sound", editSound)
                                    .putFloat("playback_speed", editSpeed)
                                    .apply()
                            }
                        }
                        itemToEdit = null
                    }
                ) { Text("SAVE", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { itemToEdit = null }) { Text("CANCEL") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UR WALLPAPER", fontWeight = FontWeight.Black) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showSettingsDialog = true
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    videoPickerLauncher.launch(arrayOf("video/*"))
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Video", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {

            // --- NEW: THE HERO BANNER (NOW PLAYING) ---
            val activeItem = savedVideosList.find { it.uriString == activeWallpaperUri }
            if (activeItem != null) {
                Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(activeItem.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Speed: ${activeItem.speed}x • Audio: ${if (activeItem.playSound) "ON" else "OFF"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${savedVideosList.size} / 20", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(12.dp))

            // THE GRID
            val libraryItems = savedVideosList.filter { it.uriString != activeWallpaperUri }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (savedVideosList.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No wallpapers saved. Tap + to begin!", color = Color.Gray)
                        }
                    }
                }

                items(libraryItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) { Text("🎥") }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            activeWallpaperUri = item.uriString
                                            sharedPreferences.edit()
                                                .putString("video_uri", item.uriString)
                                                .putBoolean("play_sound", item.playSound)
                                                .putFloat("playback_speed", item.speed)
                                                .apply()

                                            val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                                            intent.putExtra(
                                                android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                                android.content.ComponentName(context, VideoWallpaperService::class.java)
                                            )
                                            context.startActivity(intent)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) { Text("APPLY", fontWeight = FontWeight.Black, fontSize = MaterialTheme.typography.labelSmall.fontSize) }

                                    Spacer(modifier = Modifier.weight(1f))

                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            editName = TextFieldValue(text = item.name, selection = TextRange(0, item.name.length))
                                            editSound = item.playSound
                                            editSpeed = item.speed
                                            itemToEdit = item
                                        }
                                    ) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant) }

                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            savedVideosList.remove(item)
                                            saveFullListToStorage()
                                        }
                                    ) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f)) }
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}