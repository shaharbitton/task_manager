package com.example.data

import androidx.room.TypeConverter
import com.example.data.Priority
import com.example.data.TaskStatus
import com.example.data.TaskSource
import com.example.data.UserRole

class Converters {
    @TypeConverter
    fun fromPriority(priority: Priority): String = priority.name

    @TypeConverter
    fun toPriority(value: String): Priority = Priority.valueOf(value)

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun fromTaskSource(source: TaskSource): String = source.name

    @TypeConverter
    fun toTaskSource(value: String): TaskSource = TaskSource.valueOf(value)

    @TypeConverter
    fun fromUserRole(role: UserRole): String = role.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = UserRole.valueOf(value)
}
