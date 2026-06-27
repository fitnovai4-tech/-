package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.example.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.data.model.Note
import com.example.ui.viewmodel.NoteViewModel
import com.example.util.FileHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteViewModel,
    noteId: Int?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val themeMode by viewModel.themeMode.collectAsState()
    val isDark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    // Temporary editor state
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var contentValue by remember { mutableStateOf(TextFieldValue("")) }
    var tags by remember { mutableStateOf("") }
    var fullscreenImagePath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(content) {
        if (contentValue.text != content) {
            contentValue = contentValue.copy(
                text = content,
                selection = androidx.compose.ui.text.TextRange(content.length)
            )
        }
    }

    fun applyFormatting(prefix: String, suffix: String) {
        val currentText = contentValue.text
        val selection = contentValue.selection
        
        val newText: String
        val newSelectionStart: Int
        val newSelectionEnd: Int
        
        if (!selection.collapsed) {
            val selectedText = currentText.substring(selection.start, selection.end)
            val formatted = "$prefix$selectedText$suffix"
            newText = currentText.replaceRange(selection.start, selection.end, formatted)
            newSelectionStart = selection.start
            newSelectionEnd = selection.start + formatted.length
        } else {
            val formatted = "${prefix}نص${suffix}"
            newText = currentText.replaceRange(selection.start, selection.start, formatted)
            newSelectionStart = selection.start + prefix.length
            newSelectionEnd = newSelectionStart + "نص".length
        }
        
        content = newText
        contentValue = TextFieldValue(
            text = newText,
            selection = androidx.compose.ui.text.TextRange(newSelectionStart, newSelectionEnd)
        )
    }

    fun applyBlockFormatting(linePrefix: String) {
        val currentText = contentValue.text
        val selection = contentValue.selection
        
        val newText: String
        val newSelectionStart: Int
        val newSelectionEnd: Int
        
        if (!selection.collapsed) {
            val selectedText = currentText.substring(selection.start, selection.end)
            val lines = selectedText.split("\n")
            val formattedLines = lines.map { line ->
                if (line.trim().startsWith(linePrefix.trim())) {
                    line.replaceFirst(linePrefix, "")
                } else {
                    "$linePrefix$line"
                }
            }
            val formatted = formattedLines.joinToString("\n")
            newText = currentText.replaceRange(selection.start, selection.end, formatted)
            newSelectionStart = selection.start
            newSelectionEnd = selection.start + formatted.length
        } else {
            val cursorPosition = selection.start
            var lineStart = cursorPosition
            while (lineStart > 0 && currentText[lineStart - 1] != '\n') {
                lineStart--
            }
            
            val currentLine = currentText.substring(lineStart, cursorPosition)
            val formatted = if (currentLine.startsWith(linePrefix)) {
                currentLine.substring(linePrefix.length)
            } else {
                "$linePrefix$currentLine"
            }
            
            newText = currentText.replaceRange(lineStart, cursorPosition, formatted)
            val diff = formatted.length - currentLine.length
            val newCursor = (cursorPosition + diff).coerceIn(0, newText.length)
            newSelectionStart = newCursor
            newSelectionEnd = newCursor
        }
        
        content = newText
        contentValue = TextFieldValue(
            text = newText,
            selection = androidx.compose.ui.text.TextRange(newSelectionStart, newSelectionEnd)
        )
    }

    var currentNote by remember { mutableStateOf(Note(title = "", content = "")) }

    // Table & Preview State variables
    var showTableDialog by remember { mutableStateOf(false) }
    var tableCols by remember { mutableStateOf("3") }
    var tableRows by remember { mutableStateOf("3") }
    var tableColHeaders by remember { mutableStateOf("العمود ١, العمود ٢, العمود ٣") }

    // Lock Screen states
    var showLockDialog by remember { mutableStateOf(false) }
    var lockPasswordInput by remember { mutableStateOf("") }

    // Loading Note
    val notesList by viewModel.notes.collectAsState()
    
    // Check if loading existing note
    LaunchedEffect(noteId, notesList) {
        if (noteId != null && noteId != 0) {
            val existingNote = notesList.find { it.id == noteId }
            if (existingNote != null) {
                currentNote = existingNote
                title = existingNote.title
                content = existingNote.content
                tags = existingNote.tags
            }
        }
    }

    // Media states
    val isRecording by viewModel.isRecording.collectAsState()
    val recordDuration by viewModel.recordDuration.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playingNoteId by viewModel.playingNoteId.collectAsState()
    val audioProgress by viewModel.audioProgress.collectAsState()

    // Smart status
    val isSmartLoading by viewModel.isSmartLoading.collectAsState()
    val smartMessage by viewModel.smartMessage.collectAsState()

    // Show Smart Message Toast
    LaunchedEffect(smartMessage) {
        smartMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSmartMessage()
        }
    }

    // Gallery Picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.attachImage(it, currentNote) { updatedNote ->
                currentNote = updatedNote
                viewModel.saveNote(updatedNote)
            }
        }
    }

    // PDF Picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.attachPdf(it, currentNote) { updatedNote ->
                currentNote = updatedNote
                viewModel.saveNote(updatedNote)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == null || noteId == 0) "إضافة ملاحظة جديدة" else "تعديل الملاحظة الذكية", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        // Auto-save before going back
                        if (title.isNotBlank() || content.isNotBlank()) {
                            viewModel.saveNote(currentNote.copy(title = title, content = content, tags = tags))
                        }
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    // Favorite button
                    IconButton(onClick = {
                        currentNote = currentNote.copy(isFavorite = !currentNote.isFavorite)
                        viewModel.saveNote(currentNote)
                    }) {
                        Icon(
                            imageVector = if (currentNote.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "تفضيل",
                            tint = if (currentNote.isFavorite) Color.Red else MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Lock Button
                    IconButton(onClick = {
                        if (currentNote.isLocked) {
                            currentNote = currentNote.copy(isLocked = false, notePassword = null)
                            viewModel.saveNote(currentNote)
                            Toast.makeText(context, "تم إلغاء قفل الملاحظة", Toast.LENGTH_SHORT).show()
                        } else {
                            showLockDialog = true
                        }
                    }) {
                        Icon(
                            imageVector = if (currentNote.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = "قفل أمني",
                            tint = if (currentNote.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Save Button
                    IconButton(onClick = {
                        if (title.isBlank() && content.isBlank()) {
                            Toast.makeText(context, "الرجاء كتابة عنوان أو محتوى للملاحظة", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.saveNote(currentNote.copy(title = title, content = content, tags = tags)) {
                                Toast.makeText(context, "تم حفظ التعديلات بنجاح", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "حفظ", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        val gradientColors = if (isDark) {
            listOf(
                Color(0xFF121316), // Dark slate
                Color(0xFF1B1B1F), // Dark background
                Color(0xFF1F1F24)  // Dark carbon
            )
        } else {
            listOf(
                Color(0xFFE3F2FD), // Subtle soft neon blue
                Color(0xFFBBDEFB), // Delicate ice blue
                Color(0xFFECEFF1)  // Smooth metallic sky-grey
            )
        }
        val glassyBlueGradient = androidx.compose.ui.graphics.Brush.linearGradient(colors = gradientColors)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(glassyBlueGradient)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // Title Field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان الملاحظة") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.6f),
                        focusedContainerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.65f),
                        unfocusedContainerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.45f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tags Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("الوسوم والكلمات الدلالية (مفصولة بفاصلة)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.6f),
                            focusedContainerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.65f),
                            unfocusedContainerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.45f)
                        ),
                        trailingIcon = {
                            // AI Auto tagging button
                            IconButton(
                                onClick = {
                                    val tempNote = currentNote.copy(title = title, content = content)
                                    viewModel.triggerAutoTagging(tempNote) { aiTags ->
                                        tags = aiTags
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = "توليد وسوم تلقائية بالذكاء الاصطناعي",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Media Attachment Previews
                
                // Attached Image preview
                if (currentNote.imagePath != null) {
                    val file = File(currentNote.imagePath!!)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        bitmap?.let {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "الصورة المرفقة",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { fullscreenImagePath = currentNote.imagePath },
                                    contentScale = ContentScale.Crop
                                )
                                // Delete / Analyze Overlay
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                ) {
                                    // Analyze Image via Gemini
                                    IconButton(
                                        onClick = {
                                            viewModel.triggerImageAnalysis(currentNote, "") { analysisResult ->
                                                content = "$content\n\n[تحليل الصورة بالذكاء الاصطناعي]\n$analysisResult"
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "تحليل الصورة", tint = Color.White)
                                    }
                                    // Delete attachment
                                    IconButton(
                                        onClick = {
                                            File(currentNote.imagePath!!).delete()
                                            currentNote = currentNote.copy(imagePath = null)
                                            viewModel.saveNote(currentNote)
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف الصورة", tint = Color.Red)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                // Attached PDF preview
                if (currentNote.pdfPath != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = "ملف PDF", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("مستند PDF مرفق بنجاح", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("يمكنك استخدام أداة الرسم للتعليق على صفحات هذا الملف وتعديلها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                            }
                            // Delete PDF
                            IconButton(onClick = {
                                File(currentNote.pdfPath!!).delete()
                                currentNote = currentNote.copy(pdfPath = null)
                                viewModel.saveNote(currentNote)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف الـ PDF", tint = Color.Red)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Canvas Drawing preview
                if (currentNote.drawingPath != null) {
                    val file = File(currentNote.drawingPath!!)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        bitmap?.let {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "الرسم التوضيحي",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { fullscreenImagePath = currentNote.drawingPath },
                                    contentScale = ContentScale.Fit
                                )
                                // Delete drawing
                                IconButton(
                                    onClick = {
                                        File(currentNote.drawingPath!!).delete()
                                        currentNote = currentNote.copy(drawingPath = null)
                                        viewModel.saveNote(currentNote)
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف الرسم", tint = Color.Red)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                // Voice Recording controls / player
                if (currentNote.voicePath != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activePlaying = playingNoteId == currentNote.id && isPlaying
                            IconButton(onClick = { viewModel.toggleAudioPlayback(currentNote) }) {
                                Icon(
                                    imageVector = if (activePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (activePlaying) "إيقاف مؤقت" else "تشغيل"
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تسجيل صوتي", fontWeight = FontWeight.Bold)
                                if (activePlaying) {
                                    LinearProgressIndicator(progress = { audioProgress }, modifier = Modifier.fillMaxWidth())
                                } else {
                                    // Show duration
                                    val minutes = (currentNote.voiceDuration / 1000) / 60
                                    val seconds = (currentNote.voiceDuration / 1000) % 60
                                    Text(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds))
                                }
                            }

                            // Delete recording
                            IconButton(onClick = {
                                File(currentNote.voicePath!!).delete()
                                currentNote = currentNote.copy(voicePath = null, voiceDuration = 0)
                                viewModel.saveNote(currentNote)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف التسجيل", tint = Color.Red)
                            }
                        }
                    }
                }

                // Reminder scheduling display
                if (currentNote.reminderTime != null) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Alarm, contentDescription = "تنبيه", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "ميعاد التذكير: " + sdf.format(Date(currentNote.reminderTime!!)),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                viewModel.cancelReminder(currentNote)
                                currentNote = currentNote.copy(reminderTime = null)
                                Toast.makeText(context, "تم إلغاء التنبيه", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "إلغاء التنبيه", tint = Color.Red)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 1. Definition of Markdown Visual Transformation
                val boldColor = MaterialTheme.colorScheme.primary
                val italicColor = MaterialTheme.colorScheme.secondary
                val markdownVisualTransformation = remember(boldColor, italicColor) {
                    androidx.compose.ui.text.input.VisualTransformation { text ->
                        val annotated = androidx.compose.ui.text.buildAnnotatedString {
                            val rawText = text.text
                            append(rawText)
                            
                            var cursor = 0
                            while (cursor < rawText.length) {
                                val nextBold = rawText.indexOf("**", cursor)
                                val nextItalicStar = rawText.indexOf("*", cursor)
                                val nextItalicUnder = rawText.indexOf("_", cursor)
                                
                                val targets = listOf(
                                    Triple("**", nextBold, 2),
                                    Triple("*", nextItalicStar, 1),
                                    Triple("_", nextItalicUnder, 1)
                                ).filter { it.second != -1 }.sortedBy { it.second }
                                
                                if (targets.isEmpty()) {
                                    break
                                }
                                
                                val (symbol, index, len) = targets.first()
                                cursor = index + len
                                
                                val closingIndex = rawText.indexOf(symbol, cursor)
                                if (closingIndex != -1) {
                                    val style = when (symbol) {
                                        "**" -> androidx.compose.ui.text.SpanStyle(
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = boldColor
                                        )
                                        "*", "_" -> androidx.compose.ui.text.SpanStyle(
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = italicColor
                                        )
                                        else -> androidx.compose.ui.text.SpanStyle()
                                    }
                                    addStyle(androidx.compose.ui.text.SpanStyle(color = Color.Gray.copy(alpha = 0.5f)), index, index + len)
                                    addStyle(style, index + len, closingIndex)
                                    addStyle(androidx.compose.ui.text.SpanStyle(color = Color.Gray.copy(alpha = 0.5f)), closingIndex, closingIndex + len)
                                    cursor = closingIndex + len
                                }
                            }
                        }
                        androidx.compose.ui.text.input.TransformedText(annotated, androidx.compose.ui.text.input.OffsetMapping.Identity)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Content Editing Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .border(1.dp, if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Insert Table button
                    TextButton(
                        onClick = { showTableDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.GridOn, contentDescription = "إدراج جدول", modifier = Modifier.size(20.dp))
                            Text("إدراج جدول ذكي", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp), color = if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.6f))

                    // Bold shortcut
                    IconButton(onClick = { applyFormatting("**", "**") }) {
                        Icon(Icons.Default.FormatBold, contentDescription = "خط عريض", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Italic shortcut
                    IconButton(onClick = { applyFormatting("_", "_") }) {
                        Icon(Icons.Default.FormatItalic, contentDescription = "خط مائل", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Bullet list shortcut
                    IconButton(onClick = { applyBlockFormatting("* ") }) {
                        Icon(Icons.Default.FormatListBulleted, contentDescription = "قائمة نقطية", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Numbered list shortcut
                    IconButton(onClick = { applyBlockFormatting("1. ") }) {
                        Icon(Icons.Default.FormatListNumbered, contentDescription = "قائمة مرقمة", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Header shortcut
                    IconButton(onClick = { applyBlockFormatting("## ") }) {
                        Icon(Icons.Default.Title, contentDescription = "عنوان رئيسي", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Content Field with visual formatting applied live
                OutlinedTextField(
                    value = contentValue,
                    onValueChange = { newValue ->
                        contentValue = newValue
                        if (content != newValue.text) {
                            content = newValue.text
                        }
                    },
                    label = { Text("ابدأ بكتابة محتوى الملاحظة وأفكارك هنا...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.6f),
                        focusedContainerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.65f),
                        unfocusedContainerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.45f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    singleLine = false,
                    visualTransformation = markdownVisualTransformation
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "معاينة تفاعلية وتعديل حي للجداول:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Always-on Interactive Live Preview Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f)),
                    colors = CardDefaults.cardColors(containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        if (content.trim().isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "اكتب محتوى أعلاه لرؤية المعاينة الحية والتفاعل مع الجدول.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            val parsedBlocks = parseNoteContent(content)
                            var tableBlockIndex = 0
                            parsedBlocks.forEach { block ->
                                when (block) {
                                    is ContentBlock.HeaderBlock -> {
                                        val style = when (block.level) {
                                            1 -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
                                            2 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                                            3 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                                            else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            text = renderMarkdownText(block.text),
                                            style = style,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                    is ContentBlock.ListItemBlock -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = if (block.isOrdered) "${block.index}. " else "• ",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(end = 4.dp)
                                            )
                                            Text(
                                                text = renderMarkdownText(block.text),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    is ContentBlock.TextBlock -> {
                                        if (block.text.isNotEmpty()) {
                                            Text(
                                                text = renderMarkdownText(block.text),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                    is ContentBlock.TableBlock -> {
                                        val currentTableIdx = tableBlockIndex
                                        tableBlockIndex++
                                        RenderTable(
                                            headers = block.headers,
                                            rows = block.rows,
                                            isEditable = true,
                                            onCellChange = { r, c, newValue ->
                                                val updated = updateTableCellInMarkdown(content, currentTableIdx, r, c, newValue)
                                                content = updated
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(96.dp)) // padding for the bottom bar
            }

            // Full Screen Image Dialog
            if (fullscreenImagePath != null) {
                val file = File(fullscreenImagePath!!)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    bitmap?.let {
                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = { fullscreenImagePath = null },
                            properties = androidx.compose.ui.window.DialogProperties(
                                usePlatformDefaultWidth = false
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black)
                            ) {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "عرض ملء الشاشة",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                                // Close Button
                                IconButton(
                                    onClick = { fullscreenImagePath = null },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(16.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // Table Creator Dialog
            if (showTableDialog) {
                AlertDialog(
                    onDismissRequest = { showTableDialog = false },
                    title = {
                        Text(
                            text = "إنشاء جدول جديد",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "اختر عدد الصفوف والأعمدة لإدراج قالب جدول منسق تلقائياً داخل الملاحظة:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = tableCols,
                                    onValueChange = { tableCols = it },
                                    label = { Text("الأعمدة") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = tableRows,
                                    onValueChange = { tableRows = it },
                                    label = { Text("الصفوف") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }

                            OutlinedTextField(
                                value = tableColHeaders,
                                onValueChange = { tableColHeaders = it },
                                label = { Text("عناوين الأعمدة (مفصولة بفواصل)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val colsCount = tableCols.toIntOrNull() ?: 3
                                val rowsCount = tableRows.toIntOrNull() ?: 3
                                val headersList = tableColHeaders.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val headers = if (headersList.size >= colsCount) {
                                    headersList.subList(0, colsCount)
                                } else {
                                    headersList + List(colsCount - headersList.size) { "العمود ${it + headersList.size + 1}" }
                                }

                                val sb = StringBuilder()
                                sb.append("\n\n")
                                sb.append("| ").append(headers.joinToString(" | ")).append(" |").append("\n")
                                sb.append("| ").append(List(colsCount) { "---" }.joinToString(" | ")).append(" |").append("\n")
                                for (r in 1..rowsCount) {
                                    sb.append("| ").append(List(colsCount) { "خلية ${r}" }.joinToString(" | ")).append(" |").append("\n")
                                }
                                sb.append("\n")

                                content = content + sb.toString()
                                showTableDialog = false
                            }
                        ) {
                            Text("إدراج الجدول")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTableDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            // Bottom Actions Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.65f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.7f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Add Image
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Outlined.Image, contentDescription = "إرفاق صورة")
                    }

                    // 2. Add PDF
                    IconButton(onClick = { pdfPickerLauncher.launch("application/pdf") }) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = "إرفاق PDF")
                    }

                    // 3. Audio Recording Action
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                viewModel.stopVoiceRecording(currentNote) { updatedNote ->
                                    currentNote = updatedNote
                                    viewModel.saveNote(updatedNote)
                                }
                            } else {
                                viewModel.startVoiceRecording()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Outlined.Mic,
                            contentDescription = if (isRecording) "إيقاف التسجيل" else "تسجيل صوتي",
                            tint = if (isRecording) Color.Red else LocalContentColor.current
                        )
                    }

                    // 4. Drawing Canvas Action
                    IconButton(
                        onClick = {
                            // Ensure the note is saved first, so drawing canvas can load attachments
                            viewModel.saveNote(currentNote.copy(title = title, content = content, tags = tags)) { id ->
                                currentNote = currentNote.copy(id = id, title = title, content = content, tags = tags)
                                viewModel.navigateToCanvas()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Gesture, contentDescription = "رسم وتعليق")
                    }

                    // 5. Set Reminder
                    IconButton(
                        onClick = {
                            // Date Picker Dialog
                            val calendar = Calendar.getInstance()
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    calendar.set(Calendar.YEAR, year)
                                    calendar.set(Calendar.MONTH, month)
                                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    
                                    // Time Picker Dialog
                                    TimePickerDialog(
                                        context,
                                        { _, hourOfDay, minute ->
                                            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            calendar.set(Calendar.MINUTE, minute)
                                            
                                            val scheduleTime = calendar.timeInMillis
                                            viewModel.scheduleReminder(
                                                currentNote.copy(title = title, content = content, tags = tags),
                                                scheduleTime
                                            )
                                            currentNote = currentNote.copy(reminderTime = scheduleTime, title = title, content = content, tags = tags)
                                            Toast.makeText(context, "تم ضبط منبه التذكير", Toast.LENGTH_SHORT).show()
                                        },
                                        calendar.get(Calendar.HOUR_OF_DAY),
                                        calendar.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    ) {
                        Icon(Icons.Outlined.Alarm, contentDescription = "إضافة منبه وتذكير")
                    }

                    // 6. AI Summarize note
                    IconButton(
                        onClick = {
                            val tempNote = currentNote.copy(title = title, content = content)
                            viewModel.triggerSummarization(tempNote) { summary ->
                                content = "$content\n\n[الملخص الذكي للملاحظة]\n$summary"
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "تلخيص الملاحظة بالذكاء الاصطناعي")
                    }
                }
            }

            // Recording Overlay
            AnimatedVisibility(
                visible = isRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(24.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color.Red)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري تسجيل الصوت...", color = Color.White)
                    }

                    val mins = (recordDuration / 1000) / 60
                    val secs = (recordDuration / 1000) % 60
                    Text(
                        text = String.format(Locale.getDefault(), "%02d:%02d", mins, secs),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        onClick = {
                            viewModel.stopVoiceRecording(currentNote) { updatedNote ->
                                currentNote = updatedNote
                                viewModel.saveNote(updatedNote)
                            }
                        }
                    ) {
                        Text("إنهاء", color = Color.White)
                    }
                }
            }

            // AI Loading indicator
            if (isSmartLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            if (showLockDialog) {
                AlertDialog(
                    onDismissRequest = { showLockDialog = false },
                    title = {
                        Text(
                            text = "قفل الملاحظة بكلمة مرور",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "أدخل كلمة المرور التي ترغب في استخدامها لحماية هذه الملاحظة:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = lockPasswordInput,
                                onValueChange = { lockPasswordInput = it },
                                label = { Text("كلمة المرور") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (lockPasswordInput.isNotBlank()) {
                                    currentNote = currentNote.copy(
                                        isLocked = true,
                                        notePassword = lockPasswordInput
                                    )
                                    viewModel.saveNote(currentNote)
                                    showLockDialog = false
                                    lockPasswordInput = ""
                                    Toast.makeText(context, "تم قفل الملاحظة بنجاح", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "الرجاء إدخال كلمة مرور صالحة", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("قفل")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLockDialog = false }) {
                            Text("إلغاء")
                        }
                    }
                )
            }
        }
    }
}

// Custom Helpers for Markdown Parsing and Beautiful Interactive Grid Tables
sealed class ContentBlock {
    data class HeaderBlock(val text: String, val level: Int) : ContentBlock()
    data class ListItemBlock(val text: String, val isOrdered: Boolean, val index: Int) : ContentBlock()
    data class TextBlock(val text: String) : ContentBlock()
    data class TableBlock(val headers: List<String>, val rows: List<List<String>>) : ContentBlock()
}

fun parseNoteContent(rawContent: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val lines = rawContent.split("\n")
    var inTable = false
    val currentTableRows = mutableListOf<List<String>>()
    var currentTableHeaders = listOf<String>()
    var orderedListIndex = 1

    for (line in lines) {
        val trimmed = line.trim()
        
        // Table Parsing
        val isTableRow = trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length > 1
        if (isTableRow) {
            inTable = true
            val rawCells = line.split("|").map { it.trim() }
            val cells = rawCells.subList(1, rawCells.size - 1)
            val isSeparator = cells.all { cell -> cell.all { it == '-' || it == ':' || it == ' ' } && cell.isNotEmpty() }
            if (isSeparator) {
                continue
            }
            if (currentTableHeaders.isEmpty()) {
                currentTableHeaders = cells
            } else {
                currentTableRows.add(cells)
            }
            continue
        } else {
            if (inTable) {
                if (currentTableRows.isNotEmpty() || currentTableHeaders.isNotEmpty()) {
                    blocks.add(ContentBlock.TableBlock(currentTableHeaders, currentTableRows.toList()))
                    currentTableRows.clear()
                    currentTableHeaders = emptyList()
                }
                inTable = false
            }
        }

        if (trimmed.isEmpty()) {
            blocks.add(ContentBlock.TextBlock(""))
            orderedListIndex = 1
            continue
        }

        // Header parsing: #, ##, ###
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6) {
                val headerText = trimmed.drop(level).trim()
                blocks.add(ContentBlock.HeaderBlock(headerText, level))
                orderedListIndex = 1
                continue
            }
        }

        // Unordered list: *, -, •
        if (trimmed.startsWith("* ") || trimmed.startsWith("- ") || trimmed.startsWith("• ")) {
            val itemText = trimmed.substring(2).trim()
            blocks.add(ContentBlock.ListItemBlock(itemText, isOrdered = false, index = 0))
            orderedListIndex = 1
            continue
        }

        // Ordered list: 1. , 2. 
        val orderedMatch = "^(\\d+)\\.\\s+(.*)$".toRegex().find(trimmed)
        if (orderedMatch != null) {
            val num = orderedMatch.groupValues[1].toIntOrNull() ?: orderedListIndex
            val itemText = orderedMatch.groupValues[2].trim()
            blocks.add(ContentBlock.ListItemBlock(itemText, isOrdered = true, index = num))
            orderedListIndex = num + 1
            continue
        }

        // Ordinary Text
        blocks.add(ContentBlock.TextBlock(line))
        orderedListIndex = 1
    }

    if (inTable) {
        if (currentTableRows.isNotEmpty() || currentTableHeaders.isNotEmpty()) {
            blocks.add(ContentBlock.TableBlock(currentTableHeaders, currentTableRows.toList()))
        }
    }

    return blocks
}

@Composable
fun renderMarkdownText(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val nextBold = text.indexOf("**", cursor)
            val nextItalicStar = text.indexOf("*", cursor)
            val nextItalicUnder = text.indexOf("_", cursor)
            
            val targets = listOf(
                Triple("**", nextBold, 2),
                Triple("*", nextItalicStar, 1),
                Triple("_", nextItalicUnder, 1)
            ).filter { it.second != -1 }.sortedBy { it.second }
            
            if (targets.isEmpty()) {
                append(text.substring(cursor))
                break
            }
            
            val (symbol, index, len) = targets.first()
            append(text.substring(cursor, index))
            cursor = index + len
            
            val closingIndex = text.indexOf(symbol, cursor)
            if (closingIndex != -1) {
                val formattedText = text.substring(cursor, closingIndex)
                val style = when (symbol) {
                    "**" -> androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    "*", "_" -> androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    else -> androidx.compose.ui.text.SpanStyle()
                }
                pushStyle(style)
                append(formattedText)
                pop()
                cursor = closingIndex + len
            } else {
                append(symbol)
            }
        }
    }
}

@Composable
fun RenderTable(
    headers: List<String>,
    rows: List<List<String>>,
    isEditable: Boolean = false,
    onCellChange: ((rowIndex: Int, colIndex: Int, newValue: String) -> Unit)? = null
) {
    if (headers.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        val scrollState = androidx.compose.foundation.rememberScrollState()
        val columnWidth = 130.dp
        
        Box(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.width(IntrinsicSize.Max)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    headers.forEachIndexed { index, header ->
                        Box(
                            modifier = Modifier
                                .width(columnWidth)
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isEditable && onCellChange != null) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = header,
                                    onValueChange = { onCellChange(-1, index, it) },
                                    textStyle = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = header,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (index < headers.size - 1) {
                            // Vertical Divider inside header
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f))
                            )
                        }
                    }
                }

                // Header-Content Divider Line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                )

                // Data Rows
                rows.forEachIndexed { rowIndex, row ->
                    val rowBg = if (rowIndex % 2 == 0) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                    }
                    
                    Row(
                        modifier = Modifier
                            .background(rowBg)
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        headers.forEachIndexed { cellIndex, _ ->
                            val cellValue = row.getOrNull(cellIndex) ?: ""
                            Box(
                                modifier = Modifier
                                    .width(columnWidth)
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isEditable && onCellChange != null) {
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = cellValue,
                                        onValueChange = { onCellChange(rowIndex, cellIndex, it) },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    Text(
                                        text = cellValue,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (cellIndex < headers.size - 1) {
                                // Vertical Grid Line Divider
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                )
                            }
                        }
                    }

                    // Horizontal Row Divider Line
                    if (rowIndex < rows.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        )
                    }
                }
            }
        }
    }
}

fun updateTableCellInMarkdown(
    rawContent: String,
    tableBlockIndex: Int,
    rowIndex: Int, // -1 for header, 0+ for rows
    colIndex: Int,
    newValue: String
): String {
    val lines = rawContent.split("\n").toMutableList()
    var currentTableIdx = -1
    var inTable = false
    val currentTableLineIndices = mutableListOf<Int>()
    
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        val isTableRow = trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length > 1
        
        if (isTableRow) {
            if (!inTable) {
                inTable = true
                currentTableIdx++
                currentTableLineIndices.clear()
            }
            currentTableLineIndices.add(i)
        } else {
            if (inTable) {
                if (currentTableIdx == tableBlockIndex) {
                    break
                }
                inTable = false
            }
        }
        i++
    }
    
    if (currentTableIdx == tableBlockIndex && currentTableLineIndices.isNotEmpty()) {
        val headerLineIdx = currentTableLineIndices[0]
        
        // Find if there is a separator line (it's usually the second line of the table)
        var separatorLineIdxInTable = -1
        for (j in 1 until currentTableLineIndices.size) {
            val globalLineIdx = currentTableLineIndices[j]
            val trimmed = lines[globalLineIdx].trim()
            val cells = trimmed.split("|").map { it.trim() }.filterIndexed { idx, _ -> idx > 0 && idx < trimmed.split("|").size - 1 }
            if (cells.all { cell -> cell.all { it == '-' || it == ':' || it == ' ' } && cell.isNotEmpty() }) {
                separatorLineIdxInTable = j
                break
            }
        }
        
        if (rowIndex == -1) {
            // Update header cell
            val rawCells = lines[headerLineIdx].split("|").toMutableList()
            if (colIndex + 1 < rawCells.size) {
                rawCells[colIndex + 1] = " $newValue "
                lines[headerLineIdx] = rawCells.joinToString("|")
            }
        } else {
            val dataRowOffset = if (separatorLineIdxInTable != -1) separatorLineIdxInTable + 1 else 1
            val targetLineIdxInTable = dataRowOffset + rowIndex
            if (targetLineIdxInTable < currentTableLineIndices.size) {
                val targetGlobalLineIdx = currentTableLineIndices[targetLineIdxInTable]
                val rawCells = lines[targetGlobalLineIdx].split("|").toMutableList()
                if (colIndex + 1 < rawCells.size) {
                    rawCells[colIndex + 1] = " $newValue "
                    lines[targetGlobalLineIdx] = rawCells.joinToString("|")
                }
            }
        }
    }
    
    return lines.joinToString("\n")
}
