package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE familyId = :familyId AND parentId IS NULL AND isChore = 0 ORDER BY priority DESC, dueDate ASC")
    fun getAllMainTasks(familyId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE familyId = :familyId AND parentId = :parentId")
    fun getSubtasks(familyId: String, parentId: Int): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE familyId = :familyId AND isChore = 1")
    fun getAllChores(familyId: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE remoteId = :remoteId")
    suspend fun getTaskByRemoteId(remoteId: String): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): Task?

    @Query("SELECT * FROM tasks WHERE familyId = :familyId")
    suspend fun getAllTasksDirect(familyId: String): List<Task>

    @Query("SELECT * FROM tasks WHERE LOWER(TRIM(title)) = LOWER(TRIM(:title)) AND familyId = :familyId AND isChore = :isChore AND remoteId IS NULL LIMIT 1")
    suspend fun getTaskByTitleAndFamily(title: String, familyId: String, isChore: Boolean): Task?
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE familyId = :familyId")
    fun getAllUsers(familyId: String): Flow<List<User>>

    @Query("SELECT * FROM users WHERE familyId = :familyId")
    suspend fun getAllUsersDirect(familyId: String): List<User>

    @Query("SELECT * FROM users WHERE remoteId = :remoteId AND familyId = :familyId LIMIT 1")
    suspend fun getUserByRemoteId(remoteId: String, familyId: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Int): User?

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getAnyUser(): User?

    @Query("SELECT * FROM users WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND familyId = :familyId AND remoteId IS NULL LIMIT 1")
    suspend fun getUserByNameAndFamily(name: String, familyId: String): User?

    // FamilyRole methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFamilyRole(role: FamilyRole): Long

    @Query("SELECT * FROM family_roles WHERE id = :id LIMIT 1")
    suspend fun getFamilyRoleById(id: String): FamilyRole?

    @Query("SELECT * FROM family_roles WHERE familyId = :familyId")
    fun getFamilyRolesByFamily(familyId: String): Flow<List<FamilyRole>>

    @Delete
    suspend fun deleteFamilyRole(role: FamilyRole)
}

@Dao
interface RewardDao {
    @Query("SELECT * FROM reward_items WHERE familyId = :familyId")
    fun getAllRewardItems(familyId: String): Flow<List<RewardItem>>

    @Query("SELECT * FROM reward_items WHERE familyId = :familyId")
    suspend fun getRewardItemsDirect(familyId: String): List<RewardItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRewardItem(item: RewardItem): Long

    @Update
    suspend fun updateRewardItem(item: RewardItem)

    @Delete
    suspend fun deleteRewardItem(item: RewardItem)

    @Query("SELECT * FROM reward_items WHERE id = :id LIMIT 1")
    suspend fun getRewardItemById(id: Int): RewardItem?

    @Query("SELECT * FROM reward_items WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getRewardItemByRemoteId(remoteId: String): RewardItem?

    @Query("SELECT * FROM reward_items WHERE LOWER(TRIM(title)) = LOWER(TRIM(:title)) AND familyId = :familyId AND remoteId IS NULL LIMIT 1")
    suspend fun getRewardItemByTitleAndFamily(title: String, familyId: String): RewardItem?

    @Query("SELECT * FROM earnings_history WHERE userId = :userId ORDER BY timestamp DESC")
    fun getEarningsHistory(userId: Int): Flow<List<Earning>>

    @Insert
    suspend fun insertEarning(earning: Earning)
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM checklists WHERE familyId = :familyId ORDER BY id ASC")
    fun getAllChecklists(familyId: String): Flow<List<Checklist>>

    @Query("SELECT * FROM checklists WHERE familyId = :familyId ORDER BY id ASC")
    suspend fun getAllChecklistsDirect(familyId: String): List<Checklist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklist(checklist: Checklist): Long

    @Update
    suspend fun updateChecklist(checklist: Checklist)

    @Delete
    suspend fun deleteChecklist(checklist: Checklist)

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getChecklistById(id: Int): Checklist?

    @Query("SELECT * FROM checklists WHERE remoteId = :remoteId")
    suspend fun getChecklistByRemoteId(remoteId: String): Checklist?

    @Query("SELECT * FROM checklists WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND familyId = :familyId AND remoteId IS NULL LIMIT 1")
    suspend fun getChecklistByNameAndFamily(name: String, familyId: String): Checklist?

    @Query("SELECT * FROM checklists WHERE taskId = :taskId")
    suspend fun getChecklistByTaskId(taskId: Int): Checklist?

    @Query("SELECT * FROM shopping_categories WHERE familyId = :familyId ORDER BY position ASC, name ASC")
    fun getAllCategories(familyId: String): Flow<List<ShoppingCategory>>

    @Query("SELECT * FROM shopping_categories WHERE familyId = :familyId")
    suspend fun getAllCategoriesDirect(familyId: String): List<ShoppingCategory>

    @Query("SELECT * FROM shopping_items WHERE familyId = :familyId ORDER BY position ASC, name ASC")
    fun getAllItems(familyId: String): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items WHERE familyId = :familyId")
    suspend fun getAllShoppingItemsDirect(familyId: String): List<ShoppingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: ShoppingCategory): Long

