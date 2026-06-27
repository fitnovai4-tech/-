package com.example.ui.screens

import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.Note
import com.example.ui.viewmodel.NoteViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteViewModel,
    onNoteClick: (Int) -> Unit,
    onAddNote: () -> Unit
) {
    val context = LocalContext.current
    val notes by viewModel.notes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var filterFavoritesOnly by remember { mutableStateOf(false) }
    
    // Theme and Unlock states
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAuthorProfile by remember { mutableStateOf(false) }
    val themeMode by viewModel.themeMode.collectAsState()
    
    var noteToUnlock by remember { mutableStateOf<Note?>(null) }
    var passwordUnlockInput by remember { mutableStateOf("") }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var passwordDeleteInput by remember { mutableStateOf("") }

    // Backup/Restore pickers
    val sqliteImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importSqliteDbBackup(it) { success ->
                if (success) {
                    Toast.makeText(context, "تم استيراد قاعدة بيانات SQLite بنجاح!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "فشل استيراد قاعدة بيانات SQLite.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val backupImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importBackup(it) { success ->
                if (success) {
                    Toast.makeText(context, "تم استرداد النسخة الاحتياطية بنجاح!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "فشل استيراد النسخة الاحتياطية.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Extract unique tags from notes
    val allTags = remember(notes) {
        notes.flatMap { note ->
            note.tags.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }.distinct()
    }

    // Filter notes by favorites if enabled
    val displayedNotes = remember(notes, filterFavoritesOnly) {
        if (filterFavoritesOnly) {
            notes.filter { it.isFavorite }
        } else {
            notes
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ملاحظاتي الذكية", fontWeight = FontWeight.Bold) },
                actions = {
                    // Favorites filter toggle
                    IconButton(onClick = { filterFavoritesOnly = !filterFavoritesOnly }) {
                        Icon(
                            imageVector = if (filterFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "عرض المفضلة فقط",
                            tint = if (filterFavoritesOnly) Color.Red else MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // More Options Dropdown Menu
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "المزيد", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("نسخة احتياطية (تصدير)") },
                                leadingIcon = { Icon(Icons.Default.Backup, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.exportBackup { file ->
                                        if (file != null) {
                                            // Share JSON File
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/json"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "تصدير نسخة احتياطية"))
                                        } else {
                                            Toast.makeText(context, "فشل تصدير النسخة الاحتياطية.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("استيراد نسخة احتياطية") },
                                leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    backupImporter.launch("application/json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("نسخة احتياطية SQLite (تصدير)") },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.exportSqliteDbBackup { file ->
                                        if (file != null) {
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/octet-stream"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "تصدير قاعدة بيانات SQLite"))
                                        } else {
                                            Toast.makeText(context, "فشل تصدير قاعدة البيانات.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("استيراد قاعدة بيانات SQLite") },
                                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    sqliteImporter.launch("*/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("مظهر التطبيق (ثيم)") },
                                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showThemeDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            Surface(
                onClick = onAddNote,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "إضافة ملاحظة جديدة", modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { paddingValues ->
        val isDark = when (themeMode) {
            "DARK" -> true
            "LIGHT" -> false
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }

        val gradientColors = if (isDark) {
            listOf(
                Color(0xFF121316), // Dark slate
                Color(0xFF1B1B1F), // Dark background
                Color(0xFF1F1F24)  // Slightly lighter deep gray
            )
        } else {
            listOf(
                Color(0xFFE3F2FD), // Subtle soft neon blue
                Color(0xFFBBDEFB), // Delicate ice blue
                Color(0xFFECEFF1)  // Smooth metallic sky-grey
            )
        }

        val glassyBlueGradient = androidx.compose.ui.graphics.Brush.linearGradient(colors = gradientColors)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(glassyBlueGradient)
                .padding(paddingValues)
        ) {
            // Sleek Search Bar with "YA" User Avatar matching the Professional Polish HTML Theme
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(50.dp)
                    .background(if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.5f), CircleShape)
                    .border(1.dp, if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "بحث",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "البحث في ملاحظاتك، وسومك، ومحتواك...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.updateSearchQuery("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                // User Avatar replaced with developer image
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .clip(CircleShape)
                        .clickable { showAuthorProfile = true },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_icon_1782566179737),
                        contentDescription = "نبذة تعريفية",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }

            // Tags horizontally scrollable list
            if (allTags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTag == null,
                            onClick = { viewModel.selectTag(null) },
                            label = { Text("الكل") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.45f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedTag == null,
                                borderColor = if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.4f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                    items(allTags) { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = {
                                if (selectedTag == tag) {
                                    viewModel.selectTag(null)
                                } else {
                                    viewModel.selectTag(tag)
                                }
                            },
                            label = { Text(tag) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.45f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedTag == tag,
                                borderColor = if (isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.4f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            // Note List
            if (displayedNotes.isEmpty()) {
                // Beautiful empty state placeholder
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "لا توجد ملاحظات بعد",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ابدأ بكتابة أفكارك وملاحظاتك الذكية بالضغط على زر الإضافة (+)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = {
                                if (note.isLocked) {
                                    noteToUnlock = note
                                    // Try biometric immediately
                                    showBiometricPrompt(
                                        context = context,
                                        onSuccess = {
                                            val noteId = noteToUnlock?.id ?: note.id
                                            noteToUnlock = null
                                            onNoteClick(noteId)
                                        },
                                        onFailure = { error ->
                                            // Fallback to password dialog
                                        }
                                    )
                                } else {
                                    onNoteClick(note.id)
                                }
                            },
                            onDelete = { noteToDelete = note },
                            onToggleFavorite = { viewModel.toggleFavorite(note) },
                            onSharePdf = {
                                viewModel.exportToPdf(note) { file ->
                                    if (file != null) {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "تصدير الملاحظة بصيغة PDF"))
                                    } else {
                                        Toast.makeText(context, "فشل تصدير ملف PDF.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Elegant footer signature matching the Professional Polish brand
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, top = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MOHAMMAD.H.J",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAuthorProfile = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Theme selection Dialog
            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text("اختر مظهر التطبيق", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setThemeMode("LIGHT")
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = themeMode == "LIGHT", onClick = {
                                    viewModel.setThemeMode("LIGHT")
                                    showThemeDialog = false
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("الوضع الفاتح (Light Mode)")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setThemeMode("DARK")
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = themeMode == "DARK", onClick = {
                                    viewModel.setThemeMode("DARK")
                                    showThemeDialog = false
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("الوضع الداكن (Dark Mode)")
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setThemeMode("SYSTEM")
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = themeMode == "SYSTEM", onClick = {
                                    viewModel.setThemeMode("SYSTEM")
                                    showThemeDialog = false
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تلقائي حسب النظام (System)")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showThemeDialog = false }) {
                            Text("إغلاق")
                        }
                    }
                )
            }

            // Unlock note dialog
            if (noteToUnlock != null) {
                val currentUnlockNote = noteToUnlock!!
                LaunchedEffect(currentUnlockNote) {
                    showBiometricPrompt(
                        context = context,
                        onSuccess = {
                            val noteId = currentUnlockNote.id
                            noteToUnlock = null
                            passwordUnlockInput = ""
                            onNoteClick(noteId)
                        },
                        onFailure = { error ->
                            // Biometric failed or not set up, user will use password field
                        }
                    )
                }

                AlertDialog(
                    onDismissRequest = { 
                        noteToUnlock = null 
                        passwordUnlockInput = ""
                    },
                    title = { Text("الملاحظة مقفلة بأمان", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "الرجاء إدخال كلمة المرور الخاصة بالملاحظة لفتحها:",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            OutlinedTextField(
                                value = passwordUnlockInput,
                                onValueChange = { passwordUnlockInput = it },
                                label = { Text("كلمة المرور") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    showBiometricPrompt(
                                        context = context,
                                        onSuccess = {
                                            val noteId = noteToUnlock?.id
                                            noteToUnlock = null
                                            passwordUnlockInput = ""
                                            if (noteId != null) {
                                                onNoteClick(noteId)
                                            }
                                        },
                                        onFailure = { error ->
                                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                                    Text("فتح باستخدام البصمة")
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (passwordUnlockInput == noteToUnlock?.notePassword) {
                                    val noteId = noteToUnlock?.id
                                    noteToUnlock = null
                                    passwordUnlockInput = ""
                                    if (noteId != null) {
                                        onNoteClick(noteId)
                                    }
                                } else {
                                    Toast.makeText(context, "كلمة المرور خاطئة!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("تأكيد")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { 
                            noteToUnlock = null 
                            passwordUnlockInput = ""
                        }) {
                            Text("إلغاء")
                        }
                    }
                )
            }

            // Delete confirmation dialog
            if (noteToDelete != null) {
                val note = noteToDelete!!
                if (note.isLocked) {
                    AlertDialog(
                        onDismissRequest = { 
                            noteToDelete = null 
                            passwordDeleteInput = ""
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        title = {
                            Text(
                                text = "حذف ملاحظة مقفلة بأمان",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "الملاحظة \"${note.title}\" مقفلة. يرجى التحقق من هويتك بكلمة المرور أو البصمة لتأكيد الحذف:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedTextField(
                                    value = passwordDeleteInput,
                                    onValueChange = { passwordDeleteInput = it },
                                    label = { Text("كلمة مرور الملاحظة") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                                )
                                Button(
                                    onClick = {
                                        showBiometricPrompt(
                                            context = context,
                                            onSuccess = {
                                                viewModel.deleteNote(note)
                                                Toast.makeText(context, "تم حذف الملاحظة بنجاح", Toast.LENGTH_SHORT).show()
                                                noteToDelete = null
                                                passwordDeleteInput = ""
                                            },
                                            onFailure = { error ->
                                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                                        Text("التحقق بالبصمة للحذف")
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (passwordDeleteInput == note.notePassword) {
                                        viewModel.deleteNote(note)
                                        Toast.makeText(context, "تم حذف الملاحظة بنجاح", Toast.LENGTH_SHORT).show()
                                        noteToDelete = null
                                        passwordDeleteInput = ""
                                    } else {
                                        Toast.makeText(context, "كلمة المرور خاطئة!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("تأكيد الحذف")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { 
                                    noteToDelete = null 
                                    passwordDeleteInput = ""
                                }
                            ) {
                                Text("إلغاء")
                            }
                        }
                    )
                } else {
                    AlertDialog(
                        onDismissRequest = { noteToDelete = null },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        title = {
                            Text(
                                text = "تأكيد الحذف",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        text = {
                            Text(
                                text = "هل أنت متأكد من رغبتك في حذف الملاحظة \"${note.title}\"؟ لا يمكن التراجع عن هذا الإجراء.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.deleteNote(note)
                                    Toast.makeText(context, "تم حذف الملاحظة بنجاح", Toast.LENGTH_SHORT).show()
                                    noteToDelete = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("حذف")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { noteToDelete = null }
                            ) {
                                Text("إلغاء")
                            }
                        }
                    )
                }
            }

            if (showAuthorProfile) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { showAuthorProfile = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable { showAuthorProfile = false },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        shape = CircleShape
                                    )
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_app_icon_1782566179737),
                                    contentDescription = "MOHAMMAD.H.J",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }

                            Text(
                                text = "بطاقة تعريفية عن المطور",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Brush,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "تصميم",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = "MOHAMMAD.H.J",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "العنوان",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = "سوريا دمشق",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "رقم الهاتف",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Text(
                                            text = "00963988972557",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "إهداء ورسالة حب",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "هذا البرنامج هدية مني للشعوب العربية والمسلمة في أنحاء الأرض لتسهيل أمورهم وحفظ ومشاركة الملاحظات من الهاتف \"لا تنسونا من دعائكم\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "اضغط هنا أو في أي مكان على البطاقة للإغلاق",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

fun showBiometricPrompt(
    context: android.content.Context,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val activity = context.findActivity()
            val executor = activity?.mainExecutor ?: context.mainExecutor
            val biometricPrompt = BiometricPrompt.Builder(activity ?: context)
                .setTitle("التحقق الأمني")
                .setSubtitle("استخدم البصمة لتأكيد الهوية")
                .setNegativeButton("إلغاء", executor) { _, _ -> }
                .build()

            biometricPrompt.authenticate(
                CancellationSignal(),
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        super.onAuthenticationError(errorCode, errString)
                        onFailure(errString?.toString() ?: "فشل المصادقة")
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onFailure("لم يتم التعرف على البصمة")
                    }
                }
            )
        } else {
            onFailure("إصدار الأندرويد لا يدعم البصمة")
        }
    } catch (e: Exception) {
        onFailure("الجهاز لا يدعم البصمة أو لم يتم إعدادها")
    }
}

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSharePdf: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(note.updatedAt))

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    
    // Highlighting condition based on PDF attachment or Favorite status (matches the Premium HTML theme)
    val isHighlighted = note.isFavorite || note.pdfPath != null
    val containerColor = if (isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    }
    
    val contentColor = if (isHighlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val subtitleColor = if (isHighlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val borderColor = if (isHighlighted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title.ifEmpty { "بدون عنوان" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = contentColor
                )

                // Favorite and Share Actions
                Row {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (note.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "تفضيل",
                            tint = if (note.isFavorite) Color.Red else contentColor
                        )
                    }
                    IconButton(onClick = onSharePdf) {
                        Icon(
                            imageVector = Icons.Outlined.PictureAsPdf,
                            contentDescription = "مشاركة PDF",
                            tint = if (isHighlighted) contentColor else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف الملاحظة",
                            tint = if (isHighlighted) contentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Body text preview snippet
            if (note.isLocked) {
                Text(
                    text = "🔒 هذه الملاحظة محمية بقفل أمني. انقر لفك القفل ورؤية المحتوى.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                if (note.content.isNotEmpty()) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = subtitleColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // PDF visual container preview (matches the premium HTML layout style)
                if (note.pdfPath != null) {
                    val file = File(note.pdfPath)
                    val pdfName = file.name.uppercase()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isHighlighted) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (isHighlighted) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PictureAsPdf,
                                contentDescription = null,
                                size = 28.dp,
                                tint = if (isHighlighted) contentColor else Color.Red
                            )
                            Column {
                                Text(
                                    text = pdfName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "مرفق مستند للتعديل والرسم",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subtitleColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Media attachment badges & timestamps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note.isLocked) {
                        Icon(Icons.Default.Lock, contentDescription = "مغلق", size = 16.dp, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        if (note.imagePath != null) {
                            Icon(Icons.Outlined.Image, contentDescription = "يحتوي على صورة", size = 16.dp, tint = subtitleColor)
                        }
                        if (note.drawingPath != null) {
                            Icon(Icons.Default.Gesture, contentDescription = "يحتوي على رسم", size = 16.dp, tint = subtitleColor)
                        }
                        if (note.voicePath != null) {
                            Icon(Icons.Outlined.Mic, contentDescription = "يحتوي على تسجيل صوتي", size = 16.dp, tint = subtitleColor)
                        }
                        if (note.reminderTime != null) {
                            Icon(
                                imageVector = Icons.Outlined.Alarm,
                                contentDescription = "منبه نشط",
                                size = 16.dp,
                                tint = if (isHighlighted) contentColor else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor.copy(alpha = 0.7f)
                )
            }

            // Tags display with elegant capsules
            if (note.tags.isNotEmpty() && !note.isLocked) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    note.tags.split(",").forEach { tag ->
                        if (tag.trim().isNotEmpty()) {
                            val tagBg = if (isHighlighted) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            val tagText = if (isHighlighted) contentColor else MaterialTheme.colorScheme.primary
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .background(tagBg, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "#${tag.trim()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tagText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// Icon wrapper to accept Size
@Composable
fun Icon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    size: androidx.compose.ui.unit.Dp,
    tint: Color = LocalContentColor.current
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
        tint = tint
    )
}
