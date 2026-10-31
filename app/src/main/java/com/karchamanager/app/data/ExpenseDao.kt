
package com.karchamanager.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY createdAt DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE bsYear = :year AND bsMonth = :month ORDER BY bsDay DESC")
    fun getExpensesByBsMonth(year: Int, month: Int): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE bsYear = :year ORDER BY bsMonth DESC, bsDay DESC")
    fun getExpensesByBsYear(year: Int): Flow<List<ExpenseEntity>>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()
}
