package com.example.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class FirestoreSync(
    private val taskDao: TaskDao,
    private val userDao: UserDao,
    private val rewardDao: RewardDao,
    private val shoppingDao: ShoppingDao
) {
    private val db = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.e("FirestoreSync", "Firestore initialization failed. Running in local-only mode.", e)
        null
    }
    private val auth = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        Log.e("FirestoreSync", "FirebaseAuth initialization failed. Running in local-only mode.", e)
        null
    }
    private var familyId = "family_default" 
    private val scope = CoroutineScope(Dispatchers.IO)
    private val authMutex = Mutex()
    
    @Volatile
    private var isAuthenticating = false
    private var lastAuthAttemptTime = 0L

    // Listener registrations to allow stopping on family switch
    private var taskListener: ListenerRegistration? = null
    private var categoryListener: ListenerRegistration? = null
    private var itemListener: ListenerRegistration? = null
    private var rewardListener: ListenerRegistration? = null
    private var userListener: ListenerRegistration? = null
    private var roleListener: ListenerRegistration? = null
    private var checklistListener: ListenerRegistration? = null
    private var proposalListener: ListenerRegistration? = null

    private val taskSyncMutex = Mutex()
    private val categorySyncMutex = Mutex()
    private val itemSyncMutex = Mutex()
    private val rewardSyncMutex = Mutex()
    private val userSyncMutex = Mutex()
    private val roleSyncMutex = Mutex()
    private val checklistSyncMutex = Mutex()
    private val proposalSyncMutex = Mutex()

    init {
        scope.launch {
            ensureAuth()
            startRealtimeSync()
            runBackgroundOptimization(familyId)
            ensureFamilyDocumentExists(familyId)
        }
    }

    private suspend fun ensureAuth() {
        val auth = auth ?: return
        val wasAuthenticatedBefore = auth.currentUser != null
        if (wasAuthenticatedBefore) return
        
        authMutex.withLock {
            val isAuthNow = auth.currentUser != null
            if (isAuthNow) return
            
            isAuthenticating = true
            val now = System.currentTimeMillis()
            if (now - lastAuthAttemptTime < 15000) {
                isAuthenticating = false
                return
            }
            
            lastAuthAttemptTime = now
            try {
                withTimeoutOrNull(8000) {
                    auth.signInAnonymously().await()
                }
                if (auth.currentUser != null) {
                    Log.d("FirestoreSync", "Auth succeeded: ${auth.currentUser?.uid}")
                    if (!wasAuthenticatedBefore) {
                        // We just logged in! Restart the snapshot listeners to make sure they run with the authenticated token.
                        restartRealtimeSync()
                    }
                } else {
                    Log.w("FirestoreSync", "Auth timed out. Local mode active.")
                }
            } catch (e: Exception) {
                Log.e("FirestoreSync", "Auth failed", e)
            } finally {
                isAuthenticating = false
            }
        }
    }

    private fun restartRealtimeSync() {
        stopRealtimeSync()
        startRealtimeSync()
    }

    fun updateFamilyId(newFamilyId: String) {
        if (familyId == newFamilyId && taskListener != null) return
        familyId = newFamilyId
        stopRealtimeSync()
        scope.launch {
            ensureAuth()
            startRealtimeSync()
            runBackgroundOptimization(newFamilyId)
            ensureFamilyDocumentExists(newFamilyId)
        }
    }

    private fun ensureFamilyDocumentExists(fId: String) {
        val db = db ?: return
        scope.launch {
            ensureAuth()
            try {
                val docRef = db.collection("families").document(fId)
                val docSnapshot = try {
                    docRef.get().await()
                } catch (ex: Exception) {
                    Log.w("FirestoreSync", "Could not fetch existing family document, will merge new data", ex)
                    null
                }
                
                val existingName = docSnapshot?.getString("familyName")
                val familyUsers = userDao.getAllUsersDirect(fId)
                val localFamilyName = familyUsers.firstOrNull()?.familyName ?: "Our Family"
                
                val finalName = if (!existingName.isNullOrBlank()) existingName else localFamilyName
                
                val familyMap = mapOf(
                    "id" to fId,
                    "familyName" to finalName,
                    "lastActive" to System.currentTimeMillis()
                )
                docRef.set(familyMap, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("FirestoreSync", "Family document physical creation verified for: $fId")
                    }
                    .addOnFailureListener {
                        Log.e("FirestoreSync", "Failed to create physical family document", it)
                    }
            } catch (e: Exception) {
                Log.e("FirestoreSync", "Error ensuring family document exists", e)
            }
        }
    }

    fun clearSync() {
        stopRealtimeSync()
    }

    private fun stopRealtimeSync() {
        taskListener?.remove()
        taskListener = null
        categoryListener?.remove()
        categoryListener = null
        itemListener?.remove()
        itemListener = null
        rewardListener?.remove()
        rewardListener = null
        userListener?.remove()
        userListener = null
        roleListener?.remove()
        roleListener = null
        checklistListener?.remove()
        checklistListener = null
        proposalListener?.remove()
        proposalListener = null
    }

    suspend fun uploadTask(task: Task) {
        val db = db ?: return
        ensureAuth()
        
        val assignedUserRemoteId = task.assignedToUserId?.let { userId ->
            userDao.getUserById(userId)?.remoteId
        }
        val parentTaskRemoteId = task.parentId?.let { pId ->
            taskDao.getTaskById(pId)?.remoteId
        }
        val checkboxListRemoteId = task.checkboxListId?.let { listId ->
            shoppingDao.getChecklistById(listId)?.remoteId
        } ?: task.checkboxListRemoteId
        
        val taskId = task.remoteId ?: db.collection("tasks").document().id
        val updatedTaskWithRemoteId = task.copy(
            remoteId = taskId, 
            familyId = familyId,
            assignedToUserRemoteId = assignedUserRemoteId ?: task.assignedToUserRemoteId,
            parentRemoteId = parentTaskRemoteId ?: task.parentRemoteId,
            checkboxListRemoteId = checkboxListRemoteId
        )
        
        if (task.remoteId == null || 
            task.assignedToUserRemoteId != updatedTaskWithRemoteId.assignedToUserRemoteId || 
            task.parentRemoteId != updatedTaskWithRemoteId.parentRemoteId ||
            task.checkboxListRemoteId != updatedTaskWithRemoteId.checkboxListRemoteId) {
            taskDao.updateTask(updatedTaskWithRemoteId)
        }

        db.collection("families").document(familyId)
            .collection("tasks").document(taskId)
            .set(updatedTaskWithRemoteId)
            .addOnFailureListener { Log.e("FirestoreSync", "Upload failed", it) }
    }

    suspend fun deleteTask(task: Task) {
        val db = db ?: return
        val taskId = task.remoteId ?: return
        ensureAuth()
        try {
            db.collection("families").document(familyId)
                .collection("tasks").document(taskId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Delete remote task failed", e)
        }
    }

    suspend fun uploadCategory(category: ShoppingCategory) {
        val db = db ?: return
        ensureAuth()
        val taskRemoteId = category.taskId?.let { tId ->
            taskDao.getTaskById(tId)?.remoteId
        } ?: category.taskRemoteId
        val checklistRemoteId = category.checklistId?.let { chId ->
            shoppingDao.getChecklistById(chId)?.remoteId
        } ?: category.checklistRemoteId
        
        val catId = category.remoteId ?: db.collection("shopping_categories").document().id
        val updatedCat = category.copy(
            remoteId = catId, 
            familyId = familyId, 
            taskRemoteId = taskRemoteId,
            checklistRemoteId = checklistRemoteId
        )
        
        if (category.remoteId == null || category.taskRemoteId != updatedCat.taskRemoteId || category.checklistRemoteId != updatedCat.checklistRemoteId) {
            shoppingDao.updateCategory(updatedCat)
        }
        
        db.collection("families").document(familyId)
            .collection("shopping_categories").document(catId)
            .set(updatedCat)
            .addOnFailureListener { Log.e("FirestoreSync", "Upload category failed", it) }
    }

    suspend fun deleteCategory(category: ShoppingCategory) {
        val db = db ?: return
        val catId = category.remoteId ?: return
        ensureAuth()
        try {
            db.collection("families").document(familyId)
                .collection("shopping_categories").document(catId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Delete remote category failed", e)
        }
    }

    suspend fun uploadShoppingItem(item: ShoppingItem) {
        val db = db ?: return
        ensureAuth()
        val itemId = item.remoteId ?: db.collection("shopping_items").document().id
        
        val categoryRemoteId = item.categoryId?.let { catId ->
            shoppingDao.getCategoryById(catId)?.remoteId
        }
        val checklistRemoteId = item.checklistId?.let { chId ->
            shoppingDao.getChecklistById(chId)?.remoteId
        }
        
        val updatedItem = item.copy(
            remoteId = itemId, 
            categoryRemoteId = categoryRemoteId, 
            checklistRemoteId = checklistRemoteId,
            familyId = familyId
        )
        
        if (item.remoteId == null || item.categoryRemoteId != categoryRemoteId || item.checklistRemoteId != checklistRemoteId) {
            shoppingDao.updateItem(updatedItem)
        }
        
        db.collection("families").document(familyId)
            .collection("shopping_items").document(itemId)
            .set(updatedItem)
            .addOnFailureListener { Log.e("FirestoreSync", "Upload remote item failed", it) }
    }

    suspend fun deleteShoppingItem(item: ShoppingItem) {
        val db = db ?: return
        val itemId = item.remoteId ?: return
        ensureAuth()
        try {
            db.collection("families").document(familyId)
                .collection("shopping_items").document(itemId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Delete remote item failed", e)
        }
    }

    suspend fun uploadRewardItem(item: RewardItem) {
        val db = db ?: return
        ensureAuth()
        val itemId = item.remoteId ?: db.collection("reward_items").document().id
        val updatedItem = item.copy(remoteId = itemId, familyId = familyId)
        
        if (item.remoteId == null) {
            rewardDao.updateRewardItem(updatedItem)
        }
        
        db.collection("families").document(familyId)
            .collection("reward_items").document(itemId)
            .set(updatedItem)
            .addOnFailureListener { Log.e("FirestoreSync", "Upload remote reward failed", it) }
    }

    suspend fun deleteRewardItem(item: RewardItem) {
        val db = db ?: return
        val itemId = item.remoteId ?: return
        ensureAuth()
        try {
            db.collection("families").document(familyId)
                .collection("reward_items").document(itemId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Delete remote reward failed", e)
        }
    }

    suspend fun uploadUser(user: User) {
        val db = db ?: return
        ensureAuth()
        val userId = user.remoteId ?: db.collection("users").document().id
        val updatedUser = user.copy(remoteId = userId, familyId = familyId)
        
        if (user.remoteId == null) {
            userDao.updateUser(updatedUser)
        }
        
        db.collection("families").document(familyId)
            .collection("users").document(userId)
            .set(updatedUser)
            .addOnFailureListener { Log.e("FirestoreSync", "Upload remote user failed", it) }
    }

    suspend fun deleteUser(user: User) {
        val db = db ?: return
        val userId = user.remoteId ?: return
        ensureAuth()
        try {
            db.collection("families").document(familyId)
                .collection("users").document(userId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Delete remote user failed", e)
        }
    }

    suspend fun uploadFamilyRole(role: FamilyRole) {
        val db = db ?: return
        ensureAuth()
        val roleId = role.id.ifBlank { "${familyId}_${role.roleType.name}" }
        val updatedRole = role.copy(id = roleId, familyId = familyId)
        
        userDao.insertFamilyRole(updatedRole)
        
        db.collection("families").document(familyId)
            .collection("family_roles").document(roleId)
            .set(updatedRole)
            .addOnFailureListener { Log.e("FirestoreSync", "Upload role failed", it) }
    }

    suspend fun deleteFamilyRole(role: FamilyRole) {
        val db = db ?: return
        val roleId = role.id.ifBlank { return }
        ensureAuth()
        try {
            db.collection("families").document(familyId)
                .collection("family_roles").document(roleId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Delete remote role failed", e)
        }
    }

    suspend fun uploadChecklist(checklist: Checklist) {
        val db = db ?: return
        ensureAuth()
        val taskRemoteId = checklist.taskId?.let { tId ->
            taskDao.getTaskById(tId)?.remoteId
        } ?: checklist.taskRemoteId
        
        val checklistId = checklist.remoteId ?: db.collection("checklists").document().id
        val updatedChecklist = checklist.copy(remoteId = checklistId, familyId = familyId, taskRemoteId = taskRemoteId)
        
        if (checklist.remoteId == null || checklist.taskRemoteId != updatedChecklist.taskRemoteId) {
            shoppingDao.updateChecklist(updatedChecklist)
        }
        
        db.collection("families").document(familyId)
            .collection("checklists").document(checklistId)
            .set(updatedChecklist)
            .addOnFailureListener { Log.e("FirestoreSync", "Upload checklist failed", it) }
    }

    suspend fun deleteChecklist(checklist: Checklist) {
        val db = db ?: return
        val checklistId = checklist.remoteId ?: return
        ensureAuth()
        try {
            db.collection("families").document(familyId)
                .collection("checklists").document(checklistId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Delete remote checklist failed", e)
        }
    }

    suspend fun uploadProposal(proposal: SharedListProposal) {
        val db = db ?: return
        ensureAuth()
        val proposalId = proposal.remoteId ?: db.collection("shared_list_proposals").document().id
        val updatedProposal = proposal.copy(remoteId = proposalId)
        
        if (proposal.remoteId == null) {
            shoppingDao.updateProposal(updatedProposal)
        }
        
        db.collection("families").document(proposal.receiverFamilyId)
            .collection("shared_list_proposals").document(proposalId)
            .set(updatedProposal)
            .addOnFailureListener { Log.e("FirestoreSync", "Upload shared list proposal failed", it) }
    }

    suspend fun updateProposalStatusRemote(proposal: SharedListProposal) {
        val db = db ?: return
        val proposalId = proposal.remoteId ?: return
        ensureAuth()
        db.collection("families").document(proposal.receiverFamilyId)
            .collection("shared_list_proposals").document(proposalId)
            .set(proposal)
            .addOnFailureListener { Log.e("FirestoreSync", "Update proposal failed", it) }
    }

    suspend fun deleteProposalRemote(proposal: SharedListProposal) {
        val db = db ?: return
        val proposalId = proposal.remoteId ?: return
        ensureAuth()
        try {
            db.collection("families").document(proposal.receiverFamilyId)
                .collection("shared_list_proposals").document(proposalId)
                .delete()
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSync", "Delete remote proposal failed", e)
        }
    }

    private fun startRealtimeSync() {
        val db = db ?: return
        if (auth?.currentUser == null) {
            Log.w("FirestoreSync", "startRealtimeSync called but user is not authenticated yet. Skipping listener setup.")
            return
        }
        // Tasks Real-time Sync
        taskListener = db.collection("families").document(familyId).collection("tasks")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirestoreSync", "Listen failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }

                scope.launch {
                    try {
                        snapshot?.documentChanges?.forEach { change ->
                            try {
                                val remoteTask = change.document.toObject(Task::class.java)
                                try {
                                    val localTask = (taskDao.getTaskByRemoteId(remoteTask.remoteId ?: ""))
                                        ?: taskDao.getTaskByTitleAndFamily(remoteTask.title, familyId, remoteTask.isChore)
                                    
                                    val resolvedAssigneeId = remoteTask.assignedToUserRemoteId?.let { userRemoteId ->
                                        userDao.getUserByRemoteId(userRemoteId, familyId)?.id
                                    } ?: remoteTask.assignedToUserId

                                    val resolvedParentId = remoteTask.parentRemoteId?.let { pRemoteId ->
                                        taskDao.getTaskByRemoteId(pRemoteId)?.id
                                    } ?: remoteTask.parentId

                                    val resolvedCheckboxListId = remoteTask.checkboxListRemoteId?.let { listRemoteId ->
                                        shoppingDao.getChecklistByRemoteId(listRemoteId)?.id
                                    } ?: remoteTask.checkboxListId
                                    
                                    val finalTaskToSave = remoteTask.copy(
                                        assignedToUserId = resolvedAssigneeId,
                                        parentId = resolvedParentId,
                                        checkboxListId = resolvedCheckboxListId
                                    )

                                    when (change.type) {
                                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                            if (localTask == null) {
                                                taskDao.insertTask(finalTaskToSave.copy(id = 0, familyId = familyId)) 
                                            } else if (remoteTask.createdAt > localTask.createdAt || true) {
                                                taskDao.updateTask(finalTaskToSave.copy(id = localTask.id, familyId = familyId))
                                            }
                                        }
                                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                            localTask?.let { taskDao.deleteTask(it) }
                                        }
                                    }
                                } catch (dbEx: Exception) {
                                    Log.e("FirestoreSync", "Database operation failed within sync coroutine", dbEx)
                                }
                            } catch (parseEx: Exception) {
                                Log.e("FirestoreSync", "Could not deserialize document: ${change.document.id}", parseEx)
                            }
                        }
                    } catch (generalEx: Exception) {
                        Log.e("FirestoreSync", "Error handling document changes snapshot", generalEx)
                    }
                }
            }

        // Shopping Categories Real-time Sync
        categoryListener = db.collection("families").document(familyId).collection("shopping_categories")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirestoreSync", "Listen categories failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }
                scope.launch {
                    try {
                        snapshot?.documentChanges?.forEach { change ->
                            try {
                                val remoteCategory = change.document.toObject(ShoppingCategory::class.java)
                                try {
                                    val resolvedTaskId = remoteCategory.taskRemoteId?.let { tRemoteId ->
                                        taskDao.getTaskByRemoteId(tRemoteId)?.id
                                    } ?: remoteCategory.taskId

                                    val resolvedChecklistId = remoteCategory.checklistRemoteId?.let { chRemoteId ->
                                        shoppingDao.getChecklistByRemoteId(chRemoteId)?.id
                                    } ?: remoteCategory.checklistId

                                    val localCategory = shoppingDao.getCategoryByRemoteId(remoteCategory.remoteId ?: "")
                                        ?: shoppingDao.getCategoryByNameChecklistAndFamily(remoteCategory.name, resolvedChecklistId, familyId)

                                    val finalCategoryToSave = remoteCategory.copy(taskId = resolvedTaskId, checklistId = resolvedChecklistId)

                                    when (change.type) {
                                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                            val catId = if (localCategory == null) {
                                                shoppingDao.insertCategory(finalCategoryToSave.copy(id = 0, familyId = familyId)).toInt()
                                            } else {
                                                shoppingDao.updateCategory(finalCategoryToSave.copy(id = localCategory.id, familyId = familyId, isExpanded = localCategory.isExpanded))
                                                localCategory.id
                                            }
                                            shoppingDao.getItemsWithCategoryRemoteId(remoteCategory.remoteId ?: "").forEach { item ->
                                                if (item.categoryId != catId) {
                                                    shoppingDao.updateItem(item.copy(categoryId = catId, familyId = familyId))
                                                }
                                            }
                                        }
                                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                            localCategory?.let { shoppingDao.deleteCategory(it) }
                                        }
                                    }
                                } catch (dbEx: Exception) {
                                    Log.e("FirestoreSync", "Cat db sync error", dbEx)
                                }
                            } catch (parseEx: Exception) {
                                Log.e("FirestoreSync", "Could not deserialize category", parseEx)
                            }
                        }
                    } catch (generalEx: Exception) {
                        Log.e("FirestoreSync", "Error in categories sync listener", generalEx)
                    }
                }
            }

        // Shopping Items Real-time Sync
        itemListener = db.collection("families").document(familyId).collection("shopping_items")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirestoreSync", "Listen items failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }
                scope.launch {
                    itemSyncMutex.withLock {
                        try {
                            snapshot?.documentChanges?.forEach { change ->
                                try {
                                    val remoteItem = change.document.toObject(ShoppingItem::class.java)
                                    try {
                                        val resolvedCategoryId = remoteItem.categoryRemoteId?.let { catRemoteId ->
                                            shoppingDao.getCategoryByRemoteId(catRemoteId)?.id
                                        }
                                        val resolvedChecklistId = remoteItem.checklistRemoteId?.let { chRemoteId ->
                                            shoppingDao.getChecklistByRemoteId(chRemoteId)?.id
                                        }
                                        val localItem = shoppingDao.getItemByRemoteId(remoteItem.remoteId ?: "")
                                            ?: shoppingDao.getItemByNameCategoryChecklistAndFamily(remoteItem.name, resolvedCategoryId, resolvedChecklistId, familyId)
                                        
                                        when (change.type) {
                                            com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                            com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                                if (localItem == null) {
                                                    shoppingDao.insertItem(remoteItem.copy(id = 0, categoryId = resolvedCategoryId, checklistId = resolvedChecklistId, familyId = familyId))
                                                } else {
                                                    shoppingDao.updateItem(remoteItem.copy(id = localItem.id, categoryId = resolvedCategoryId, checklistId = resolvedChecklistId, familyId = familyId))
                                                }
                                            }
                                            com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                                localItem?.let { shoppingDao.deleteItem(it) }
                                            }
                                        }
                                    } catch (dbEx: Exception) {
                                        Log.e("FirestoreSync", "Item db sync error", dbEx)
                                    }
                                } catch (parseEx: Exception) {
                                    Log.e("FirestoreSync", "Could not deserialize item", parseEx)
                                }
                            }
                        } catch (generalEx: Exception) {
                            Log.e("FirestoreSync", "Error in items sync listener", generalEx)
                        }
                    }
                }
            }

        // Reward Items Real-time Sync
        rewardListener = db.collection("families").document(familyId).collection("reward_items")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirestoreSync", "Listen rewards failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }
                scope.launch {
                    try {
                        snapshot?.documentChanges?.forEach { change ->
                            try {
                                val remoteReward = change.document.toObject(RewardItem::class.java)
                                try {
                                    val localReward = (remoteReward.remoteId?.let { rewardDao.getRewardItemByRemoteId(it) })
                                        ?: rewardDao.getRewardItemByTitleAndFamily(remoteReward.title, familyId)
                                    
                                    when (change.type) {
                                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                            if (localReward == null) {
                                                rewardDao.insertRewardItem(remoteReward.copy(id = 0, familyId = familyId))
                                            } else {
                                                rewardDao.updateRewardItem(remoteReward.copy(id = localReward.id, familyId = familyId))
                                            }
                                        }
                                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                            localReward?.let { rewardDao.deleteRewardItem(it) }
                                        }
                                    }
                                } catch (dbEx: Exception) {
                                    Log.e("FirestoreSync", "Reward db sync error", dbEx)
                                }
                            } catch (parseEx: Exception) {
                                Log.e("FirestoreSync", "Could not deserialize reward item", parseEx)
                            }
                        }
                    } catch (generalEx: Exception) {
                        Log.e("FirestoreSync", "Error in rewards sync listener", generalEx)
                    }
                }
            }

        // Users Profiles Real-time Sync
        userListener = db.collection("families").document(familyId).collection("users")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirestoreSync", "Listen users failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }
                scope.launch {
                    try {
                        snapshot?.documentChanges?.forEach { change ->
                            try {
                                val remoteUser = change.document.toObject(User::class.java)
                                try {
                                    val localUser = if (remoteUser.remoteId != null) {
                                        userDao.getUserByRemoteId(remoteUser.remoteId, familyId)
                                    } else {
                                        userDao.getUserByNameAndFamily(remoteUser.name, familyId)
                                    }
                                    when (change.type) {
                                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                            if (localUser == null) {
                                                userDao.insertUser(remoteUser.copy(id = 0, familyId = familyId))
                                            } else {
                                                userDao.updateUser(remoteUser.copy(id = localUser.id, familyId = familyId))
                                            }
                                        }
                                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                            localUser?.let { userDao.deleteUser(it) }
                                        }
                                    }
                                } catch (dbEx: Exception) {
                                    Log.e("FirestoreSync", "User db sync error", dbEx)
                                }
                            } catch (parseEx: Exception) {
                                Log.e("FirestoreSync", "Could not deserialize user profile", parseEx)
                            }
                        }
                    } catch (generalEx: Exception) {
                        Log.e("FirestoreSync", "Error in users sync listener", generalEx)
                    }
                }
            }

        // Family Roles Real-time Sync
        roleListener = db.collection("families").document(familyId).collection("family_roles")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirestoreSync", "Listen roles failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }
                scope.launch {
                    try {
                        snapshot?.documentChanges?.forEach { change ->
                            try {
                                val remoteRole = change.document.toObject(FamilyRole::class.java)
                                try {
                                    val localRole = remoteRole.id.let { userDao.getFamilyRoleById(it) }
                                    when (change.type) {
                                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                            userDao.insertFamilyRole(remoteRole.copy(familyId = familyId))
                                        }
                                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                            localRole?.let { userDao.deleteFamilyRole(it) }
                                        }
                                    }
                                } catch (dbEx: Exception) {
                                    Log.e("FirestoreSync", "Role db sync error", dbEx)
                                }
                            } catch (parseEx: Exception) {
                                Log.e("FirestoreSync", "Could not deserialize family role", parseEx)
                            }
                        }
                    } catch (generalEx: Exception) {
                        Log.e("FirestoreSync", "Error in roles sync listener", generalEx)
                    }
                }
            }

        // Checklists Real-time Sync
        checklistListener = db.collection("families").document(familyId).collection("checklists")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirestoreSync", "Listen checklists failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }
                scope.launch {
                    try {
                        snapshot?.documentChanges?.forEach { change ->
                            try {
                                val remoteChecklist = change.document.toObject(Checklist::class.java)
                                try {
                                    val localChecklist = shoppingDao.getChecklistByRemoteId(remoteChecklist.remoteId ?: "")
                                        ?: shoppingDao.getChecklistByNameAndFamily(remoteChecklist.name, familyId)
                                    
                                    val resolvedTaskId = remoteChecklist.taskRemoteId?.let { tRemoteId ->
                                        taskDao.getTaskByRemoteId(tRemoteId)?.id
                                    } ?: remoteChecklist.taskId

                                    val finalChecklistToSave = remoteChecklist.copy(taskId = resolvedTaskId)

                                    when (change.type) {
                                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                            val chId = if (localChecklist == null) {
                                                shoppingDao.insertChecklist(finalChecklistToSave.copy(id = 0, familyId = familyId)).toInt()
                                            } else {
                                                shoppingDao.updateChecklist(finalChecklistToSave.copy(id = localChecklist.id, familyId = familyId))
                                                localChecklist.id
                                            }
                                            shoppingDao.getCategoriesWithChecklistRemoteId(remoteChecklist.remoteId ?: "").forEach { cat ->
                                                if (cat.checklistId != chId) {
                                                    shoppingDao.updateCategory(cat.copy(checklistId = chId, familyId = familyId))
                                                }
                                            }
                                            shoppingDao.getItemsWithChecklistRemoteId(remoteChecklist.remoteId ?: "").forEach { item ->
                                                if (item.checklistId != chId) {
                                                    shoppingDao.updateItem(item.copy(checklistId = chId, familyId = familyId))
                                                }
                                            }
                                        }
                                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                            localChecklist?.let { shoppingDao.deleteChecklist(it) }
                                        }
                                    }
                                } catch (dbEx: Exception) {
                                    Log.e("FirestoreSync", "Checklist db sync error", dbEx)
                                }
                            } catch (parseEx: Exception) {
                                Log.e("FirestoreSync", "Could not deserialize checklist", parseEx)
                            }
                        }
                    } catch (generalEx: Exception) {
                        Log.e("FirestoreSync", "Error in checklists sync listener", generalEx)
                    }
                }
            }

        // Shared List Proposals Real-time Sync
        proposalListener = db.collection("families").document(familyId).collection("shared_list_proposals")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirestoreSync", "Listen shared_list_proposals failed.", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.metadata.hasPendingWrites()) {
                    return@addSnapshotListener
                }
                scope.launch {
                    try {
                        snapshot?.documentChanges?.forEach { change ->
                            try {
                                val remoteProposal = change.document.toObject(SharedListProposal::class.java)
                                try {
                                    val localProposal = shoppingDao.getProposalByRemoteId(remoteProposal.remoteId ?: "")
                                    
                                    when (change.type) {
                                        com.google.firebase.firestore.DocumentChange.Type.ADDED,
                                        com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                            if (localProposal == null) {
                                                shoppingDao.insertProposal(remoteProposal.copy(id = 0))
                                            } else {
                                                shoppingDao.updateProposal(remoteProposal.copy(id = localProposal.id))
                                            }
                                        }
                                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                            localProposal?.let { shoppingDao.deleteProposal(it) }
                                        }
                                    }
                                } catch (dbEx: Exception) {
                                    Log.e("FirestoreSync", "Shared list proposal db sync error", dbEx)
                                }
                            } catch (parseEx: Exception) {
                                Log.e("FirestoreSync", "Could not deserialize shared list proposal", parseEx)
                            }
                        }
                    } catch (generalEx: Exception) {
                        Log.e("FirestoreSync", "Error in shared_list_proposals sync listener", generalEx)
                    }
                }
            }
    }

    suspend fun runBackgroundOptimization(fId: String) {
        val db = db ?: return
        try {
            Log.d("FirestoreSync", "Starting background data optimization/healing for family: $fId")

            // 1. Optimize Users
            try {
                val usersSnapshot = db.collection("families").document(fId).collection("users").get().await()
                val remoteUsers = usersSnapshot.documents.mapNotNull { it.toObject(User::class.java) }
                val remoteUserIds = remoteUsers.mapNotNull { it.remoteId }.toSet()

                remoteUsers.forEach { remoteUser ->
                    val localUser = userDao.getUserByRemoteId(remoteUser.remoteId ?: "", fId)
                        ?: userDao.getUserByNameAndFamily(remoteUser.name, fId)

                    val finalUserToSave = remoteUser.copy(familyId = fId)

                    if (localUser == null) {
                        userDao.insertUser(finalUserToSave.copy(id = 0))
                    } else {
                        if (localUser.name != remoteUser.name || localUser.balance != remoteUser.balance || localUser.role != remoteUser.role || localUser.remoteId != remoteUser.remoteId) {
                            userDao.updateUser(finalUserToSave.copy(id = localUser.id))
                        }
                    }
                }

                val localUsers = userDao.getAllUsersDirect(fId)
                localUsers.forEach { localUser ->
                    if (!localUser.remoteId.isNullOrBlank() && !remoteUserIds.contains(localUser.remoteId)) {
                        userDao.deleteUser(localUser)
                    }
                }
            } catch (e: Exception) {
                Log.e("FirestoreSync", "Error in background users optimization: ${e.message}", e)
            }

            // 2. Optimize Family Roles
            try {
                val rolesSnapshot = db.collection("families").document(fId).collection("family_roles").get().await()
                val remoteRoles = rolesSnapshot.documents.mapNotNull { it.toObject(FamilyRole::class.java) }

                remoteRoles.forEach { remoteRole ->
                    userDao.insertFamilyRole(remoteRole.copy(familyId = fId))
                }
            } catch (e: Exception) {
                Log.e("FirestoreSync", "Error in background family roles optimization: ${e.message}", e)
            }

            // 3. Optimize Tasks
            try {
                val tasksSnapshot = db.collection("families").document(fId).collection("tasks").get().await()
                val remoteTasks = tasksSnapshot.documents.mapNotNull { it.toObject(Task::class.java) }
                val remoteTaskIds = remoteTasks.mapNotNull { it.remoteId }.toSet()

                remoteTasks.forEach { remoteTask ->
                    val localTask = taskDao.getTaskByRemoteId(remoteTask.remoteId ?: "")
                        ?: taskDao.getTaskByTitleAndFamily(remoteTask.title, fId, remoteTask.isChore)

                    val resolvedAssigneeId = remoteTask.assignedToUserRemoteId?.let { userRemoteId ->
                        userDao.getUserByRemoteId(userRemoteId, fId)?.id
                    } ?: remoteTask.assignedToUserId

                    val resolvedParentId = remoteTask.parentRemoteId?.let { pRemoteId ->
                        taskDao.getTaskByRemoteId(pRemoteId)?.id
                    } ?: remoteTask.parentId

                    val resolvedCheckboxListId = remoteTask.checkboxListRemoteId?.let { listRemoteId ->
                        shoppingDao.getChecklistByRemoteId(listRemoteId)?.id
                    } ?: remoteTask.checkboxListId

                    val finalTaskToSave = remoteTask.copy(
                        assignedToUserId = resolvedAssigneeId,
                        parentId = resolvedParentId,
                        checkboxListId = resolvedCheckboxListId,
                        familyId = fId
                    )

                    if (localTask == null) {
                        taskDao.insertTask(finalTaskToSave.copy(id = 0))
                    } else {
                        if (localTask.title != remoteTask.title ||
                            localTask.description != remoteTask.description ||
                            localTask.status != remoteTask.status ||
                            localTask.rewardPoints != remoteTask.rewardPoints ||
                            localTask.assignedToUserId != resolvedAssigneeId ||
                            localTask.parentId != resolvedParentId ||
                            localTask.checkboxListId != resolvedCheckboxListId ||
                            localTask.completedAt != remoteTask.completedAt ||
                            localTask.remoteId != remoteTask.remoteId
                        ) {
                            taskDao.updateTask(finalTaskToSave.copy(id = localTask.id))
                        }
                    }
                }

                val localTasks = taskDao.getAllTasksDirect(fId)
                localTasks.forEach { localTask ->
                    if (!localTask.remoteId.isNullOrBlank() && !remoteTaskIds.contains(localTask.remoteId)) {
                        taskDao.deleteTask(localTask)
                    }
                }
            } catch (e: Exception) {
                Log.e("FirestoreSync", "Error in background tasks optimization: ${e.message}", e)
            }

            // 4. Optimize Checklists
            try {
                val checklistsSnapshot = db.collection("families").document(fId).collection("checklists").get().await()
                val remoteChecklists = checklistsSnapshot.documents.mapNotNull { it.toObject(Checklist::class.java) }
                val remoteChecklistIds = remoteChecklists.mapNotNull { it.remoteId }.toSet()

                remoteChecklists.forEach { remoteChecklist ->
                    val resolvedTaskId = remoteChecklist.taskRemoteId?.let { tRemoteId ->
                        taskDao.getTaskByRemoteId(tRemoteId)?.id
                    } ?: remoteChecklist.taskId

                    val localChecklist = shoppingDao.getChecklistByRemoteId(remoteChecklist.remoteId ?: "")
                        ?: shoppingDao.getChecklistByNameAndFamily(remoteChecklist.name, fId)

                    val finalChecklistToSave = remoteChecklist.copy(
                        taskId = resolvedTaskId,
                        familyId = fId
                    )

                    val chId = if (localChecklist == null) {
                        shoppingDao.insertChecklist(finalChecklistToSave.copy(id = 0)).toInt()
                    } else {
                        if (localChecklist.name != remoteChecklist.name ||
                            localChecklist.isPrivate != remoteChecklist.isPrivate ||
                            localChecklist.taskId != resolvedTaskId ||
                            localChecklist.remoteId != remoteChecklist.remoteId
                        ) {
                            shoppingDao.updateChecklist(finalChecklistToSave.copy(id = localChecklist.id))
                        }
                        localChecklist.id
                    }

                    shoppingDao.getCategoriesWithChecklistRemoteId(remoteChecklist.remoteId ?: "").forEach { cat ->
                        if (cat.checklistId != chId) {
                            shoppingDao.updateCategory(cat.copy(checklistId = chId, familyId = fId))
                        }
                    }

                    shoppingDao.getItemsWithChecklistRemoteId(remoteChecklist.remoteId ?: "").forEach { item ->
                        if (item.checklistId != chId) {
                            shoppingDao.updateItem(item.copy(checklistId = chId, familyId = fId))
                        }
                    }
                }

                val localChecklists = shoppingDao.getAllChecklistsDirect(fId)
                localChecklists.forEach { localChecklist ->
                    if (!localChecklist.remoteId.isNullOrBlank() && !remoteChecklistIds.contains(localChecklist.remoteId)) {
                        shoppingDao.deleteChecklist(localChecklist)
                    }
                }
            } catch (e: Exception) {
                Log.e("FirestoreSync", "Error in background checklists optimization: ${e.message}", e)
            }

            // 5. Optimize Shopping Categories
            try {
                val catsSnapshot = db.collection("families").document(fId).collection("shopping_categories").get().await()
                val remoteCats = catsSnapshot.documents.mapNotNull { it.toObject(ShoppingCategory::class.java) }
                val remoteCatIds = remoteCats.mapNotNull { it.remoteId }.toSet()

                remoteCats.forEach { remoteCategory ->
                    val resolvedTaskId = remoteCategory.taskRemoteId?.let { tRemoteId ->
                        taskDao.getTaskByRemoteId(tRemoteId)?.id
                    } ?: remoteCategory.taskId

                    val resolvedChecklistId = remoteCategory.checklistRemoteId?.let { chRemoteId ->
                        shoppingDao.getChecklistByRemoteId(chRemoteId)?.id
                    } ?: remoteCategory.checklistId

                    val localCategory = shoppingDao.getCategoryByRemoteId(remoteCategory.remoteId ?: "")
                        ?: shoppingDao.getCategoryByNameChecklistAndFamily(remoteCategory.name, resolvedChecklistId, fId)

                    val finalCategoryToSave = remoteCategory.copy(
                        taskId = resolvedTaskId,
                        checklistId = resolvedChecklistId,
                        familyId = fId
                    )

                    val catId = if (localCategory == null) {
                        shoppingDao.insertCategory(finalCategoryToSave.copy(id = 0)).toInt()
                    } else {
                        if (localCategory.name != remoteCategory.name ||
                            localCategory.isCompleted != remoteCategory.isCompleted ||
                            localCategory.checklistId != resolvedChecklistId ||
                            localCategory.remoteId != remoteCategory.remoteId
                        ) {
                            shoppingDao.updateCategory(finalCategoryToSave.copy(id = localCategory.id, isExpanded = localCategory.isExpanded))
                        }
                        localCategory.id
                    }

                    shoppingDao.getItemsWithCategoryRemoteId(remoteCategory.remoteId ?: "").forEach { item ->
                        if (item.categoryId != catId) {
                            shoppingDao.updateItem(item.copy(categoryId = catId, familyId = fId))
                        }
                    }
                }

                val localCats = shoppingDao.getAllCategoriesDirect(fId)
                localCats.forEach { localCat ->
                    if (!localCat.remoteId.isNullOrBlank() && !remoteCatIds.contains(localCat.remoteId)) {
                        shoppingDao.deleteCategory(localCat)
                    }
                }
            } catch (e: Exception) {
                Log.e("FirestoreSync", "Error in background shopping categories optimization: ${e.message}", e)
            }

            // 6. Optimize Shopping Items
            try {
                val itemsSnapshot = db.collection("families").document(fId).collection("shopping_items").get().await()
                val remoteItems = itemsSnapshot.documents.mapNotNull { it.toObject(ShoppingItem::class.java) }
                val remoteItemIds = remoteItems.mapNotNull { it.remoteId }.toSet()

                remoteItems.forEach { remoteItem ->
                    val resolvedCategoryId = remoteItem.categoryRemoteId?.let { catRemoteId ->
                        shoppingDao.getCategoryByRemoteId(catRemoteId)?.id
                    } ?: remoteItem.categoryId

                    val resolvedChecklistId = remoteItem.checklistRemoteId?.let { chRemoteId ->
                        shoppingDao.getChecklistByRemoteId(chRemoteId)?.id
                    } ?: remoteItem.checklistId

                    val localItem = shoppingDao.getItemByRemoteId(remoteItem.remoteId ?: "")
                        ?: shoppingDao.getItemByNameCategoryChecklistAndFamily(remoteItem.name, resolvedCategoryId, resolvedChecklistId, fId)

                    val finalItemToSave = remoteItem.copy(
                        categoryId = resolvedCategoryId,
                        checklistId = resolvedChecklistId,
                        familyId = fId
                    )

                    if (localItem == null) {
                        shoppingDao.insertItem(finalItemToSave.copy(id = 0))
                    } else {
                        if (localItem.name != remoteItem.name ||
                            localItem.isCompleted != remoteItem.isCompleted ||
                            localItem.categoryId != resolvedCategoryId ||
                            localItem.checklistId != resolvedChecklistId ||
                            localItem.remoteId != remoteItem.remoteId
                        ) {
                            shoppingDao.updateItem(finalItemToSave.copy(id = localItem.id))
                        }
                    }
                }

                val localItems = shoppingDao.getAllShoppingItemsDirect(fId)
                localItems.forEach { localItem ->
                    if (!localItem.remoteId.isNullOrBlank() && !remoteItemIds.contains(localItem.remoteId)) {
                        shoppingDao.deleteItem(localItem)
                    }
                }
            } catch (e: Exception) {
                Log.e("FirestoreSync", "Error in background shopping items optimization: ${e.message}", e)
            }

            // 7. Optimize Reward Items
            try {
                val rewardsSnapshot = db.collection("families").document(fId).collection("reward_items").get().await()
                val remoteRewards = rewardsSnapshot.documents.mapNotNull { it.toObject(RewardItem::class.java) }
                val remoteRewardIds = remoteRewards.mapNotNull { it.remoteId }.toSet()

                remoteRewards.forEach { remoteReward ->
                    val localReward = rewardDao.getRewardItemByRemoteId(remoteReward.remoteId ?: "")
                        ?: rewardDao.getRewardItemByTitleAndFamily(remoteReward.title, fId)

                    val finalRewardToSave = remoteReward.copy(familyId = fId)

                    if (localReward == null) {
                        rewardDao.insertRewardItem(finalRewardToSave.copy(id = 0))
                    } else {
                        if (localReward.title != remoteReward.title ||
                            localReward.price != remoteReward.price ||
                            localReward.description != remoteReward.description ||
                            localReward.imageUrl != remoteReward.imageUrl ||
                            localReward.remoteId != remoteReward.remoteId
                        ) {
                            rewardDao.updateRewardItem(finalRewardToSave.copy(id = localReward.id))
                        }
                    }
                }

                val localRewards = rewardDao.getRewardItemsDirect(fId)
                localRewards.forEach { localReward ->
                    if (!localReward.remoteId.isNullOrBlank() && !remoteRewardIds.contains(localReward.remoteId)) {
                        rewardDao.deleteRewardItem(localReward)
                    }
                }
            } catch (e: Exception) {
                Log.e("FirestoreSync", "Error in background reward items optimization: ${e.message}", e)
            }

            Log.d("FirestoreSync", "Background data optimization/healing for family $fId completed!")
        } catch (generalEx: Exception) {
            Log.e("FirestoreSync", "Failed overall background data optimization: ${generalEx.message}", generalEx)
        }
    }
}
