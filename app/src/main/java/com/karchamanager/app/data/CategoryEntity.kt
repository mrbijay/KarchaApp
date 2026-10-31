
package com.karchamanager.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val nameNp: String,
    val nameEn: String,
    val type: String, // "expense" or "income"
    val icon: String = "Tag",
    val color: String = "#10B981",
    val isCustom: Boolean = false
)
