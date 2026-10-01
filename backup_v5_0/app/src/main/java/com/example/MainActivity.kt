package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.*
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.rotate

import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.speech.tts.TextToSpeech

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskHubTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TaskHubApp()
                }
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Tasks", Icons.AutoMirrored.Filled.List)
    object Chores : Screen("chores", "Chores", Icons.Filled.Face)
    object Achievements : Screen("achievements", "Leaderboard", Icons.Filled.EmojiEvents)
    object ShoppingList : Screen("shopping", "Checklists", Icons.Filled.PlaylistAddCheck)
    object Store : Screen("store", "Store", Icons.Filled.ShoppingCart)
    object FamilySettings : Screen("permissions", "Settings", Icons.Filled.Settings)
}

@Composable
fun LanguageSelector(viewModel: TaskViewModel) {
    val currentLang by viewModel.language.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f))
            .clickable { viewModel.toggleLanguage() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Language,
            contentDescription = "Language Selector",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            text = if (currentLang == "HE") "עברית" else "English",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHubApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = remember { context.applicationContext as TaskApplication }
    val viewModel: TaskViewModel = viewModel(
        factory = TaskViewModelFactory(application.repository, context)
    )

    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentLang by viewModel.language.collectAsStateWithLifecycle()

    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearToast()
        }
    }

    val activeChecklistTask by viewModel.activeChecklistTask.collectAsStateWithLifecycle()

    val layoutDirection = if (currentLang == "HE") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection
    ) {
        if (currentUser == null) {
            FamilyLoginSetupScreen(viewModel)
        } else if (activeChecklistTask != null) {
            val task = activeChecklistTask!!
            val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
            val chores by viewModel.allChores.collectAsStateWithLifecycle()
            val latestTask = remember(task, allTasks, chores) {
                (allTasks + chores).find { it.id == task.id } ?: task
            }
            com.example.ui.ChecklistTaskDetailsOverlay(
                viewModel = viewModel,
                task = latestTask,
                onDismiss = { viewModel.openChecklist(null) }
            )
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        
                        val navigationScreens = listOfNotNull(
                            Screen.Dashboard,
                            if (currentUser?.role == UserRole.ADULT) Screen.Chores else null,
                            Screen.Achievements,
                            Screen.ShoppingList,
                            Screen.FamilySettings
                        )

                        navigationScreens.forEach { screen ->
                            val labelText = when (screen) {
                                Screen.Dashboard -> Localization.get("tasks", currentLang)
                                Screen.Chores -> Localization.get("chores", currentLang)
                                Screen.Achievements -> if (currentLang == "HE") "תחרות" else "Leaderboard"
                                Screen.ShoppingList -> Localization.get("checklists", currentLang)
                                Screen.Store -> Localization.get("store", currentLang)
                                Screen.FamilySettings -> Localization.get("settings", currentLang)
                                else -> screen.title
                            }
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = labelText) },
                                label = { Text(labelText) },
                                selected = currentDestination?.route == screen.route,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(navController, startDestination = Screen.Dashboard.route, modifier = Modifier.padding(innerPadding)) {
                        composable(Screen.Dashboard.route) {
                            DashboardScreen(viewModel, onNavigateToStore = { navController.navigate(Screen.Store.route) })
                        }
                        composable(Screen.Chores.route) {
                            ChoresScreen(viewModel, onNavigateToStore = { navController.navigate(Screen.Store.route) })
                        }
                        composable(Screen.Achievements.route) {
                            AchievementsScreen(viewModel)
                        }
                        composable(Screen.ShoppingList.route) {
                            ShoppingListScreen(viewModel, onNavigateToStore = { navController.navigate(Screen.Store.route) })
                        }
                        composable(Screen.Store.route) {
                            StoreScreen(viewModel, onBack = { navController.popBackStack() })
                        }
                        composable(Screen.FamilySettings.route) {
                            SettingsScreen(viewModel)
                        }
                    }
                    
                    // Main family-wide celebration overlay
                    val celebration by viewModel.celebrationEvent.collectAsStateWithLifecycle()
                    celebration?.let { event ->
                        AlertDialog(
                            onDismissRequest = { viewModel.clearCelebrationEvent() },
                            title = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("🎉👑🎉", style = MaterialTheme.typography.headlineLarge)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = if (currentLang == "HE") "הישג משפחתי מושלם! ⭐" else "Perfect Daily Streak! ⭐",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (currentLang == "HE") 
                                            "כל הכבוד ל-${event.userName}! 🎉 הישג מדהים: ביצע את כל ${event.taskCount} המשימות של היום בהצלחה יתרה! כולנו גאים בך! 💪🌟"
                                            else "Congratulations ${event.userName}! 🎉 Fantastic achievement: completed all ${event.taskCount} tasks for today with outstanding effort! We are all proud of you! 💪🌟",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    // Visual card with stats
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = if (currentLang == "HE") "תואר היום: אלוףהיום 🏅" else "Today's Title: Hero of the Day 🏅",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.clearCelebrationEvent() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (currentLang == "HE") "המשך לחגוג!" else "Keep Celebrating!")
                                }
                            },
                            modifier = Modifier.padding(16.dp)
                        )
                        
                        // Overlay confetti flying directly over the prompt modal!
                        ConfettiOverlay(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyLoginSetupScreen(viewModel: TaskViewModel) {
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentLang by viewModel.language.collectAsStateWithLifecycle()
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    
    var isCreatingFamily by remember { mutableStateOf(false) }
    var isJoiningFamily by remember { mutableStateOf(false) }
    var familyName by remember { mutableStateOf("") }
    var managerName by remember { mutableStateOf("") }
    var familyJoinCode by remember { mutableStateOf("") }

    var loginPromptUser by remember { mutableStateOf<User?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var isSettingPin by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }

    val activeFamilyId by viewModel.familyId.collectAsStateWithLifecycle()
    val isDefaultFamily = activeFamilyId == "family_default"

    LaunchedEffect(activeFamilyId) {
        if (!isDefaultFamily) {
            isCreatingFamily = false
            isJoiningFamily = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            LanguageSelector(viewModel)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.HomeWork,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = Localization.get("welcome_to_familyhub", currentLang),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = Localization.get("welcome_subtitle", currentLang),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Spacer(Modifier.height(32.dp))

            if (isCreatingFamily) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            text = Localization.get("create_family_manager", currentLang),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = familyName,
                            onValueChange = { familyName = it },
                            label = { Text(Localization.get("family_name_placeholder", currentLang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isAuthLoading
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = managerName,
                            onValueChange = { managerName = it },
                            label = { Text(Localization.get("your_name_placeholder", currentLang)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isAuthLoading
                        )
                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (familyName.isNotBlank() && managerName.isNotBlank()) {
                                    viewModel.createFamily(familyName, managerName)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = familyName.isNotBlank() && managerName.isNotBlank() && !isAuthLoading
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(Localization.get("create_signin", currentLang))
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        TextButton(
                            onClick = { isCreatingFamily = false },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAuthLoading
                        ) {
                            Text(Localization.get("back_to_login", currentLang))
                        }
                    }
                }
            } else if (isJoiningFamily) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            text = if (currentLang == "HE") "התחברות למשפחה קיימת" else "Connect to Existing Family",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (currentLang == "HE") 
                                "הזן את קוד הזיהוי המשפחתי (כדי לבחור פרופיל) או את הקוד האישי שלך (להתחברות ישירה)." 
                                else "Enter your unique family identifier code (to pick profile) or personal code (to log in directly).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(16.dp))

                        val context = LocalContext.current
                        OutlinedTextField(
                            value = familyJoinCode,
                            onValueChange = { familyJoinCode = it.replace(" ", "") },
                            label = { Text(if (currentLang == "HE") "קוד זיהוי (למשל: 485926-15)" else "Identifier (e.g., 485926-15)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            enabled = !isAuthLoading
                        )
                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val cleanInput = familyJoinCode.trim()
                                if (cleanInput.isBlank()) return@Button

                                if (cleanInput.contains("-")) {
                                    val parts = cleanInput.split("-")
                                    val fPart = parts[0].trim()
                                    val uPart = parts[1].trim()
                                    val fId = if (fPart.all { it.isDigit() }) "family_$fPart" else fPart
                                    val uId = uPart.toIntOrNull()
                                    
                                    if (uId != null) {
                                        viewModel.joinAndSelectProfile(fId, uId)
                                        android.widget.Toast.makeText(
                                            context,
                                            if (currentLang == "HE") "מתחבר ישירות לפרופיל..." else "Connecting directly to profile...",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            if (currentLang == "HE") "קוד שגוי או לא תקין" else "Invalid code identifier format",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    val fId = if (cleanInput.all { it.isDigit() }) "family_$cleanInput" else cleanInput
                                    viewModel.joinFamily(fId)
                                    android.widget.Toast.makeText(
                                        context,
                                        if (currentLang == "HE") "מתחבר למשפחה..." else "Connecting to family...",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = familyJoinCode.isNotBlank() && !isAuthLoading
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(if (currentLang == "HE") "התחבר" else "Connect")
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        TextButton(
                            onClick = { isJoiningFamily = false },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAuthLoading
                        ) {
                            Text(Localization.get("back_to_login", currentLang))
                        }
                    }
                }
            } else {
                val activeFamilyId by viewModel.familyId.collectAsStateWithLifecycle()
                val isDefaultFamily = activeFamilyId == "family_default"

                if (!isDefaultFamily && users.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (currentLang == "HE") "מתחבר ללוח המשפחה..." else "Connecting to Family Board...",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (currentLang == "HE") "מזהה משפחה: ${activeFamilyId.substringAfter("family_")}" else "Family ID: ${activeFamilyId.substringAfter("family_")}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(24.dp))
                            
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = if (currentLang == "HE") 
                                    "מנסה לסנכרן פרופילים משרת הענן המשפחתי. אם זו משפחה חדשה או שברצונך להוסיף פרופיל ראשון ללוח, לחץ למטה."
                                    else "Trying to sync family profiles from the cloud server. If this is a brand new family profile board, you can create the first profile below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(Modifier.height(24.dp))
                            
                            var showCreateFirstProfileInline by remember { mutableStateOf(false) }
                            var firstProfileName by remember { mutableStateOf("") }
                            var firstProfileIsAdult by remember { mutableStateOf(true) }
                            
                            if (!showCreateFirstProfileInline) {
                                Button(
                                    onClick = { showCreateFirstProfileInline = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (currentLang == "HE") "יצירת פרופיל ראשון במשפחה זו" else "Create First Profile in Family")
                                }
                            } else {
                                OutlinedTextField(
                                    value = firstProfileName,
                                    onValueChange = { firstProfileName = it },
                                    label = { Text(if (currentLang == "HE") "שם הפרופיל" else "Profile Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = firstProfileIsAdult,
                                            onClick = { firstProfileIsAdult = true }
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (currentLang == "HE") "מבוגר (הורה)" else "Adult (Parent)")
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = !firstProfileIsAdult,
                                            onClick = { firstProfileIsAdult = false }
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (currentLang == "HE") "ילד/ה" else "Child")
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                
                                Button(
                                    onClick = {
                                        if (firstProfileName.isNotBlank()) {
                                            viewModel.inviteMember(
                                                name = firstProfileName.trim(),
                                                role = if (firstProfileIsAdult) UserRole.ADULT else UserRole.CHILD
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = firstProfileName.isNotBlank()
                                ) {
                                    Text(if (currentLang == "HE") "הוספה וכניסה" else "Add and Log In")
                                }
                                
                                Spacer(Modifier.height(8.dp))
                                
                                TextButton(onClick = { showCreateFirstProfileInline = false }) {
                                    Text(if (currentLang == "HE") "ביטול" else "Cancel")
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            TextButton(
                                onClick = {
                                    viewModel.joinFamily("family_default")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (currentLang == "HE") "התנתק וחזור למסך הראשי" else "Disconnect & Go Back",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else if (users.isNotEmpty()) {
                    Text(
                        text = Localization.get("login_to_family_board", currentLang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(users) { user ->
                            val roleText = if (user.roleId.endsWith("MANAGER")) {
                                if (currentLang == "HE") "מנהל משפחה" else "Family Manager"
                            } else {
                                if (user.role == UserRole.ADULT) {
                                    if (currentLang == "HE") "הורה" else "Parent"
                                } else {
                                    if (currentLang == "HE") "ילד/ה" else "Child"
                                }
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val passcode = user.passcode
                                        if (user.role == UserRole.ADULT) {
                                            if (passcode.isNullOrBlank()) {
                                                loginPromptUser = user
                                                isSettingPin = true
                                                pinInput = ""
                                                pinError = false
                                            } else {
                                                loginPromptUser = user
                                                isSettingPin = false
                                                pinInput = ""
                                                pinError = false
                                            }
                                        } else {
                                            viewModel.selectUser(user.id)
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(
                                        user = user,
                                        size = 44.dp,
                                        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                                        textColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = user.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = roleText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.Gray
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Icon(
                                        imageVector = Icons.Default.Login,
                                        contentDescription = "Log In",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { isCreatingFamily = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(Localization.get("join_as_leader", currentLang))
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { isJoiningFamily = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (currentLang == "HE") "התחברות למשפחה אחרת עם קוד" else "Connect to Another Family with Code")
                    }
                } else {
                    // Empty users state on extremely fresh boot
                    Button(
                        onClick = { isCreatingFamily = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(Localization.get("start_new_family", currentLang))
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { isJoiningFamily = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (currentLang == "HE") "התחברות למשפחה קיימת עם קוד" else "Connect to Existing Family with Code")
                    }
                }
            }
        }

        if (isAuthLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.56f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Block all interactions */ },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(0.85f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(56.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        val messageTitle = if (isCreatingFamily) {
                            if (currentLang == "HE") "יוצר משפחה חדשה..." else "Creating new family..."
                        } else {
                            if (currentLang == "HE") "מתחבר ללוח המשפחה..." else "Connecting to family..."
                        }
                        
                        val messageDesc = if (isCreatingFamily) {
                            if (currentLang == "HE") 
                                "מכין את לוח המשימות בענן המשפחתי שלך. זה עשוי לקחת מספר שניות." 
                                else "Setting up your family board in the cloud. This may take a few seconds."
                        } else {
                            if (currentLang == "HE")
                                "מתחבר ומסנכרן את המשימות מהשרת. זה עשוי לקחת מספר שניות."
                                else "Connecting and synchronizing tasks from the server. This may take a few seconds."
                        }

                        Text(
                            text = messageTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = messageDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    if (loginPromptUser != null) {
        val targetUser = loginPromptUser!!
        AlertDialog(
            onDismissRequest = { loginPromptUser = null },
            title = {
                Text(
                    text = if (isSettingPin) {
                        if (currentLang == "HE") "הגדרת קוד גישה לפרופיל" else "Set Profile PIN"
                    } else {
                        if (currentLang == "HE") "הזנת קוד גישה לפרופיל ${targetUser.name}" else "Enter PIN for ${targetUser.name}"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isSettingPin) {
                            if (currentLang == "HE") "אנא קבע/י קוד סודי בן 4 ספרות כדי למנוע מעקף הרשאות על ידי ילדים" else "Please set a 4-digit PIN code to secure your parental settings"
                        } else {
                            if (currentLang == "HE") "הזן/י את קוד הגישה בן 4 הספרות של ${targetUser.name}" else "Please type the 4-digit security PIN for ${targetUser.name}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                pinInput = input
                                pinError = false
                            }
                        },
                        label = { Text(if (currentLang == "HE") "קוד סודי (4 ספרות)" else "PIN Code (4 digits)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        isError = pinError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_code_input")
                    )
                    
                    if (pinError) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isSettingPin) {
                                if (currentLang == "HE") "הקוד חייב להכיל 4 ספרות" else "PIN must be exactly 4 digits"
                            } else {
                                if (currentLang == "HE") "קוד גישה לא נכון, נסו שנית" else "Incorrect PIN, please try again"
                            },
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSettingPin) {
                            if (pinInput.length == 4) {
                                viewModel.updateMemberPasscode(targetUser.id, pinInput)
                                viewModel.selectUser(targetUser.id)
                                loginPromptUser = null
                            } else {
                                pinError = true
                            }
                        } else {
                            if (pinInput == targetUser.passcode) {
                                viewModel.selectUser(targetUser.id)
                                loginPromptUser = null
                            } else {
                                pinError = true
                            }
                        }
                    },
                    modifier = Modifier.testTag("pin_confirm_button")
                ) {
                    Text(if (currentLang == "HE") "אישור" else "Confirm")
                }
            },
            dismissButton = {
                Row {
                    if (isSettingPin) {
                        TextButton(
                            onClick = {
                                viewModel.selectUser(targetUser.id)
                                loginPromptUser = null
                            },
                            modifier = Modifier.testTag("pin_skip_button")
                        ) {
                            Text(if (currentLang == "HE") "דלג" else "Skip")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = { loginPromptUser = null }) {
                        Text(if (currentLang == "HE") "ביטול" else "Cancel")
                    }
                }
            }
        )
    }
}

// isScheduledForToday is now imported from com.example.data


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: TaskViewModel, onNavigateToStore: () -> Unit) {
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val chores by viewModel.allChores.collectAsStateWithLifecycle()
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val currentLang by viewModel.language.collectAsStateWithLifecycle()

    val returnedHe by viewModel.returnedTasksAlertHe.collectAsStateWithLifecycle()
    val returnedEn by viewModel.returnedTasksAlertEn.collectAsStateWithLifecycle()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showPullFromBankDialog by remember { mutableStateOf(false) }
    var selectedMemberId by remember(currentUser) { mutableStateOf<Int?>(currentUser?.id) }

    val isHe = currentLang == "HE"

    // 1. Day-transition notification dialog
    if ((isHe && returnedHe.isNotEmpty()) || (!isHe && returnedEn.isNotEmpty())) {
        val returnedList = if (isHe) returnedHe else returnedEn
        AlertDialog(
            onDismissRequest = { viewModel.clearReturnedTasksAlert() },
            title = {
                Text(
                    text = if (isHe) "הודעת מערכת: משימות הוחזרו לבנק 🌤️" else "System: Tasks Returned to Bank 🌤️",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isHe) 
                            "בוקר טוב! המשימות והמטלות הבאות שהיו אצלכם אתמול אך לא הושלמו, הוחזרו באופן אוטומטי לבנק המשותף כדי שתוכלו לקחת או לשייך אותן מחדש:"
                            else "Good morning! The following active tasks/chores from yesterday were incomplete and have been returned to the bank for today:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(returnedList) { name ->
                            Text("• $name", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.clearReturnedTasksAlert() }) {
                    Text(if (isHe) "הבנתי, תודה" else "Got it, thanks")
                }
            }
        )
    }

    // Determine current viewed member (for admin/adult viewing others)
    val activeMemberId = if (currentUser?.role == UserRole.ADULT) {
        selectedMemberId ?: currentUser?.id
    } else {
        currentUser?.id
    }
    val activeMemberUser = users.find { it.id == activeMemberId }

    // Grouping and Filtering
    val allCombined = remember(tasks, chores) { tasks + chores }
    
    // Parental approvals to verify (only visible if logged-in user is ADULT)
    val parentVerifications = remember(allCombined, currentUser) {
        if (currentUser?.role == UserRole.ADULT) {
            allCombined.filter { 
                it.status == TaskStatus.PENDING_APPROVAL
            }
        } else {
            emptyList()
        }
    }

    // Active schedule for the chosen member in "My Day"
    val todaysTasks = remember(allCombined, activeMemberId) {
        allCombined.filter { 
            it.assignedToUserId == activeMemberId && 
            it.source != TaskSource.STORE_REDEMPTION &&
            !(it.parentId == null && !it.recurrenceRule.isNullOrBlank() && it.recurrenceRule != "NONE") &&
            isScheduledForToday(it)
        }
    }

    val activeToday = todaysTasks.filter { it.status != TaskStatus.DONE }
    val completedToday = todaysTasks.filter { it.status == TaskStatus.DONE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(Localization.get("taskhub_dashboard", currentLang), fontWeight = FontWeight.Bold)
                        val loggedInText = if (isHe) "מחובר בתור" else "Logged in as"
                        Text(
                            text = "${currentUser?.familyName ?: ""} • $loggedInText ${currentUser?.name ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToStore,
                        modifier = Modifier.testTag("dashboard_store_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = if (isHe) "חנות וירטואלית" else "Virtual Store",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.selectUser(null) },
                        modifier = Modifier.testTag("switch_profile_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwitchAccount,
                            contentDescription = if (isHe) "החלף פרופיל" else "Switch Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    LanguageSelector(viewModel)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 2. Member selector chips for parents
            if (currentUser?.role == UserRole.ADULT && users.isNotEmpty()) {
                Text(
                    text = if (isHe) "צפייה ביום שלי של חבר משפחה:" else "View My Day for Member:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(users) { member ->
                        val isSelected = selectedMemberId == member.id
                        val color = getAssigneeColor(member.id)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMemberId = member.id },
                            label = { Text(member.name + if (member.id == currentUser?.id) " (אני)" else "") },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(color, CircleShape)
                                )
                            }
                        )
                    }
                    item {
                        Spacer(Modifier.width(16.dp))
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 3. Parent Inbox Section (Verify and Reward)
                if (currentUser?.role == UserRole.ADULT && parentVerifications.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isHe) "אישורי הורים להיום (${parentVerifications.size})" else "Parent Approvals Today (${parentVerifications.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    parentVerifications.forEach { task ->
                                        val assignee = users.find { it.id == task.assignedToUserId }
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Column(Modifier.padding(10.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        val badgeText = when {
                                                            task.source == TaskSource.STORE_REDEMPTION -> {
                                                                if (isHe) "🎁 רכישת חנות" else "🎁 Store Purchase"
                                                            }
                                                            task.isChore -> {
                                                                if (isHe) "🧹 מטלה משפחתית" else "🧹 Family Chore"
                                                            }
                                                            else -> {
                                                                if (isHe) "📌 משימה משפחתית" else "📌 Family Task"
                                                            }
                                                        }
                                                        Text(badgeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                                                        Text(task.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                        val timeStr = if (task.lastCompletedTimestamp != null && task.lastCompletedTimestamp > 0L) {
                                                            try {
                                                                val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                                                formatter.format(java.util.Date(task.lastCompletedTimestamp))
                                                            } catch (e: Exception) {
                                                                ""
                                                            }
                                                        } else ""
                                                        val subtitleText = buildString {
                                                            append("${if (isHe) "מפי" else "From"}: ${assignee?.name ?: "Unknown"} • +${task.rewardPoints} ${Localization.get("pts", currentLang)}")
                                                            if (timeStr.isNotEmpty()) {
                                                                 append(if (isHe) " • בוצע ב-$timeStr" else " • Completed at $timeStr")
                                                            }
                                                        }
                                                        Text(
                                                            text = subtitleText,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        // Reject/Decline Button
                                                        IconButton(
                                                            onClick = { viewModel.approveChore(task, false) },
                                                            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), CircleShape)
                                                        ) {
                                                            Icon(Icons.Default.Close, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                        }
                                                        // Approve/Accept Button
                                                        IconButton(
                                                            onClick = { 
                                                                if (task.source == TaskSource.STORE_REDEMPTION) {
                                                                    viewModel.completeTask(task)
                                                                } else {
                                                                    viewModel.approveChore(task, true)
                                                                }
                                                            },
                                                            modifier = Modifier.size(36.dp).background(Color(0xFFE8F5E9), CircleShape)
                                                        ) {
                                                            Icon(Icons.Default.Check, contentDescription = "Approve", tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Quick Actions for activeMember
                item {
                    Button(
                        onClick = { showPullFromBankDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isHe) "משוך משימה/מטלה מהבנק +" else "Pull Task/Chore from Bank +",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Header section
                item {
                    val nameStr = if (activeMemberId == currentUser?.id) {
                        if (isHe) "היום שלי" else "My Schedule"
                    } else {
                        if (isHe) "היום של ${activeMemberUser?.name}" else "${activeMemberUser?.name}'s Schedule"
                    }
                    Text(
                        text = nameStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // 5. Active Schedule List in My Day
                if (activeToday.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Celebration, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.LightGray)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (isHe) "איזה כיף! אין משימות או מטלות פעילות להיום ✨" else "Amazing! No active tasks or chores for today ✨",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (isHe) "לחצו על 'משוך מהבנק' כדי להתחיל!" else "Tap 'Pull from Bank' to start!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(activeToday) { task ->
                        TaskItemMyDay(
                            task = task,
                            isHe = isHe,
                            viewModel = viewModel,
                            onComplete = { viewModel.completeTask(task) },
                            onUncomplete = { viewModel.uncompleteTask(task) },
                            onReturnToBank = {
                                viewModel.updateTask(task.copy(assignedToUserId = null, status = TaskStatus.TODO))
                            },
                            onDelete = { viewModel.deleteTask(task) },
                            onOpenChecklist = { viewModel.openChecklist(task) }
                        )
                    }
                }

                // 6. Completed Schedule List in My Day
                if (completedToday.isNotEmpty()) {
                    item {
                        Text(
                            text = if (isHe) "משימות שהושלמו היום" else "Completed Today",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    items(completedToday) { task ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                        color = Color.Gray
                                    )
                                    val completedTypeLabel = if (task.isChore) {
                                        if (isHe) "מטלה • אושר ובוצע (+${task.rewardPoints} נק')" else "Chore • Approved & Earned (+${task.rewardPoints} pts)"
                                    } else {
                                        if (isHe) "משימה • הושלמה בהצלחה" else "Task • Done successfully"
                                    }
                                    Text(completedTypeLabel, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                Icon(Icons.Default.CheckCircle, contentDescription = "Completed", tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
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



     // Pull from Bank Dialog
     if (showPullFromBankDialog) {
         val unclaimedTasks = remember(allCombined, activeMemberId) {
             val todayStr = String.format(
                 "%04d-%02d-%02d", 
                 java.util.Calendar.getInstance().get(java.util.Calendar.YEAR), 
                 java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1, 
                 java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
             )
             allCombined.filter { task ->
                 if (task.source == TaskSource.STORE_REDEMPTION) return@filter false
                 if (task.status == TaskStatus.DONE || task.status == TaskStatus.PENDING_APPROVAL) return@filter false
                 
                 val isSimpleTask = task.parentId == null && (task.recurrenceRule.isNullOrBlank() || task.recurrenceRule == "NONE")
                 val isChildTaskForToday = task.parentId != null && task.pulledForDate == todayStr
                 
                 (isSimpleTask || isChildTaskForToday) && task.assignedToUserId == null
             }
         }
         PullFromBankDialog(
             languageCode = currentLang,
             availableTasks = unclaimedTasks,
             onDismiss = { showPullFromBankDialog = false },
             onPull = { task ->
                 val todayStr = String.format(
                     "%04d-%02d-%02d", 
                     java.util.Calendar.getInstance().get(java.util.Calendar.YEAR), 
                     java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1, 
                     java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
                 )
                 val updatedTask = task.copy(
                     assignedToUserId = activeMemberId, 
                     status = TaskStatus.TODO,
                     pulledForDate = todayStr
                 )
                 viewModel.updateTask(updatedTask)
                 showPullFromBankDialog = false
             }
         )
     }
}

@Composable
fun PullFromBankDialog(
    languageCode: String,
    availableTasks: List<Task>,
    onDismiss: () -> Unit,
    onPull: (Task) -> Unit
) {
    val isHe = languageCode == "HE"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isHe) "משיכת משימה/מטלה מהבנק" else "Pull Task/Chore from Bank") },
        text = {
            if (availableTasks.isEmpty()) {
                Text(if (isHe) "אין משימות זמינות בבנק כרגע." else "No available tasks in the bank right now.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(availableTasks) { task ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPull(task) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, fontWeight = FontWeight.Bold)
                                    val recurrenceText = formatRecurrenceRule(task.recurrenceRule, languageCode)
                                    val subtitle = if (task.isChore) {
                                        if (isHe) {
                                            if (!recurrenceText.isNullOrBlank()) "מטלה מחזורית ($recurrenceText) • +${task.rewardPoints} נק'"
                                            else "מטלה • +${task.rewardPoints} נק'"
                                        } else {
                                            if (!recurrenceText.isNullOrBlank()) "Recurring Chore ($recurrenceText) • +${task.rewardPoints} pts"
                                            else "Chore • +${task.rewardPoints} pts"
                                        }
                                    } else {
                                        if (isHe) {
                                            if (!recurrenceText.isNullOrBlank()) "משימה מחזורית ($recurrenceText)"
                                            else "משימה"
                                        } else {
                                            if (!recurrenceText.isNullOrBlank()) "Recurring Task ($recurrenceText)"
                                            else "Standard Task"
                                        }
                                    }
                                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Pull",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isHe) "סגור" else "Close")
            }
        }
    )
}

@Composable
fun TaskItemMyDay(
    task: Task,
    isHe: Boolean,
    viewModel: TaskViewModel,
    onComplete: () -> Unit,
    onUncomplete: () -> Unit,
    onReturnToBank: () -> Unit,
    onDelete: () -> Unit,
    onOpenChecklist: () -> Unit
) {
    val cardColor = if (task.status == TaskStatus.PENDING_APPROVAL) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Chore badge
                    if (task.isChore) {
                        Text(
                            text = if (isHe) "🧹 מטלה • +${task.rewardPoints} נק'" else "🧹 Chore • +${task.rewardPoints} pts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    } else {
                        Text(
                            text = if (isHe) "📌 משימה" else "📌 Task",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = task.title, 
                    style = MaterialTheme.typography.bodyLarge, 
                    fontWeight = FontWeight.Bold,
                )
                if (task.description.isNotEmpty()) {
                    Text(task.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }

                // Recurrence Indicator
                val recurrenceText = formatRecurrenceRule(task.recurrenceRule, if (isHe) "HE" else "EN")
                if (recurrenceText != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(recurrenceText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }

                // Checklist Status/Link Row
                val listId = task.checkboxListId
                if (listId != null) {
                    val itemsFlow = remember(listId) { viewModel.getItemsForChecklist(listId) }
                    val items by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                    val total = items.size
                    val completed = items.count { it.isCompleted }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { onOpenChecklist() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAddCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isHe) "רשימת סימון ($completed/$total)" else "Checklist ($completed/$total)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onOpenChecklist() }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isHe) "חבר רשימת סימון..." else "Link checklist...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                        )
                    }
                }

                if (task.status == TaskStatus.PENDING_APPROVAL) {
                    Spacer(Modifier.height(4.dp))
                    val timeStr = if (task.lastCompletedTimestamp != null && task.lastCompletedTimestamp > 0L) {
                        try {
                            val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            formatter.format(java.util.Date(task.lastCompletedTimestamp))
                        } catch (e: Exception) {
                            ""
                        }
                    } else ""
                    val approvalText = if (isHe) {
                        if (timeStr.isNotEmpty()) "⏳ ממתין לאישור הורה (סומן ב-$timeStr)" else "⏳ ממתין לאישור הורה..."
                    } else {
                        if (timeStr.isNotEmpty()) "⏳ Awaiting parent approval (Done at $timeStr)" else "⏳ Awaiting parent approval..."
                    }
                    Text(
                        text = approvalText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF57C00),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Return to bank button
                IconButton(
                    onClick = onReturnToBank,
                    modifier = Modifier.testTag("return_to_bank_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AssignmentReturn,
                        contentDescription = if (isHe) "החזר לבנק" else "Return to Bank",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                if (task.status == TaskStatus.PENDING_APPROVAL) {
                    // Undo action (let them change their mind and mark NOT complete)
                    TextButton(onClick = onUncomplete) {
                        Text(if (isHe) "בטל" else "Undo")
                    }
                } else {
                    Checkbox(
                        checked = task.status == TaskStatus.DONE || task.status == TaskStatus.PENDING_APPROVAL,
                        onCheckedChange = { isChecked ->
                            if (isChecked) onComplete() else onUncomplete()
                        },
                        modifier = Modifier.testTag("complete_checkbox")
                    )
                }
            }
        }
    }
}

@Composable
fun TaskItem(
    task: Task,
    currentUser: User?,
    currentUserRole: FamilyRole?,
    viewModel: TaskViewModel,
    onComplete: () -> Unit,
    onUncomplete: () -> Unit,
    onDelete: () -> Unit,
    onOpenChecklist: () -> Unit
) {
    val sysLanguage = java.util.Locale.getDefault().language.lowercase()
    val isHe = sysLanguage.contains("he") || sysLanguage.contains("iw")
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title, 
                    style = MaterialTheme.typography.bodyLarge, 
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (task.status == TaskStatus.DONE) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    color = if (task.status == TaskStatus.DONE) Color.Gray else Color.Unspecified
                )
                if (task.description.isNotEmpty()) {
                    Text(task.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                
                // Recurrence status indicator for standard tasks
                val recurrenceText = formatRecurrenceRule(task.recurrenceRule, if (isHe) "HE" else "EN")
                if (recurrenceText != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (task.status == TaskStatus.DONE) {
                                if (isHe) "תיכנס לתוקף מחדש במחזוריות הבאה" else "Will enter into force again in the next cycle"
                            } else {
                                recurrenceText
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    val icon = when(task.source) {
                        TaskSource.WHATSAPP -> Icons.AutoMirrored.Filled.Send
                        TaskSource.CALENDAR -> Icons.Default.DateRange
                        TaskSource.EMAIL -> Icons.Default.Email
                        TaskSource.CALL -> Icons.Default.Call
                        else -> Icons.Default.Edit
                    }
                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text(task.source.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }

                // Checklist Status/Link Row in TaskItem
                val listId = task.checkboxListId
                if (listId != null) {
                    val itemsFlow = remember(listId) { viewModel.getItemsForChecklist(listId) }
                    val items by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                    val total = items.size
                    val completed = items.count { it.isCompleted }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { onOpenChecklist() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAddCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isHe) "רשימת סימון ($completed/$total)" else "Checklist ($completed/$total)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onOpenChecklist() }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isHe) "חבר רשימת סימון..." else "Link checklist...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Delete button permission check
                val allowDelete = currentUserRole?.allowDeleteTasks == true || currentUserRole?.isManager == true
                if (allowDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Task", tint = MaterialTheme.colorScheme.error)
                    }
                }

                Checkbox(
                    checked = task.status == TaskStatus.DONE,
                    onCheckedChange = { isChecked ->
                        if (isChecked) onComplete() else onUncomplete()
                    }
                )
            }
        }
    }
}

val UserColorsList = listOf(
    Color(0xFF3F51B5), // Indigo
    Color(0xFF009688), // Teal
    Color(0xFFE91E63), // Pink
    Color(0xFF9C27B0), // Purple
    Color(0xFFFF9800), // Orange
    Color(0xFF03A9F4), // Light Blue
    Color(0xFF4CAF50), // Green
)

fun getAssigneeColor(userId: Int?): Color {
    if (userId == null || userId < 0) return Color(0xFF757575) // Charcoal Gray
    return UserColorsList[userId % UserColorsList.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoresScreen(viewModel: TaskViewModel, onNavigateToStore: () -> Unit) {
    val chores by viewModel.allChores.collectAsStateWithLifecycle()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val allBankItems = remember(chores, tasks) { chores + tasks }
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val currentLang by viewModel.language.collectAsStateWithLifecycle()
    val kids = users.filter { it.role == UserRole.CHILD }
    var showAddChoreDialog by remember { mutableStateOf(false) }
    var showAiTaskGenerator by remember { mutableStateOf(false) }
    var editingChore by remember { mutableStateOf<Task?>(null) }
    var selectedFilterUserId by remember { mutableStateOf<Int?>(null) }
    var showRecentlyApprovedScreen by remember { mutableStateOf(false) }
    var hideExpired by remember { mutableStateOf(true) }
    var showIncomplete by remember { mutableStateOf(true) }
    var showCompleted by remember { mutableStateOf(false) }
    val expandedParentIds = remember { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(currentUser) {
        selectedFilterUserId = null
    }

    if (showRecentlyApprovedScreen) {
        androidx.activity.compose.BackHandler {
            showRecentlyApprovedScreen = false
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            text = Localization.get("recently_approved_chores", currentLang), 
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    navigationIcon = {
                        IconButton(onClick = { showRecentlyApprovedScreen = false }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = if (currentLang == "HE") "חזור" else "Back"
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            val completedChores = allBankItems.filter { it.status == TaskStatus.DONE }
            if (completedChores.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.LightGray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (currentLang == "HE") "אין מטלות שאושרו לאחרונה." else "No recently approved chores.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showRecentlyApprovedScreen = false }) {
                            Text(if (currentLang == "HE") "סגור" else "Close")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(completedChores) { chore ->
                        val assignee = users.find { it.id == chore.assignedToUserId }
                        val cardBorder = BorderStroke(1.5.dp, getAssigneeColor(assignee?.id).copy(alpha = 0.4f))
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("recently_approved_chore_item_${chore.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                            border = cardBorder
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = chore.title, 
                                        fontWeight = FontWeight.Bold, 
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, 
                                        color = Color.Gray
                                    )
                                    val approvedText = if (currentLang == "HE") "אושר עבור" else "Approved for"
                                    Text(
                                        text = "$approvedText ${assignee?.name ?: "Unknown"} • +${chore.rewardPoints} ${Localization.get("pts", currentLang)}", 
                                        style = MaterialTheme.typography.labelMedium, 
                                        color = Color.Gray
                                    )
                                    
                                    val recurrenceText = formatRecurrenceRule(chore.recurrenceRule, currentLang)
                                    if (recurrenceText != null) {
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (currentLang == "HE") "תיכנס לתוקף מחדש במחזוריות הבאה" else "Will enter into force again in the next cycle",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.deleteTask(chore) },
                                        modifier = Modifier.testTag("delete_recently_approved_chore_${chore.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = Localization.get("delete_task", currentLang),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    
                                    TextButton(
                                        onClick = { viewModel.unapproveChore(chore) },
                                        modifier = Modifier.testTag("revert_recently_approved_chore_${chore.id}")
                                    ) {
                                        Text(Localization.get("revert", currentLang), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text(Localization.get("chores", currentLang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            val viewText = if (currentUserRole?.isManager == true) {
                                if (currentLang == "HE") "תצוגת מנהל: ${currentUser?.name}" else "Manager View: ${currentUser?.name}"
                            } else {
                                if (currentLang == "HE") "תצוגת חבר: ${currentUser?.name}" else "Member View: ${currentUser?.name}"
                            }
                            Text(
                                text = viewText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToStore) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = Localization.get("store", currentLang),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.selectUser(null) },
                            modifier = Modifier.testTag("switch_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwitchAccount,
                                contentDescription = if (currentLang == "HE") "החלף פרופיל" else "Switch Profile",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        LanguageSelector(viewModel)
                    }
                )
            },
            floatingActionButton = {
                // Check create task permissions
                val allowCreate = currentUserRole?.allowCreateTasks == true || currentUserRole?.isManager == true
                if (allowCreate) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                        // AI Smart Voice Task Parser FAB
                        ExtendedFloatingActionButton(
                            onClick = { showAiTaskGenerator = true },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.testTag("ai_task_generator_fab")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI Task Parser")
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (currentLang == "HE") "עוזר משימות AI" else "AI Task Helper",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Normal Task FAB
                        FloatingActionButton(
                            onClick = { 
                                editingChore = null
                                showAddChoreDialog = true 
                            }, 
                            containerColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.testTag("add_chore_fab")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = Localization.get("new_family_chore", currentLang))
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                 if (currentUser?.role == UserRole.ADULT) {
                    Text(Localization.get("filter_chores_by", currentLang), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = selectedFilterUserId == null,
                                onClick = { selectedFilterUserId = null },
                                label = { Text(Localization.get("all_members", currentLang)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilterUserId == -1,
                                onClick = { selectedFilterUserId = -1 },
                                label = { Text(if (currentLang == "HE") "לא משויך" else "Unassigned") }
                            )
                        }
                        items(users) { member ->
                            val isSelected = selectedFilterUserId == member.id
                            val color = getAssigneeColor(member.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilterUserId = member.id },
                                label = { Text(member.name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(color, CircleShape)
                                    )
                                }
                            )
                        }
                        item {
                            Spacer(Modifier.width(16.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Unified Filter Section (Visible to everyone)
                Text(
                    text = if (currentLang == "HE") "סינון משימות:" else "Filter Tasks:",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    item {
                        FilterChip(
                            selected = hideExpired,
                            onClick = { hideExpired = !hideExpired },
                            label = { Text(if (currentLang == "HE") "הסתר משימות שעבר זמנן" else "Hide Expired") },
                            leadingIcon = {
                                if (hideExpired) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                    item {
                        FilterChip(
                            selected = showIncomplete,
                            onClick = { showIncomplete = !showIncomplete },
                            label = { Text(if (currentLang == "HE") "טרם בוצעו" else "Incomplete") },
                            leadingIcon = {
                                if (showIncomplete) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                    item {
                        FilterChip(
                            selected = showCompleted,
                            onClick = { showCompleted = !showCompleted },
                            label = { Text(if (currentLang == "HE") "בוצעו" else "Completed") },
                            leadingIcon = {
                                if (showCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        )
                    }
                    item {
                        Spacer(Modifier.width(16.dp))
                    }
                }

                // Filter chores: child sees the shared bank of available (unassigned) chores; adult sees all or filtered by member
                val user = currentUser
                val pendingChores = remember(allBankItems, user, selectedFilterUserId) {
                    if (user == null) emptyList()
                    else if (user.role == UserRole.ADULT) {
                        if (selectedFilterUserId == -1) {
                            allBankItems.filter { it.assignedToUserId == null && it.status == TaskStatus.PENDING_APPROVAL }
                        } else if (selectedFilterUserId != null) {
                            allBankItems.filter { it.assignedToUserId == selectedFilterUserId && it.status == TaskStatus.PENDING_APPROVAL }
                        } else {
                            allBankItems.filter { it.status == TaskStatus.PENDING_APPROVAL }
                        }
                    } else {
                        // Kids see their own chores pending validation
                        allBankItems.filter { it.assignedToUserId == user.id && it.status == TaskStatus.PENDING_APPROVAL }
                    }
                }

                val todayStart: Long = remember {
                    java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }

                val simpleTasks = remember(allBankItems, user, selectedFilterUserId, hideExpired, showIncomplete) {
                    if (user == null || !showIncomplete) emptyList()
                    else {
                        allBankItems.filter { task ->
                            task.status == TaskStatus.TODO &&
                            task.parentId == null &&
                            (task.recurrenceRule.isNullOrBlank() || task.recurrenceRule == "NONE") &&
                            (if (user.role == UserRole.ADULT) {
                                if (selectedFilterUserId == -1) task.assignedToUserId == null
                                else if (selectedFilterUserId != null) task.assignedToUserId == selectedFilterUserId
                                else true
                             } else {
                                // For kid, only show unassigned simple tasks (available chores in the bank)!
                                task.assignedToUserId == null
                             }) &&
                            (!hideExpired || task.dueDate == null || task.dueDate >= todayStart)
                        }
                    }
                }

                val parentTasks = remember(allBankItems, user, selectedFilterUserId) {
                    if (user == null) emptyList()
                    else {
                        allBankItems.filter { task ->
                            task.parentId == null &&
                            !task.recurrenceRule.isNullOrBlank() &&
                            task.recurrenceRule != "NONE" &&
                            (if (user.role == UserRole.ADULT) {
                                if (selectedFilterUserId == -1) task.assignedToUserId == null
                                else if (selectedFilterUserId != null) task.assignedToUserId == selectedFilterUserId
                                else true
                             } else {
                                // For kid, only show unassigned parent tasks! Note that if the parent task has an assignee, we don't display it in the shared bank for other kids to claim.
                                task.assignedToUserId == null
                             })
                        }
                    }
                }

                val completedChores = remember(allBankItems, user, selectedFilterUserId, showCompleted) {
                    if (user == null || !showCompleted) emptyList()
                    else if (user.role == UserRole.ADULT) {
                        if (selectedFilterUserId == -1) {
                            allBankItems.filter { it.assignedToUserId == null && it.status == TaskStatus.DONE }
                        } else if (selectedFilterUserId != null) {
                            allBankItems.filter { it.assignedToUserId == selectedFilterUserId && it.status == TaskStatus.DONE }
                        } else {
                            allBankItems.filter { it.status == TaskStatus.DONE }
                        }
                    } else {
                        // Kids see their own completed chores
                        allBankItems.filter { it.assignedToUserId == user.id && it.status == TaskStatus.DONE }
                    }
                }

                // Parent's Verification Section
                if (currentUser?.role == UserRole.ADULT && pendingChores.isNotEmpty()) {
                    Text(Localization.get("verify_reward", currentLang), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp).weight(1f, fill = false)) {
                        items(pendingChores) { chore ->
                            val assignee = users.find { it.id == chore.assignedToUserId }
                            VerifyChoreItem(
                                chore = chore,
                                assignee = assignee,
                                showAssigneeTag = currentUser?.role == UserRole.ADULT,
                                languageCode = currentLang,
                                onApprove = { viewModel.approveChore(chore, true) },
                                onReject = { viewModel.approveChore(chore, false) },
                                onDelete = { viewModel.deleteTask(chore) }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Kid's pending approval section (with Undo option)
                if (currentUser?.role == UserRole.CHILD && pendingChores.isNotEmpty()) {
                    Text(Localization.get("awaiting_verification", currentLang), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp).weight(1f, fill = false)) {
                        items(pendingChores) { chore ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(chore.title, fontWeight = FontWeight.Bold)
                                        val waitingText = if (currentLang == "HE") "ממתין לאישור הורה" else "Waiting for parent"
                                        Text("$waitingText • +${chore.rewardPoints} ${Localization.get("pts", currentLang)}", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                    }
                                    TextButton(onClick = { viewModel.uncompleteTask(chore) }) {
                                        Text(Localization.get("undo", currentLang), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                val activeChoresHeader = if (currentUser?.role == UserRole.CHILD) {
                    if (currentLang == "HE") "בנק המשימות והמטלות הפנויות 📋" else "Available Task & Chore Bank 📋"
                } else {
                    if (currentLang == "HE") "כל המטלות הפעילות" else "All Active Chores"
                }
                Text(
                    text = activeChoresHeader, 
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                val context = org.jetbrains.annotations.Nullable::class.java.classLoader?.let { LocalContext.current } ?: LocalContext.current
                if (simpleTasks.isEmpty() && parentTasks.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Celebration, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                            val emptyText = if (currentUser?.role == UserRole.CHILD) {
                                if (currentLang == "HE") "אין משימות פנויות בבנק כרגע!" else "No available tasks in the bank right now!"
                            } else {
                                Localization.get("no_active_chores", currentLang)
                            }
                            Text(emptyText, color = Color.Gray)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp).weight(1f)
                    ) {
                        // 1. Simple Tasks Group (Partitioned visually for maximum simple clarity)
                        val unassignedSimple = simpleTasks.filter { it.assignedToUserId == null }
                        val assignedSimple = simpleTasks.filter { it.assignedToUserId != null }

                        if (unassignedSimple.isNotEmpty()) {
                            item {
                                Text(
                                    text = if (currentLang == "HE") "בנק המשימות הפנויות לשאיבה (ללא שיוך) 📥" else "Unassigned Task Bank (Claimable) 📥",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(unassignedSimple) { task ->
                                val assignee = users.find { it.id == task.assignedToUserId }
                                ChoreItem(
                                    chore = task,
                                    currentUser = currentUser,
                                    currentUserRole = currentUserRole,
                                    assignee = assignee,
                                    showAssigneeTag = currentUser?.role == UserRole.ADULT,
                                    languageCode = currentLang,
                                    viewModel = viewModel,
                                    onComplete = { viewModel.completeTask(task) },
                                    onDelete = { viewModel.deleteTask(task) },
                                    onEdit = {
                                        editingChore = task
                                        showAddChoreDialog = true
                                    },
                                    onClaim = null,
                                    onOpenChecklist = { viewModel.openChecklist(task) }
                                )
                            }
                        }

                        if (assignedSimple.isNotEmpty()) {
                            item {
                                Text(
                                    text = if (currentLang == "HE") "משימות ומטלות פעילות משויכות 👥" else "Assigned Active Tasks 👥",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                )
                            }
                            items(assignedSimple) { task ->
                                val assignee = users.find { it.id == task.assignedToUserId }
                                ChoreItem(
                                    chore = task,
                                    currentUser = currentUser,
                                    currentUserRole = currentUserRole,
                                    assignee = assignee,
                                    showAssigneeTag = currentUser?.role == UserRole.ADULT,
                                    languageCode = currentLang,
                                    viewModel = viewModel,
                                    onComplete = { viewModel.completeTask(task) },
                                    onDelete = { viewModel.deleteTask(task) },
                                    onEdit = {
                                        editingChore = task
                                        showAddChoreDialog = true
                                    },
                                    onClaim = {
                                        // Simple unassign back to the bank
                                        viewModel.updateTask(task.copy(assignedToUserId = null))
                                    },
                                    onOpenChecklist = { viewModel.openChecklist(task) }
                                )
                            }
                        }
                        
                        // 2. Parent-Child Recurring Categories
                        if (parentTasks.isNotEmpty()) {
                            item {
                                Text(
                                    text = if (currentLang == "HE") "קטגוריות משימה מחזוריות 🔁" else "Recurring Task Categories 🔁",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                                )
                            }
                            
                            parentTasks.forEach { parent ->
                                val isExpanded = expandedParentIds[parent.id] ?: false
                                item {
                                    val recurrenceText = formatRecurrenceRule(parent.recurrenceRule, currentLang)
                                    val nextDateStr = viewModel.getNextRecurrenceDateStr(parent)
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedParentIds[parent.id] = !isExpanded },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Autorenew,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = parent.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.ExpandMore,
                                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .rotate(if (isExpanded) 180f else 0f)
                                                )
                                            }
                                            if (parent.description.isNotEmpty()) {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = parent.description,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val nextRecurrenceLabel = if (currentLang == "HE") {
                                                    "מחזוריות: $recurrenceText • מופע הבא: $nextDateStr"
                                                } else {
                                                    "Recurrence: $recurrenceText • Next: $nextDateStr"
                                                }
                                                Text(
                                                    text = nextRecurrenceLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                                
                                                val allowEditDelete = currentUserRole?.allowCreateTasks == true || currentUserRole?.isManager == true
                                                if (allowEditDelete) {
                                                    Row {
                                                        IconButton(
                                                            onClick = {
                                                                editingChore = parent
                                                                showAddChoreDialog = true
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Edit,
                                                                contentDescription = "Edit Parent",
                                                                tint = MaterialTheme.colorScheme.secondary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { viewModel.deleteTask(parent) },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = "Delete Parent",
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                val childrenOfParent = allBankItems.filter { child ->
                                    child.parentId == parent.id &&
                                    (
                                        (showIncomplete && child.status == TaskStatus.TODO) ||
                                        (showCompleted && child.status == TaskStatus.DONE) ||
                                        (child.status == TaskStatus.PENDING_APPROVAL)
                                    ) &&
                                    (!hideExpired || child.dueDate == null || child.dueDate >= todayStart)
                                }
                                
                                if (isExpanded) {
                                    if (childrenOfParent.isNotEmpty()) {
                                        items(childrenOfParent) { child ->
                                            val assignee = users.find { it.id == child.assignedToUserId }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 24.dp)
                                            ) {
                                                ChoreItem(
                                                    chore = child,
                                                    currentUser = currentUser,
                                                    currentUserRole = currentUserRole,
                                                    assignee = assignee,
                                                    showAssigneeTag = currentUser?.role == UserRole.ADULT,
                                                    languageCode = currentLang,
                                                    viewModel = viewModel,
                                                    onComplete = { viewModel.completeTask(child) },
                                                    onDelete = { viewModel.deleteTask(child) },
                                                    onEdit = {
                                                        editingChore = child
                                                        showAddChoreDialog = true
                                                    },
                                                    onClaim = null,
                                                    onOpenChecklist = { viewModel.openChecklist(child) }
                                                )
                                            }
                                        }
                                    } else {
                                        item {
                                            val noInstancesLabel = if (currentLang == "HE") {
                                                "אין משימות שלא עבר זמנן בקטגוריה זו הממתינות לביצוע."
                                            } else {
                                                "No active unexpired tasks in this category."
                                            }
                                            Text(
                                                text = noInstancesLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray,
                                                modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                                            )
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

                // Link to "Recently Approved Chores" Screen
                if (currentUser?.role == UserRole.ADULT && completedChores.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRecentlyApprovedScreen = true }
                            .testTag("recently_approved_chores_link"),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = Localization.get("recently_approved_chores", currentLang),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val countText = if (currentLang == "HE") {
                                        "סה\"כ: ${completedChores.size} מטלות (לחץ לצפייה)"
                                    } else {
                                        "Total: ${completedChores.size} chores (Click to view)"
                                    }
                                    Text(
                                        text = countText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Kid's recently completed/approved chores section (with recurrence indicator)
                if (currentUser?.role == UserRole.CHILD && completedChores.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    val completedHeader = if (currentLang == "HE") "מטלות שהשלמתי" else "My Completed Chores"
                    Text(completedHeader, style = MaterialTheme.typography.titleMedium, color = Color.Gray, fontWeight = FontWeight.Bold)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp).weight(1f, fill = false)) {
                        items(completedChores) { chore ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = chore.title,
                                            fontWeight = FontWeight.Bold,
                                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                                            color = Color.Gray
                                        )
                                        val completedStatusText = if (currentLang == "HE") "בוצע בהצלחה והורה אישר!" else "Successfully completed & approved!"
                                        Text("$completedStatusText • +${chore.rewardPoints} ${Localization.get("pts", currentLang)}", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                        
                                        val recurrenceText = formatRecurrenceRule(chore.recurrenceRule, currentLang)
                                        if (recurrenceText != null) {
                                            Spacer(Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = if (currentLang == "HE") "תיכנס לתוקף מחדש במחזוריות הבאה" else "Will enter into force again in the next cycle",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Completed",
                                        tint = Color(0xFF4CAF50), // Green
                                        modifier = Modifier.padding(horizontal = 12.dp).size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddChoreDialog) {
        val context = org.jetbrains.annotations.Nullable::class.java.classLoader?.let { LocalContext.current } ?: LocalContext.current
        val parentTask = if (editingChore?.parentId != null) {
            allBankItems.find { it.id == editingChore?.parentId }
        } else null
        AddChoreDialog(
            languageCode = currentLang,
            familyMembers = users,
            existingChore = editingChore,
            parentTask = parentTask,
            onDismiss = { 
                showAddChoreDialog = false
                editingChore = null
            },
            onConfirm = { title, points, kidId, recurrenceRule, isChore ->
                val choreToSave = editingChore
                if (choreToSave != null) {
                    viewModel.updateTask(
                        choreToSave.copy(
                            title = title,
                            rewardPoints = if (isChore) points else 0,
                            assignedToUserId = kidId,
                            recurrenceRule = recurrenceRule,
                            isChore = isChore,
                            isTemplate = false
                        )
                    )
                    val assignedUser = users.find { it.id == kidId }
                    if (assignedUser != null) {
                        val msg = if (currentLang == "HE") "המשימה עודכנה ושוייכה ל-${assignedUser.name} בהצלחה!" else "Task updated and assigned to ${assignedUser.name} successfully!"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        val msg = if (currentLang == "HE") "המשימה עודכנה בהצלחה!" else "Task updated successfully!"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    viewModel.addTask(
                        title = title,
                        isChore = isChore,
                        rewardPoints = if (isChore) points else 0,
                        assignedToUserId = kidId,
                        priority = Priority.MEDIUM,
                        recurrenceRule = recurrenceRule,
                        isTemplate = false
                    )
                    val assignedUser = users.find { it.id == kidId }
                    if (assignedUser != null) {
                        val msg = if (currentLang == "HE") "המשימה נוצרה ושוייכה ל-${assignedUser.name} בהצלחה!" else "Task created and assigned to ${assignedUser.name} successfully!"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        val msg = if (currentLang == "HE") "המשימה נוצרה בבנק המשותף!" else "Task created in the shared bank!"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                showAddChoreDialog = false
                editingChore = null
            }
        )
    }

    if (showAiTaskGenerator) {
        AITaskGeneratorDialog(
            currentLang = currentLang,
            users = users,
            viewModel = viewModel,
            onDismiss = { showAiTaskGenerator = false }
        )
    }
}

@Composable
fun LinkedChecklistDialog(
    task: Task,
    viewModel: TaskViewModel,
    onDismiss: () -> Unit
) {
    val languageCode by viewModel.language.collectAsStateWithLifecycle()
    val isHe = languageCode == "HE"
    
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    
    val checklistId = task.checkboxListId
    val listItemsFlow = remember(checklistId) { viewModel.getItemsForChecklist(checklistId) }
    val currentItems by listItemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val isManager = currentUserRole?.isManager == true || currentUserRole?.allowCreateTasks == true || currentUserRole?.allowCreateChecklists == true
    
    var newItemName by remember { mutableStateOf("") }
    var showLinkExistingDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isHe) "רשימת סימון מקושרת" else "Linked Checkbox List",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    if (checklistId == null) {
                        Text(
                            text = if (isHe) "משימה זו אינה מקושרת לרשימת סימון." else "This task is not linked to any checkbox list.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        
                        if (isManager) {
                            Button(
                                onClick = {
                                    viewModel.createChecklistForTask(
                                        task,
                                        if (isHe) "רשימת סימון: ${task.title}" else "List: ${task.title}"
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("create_checklist_for_task_button")
                            ) {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isHe) "צור רשימת סימון חדשה" else "Create New Checklist")
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            OutlinedButton(
                                onClick = { showLinkExistingDropdown = !showLinkExistingDropdown },
                                modifier = Modifier.fillMaxWidth().testTag("link_existing_checklist_button")
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (isHe) "קשר לרשימת סימון קיימת" else "Link Existing Checklist")
                            }
                            
                            if (showLinkExistingDropdown) {
                                Spacer(Modifier.height(8.dp))
                                val availableCats = allCategories.filter { it.taskId == null }
                                if (availableCats.isEmpty()) {
                                    Text(
                                        text = if (isHe) "אין רשימות סימון פנויות שאינן מקושרות למשימות אחרות." else "No available checklists not already linked.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.Red,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    Text(
                                        text = if (isHe) "בחר רשימה מהרשימה הבאה:" else "Choose a list below:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 120.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .padding(4.dp)
                                    ) {
                                        items(availableCats) { cat ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { 
                                                        viewModel.linkTaskWithCheckboxList(task, cat.id)
                                                        showLinkExistingDropdown = false
                                                    }
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.List, contentDescription = null, tint = Color.Gray)
                                                Spacer(Modifier.width(8.dp))
                                                Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = if (isHe) "רק מנהלים או בעלי הרשאה מתאימה יכולים לקשר רשימות סימון חדשות." else "Only managers or authorized members can configure checklist associations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    } else {
                        val associatedCategory = allCategories.find { it.id == checklistId }
                        val activeListName = associatedCategory?.name ?: (if (isHe) "רשימה סימון" else "Checkbox List")
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeListName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (isManager) {
                                IconButton(
                                    onClick = { viewModel.unlinkTaskFromCheckboxList(task) },
                                    modifier = Modifier.testTag("unlink_checklist_button")
                                ) {
                                    Icon(Icons.Default.LinkOff, contentDescription = if (isHe) "נתק רשימה" else "Unlink Checklist", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        
                        if (isManager) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextField(
                                    value = newItemName,
                                    onValueChange = { newItemName = it },
                                    label = { Text(if (isHe) "הוסף פריט לרשימה" else "Add list item") },
                                    modifier = Modifier.weight(1f).testTag("add_checklist_item_input"),
                                    singleLine = true
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (newItemName.isNotBlank()) {
                                            viewModel.addChecklistItem(checklistId, newItemName.trim())
                                            newItemName = ""
                                        }
                                    },
                                    modifier = Modifier.testTag("add_checklist_item_button")
                                ) {
                                    Text(if (isHe) "הוסף" else "Add")
                                }
                            }
                        }
                        
                        if (currentItems.isEmpty()) {
                            Text(
                                text = if (isHe) "הרשימה ריקה. השתמש בטופס למעלה כדי להוסיף פריטים." else "List is empty. Use the input field above to add items.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                    .padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(currentItems) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.toggleChecklistItem(item) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = item.isCompleted,
                                            onCheckedChange = { viewModel.toggleChecklistItem(item) },
                                            modifier = Modifier.testTag("checklist_item_checkbox_${item.id}")
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textDecoration = if (item.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                            color = if (item.isCompleted) Color.Gray else Color.Unspecified
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("dismiss_checklist_dialog_button")) {
                Text(if (isHe) "סגור" else "Close")
            }
        }
    )
}

@Composable
fun VerifyChoreItem(
    chore: Task, 
    assignee: User?,
    showAssigneeTag: Boolean,
    languageCode: String,
    onApprove: () -> Unit, 
    onReject: () -> Unit, 
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = if (showAssigneeTag) BorderStroke(1.5.dp, getAssigneeColor(assignee?.id).copy(alpha = 0.7f)) else null
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(chore.title, fontWeight = FontWeight.Bold)
                    val recurrenceText = formatRecurrenceRule(chore.recurrenceRule, languageCode)
                    if (recurrenceText != null) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = recurrenceText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text("${Localization.get("needs_verification", languageCode)} • ${chore.rewardPoints} ${Localization.get("points", languageCode)}", style = MaterialTheme.typography.labelMedium)
                        if (showAssigneeTag && assignee != null) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = getAssigneeColor(assignee.id).copy(alpha = 0.15f),
                                contentColor = getAssigneeColor(assignee.id),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = assignee.name,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    val isHe = languageCode == "HE"
                    if (chore.lastCompletedTimestamp != null && chore.lastCompletedTimestamp > 0L) {
                        val timeStr = try {
                            val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            formatter.format(java.util.Date(chore.lastCompletedTimestamp))
                        } catch (e: Exception) {
                            ""
                        }
                        if (timeStr.isNotEmpty()) {
                            val compText = if (isHe) "בוצע בשעה: $timeStr 🌤️" else "Completed at: $timeStr 🌤️"
                            Spacer(Modifier.height(4.dp))
                            Text(compText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = Localization.get("delete_task", languageCode), tint = MaterialTheme.colorScheme.error)
                    }
                    Icon(Icons.Default.Pending, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onReject) { Text(Localization.get("reject", languageCode), color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onApprove) { Text(Localization.get("verify_pay", languageCode)) }
            }
        }
    }
}

@Composable
fun ChoreItem(
    chore: Task, 
    currentUser: User?, 
    currentUserRole: FamilyRole?,
    assignee: User?,
    showAssigneeTag: Boolean,
    languageCode: String,
    viewModel: TaskViewModel,
    onComplete: () -> Unit, 
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onClaim: (() -> Unit)? = null,
    onOpenChecklist: () -> Unit
) {
    val isHe = languageCode == "HE"
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (showAssigneeTag) BorderStroke(1.5.dp, getAssigneeColor(assignee?.id).copy(alpha = 0.7f)) else null
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(chore.title, fontWeight = FontWeight.Bold)
                
                val recurrenceText = formatRecurrenceRule(chore.recurrenceRule, languageCode)
                if (recurrenceText != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = recurrenceText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (chore.isChore) {
                        val ptsLabel = Localization.get("points", languageCode)
                        Text(
                            text = "+${chore.rewardPoints} $ptsLabel",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (languageCode == "HE") "משימה סטנדרטית" else "Standard Task",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    if (chore.parentId != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            val badgeLabel = if (languageCode == "HE") {
                                if (chore.pulledForDate != null) "מופע ליום ${chore.pulledForDate} 🔁" else "מופע מחזורי 🔁"
                            } else {
                                if (chore.pulledForDate != null) "Occurrence for ${chore.pulledForDate} 🔁" else "Recurring Occurrence 🔁"
                            }
                            Text(
                                text = badgeLabel,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    if (showAssigneeTag && assignee != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = getAssigneeColor(assignee.id).copy(alpha = 0.15f),
                            contentColor = getAssigneeColor(assignee.id),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = assignee.name,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Checklist Status/Link Row in ChoreItem
                val listId = chore.checkboxListId
                if (listId != null) {
                    val itemsFlow = remember(listId) { viewModel.getItemsForChecklist(listId) }
                    val items by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
                    val total = items.size
                    val completed = items.count { it.isCompleted }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable { onOpenChecklist() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAddCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isHe) "רשימת סימון ($completed/$total)" else "Checklist ($completed/$total)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                } else {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onOpenChecklist() }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isHe) "חבר רשימת סימון..." else "Link checklist...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Edit chore button
                val allowEdit = currentUserRole?.allowCreateTasks == true || currentUserRole?.isManager == true
                if (allowEdit) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = if (languageCode == "HE") "ערוך משימה" else "Edit Task",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Delete chore card
                val allowDelete = currentUserRole?.allowDeleteTasks == true || currentUserRole?.isManager == true
                if (allowDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = Localization.get("delete_task", languageCode), tint = MaterialTheme.colorScheme.error)
                    }
                }
                
                if (chore.assignedToUserId == null && onClaim != null) {
                    Button(onClick = onClaim) {
                        Text(if (languageCode == "HE") "קח אליי 📌" else "Claim 📌")
                    }
                } else {
                    Button(onClick = onComplete, enabled = chore.status != TaskStatus.DONE) {
                        Text(if (chore.status == TaskStatus.DONE) Localization.get("done", languageCode) else Localization.get("finish", languageCode))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilySettingsScreen(viewModel: TaskViewModel) {
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val allFamilyRoles by viewModel.allFamilyRoles.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentLang by viewModel.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showResetPromptDialog by remember { mutableStateOf(false) }
    var resetFamilyNameInput by remember { mutableStateOf("") }
    var resetManagerNameInput by remember { mutableStateOf("") }
    val currentUserIsAdmin = currentUserRole?.isManager == true || currentUser?.role == UserRole.ADULT

    val managerTitleText = if (currentLang == "HE") "מנהל מרכז המשפחה" else "Family Hub Manager"
    val managerSubtitleText = if (currentLang == "HE") "נהלו הרשאות ותפקידים קבוצתיים במשפחה" else "Manage permissions and family group roles"
    val addMemberDesc = if (currentLang == "HE") "הוספת בן משפחה" else "Add Family Member"
    val noUsersFoundText = if (currentLang == "HE") "לא נמצאו משתמשים" else "No users found"
    val deleteUserDesc = if (currentLang == "HE") "מחיקת משתמש" else "Delete User"
    val rolesAndPermsText = if (currentLang == "HE") "תפקידים והרשאות" else "Roles & Permissions"
    val adminDescText = if (currentLang == "HE") "למנהלי המשפחה יש הרשאות ניהול מלאות (יצירה, מחיקה وتצוגה מלאה)." else "Family Managers have complete administrative permissions (Create, Delete, and Full View of all tasks)."
    
    val allowCreateText = if (currentLang == "HE") "אפשר יצירת משימות ומטלות" else "Allow Creating Tasks & Chores"
    val allowDeleteText = if (currentLang == "HE") "אפשר מחיקת משימות ומטלות" else "Allow Deleting Tasks & Chores"
    val allowSeeOthersText = if (currentLang == "HE") "אפשר לראות משימות של חברים אחרים" else "Allow Seeing Other Members' Tasks"
    val allowCreateChecklistsText = if (currentLang == "HE") "אפשר יצירת רשימות סינון" else "Allow Creating Checklists"

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(managerTitleText, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(managerSubtitleText, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.selectUser(null) },
                    modifier = Modifier.testTag("switch_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwitchAccount,
                        contentDescription = if (currentLang == "HE") "החלף פרופיל" else "Switch Profile",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (currentUserIsAdmin) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { showAddMemberDialog = true },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = addMemberDesc)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        val activeFamilyId by viewModel.familyId.collectAsStateWithLifecycle()
        val familyCodeOnly = activeFamilyId.substringAfter("family_")
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentLang == "HE") "מזהה משפחה ייחודי" else "Unique Family ID",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (currentLang == "HE") "שתפו את הקוד עם בני המשפחה להתחברות" else "Share this code with your family members to join",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = familyCodeOnly,
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(16.dp))
                Button(
                    onClick = {
                        val annotatedText = androidx.compose.ui.text.AnnotatedString(familyCodeOnly)
                        clipboardManager.setText(annotatedText)
                        android.widget.Toast.makeText(
                            context,
                            if (currentLang == "HE") "מזהה המשפחה הועתק לפנקס!" else "Family ID copied to clipboard!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy ID",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (currentLang == "HE") "העתק" else "Copy")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (users.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(noUsersFoundText, color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(users) { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                UserAvatar(
                                    user = user,
                                    size = 40.dp,
                                    backgroundColor = if (user.role == UserRole.ADULT) MaterialTheme.colorScheme.primaryContainer 
                                                     else MaterialTheme.colorScheme.secondaryContainer,
                                    textColor = if (user.role == UserRole.ADULT) MaterialTheme.colorScheme.onPrimaryContainer 
                                                else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(user.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    val userRole = allFamilyRoles.find { it.id == user.roleId }
                                    val isUserManager = userRole?.isManager == true || user.roleId.endsWith("MANAGER")
                                    val roleText = if (isUserManager) {
                                        if (currentLang == "HE") "מנהל / מארגן" else "Manager / Organizer"
                                    } else {
                                        if (user.role == UserRole.ADULT) {
                                            if (currentLang == "HE") "מבוגר / הורה" else "Adult / Parent"
                                        } else {
                                            if (currentLang == "HE") "ילד / ילדה" else "Kid / Child"
                                        }
                                    }
                                    Text(
                                        text = roleText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    
                                    val personalCode = "${familyCodeOnly}-${user.id}"
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                            .clickable {
                                                val annotatedText = androidx.compose.ui.text.AnnotatedString(personalCode)
                                                clipboardManager.setText(annotatedText)
                                                android.widget.Toast.makeText(
                                                    context,
                                                    if (currentLang == "HE") "קוד גישה אישי הועתק לפנקס!" else "Personal code copied!",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = "Personal Code",
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = if (currentLang == "HE") "קוד אישי: $personalCode" else "Personal Code: $personalCode",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                // Prevent self-deletion and restrict to managers/adults
                                if (user.id != currentUser?.id && currentUserIsAdmin) {
                                    IconButton(onClick = { viewModel.deleteUser(user) }) {
                                        Icon(Icons.Default.Delete, contentDescription = deleteUserDesc, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))

                            Text(rolesAndPermsText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(8.dp))

                            val userRole = allFamilyRoles.find { it.id == user.roleId }
                            val isUserRoleManager = userRole?.isManager == true || user.roleId.endsWith("MANAGER")
                            if (isUserRoleManager) {
                                Text(
                                    text = adminDescText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            } else {
                                val canEditPermissions = currentUserRole?.isManager == true || currentUser?.role == UserRole.ADULT
                                PermissionToggleRow(
                                    label = allowCreateText,
                                    checked = userRole?.allowCreateTasks == true,
                                    enabled = canEditPermissions,
                                    onCheckedChange = { viewModel.updateRolePermissions(user.roleId, allowDelete = userRole?.allowDeleteTasks == true, allowCreate = it, allowSeeOthers = userRole?.allowSeeOtherTasks == true, allowCreateChecklists = userRole?.allowCreateChecklists == true) }
                                )
                                PermissionToggleRow(
                                    label = allowDeleteText,
                                    checked = userRole?.allowDeleteTasks == true,
                                    enabled = canEditPermissions,
                                    onCheckedChange = { viewModel.updateRolePermissions(user.roleId, allowDelete = it, allowCreate = userRole?.allowCreateTasks == true, allowSeeOthers = userRole?.allowSeeOtherTasks == true, allowCreateChecklists = userRole?.allowCreateChecklists == true) }
                                )
                                PermissionToggleRow(
                                    label = allowSeeOthersText,
                                    checked = userRole?.allowSeeOtherTasks == true,
                                    enabled = canEditPermissions,
                                    onCheckedChange = { viewModel.updateRolePermissions(user.roleId, allowDelete = userRole?.allowDeleteTasks == true, allowCreate = userRole?.allowCreateTasks == true, allowSeeOthers = it, allowCreateChecklists = userRole?.allowCreateChecklists == true) }
                                )
                                PermissionToggleRow(
                                    label = allowCreateChecklistsText,
                                    checked = userRole?.allowCreateChecklists == true,
                                    enabled = canEditPermissions,
                                    onCheckedChange = { viewModel.updateRolePermissions(user.roleId, allowDelete = userRole?.allowDeleteTasks == true, allowCreate = userRole?.allowCreateTasks == true, allowSeeOthers = userRole?.allowSeeOtherTasks == true, allowCreateChecklists = it) }
                                )
                            }
                        }
                    }
                }

                item {
                    val resetTitle = if (currentLang == "HE") "אזור מחיקה (אזור מפתח)" else "Danger Zone (Developer Area)"
                    val resetDesc = if (currentLang == "HE") "ניקוי ואיפוס מוחלט של כל המידע המקומי, כל המשפחות, הארנקים ורשימות הקניות, וחזרה למצב נקי." else "Clean and completely reset all local database records, families, balances, and shopping lists to start fresh."
                    val resetButtonText = if (currentLang == "HE") "איפוס מלא של האפליקציה" else "Full App Reset"
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("developer_reset_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(resetTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(resetDesc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (currentUserIsAdmin) {
                                        resetFamilyNameInput = ""
                                        resetManagerNameInput = if (currentLang == "HE") "אבא" else "Dad"
                                        showResetPromptDialog = true
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            if (currentLang == "HE") "שגיאה: רק מנהל משפחה או מבוגר רשאי לאפס את האפליקציה!" else "Error: Only a Family Manager or Adult is allowed to reset the app!",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().testTag("confirm_reset_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(resetButtonText)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddMemberDialog) {
        AddMemberDialog(
            languageCode = currentLang,
            onDismiss = { showAddMemberDialog = false },
            onAdd = { name, role ->
                viewModel.inviteMember(name, role)
                showAddMemberDialog = false
            }
        )
    }

    if (showResetPromptDialog) {
        AlertDialog(
            onDismissRequest = { showResetPromptDialog = false },
            title = {
                Text(
                    text = if (currentLang == "HE") "איפוס מלא של האפליקציה" else "Full App Reset",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (currentLang == "HE") 
                            "פעולה זו תמחק לחלוטין את כל המידע המקומי. אנא הזן את שם המשפחה וההורה המנהל החדשים ליצירת המרחב המשפחתי החדש:" 
                            else "This action will permanently erase all local records. Please enter the new family name and manager name to seed the fresh family space:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = resetFamilyNameInput,
                        onValueChange = { resetFamilyNameInput = it },
                        label = { Text(if (currentLang == "HE") "שם משפחה (למשל: כהן)" else "Family Name (e.g. Smiths)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = resetManagerNameInput,
                        onValueChange = { resetManagerNameInput = it },
                        label = { Text(if (currentLang == "HE") "שם מנהל/הורה (למשל: אבא)" else "Manager Name (e.g. Dad)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resetFamilyNameInput.isNotBlank() && resetManagerNameInput.isNotBlank()) {
                            viewModel.resetAllLocalData(resetFamilyNameInput, resetManagerNameInput) {
                                showResetPromptDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = resetFamilyNameInput.isNotBlank() && resetManagerNameInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (currentLang == "HE") "אשר ואפס הכל" else "Confirm & Reset All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPromptDialog = false }) {
                    Text(if (currentLang == "HE") "ביטול" else "Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun PermissionToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (enabled) Color.Unspecified else Color.Gray)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberDialog(
    languageCode: String,
    onDismiss: () -> Unit,
    onAdd: (String, UserRole) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.CHILD) }

    val inviteTitleText = if (languageCode == "HE") "הזמן בן משפחה" else "Invite Family Member"
    val nameLabelText = if (languageCode == "HE") "שם" else "Name"
    val roleLabelText = if (languageCode == "HE") "תפקיד" else "Role"
    val kidLabelText = if (languageCode == "HE") "ילד / ילדה" else "Kid / Child"
    val adultLabelText = if (languageCode == "HE") "מבוגר / הורה" else "Adult / Manager"
    val inviteText = if (languageCode == "HE") "הזמן" else "Invite"
    val cancelText = if (languageCode == "HE") "ביטול" else "Cancel"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(inviteTitleText) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(nameLabelText) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                
                Text(roleLabelText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == UserRole.CHILD,
                        onClick = { 
                            role = UserRole.CHILD
                        },
                        label = { Text(kidLabelText) }
                    )
                    FilterChip(
                        selected = role == UserRole.ADULT,
                        onClick = { 
                            role = UserRole.ADULT
                        },
                        label = { Text(adultLabelText) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onAdd(name, role) },
                enabled = name.isNotBlank()
            ) {
                Text(inviteText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelText) }
        }
    )
}

@Composable
fun PriorityHeader(priority: Priority, languageCode: String) {
    val color = when (priority) {
        Priority.URGENT -> PriorityUrgent
        Priority.HIGH -> PriorityHigh
        Priority.MEDIUM -> PriorityMedium
        Priority.LOW -> PriorityLow
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(Localization.getPriorityTranslation(priority.name, languageCode), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    val items by viewModel.rewardItems.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val currentLang by viewModel.language.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<RewardItem?>(null) }
    var deletingItem by remember { mutableStateOf<RewardItem?>(null) }

    val canManageStore = currentUserRole?.isManager == true || currentUser?.role == UserRole.ADULT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.get("virtual_store", currentLang), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentUser != null) {
                        Surface(color = Accent, shape = CircleShape) {
                            val pointsLabel = if (currentLang == "HE") "נקודות" else "Points"
                            Text("${currentUser?.balance} $pointsLabel", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (canManageStore) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (currentLang == "HE") "הוסף מוצר" else "Add Product"
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val storeDesc = if (currentLang == "HE") "ממשו את הנקודות שצברתם עבור הטבות נפלאות!" else "Redeem your earnings for awesome rewards!"
            Text(storeDesc, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            
            Spacer(Modifier.height(16.dp))

            val uniqueItems = remember(items) {
                items.distinctBy { it.remoteId?.ifBlank { null } ?: it.id.toString() }
            }
 
            LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(uniqueItems) { item ->
                    StoreItemCard(
                        item = item,
                        languageCode = currentLang,
                        canAfford = (currentUser?.balance ?: 0) >= item.price,
                        canManageStore = canManageStore,
                        onEdit = { editingItem = item },
                        onDelete = { deletingItem = item },
                        onPurchase = {
                            currentUser?.let { user -> viewModel.purchaseReward(user.id, item, currentLang) }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditRewardItemDialog(
            item = null,
            languageCode = currentLang,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, price, imgUrl ->
                viewModel.addRewardItem(RewardItem(title = title, price = price, imageUrl = imgUrl))
                showAddDialog = false
            }
        )
    }

    editingItem?.let { item ->
        AddEditRewardItemDialog(
            item = item,
            languageCode = currentLang,
            onDismiss = { editingItem = null },
            onConfirm = { title, price, imgUrl ->
                viewModel.updateRewardItem(item.copy(title = title, price = price, imageUrl = imgUrl))
                editingItem = null
            }
        )
    }

    deletingItem?.let { item ->
        ConfirmDeleteRewardDialog(
            item = item,
            languageCode = currentLang,
            onDismiss = { deletingItem = null },
            onConfirm = {
                viewModel.deleteRewardItem(item)
                deletingItem = null
            }
        )
    }
}

@Composable
fun ConfirmDeleteRewardDialog(
    item: RewardItem,
    languageCode: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val title = if (languageCode == "HE") "מחיקת מוצר" else "Delete Product"
    val text = if (languageCode == "HE") "האם אתה בטוח שברצונך למחוק את '${item.title}' מהחנות?" else "Are you sure you want to delete '${item.title}' from the store?"
    val confirmText = if (languageCode == "HE") "מחק" else "Delete"
    val cancelText = if (languageCode == "HE") "ביטול" else "Cancel"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText)
            }
        }
    )
}

data class RewardImageOption(
    val titleHe: String,
    val titleEn: String,
    val url: String
)

val rewardImageOptions = listOf(
    RewardImageOption("זמן מסך", "Screen Time", "https://images.unsplash.com/photo-1542751371-adc38448a05e?q=80&w=300&auto=format&fit=crop"),
    RewardImageOption("ממתקים", "Sweets", "https://images.unsplash.com/photo-1501443715934-6271f24f5cf9?q=80&w=300&auto=format&fit=crop"),
    RewardImageOption("צעצוע", "Toy", "https://images.unsplash.com/photo-1515488042361-404e9250afef?q=80&w=300&auto=format&fit=crop"),
    RewardImageOption("טיול / בילוי", "Outing", "https://images.unsplash.com/photo-1501555088652-021faa106b9b?q=80&w=300&auto=format&fit=crop"),
    RewardImageOption("סרט קולנוע", "Movie", "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?q=80&w=300&auto=format&fit=crop"),
    RewardImageOption("ספר קריאה", "Book", "https://images.unsplash.com/photo-1512820790803-83ca734da794?q=80&w=300&auto=format&fit=crop"),
    RewardImageOption("פיצה / מזון", "Pizza / Food", "https://images.unsplash.com/photo-1513104890138-7c749659a591?q=80&w=300&auto=format&fit=crop"),
    RewardImageOption("מתנה / כסף", "Gift", "https://images.unsplash.com/photo-1549465220-1a8b9238cd48?q=80&w=300&auto=format&fit=crop")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRewardItemDialog(
    item: RewardItem? = null,
    languageCode: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String?) -> Unit
) {
    var title by remember { mutableStateOf(item?.title ?: "") }
    var priceStr by remember { mutableStateOf(item?.price?.toString() ?: "50") }
    var imageUrl by remember { mutableStateOf(item?.imageUrl ?: "") }
    val isEdit = item != null

    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val base64 = compressUriToBase64(context, uri)
            if (base64 != null) {
                imageUrl = base64
            } else {
                imageUrl = uri.toString()
            }
        }
    }

    val dialogTitle = if (languageCode == "HE") {
        if (isEdit) "עריכת מוצר בחנות" else "הוספת מוצר חדש לחנות"
    } else {
        if (isEdit) "Edit Reward Product" else "Add New Reward Product"
    }

    val labelTitle = if (languageCode == "HE") "שם המוצר/הטבה" else "Product/Reward Name"
    val labelPrice = if (languageCode == "HE") "מחיר בנקודות" else "Price in Points"
    val labelGallery = if (languageCode == "HE") "בחר תמונה מוכנה מהגלריה:" else "Choose a preset image:"
    val labelCustomImage = if (languageCode == "HE") "כתובת תמונה מותאמת אישית (URL):" else "Custom Image URL:"
    val labelPreview = if (languageCode == "HE") "תצוגה מקדימה של התמונה:" else "Image Preview:"
    val confirmBtnText = if (languageCode == "HE") "אישור" else "Confirm"
    val cancelBtnText = if (languageCode == "HE") "ביטול" else "Cancel"
    val selectPhotoBtnText = if (languageCode == "HE") "בחר תמונה" else "Choose Image"
    val removePhotoBtnText = if (languageCode == "HE") "הסר תמונה" else "Remove Image"

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(labelTitle) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text(labelPrice) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )

                Text(labelGallery, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(rewardImageOptions) { option ->
                        val isSelected = imageUrl == option.url
                        val optionName = if (languageCode == "HE") option.titleHe else option.titleEn
                        Card(
                            modifier = Modifier
                                .width(90.dp)
                                .clickable { imageUrl = option.url },
                            border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
                                AsyncImage(
                                    model = option.url,
                                    contentDescription = optionName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(55.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    text = optionName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 4.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                val isHe = languageCode == "HE"
                val isPreset = rewardImageOptions.any { it.url == imageUrl }
                val isCustomPhoto = imageUrl.isNotEmpty() && !isPreset

                Text(labelCustomImage, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)

                if (isCustomPhoto) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = getCoilModel(imageUrl),
                                    contentDescription = "Selected Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isHe) "נבחרה תמונה אישית" else "Custom photo selected",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = if (isHe) "שנה תמונה" else "Change Image",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                            IconButton(
                                onClick = { imageUrl = "" },
                                modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = removePhotoBtnText,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                } else {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { imagePickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = selectPhotoBtnText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isHe) "לחצו לבחירת תמונה מגלריית המכשיר" else "Click to choose custom photo from gallery",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceStr.toIntOrNull() ?: 0
                    if (title.isNotBlank() && price > 0) {
                        onConfirm(title, price, imageUrl.ifBlank { null })
                    }
                }
            ) {
                Text(confirmBtnText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelBtnText)
            }
        }
    )
}

@Composable
fun StoreItemCard(
    item: RewardItem,
    languageCode: String,
    canAfford: Boolean,
    canManageStore: Boolean = false,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onPurchase: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (!item.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = getCoilModel(item.imageUrl),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Star, contentDescription = null, Modifier.size(48.dp), tint = Accent)
                    }
                }
                if (canManageStore) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit product", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete product", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${item.price} ${Localization.get("pts", languageCode)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onPurchase, enabled = canAfford, modifier = Modifier.fillMaxWidth()) {
                    Text(Localization.get("redeem", languageCode))
                }
            }
        }
    }
}

@Composable
fun UserChip(user: User, isSelected: Boolean, languageCode: String = "EN", onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (isSelected) 8.dp else 0.dp,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            UserAvatar(
                user = user,
                size = 40.dp,
                backgroundColor = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
                textColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
            )
            Text(user.name, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else Color.Unspecified)
            Text("${user.balance} ${Localization.get("pts", languageCode)}", style = MaterialTheme.typography.labelSmall, color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color.Unspecified)
        }
    }
}

@Composable
fun AddTaskDialog(languageCode: String, onDismiss: () -> Unit, onAdd: (String, Priority, TaskSource) -> Unit) {
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Localization.get("new_task", languageCode)) },
        text = {
            Column {
                TextField(value = title, onValueChange = { title = it }, label = { Text(Localization.get("task_title", languageCode)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(Localization.get("priority", languageCode))
                Row {
                    Priority.values().forEach { p ->
                        val selected = priority == p
                        FilterChip(
                            selected = selected,
                            onClick = { priority = p },
                            label = { Text(Localization.getPriorityTranslation(p.name, languageCode)) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (title.isNotBlank()) onAdd(title, priority, TaskSource.MANUAL) }) {
                Text(Localization.get("add", languageCode))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Localization.get("cancel", languageCode)) }
        }
    )
}

fun formatRecurrenceRule(rule: String?, languageCode: String): String? {
    if (rule.isNullOrBlank()) return null
    val parts = rule.split(":")
    val type = parts.getOrNull(0) ?: return null
    val dayArg = parts.getOrNull(1)
    
    val isHe = languageCode == "HE"
    return when (type) {
        "DAILY" -> if (isHe) "מחזוריות: יומית" else "Recurrence: Daily"
        "WEEKLY" -> {
            val dayName = when (dayArg) {
                "1" -> if (isHe) "ראשון" else "Sunday"
                "2" -> if (isHe) "שני" else "Monday"
                "3" -> if (isHe) "שלישי" else "Tuesday"
                "4" -> if (isHe) "רביעי" else "Wednesday"
                "5" -> if (isHe) "חמישי" else "Thursday"
                "6" -> if (isHe) "שישי" else "Friday"
                "7" -> if (isHe) "שבת" else "Saturday"
                else -> dayArg ?: ""
            }
            if (isHe) "מחזוריות: שבועי ביום $dayName" else "Recurrence: Weekly on $dayName"
        }
        "MONTHLY" -> {
            if (isHe) "מחזוריות: חודשי ב-$dayArg לחודש" else "Recurrence: Monthly on day $dayArg"
        }
        else -> null
    }
}

@Composable
fun AddChoreDialog(
    languageCode: String,
    familyMembers: List<User>,
    existingChore: Task? = null,
    parentTask: Task? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, points: Int, kidId: Int?, recurrenceRule: String?, isChore: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(existingChore?.title ?: "") }
    var points by remember { mutableStateOf((existingChore?.rewardPoints ?: 10).toString()) }
    
    val inheritedKidId = parentTask?.assignedToUserId
    val isKidInheritedAndLocked = inheritedKidId != null
    var selectedKidId by remember { mutableStateOf<Int?>(inheritedKidId ?: existingChore?.assignedToUserId) }
    var isChoreSelected by remember { mutableStateOf(existingChore?.isChore ?: true) }

    val existingRule = existingChore?.recurrenceRule
    val initialType = when {
        existingRule == null -> "NONE"
        existingRule.startsWith("DAILY") -> "DAILY"
        existingRule.startsWith("WEEKLY") -> "WEEKLY"
        existingRule.startsWith("MONTHLY") -> "MONTHLY"
        else -> "NONE"
    }
    val initialDay = when {
        existingRule != null && existingRule.contains(":") -> existingRule.split(":").getOrNull(1) ?: "1"
        else -> "1"
    }

    var recurrenceType by remember { mutableStateOf(initialType) }
    var selectedDayOfWeek by remember { mutableStateOf(if (initialType == "WEEKLY") initialDay else "1") }
    var selectedDayOfMonth by remember { mutableStateOf(if (initialType == "MONTHLY") initialDay else "1") }

    val isHe = languageCode == "HE"
    val dialogTitle = if (existingChore != null) {
        if (isHe) "עדכון משימה/מטלה" else "Update Task/Chore"
    } else {
        if (isHe) "יצירת משימה/מטלה חדשה" else "New Task/Chore"
    }
    
    val confirmBtnText = if (existingChore != null) {
        if (isHe) "עדכן" else "Update"
    } else {
        if (isHe) "צור" else "Create"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // Choice: Chore (Reward) vs Standard Task
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isChoreSelected,
                        onClick = { isChoreSelected = true },
                        label = { Text(if (isHe) "מטלה (פרס)" else "Chore (Reward)") },
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = !isChoreSelected,
                        onClick = { isChoreSelected = false },
                        label = { Text(if (isHe) "משימה" else "Standard Task") },
                        leadingIcon = { Icon(Icons.Default.Task, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                TextField(
                    value = title, 
                    onValueChange = { title = it }, 
                    label = { Text(if (isChoreSelected) Localization.get("chore_title", languageCode) else (if (isHe) "שם המשימה" else "Task Title")) }, 
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                
                if (isChoreSelected) {
                    TextField(
                        value = points, 
                        onValueChange = { if (it.all { char -> char.isDigit() }) points = it }, 
                        label = { Text(Localization.get("reward_points", languageCode)) }, 
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(12.dp))

                // Assign to row
                Text(
                    text = Localization.get("assign_to", languageCode),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (isKidInheritedAndLocked) {
                    val inheritedMemberName = familyMembers.find { it.id == inheritedKidId }?.name ?: "Unknown"
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isHe) {
                                "משויך אוטומטית ל-$inheritedMemberName דרך משימת האב (נעול)"
                            } else {
                                "Automatically assigned to $inheritedMemberName via parent task (locked)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    LazyRow(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedKidId == null,
                                onClick = { selectedKidId = null },
                                label = { Text(if (isHe) "בלתי משויך (בבנק)" else "Unassigned (Bank)") }
                            )
                        }
                        items(familyMembers) { member ->
                            FilterChip(
                                selected = selectedKidId == member.id,
                                onClick = { selectedKidId = member.id },
                                label = { Text(member.name) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                
                // Recurrence Header
                Text(
                    text = if (isHe) "מחזוריות משימה:" else "Task/Chore Recurrence:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                
                val recurrenceOptions = listOf(
                    "NONE" to (if (isHe) "ללא" else "None"),
                    "DAILY" to (if (isHe) "יומית" else "Daily"),
                    "WEEKLY" to (if (isHe) "שבועית" else "Weekly"),
                    "MONTHLY" to (if (isHe) "חודשית" else "Monthly")
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    recurrenceOptions.forEach { (typeKey, typeLabel) ->
                        FilterChip(
                            selected = recurrenceType == typeKey,
                            onClick = { recurrenceType = typeKey },
                            label = { Text(typeLabel) }
                        )
                    }
                }
                
                // Day Selectors
                if (recurrenceType == "WEEKLY") {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isHe) "בחר יום בשבוע:" else "Select Day of Week:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(4.dp))
                    val daysOfWeek = if (isHe) {
                        listOf("א'", "ב'", "ג'", "ד'", "ה'", "ו'", "ש'")
                    } else {
                        listOf("S", "M", "T", "W", "T", "F", "S")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        daysOfWeek.forEachIndexed { index, day ->
                            val dayValue = (index + 1).toString()
                            val isSel = selectedDayOfWeek == dayValue
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedDayOfWeek = dayValue },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                } else if (recurrenceType == "MONTHLY") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = selectedDayOfMonth,
                        onValueChange = { input ->
                            if (input.isEmpty()) {
                                selectedDayOfMonth = ""
                            } else {
                                val num = input.toIntOrNull()
                                  if (num != null && num in 1..31) {
                                      selectedDayOfMonth = num.toString()
                                  }
                            }
                        },
                        label = { Text(if (isHe) "יום בחודש (1-31)" else "Day of Month (1-31)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.width(140.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (title.isNotBlank()) {
                        val recurrenceRule = when (recurrenceType) {
                            "DAILY" -> "DAILY"
                            "WEEKLY" -> "WEEKLY:$selectedDayOfWeek"
                            "MONTHLY" -> "MONTHLY:${selectedDayOfMonth.ifBlank { "1" }}"
                            else -> null
                        }
                        onConfirm(
                            title, 
                            if (isChoreSelected) (points.toIntOrNull() ?: 10) else 0, 
                            selectedKidId, 
                            recurrenceRule, 
                            isChoreSelected
                        )
                    }
                }
            ) {
                Text(confirmBtnText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Localization.get("cancel", languageCode)) }
        }
    )
}

// ==========================================
// COMPREHENSIVE SETTINGS MODULE (BILINGUAL/M3)
// ==========================================

sealed class SettingsSubScreen {
    object Main : SettingsSubScreen()
    object Profile : SettingsSubScreen()
    object Permissions : SettingsSubScreen()
    object Reminders : SettingsSubScreen()
    object SharedLists : SettingsSubScreen()
}

@Composable
fun SettingsScreen(viewModel: TaskViewModel) {
    var activeSubScreen by remember { mutableStateOf<SettingsSubScreen>(SettingsSubScreen.Main) }
    val currentLang by viewModel.language.collectAsStateWithLifecycle()

    // Handle system physical/gesture back behaviors
    androidx.activity.compose.BackHandler(enabled = activeSubScreen != SettingsSubScreen.Main) {
        activeSubScreen = SettingsSubScreen.Main
    }

    when (activeSubScreen) {
        SettingsSubScreen.Main -> {
            SettingsMainDashboard(
                viewModel = viewModel,
                onNavigateTo = { sub -> activeSubScreen = sub }
            )
        }
        SettingsSubScreen.Profile -> {
            EditProfileSubScreen(
                viewModel = viewModel,
                onBack = { activeSubScreen = SettingsSubScreen.Main }
            )
        }
        SettingsSubScreen.Permissions -> {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeSubScreen = SettingsSubScreen.Main }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = if (currentLang == "HE") "חזרה" else "Back"
                        )
                    }
                    Text(
                        text = if (currentLang == "HE") "הרשאות וניהול משפחה" else "Family Management & Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
                Box(Modifier.weight(1f)) {
                    FamilySettingsScreen(viewModel)
                }
            }
        }
        SettingsSubScreen.Reminders -> {
            RemindersSubScreen(
                viewModel = viewModel,
                onBack = { activeSubScreen = SettingsSubScreen.Main }
            )
        }
        SettingsSubScreen.SharedLists -> {
            SharedListsSubScreen(
                viewModel = viewModel,
                onBack = { activeSubScreen = SettingsSubScreen.Main }
            )
        }
    }
}

@Composable
fun SharedListsSubScreen(viewModel: TaskViewModel, onBack: () -> Unit) {
    val currentLang by viewModel.language.collectAsStateWithLifecycle()
    val incomingProposals by viewModel.incomingProposals.collectAsStateWithLifecycle()
    val allChecklists by viewModel.allChecklists.collectAsStateWithLifecycle()
    val activeFamilyId by viewModel.familyId.collectAsStateWithLifecycle()
    val familyCodeOnly = activeFamilyId.substringAfter("family_")
    val context = LocalContext.current

    var selectedChecklistId by remember { mutableStateOf<Int?>(null) }
    var selectedChecklistName by remember { mutableStateOf("") }
    var targetFamilyInput by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    var sharedListLinkInput by remember { mutableStateOf("") }
    var isImportingList by remember { mutableStateOf(false) }

    // For selecting list: dropdown/menu
    var showListDropdown by remember { mutableStateOf(false) }

    val titleText = if (currentLang == "HE") "רשימות משותפות" else "Shared Lists"
    val sendSectionTitle = if (currentLang == "HE") "שלח רשימה משותפת ל" else "Send Shared List To"
    val familyCodePlaceholder = if (currentLang == "HE") "קוד משפחה מקבלת..." else "Receiving family code..."
    val chooseListText = if (currentLang == "HE") "בחר רשימה לשיתוף..." else "Select list to share..."
    val primaryListLabel = if (currentLang == "HE") "רשימת קניות ראשית (מקורית)" else "Primary Shopping List (Default)"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Back Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = if (currentLang == "HE") "חזרה" else "Back"
                )
            }
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Section 1: Send Shared List To
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("send_shared_list_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = sendSectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (currentLang == "HE") "שלחו רשימת קניות או סינון שלמה למשפחה אחרת באמצעות הזנת מזהה המשפחה שלה" 
                           else "Send a complete shopping or filter list to another family by entering their family ID",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))

                // Select List Box
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showListDropdown = true },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activeSelectionText = if (selectedChecklistId == null && selectedChecklistName.isEmpty()) {
                                chooseListText
                            } else if (selectedChecklistId == null) {
                                primaryListLabel
                            } else {
                                selectedChecklistName
                            }
                            Text(activeSelectionText)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = showListDropdown,
                        onDismissRequest = { showListDropdown = false }
                    ) {
                        // Option 1: Primary shopping list (id = null, name = "")
                        DropdownMenuItem(
                            text = { Text(primaryListLabel) },
                            onClick = {
                                selectedChecklistId = null
                                selectedChecklistName = ""
                                showListDropdown = false
                            }
                        )
                        // Option 2: All custom checklists
                        allChecklists.forEach { checklist ->
                            DropdownMenuItem(
                                text = { Text(checklist.name) },
                                onClick = {
                                    selectedChecklistId = checklist.id
                                    selectedChecklistName = checklist.name
                                    showListDropdown = false
                                }
                            )
                        }
                    }
                }

                // Family Code Input
                OutlinedTextField(
                    value = targetFamilyInput,
                    onValueChange = { targetFamilyInput = it },
                    placeholder = { Text(familyCodePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("receiver_family_code_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                // Send Button
                Button(
                    onClick = {
                        val activeName = if (selectedChecklistId == null) {
                            if (currentLang == "HE") "רשימת קניות ראשית" else "Primary Shopping List"
                        } else {
                            selectedChecklistName
                        }
                        isSending = true
                        viewModel.sendSharedListProposal(
                            checklistId = selectedChecklistId,
                            listTitle = activeName,
                            targetFamilyCode = targetFamilyInput,
                            onComplete = { success, resultName ->
                                isSending = false
                                if (success) {
                                    targetFamilyInput = ""
                                    android.widget.Toast.makeText(
                                        context,
                                        if (currentLang == "HE") "הרשימה '$resultName' נשלחה בהצלחה!" else "List '$resultName' sent successfully!",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        if (currentLang == "HE") "שגיאה בשליחת הרשימה: $resultName" else "Failed to send list: $resultName",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    },
                    enabled = targetFamilyInput.isNotBlank() && !isSending,
                    modifier = Modifier.fillMaxWidth().testTag("send_list_proposal_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (currentLang == "HE") "שלח רשימה משותפת" else "Send Shared List")
                    }
                }
            }
        }

        // Section 2: Received Proposals
        Text(
            text = if (currentLang == "HE") "רשימות משותפות שהתקבלו" else "Received Shared Lists",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        val pendingProposals = incomingProposals.filter { it.status == "PENDING" }

        if (pendingProposals.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("no_pending_proposals_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (currentLang == "HE") "אין רשימות משותפות שממתינות לאישור" else "No shared lists awaiting approval",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        } else {
            pendingProposals.forEach { proposal ->
                var replacePrimaryChecked by remember { mutableStateOf(proposal.isPrimaryShoppingList) }
                var isProcessing by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("proposal_card_${proposal.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = proposal.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "${if (currentLang == "HE") "משולח: משפחה" else "From family:"} ${proposal.senderFamilyId.substringAfter("family_")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }

                            // Reject Button
                            IconButton(
                                onClick = {
                                    viewModel.rejectSharedListProposal(proposal) { success ->
                                        if (success) {
                                            android.widget.Toast.makeText(
                                                context,
                                                if (currentLang == "HE") "ההצעה נדחתה והוסרה" else "Proposal rejected and removed",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !isProcessing
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Reject", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Switch/Checkbox to replace primary shopping list
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = replacePrimaryChecked,
                                onCheckedChange = { replacePrimaryChecked = it },
                                enabled = !isProcessing,
                                modifier = Modifier.testTag("replace_primary_checkbox_${proposal.id}")
                            )
                            Text(
                                text = if (currentLang == "HE") "החלף את רשימת הקניות הראשית של המשפחה ברשימה זו" 
                                       else "Replace the family's main shopping list with this list",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                isProcessing = true
                                viewModel.approveSharedListProposal(
                                    proposal = proposal,
                                    replacePrimary = replacePrimaryChecked,
                                    onComplete = { success, msg ->
                                        isProcessing = false
                                        if (success) {
                                            android.widget.Toast.makeText(
                                                context,
                                                if (currentLang == "HE") "הרשימה '$msg' יובאה ואושרה בהצלחה!" else "List '$msg' approved and imported successfully!",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                if (currentLang == "HE") "שגיאה באישור הרשימה: $msg" else "Error approving list: $msg",
                                                android.widget.Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                )
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.fillMaxWidth().testTag("approve_proposal_btn_${proposal.id}"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(if (currentLang == "HE") "אשר וייבא רשימה" else "Approve & Import List")
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Section 3: Import Shared List via Link (Relocated Here!)
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("link_import_proposal_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (currentLang == "HE") "ייבוא רשימה משותפת באמצעות קישור" else "Import Shared List via Link",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (currentLang == "HE") "הדבק את קישור השיתוף שקיבלת מחבר כדי לייבא עותק מלא של הרשימה למשפחה זו" 
                           else "Paste a shared list link to import a full copy of the list into this family group",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = sharedListLinkInput,
                        onValueChange = { sharedListLinkInput = it },
                        placeholder = { 
                            Text(
                                if (currentLang == "HE") "הדבק קישור שיתוף..." else "Paste share link here..."
                            ) 
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("shared_list_link_input_sub"),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Button(
                        onClick = {
                            if (sharedListLinkInput.isNotBlank()) {
                                isImportingList = true
                                viewModel.importSharedList(
                                    link = sharedListLinkInput,
                                    onSuccess = { importedName ->
                                        isImportingList = false
                                        sharedListLinkInput = ""
                                        android.widget.Toast.makeText(
                                            context,
                                            if (currentLang == "HE") "הרשימה '$importedName' יובאה בהצלחה!" else "List '$importedName' imported successfully!",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    onError = { errorMsg ->
                                        isImportingList = false
                                        android.widget.Toast.makeText(
                                            context,
                                            if (currentLang == "HE") "שגיאה בייבוא הרשימה: $errorMsg" else "Import failed: $errorMsg",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        },
                        enabled = sharedListLinkInput.isNotBlank() && !isImportingList,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("import_via_link_btn")
                    ) {
                        if (isImportingList) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(if (currentLang == "HE") "ייבא" else "Import")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsMainDashboard(
    viewModel: TaskViewModel,
    onNavigateTo: (SettingsSubScreen) -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentLang by viewModel.language.collectAsStateWithLifecycle()

    val titleText = if (currentLang == "HE") "הגדרות המשפחה והחשבון" else "Family & Account Settings"
    val subtitleText = if (currentLang == "HE") "נהלו את הפרופיל שלכם, הגדרות משפחתיות ותזכורות" else "Manage your profile, family settings and notifications"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Upper Title
        Text(
            text = titleText,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("settings_title")
        )
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // User Profile Summary Card
        currentUser?.let { user ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .testTag("settings_profile_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Avatar
                    UserAvatar(
                        user = user,
                        size = 64.dp,
                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        textColor = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val roleText = if (user.roleId.endsWith("MANAGER")) {
                            if (currentLang == "HE") "מנהל מרכז המשפחה" else "Family Organizer"
                        } else if (user.role == UserRole.ADULT) {
                            if (currentLang == "HE") "הורה מלווה" else "Parent"
                        } else {
                            if (currentLang == "HE") "בן משפחה" else "Child"
                        }
                        Text(
                            text = roleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        val phoneText = if (!user.phoneNumber.isNullOrBlank()) {
                            user.phoneNumber ?: ""
                        } else {
                            if (currentLang == "HE") "לא הוגדר טלפון" else "No phone number added"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = Color.Gray
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = phoneText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (currentLang == "HE") "יתרת הנקודות שלך: ${user.balance} נק׳ 🌟" else "Your balance: ${user.balance} pts 🌟",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = { onNavigateTo(SettingsSubScreen.Profile) },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .size(36.dp)
                            .testTag("settings_edit_profile_button"),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // List of configuration categories
        Text(
            text = if (currentLang == "HE") "הגדרות האפליקציה" else "App Adjustments",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Category 1: Family & Permissions
        SettingsCategoryItem(
            title = if (currentLang == "HE") "הרשאות וניהול משפחה" else "Family & Permissions",
            subtitle = if (currentLang == "HE") "ניהול תפקידים, הוספת חברים וצפייה במזהה המשפחה" else "Roles configuration, member invitations and group ID",
            icon = Icons.Default.SupervisorAccount,
            onClick = { onNavigateTo(SettingsSubScreen.Permissions) },
            testTag = "category_family_permissions"
        )

        // Category 2: Daily Reminders
        SettingsCategoryItem(
            title = if (currentLang == "HE") "תזכורות והתראות יומיות" else "Daily Reminder Alarms",
            subtitle = if (currentLang == "HE") "תזכורות בוקר לארגון המשימות ותזכורות ערב לסיומן" else "Morning organizing lists and evening task completion status",
            icon = Icons.Default.NotificationsActive,
            onClick = { onNavigateTo(SettingsSubScreen.Reminders) },
            testTag = "category_reminders"
        )

        // Category: Shared Lists
        SettingsCategoryItem(
            title = if (currentLang == "HE") "רשימות משותפות" else "Shared Lists",
            subtitle = if (currentLang == "HE") "שליחה וקבלה של רשימות קניות או סינון בין משפחות" else "Send and receive shopping or filter lists between families",
            icon = Icons.Default.Share,
            onClick = { onNavigateTo(SettingsSubScreen.SharedLists) },
            testTag = "category_shared_lists"
        )

        // Category 3: System Language
        val langName = if (currentLang == "HE") "עברית 🇮🇱" else "English 🇺🇸"
        SettingsCategoryItem(
            title = if (currentLang == "HE") "שינוי שפת ממשק" else "App Interface Language",
            subtitle = "${if (currentLang == "HE") "שפה פעילה:" else "Active:"} $langName",
            icon = Icons.Default.Language,
            onClick = { viewModel.toggleLanguage() },
            testTag = "category_language"
        )
    }
}

@Composable
fun SettingsCategoryItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun EditProfileSubScreen(
    viewModel: TaskViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentLang by viewModel.language.collectAsStateWithLifecycle()

    val profileToEdit = currentUser ?: return

    var name by remember { mutableStateOf(profileToEdit.name) }
    var phoneNumber by remember { mutableStateOf(profileToEdit.phoneNumber ?: "") }
    var avatarUrl by remember { mutableStateOf(profileToEdit.avatarUrl ?: "") }
    var passcode by remember { mutableStateOf(profileToEdit.passcode ?: "") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val base64 = compressUriToBase64(context, uri)
            if (base64 != null) {
                avatarUrl = base64
            } else {
                avatarUrl = uri.toString()
            }
        }
    }

    val presetEmojis = listOf("🦁", "🐼", "🐨", "🦊", "🦄", "🦖", "🐻", "🐱", "🐶", "🐰", "🐙", "🐳")

    val backLabel = if (currentLang == "HE") "חזרה" else "Back"
    val titleText = if (currentLang == "HE") "עדכון פרטים אישיים" else "Edit Profile Details"
    val saveLabel = if (currentLang == "HE") "שמור שינויים" else "Save Changes"
    val cancelLabel = if (currentLang == "HE") "ביטול" else "Cancel"
    val avatarTitle = if (currentLang == "HE") "בחרו סמל פנים (אוואטר)" else "Select Your Avatar Emoji"
    val customUrlLabel = if (currentLang == "HE") "או קישור חיצוני לתמונה" else "Or enter custom photo URL"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_button")) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = backLabel)
            }
            Spacer(Modifier.width(8.dp))
            Text(text = titleText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .align(Alignment.CenterHorizontally)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl.length == 1 || (avatarUrl.isNotEmpty() && avatarUrl.codePointCount(0, avatarUrl.length) == 1)) {
                Text(text = avatarUrl, style = MaterialTheme.typography.displayMedium)
            } else if (avatarUrl.isNotEmpty() && (avatarUrl.startsWith("http") || avatarUrl.startsWith("https") || avatarUrl.startsWith("content:") || avatarUrl.startsWith("file:") || avatarUrl.contains("/") || avatarUrl.startsWith("data:image/"))) {
                coil.compose.AsyncImage(
                    model = getCoilModel(avatarUrl),
                    contentDescription = "Profile Pic",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(if (currentLang == "HE") "שם מלא / כינוי" else "Full Name / Handle") },
            placeholder = { Text(if (currentLang == "HE") "למשל: יובל כהן" else "e.g., Jennifer") },
            modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text(if (currentLang == "HE") "מספר טלפון ליצירת קשר" else "Phone Number") },
            placeholder = { Text(if (currentLang == "HE") "למשל: 054-1234567" else "e.g., +1 555-0199") },
            modifier = Modifier.fillMaxWidth().testTag("profile_phone_input"),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = passcode,
            onValueChange = { input ->
                if (input.all { it.isDigit() } && input.length <= 4) {
                    passcode = input
                }
            },
            label = { Text(if (currentLang == "HE") "קוד גישה סודי (4 ספרות PIN)" else "Secret PIN (4 digits)") },
            placeholder = { Text(if (currentLang == "HE") "למשל: 1234 (השאירו ריק ללא קוד)" else "e.g., 1234 (blank for no PIN)") },
            modifier = Modifier.fillMaxWidth().testTag("profile_passcode_input"),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = avatarTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(presetEmojis) { emoji ->
                val isSelected = avatarUrl == emoji
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                        .clickable { avatarUrl = emoji }
                        .border(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        val isHe = currentLang == "HE"
        val uploadPhotoLabel = if (isHe) "העלאת תמונה מהמכשיר" else "Upload Photo from Device"
        val selectPhotoBtnText = if (isHe) "בחר תמונה" else "Choose Image"
        val removePhotoBtnText = if (isHe) "הסר תמונה" else "Remove Image"

        Spacer(Modifier.height(16.dp))
        Text(
            text = uploadPhotoLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val isCustomPhoto = avatarUrl.isNotEmpty() && !presetEmojis.contains(avatarUrl)

        if (isCustomPhoto) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        coil.compose.AsyncImage(
                            model = getCoilModel(avatarUrl),
                            contentDescription = "Selected Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHe) "נבחרה תמונה אישית" else "Custom photo selected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (isHe) "שנה תמונה" else "Change Image",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    IconButton(
                        onClick = { avatarUrl = "" },
                        modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = removePhotoBtnText,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        } else {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { imagePickerLauncher.launch("image/*") }
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = selectPhotoBtnText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isHe) "לחצו לבחירת קובץ תמונה מגלריית המכשיר שלכם" else "Click to choose an image file from your device folder",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(48.dp).testTag("profile_cancel_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = cancelLabel)
            }
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                         viewModel.updateUserProfile(
                             userId = profileToEdit.id,
                             name = name,
                             avatarUrl = avatarUrl.ifBlank { null },
                             phoneNumber = phoneNumber.ifBlank { null },
                             passcode = passcode.ifBlank { null }
                         )
                         onBack()
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp).testTag("profile_save_button"),
                shape = RoundedCornerShape(12.dp),
                enabled = name.isNotBlank()
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text = saveLabel)
            }
        }
    }
}

@Composable
fun RemindersSubScreen(
    viewModel: TaskViewModel,
    onBack: () -> Unit
) {
    val currentLang by viewModel.language.collectAsStateWithLifecycle()
    val dailyReminderEnabled by viewModel.dailyReminderEnabled.collectAsStateWithLifecycle()
    val planReminderEnabled by viewModel.planReminderEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val backLabel = if (currentLang == "HE") "חזרה" else "Back"
    val titleText = if (currentLang == "HE") "הגדרות תזכורות יומיות" else "Daily Notification Reminders"
    val subtitleText = if (currentLang == "HE") 
        "נהלו את ההתראות המתוזמנות על מנת להישאר מעודכנים במשימות" 
        else "Configure automated scheduling alarms to keep everyone updated"

    val headerMorning = if (currentLang == "HE") "1. תזכורת לארגון היום למשימות (בוקר)" else "1. Day-Planning Reminders (Morning)"
    val descMorning = if (currentLang == "HE") 
        "שלח תזכורת ידידותית בכל בוקר בשעה 08:00 לעבור על לוח המשימות ולתכנן את היום שלכם למוקד מוגדר וברור." 
        else "Send a morning update alert details daily at 8:00 AM to review chores list and organize actions."
    val toggleMorningLabel = if (currentLang == "HE") "אפשר תזכורת בוקר ב-08:00" else "Enable Morning Alert at 8:00 AM"
    val testMorningButtonLabel = if (currentLang == "HE") "שלח תזכורת ניסיון בוקר" else "Send Morning Test Alert"

    val headerEvening = if (currentLang == "HE") "2. תזכורת לסיום והשלמת משימות (ערב)" else "2. Task Finish Reminders (Evening)"
    val descEvening = if (currentLang == "HE") 
        "שלח תזכורת ידידותית בכל ערב בשעה 18:00 לכל בני המשפחה לסיים ולסמן את המטלות והמשימות שנותרו להם היום." 
        else "Send a gentle prompt daily at 6:00 PM to remind all members to complete outstanding tasks."
    val toggleEveningLabel = if (currentLang == "HE") "אפשר תזכורת ערב ב-18:00" else "Enable Evening Alert at 6:00 PM"
    val testEveningButtonLabel = if (currentLang == "HE") "שלח תזכורת ניסיון ערב" else "Send Evening Test Alert"

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            android.widget.Toast.makeText(
                context,
                if (currentLang == "HE") "יש לאשר התראות בהגדרות המערכת" else "Please enable notification permissions",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestPermissionIfNecessary: (Boolean) -> Boolean = { intendedEnable ->
        if (intendedEnable && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                false
            } else {
                true
            }
        } else {
            true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("reminders_back_button")) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = backLabel)
            }
            Spacer(Modifier.width(8.dp))
            Text(text = titleText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 24.dp)
        )

        // 1. Morning Planner Section Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("morning_reminder_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(text = headerMorning, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(text = descMorning, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(toggleMorningLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = planReminderEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (requestPermissionIfNecessary(true)) {
                                    viewModel.setPlanReminderEnabled(true, context)
                                }
                            } else {
                                viewModel.setPlanReminderEnabled(false, context)
                            }
                        },
                        modifier = Modifier.testTag("plan_reminder_switch")
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        if (requestPermissionIfNecessary(true)) {
                            val testIntent = android.content.Intent("com.example.ACTION_TEST_PLAN_REMINDER").apply {
                                setClass(context, com.example.ReminderReceiver::class.java)
                            }
                            context.sendBroadcast(testIntent)
                            android.widget.Toast.makeText(
                                context,
                                if (currentLang == "HE") "תזכורת ניסיון לתכנון בוקר נשלחה!" else "Morning test alert sent!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("send_test_morning_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(testMorningButtonLabel)
                }
            }
        }

        // 2. Evening Completion Section Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("evening_reminder_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(text = headerEvening, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(text = descEvening, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(toggleEveningLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = dailyReminderEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (requestPermissionIfNecessary(true)) {
                                    viewModel.setDailyReminderEnabled(true, context)
                                }
                            } else {
                                viewModel.setDailyReminderEnabled(false, context)
                            }
                        },
                        modifier = Modifier.testTag("daily_reminder_switch")
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        if (requestPermissionIfNecessary(true)) {
                            val testIntent = android.content.Intent("com.example.ACTION_TEST_REMINDER").apply {
                                setClass(context, com.example.ReminderReceiver::class.java)
                            }
                            context.sendBroadcast(testIntent)
                            android.widget.Toast.makeText(
                                context,
                                if (currentLang == "HE") "תזכורת ניסיון ערב נשלחה!" else "Evening test alert sent!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("send_test_evening_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(testEveningButtonLabel)
                }
            }
        }
    }
}

@Composable
fun UserAvatar(
    user: User,
    size: Dp,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    textColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Box(
        modifier = modifier
            .size(size)
            .background(backgroundColor, CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val avatar = user.avatarUrl ?: ""
        if (avatar.length == 1 || (avatar.isNotEmpty() && avatar.codePointCount(0, avatar.length) == 1)) {
            Text(
                text = avatar,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (size.value * 0.5f).sp
                )
            )
        } else if (avatar.isNotEmpty() && (avatar.startsWith("http") || avatar.startsWith("https") || avatar.startsWith("content:") || avatar.startsWith("file:") || avatar.contains("/") || avatar.startsWith("data:image/"))) {
            AsyncImage(
                model = getCoilModel(avatar),
                contentDescription = "${user.name}'s Avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = user.name.take(1).uppercase(),
                color = textColor,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.45f).sp
                )
            )
        }
    }
}

fun getCoilModel(url: String?): Any? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("data:image/") && url.contains("base64,")) {
        return try {
            val base64String = url.substringAfter("base64,")
            val decodedBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            url
        }
    }
    return url
}

fun compressUriToBase64(context: android.content.Context, uri: android.net.Uri, maxDimension: Int = 200): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bytes = inputStream.readBytes()
        inputStream.close()
        
        if (bytes.isEmpty()) return null

        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        var width = options.outWidth
        var height = options.outHeight
        var sampleSize = 1
        while (width > maxDimension || height > maxDimension) {
            width /= 2
            height /= 2
            sampleSize *= 2
        }

        val options2 = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val decodedBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options2) ?: return null

        val scaledBitmap = if (decodedBitmap.width > maxDimension || decodedBitmap.height > maxDimension) {
            val ratio = decodedBitmap.width.toFloat() / decodedBitmap.height.toFloat()
            val targetWidth: Int
            val targetHeight: Int
            if (decodedBitmap.width > decodedBitmap.height) {
                targetWidth = maxDimension
                targetHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
            } else {
                targetHeight = maxDimension
                targetWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
            }
            android.graphics.Bitmap.createScaledBitmap(decodedBitmap, targetWidth, targetHeight, true)
        } else {
            decodedBitmap
        }

        val byteArrayOutputStream = java.io.ByteArrayOutputStream()
        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, byteArrayOutputStream)
        val compressedBytes = byteArrayOutputStream.toByteArray()
        val base64String = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.NO_WRAP)
        "data:image/jpeg;base64,$base64String"
    } catch (e: Exception) {
        android.util.Log.e("ImageCompress", "Failed to compress and encode image", e)
        null
    }
}


class ConfettiParticle(
    var x: Float,
    var y: Float,
    val color: Color,
    val size: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    val rotationSpeed: Float
)

@Composable
fun ConfettiOverlay(modifier: Modifier = Modifier) {
    val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta, Color.Cyan)
    val particles = remember {
        List(110) {
            ConfettiParticle(
                x = (0..1200).random().toFloat(),
                y = -(0..500).random().toFloat(),
                color = colors.random(),
                size = (12..28).random().toFloat(),
                vx = (-4..4).random().toFloat(),
                vy = (12..26).random().toFloat(),
                rotation = (0..360).random().toFloat(),
                rotationSpeed = (-15..15).random().toFloat()
            )
        }
    }
    
    var ticks by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { frameTime ->
                ticks = frameTime
            }
        }
    }
    
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        
        particles.forEach { p ->
            if (p.x < 0f) p.x = w
            if (p.x > w) p.x = 0f
            if (p.y > h) {
                p.y = -(10..150).random().toFloat()
                p.vy = (12..26).random().toFloat()
            }
            
            p.x += p.vx
            p.y += p.vy
            p.rotation += p.rotationSpeed
            
            rotate(
                degrees = p.rotation,
                pivot = androidx.compose.ui.geometry.Offset(p.x + p.size / 2f, p.y + p.size / 2f)
            ) {
                drawRect(
                    color = p.color,
                    topLeft = androidx.compose.ui.geometry.Offset(p.x, p.y),
                    size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.55f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(viewModel: TaskViewModel) {
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val chores by viewModel.allChores.collectAsStateWithLifecycle()
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentLang by viewModel.language.collectAsStateWithLifecycle()
    
    val isHe = currentLang == "HE"
    
    val allCombined = remember(tasks, chores) { tasks + chores }
    
    var selectedTab by remember { mutableStateOf(0) } // 0 = Daily, 1 = Weekly, 2 = Points Balance
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Upper Title card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isHe) "לוח האליפות המשפחתי 🏆" else "Family Leaderboard 🏆",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isHe) "מי ישלים הכי הרבה משימות השבוע?" else "Who will complete the most tasks this week?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Tab row: Daily vs Weekly vs Points Standings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text(if (isHe) "תחרות יומית" else "Daily") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text(if (isHe) "תחרות שבועית" else "Weekly") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                label = { Text(if (isHe) "יתרת נקודות" else "Points Balance") },
                modifier = Modifier.weight(1.2f)
            )
        }
        
        // Calculate scores for each user
        val scores = remember(allCombined, users, selectedTab) {
            val now = System.currentTimeMillis()
            val startOfWeek = now - 7 * 24 * 60 * 60 * 1000
            
            // Group users by lowercase trimmed name to completely eliminate duplicates
            val groupedUsers = users.groupBy { it.name.trim().lowercase() }
            
            groupedUsers.map { (lowerName, userList) ->
                val representativeUser = userList.first()
                val userIds = userList.map { it.id }.toSet()
                
                val assignedToday = allCombined.filter { 
                    it.assignedToUserId in userIds && 
                    it.source != TaskSource.STORE_REDEMPTION &&
                    isScheduledForToday(it)
                }
                
                val completedToday = assignedToday.count { it.status == TaskStatus.DONE }
                
                val completedThisWeekList = allCombined.filter {
                    it.assignedToUserId in userIds &&
                    it.status == TaskStatus.DONE &&
                    (it.completedAt ?: 0L) >= startOfWeek
                }
                val completedThisWeekCount = completedThisWeekList.size
                val pointsThisWeekVal = completedThisWeekList.sumOf { if (it.isChore) it.rewardPoints else 10 } // give 10 points for standard tasks too
                
                val badges = mutableListOf<String>()
                val badgesEn = mutableListOf<String>()
                
                // Perfect day Badge (Completed all tasks today)
                if (assignedToday.isNotEmpty() && completedToday == assignedToday.size) {
                    badges.add("יום מושלם 🌟")
                    badgesEn.add("Perfect Day 🌟")
                }
                // Chore Champion Badge (Completed at least 3 chores/tasks this week)
                val choresCountThisWeek = completedThisWeekList.count { it.isChore }
                if (choresCountThisWeek >= 1) {
                    badges.add("חרוץ משפחתי 🧹")
                    badgesEn.add("Busy Bee 🧹")
                }
                if (pointsThisWeekVal >= 50) {
                    badges.add("אספן כוכבים ⭐")
                    badgesEn.add("Star Collector ⭐")
                }
                // Early bird badge (A task completed before noon)
                val earlyBird = completedThisWeekList.any { task ->
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = task.completedAt ?: 0L }
                    cal.get(java.util.Calendar.HOUR_OF_DAY) < 12
                }
                if (earlyBird) {
                    badges.add("ציפור משכימה 🌅")
                    badgesEn.add("Early Bird 🌅")
                }
                
                LeaderboardRow(
                    user = representativeUser,
                    assignedCount = assignedToday.size,
                    completedCount = completedToday,
                    weeklyCompletedCount = completedThisWeekCount,
                    weeklyPoints = pointsThisWeekVal,
                    badges = if (isHe) badges else badgesEn
                )
            }.sortedWith(
                when (selectedTab) {
                    0 -> {
                        compareByDescending<LeaderboardRow> { it.completedCount }
                            .thenByDescending { it.weeklyPoints }
                    }
                    1 -> {
                        compareByDescending<LeaderboardRow> { it.weeklyCompletedCount }
                            .thenByDescending { it.weeklyPoints }
                    }
                    else -> {
                        compareByDescending<LeaderboardRow> { it.user.balance }
                    }
                }
            )
        }
        
        if (scores.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (isHe) "אין חברים רשומים בקבוצה." else "No members in the family yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(scores) { index, row ->
                    val rank = index + 1
                    val isFirst = rank == 1
                    
                    val cardBorder = if (isFirst) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        null
                    }
                    
                    val cardBg = if (isFirst) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        border = cardBorder,
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rank Badge
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(36.dp)
                            ) {
                                when (rank) {
                                    1 -> Text("👑", style = MaterialTheme.typography.titleLarge)
                                    2 -> Text("🥈", style = MaterialTheme.typography.titleLarge)
                                    3 -> Text("🥉", style = MaterialTheme.typography.titleLarge)
                                    else -> Text("#$rank", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.Gray)
                                }
                            }
                            
                            Spacer(Modifier.width(8.dp))
                            
                            // User avatar
                            UserAvatar(user = row.user, size = 36.dp)
                            
                            Spacer(Modifier.width(12.dp))
                            
                            // Stats description
                            Column(Modifier.weight(1f)) {
                                Text(row.user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                
                                // Badges
                                if (row.badges.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        row.badges.take(3).forEach { badge ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                            ) {
                                                Text(
                                                    text = badge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // Stats Column
                            Column(horizontalAlignment = Alignment.End) {
                                when (selectedTab) {
                                    0 -> {
                                        val pct = if (row.assignedCount > 0) (row.completedCount * 100 / row.assignedCount) else 0
                                        Text(
                                            text = "$pct%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (pct == 100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isHe) "${row.completedCount}/${row.assignedCount} משימות" else "${row.completedCount}/${row.assignedCount} tasks",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                    1 -> {
                                        Text(
                                            text = "+${row.weeklyPoints}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = if (isHe) "${row.weeklyCompletedCount} משימות" else "${row.weeklyCompletedCount} tasks",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "${row.user.balance}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = if (isHe) "נקודות סה״כ" else "Total Points",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class LeaderboardRow(
    val user: User,
    val assignedCount: Int,
    val completedCount: Int,
    val weeklyCompletedCount: Int,
    val weeklyPoints: Int,
    val badges: List<String>
)

// ==========================================
// AI Voice / Long Message Task Parser Dialog
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AITaskGeneratorDialog(
    currentLang: String,
    users: List<User>,
    viewModel: TaskViewModel,
    onDismiss: () -> Unit
) {
    val isHe = currentLang == "HE"
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var userText by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var isListeningSimulated by remember { mutableStateOf(false) }

    // Sinus Wave animation
    val infiniteTransition = rememberInfiniteTransition(label = "sinus_tasks")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_tasks"
    )

    fun handleAIParse(transcription: String) {
        if (transcription.isBlank()) return
        isThinking = true
        userText = ""
        isListeningSimulated = false
        coroutineScope.launch {
            try {
                val parsedTasks = com.example.data.FirebaseAIService.parseLongVoiceTask(
                    transcriptionText = transcription,
                    familyMembers = users,
                    currentLang = currentLang
                )

                if (parsedTasks.isEmpty()) {
                    resultMessage = if (isHe) "ה-AI לא הצליח לזהות משימות מוגדרות בטקסט." else "AI could not identify specific tasks in the text."
                } else {
                    // Create all parsed tasks!
                    parsedTasks.forEach { parsed ->
                        val dateMs = try {
                            parsed.dueDateStr?.let { dateStr ->
                                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                sdf.parse(dateStr)?.time
                            }
                        } catch (e: Exception) {
                            null
                        }

                        viewModel.addTask(
                            title = parsed.title,
                            description = if (isHe) "נוצר אוטומטית באמצעות עוזר ה-AI" else "Parsed and created automatically by AI Helper",
                            isChore = parsed.isChore,
                            assignedToUserId = parsed.assignedToUserId,
                            dueDate = dateMs,
                            rewardPoints = parsed.points
                        )
                    }

                    resultMessage = if (isHe) {
                        "הצלחנו! יצרנו בהצלחה ${parsedTasks.size} משימות בבנק המשותף!"
                    } else {
                        "Success! Created ${parsedTasks.size} tasks in the shared family bank!"
                    }
                }
                isThinking = false
            } catch (e: Exception) {
                isThinking = false
                resultMessage = if (isHe) "שגיאה בפירוק המשימות ב-AI." else "Failed to parse tasks via AI."
            }
        }
    }

    val presets = if (isHe) {
        listOf(
            "אמא צריכה לנקות את המטבח מחר, ויאיר צריך להוריד את הזבל היום 🧹",
            "דני צריך לסדר את המיטה בבוקר, ורננה חייבת לשטוף כלים הערב 🧼"
        )
    } else {
        listOf(
            "Mom needs to vacuum the living room tomorrow and Jack must clean the bedroom today 🛏️",
            "Daniel has to feed the dog tonight and Sarah needs to study for 2 hours 📚"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isHe) "מפרק משימות קולי (AI)" else "AI Long Message Task Parser",
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
                Text(
                    text = if (isHe) {
                        "הקליטו הודעה קולית ארוכה או הקלידו הנחיות חופשיות. העוזר יפרק אותן למשימות נפרדות, ישייך לבני המשפחה המתאימים, ויקבע נקודות וזמנים!"
                    } else {
                        "Record a long voice memo or type loose directives. The AI assistant will decompose them into list items, auto-assign family members, and attach points!"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                // Status or Success/Error Alert Message
                if (resultMessage.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (resultMessage.contains("שגיאה") || resultMessage.contains("Failed")) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = resultMessage,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (resultMessage.contains("שגיאה") || resultMessage.contains("Failed")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (isThinking) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isHe) "ה-AI מפרק את המשימות..." else "AI is decomposing instructions...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Waveform drawing
                if (isListeningSimulated || isThinking) {
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
                                val amplitude = if (isThinking) 7f else 22f
                                val frequency = 12f
                                val y = midY + Math.sin((normalizedX * frequency + phaseShift).toDouble()).toFloat() * amplitude
                                path.lineTo(x.toFloat(), y)
                            }
                            drawPath(
                                path = path,
                                color = androidx.compose.ui.graphics.Color(0xFFE91E63),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                }

                // Presets Suggestions
                Text(
                    text = if (isHe) "הודעות לדוגמה:" else "Example Prompts:",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { text ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { handleAIParse(text.replace(Regex("[🧹🧼🛏️📚]"), "")) }
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                // Manual typed instructions field
                TextField(
                    value = userText,
                    onValueChange = { userText = it },
                    placeholder = { Text(if (isHe) "הקלד הוראה ארוכה כאן..." else "Type full family directions here...") },
                    trailingIcon = {
                        IconButton(
                            onClick = { handleAIParse(userText) },
                            enabled = userText.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Parse", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Large central voice button simulation
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
                                        delay(3500)
                                        if (isListeningSimulated) {
                                            // Trigger a beautiful, complete, automatic example voice transcription parsing!
                                            handleAIParse(
                                                if (isHe) "אמא צריכה לנקות את המטבח מחר בצהריים, ודני צריך להוריד את הזבל"
                                                else "Mom needs to vacuum the living room tomorrow and Jack must clean the bedroom today"
                                            )
                                        }
                                    }
                                }
                            }
                            .testTag("task_ai_mic_simulation_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListeningSimulated) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Record",
                            tint = if (isListeningSimulated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(if (isHe) "סגור עוזר" else "Done")
            }
        }
    )
}




