
package com.karchamanager.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey
    val id: String,
    val type: String, // "expense" or "income"
    val amount: Double,
    val categoryId: String,
    val bsDate: String, // e.g. "2083-07-14"
    val adDate: String, // e.g. "2026-10-29"
    val bsYear: Int,
    val bsMonth: Int,
    val bsDay: Int,
    val paymentMethod: String,
    val notes: String,
    val receiptUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
