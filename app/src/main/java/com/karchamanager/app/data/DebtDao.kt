
package com.karchamanager.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY dueDateBs ASC")
    fun getAllDebts(): Flow<List<DebtEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtEntity)

    @Update
    suspend fun updateDebt(debt: DebtEntity)

    @Delete
    suspend fun deleteDebt(debt: DebtEntity)

    @Query("UPDATE debts SET status = :status WHERE id = :id")
    suspend fun updateDebtStatus(id: String, status: String)

    @Query("SELECT COUNT(*) FROM debts")
    suspend fun getCount(): Int
}
