package com.example.ui

import androidx.compose.ui.unit.LayoutDirection

enum class Language(val code: String, val displayName: String, val layoutDirection: LayoutDirection) {
    EN("en", "English", LayoutDirection.Ltr),
    HE("he", "עברית", LayoutDirection.Rtl)
}

object Localization {
    private val translations = mapOf(
        // Common / Top Bar / Nav
        "tasks" to Pair("My Day", "היום שלי"),
        "chores" to Pair("Task Bank", "בנק המשימות"),
        "store" to Pair("Store", "חנות"),
        "permissions" to Pair("Permissions", "הרשאות"),
        "settings" to Pair("Settings", "הגדרות"),
        "pts" to Pair("pts", "נק'"),
        "points" to Pair("Points", "נקודות"),
        "cancel" to Pair("Cancel", "ביטול"),
        "shopping" to Pair("Shopping", "קניות"),
        "shopping_list" to Pair("Shopping List", "רשימת קניות"),
        "new_category" to Pair("New Category", "קטגוריה חדשה"),
        "new_product" to Pair("New Record", "רשומה חדשה"),
        "add_category" to Pair("Add Category", "הוסף קטגוריה"),
        "add_product" to Pair("Add Record", "הוסף רשומה"),
        "category_name" to Pair("Category Name", "שם קטגוריה"),
        "product_name" to Pair("Record Name", "שם רשומה"),
        "choose_category" to Pair("Choose Category", "בחר קטגוריה"),
        "uncategorized" to Pair("Uncategorized", "ללא קטגוריה"),
        "search_placeholder" to Pair("Search records...", "חיפוש רשומות..."),
        "edit_product" to Pair("Edit Record", "ערוך רשומה"),
        "delete" to Pair("Delete", "מחק"),
        "edit" to Pair("Edit", "ערוך"),
        "save" to Pair("Save", "שמור"),
        "buy_again" to Pair("Buy Again", "לקנות שוב"),
        "bought" to Pair("Bought", "נקנה"),
        "empty_shopping_list" to Pair("Your shopping list is empty!", "רשימת הקניות שלכם ריקה!"),
        "empty_checklist" to Pair("The checklist is empty! Invite you to create a new item or category to get started.", "רשימת הסימון ריקה! צרו פריט חדש או קטגוריה כדי להתחיל."),
        "checklists" to Pair("Checklists", "רשימות סימון"),
        "new_checklist" to Pair("New Checklist", "רשימת סימון חדשה"),
        "checklist_name" to Pair("Checklist Name", "שם רשימת הסימון"),
        "empty_checklists_subtitle" to Pair("No checklists found. Add one!", "לא נמצאו רשימות סימון משפחתיות. צור רשימה!"),
        "link_checklist" to Pair("Link Checklist", "קשר רשימת סימון"),
        "unlink_checklist" to Pair("Unlink List", "נתק רשימה"),


        // Login / Setup Screen
        "welcome_to_familyhub" to Pair("Welcome to FamilyHub", "ברוכים הבאים ל-FamilyHub"),
        "welcome_subtitle" to Pair("Keep your household synchronized and rewarding", "שמרו על המשפחה מסונכרנת ומתוגמלת ברקע"),
        "login_to_family_board" to Pair("Log In to Family Board", "התחבר ללוח המשפחתי"),
        "select_profile" to Pair("Select your profile to start:", "בחר את הפרופיל שלך כדי להתחיל:"),
        "start_new_family" to Pair("Start New Family Space", "הקם מרחב משפחתי חדש"),
        "join_as_leader" to Pair("Join as New Leader", "הצטרף כמנהל חדש"),
        "create_family_manager" to Pair("Create Family & Manager", "יצירת משפחה ומנהל"),
        "family_name_placeholder" to Pair("Family Name (e.g., The Smiths)", "שם משפחה (למשל: כהן)"),
        "your_name_placeholder" to Pair("Your Name (Parent/Manager)", "השם שלך (הורה/מנהל)"),
        "create_signin" to Pair("Create & Sign In", "צור והתחבר"),
        "back_to_login" to Pair("Back to log in", "חזרה להתחברות"),
        "family_space" to Pair("Family Space", "מרחב משפחתי"),
        "parent" to Pair("Parent", "הורה"),
        "child" to Pair("Child", "ילד/ה"),

        // Dashboard Screen
        "taskhub_dashboard" to Pair("My Day", "היום שלי"),
        "manager_view" to Pair("Manager View:", "תצוגת מנהל:"),
        "member_view" to Pair("Member View:", "תצוגת חבר:"),
        "todays_tasks" to Pair("Today's Tasks", "המשימות של היום"),
        "task_title" to Pair("Task Title", "שם המשימה"),
        "priority" to Pair("Priority", "עדיפות"),
        "add" to Pair("Add", "הוסף"),
        "new_task" to Pair("New Task", "משימה חדשה"),
        "delete_task" to Pair("Delete Task", "מחק משימה"),

        // Chores Screen
        "switch_user_profile" to Pair("Switch User / Profile:", "החלף משתמש / פרופיל:"),
        "filter_chores_by" to Pair("Filter Chores by Member:", "סנן מטלות לפי חבר:"),
        "all_members" to Pair("All Members", "כל החברים"),
        "verify_reward" to Pair("Verify & Reward", "אימות ותגמול"),
        "awaiting_verification" to Pair("Awaiting Verification", "ממתין לאימות"),
        "waiting_for_parent" to Pair("Waiting for parent", "ממתין להורה"),
        "undo" to Pair("Undo", "בטל"),
        "no_active_chores" to Pair("No active chores", "אין מטלות פעילות"),
        "recently_approved_chores" to Pair("Recently Approved Chores", "מטלות שאושרו לאחרונה"),
        "revert" to Pair("Revert", "החזר"),
        "needs_verification" to Pair("Needs verification", "ממתין לאישור"),
        "reject" to Pair("Reject", "דחה"),
        "verify_pay" to Pair("Verify & Pay", "אשר ושלם"),
        "finish" to Pair("Finish", "סיום"),
        "done" to Pair("Done", "בוצע"),
        "new_family_chore" to Pair("New Family Chore", "מטלה משפחתית חדשה"),
        "chore_title" to Pair("Chore Title", "שם המטלה"),
        "reward_points" to Pair("Reward Points", "נקודות תגמול"),
        "assign_to" to Pair("Assign to:", "הקצה ל:"),
        "create" to Pair("Create", "צור"),

        // Store Screen
        "virtual_store" to Pair("Virtual Store", "חנות וירטואלית"),
        "redeem_subtitle" to Pair("Redeem your earnings for awesome rewards!", "פדה את הנקודות שלך תמורת פרסים שווים!"),
        "redeem" to Pair("Redeem", "פדה"),

        // Family Manager Settings Screen
        "family_hub_manager" to Pair("Family Hub Manager", "מנהל מרכז משפחתי"),
        "manage_desc" to Pair("Manage permissions and family group roles", "ניהול הרשאות ותפקידים בקבוצה המשפחתית"),
        "no_users_found" to Pair("No users found", "לא נמצאו משתמשים"),
        "roles_permissions" to Pair("Roles & Permissions", "תפקידים והרשאות"),
        "invite_family_member" to Pair("Invite Family Member", "הזמן בן משפחה"),
        "name" to Pair("Name", "שם"),
        "role" to Pair("Role", "תפקיד"),
        "kid_child" to Pair("Kid / Child", "ילד/ה"),
        "adult_manager" to Pair("Adult / Manager", "מבוגר / מנהל"),
        "configure_permissions" to Pair("Configure Permissions", "הגדר הרשאות"),
        "invite" to Pair("Invite", "הזמן"),
        "delete_user" to Pair("Delete User", "מחק משתמש"),
        
        // Priority labels
        "low" to Pair("Low", "נמוכה"),
        "medium" to Pair("Medium", "בינונית"),
        "high" to Pair("High", "גבוהה"),
        "urgent" to Pair("Urgent", "דחופה"),

        // Permissions labels
        "allow_complete_chores" to Pair("Allow complete chores", "אישור סימון מטלות כבוצעו"),
        "allow_redeem_rewards" to Pair("Allow redeem rewards", "אישור פדיית פרסים בחנות"),
        "allow_see_other_tasks" to Pair("Allow see other tasks", "אישור צפייה במשימות של אחרים"),
        "allow_add_tasks" to Pair("Allow add tasks", "אישור הוספת משימות חדשות")
    )

    fun get(key: String, languageCode: String): String {
        val pair = translations[key.lowercase()] ?: return key
        return if (languageCode.uppercase() == "HE") pair.second else pair.first
    }

    fun getPriorityTranslation(priorityName: String, languageCode: String): String {
        return when (priorityName.uppercase()) {
            "LOW" -> get("low", languageCode)
            "MEDIUM" -> get("medium", languageCode)
            "HIGH" -> get("high", languageCode)
            "URGENT" -> get("urgent", languageCode)
            else -> priorityName
        }
    }
}
