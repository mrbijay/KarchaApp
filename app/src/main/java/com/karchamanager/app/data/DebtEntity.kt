
package com.karchamanager.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey
    val id: String,
    val type: String, // "lent", "borrowed", "emi"
    val personOrInstitution: String,
    val phoneNumber: String? = null,
    val amount: Double,
    val installmentAmount: Double? = null,
    val totalInstallments: Int? = null,
    val paidInstallments: Int? = null,
    val startDateBs: String,
    val dueDateBs: String,
    val dueDateAd: String,
    val status: String = "pending",
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
