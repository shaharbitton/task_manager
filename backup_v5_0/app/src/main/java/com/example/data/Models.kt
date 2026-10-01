package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.google.firebase.firestore.PropertyName

@Serializable
enum class UserRole {
    ADULT, CHILD
}

@Serializable
@Entity(tableName = "family_roles")
data class FamilyRole(
    @PrimaryKey val id: String = "", // e.g. "smith_ADULT"
    val familyId: String = "family_default",
    val roleType: UserRole = UserRole.ADULT,
    @get:PropertyName("isManager")
    @set:PropertyName("isManager")
    var isManager: Boolean = false,
    @get:PropertyName("allowDeleteTasks")
    @set:PropertyName("allowDeleteTasks")
    var allowDeleteTasks: Boolean = false,
    @get:PropertyName("allowCreateTasks")
    @set:PropertyName("allowCreateTasks")
    var allowCreateTasks: Boolean = false,
    @get:PropertyName("allowSeeOtherTasks")
    @set:PropertyName("allowSeeOtherTasks")
    var allowSeeOtherTasks: Boolean = false,
    @get:PropertyName("allowCreateChecklists")
    @set:PropertyName("allowCreateChecklists")
    var allowCreateChecklists: Boolean = false,
    val remoteId: String? = null
)

@Serializable
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val role: UserRole = UserRole.ADULT,
    val balance: Int = 0,
    val avatarUrl: String? = null,
    val remoteId: String? = null,
    val familyName: String? = null,
    val familyId: String = "family_default",
    val roleId: String = "family_default_ADULT", // Points to FamilyRole.id
    val passcode: String? = null, // Profile login PIN
    val phoneNumber: String? = null
)

@Serializable
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String = "",
    val description: String = "",
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
    val dueDate: Long? = null,
    val parentId: Int? = null, // For subtasks
    val recurrenceRule: String? = null, // e.g., "DAILY", "WEEKLY"
    val source: TaskSource = TaskSource.MANUAL,
    val assignedToUserId: Int? = null,
    val rewardPoints: Int = 0, // Used for chores
    @get:PropertyName("isChore")
    @set:PropertyName("isChore")
    var isChore: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val remoteId: String? = null,
    val familyId: String = "family_default",
    val pulledForDate: String? = null,
    @get:PropertyName("isTemplate")
    @set:PropertyName("isTemplate")
    var isTemplate: Boolean = false,
    val assignedToUserRemoteId: String? = null,
    val parentRemoteId: String? = null,
    val checkboxListId: Int? = null,
    val checkboxListRemoteId: String? = null,
    val lastCompletedTimestamp: Long? = null
)

enum class Priority {
    LOW, MEDIUM, HIGH, URGENT
}

enum class TaskStatus {
    TODO, IN_PROGRESS, PENDING_APPROVAL, DONE
}

enum class TaskSource {
    MANUAL, WHATSAPP, CALENDAR, EMAIL, CALL, STORE_REDEMPTION
}

@Serializable
@Entity(tableName = "reward_items")
data class RewardItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String = "",
    val description: String = "",
    val price: Int = 0,
    val imageUrl: String? = null,
    val remoteId: String? = null,
    val familyId: String = "family_default"
)

@Serializable
@Entity(tableName = "earnings_history")
data class Earning(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val amount: Int,
    val taskId: Int?,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val familyId: String = "family_default"
)

@Serializable
@Entity(tableName = "checklists")
data class Checklist(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val familyId: String = "family_default",
    val taskId: Int? = null,
    val taskRemoteId: String? = null,
    val remoteId: String? = null,
    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("isPrivate")
    @set:PropertyName("isPrivate")
    var isPrivate: Boolean = false,
    val creatorId: Int? = null
)

@Serializable
@Entity(tableName = "shopping_categories")
data class ShoppingCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,
    val position: Int = 0,
    @get:PropertyName("isExpanded")
    @set:PropertyName("isExpanded")
    var isExpanded: Boolean = true,
    val remoteId: String? = null,
    val familyId: String = "family_default",
    val taskId: Int? = null,
    val taskRemoteId: String? = null,
    val checklistId: Int? = null,
    val checklistRemoteId: String? = null
)

@Serializable
@Entity(tableName = "shopping_items")
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val categoryId: Int? = null, // Null means uncategorized
    @get:PropertyName("isCompleted")
    @set:PropertyName("isCompleted")
    var isCompleted: Boolean = false,
    val position: Int = 0,
    val remoteId: String? = null,
    val categoryRemoteId: String? = null,
    val familyId: String = "family_default",
    val checklistId: Int? = null,
    val checklistRemoteId: String? = null
)

@Serializable
data class CelebrationEvent(
    val userName: String,
    val taskCount: Int,
    val todayDate: String
)

fun isScheduledForToday(task: Task): Boolean {
    val todayStr = String.format(
        "%04d-%02d-%02d", 
        java.util.Calendar.getInstance().get(java.util.Calendar.YEAR), 
        java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1, 
        java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
    )
    if (task.pulledForDate == todayStr) return true
    if (!task.pulledForDate.isNullOrBlank() && task.pulledForDate != todayStr) return false

    // If a task is completed on a previous day, it is NOT scheduled for today
    if (task.status == TaskStatus.DONE && task.completedAt != null) {
        val cal = java.util.Calendar.getInstance()
        val todayDay = cal.get(java.util.Calendar.DAY_OF_YEAR)
        val todayYear = cal.get(java.util.Calendar.YEAR)
        cal.timeInMillis = task.completedAt
        val completedDay = cal.get(java.util.Calendar.DAY_OF_YEAR)
        val completedYear = cal.get(java.util.Calendar.YEAR)
        if (todayDay != completedDay || todayYear != completedYear) {
            return false
        }
    }

    val rule = task.recurrenceRule ?: return true
    if (rule.isBlank() || rule == "NONE") return true
    
    val parts = rule.split(":")
    val type = parts.getOrNull(0) ?: return true
    val dayArg = parts.getOrNull(1)
    
    val curCal = java.util.Calendar.getInstance()
    return when (type) {
        "DAILY" -> true
        "WEEKLY" -> {
            val scheduledDay = dayArg?.toIntOrNull() ?: return true
            curCal.get(java.util.Calendar.DAY_OF_WEEK) == scheduledDay
        }
        "MONTHLY" -> {
            val scheduledDayOfMonth = dayArg?.toIntOrNull() ?: return true
            curCal.get(java.util.Calendar.DAY_OF_MONTH) == scheduledDayOfMonth
        }
        else -> true
    }
}

@Serializable
data class SharedCategoryPayload(
    val name: String,
    val isCompleted: Boolean = false,
    val position: Int = 0,
    val isExpanded: Boolean = true
)

@Serializable
data class SharedItemPayload(
    val name: String,
    val categoryName: String?,
    val isCompleted: Boolean = false,
    val position: Int = 0
)

@Serializable
data class SharedListPayload(
    val listName: String,
    val categories: List<SharedCategoryPayload>,
    val items: List<SharedItemPayload>
)

@Serializable
@Entity(tableName = "shared_list_proposals")
data class SharedListProposal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val senderFamilyId: String = "",
    val receiverFamilyId: String = "",
    val payloadJson: String = "",
    @get:PropertyName("isPrimaryShoppingList")
    @set:PropertyName("isPrimaryShoppingList")
    var isPrimaryShoppingList: Boolean = false,
    val status: String = "PENDING", // PENDING, APPROVED, REJECTED
    val createdAt: Long = System.currentTimeMillis(),
    val remoteId: String? = null
)



