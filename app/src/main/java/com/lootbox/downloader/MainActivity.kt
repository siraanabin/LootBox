package com.lootbox.downloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Ink = Color(0xFF0B1020)
private val Panel = Color(0xFF171F35)
private val Purple = Color(0xFF9B7BFF)
private val Cyan = Color(0xFF55E6E0)

private data class DownloadItem(val id: Long, val name: String, val progress: Int = 0, val downloaded: Long = 0L, val total: Long = -1L, val state: String = "Queued")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LootBoxApp() }
    }
}

@Composable
fun LootBoxApp() {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready when you are") }
    var quality by remember { mutableStateOf("Auto") }
    var qualityMenu by remember { mutableStateOf(false) }
    val downloads = remember { mutableStateListOf<DownloadItem>() }
    val manager = remember { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }

    LaunchedEffect(Unit) {
        while (true) {
            downloads.indices.forEach { index ->
                val item = downloads[index]
                manager.query(DownloadManager.Query().setFilterById(item.id))?.use { c ->
                    if (c.moveToFirst()) {
                        val s = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val pct = if (total > 0) ((done * 100L) / total).toInt().coerceIn(0, 100) else 0
                        val state = when (s) {
                            DownloadManager.STATUS_PENDING -> "Waiting"
                            DownloadManager.STATUS_RUNNING -> "Downloading"
                            DownloadManager.STATUS_PAUSED -> "Paused"
                            DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                            DownloadManager.STATUS_FAILED -> "Failed"
                            else -> "Queued"
                        }
                        downloads[index] = item.copy(progress = pct, downloaded = done, total = total, state = state)
                    }
                }
            }
            delay(650)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Panel, primary = Purple, secondary = Cyan, onBackground = Color.White, onSurface = Color.White)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF17152F), Ink, Color(0xFF071C2A))))) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(9.dp)
                    .border(1.dp, Brush.linearGradient(listOf(Cyan.copy(alpha = .9f), Purple, Cyan.copy(alpha = .35f))), RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp)),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Purple.copy(alpha = .18f), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.CloudDownload, null, tint = Cyan, modifier = Modifier.padding(13.dp).size(30.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("LootBox", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("YOUR MEDIA. YOUR BOX.", fontSize = 10.sp, letterSpacing = 2.sp, color = Cyan)
                        }
                    }
                }
                item {
                    Surface(color = Panel.copy(alpha = .94f), shape = RoundedCornerShape(24.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("New download", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Paste a direct link to a file you own or are allowed to download.", color = Color(0xFFB7C1D8), fontSize = 13.sp)
                            OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("https://example.com/file.mp4") }, leadingIcon = { Icon(Icons.Default.Link, null) },
                                singleLine = true, shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF46516B), focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Quality", color = Color(0xFFB7C1D8), fontSize = 13.sp)
                                Spacer(Modifier.width(12.dp))
                                Box {
                                    OutlinedButton(onClick = { qualityMenu = true }) { Text(quality) }
                                    DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                                        listOf("Auto", "1080p", "720p", "480p", "360p").forEach { option ->
                                            DropdownMenuItem(text = { Text(option) }, onClick = { quality = option; qualityMenu = false })
                                        }
                                    }
                                }
                            }
                            Text("Quality selection works only when a source provides multiple versions. A direct file link has one fixed quality.", color = Color(0xFF9AA8C2), fontSize = 11.sp)
                            Button(onClick = {
                                startDownload(context, url) { message, id, fileName ->
                                    status = message
                                    if (id != null) downloads.add(0, DownloadItem(id, fileName ?: "Download"))
                                }
                            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Purple)) {
                                Icon(Icons.Default.CloudDownload, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Start download", fontWeight = FontWeight.Bold)
                            }
                            Text(status, color = Cyan, fontSize = 12.sp)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoCard("Downloads", downloads.size.toString(), Modifier.weight(1f))
                        InfoCard("Mode", "In-app progress", Modifier.weight(1f))
                    }
                }
                item { Text("Your downloads", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
                if (downloads.isEmpty()) {
                    item {
                        Surface(color = Panel.copy(alpha = .8f), shape = RoundedCornerShape(18.dp)) {
                            Text("Your download progress will appear here.", Modifier.padding(18.dp), color = Color(0xFFB7C1D8), fontSize = 13.sp)
                        }
                    }
                } else {
                    items(downloads, key = { it.id }) { item ->
                        Surface(color = Panel.copy(alpha = .94f), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CloudDownload, null, tint = Cyan)
                                    Spacer(Modifier.width(9.dp))
                                    Text(item.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(item.state, color = if (item.state == "Failed") Color(0xFFFF8A8A) else Cyan, fontSize = 11.sp)
                                }
                                LinearProgressIndicator(progress = item.progress / 100f, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)), color = Cyan, trackColor = Color(0xFF35415A))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (item.total > 0) item.progress.toString() + "%" else (item.downloaded / 1024).toString() + " KB", color = Color(0xFFB7C1D8), fontSize = 11.sp)
                                    Text(if (item.total > 0) (item.downloaded / 1024).toString() + " / " + (item.total / 1024).toString() + " KB" else "Size unknown", color = Color(0xFF9AA8C2), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                item {
                    Surface(color = Panel.copy(alpha = .8f), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = Cyan, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Saved to Downloads", fontWeight = FontWeight.SemiBold)
                                Text("Completed files are saved in your phone's Downloads folder.", color = Color(0xFFB7C1D8), fontSize = 12.sp)
                            }
                        }
                    }
                }
                item { Text("Privacy first • No ads • No account required", color = Color(0xFF9AA8C2), fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color(0xFFB7C1D8), fontSize = 11.sp)
            Text(value, color = Cyan, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun startDownload(context: Context, rawUrl: String, onStatus: (String, Long?, String?) -> Unit) {
    val value = rawUrl.trim()
    val uri = runCatching { Uri.parse(value) }.getOrNull()
    val host = uri?.host?.lowercase().orEmpty()
    if (host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") || host == "m.youtube.com" ||
        host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")) {
        onStatus("YouTube links are not direct files. Use a permitted direct file URL.", null, null)
        Toast.makeText(context, "This is not a direct file link", Toast.LENGTH_LONG).show()
        return
    }
    if (uri == null || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
        onStatus("Enter a valid http or https direct file link.", null, null)
        return
    }
    try {
        val fileName = URLUtil.guessFileName(value, null, null)
        val request = DownloadManager.Request(uri).setTitle(fileName).setDescription("LootBox download")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true).setAllowedOverRoaming(false)
        val id = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        onStatus("Download started — track progress below.", id, fileName)
        Toast.makeText(context, "Download started in LootBox", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        onStatus("Could not start. Check the link and try again.", null, null)
    }
}