    @Update
    suspend fun updateCategory(category: ShoppingCategory)

    @Delete
    suspend fun deleteCategory(category: ShoppingCategory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItem): Long

    @Update
    suspend fun updateItem(item: ShoppingItem)

    @Delete
    suspend fun deleteItem(item: ShoppingItem)

    @Query("SELECT * FROM shopping_items WHERE categoryId = :categoryId")
    suspend fun getItemsByCategoryId(categoryId: Int): List<ShoppingItem>

    @Query("SELECT * FROM shopping_categories WHERE id = :id")
    suspend fun getCategoryById(id: Int): ShoppingCategory?

    @Query("SELECT * FROM shopping_items WHERE id = :id")
    suspend fun getItemById(id: Int): ShoppingItem?

    @Query("SELECT * FROM shopping_categories WHERE remoteId = :remoteId")
    suspend fun getCategoryByRemoteId(remoteId: String): ShoppingCategory?

    @Query("SELECT * FROM shopping_items WHERE remoteId = :remoteId")
    suspend fun getItemByRemoteId(remoteId: String): ShoppingItem?

    @Query("SELECT * FROM shopping_items WHERE categoryRemoteId = :categoryRemoteId")
    suspend fun getItemsWithCategoryRemoteId(categoryRemoteId: String): List<ShoppingItem>

    @Query("SELECT * FROM shopping_categories WHERE checklistRemoteId = :checklistRemoteId")
    suspend fun getCategoriesWithChecklistRemoteId(checklistRemoteId: String): List<ShoppingCategory>

    @Query("SELECT * FROM shopping_items WHERE checklistRemoteId = :checklistRemoteId")
    suspend fun getItemsWithChecklistRemoteId(checklistRemoteId: String): List<ShoppingItem>

    @Query("SELECT * FROM shopping_categories WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND familyId = :familyId AND ((:checklistId IS NULL AND checklistId IS NULL) OR checklistId = :checklistId) AND remoteId IS NULL LIMIT 1")
    suspend fun getCategoryByNameChecklistAndFamily(name: String, checklistId: Int?, familyId: String): ShoppingCategory?

    @Query("SELECT * FROM shopping_items WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND familyId = :familyId AND ((:categoryId IS NULL AND categoryId IS NULL) OR categoryId = :categoryId) AND ((:checklistId IS NULL AND checklistId IS NULL) OR checklistId = :checklistId) AND remoteId IS NULL LIMIT 1")
    suspend fun getItemByNameCategoryChecklistAndFamily(name: String, categoryId: Int?, checklistId: Int?, familyId: String): ShoppingItem?

    @Query("DELETE FROM shopping_categories WHERE familyId = :familyId")
    suspend fun deleteCategoriesByFamily(familyId: String)

    @Query("DELETE FROM shopping_items WHERE familyId = :familyId")
    suspend fun deleteItemsByFamily(familyId: String)

    @Query("SELECT * FROM shared_list_proposals WHERE receiverFamilyId = :familyId ORDER BY createdAt DESC")
    fun getIncomingProposals(familyId: String): Flow<List<SharedListProposal>>

    @Query("SELECT * FROM shared_list_proposals WHERE senderFamilyId = :familyId ORDER BY createdAt DESC")
    fun getOutgoingProposals(familyId: String): Flow<List<SharedListProposal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProposal(proposal: SharedListProposal): Long

    @Update
    suspend fun updateProposal(proposal: SharedListProposal)

    @Delete
    suspend fun deleteProposal(proposal: SharedListProposal)

    @Query("SELECT * FROM shared_list_proposals WHERE id = :id")
    suspend fun getProposalById(id: Int): SharedListProposal?

    @Query("SELECT * FROM shared_list_proposals WHERE remoteId = :remoteId")
    suspend fun getProposalByRemoteId(remoteId: String): SharedListProposal?

    @Query("DELETE FROM shopping_categories WHERE familyId = :familyId AND checklistId IS NULL")
    suspend fun deletePrimaryCategories(familyId: String)

    @Query("DELETE FROM shopping_items WHERE familyId = :familyId AND checklistId IS NULL")
    suspend fun deletePrimaryItems(familyId: String)
}
