package com.example.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val taskDao: TaskDao,
    private val userDao: UserDao,
    private val rewardDao: RewardDao,
    private val shoppingDao: ShoppingDao,
    private val firestoreSync: FirestoreSync
) {
    fun updateSyncFamilyId(familyId: String) {
        firestoreSync.updateFamilyId(familyId)
    }

    fun clearSync() {
        firestoreSync.clearSync()
    }

    fun getAllMainTasks(familyId: String): Flow<List<Task>> = taskDao.getAllMainTasks(familyId)
    fun getAllChores(familyId: String): Flow<List<Task>> = taskDao.getAllChores(familyId)
    suspend fun getAllTasksDirect(familyId: String): List<Task> = taskDao.getAllTasksDirect(familyId)
    fun getAllUsers(familyId: String): Flow<List<User>> = userDao.getAllUsers(familyId)
    fun getRewardItems(familyId: String): Flow<List<RewardItem>> = rewardDao.getAllRewardItems(familyId)

    // Shopping list streams
    fun getAllCategories(familyId: String): Flow<List<ShoppingCategory>> = shoppingDao.getAllCategories(familyId)
    fun getAllShoppingItems(familyId: String): Flow<List<ShoppingItem>> = shoppingDao.getAllItems(familyId)

    suspend fun getItemsByCategoryId(categoryId: Int): List<ShoppingItem> {
        return shoppingDao.getItemsByCategoryId(categoryId)
    }

    suspend fun getCategoryById(id: Int): ShoppingCategory? {
        return shoppingDao.getCategoryById(id)
    }

    suspend fun insertCategory(category: ShoppingCategory): Long {
        val id = shoppingDao.insertCategory(category)
        val newCat = shoppingDao.getCategoryById(id.toInt())
        if (newCat != null) {
            firestoreSync.uploadCategory(newCat)
        }
        return id
    }

    suspend fun updateCategory(category: ShoppingCategory) {
        shoppingDao.updateCategory(category)
        firestoreSync.uploadCategory(category)
    }

    suspend fun deleteCategory(category: ShoppingCategory) {
        val items = shoppingDao.getItemsByCategoryId(category.id)
        items.forEach { item ->
            val updatedItem = item.copy(categoryId = null)
            shoppingDao.updateItem(updatedItem)
            firestoreSync.uploadShoppingItem(updatedItem)
        }
        shoppingDao.deleteCategory(category)
        firestoreSync.deleteCategory(category)
    }

    suspend fun insertShoppingItem(item: ShoppingItem): Long {
        val id = shoppingDao.insertItem(item)
        val newItem = shoppingDao.getItemById(id.toInt())
        if (newItem != null) {
            firestoreSync.uploadShoppingItem(newItem)
        }
        return id
    }

    suspend fun updateShoppingItem(item: ShoppingItem) {
        shoppingDao.updateItem(item)
        firestoreSync.uploadShoppingItem(item)
    }

    suspend fun deleteShoppingItem(item: ShoppingItem) {
        shoppingDao.deleteItem(item)
        firestoreSync.deleteShoppingItem(item)
    }

    suspend fun insertTask(task: Task) {
        val id = taskDao.insertTask(task)
        val newTask = taskDao.getTaskById(id.toInt())
        if (newTask != null) {
            firestoreSync.uploadTask(newTask)
        }
    }

    suspend fun updateTask(task: Task) {
        taskDao.updateTask(task)
        firestoreSync.uploadTask(task)
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
        firestoreSync.deleteTask(task)
    }

    suspend fun getTaskById(id: Int) = taskDao.getTaskById(id)

    fun getSubtasks(familyId: String, parentId: Int) = taskDao.getSubtasks(familyId, parentId)

    suspend fun completeTask(task: Task) {
        val updatedTask = if (task.isChore) {
            task.copy(status = TaskStatus.PENDING_APPROVAL, lastCompletedTimestamp = System.currentTimeMillis())
        } else {
            task.copy(status = TaskStatus.DONE, completedAt = System.currentTimeMillis(), lastCompletedTimestamp = System.currentTimeMillis())
        }
        updateTask(updatedTask)
    }

    suspend fun uncompleteTask(task: Task) {
        val updatedTask = task.copy(status = TaskStatus.TODO, completedAt = null, lastCompletedTimestamp = null)
        updateTask(updatedTask)
    }

    suspend fun unapproveChore(task: Task) {
        if (task.status == TaskStatus.DONE) {
            val updatedTask = task.copy(status = TaskStatus.TODO, completedAt = null, lastCompletedTimestamp = null)
            updateTask(updatedTask)

            // Deduct points since it was unapproved
            if (task.assignedToUserId != null && task.rewardPoints > 0) {
                val user = userDao.getUserById(task.assignedToUserId)
                if (user != null) {
                    val updatedUser = user.copy(balance = (user.balance - task.rewardPoints).coerceAtLeast(0))
                    userDao.updateUser(updatedUser)
                    firestoreSync.uploadUser(updatedUser)
                    
                    val earning = Earning(
                        userId = user.id,
                        amount = -task.rewardPoints,
                        taskId = task.id,
                        description = "Reverted chore approval: ${task.title}",
                        familyId = task.familyId
                    )
                    rewardDao.insertEarning(earning)
                }
            }
        }
    }

    suspend fun deleteUser(user: User) {
        userDao.deleteUser(user)
        firestoreSync.deleteUser(user)
    }

    suspend fun deleteUserLocallyOnly(user: User) {
        userDao.deleteUser(user)
    }

    suspend fun deleteUserRemoteOnly(user: User) {
        firestoreSync.deleteUser(user)
    }

    suspend fun approveChore(task: Task, approved: Boolean) {
        if (approved) {
            val updatedTask = task.copy(status = TaskStatus.DONE, completedAt = System.currentTimeMillis())
            updateTask(updatedTask)

            // Award points only upon approval
            if (task.assignedToUserId != null && task.rewardPoints > 0) {
                val user = userDao.getUserById(task.assignedToUserId)
                if (user != null) {
                    val updatedUser = user.copy(balance = user.balance + task.rewardPoints)
                    userDao.updateUser(updatedUser)
                    firestoreSync.uploadUser(updatedUser)
                    
                    val earning = Earning(
                        userId = user.id,
                        amount = task.rewardPoints,
                        taskId = task.id,
                        description = "Approved chore: ${task.title}",
                        familyId = task.familyId
                    )
                    rewardDao.insertEarning(earning)
                }
            }
        } else {
            val updatedTask = task.copy(status = TaskStatus.TODO, lastCompletedTimestamp = null)
            updateTask(updatedTask)
        }
    }

    suspend fun purchaseReward(userId: Int, item: RewardItem, languageCode: String): Boolean {
        val user = userDao.getUserById(userId) ?: return false
        if (user.balance >= item.price) {
            val updatedUser = user.copy(balance = user.balance - item.price)
            userDao.updateUser(updatedUser)
            firestoreSync.uploadUser(updatedUser)
            
            val earning = Earning(
                userId = user.id,
                amount = -item.price,
                taskId = null,
                description = if (languageCode == "HE") "רכישת הגמול: ${item.title}" else "Purchased: ${item.title}",
                familyId = item.familyId
            )
            rewardDao.insertEarning(earning)
            
            val pTitle = if (languageCode == "HE") "מימוש הטבה: ${item.title}" else "Redeem Reward: ${item.title}"
            val pDesc = if (languageCode == "HE") "המשתמש ${user.name} ביקש לממש את ההטבה '${item.title}' תמורת ${item.price} נקודות" else "${user.name} requested to redeem the reward '${item.title}' for ${item.price} Points"
            val redemptionTask = Task(
                title = pTitle,
                description = pDesc,
                priority = Priority.HIGH,
                status = TaskStatus.PENDING_APPROVAL,
                source = TaskSource.STORE_REDEMPTION,
                assignedToUserId = userId,
                rewardPoints = item.price,
                isChore = false,
                createdAt = System.currentTimeMillis(),
                familyId = item.familyId
            )
            val insertedTaskId = taskDao.insertTask(redemptionTask)
            val insertedTask = taskDao.getTaskById(insertedTaskId.toInt())
            if (insertedTask != null) {
                firestoreSync.uploadTask(insertedTask)
            }
            return true
        }
        return false
    }

    suspend fun insertUser(user: User): Long {
        val id = userDao.insertUser(user)
        val newUser = userDao.getUserById(id.toInt())
        if (newUser != null) {
            firestoreSync.uploadUser(newUser)
        }
        return id
    }

    suspend fun updateUser(user: User) {
        userDao.updateUser(user)
        firestoreSync.uploadUser(user)
    }

    suspend fun getAnyUser() = userDao.getAnyUser()

    suspend fun getUserById(id: Int) = userDao.getUserById(id)

    suspend fun insertRewardItem(item: RewardItem) {
        val id = rewardDao.insertRewardItem(item)
        val newItem = rewardDao.getRewardItemById(id.toInt())
        if (newItem != null) {
            firestoreSync.uploadRewardItem(newItem)
        }
    }

    suspend fun updateRewardItem(item: RewardItem) {
        rewardDao.updateRewardItem(item)
        firestoreSync.uploadRewardItem(item)
    }

    suspend fun deleteRewardItem(item: RewardItem) {
        rewardDao.deleteRewardItem(item)
        firestoreSync.deleteRewardItem(item)
    }

    fun getEarningsHistory(userId: Int) = rewardDao.getEarningsHistory(userId)

    // FamilyRole actions
    suspend fun insertFamilyRole(role: FamilyRole) {
        userDao.insertFamilyRole(role)
        firestoreSync.uploadFamilyRole(role)
    }

    suspend fun getFamilyRoleById(roleId: String) = userDao.getFamilyRoleById(roleId)

    fun getFamilyRolesByFamily(familyId: String) = userDao.getFamilyRolesByFamily(familyId)

    suspend fun deleteFamilyRole(role: FamilyRole) {
        userDao.deleteFamilyRole(role)
        firestoreSync.deleteFamilyRole(role)
    }

    // Checklist operations
    fun getAllChecklists(familyId: String): Flow<List<Checklist>> = shoppingDao.getAllChecklists(familyId)

    suspend fun getChecklistById(id: Int): Checklist? = shoppingDao.getChecklistById(id)

    suspend fun getChecklistByTaskId(taskId: Int): Checklist? = shoppingDao.getChecklistByTaskId(taskId)

    suspend fun insertChecklist(checklist: Checklist): Long {
        val id = shoppingDao.insertChecklist(checklist)
        val newChecklist = shoppingDao.getChecklistById(id.toInt())
        if (newChecklist != null) {
            firestoreSync.uploadChecklist(newChecklist)
        }
        return id
    }

    suspend fun updateChecklist(checklist: Checklist) {
        shoppingDao.updateChecklist(checklist)
        firestoreSync.uploadChecklist(checklist)
    }

    suspend fun deleteChecklist(checklist: Checklist) {
        shoppingDao.deleteChecklist(checklist)
        firestoreSync.deleteChecklist(checklist)
    }

    suspend fun deleteShoppingCategoriesAndItemsForFamily(familyId: String) {
        shoppingDao.deleteItemsByFamily(familyId)
        shoppingDao.deleteCategoriesByFamily(familyId)
    }

    // Shared List Proposals
    fun getIncomingProposals(familyId: String): Flow<List<SharedListProposal>> = shoppingDao.getIncomingProposals(familyId)

    fun getOutgoingProposals(familyId: String): Flow<List<SharedListProposal>> = shoppingDao.getOutgoingProposals(familyId)

    suspend fun getProposalById(id: Int): SharedListProposal? = shoppingDao.getProposalById(id)

    suspend fun getProposalByRemoteId(remoteId: String): SharedListProposal? = shoppingDao.getProposalByRemoteId(remoteId)

    suspend fun insertProposal(proposal: SharedListProposal): Long {
        val id = shoppingDao.insertProposal(proposal)
        val newProposal = shoppingDao.getProposalById(id.toInt())
        if (newProposal != null) {
            firestoreSync.uploadProposal(newProposal)
        }
        return id
    }

    suspend fun updateProposal(proposal: SharedListProposal) {
        shoppingDao.updateProposal(proposal)
        firestoreSync.uploadProposal(proposal)
    }

    suspend fun deleteProposal(proposal: SharedListProposal) {
        shoppingDao.deleteProposal(proposal)
        firestoreSync.deleteProposalRemote(proposal)
    }

    suspend fun updateProposalStatusRemote(proposal: SharedListProposal) {
        shoppingDao.updateProposal(proposal)
        firestoreSync.updateProposalStatusRemote(proposal)
    }

    suspend fun deletePrimaryShoppingList(familyId: String) {
        shoppingDao.deletePrimaryItems(familyId)
        shoppingDao.deletePrimaryCategories(familyId)
    }
}
