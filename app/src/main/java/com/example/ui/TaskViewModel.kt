package com.example.ui

import android.content.Context
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModel(
    private val repository: TaskRepository,
    private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("family_prefs", Context.MODE_PRIVATE)

    // Detect system language (HE/iw or EN) for first time defaults
    private val defaultLanguageCode: String by lazy {
        val sysLang = Locale.getDefault().language.lowercase()
        if (sysLang.contains("he") || sysLang.contains("iw")) "HE" else "EN"
    }

    private val _language = MutableStateFlow<String>(
        prefs.getString("app_language", defaultLanguageCode) ?: defaultLanguageCode
    )
    val language = _language.asStateFlow()

    private val _familyId = MutableStateFlow<String>(
        prefs.getString("active_family_id", "family_default") ?: "family_default"
    )
    val familyId = _familyId.asStateFlow()

    private val _isAuthLoading = MutableStateFlow<Boolean>(false)
    val isAuthLoading = _isAuthLoading.asStateFlow()

    private val _dailyReminderEnabled = MutableStateFlow<Boolean>(
        prefs.getBoolean("daily_reminder_enabled", false)
    )
    val dailyReminderEnabled = _dailyReminderEnabled.asStateFlow()

    fun setDailyReminderEnabled(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            _dailyReminderEnabled.value = enabled
            prefs.edit().putBoolean("daily_reminder_enabled", enabled).apply()
            if (enabled) {
                com.example.ReminderReceiver.scheduleDailyReminder(context)
            } else {
                com.example.ReminderReceiver.cancelDailyReminder(context)
            }
        }
    }

    private val _planReminderEnabled = MutableStateFlow<Boolean>(
        prefs.getBoolean("plan_reminder_enabled", false)
    )
    val planReminderEnabled = _planReminderEnabled.asStateFlow()

    fun setPlanReminderEnabled(enabled: Boolean, context: Context) {
        viewModelScope.launch {
            _planReminderEnabled.value = enabled
            prefs.edit().putBoolean("plan_reminder_enabled", enabled).apply()
            if (enabled) {
                com.example.ReminderReceiver.schedulePlanReminder(context)
            } else {
                com.example.ReminderReceiver.cancelPlanReminder(context)
            }
        }
    }


    // Reactive streams filtered by familyId
    val allTasks = familyId.flatMapLatest { fId ->
        repository.getAllMainTasks(fId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allChores = familyId.flatMapLatest { fId ->
        repository.getAllChores(fId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allUsers = familyId.flatMapLatest { fId ->
        repository.getAllUsers(fId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val rewardItems = familyId.flatMapLatest { fId ->
        repository.getRewardItems(fId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allCategories = familyId.flatMapLatest { fId ->
        repository.getAllCategories(fId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allShoppingItems = familyId.flatMapLatest { fId ->
        repository.getAllShoppingItems(fId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allChecklists = familyId.flatMapLatest { fId ->
        repository.getAllChecklists(fId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allFamilyRoles = familyId.flatMapLatest { fId ->
        repository.getFamilyRolesByFamily(fId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val incomingProposals = familyId.flatMapLatest { fId ->
        repository.getIncomingProposals(fId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val outgoingProposals = familyId.flatMapLatest { fId ->
        repository.getOutgoingProposals(fId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // 1. Automatically sync firestore whenever familyId updates
        viewModelScope.launch {
            familyId.collect { fId ->
                repository.updateSyncFamilyId(fId)
            }
        }

        // 2. Automated Child Task Instance Generation for Recurring parent tasks & sync attributes
        viewModelScope.launch {
            // Trigger loop-free initial sync after a short delay to let DB/ViewModel settle
            kotlinx.coroutines.delay(500)
            syncRecurringInstances()
        }

        // 3. Automated End-Of-Day Check: Return active uncompleted/unapproved tasks back to the bank
        checkAndHandleDayTransition()

        // 4. Automated Daily Completion Celebration Collector
        viewModelScope.launch {
            combine(allTasks, allChores, allUsers) { tasks, chores, usersList ->
                Triple(tasks, chores, usersList)
            }.collect { (tasks, chores, usersList) ->
                val todayStr = getTodayString()
                val allItems = tasks + chores
                
                usersList.forEach { user ->
                    // Find all chores/tasks assigned to this user that are scheduled for today, excluding store redemptions
                    val userTodayItems = allItems.filter { 
                        it.assignedToUserId == user.id && 
                        it.source != TaskSource.STORE_REDEMPTION &&
                        isScheduledForToday(it)
                    }
                    
                    if (userTodayItems.isNotEmpty()) {
                        val allDone = userTodayItems.all { it.status == TaskStatus.DONE }
                        if (allDone) {
                            val prefKey = "celebrated_${user.id}_$todayStr"
                            val alreadyCelebrated = prefs.getBoolean(prefKey, false)
                            if (!alreadyCelebrated) {
                                // Mark it as celebrated
                                prefs.edit().putBoolean(prefKey, true).apply()
                                
                                // Trigger celebration!
                                _celebrationEvent.value = CelebrationEvent(
                                    userName = user.name,
                                    taskCount = userTodayItems.size,
                                    todayDate = todayStr
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Automated User Duplicate Merging and Cleanup (guarded against reentrancy loops)
        var isCleaningUpDuplicates = false
        viewModelScope.launch {
            allUsers.collect { usersList ->
                if (usersList.isEmpty() || isCleaningUpDuplicates) return@collect
                val grouped = usersList.groupBy { it.name.trim().lowercase() }
                val duplicateGroups = grouped.filter { it.value.size > 1 }
                if (duplicateGroups.isNotEmpty()) {
                    isCleaningUpDuplicates = true
                    launch(Dispatchers.IO) {
                        try {
                            val currentFamily = familyId.value
                            val tasks = repository.getAllTasksDirect(currentFamily)
                            duplicateGroups.forEach { (name, userList) ->
                                val sorted = userList.sortedBy { it.id }
                                var primaryUser = sorted.first()
                                val duplicates = sorted.drop(1)
                                val dupIds = duplicates.map { it.id }
                                
                                // Coalesce remoteId and take maximum/combined balance
                                var updatedPrimary = primaryUser
                                duplicates.forEach { dup ->
                                    if (updatedPrimary.remoteId.isNullOrBlank() && !dup.remoteId.isNullOrBlank()) {
                                        updatedPrimary = updatedPrimary.copy(remoteId = dup.remoteId)
                                    }
                                    if (dup.balance > updatedPrimary.balance) {
                                        updatedPrimary = updatedPrimary.copy(balance = dup.balance)
                                    }
                                }
                                
                                if (updatedPrimary != primaryUser) {
                                    repository.updateUser(updatedPrimary)
                                    primaryUser = updatedPrimary
                                }
                                
                                tasks.forEach { task ->
                                    if (task.assignedToUserId in dupIds) {
                                        repository.updateTask(task.copy(assignedToUserId = primaryUser.id))
                                    }
                                }
                                
                                duplicates.forEach { dupUser ->
                                    repository.deleteUserLocallyOnly(dupUser)
                                    // If have distinct remoteIds, clean up the duplicate user remote doc as we merged onto the main one
                                    if (!dupUser.remoteId.isNullOrBlank() && dupUser.remoteId != primaryUser.remoteId) {
                                        repository.deleteUserRemoteOnly(dupUser)
                                    }
                                    android.util.Log.d("TaskViewModel", "Cleaned up duplicate user ${dupUser.name} (${dupUser.id}) in favor of ${primaryUser.id}")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("TaskViewModel", "Error cleaning up duplicate users", e)
                        } finally {
                            // Settle database flow emissions first to avoid thrashing
                            kotlinx.coroutines.delay(1000)
                            isCleaningUpDuplicates = false
                        }
                    }
                }
            }
        }
    }
    
    fun syncRecurringInstances() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fId = familyId.value
                val tasks = repository.getAllMainTasks(fId).first()
                val chores = repository.getAllChores(fId).first()
                val list = tasks + chores
                val todayStr = getTodayString()
                val parents = list.filter { it.parentId == null && !it.recurrenceRule.isNullOrBlank() && it.recurrenceRule != "NONE" }
                
                parents.forEach { parent ->
                    if (isScheduledForToday(parent)) {
                        val childExists = list.any { it.parentId == parent.id && it.pulledForDate == todayStr }
                        if (!childExists) {
                            // Automatically insert today's Child Task instance
                            val child = Task(
                                title = parent.title,
                                description = parent.description,
                                priority = parent.priority,
                                status = TaskStatus.TODO,
                                parentId = parent.id,
                                recurrenceRule = null, // child doesn't recur
                                isChore = parent.isChore,
                                rewardPoints = parent.rewardPoints,
                                assignedToUserId = parent.assignedToUserId, // Inherit parent's assignment automatically
                                pulledForDate = todayStr,
                                familyId = parent.familyId,
                                assignedToUserRemoteId = parent.assignedToUserRemoteId,
                                parentRemoteId = parent.remoteId
                            )
                            repository.insertTask(child)
                        } else {
                            // If parent has been modified, sync those modifications to uncompleted today's child
                            val child = list.find { it.parentId == parent.id && it.pulledForDate == todayStr }
                            if (child != null && child.status == TaskStatus.TODO) {
                                val expectedAssignee = parent.assignedToUserId ?: child.assignedToUserId
                                if (child.title != parent.title || 
                                    child.description != parent.description || 
                                    child.rewardPoints != parent.rewardPoints || 
                                    child.isChore != parent.isChore ||
                                    child.priority != parent.priority ||
                                    child.assignedToUserId != expectedAssignee) {
                                    val updatedChild = child.copy(
                                        title = parent.title,
                                        description = parent.description,
                                        priority = parent.priority,
                                        rewardPoints = parent.rewardPoints,
                                        isChore = parent.isChore,
                                        assignedToUserId = expectedAssignee
                                    )
                                    repository.updateTask(updatedChild)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TaskViewModel", "Error syncing recurring instances", e)
            }
        }
    }

    private val _celebrationEvent = MutableStateFlow<CelebrationEvent?>(null)
    val celebrationEvent = _celebrationEvent.asStateFlow()

    fun clearCelebrationEvent() {
        _celebrationEvent.value = null
    }

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage = _toastMessage.asStateFlow()

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    private val _activeChecklistTask = MutableStateFlow<Task?>(null)
    val activeChecklistTask = _activeChecklistTask.asStateFlow()

    fun openChecklist(task: Task?) {
        _activeChecklistTask.value = task
    }

    fun getItemsForCategory(categoryId: Int?): kotlinx.coroutines.flow.Flow<List<ShoppingItem>> {
        return allShoppingItems.map { items ->
            if (categoryId == null) emptyList() else items.filter { it.categoryId == categoryId }
        }
    }

    fun getItemsForChecklist(checklistId: Int?): kotlinx.coroutines.flow.Flow<List<ShoppingItem>> {
        return allShoppingItems.map { items ->
            if (checklistId == null) {
                emptyList()
            } else if (checklistId == -1) {
                items.filter { it.checklistId == null }
            } else {
                items.filter { it.checklistId == checklistId }
            }
        }
    }

    fun getCategoriesForChecklist(checklistId: Int?): kotlinx.coroutines.flow.Flow<List<ShoppingCategory>> {
        return allCategories.map { categories ->
            if (checklistId == null) {
                emptyList()
            } else if (checklistId == -1) {
                categories.filter { it.checklistId == null }
            } else {
                categories.filter { it.checklistId == checklistId }
            }
        }
    }

    fun createChecklistForTask(task: Task, listName: String, isPrivate: Boolean = false, creatorId: Int? = null) {
        viewModelScope.launch {
            val newChecklist = Checklist(
                name = listName,
                familyId = familyId.value,
                taskId = task.id,
                taskRemoteId = task.remoteId,
                isPrivate = isPrivate,
                creatorId = creatorId
            )
            val checklistId = repository.insertChecklist(newChecklist).toInt()
            
            val updatedTask = task.copy(checkboxListId = checklistId)
            repository.updateTask(updatedTask)
        }
    }

    fun linkTaskWithCheckboxList(task: Task, checklistId: Int) {
        viewModelScope.launch {
            if (checklistId != -1) {
                val checklist = repository.getChecklistById(checklistId)
                if (checklist != null) {
                    repository.updateChecklist(checklist.copy(taskId = task.id, taskRemoteId = task.remoteId))
                }
            }
            val updatedTask = task.copy(checkboxListId = checklistId)
            repository.updateTask(updatedTask)
        }
    }

    fun unlinkTaskFromCheckboxList(task: Task) {
         viewModelScope.launch {
             val listId = task.checkboxListId
             if (listId != null && listId != -1) {
                 val checklist = repository.getChecklistById(listId)
                 if (checklist != null) {
                     repository.updateChecklist(checklist.copy(taskId = null, taskRemoteId = null))
                 }
             }
             val updatedTask = task.copy(checkboxListId = null, checkboxListRemoteId = null)
             repository.updateTask(updatedTask)
         }
    }

    fun addChecklistItem(checklistId: Int, name: String) {
        viewModelScope.launch {
            val newItem = ShoppingItem(
                name = name,
                checklistId = checklistId,
                categoryId = null,
                familyId = familyId.value
            )
            repository.insertShoppingItem(newItem)
        }
    }

    fun toggleChecklistItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.updateShoppingItem(item.copy(isCompleted = !item.isCompleted))
        }
    }

    fun getTodayString(): String {
        val cal = java.util.Calendar.getInstance()
        return String.format(
            "%04d-%02d-%02d", 
            cal.get(java.util.Calendar.YEAR), 
            cal.get(java.util.Calendar.MONTH) + 1, 
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    private val _returnedTasksAlertHe = MutableStateFlow<List<String>>(emptyList())
    val returnedTasksAlertHe = _returnedTasksAlertHe.asStateFlow()

    private val _returnedTasksAlertEn = MutableStateFlow<List<String>>(emptyList())
    val returnedTasksAlertEn = _returnedTasksAlertEn.asStateFlow()

    fun clearReturnedTasksAlert() {
        _returnedTasksAlertHe.value = emptyList()
        _returnedTasksAlertEn.value = emptyList()
    }

    fun checkAndHandleDayTransition() {
        viewModelScope.launch {
            val nowCal = java.util.Calendar.getInstance()
            val todayStr = getTodayString()
            val lastCheckDate = prefs.getString("last_day_transition_check_date", "") ?: ""
            
            if (lastCheckDate.isNotEmpty() && lastCheckDate != todayStr) {
                // A new day has started!
                val fId = familyId.value
                val tasksInDb = repository.getAllMainTasks(fId).first()
                val choresInDb = repository.getAllChores(fId).first()
                val allTasksList = tasksInDb + choresInDb
                
                val returnedTaskNamesEn = mutableListOf<String>()
                val returnedTaskNamesHe = mutableListOf<String>()
                
                for (task in allTasksList) {
                    // If a task/chore is assigned to someone but was not completed & approved (status != DONE)
                    if (task.assignedToUserId != null && task.status != TaskStatus.DONE) {
                        val hasRecurrence = !task.recurrenceRule.isNullOrBlank() && task.recurrenceRule != "NONE"
                        
                        if (hasRecurrence) {
                            if (task.pulledForDate != null) {
                                // Pulled recurring bank task -> Return to bank by resetting assignment and pulledForDate
                                val updatedTask = task.copy(
                                    assignedToUserId = null,
                                    pulledForDate = null,
                                    status = TaskStatus.TODO,
                                    completedAt = null
                                )
                                repository.updateTask(updatedTask)
                                
                                if (task.isChore) {
                                    returnedTaskNamesEn.add("[Recurring Chore] ${task.title} (Returned to bank)")
                                    returnedTaskNamesHe.add("[מטלה מחזורית] ${task.title} (הוחזרה לבנק)")
                                } else {
                                    returnedTaskNamesEn.add("[Recurring Task] ${task.title} (Returned to bank)")
                                    returnedTaskNamesHe.add("[משימה מחזורית] ${task.title} (הוחזרה לבנק)")
                                }
                            } else {
                                // Personal recurring task -> Reset status but keep the assignment
                                val updatedTask = task.copy(
                                    status = TaskStatus.TODO,
                                    completedAt = null
                                )
                                repository.updateTask(updatedTask)
                                
                                if (task.isChore) {
                                    returnedTaskNamesEn.add("[Recurring Chore] ${task.title} (Reset for today)")
                                    returnedTaskNamesHe.add("[מטלה מחזורית] ${task.title} (אותחלה ליום החדש)")
                                } else {
                                    returnedTaskNamesEn.add("[Recurring Task] ${task.title} (Reset for today)")
                                    returnedTaskNamesHe.add("[משימה מחזורית] ${task.title} (אותחלה ליום החדש)")
                                }
                            }
                        } else {
                            // No recurrence -> Return to bank by setting assignedToUserId = null
                            val updatedTask = task.copy(
                                assignedToUserId = null,
                                status = TaskStatus.TODO,
                                completedAt = null
                            )
                            repository.updateTask(updatedTask)
                            
                            if (task.isChore) {
                                returnedTaskNamesEn.add("[Chore] ${task.title} (Returned to bank)")
                                returnedTaskNamesHe.add("[מטלה] ${task.title} (הוחזרה לבנק)")
                            } else {
                                returnedTaskNamesEn.add("[Task] ${task.title} (Returned to bank)")
                                returnedTaskNamesHe.add("[משימה] ${task.title} (הוחזרה לבנק)")
                            }
                        }
                    }
                }
                
                if (returnedTaskNamesEn.isNotEmpty()) {
                    _returnedTasksAlertHe.value = returnedTaskNamesHe
                    _returnedTasksAlertEn.value = returnedTaskNamesEn
                }
            }
            
            prefs.edit().putString("last_day_transition_check_date", todayStr).apply()
            syncRecurringInstances()
        }
    }

    fun simulateDayTransition() {
        viewModelScope.launch {
            val yesterdayCal = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            val yesterdayStr = String.format(
                "%04d-%02d-%02d", 
                yesterdayCal.get(java.util.Calendar.YEAR), 
                yesterdayCal.get(java.util.Calendar.MONTH) + 1, 
                yesterdayCal.get(java.util.Calendar.DAY_OF_MONTH)
            )
            prefs.edit().putString("last_day_transition_check_date", yesterdayStr).apply()
            checkAndHandleDayTransition()
        }
    }

    private fun getUpcomingScheduledCal(task: Task, refMidnight: java.util.Calendar): java.util.Calendar {
        val rule = task.recurrenceRule ?: return refMidnight
        if (rule.isBlank() || rule == "NONE") return refMidnight
        
        val parts = rule.split(":")
        val type = parts.getOrNull(0) ?: return refMidnight
        val dayArg = parts.getOrNull(1)
        
        val cal = refMidnight.clone() as java.util.Calendar
        when (type) {
            "DAILY" -> {
                return cal
            }
            "WEEKLY" -> {
                val scheduledDay = dayArg?.toIntOrNull() ?: return cal
                var count = 0
                while (cal.get(java.util.Calendar.DAY_OF_WEEK) != scheduledDay && count < 8) {
                    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    count++
                }
                return cal
            }
            "MONTHLY" -> {
                val scheduledDayOfMonth = dayArg?.toIntOrNull() ?: return cal
                var count = 0
                while (cal.get(java.util.Calendar.DAY_OF_MONTH) != scheduledDayOfMonth && count < 32) {
                    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    count++
                }
                return cal
            }
        }
        return cal
    }

    private fun getPreviousScheduledCal(task: Task, upcomingCal: java.util.Calendar): java.util.Calendar {
        val rule = task.recurrenceRule ?: return upcomingCal
        if (rule.isBlank() || rule == "NONE") return upcomingCal
        
        val parts = rule.split(":")
        val type = parts.getOrNull(0) ?: return upcomingCal
        
        val cal = upcomingCal.clone() as java.util.Calendar
        when (type) {
            "DAILY" -> {
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            "WEEKLY" -> {
                cal.add(java.util.Calendar.DAY_OF_YEAR, -7)
            }
            "MONTHLY" -> {
                cal.add(java.util.Calendar.MONTH, -1)
            }
        }
        return cal
    }

    private fun shouldResetTask(task: Task, currentTime: Long): Boolean {
        val completedAt = task.completedAt ?: return false
        val rule = task.recurrenceRule ?: return false
        if (rule.isBlank() || rule == "NONE") return false
        if (task.status != TaskStatus.DONE) return false

        val curCal = java.util.Calendar.getInstance().apply { timeInMillis = currentTime }
        fun java.util.Calendar.toMidnight() {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val curMidnight = (curCal.clone() as java.util.Calendar).apply { toMidnight() }
        
        val compCal = java.util.Calendar.getInstance().apply { timeInMillis = completedAt }
        val compMidnight = (compCal.clone() as java.util.Calendar).apply { toMidnight() }

        val upcomingCal = getUpcomingScheduledCal(task, curMidnight)
        val previousCal = getPreviousScheduledCal(task, upcomingCal)

        val completedInCurrentCycle = compMidnight.after(previousCal)
        return !completedInCurrentCycle
    }

    fun addChecklist(name: String, taskId: Int? = null, isPrivate: Boolean = false, creatorId: Int? = null) {
        viewModelScope.launch {
            val checklist = Checklist(
                name = name,
                familyId = familyId.value,
                taskId = taskId,
                isPrivate = isPrivate,
                creatorId = creatorId
            )
            repository.insertChecklist(checklist)
        }
    }

    fun updateChecklist(checklist: Checklist) {
        viewModelScope.launch {
            repository.updateChecklist(checklist)
        }
    }

    fun deleteChecklist(checklist: Checklist) {
        viewModelScope.launch {
            repository.deleteChecklist(checklist)
        }
    }

    fun addCategory(name: String, checklistId: Int? = null) {
        viewModelScope.launch {
            repository.insertCategory(ShoppingCategory(name = name, familyId = familyId.value, checklistId = checklistId))
        }
    }

    fun addShoppingItem(name: String, categoryId: Int? = null, checklistId: Int? = null) {
        viewModelScope.launch {
            repository.insertShoppingItem(ShoppingItem(name = name, categoryId = categoryId, checklistId = checklistId, familyId = familyId.value))
        }
    }

    fun updateCategory(category: ShoppingCategory) {
        viewModelScope.launch {
            repository.updateCategory(category.copy(familyId = familyId.value))
        }
    }

    fun deleteCategory(category: ShoppingCategory) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    fun updateShoppingItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.updateShoppingItem(item.copy(familyId = familyId.value))
        }
    }

    fun deleteShoppingItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.deleteShoppingItem(item)
        }
    }

    fun setCategoryExpanded(category: ShoppingCategory, expanded: Boolean) {
        viewModelScope.launch {
            repository.updateCategory(category.copy(isExpanded = expanded, familyId = familyId.value))
        }
    }

    fun toggleCategoryCompletion(category: ShoppingCategory, categoryItems: List<ShoppingItem>) {
        viewModelScope.launch {
            val targetCompleted = !category.isCompleted
            repository.updateCategory(category.copy(isCompleted = targetCompleted, familyId = familyId.value))
            categoryItems.forEach { item ->
                if (item.isCompleted != targetCompleted) {
                    repository.updateShoppingItem(item.copy(isCompleted = targetCompleted, familyId = familyId.value))
                }
            }
        }
    }

    fun toggleItemCompletion(item: ShoppingItem, anySiblings: List<ShoppingItem>, parentCategory: ShoppingCategory?) {
        viewModelScope.launch {
            val targetCompleted = !item.isCompleted
            repository.updateShoppingItem(item.copy(isCompleted = targetCompleted, familyId = familyId.value))

            if (parentCategory != null) {
                if (targetCompleted) {
                    val allSiblingsNowCompleted = anySiblings.filter { it.id != item.id }.all { it.isCompleted }
                    if (allSiblingsNowCompleted && !parentCategory.isCompleted) {
                        repository.updateCategory(parentCategory.copy(isCompleted = true, familyId = familyId.value))
                    }
                } else {
                    if (parentCategory.isCompleted) {
                        repository.updateCategory(parentCategory.copy(isCompleted = false, familyId = familyId.value))
                    }
                }
            }
        }
    }

    fun moveShoppingItem(itemId: Int, targetCategoryId: Int?, targetPosition: Int) {
        viewModelScope.launch {
            val items = allShoppingItems.value
            val item = items.find { it.id == itemId } ?: return@launch
            
            val updatedItem = item.copy(categoryId = targetCategoryId, position = targetPosition, familyId = familyId.value)
            repository.updateShoppingItem(updatedItem)
            
            val siblingItems = allShoppingItems.value
                .filter { it.categoryId == targetCategoryId && it.id != itemId }
                .sortedBy { it.position }
            
            var currentPos = 0
            siblingItems.forEach { sibling ->
                if (currentPos == targetPosition) {
                    currentPos++
                }
                if (sibling.position != currentPos) {
                    repository.updateShoppingItem(sibling.copy(position = currentPos, familyId = familyId.value))
                }
                currentPos++
            }
        }
    }

    private val _selectedUserId = MutableStateFlow<Int?>(
        if (prefs.contains("logged_in_user_id")) prefs.getInt("logged_in_user_id", -1).takeIf { it != -1 } else null
    )
    val selectedUserId = _selectedUserId.asStateFlow()

    val currentUser = selectedUserId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else allUsers.map { users -> users.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Automatically resolve active permissions from the profile's linked FamilyRole
    val currentUserRole = combine(currentUser, allFamilyRoles) { user, roles ->
        if (user == null || roles.isEmpty()) null
        else roles.find { it.id == user.roleId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectUser(userId: Int?) {
        _selectedUserId.value = userId
        if (userId != null) {
            prefs.edit().putInt("logged_in_user_id", userId).apply()
        } else {
            prefs.edit().remove("logged_in_user_id").apply()
        }
    }

    fun setLanguage(langCode: String) {
        _language.value = langCode
        prefs.edit().putString("app_language", langCode).apply()
    }

    fun toggleLanguage() {
        val nextLang = if (_language.value == "HE") "EN" else "HE"
        setLanguage(nextLang)
    }

    fun addTask(
        title: String,
        description: String = "",
        priority: Priority = Priority.MEDIUM,
        dueDate: Long? = null,
        isChore: Boolean = false,
        assignedToUserId: Int? = null,
        rewardPoints: Int = 0,
        source: TaskSource = TaskSource.MANUAL,
        recurrenceRule: String? = null,
        isTemplate: Boolean = false
    ) {
        viewModelScope.launch {
            val autoChecklistId = if (title.trim() == "קניית מצרכים שבועיים" || title.trim().equals("Buy Groceries", ignoreCase = true)) {
                -1
            } else {
                null
            }
            repository.insertTask(
                Task(
                    title = title,
                    description = description,
                    priority = priority,
                    dueDate = dueDate,
                    isChore = isChore,
                    assignedToUserId = assignedToUserId,
                    rewardPoints = rewardPoints,
                    source = source,
                    familyId = familyId.value,
                    recurrenceRule = recurrenceRule,
                    isTemplate = isTemplate,
                    checkboxListId = autoChecklistId
                )
            )
            syncRecurringInstances()
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            syncRecurringInstances()
        }
    }

    fun getNextRecurrenceDateStr(parent: Task): String {
        val nextCal = java.util.Calendar.getInstance()
        val upcomingCal = getUpcomingScheduledCal(parent, nextCal)
        val sdf = if (language.value == "HE") {
            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        } else {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        }
        return sdf.format(upcomingCal.time)
    }

    fun completeTask(task: Task) {
        viewModelScope.launch {
            val checklistId = task.checkboxListId
            if (checklistId != null) {
                val items = repository.getItemsByCategoryId(checklistId)
                if (items.any { !it.isCompleted }) {
                    showToast(
                        if (language.value == "HE") {
                            "לא ניתן לסגור את המשימה '${task.title}' - יש פריטים ברשימת הסימון שטרם בוצעו!"
                        } else {
                            "Cannot complete task '${task.title}' - there are uncompleted items in the checklist!"
                        }
                    )
                    return@launch
                }
            }

            val user = currentUser.value
            if (user?.role == UserRole.ADULT) {
                if (task.isChore) {
                    repository.approveChore(task, true)
                } else {
                    val updatedTask = task.copy(
                        status = TaskStatus.DONE, 
                        completedAt = System.currentTimeMillis(),
                        lastCompletedTimestamp = System.currentTimeMillis()
                    )
                    repository.updateTask(updatedTask)
                }
            } else {
                val updatedTask = task.copy(
                    status = TaskStatus.PENDING_APPROVAL,
                    lastCompletedTimestamp = System.currentTimeMillis()
                )
                repository.updateTask(updatedTask)
            }
        }
    }

    fun uncompleteTask(task: Task) {
        viewModelScope.launch {
            repository.uncompleteTask(task)
        }
    }

    fun approveChore(task: Task, approved: Boolean) {
        viewModelScope.launch {
            repository.approveChore(task, approved)
        }
    }

    fun unapproveChore(task: Task) {
        viewModelScope.launch {
            repository.unapproveChore(task)
        }
    }

    fun purchaseReward(userId: Int, item: RewardItem, languageCode: String) {
        viewModelScope.launch {
            repository.purchaseReward(userId, item, languageCode)
        }
    }

    fun addRewardItem(item: RewardItem) {
        viewModelScope.launch {
            repository.insertRewardItem(item.copy(familyId = familyId.value))
        }
    }

    fun updateRewardItem(item: RewardItem) {
        viewModelScope.launch {
            repository.updateRewardItem(item.copy(familyId = familyId.value))
        }
    }

    fun deleteRewardItem(item: RewardItem) {
        viewModelScope.launch {
            repository.deleteRewardItem(item)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            val isParent = task.parentId == null && !task.recurrenceRule.isNullOrBlank() && task.recurrenceRule != "NONE"
            if (isParent) {
                val fId = familyId.value
                val allCombinedList = allTasks.value + allChores.value
                val children = allCombinedList.filter { it.parentId == task.id }
                children.forEach { child ->
                    repository.deleteTask(child)
                }
            }
            repository.deleteTask(task)
        }
    }

    fun deleteUser(user: User) {
        viewModelScope.launch {
            repository.deleteUser(user)
            if (_selectedUserId.value == user.id) {
                selectUser(null)
            }
        }
    }

    fun createFamily(familyName: String, managerName: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            try {
                // Generate robust randomized unique family ID
                val generatedFamilyId = "family_" + (100000..999999).random().toString()
                _familyId.value = generatedFamilyId
                prefs.edit().putString("active_family_id", generatedFamilyId).apply()
                
                // Seed default values localized to current display language!
                seedFamilyData(generatedFamilyId, familyName, managerName, _language.value == "HE")
                
                prefs.edit().putBoolean("db_seeded", true).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun joinFamily(fId: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            try {
                _familyId.value = fId
                prefs.edit().putString("active_family_id", fId).apply()
                prefs.edit().putBoolean("db_seeded", true).apply()
                selectUser(null) // Let them pick an existing profile
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun joinAndSelectProfile(fId: String, uId: Int) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            try {
                _familyId.value = fId
                prefs.edit().putString("active_family_id", fId).apply()
                prefs.edit().putBoolean("db_seeded", true).apply()
                selectUser(uId) // Log in directly into this profile
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isAuthLoading.value = false
            }
        }
    }

    fun inviteMember(
        name: String,
        role: UserRole
    ) {
        val currentFamily = currentUser.value?.familyName ?: "Our Family"
        val currentFamilyId = familyId.value
        val targetRoleId = "${currentFamilyId}_${role.name}"

        viewModelScope.launch {
            val newMember = User(
                name = name,
                role = role,
                familyName = currentFamily,
                familyId = currentFamilyId,
                roleId = targetRoleId,
                balance = 0
            )
            repository.insertUser(newMember)
        }
    }

    fun updateRolePermissions(
        roleId: String,
        allowDelete: Boolean,
        allowCreate: Boolean,
        allowSeeOthers: Boolean,
        allowCreateChecklists: Boolean = false
    ) {
        viewModelScope.launch {
            val role = allFamilyRoles.value.find { it.id == roleId }
            if (role != null) {
                repository.insertFamilyRole(
                    role.copy(
                        allowDeleteTasks = allowDelete,
                        allowCreateTasks = allowCreate,
                        allowSeeOtherTasks = allowSeeOthers,
                        allowCreateChecklists = allowCreateChecklists
                    )
                )
            }
        }
    }

    fun updateMemberRole(userId: Int, roleType: UserRole) {
        viewModelScope.launch {
            val user = repository.getUserById(userId)
            if (user != null) {
                val targetRoleId = "${user.familyId}_${roleType.name}"
                repository.updateUser(user.copy(role = roleType, roleId = targetRoleId))
            }
        }
    }

    fun updateMemberPasscode(userId: Int, passcode: String?) {
        viewModelScope.launch {
            val user = repository.getUserById(userId)
            if (user != null) {
                repository.updateUser(user.copy(passcode = passcode))
            }
        }
    }

    fun updateUserProfile(userId: Int, name: String, avatarUrl: String?, phoneNumber: String?, passcode: String? = null) {
        viewModelScope.launch {
            val user = repository.getUserById(userId)
            if (user != null) {
                val updatedUser = user.copy(
                    name = name,
                    avatarUrl = avatarUrl,
                    phoneNumber = phoneNumber,
                    passcode = passcode
                )
                repository.updateUser(updatedUser)
            }
        }
    }

    // Seeding logic that creates the full isolated sandbox per family, fully translated
    private suspend fun seedFamilyData(
        fId: String,
        familyName: String,
        managerName: String,
        isHebrew: Boolean
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // 1. Roles & Group Permissions
        val managerRole = FamilyRole(
            id = "${fId}_MANAGER",
            familyId = fId,
            roleType = UserRole.ADULT,
            isManager = true,
            allowDeleteTasks = true,
            allowCreateTasks = true,
            allowSeeOtherTasks = true,
            allowCreateChecklists = true
        )
        val adultRole = FamilyRole(
            id = "${fId}_ADULT",
            familyId = fId,
            roleType = UserRole.ADULT,
            isManager = false,
            allowDeleteTasks = true,
            allowCreateTasks = true,
            allowSeeOtherTasks = true,
            allowCreateChecklists = true
        )
        val childRole = FamilyRole(
            id = "${fId}_CHILD",
            familyId = fId,
            roleType = UserRole.CHILD,
            isManager = false,
            allowDeleteTasks = false,
            allowCreateTasks = false,
            allowSeeOtherTasks = false,
            allowCreateChecklists = false
        )

        repository.insertFamilyRole(managerRole)
        repository.insertFamilyRole(adultRole)
        repository.insertFamilyRole(childRole)

        // 2. Family Members
        val topLeader = User(
            name = managerName,
            role = UserRole.ADULT,
            familyName = familyName,
            familyId = fId,
            roleId = managerRole.id,
            balance = 100
        )
        val managerProfileId = repository.insertUser(topLeader).toInt()

        val secondAdultName = if (isHebrew) "אמא" else "Mom"
        val firstChildName = if (isHebrew) "לאו" else "Leo"
        val secondChildName = if (isHebrew) "מיה" else "Mia"

        repository.insertUser(
            User(
                name = secondAdultName,
                role = UserRole.ADULT,
                familyName = familyName,
                familyId = fId,
                roleId = adultRole.id,
                balance = 100
            )
        )

        val kid1Id = repository.insertUser(
            User(
                name = firstChildName,
                role = UserRole.CHILD,
                familyName = familyName,
                familyId = fId,
                roleId = childRole.id,
                balance = 0
            )
        ).toInt()

        repository.insertUser(
            User(
                name = secondChildName,
                role = UserRole.CHILD,
                familyName = familyName,
                familyId = fId,
                roleId = childRole.id,
                balance = 0
            )
        )

        // 3. Rewards Seeding
        val reward1 = if (isHebrew) "זמן מסך נוסף (30 דק')" else "Extra Screen Time (30 min)"
        val reward2 = if (isHebrew) "מכונית צעצוע חדשה" else "New Toy Car"
        val reward3 = if (isHebrew) "טיול סופשבוע לפארק" else "Weekend Trip to Park"

        repository.insertRewardItem(RewardItem(title = reward1, price = 50, familyId = fId))
        repository.insertRewardItem(RewardItem(title = reward2, price = 200, familyId = fId))
        repository.insertRewardItem(RewardItem(title = reward3, price = 500, familyId = fId))

        // 4. Seeding Tasks & Daily Chores
        val cleanRoomChore = if (isHebrew) "ניקוי חדר השינה" else "Clean Bedroom"
        val projectFeedback = if (isHebrew) "משוב לפרויקט משפחתי" else "Feedback for Project"
        val groceryTaskName = if (isHebrew) "קניית מצרכים שבועיים" else "Buy Groceries"

        repository.insertTask(
            Task(
                title = cleanRoomChore,
                isChore = true,
                assignedToUserId = kid1Id,
                rewardPoints = 20,
                priority = Priority.HIGH,
                familyId = fId
            )
        )
        repository.insertTask(
            Task(
                title = projectFeedback,
                source = TaskSource.EMAIL,
                priority = Priority.URGENT,
                familyId = fId
            )
        )
        repository.insertTask(
            Task(
                title = groceryTaskName,
                source = TaskSource.MANUAL,
                priority = Priority.MEDIUM,
                familyId = fId,
                checkboxListId = -1
            )
        )

        // 5. Seeding Shopping Categories & Items
        val catFruits = if (isHebrew) "פירות וירקות" else "Fruits & Veggies"
        val catDairy = if (isHebrew) "מוצרי חלב ומקרר" else "Dairy & Fridge"
        val catBakery = if (isHebrew) "מאפה ולחם" else "Bakery & Bread"

        val catFruitsId = repository.insertCategory(ShoppingCategory(name = catFruits, familyId = fId)).toInt()
        val catDairyId = repository.insertCategory(ShoppingCategory(name = catDairy, familyId = fId)).toInt()
        val catBakeryId = repository.insertCategory(ShoppingCategory(name = catBakery, familyId = fId)).toInt()

        val itemApples = if (isHebrew) "תפוחים" else "Apples"
        val itemCucumbers = if (isHebrew) "מלפפונים" else "Cucumbers"
        val itemTomatoes = if (isHebrew) "עגבניות" else "Tomatoes"
        val itemMilk = if (isHebrew) "חלב 3%" else "Milk 3%"
        val itemCheese = if (isHebrew) "גבינה צהובה" else "Yellow Cheese"
        val itemYogurt = if (isHebrew) "יוגורט" else "Yogurt"
        val itemPitas = if (isHebrew) "פיתות" else "Pita Bread"
        val itemChallah = if (isHebrew) "חלה לשבת" else "Challah for Shabbat"
        val itemOliveOil = if (isHebrew) "שמן זית" else "Olive Oil"
        val itemSalt = if (isHebrew) "מלח" else "Salt"

        repository.insertShoppingItem(ShoppingItem(name = itemApples, categoryId = catFruitsId, position = 0, familyId = fId))
        repository.insertShoppingItem(ShoppingItem(name = itemCucumbers, categoryId = catFruitsId, position = 1, familyId = fId))
        repository.insertShoppingItem(ShoppingItem(name = itemTomatoes, categoryId = catFruitsId, position = 2, familyId = fId))

        repository.insertShoppingItem(ShoppingItem(name = itemMilk, categoryId = catDairyId, position = 0, familyId = fId))
        repository.insertShoppingItem(ShoppingItem(name = itemCheese, categoryId = catDairyId, position = 1, familyId = fId))
        repository.insertShoppingItem(ShoppingItem(name = itemYogurt, categoryId = catDairyId, position = 2, familyId = fId))

        repository.insertShoppingItem(ShoppingItem(name = itemPitas, categoryId = catBakeryId, position = 0, familyId = fId))
        repository.insertShoppingItem(ShoppingItem(name = itemChallah, categoryId = catBakeryId, position = 1, familyId = fId))

        repository.insertShoppingItem(ShoppingItem(name = itemOliveOil, categoryId = null, position = 0, familyId = fId))
        repository.insertShoppingItem(ShoppingItem(name = itemSalt, categoryId = null, position = 1, familyId = fId))

        selectUser(managerProfileId)
    }

    fun resetAllLocalData(familyName: String, managerName: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.clearSync()
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(context).clearAllTables()
                }
                prefs.edit().clear().apply()
                
                // Generate a fresh unique randomized family ID upon reset
                val generatedFamilyId = "family_" + (100000..999999).random().toString()
                _familyId.value = generatedFamilyId
                prefs.edit().putString("active_family_id", generatedFamilyId).apply()
                
                selectUser(null)
                _language.value = defaultLanguageCode
                
                val isHeb = defaultLanguageCode == "HE"
                seedFamilyData(generatedFamilyId, familyName, managerName, isHeb)
                
                prefs.edit().putBoolean("db_seeded", true).apply()
                
                try {
                    android.widget.Toast.makeText(
                        context,
                        if (isHeb) "האפליקציה אופסה בהצלחה!" else "App reset successfully!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } catch (toastEx: Exception) {
                    // Fail-safe in case of non-looper thread or UI context issues
                }
                
                onComplete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun checkForCustom664039Import(fId: String) {
        if (!fId.contains("664039")) return
        val prefKey = "imported_json_664039_$fId"
        if (prefs.getBoolean(prefKey, false)) return

        viewModelScope.launch {
            try {
                // Delete existing ones
                repository.deleteShoppingCategoriesAndItemsForFamily(fId)

                // Define categories mapping (remoteId -> localGeneratedId)
                val catMap = mutableMapOf<String, Int>()

                val categoriesToInsert = listOf(
                    "קשות" to "0c5e8fd7-8db9-45d7-940c-a08d4b6af886",
                    "שימורים" to "1770e419-ba6b-4d6f-8bb2-d7f908387287",
                    "ירקות" to "3edcf5aa-7842-423d-b6ed-57b7573d4f64",
                    "פירות" to "4050573e-e9da-4281-bdcf-b3cd88c56417",
                    "מקרר ומקפיא" to "4e9e6b0e-d2b4-4d02-9d6b-30a938be3c4b",
                    "נקיון והיגיינה" to "60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3",
                    "קפה, תה וסוכר" to "83f8aae0-6d9c-47d6-9b23-4dc4da8cc8d0",
                    "בשר ודגים" to "bcff16ff-3991-4445-91b1-e788920ccd08",
                    "תבלינים" to "1441175f-80d5-42c1-8884-84ec29ebd494",
                    "כללי לבית" to "9c25d6af-1be2-4bbe-840c-274ad73b0c30",
                    "רטבים ושמנים" to "ac977efa-f6f4-4cf7-8a6f-37ccc77de077",
                    "לחמים" to "71c6abb7-c726-4c73-966c-09f0b21fd19e",
                    "משקאות" to "7be2db92-5e8f-4b01-806d-8fe3ff801a05"
                )

                for ((name, remoteId) in categoriesToInsert) {
                    val catId = repository.insertCategory(
                        ShoppingCategory(
                            name = name,
                            remoteId = remoteId,
                            familyId = fId
                        )
                    ).toInt()
                    catMap[remoteId] = catId
                }

                // Define items with remote_parent_id, title, isCompleted
                val itemsToInsert = listOf(
                    Triple("83f8aae0-6d9c-47d6-9b23-4dc4da8cc8d0", "תיונים (של סרמוני)", true),
                    Triple("bcff16ff-3991-4445-91b1-e788920ccd08", "אונטריב", true),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "עגבניית שרי", true),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "מלפפונים", true),
                    Triple("1441175f-80d5-42c1-8884-84ec29ebd494", "קארי צהוב", true),
                    Triple("0c5e8fd7-8db9-45d7-940c-a08d4b6af886", "עדשים כתומות/ירוקות", true),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "פלפל", true),
                    Triple("0c5e8fd7-8db9-45d7-940c-a08d4b6af886", "שקדי מרק", true),
                    Triple("83f8aae0-6d9c-47d6-9b23-4dc4da8cc8d0", "שבבי קוקוס ", true),
                    Triple("1770e419-ba6b-4d6f-8bb2-d7f908387287", "ארטישוק אה לה רומנה", true),
                    Triple("4e9e6b0e-d2b4-4d02-9d6b-30a938be3c4b", "אפונה", true),
                    Triple("9c25d6af-1be2-4bbe-840c-274ad73b0c30", "תבניות לסופלה", true),
                    Triple("1770e419-ba6b-4d6f-8bb2-d7f908387287", "טונה", true),
                    Triple("0c5e8fd7-8db9-45d7-940c-a08d4b6af886", "ביסקוויטים", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "סמרטוט ריצפה", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "שמפו", true),
                    Triple("bcff16ff-3991-4445-91b1-e788920ccd08", "אנטריקוט", true),
                    Triple("ac977efa-f6f4-4cf7-8a6f-37ccc77de077", "רוטב ברביקיו", true),
                    Triple("83f8aae0-6d9c-47d6-9b23-4dc4da8cc8d0", "קקאו", true),
                    Triple("83f8aae0-6d9c-47d6-9b23-4dc4da8cc8d0", "סוכר קנים", true),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "שום מקולף", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "מרכך לשיער", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "טבליות לניקוי פלטות", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "מסיר שומנים", true),
                    Triple("1441175f-80d5-42c1-8884-84ec29ebd494", "בהרט", true),
                    Triple("83f8aae0-6d9c-47d6-9b23-4dc4da8cc8d0", "שמן קוקוס", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "שקיות אשפה", true),
                    Triple("4e9e6b0e-d2b4-4d02-9d6b-30a938be3c4b", "חלב דל לוקטוז", true),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "חציל", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "קרם לחות לשיער", true),
                    Triple("1770e419-ba6b-4d6f-8bb2-d7f908387287", "מלפפון חמוץ", true),
                    Triple("4e9e6b0e-d2b4-4d02-9d6b-30a938be3c4b", "גבינה צהובה", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "קרם גוף", true),
                    Triple("4e9e6b0e-d2b4-4d02-9d6b-30a938be3c4b", "שמנת לבישול", true),
                    Triple("0c5e8fd7-8db9-45d7-940c-a08d4b6af886", "קינואה לבנה/אדומה", true),
                    Triple("bcff16ff-3991-4445-91b1-e788920ccd08", "נקניקיות", true),
                    Triple("bcff16ff-3991-4445-91b1-e788920ccd08", "אסאדו", true),
                    Triple("0c5e8fd7-8db9-45d7-940c-a08d4b6af886", "בורגול", true),
                    Triple("bcff16ff-3991-4445-91b1-e788920ccd08", "חזה עוף טחון", true),
                    Triple("bcff16ff-3991-4445-91b1-e788920ccd08", "שניצלונים/חזה עוף פרוס", true),
                    Triple("ac977efa-f6f4-4cf7-8a6f-37ccc77de077", "חומץ", true),
                    Triple("4050573e-e9da-4281-bdcf-b3cd88c56417", "קיוי", true),
                    Triple("0c5e8fd7-8db9-45d7-940c-a08d4b6af886", "פתיתים", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "סבון כלים", true),
                    Triple("0c5e8fd7-8db9-45d7-940c-a08d4b6af886", "שמן קנולה/חמניות", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "אקונומיקה", true),
                    Triple("7be2db92-5e8f-4b01-806d-8fe3ff801a05", "מיץ תפוזים", true),
                    Triple("4050573e-e9da-4281-bdcf-b3cd88c56417", "תות", true),
                    Triple("4050573e-e9da-4281-bdcf-b3cd88c56417", "תפוחים", true),
                    Triple("9c25d6af-1be2-4bbe-840c-274ad73b0c30", "סוללות", true),
                    Triple("1441175f-80d5-42c1-8884-84ec29ebd494", "ציפורן", true),
                    Triple("4e9e6b0e-d2b4-4d02-9d6b-30a938be3c4b", "מעדנים", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "כביסכל", true),
                    Triple("ac977efa-f6f4-4cf7-8a6f-37ccc77de077", "רוטב סויה", true),
                    Triple("83f8aae0-6d9c-47d6-9b23-4dc4da8cc8d0", "שוקולד מריר", true),
                    Triple("1770e419-ba6b-4d6f-8bb2-d7f908387287", "תירס", true),
                    Triple("ac977efa-f6f4-4cf7-8a6f-37ccc77de077", "מיונז", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "נוזל הברקה למדיח", true),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "כרישה", true),
                    Triple("83f8aae0-6d9c-47d6-9b23-4dc4da8cc8d0", "קפה מגורען", true),
                    Triple("0c5e8fd7-8db9-45d7-940c-a08d4b6af886", "פסטה", true),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "גזר", true),
                    Triple("4e9e6b0e-d2b4-4d02-9d6b-30a938be3c4b", "שמנת מתוקה", true),
                    Triple("1770e419-ba6b-4d6f-8bb2-d7f908387287", "זיתים", true),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "עגבניות", true),
                    Triple("bcff16ff-3991-4445-91b1-e788920ccd08", "כרעיים/שוק/ירך/עוף שלם", true),
                    Triple("9c25d6af-1be2-4bbe-840c-274ad73b0c30", "יעה עומד", false),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "ספריי הברקה לחלונות ומראות", true),
                    Triple("1441175f-80d5-42c1-8884-84ec29ebd494", "מלח גס", true),
                    Triple("83f8aae0-6d9c-47d6-9b23-4dc4da8cc8d0", "שוקולד חלב", true),
                    Triple("4e9e6b0e-d2b4-4d02-9d6b-30a938be3c4b", "מוצרלה/צהובה מגורדת", true),
                    Triple("83f8aae0-6d9c-47d6-9b23-4dc4da8cc8d0", "קפה שחור", true),
                    Triple("0c5e8fd7-8db9-45d7-940c-a08d4b6af886", "קרקרים", true),
                    Triple("4e9e6b0e-d2b4-4d02-9d6b-30a938be3c4b", "שמנת חמוצה", true),
                    Triple("1770e419-ba6b-4d6f-8bb2-d7f908387287", "פטריות משומרות", true),
                    Triple("bcff16ff-3991-4445-91b1-e788920ccd08", "צלי כתף מספר 5", true),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "שמיר", true),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "בטטה", true),
                    Triple("83f8aae0-6d9c-47d6-9b23-4dc4da8cc8d0", "סוכר לבן", true),
                    Triple("ac977efa-f6f4-4cf7-8a6f-37ccc77de077", "שמן זית", true),
                    Triple("71c6abb7-c726-4c73-966c-09f0b21fd19e", "לחם שיפון", false),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "סלק", true),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "פטרוזיליה", true),
                    Triple("4050573e-e9da-4281-bdcf-b3cd88c56417", "מלון", true),
                    Triple("3edcf5aa-7842-423d-b6ed-57b7573d4f64", "כרוב", true),
                    Triple("9c25d6af-1be2-4bbe-840c-274ad73b0c30", "נרות שבת קטנים (למבער)", true),
                    Triple("ac977efa-f6f4-4cf7-8a6f-37ccc77de077", "סילן", true),
                    Triple("4e9e6b0e-d2b4-4d02-9d6b-30a938be3c4b", "חומוס/חומוס קפוא", true),
                    Triple("60fdfd6e-4ce4-4aad-b659-2aa57c99f8e3", "מסיכה לשיער", true),
                    Triple("4050573e-e9da-4281-bdcf-b3cd88c56417", "שזיפים", true),
                    Triple("ac977efa-f6f4-4cf7-8a6f-37ccc77de077", "עמבה", true)
                )

                for ((remoteParentId, name, isCompleted) in itemsToInsert) {
                    val localCatId = catMap[remoteParentId]
                    repository.insertShoppingItem(
                        ShoppingItem(
                            name = name,
                            categoryId = localCatId,
                            isCompleted = isCompleted,
                            familyId = fId
                        )
                    )
                }

                prefs.edit().putBoolean(prefKey, true).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        // Do not perform silent auto-seeding with static hardcoded values.
        // Instead, if there are no existing users, the applet starts up clean, allowing the onboarding
        // flow to beautifully prompt the user for their customized family and manager credentials.
        viewModelScope.launch {
            try {
                val existingUser = repository.getAnyUser()
                if (existingUser != null) {
                    // Already has users, initialize properly
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Keep custom 664039 checklist data in sync / auto-imported when active
        viewModelScope.launch {
            _familyId.collect { fId ->
                if (fId.contains("664039")) {
                    checkForCustom664039Import(fId)
                }
            }
        }
    }

    fun generateShareLink(checklistId: Int?, listTitle: String): String {
        try {
            val cats = allCategories.value.filter { it.checklistId == checklistId }
            val its = allShoppingItems.value.filter { it.checklistId == checklistId }

            val sharedCats = cats.map { cat ->
                SharedCategoryPayload(
                    name = cat.name,
                    isCompleted = cat.isCompleted,
                    position = cat.position,
                    isExpanded = cat.isExpanded
                )
            }

            val sharedIts = its.map { item ->
                val catName = cats.find { it.id == item.categoryId }?.name
                SharedItemPayload(
                    name = item.name,
                    categoryName = catName,
                    isCompleted = item.isCompleted,
                    position = item.position
                )
            }

            val payload = SharedListPayload(
                listName = listTitle,
                categories = sharedCats,
                items = sharedIts
            )

            val jsonStr = kotlinx.serialization.json.Json.encodeToString(SharedListPayload.serializer(), payload)
            val base64Str = android.util.Base64.encodeToString(jsonStr.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            return "https://taskhub.example.com/import?data=$base64Str"
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    fun importSharedList(link: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val cleanLink = link.trim()
                val base64Str = if (cleanLink.startsWith("http")) {
                    val uri = android.net.Uri.parse(cleanLink)
                    uri.getQueryParameter("data") ?: ""
                } else {
                    cleanLink
                }

                if (base64Str.isBlank()) {
                    onError("Invalid link format")
                    return@launch
                }

                val jsonBytes = android.util.Base64.decode(base64Str, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
                val jsonStr = String(jsonBytes, Charsets.UTF_8)
                val payload = kotlinx.serialization.json.Json.decodeFromString(SharedListPayload.serializer(), jsonStr)

                val currentFamilyId = familyId.value

                // Insert Checklist copy
                val checklistObj = Checklist(
                    name = payload.listName,
                    familyId = currentFamilyId
                )
                val newChecklistId = repository.insertChecklist(checklistObj).toInt()

                // Insert Categories
                val localCatMap = mutableMapOf<String, Int>()
                for (cat in payload.categories) {
                    val newCatObj = ShoppingCategory(
                        name = cat.name,
                        isCompleted = cat.isCompleted,
                        position = cat.position,
                        isExpanded = cat.isExpanded,
                        familyId = currentFamilyId,
                        checklistId = newChecklistId
                    )
                    val newCatId = repository.insertCategory(newCatObj).toInt()
                    localCatMap[cat.name] = newCatId
                }

                // Insert Items
                for (item in payload.items) {
                    val localCatId = item.categoryName?.let { localCatMap[it] }
                    val newItemObj = ShoppingItem(
                        name = item.name,
                        categoryId = localCatId,
                        isCompleted = item.isCompleted,
                        position = item.position,
                        familyId = currentFamilyId,
                        checklistId = newChecklistId
                    )
                    repository.insertShoppingItem(newItemObj)
                }

                onSuccess(payload.listName)
            } catch (e: Exception) {
                e.printStackTrace()
                onError(e.localizedMessage ?: "Unknown parsing error")
            }
        }
    }

    fun sendSharedListProposal(
        checklistId: Int?,
        listTitle: String,
        targetFamilyCode: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val cleanCode = targetFamilyCode.trim()
                if (cleanCode.isBlank()) {
                    onComplete(false, "Family code cannot be empty")
                    return@launch
                }
                val receiverFamilyId = if (cleanCode.startsWith("family_")) cleanCode else "family_$cleanCode"
                val senderId = familyId.value

                if (receiverFamilyId == senderId) {
                    onComplete(false, if (language.value == "HE") "לא ניתן לשלוח רשימה למשפחה של עצמך" else "Cannot send a list to your own family")
                    return@launch
                }

                // Get categories and items
                val cats = allCategories.value.filter { it.checklistId == checklistId }
                val its = allShoppingItems.value.filter { it.checklistId == checklistId }

                val sharedCats = cats.map { cat ->
                    SharedCategoryPayload(
                        name = cat.name,
                        isCompleted = cat.isCompleted,
                        position = cat.position,
                        isExpanded = cat.isExpanded
                    )
                }

                val sharedIts = its.map { item ->
                    val catName = cats.find { it.id == item.categoryId }?.name
                    SharedItemPayload(
                        name = item.name,
                        categoryName = catName,
                        isCompleted = item.isCompleted,
                        position = item.position
                    )
                }

                val payload = SharedListPayload(
                    listName = listTitle,
                    categories = sharedCats,
                    items = sharedIts
                )

                val payloadJson = kotlinx.serialization.json.Json.encodeToString(SharedListPayload.serializer(), payload)

                val isPrimaryShoppingList = (checklistId == null)

                val proposal = SharedListProposal(
                    name = listTitle,
                    senderFamilyId = senderId,
                    receiverFamilyId = receiverFamilyId,
                    payloadJson = payloadJson,
                    isPrimaryShoppingList = isPrimaryShoppingList,
                    status = "PENDING"
                )

                repository.insertProposal(proposal)
                onComplete(true, listTitle)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false, e.localizedMessage ?: "Unknown sending error")
            }
        }
    }

    fun approveSharedListProposal(
        proposal: SharedListProposal,
        replacePrimary: Boolean,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val payload = kotlinx.serialization.json.Json.decodeFromString(SharedListPayload.serializer(), proposal.payloadJson)
                val currentFamilyId = familyId.value

                if (replacePrimary) {
                    // Delete existing primary categories and items
                    repository.deletePrimaryShoppingList(currentFamilyId)

                    // Insert Categories
                    val localCatMap = mutableMapOf<String, Int>()
                    for (cat in payload.categories) {
                        val newCatObj = ShoppingCategory(
                            name = cat.name,
                            isCompleted = cat.isCompleted,
                            position = cat.position,
                            isExpanded = cat.isExpanded,
                            familyId = currentFamilyId,
                            checklistId = null
                        )
                        val newCatId = repository.insertCategory(newCatObj).toInt()
                        localCatMap[cat.name] = newCatId
                    }

                    // Insert Items
                    for (item in payload.items) {
                        val localCatId = item.categoryName?.let { localCatMap[it] }
                        val newItemObj = ShoppingItem(
                            name = item.name,
                            categoryId = localCatId,
                            isCompleted = item.isCompleted,
                            position = item.position,
                            familyId = currentFamilyId,
                            checklistId = null
                        )
                        repository.insertShoppingItem(newItemObj)
                    }
                } else {
                    // Import as new Checklist copy
                    val checklistObj = Checklist(
                        name = payload.listName,
                        familyId = currentFamilyId
                    )
                    val newChecklistId = repository.insertChecklist(checklistObj).toInt()

                    // Insert Categories
                    val localCatMap = mutableMapOf<String, Int>()
                    for (cat in payload.categories) {
                        val newCatObj = ShoppingCategory(
                            name = cat.name,
                            isCompleted = cat.isCompleted,
                            position = cat.position,
                            isExpanded = cat.isExpanded,
                            familyId = currentFamilyId,
                            checklistId = newChecklistId
                        )
                        val newCatId = repository.insertCategory(newCatObj).toInt()
                        localCatMap[cat.name] = newCatId
                    }

                    // Insert Items
                    for (item in payload.items) {
                        val localCatId = item.categoryName?.let { localCatMap[it] }
                        val newItemObj = ShoppingItem(
                            name = item.name,
                            categoryId = localCatId,
                            isCompleted = item.isCompleted,
                            position = item.position,
                            familyId = currentFamilyId,
                            checklistId = newChecklistId
                        )
                        repository.insertShoppingItem(newItemObj)
                    }
                }

                // Update proposal status & sync to remote, or delete since it's processed
                val updatedProposal = proposal.copy(status = "APPROVED")
                repository.updateProposalStatusRemote(updatedProposal)
                repository.deleteProposal(proposal) // delete from local/remote to clean up lists since it's successfully approved and imported!
                onComplete(true, payload.listName)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false, e.localizedMessage ?: "Unknown approval error")
            }
        }
    }

    fun rejectSharedListProposal(
        proposal: SharedListProposal,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val updatedProposal = proposal.copy(status = "REJECTED")
                repository.updateProposalStatusRemote(updatedProposal)
                repository.deleteProposal(proposal) // Also delete to keep workspace clean!
                onComplete(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete(false)
            }
        }
    }
}


class TaskViewModelFactory(
    private val repository: TaskRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
