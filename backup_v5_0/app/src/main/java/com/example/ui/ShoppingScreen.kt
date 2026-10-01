package com.example.ui

import com.example.LanguageSelector
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ShoppingCategory
import com.example.data.ShoppingItem
import com.example.data.Checklist
import com.example.data.UserRole
import com.example.data.Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import android.speech.tts.TextToSpeech

data class CategoryDisplayBlock(
    val category: ShoppingCategory,
    val items: List<ShoppingItem>
)

data class ShoppingListDisplayState(
    val categories: List<CategoryDisplayBlock>,
    val uncategorizedItems: List<ShoppingItem>
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: TaskViewModel, onNavigateToStore: () -> Unit) {
    var activeChecklistId by rememberSaveable { mutableStateOf<Int?>(null) }
    var isDetailActive by rememberSaveable { mutableStateOf(false) }
    var detailTitle by rememberSaveable { mutableStateOf("") }
    
    val checklists by viewModel.allChecklists.collectAsStateWithLifecycle()
    val currentLang by viewModel.language.collectAsStateWithLifecycle()
    val isHe = currentLang == "HE"

    if (isDetailActive) {
        ChecklistDetailsScreen(
            viewModel = viewModel,
            checklistId = activeChecklistId,
            listTitle = if (activeChecklistId == null) {
                if (isHe) "רשימת קניות" else "Shopping List"
            } else {
                checklists.find { it.id == activeChecklistId }?.name ?: detailTitle
            },
            onBack = { isDetailActive = false }
        )
    } else {
        ChecklistsSelectionScreen(
            viewModel = viewModel,
            onSelect = { checklistId, title ->
                activeChecklistId = checklistId
                detailTitle = title
                isDetailActive = true
            },
            onNavigateToStore = onNavigateToStore
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistsSelectionScreen(
    viewModel: TaskViewModel,
    onSelect: (checklistId: Int?, title: String) -> Unit,
    onNavigateToStore: () -> Unit
) {
    val checklists by viewModel.allChecklists.collectAsStateWithLifecycle()
    val items by viewModel.allShoppingItems.collectAsStateWithLifecycle()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val chores by viewModel.allChores.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentLang by viewModel.language.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val isChild = currentUser?.role == UserRole.CHILD
    val isHe = viewModel.language.value == "HE"

    var showCreateDialog by remember { mutableStateOf(false) }
    var checklistToDelete by remember { mutableStateOf<Checklist?>(null) }
    var checklistToRename by remember { mutableStateOf<Checklist?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredChecklists = remember(checklists, searchQuery, currentUser) {
        val visible = checklists.filter {
            !it.isPrivate || it.creatorId == currentUser?.id
        }
        if (searchQuery.isBlank()) {
            visible
        } else {
            visible.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = Localization.get("checklists", currentLang),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToStore,
                        modifier = Modifier.testTag("shopping_store_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = if (isHe) "חנות וירטואלית" else "Virtual Store",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.selectUser(null) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwitchAccount,
                            contentDescription = if (isHe) "החלף פרופיל" else "Switch Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    LanguageSelector(viewModel)
                }
            )
        },
        floatingActionButton = {
            if (!isChild || currentUserRole?.allowCreateChecklists == true) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.testTag("create_checklist_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = Localization.get("new_checklist", currentLang))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (isHe) "חיפוש רשימת סימון..." else "Search checklists...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Default MAIN shopping list
                if ("רשימת קניות".contains(searchQuery, ignoreCase = true) || "shopping list".contains(searchQuery, ignoreCase = true) || searchQuery.isBlank()) {
                    item {
                        val mainItems = remember(items) { items.filter { it.checklistId == null } }
                        val mainCompleted = remember(mainItems) { mainItems.count { it.isCompleted } }
                        val mainTotal = mainItems.size
                        
                        Card(
                            onClick = { onSelect(null, if (isHe) "רשימת קניות" else "Shopping List") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("main_shopping_list_card")
                                .shadow(2.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isHe) "רשימת קניות" else "Shopping List",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (isHe) "רשימת הקניות הראשית של המשפחה" else "The family's main shopping list",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "$mainCompleted/$mainTotal",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                    IconButton(
                                        onClick = {
                                            val link = viewModel.generateShareLink(null, if (isHe) "רשימת קניות" else "Shopping List")
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(link))
                                            android.widget.Toast.makeText(
                                                context,
                                                if (isHe) "קישור השיתוף הועתק לרשימת הקניות הראשית!" else "Family main shopping list share link copied!",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.size(36.dp).testTag("share_main_list_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Render all additional checklists
                if (filteredChecklists.isEmpty() && searchQuery.isNotBlank()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isHe) "לא נמצאו תוצאות לחיפוש" else "No matching checklists found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                } else if (filteredChecklists.isEmpty() && searchQuery.isBlank() && !("רשימת קניות".contains(searchQuery, ignoreCase = true) || searchQuery.isBlank())) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = Localization.get("empty_checklists_subtitle", currentLang),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    items(filteredChecklists) { checklist ->
                        val listItems = remember(items, checklist.id) {
                            items.filter { it.checklistId == checklist.id }
                        }
                        val completedCount = remember(listItems) { listItems.count { it.isCompleted } }
                        val totalCount = listItems.size
                        
                        val linkedTask = remember(tasks, chores, checklist.taskId) {
                            (tasks + chores).find { it.id == checklist.taskId }
                        }

                        val assigneeUser = remember(allUsers, linkedTask) {
                            linkedTask?.assignedToUserId?.let { userId -> allUsers.find { it.id == userId } }
                        }

                        Card(
                            onClick = { onSelect(checklist.id, checklist.name) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("checklist_card_${checklist.id}")
                                .shadow(1.dp, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckBox,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = checklist.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (checklist.isPrivate) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Private",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    if (linkedTask != null) {
                                        Spacer(Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Link, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (isHe) "מקושר ל: ${linkedTask.title}" else "Linked to: ${linkedTask.title}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        assigneeUser?.let { user ->
                                            Spacer(Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = if (isHe) "משויך ל: ${user.name}" else "Assigned to: ${user.name}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "$completedCount/$totalCount",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    val context = androidx.compose.ui.platform.LocalContext.current
                                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                    IconButton(
                                        onClick = {
                                            val link = viewModel.generateShareLink(checklist.id, checklist.name)
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(link))
                                            android.widget.Toast.makeText(
                                                context,
                                                if (isHe) "קישור שיתוף עבור '${checklist.name}' הועתק!" else "Share link for '${checklist.name}' copied!",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.size(36.dp).testTag("share_checklist_button_${checklist.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    val canEditThisList = !isChild || currentUserRole?.allowCreateChecklists == true
                                    if (canEditThisList) {
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(onClick = { checklistToRename = checklist }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (!isChild) {
                                        Spacer(Modifier.width(4.dp))
                                        IconButton(onClick = { checklistToDelete = checklist }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // New Checklist Dialog
    if (showCreateDialog) {
        var newListName by remember { mutableStateOf("") }
        var isPrivate by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (isHe) "יצירת רשימה חדשה" else "Create New Checklist") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = newListName,
                        onValueChange = { newListName = it },
                        placeholder = { Text(if (isHe) "הזן שם לרשימה..." else "Enter list name...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_checklist_name_input")
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (isHe) "נגישות הרשימה:" else "List Accessibility:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Family Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPrivate = false }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isPrivate,
                            onClick = { isPrivate = false }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isHe) "רשימה לכל המשפחה" else "Family Playlist/Checklist",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isHe) "נגישה לכל בני המשפחה" else "Accessible to all family members",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Private Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPrivate = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isPrivate,
                            onClick = { isPrivate = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isHe) "רשימה פרטית" else "Private Checklist",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isHe) "נגישה רק ליוצר הרשימה" else "Accessible only to the creator",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newListName.isNotBlank()) {
                            viewModel.addChecklist(
                                name = newListName.trim(),
                                isPrivate = isPrivate,
                                creatorId = currentUser?.id
                            )
                            showCreateDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_checklist_button")
                ) {
                    Text(if (isHe) "צור" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(if (isHe) "ביטול" else "Cancel")
                }
            }
        )
    }

    // Rename Checklist Dialog
    checklistToRename?.let { checklist ->
        var listName by remember { mutableStateOf(checklist.name) }
        AlertDialog(
            onDismissRequest = { checklistToRename = null },
            title = { Text(if (isHe) "עריכת שם הרשימה" else "Edit Checklist Name") },
            text = {
                TextField(
                    value = listName,
                    onValueChange = { listName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_checklist_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (listName.isNotBlank()) {
                            viewModel.updateChecklist(checklist.copy(name = listName.trim()))
                            checklistToRename = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_rename_checklist_button")
                ) {
                    Text(if (isHe) "שמור" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { checklistToRename = null }) {
                    Text(if (isHe) "ביטול" else "Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    checklistToDelete?.let { checklist ->
        AlertDialog(
            onDismissRequest = { checklistToDelete = null },
            title = { Text(if (isHe) "מחיקת רשימת סימון?" else "Delete Checklist?") },
            text = { Text(if (isHe) "האם אתה בטוח שברצונך למחוק את הרשימה '${checklist.name}'? פעולה זו תמחק גם את כל הפריטים והקטגוריות שבתוכה." else "Are you sure you want to delete '${checklist.name}'? This will also delete all categories and items inside.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChecklist(checklist)
                        checklistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_checklist_button")
                ) {
                    Text(if (isHe) "מחק" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { checklistToDelete = null }) {
                    Text(if (isHe) "ביטול" else "Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChecklistDetailsScreen(
    viewModel: TaskViewModel,
    checklistId: Int?,
    listTitle: String,
    taskIdToLink: Int? = null,
    onBack: () -> Unit
) {
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    val items by viewModel.allShoppingItems.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentLang by viewModel.language.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val isChild = currentUser?.role == UserRole.CHILD
    val isHe = currentLang == "HE"

    var searchQuery by remember { mutableStateOf("") }

    // Dialog flags
    var showAiAssistantSheet by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ShoppingItem?>(null) }
    var editingCategory by remember { mutableStateOf<ShoppingCategory?>(null) }
    var preselectedCategoryIdForNewProduct by remember { mutableStateOf<Int?>(null) }

    // Drag-and-drop state
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var draggedItemId by remember { mutableStateOf<Int?>(null) }
    var dragViewportTouchY by remember { mutableStateOf<Float?>(null) }
    var dragDeltaY by remember { mutableStateOf<Float?>(null) }
    var relativeTouchOffsetInItemY by remember { mutableStateOf(0f) }
    var hoveredTargetKey by remember { mutableStateOf<String?>(null) }

    // Filter categories & items belonging to THIS checklistId
    val filteredCategoriesRaw = remember(categories, checklistId) {
        categories.filter { it.checklistId == checklistId }
    }
    val filteredItemsRaw = remember(items, checklistId) {
        items.filter { it.checklistId == checklistId }
    }

    // Compute sorted display blocks reactively with remembered performance
    val displayState = remember(filteredCategoriesRaw, filteredItemsRaw, searchQuery) {
        val filteredItems = if (searchQuery.isBlank()) {
            filteredItemsRaw
        } else {
            filteredItemsRaw.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        val grouped = filteredItems.groupBy { it.categoryId }

        // Categories: active first, then completed. Sorted by position, then name.
        val sortedCategories = filteredCategoriesRaw.sortedWith(
            compareBy<ShoppingCategory> { it.isCompleted }
                .thenBy { it.position }
                .thenBy { it.name }
        )

        // Deduplicate categories with the same visual name (case-insensitive and trimmed)
        val uniqueSortedCategories = sortedCategories.distinctBy { it.name.trim().lowercase() }

        val catBlocks = uniqueSortedCategories.map { category ->
            val matchingCategories = filteredCategoriesRaw.filter { it.name.trim().equals(category.name.trim(), ignoreCase = true) }
            val matchingIds = matchingCategories.map { it.id }
            
            val catItems = matchingIds.flatMap { grouped[it] ?: emptyList() }
            
            val sortedCatItems = catItems.sortedWith(
                compareBy<ShoppingItem> { it.isCompleted }
                    .thenBy { it.position }
                    .thenBy { it.name }
            )
            CategoryDisplayBlock(category, sortedCatItems)
        }.filter { block ->
            if (searchQuery.isBlank()) {
                true
            } else {
                block.items.isNotEmpty()
            }
        }

        val uncategorized = grouped[null] ?: emptyList()
        val sortedUncategorized = uncategorized.sortedWith(
            compareBy<ShoppingItem> { it.isCompleted }
                .thenBy { it.position }
                .thenBy { it.name }
        )

        ShoppingListDisplayState(catBlocks, sortedUncategorized)
    }

    val currentDragY = rememberUpdatedState(dragViewportTouchY)

    // Auto scroll when dragging near edges
    LaunchedEffect(draggedItemId != null) {
        if (draggedItemId != null) {
            while (true) {
                val layoutInfo = lazyListState.layoutInfo
                val listHeight = layoutInfo.viewportSize.height
                val currentY = currentDragY.value ?: break
                
                if (currentY < listHeight * 0.15f) {
                    val scrollAmount = -20f
                    lazyListState.scrollBy(scrollAmount)
                } else if (currentY > listHeight * 0.85f) {
                    val scrollAmount = 20f
                    lazyListState.scrollBy(scrollAmount)
                }
                delay(16)
            }
        }
    }

    // Drag target tracking tick
    LaunchedEffect(draggedItemId != null) {
        if (draggedItemId != null) {
            while (true) {
                val currentY = currentDragY.value
                if (currentY != null) {
                    val layoutInfo = lazyListState.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    
                    val targetItem = visibleItems.find { itemInfo ->
                        val top = itemInfo.offset.toFloat()
                        val bottom = (itemInfo.offset + itemInfo.size).toFloat()
                        currentY >= top && currentY <= bottom
                    }
                    
                    hoveredTargetKey = targetItem?.key?.toString()
                } else {
                    hoveredTargetKey = null
                }
                delay(100)
            }
        } else {
            hoveredTargetKey = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = listTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (taskIdToLink != null) {
                            val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
                            val chores by viewModel.allChores.collectAsStateWithLifecycle()
                            val linkedTask = remember(allTasks, chores, taskIdToLink) {
                                (allTasks + chores).find { it.id == taskIdToLink }
                            }
                            val assigneeUser = remember(allUsers, linkedTask) {
                                linkedTask?.assignedToUserId?.let { userId -> allUsers.find { it.id == userId } }
                            }
                            linkedTask?.let {
                                Text(
                                    text = if (isHe) "מקושר ל: ${it.title}" else "Linked to: ${it.title}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                assigneeUser?.let { user ->
                                    Text(
                                        text = if (isHe) "משויך ל: ${user.name}" else "Assigned to: ${user.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = if (isHe) "חזרה" else "Back"
                        )
                    }
                },
                actions = {
                    if (taskIdToLink != null) {
                        val isManager = currentUserRole?.isManager == true || currentUserRole?.allowCreateTasks == true || currentUserRole?.allowCreateChecklists == true
                        if (isManager) {
                            IconButton(
                                onClick = {
                                    val allTasks = viewModel.allTasks.value
                                    val chores = viewModel.allChores.value
                                    val taskToUnlink = (allTasks + chores).find { it.id == taskIdToLink }
                                    taskToUnlink?.let {
                                        viewModel.unlinkTaskFromCheckboxList(it)
                                    }
                                    onBack()
                                },
                                modifier = Modifier.testTag("unlink_from_task_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LinkOff,
                                    contentDescription = if (isHe) "נתק רשימה" else "Unlink Checklist",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    IconButton(
                        onClick = {
                            val link = viewModel.generateShareLink(checklistId, listTitle)
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(link))
                            android.widget.Toast.makeText(
                                context,
                                if (isHe) "קישור השיתוף עבור '$listTitle' הועתק!" else "Share link for '$listTitle' copied!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.testTag("share_checklist_detail_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Checklist",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showAiAssistantSheet = true },
                        modifier = Modifier.testTag("open_shopping_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = if (isHe) "עוזר קניות קולי AI" else "AI Voice Assistant",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.selectUser(null) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwitchAccount,
                            contentDescription = if (isHe) "החלף פרופיל" else "Switch Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    LanguageSelector(viewModel)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("shopping_screen")
        ) {
            // Search Bar With Magnifying Glass
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(Localization.get("search_placeholder", currentLang)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shopping_search")
                    .clip(RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Main Contents (LazyColumn supporting Drag-and-Drop + Swipe)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (displayState.categories.isEmpty() && displayState.uncategorizedItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = "Inbox empty",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (isHe) "רשימת הסימון ריקה" else "The checklist is empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isHe) "צרו פריט חדש או קטגוריה כדי להתחיל" else "Create a new item or category to get started",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Uncategorized Items header
                        if (displayState.uncategorizedItems.isNotEmpty()) {
                            item(key = "header_uncategorized") {
                                DropTargetZone(
                                    isActive = hoveredTargetKey == "header_uncategorized",
                                    label = Localization.get("uncategorized", currentLang)
                                )
                            }
                            
                            items(
                                items = displayState.uncategorizedItems,
                                key = { "item_${it.id}" }
                            ) { item ->
                                val isDragged = draggedItemId == item.id
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isDragged) 10f else 1f)
                                        .pointerInput(item.id) {
                                            if (!isChild) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { offset ->
                                                        draggedItemId = item.id
                                                        relativeTouchOffsetInItemY = offset.y
                                                        val initialItemInfo = lazyListState.layoutInfo.visibleItemsInfo.find { it.key == "item_${item.id}" }
                                                        if (initialItemInfo != null) {
                                                            dragViewportTouchY = (initialItemInfo.offset + offset.y)
                                                        }
                                                        dragDeltaY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragDeltaY = (dragDeltaY ?: 0f) + dragAmount.y
                                                        dragViewportTouchY = (dragViewportTouchY ?: 0f) + dragAmount.y
                                                    },
                                                    onDragEnd = {
                                                        handleDrop(
                                                            itemId = item.id,
                                                            targetKey = hoveredTargetKey,
                                                            viewModel = viewModel,
                                                            categoriesList = filteredCategoriesRaw,
                                                            itemsList = filteredItemsRaw
                                                        )
                                                        draggedItemId = null
                                                        dragViewportTouchY = null
                                                        dragDeltaY = null
                                                    },
                                                    onDragCancel = {
                                                        draggedItemId = null
                                                        dragViewportTouchY = null
                                                        dragDeltaY = null
                                                    }
                                                )
                                            }
                                        }
                                        .offset {
                                            if (isDragged) {
                                                IntOffset(0, (dragDeltaY ?: 0f).roundToInt())
                                            } else {
                                                IntOffset(0, 0)
                                            }
                                        }
                                ) {
                                    ShoppingItemCard(
                                        item = item,
                                        isDragged = isDragged,
                                        isChild = isChild && currentUserRole?.allowCreateChecklists != true,
                                        onToggle = { viewModel.toggleItemCompletion(item, displayState.uncategorizedItems, null) },
                                        onEdit = { editingProduct = item }
                                    )
                                }
                            }
                        }

                        // 2. Categories with nested items
                        displayState.categories.forEach { categoryBlock ->
                            val category = categoryBlock.category
                            val catItems = categoryBlock.items
                            
                            item(key = "category_${category.id}") {
                                DropTargetZone(
                                    isActive = hoveredTargetKey == "category_${category.id}",
                                    label = category.name,
                                    onTitleClick = { viewModel.updateCategory(category.copy(isExpanded = !category.isExpanded)) },
                                    isExpanded = category.isExpanded,
                                    isCompleted = category.isCompleted,
                                    onToggleCheck = { viewModel.toggleCategoryCompletion(category, catItems) },
                                    isChild = isChild && currentUserRole?.allowCreateChecklists != true,
                                    onEdit = { editingCategory = category },
                                    onAddProductInside = {
                                        preselectedCategoryIdForNewProduct = category.id
                                        showAddProductDialog = true
                                    }
                                )
                            }

                            if (category.isExpanded) {
                                items(
                                    items = catItems,
                                    key = { "item_${it.id}" }
                                ) { item ->
                                    val isDragged = draggedItemId == item.id
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .zIndex(if (isDragged) 10f else 1f)
                                            .pointerInput(item.id) {
                                                if (!isChild) {
                                                    detectDragGesturesAfterLongPress(
                                                        onDragStart = { offset ->
                                                            draggedItemId = item.id
                                                            relativeTouchOffsetInItemY = offset.y
                                                            val initialItemInfo = lazyListState.layoutInfo.visibleItemsInfo.find { it.key == "item_${item.id}" }
                                                            if (initialItemInfo != null) {
                                                                dragViewportTouchY = (initialItemInfo.offset + offset.y)
                                                            }
                                                            dragDeltaY = 0f
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragDeltaY = (dragDeltaY ?: 0f) + dragAmount.y
                                                            dragViewportTouchY = (dragViewportTouchY ?: 0f) + dragAmount.y
                                                        },
                                                        onDragEnd = {
                                                            handleDrop(
                                                                itemId = item.id,
                                                                targetKey = hoveredTargetKey,
                                                                viewModel = viewModel,
                                                                categoriesList = filteredCategoriesRaw,
                                                                itemsList = filteredItemsRaw
                                                            )
                                                            draggedItemId = null
                                                            dragViewportTouchY = null
                                                            dragDeltaY = null
                                                        },
                                                        onDragCancel = {
                                                            draggedItemId = null
                                                            dragViewportTouchY = null
                                                            dragDeltaY = null
                                                        }
                                                    )
                                                }
                                            }
                                            .offset {
                                                if (isDragged) {
                                                    IntOffset(0, (dragDeltaY ?: 0f).roundToInt())
                                                } else {
                                                    IntOffset(0, 0)
                                                }
                                            }
                                    ) {
                                        ShoppingItemCard(
                                            item = item,
                                            isDragged = isDragged,
                                            isChild = isChild && currentUserRole?.allowCreateChecklists != true,
                                            onToggle = { viewModel.toggleItemCompletion(item, catItems, category) },
                                            onEdit = { editingProduct = item }
                                        )
                                    }
                                }
                            }
                        }

                        // Last Spacer drop zone
                        item(key = "bottom_spacer_drop_zone") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(
                                        if (hoveredTargetKey == "bottom_spacer_drop_zone") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hoveredTargetKey == "bottom_spacer_drop_zone") {
                                    Text(
                                        text = if (isHe) "שחרר כאן כדי להעביר ללא קטגוריה" else "Release here to uncategorize",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        item {
                            Spacer(Modifier.height(100.dp))
                        }
                    }
                }

                // Add Dialog FAB Buttons for Adults
                if (!isChild || currentUserRole?.allowCreateChecklists == true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Category Add FAB
                            SmallFloatingActionButton(
                                onClick = { showAddCategoryDialog = true },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.testTag("add_category_fab")
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = Localization.get("add_category", currentLang))
                            }

                            // Product Add FAB
                            FloatingActionButton(
                                onClick = { 
                                    preselectedCategoryIdForNewProduct = null
                                    showAddProductDialog = true 
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.testTag("add_product_fab")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = Localization.get("add_product", currentLang))
                            }
                        }
                    }
                }
            }
        }

        // Add Dialogs
        if (showAddCategoryDialog) {
            AddEditCategoryDialog(
                currentLang = currentLang,
                onDismiss = { showAddCategoryDialog = false },
                onSave = { name ->
                    viewModel.addCategory(name, checklistId)
                    showAddCategoryDialog = false
                }
            )
        }

        if (showAddProductDialog) {
            AddEditProductDialog(
                currentLang = currentLang,
                categories = filteredCategoriesRaw,
                preselectedCategoryId = preselectedCategoryIdForNewProduct,
                onDismiss = { showAddProductDialog = false },
                onSave = { name, catId ->
                    viewModel.addShoppingItem(name = name, categoryId = catId, checklistId = checklistId)
                    showAddProductDialog = false
                }
            )
        }

        editingProduct?.let { item ->
            AddEditProductDialog(
                currentLang = currentLang,
                categories = filteredCategoriesRaw,
                itemToEdit = item,
                onDismiss = { editingProduct = null },
                onSave = { name, catId ->
                    viewModel.updateShoppingItem(item.copy(name = name, categoryId = catId))
                    editingProduct = null
                },
                onDelete = if (isChild) null else { {
                    viewModel.deleteShoppingItem(item)
                    editingProduct = null
                } }
            )
        }

        editingCategory?.let { category ->
            AddEditCategoryDialog(
                currentLang = currentLang,
                categoryToEdit = category,
                onDismiss = { editingCategory = null },
                onSave = { name ->
                    viewModel.updateCategory(category.copy(name = name))
                    editingCategory = null
                },
                onDelete = if (isChild) null else { {
                    val catItems = filteredItemsRaw.filter { it.categoryId == category.id }
                    viewModel.deleteCategory(category)
                    catItems.forEach { item ->
                        viewModel.deleteShoppingItem(item)
                    }
                    editingCategory = null
                } }
            )
        }

        if (showAiAssistantSheet) {
            AIShoppingAssistantSheet(
                currentLang = currentLang,
                checklistId = checklistId,
                uncheckedItems = filteredItemsRaw.filter { !it.isCompleted },
                viewModel = viewModel,
                onDismiss = { showAiAssistantSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistTaskDetailsOverlay(
    viewModel: TaskViewModel,
    task: Task,
    onDismiss: () -> Unit
) {
    val languageCode by viewModel.language.collectAsStateWithLifecycle()
    val isHe = languageCode == "HE"
    
    val allChecklists by viewModel.allChecklists.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val checklistId = task.checkboxListId
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val isManager = currentUserRole?.isManager == true || currentUserRole?.allowCreateTasks == true || currentUserRole?.allowCreateChecklists == true

    var showLinkExistingDropdown by remember { mutableStateOf(false) }
    var showCreateChecklistForTaskDialog by remember { mutableStateOf(false) }

    if (checklistId == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (isHe) "קישור רשימת סימון" else "Link Checklist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(task.title, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (isHe) "אין רשימת סימון למשימה זו" else "No checklist for this task",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isHe) "משימה זו אינה מקושרת לרשימת סימון כרגע. באפשרותך ליצור רשימה חדשה או לקשר אחת קיימת."
                            else "This task is not linked to any checklist. You can create a new list or link an existing one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        
                        if (isManager) {
                            Button(
                                onClick = {
                                    showCreateChecklistForTaskDialog = true
                                },
                                modifier = Modifier.fillMaxWidth().testTag("create_checklist_for_task_button")
                            ) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isHe) "צור רשימת סימון חדשה" else "Create New Checklist")
                            }

                            if (showCreateChecklistForTaskDialog) {
                                var checklistName by remember { mutableStateOf(if (isHe) "רשימה ל: ${task.title}" else "List for: ${task.title}") }
                                var isPrivate by remember { mutableStateOf(false) }
                                AlertDialog(
                                    onDismissRequest = { showCreateChecklistForTaskDialog = false },
                                    title = { Text(if (isHe) "צור רשימת סימון חדשה" else "Create Checklist for Task") },
                                    text = {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            TextField(
                                                value = checklistName,
                                                onValueChange = { checklistName = it },
                                                placeholder = { Text(if (isHe) "הזן שם לרשימה..." else "Enter list name...") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = if (isHe) "נגישות הרשימה:" else "List Accessibility:",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            // Family Option
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { isPrivate = false }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = !isPrivate,
                                                    onClick = { isPrivate = false }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = if (isHe) "רשימה לכל המשפחה" else "Family Checklist",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = if (isHe) "נגישה לכל בני המשפחה" else "Accessible to all family members",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            // Private Option
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { isPrivate = true }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isPrivate,
                                                    onClick = { isPrivate = true }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = if (isHe) "רשימה פרטית" else "Private Checklist",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = if (isHe) "נגישה רק ליוצר הרשימה" else "Accessible only to the creator",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.Gray
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                if (checklistName.isNotBlank()) {
                                                    viewModel.createChecklistForTask(
                                                        task = task,
                                                        listName = checklistName.trim(),
                                                        isPrivate = isPrivate,
                                                        creatorId = currentUser?.id
                                                    )
                                                    showCreateChecklistForTaskDialog = false
                                                }
                                            }
                                        ) {
                                            Text(if (isHe) "צור" else "Create")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showCreateChecklistForTaskDialog = false }) {
                                            Text(if (isHe) "ביטול" else "Cancel")
                                        }
                                    }
                                )
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            
                            OutlinedButton(
                                onClick = { showLinkExistingDropdown = !showLinkExistingDropdown },
                                modifier = Modifier.fillMaxWidth().testTag("link_existing_checklist_button")
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isHe) "קשר לרשימת סימון קיימת" else "Link Existing Checklist")
                            }
                            
                            if (showLinkExistingDropdown) {
                                Spacer(Modifier.height(16.dp))
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                        .padding(4.dp)
                                ) {
                                    // Main Shopping List Option (-1)
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { 
                                                    viewModel.linkTaskWithCheckboxList(task, -1)
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = if (isHe) "רשימת קניות (ראשית)" else "Shopping List (Main)",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }

                                    val availableLists = allChecklists.filter { it.taskId == null && (!it.isPrivate || it.creatorId == currentUser?.id) }
                                    items(availableLists) { list ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { 
                                                    viewModel.linkTaskWithCheckboxList(task, list.id)
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.List, contentDescription = null, tint = Color.Gray)
                                            Spacer(Modifier.width(8.dp))
                                            Text(list.name, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = if (isHe) "רק מנהלים יכולים לקשר רשימות סימון." else "Only managers can configure checklist associations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    } else {
        val isMain = checklistId == -1
        val associatedList = if (isMain) null else allChecklists.find { it.id == checklistId }
        val listTitle = if (isMain) {
            if (isHe) "רשימת קניות" else "Shopping List"
        } else {
            associatedList?.name ?: (if (isHe) "רשימת סימון" else "Checkbox List")
        }
        ChecklistDetailsScreen(
            viewModel = viewModel,
            checklistId = if (isMain) null else checklistId,
            listTitle = listTitle,
            taskIdToLink = task.id,
            onBack = onDismiss
        )
    }
}

@Composable
fun AddEditCategoryDialog(
    currentLang: String,
    categoryToEdit: ShoppingCategory? = null,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(categoryToEdit?.name ?: "") }
    val isHe = currentLang == "HE"
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (categoryToEdit == null) {
                    Localization.get("new_category", currentLang)
                } else {
                    if (isHe) "ערוך קטגוריה" else "Edit Category"
                }
            )
        },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(Localization.get("category_name", currentLang)) },
                modifier = Modifier.fillMaxWidth().testTag("add_category_input")
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (categoryToEdit != null && onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(name.trim())
                        }
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("save_category_button")
                ) {
                    Text(Localization.get("save", currentLang))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Localization.get("cancel", currentLang))
            }
        }
    )
}

@Composable
fun AddEditProductDialog(
    currentLang: String,
    categories: List<ShoppingCategory>,
    preselectedCategoryId: Int? = null,
    itemToEdit: ShoppingItem? = null,
    onDismiss: () -> Unit,
    onSave: (String, Int?) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(itemToEdit?.name ?: "") }
    var chosenCatId by remember { mutableStateOf<Int?>(itemToEdit?.categoryId ?: preselectedCategoryId) }
    val isHe = currentLang == "HE"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (itemToEdit == null) {
                    Localization.get("new_product", currentLang)
                } else {
                    Localization.get("edit_product", currentLang)
                }
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(Localization.get("product_name", currentLang)) },
                    modifier = Modifier.fillMaxWidth().testTag("add_product_input")
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = Localization.get("choose_category", currentLang),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = chosenCatId == null,
                        onClick = { chosenCatId = null },
                        label = { Text(Localization.get("uncategorized", currentLang)) },
                        modifier = Modifier.testTag("category_chip_uncategorized")
                    )

                    categories.distinctBy { it.name.trim().lowercase() }.forEach { cat ->
                        FilterChip(
                            selected = chosenCatId == cat.id,
                            onClick = { chosenCatId = cat.id },
                            label = { Text(cat.name) },
                            modifier = Modifier.testTag("category_chip_${cat.id}")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (itemToEdit != null && onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onSave(name.trim(), chosenCatId)
                        }
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.testTag("save_product_button")
                ) {
                    Text(Localization.get("save", currentLang))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Localization.get("cancel", currentLang))
            }
        }
    )
}

@Composable
fun DropTargetZone(
    isActive: Boolean,
    label: String,
    onTitleClick: (() -> Unit)? = null,
    isExpanded: Boolean = true,
    isCompleted: Boolean = false,
    onToggleCheck: (() -> Unit)? = null,
    isChild: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onAddProductInside: (() -> Unit)? = null
) {
    if (onToggleCheck != null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTitleClick?.invoke() },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                else if (isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onTitleClick?.invoke() }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = "Expand folder",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Checkbox(
                    checked = isCompleted,
                    onCheckedChange = { onToggleCheck() },
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isCompleted) Color.Gray else Color.Unspecified
                    )
                }

                if (onAddProductInside != null) {
                    IconButton(onClick = onAddProductInside, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Quick add product inside folder", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (!isChild && onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit category", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ShoppingItemCard(
    item: ShoppingItem,
    isDragged: Boolean,
    isChild: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDragged) 8.dp else 1.dp,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCompleted) MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isChild) {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorganize",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }

            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = { onToggle() },
                modifier = Modifier.testTag("shopping_item_checkbox_${item.id}")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    fontWeight = if (item.isCompleted) FontWeight.Normal else FontWeight.Medium,
                    color = if (item.isCompleted) Color.Gray else Color.Unspecified
                )
            }

            if (!isChild) {
                IconButton(onClick = onEdit, modifier = Modifier.testTag("shopping_item_edit_${item.id}")) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit or Delete",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

fun handleDrop(
    itemId: Int,
    targetKey: String?,
    viewModel: TaskViewModel,
    categoriesList: List<ShoppingCategory>,
    itemsList: List<ShoppingItem>
) {
    if (targetKey == null) return

    val item = itemsList.find { it.id == itemId } ?: return

    when {
        targetKey.startsWith("category_") -> {
            val catId = targetKey.substringAfter("category_").toIntOrNull()
            if (catId != null) {
                viewModel.moveShoppingItem(itemId = itemId, targetCategoryId = catId, targetPosition = 0)
            }
        }
        targetKey.startsWith("item_") -> {
            val otherItemId = targetKey.substringAfter("item_").toIntOrNull()
            if (otherItemId != null && otherItemId != itemId) {
                val otherItem = itemsList.find { it.id == otherItemId }
                if (otherItem != null) {
                    viewModel.moveShoppingItem(
                        itemId = itemId,
                        targetCategoryId = otherItem.categoryId,
                        targetPosition = otherItem.position + 1
                    )
                }
            }
        }
        targetKey == "header_uncategorized" || targetKey == "bottom_spacer_drop_zone" -> {
            viewModel.moveShoppingItem(itemId = itemId, targetCategoryId = null, targetPosition = 0)
        }
    }
}

// ==========================================
// AI Voice Shopping Assistant Bottom Sheet
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIShoppingAssistantSheet(
    currentLang: String,
    checklistId: Int?,
    uncheckedItems: List<ShoppingItem>,
    viewModel: TaskViewModel,
    onDismiss: () -> Unit
) {
    val isHe = currentLang == "HE"
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var userText by remember { mutableStateOf("") }
    var aiText by remember { mutableStateOf(if (isHe) "שלום! אני עוזר הקניות החכם שלך. באילו מוצרים או אזור תרצה שנתמקד?" else "Hello! I am your AI Smart Shopping Assistant. Which area or products would you like to focus on?") }
    var isThinking by remember { mutableStateOf(false) }
    var isListeningSimulated by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }

    // TextToSpeech initialization
    var ttsPlayer by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Keep try context matching
            }
        }
        val locale = if (isHe) java.util.Locale("he") else java.util.Locale.US
        tts.language = locale
        ttsPlayer = tts

        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    // Sinus Wave animation for visual appeal
    val infiniteTransition = rememberInfiniteTransition(label = "sinus")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    fun speakTTS(message: String) {
        ttsPlayer?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun handleUserRequest(request: String) {
        if (request.isBlank()) return
        isThinking = true
        userText = ""
        isListeningSimulated = false
        coroutineScope.launch {
            try {
                val res = com.example.data.FirebaseAIService.speakToShoppingAssistant(
                    userQuery = request,
                    uncheckedItems = uncheckedItems,
                    currentLang = currentLang
                )
                aiText = res.speechMessage
                isThinking = false
                isSpeaking = true
                speakTTS(res.speechMessage)

                // Perform database updates for check-offs
                res.itemNamesToCheckOff.forEach { name ->
                    val matchedItem = uncheckedItems.find { item ->
                        item.name.contains(name, ignoreCase = true) || name.contains(item.name, ignoreCase = true)
                    }
                    if (matchedItem != null) {
                        viewModel.toggleItemCompletion(matchedItem, uncheckedItems, null)
                    }
                }
            } catch (e: Exception) {
                isThinking = false
                aiText = if (isHe) "אירעה שגיאה בחיבור לעוזר ה-AI." else "Connection to the AI assistant failed."
            }
        }
    }

    // Quick trigger suggestion presets
    val presets = if (isHe) {
        listOf(
            "אני באזור הפירות 🍇",
            "אני באזור מוצרי החלב 🥛",
            "תקריא לי הכל 📢",
            "סימנתי את התפוחים כהושלמו ✔️"
        )
    } else {
        listOf(
            "I am in the fruits area 🍇",
            "I'm in the dairy aisle 🥛",
            "Read everything aloud 📢",
            "Mark apples as bought ✔️"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isHe) "עוזר קולי חכם (AI)" else "AI Smart Voice Assistant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // AI Response speech box
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (isHe) "העוזר:" else "AI Assistant:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        if (isThinking) {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                Text(
                                    text = if (isHe) "מעבד בקשה קולית..." else "Analyzing voice instructions...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            Text(
                                text = aiText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Waveform Canvas (Plays when AI is listening or speaking)
                if (isListeningSimulated || isThinking || isSpeaking) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.05f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val width = size.width
                            val height = size.height
                            val midY = height / 2
                            val path = Path()

                            path.moveTo(0f, midY)
                            for (x in 0..width.toInt() step 5) {
                                val normalizedX = x.toFloat() / width
                                val amplitude = if (isThinking) 8f else 18f
                                val frequency = 15f
                                val y = midY + Math.sin((normalizedX * frequency + phaseShift).toDouble()).toFloat() * amplitude
                                path.lineTo(x.toFloat(), y)
                            }
                            drawPath(
                                path = path,
                                color = androidx.compose.ui.graphics.Color(0xFF2196F3),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                }

                // Presets Suggestions row
                Text(
                    text = if (isHe) "הנחיות מהירות:" else "Quick Actions:",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { text ->
                        SuggestionChip(
                            onClick = {
                                val cleanTxt = text.replace(Regex("[🍇🥛📢✔️]"), "").trim()
                                handleUserRequest(cleanTxt)
                            },
                            label = { Text(text, style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }

                // Text/Manual Input Box
                TextField(
                    value = userText,
                    onValueChange = { userText = it },
                    placeholder = { Text(if (isHe) "הקלד משהו או בקש קולית..." else "Type something or ask..." ) },
                    trailingIcon = {
                        IconButton(
                            onClick = { handleUserRequest(userText) },
                            enabled = userText.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                // Voice Recording Simulation Trigger
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListeningSimulated) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                            .clickable {
                                if (isListeningSimulated) {
                                    isListeningSimulated = false
                                    isThinking = false
                                } else {
                                    isListeningSimulated = true
                                    coroutineScope.launch {
                                        delay(3000)
                                        if (isListeningSimulated) {
                                            // Auto simulate speaking fruits area as a fun, complete POC
                                            handleUserRequest(if (isHe) "אני באזור הפירות" else "I'm in the fruits aisle")
                                        }
                                    }
                                }
                            }
                            .testTag("ai_mic_simulation_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListeningSimulated) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mic",
                            tint = if (isListeningSimulated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (isHe) "סגור עוזר" else "Stop AI")
            }
        }
    )
}
