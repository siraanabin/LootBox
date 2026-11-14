package com.lootbox.downloader

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.webkit.URLUtil

private val Ink = Color(0xFF0B1020)
private val Panel = Color(0xFF171F35)
private val Purple = Color(0xFF9B7BFF)
private val Cyan = Color(0xFF55E6E0)

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
    var downloads by remember { mutableStateOf(0) }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Purple,
        secondary = Cyan, onBackground = Color.White, onSurface = Color.White
    )) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF17152F), Ink, Color(0xFF071C2A)))
            )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(9.dp)
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(Cyan.copy(alpha = .9f), Purple, Cyan.copy(alpha = .35f))),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .clip(RoundedCornerShape(28.dp)),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Purple.copy(alpha = .18f), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.CloudDownload, null, tint = Cyan,
                                modifier = Modifier.padding(13.dp).size(30.dp))
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
                            Text("Paste a direct link to a file you own or are allowed to download.",
                                color = Color(0xFFB7C1D8), fontSize = 13.sp)
                            OutlinedTextField(
                                value = url, onValueChange = { url = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("https://example.com/file.mp4") },
                                leadingIcon = { Icon(Icons.Default.Link, null) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF46516B),
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                )
                            )
                            Button(
                                onClick = { startDownload(context, url) { message ->
                                    status = message
                                    if (message.startsWith("Download added")) downloads++
                                } },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Purple)
                            ) {
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
                        InfoCard("Downloads started", downloads.toString(), Modifier.weight(1f))
                        InfoCard("Mode", "Ad-free", Modifier.weight(1f))
                    }
                }
                item {
                    Surface(color = Panel.copy(alpha = .8f), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = Cyan, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Saved to Downloads", fontWeight = FontWeight.SemiBold)
                                Text("Check your phone's Downloads folder for completed files.",
                                    color = Color(0xFFB7C1D8), fontSize = 12.sp)
                            }
                        }
                    }
                }
                item {
                    Text("Privacy first • No ads • No account required",
                        color = Color(0xFF9AA8C2), fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
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

private fun startDownload(context: Context, rawUrl: String, onStatus: (String) -> Unit) {
    val value = rawUrl.trim()
    val uri = runCatching { Uri.parse(value) }.getOrNull()
    val host = uri?.host?.lowercase().orEmpty()
    if (host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ||
        host == "m.youtube.com" || host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")) {
        onStatus("YouTube links are not direct files. LootBox supports direct file URLs only.")
        Toast.makeText(context, "YouTube link isn't a direct download link", Toast.LENGTH_LONG).show()
        return
    }
    if (uri == null || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
        onStatus("Enter a valid http or https direct file link.")
        return
    }
    try {
        val request = DownloadManager.Request(uri)
            .setTitle(URLUtil.guessFileName(value, null, null))
            .setDescription("Downloading with LootBox")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                URLUtil.guessFileName(value, null, null))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        onStatus("Download added to Android Download Manager.")
        Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        onStatus("Could not start. Check the link and try again.")
    }
}
