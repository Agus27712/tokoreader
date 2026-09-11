package com.tkc.screener.ui.components.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tkc.screener.ui.theme.*
import com.tkc.screener.util.DebugLogEntry
import com.tkc.screener.util.DebugLogManager
import com.tkc.screener.util.LogCategory
import java.util.Locale

@Composable
fun DebugLogDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val logs by DebugLogManager.logsState.collectAsState()

    var selectedCategory by remember { mutableStateOf(LogCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }

    val filteredLogs = remember(logs, selectedCategory, searchQuery) {
        logs.filter { entry ->
            val matchCategory = when (selectedCategory) {
                LogCategory.ALL -> true
                LogCategory.CONNECTION -> entry.category == LogCategory.CONNECTION || entry.level == "CONN"
                LogCategory.USER_ACTION -> entry.category == LogCategory.USER_ACTION || entry.level == "USER"
                LogCategory.TRADE -> entry.category == LogCategory.TRADE || entry.level == "TRADE"
                LogCategory.SYSTEM -> entry.category == LogCategory.SYSTEM
                LogCategory.ERROR -> entry.category == LogCategory.ERROR || entry.level == "ERROR" || entry.level == "WARN"
            }
            val matchQuery = if (searchQuery.isBlank()) true else {
                entry.message.contains(searchQuery, ignoreCase = true) ||
                        entry.tag.contains(searchQuery, ignoreCase = true) ||
                        entry.level.contains(searchQuery, ignoreCase = true)
            }
            matchCategory && matchQuery
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(filteredLogs.size, autoScroll) {
        if (autoScroll && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0D1117), // Dark terminal background
            border = BorderStroke(1.dp, TvBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // HEADER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = TvGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SYSTEM DEBUG LOGCAT",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${filteredLogs.size} dari ${logs.size} baris log terekam",
                                fontSize = 10.sp,
                                color = TvTextSecondary
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = { autoScroll = !autoScroll }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.Pause,
                                contentDescription = "Auto Scroll",
                                tint = if (autoScroll) TvGreen else TvTextMuted
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TvTextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // SEARCH BAR
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari log/kata kunci/tag...", fontSize = 11.sp, color = TvTextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TvTextSecondary, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, null, tint = TvTextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TvGreen,
                        unfocusedBorderColor = TvBorder,
                        focusedContainerColor = TvCardBackground,
                        unfocusedContainerColor = TvCardBackground,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                )

                Spacer(Modifier.height(8.dp))

                // CATEGORY CHIPS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        LogCategory.ALL to "SEMUA",
                        LogCategory.CONNECTION to "KONEKSI",
                        LogCategory.USER_ACTION to "ACTION",
                        LogCategory.TRADE to "TRADE",
                        LogCategory.ERROR to "ERROR"
                    ).forEach { (cat, label) ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) TvGreen.copy(alpha = 0.2f) else TvSurfaceVariant,
                            border = BorderStroke(0.5.dp, if (isSelected) TvGreen else TvBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedCategory = cat }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) TvGreen else TvTextSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 5.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // LOG CONSOLE VIEW
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF010409), RoundedCornerShape(8.dp))
                        .border(1.dp, TvBorder, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (filteredLogs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (logs.isEmpty()) "Belum ada log terekam." else "Tidak ada log yang cocok dengan filter.",
                                color = TvTextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredLogs, key = { it.id }) { item ->
                                LogLineItem(item)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // BOTTOM ACTION BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // EKSPOR KE SDCARD / DOWNLOADS
                    Button(
                        onClick = {
                            val res = DebugLogManager.exportLogsToStorage(context)
                            res.onSuccess { file ->
                                Toast.makeText(
                                    context,
                                    "Log Berhasil Diekspor!\nLokasi: ${file.absolutePath}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    "Gagal ekspor log: ${err.localizedMessage}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TvGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.3f).height(40.dp)
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("EKSPOR KE SDCARD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // SALIN CLIPBOARD
                    OutlinedButton(
                        onClick = {
                            if (filteredLogs.isNotEmpty()) {
                                val textToCopy = filteredLogs.joinToString("\n") {
                                    "[${it.formattedFullDateTime}] [${it.level}] [${it.tag}] ${it.message}"
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("TKCScreener Debug Log", textToCopy)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "${filteredLogs.size} baris log disalin!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, TvBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("SALIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // BERSIHKAN LOG
                    OutlinedButton(
                        onClick = {
                            DebugLogManager.clearLogs()
                            Toast.makeText(context, "Log dibersihkan", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TvRed),
                        border = BorderStroke(1.dp, TvRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(0.9f).height(40.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("HAPUS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogLineItem(entry: DebugLogEntry) {
    val levelColor = when (entry.level.uppercase()) {
        "ERROR", "FATAL" -> TvRed
        "WARN" -> TvAmber
        "USER" -> Color(0xFF00E5FF)
        "CONN" -> Color(0xFF8C9EFF)
        "TRADE" -> TvGreen
        else -> Color(0xFFB0BEC5)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = entry.formattedTime,
            color = Color(0xFF6E7681),
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(62.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "[${entry.level}]",
            color = levelColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(52.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "[${entry.tag}]",
            color = Color(0xFF8B949E),
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(80.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = entry.message,
            color = Color(0xFFC9D1D9),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            lineHeight = 13.sp
        )
    }
}
