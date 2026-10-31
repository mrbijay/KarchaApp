package com.karchamanager.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.karchamanager.app.data.AppDatabase
import com.karchamanager.app.data.CategoryEntity
import com.karchamanager.app.data.DebtEntity
import com.karchamanager.app.data.ExpenseEntity
import com.karchamanager.app.ui.theme.KarchaManagerTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)

        setContent {
            KarchaManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KarchaFullApp(db)
                }
            }
        }
    }
}

fun formatRs(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    return "रु " + formatter.format(amount)
}

fun saveReceiptToStorage(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val dir = File(context.filesDir, "receipts")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            inputStream.copyTo(out)
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun loadBitmapFromPathOrBase64(context: Context, source: String?): androidx.compose.ui.graphics.ImageBitmap? {
    if (source.isNullOrBlank()) return null
    return try {
        if (source.startsWith("data:image") && source.contains(",")) {
            val base64Data = source.substringAfter(",")
            val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } else if (source.startsWith("content://") || source.startsWith("file://")) {
            val uri = Uri.parse(source)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } else {
            val file = File(source)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KarchaFullApp(db: AppDatabase) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val expenses by db.expenseDao().getAllExpenses().collectAsState(initial = emptyList())
    val debts by db.debtDao().getAllDebts().collectAsState(initial = emptyList())
    val categories by db.categoryDao().getAllCategories().collectAsState(initial = emptyList())

    val prefs = remember { context.getSharedPreferences("karcha_db_prefs", Context.MODE_PRIVATE) }
    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var showEditNameDialog by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showAddDebtDialog by remember { mutableStateOf(false) }
    var editingDebt by remember { mutableStateOf<DebtEntity?>(null) }
    var preselectedDate by remember { mutableStateOf<String?>(null) }
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    var debtToDelete by remember { mutableStateOf<DebtEntity?>(null) }

    // Seed sample data ONCE on initial installation using SharedPreferences
    // Guarantees user added/edited data is NEVER overwritten or lost on app restart
    LaunchedEffect(Unit) {
        val isSeeded = prefs.getBoolean("is_seeded_v3", false)
        if (!isSeeded) {
            val count = db.expenseDao().getCount()
            if (count == 0) {
                val today = NepaliCalendar.getCurrentNepaliDate()
                val starterData = listOf(
                    ExpenseEntity(
                        id = "starter_1",
                        type = "income",
                        amount = 65000.0,
                        categoryId = "तलब / ज्याला",
                        bsDate = "${today.year}-${today.month.toString().padStart(2, '0')}-01",
                        adDate = "2026-10-18",
                        bsYear = today.year,
                        bsMonth = today.month,
                        bsDay = 1,
                        paymentMethod = "Bank Transfer",
                        notes = "मासिक तलब जम्मा"
                    ),
                    ExpenseEntity(
                        id = "starter_2",
                        type = "expense",
                        amount = 15000.0,
                        categoryId = "घर / कोठा भाडा",
                        bsDate = "${today.year}-${today.month.toString().padStart(2, '0')}-02",
                        adDate = "2026-10-19",
                        bsYear = today.year,
                        bsMonth = today.month,
                        bsDay = 2,
                        paymentMethod = "eSewa",
                        notes = "कोठा भाडा भुक्तानी"
                    ),
                    ExpenseEntity(
                        id = "starter_3",
                        type = "expense",
                        amount = 3500.0,
                        categoryId = "किराना / खाद्यान्न",
                        bsDate = "${today.year}-${today.month.toString().padStart(2, '0')}-05",
                        adDate = "2026-10-22",
                        bsYear = today.year,
                        bsMonth = today.month,
                        bsDay = 5,
                        paymentMethod = "Cash (नगद)",
                        notes = "तरकारी र खाद्यान्न सामान"
                    ),
                    ExpenseEntity(
                        id = "starter_4",
                        type = "expense",
                        amount = 1800.0,
                        categoryId = "पेट्रोल / डिजेल",
                        bsDate = "${today.year}-${today.month.toString().padStart(2, '0')}-10",
                        adDate = "2026-10-27",
                        bsYear = today.year,
                        bsMonth = today.month,
                        bsDay = 10,
                        paymentMethod = "Khalti",
                        notes = "बाइक पेट्रोल"
                    )
                )
                starterData.forEach { db.expenseDao().insertExpense(it) }
            }

            val debtCount = db.debtDao().getCount()
            if (debtCount == 0) {
                val today = NepaliCalendar.getCurrentNepaliDate()
                val starterDebts = listOf(
                    DebtEntity(
                        id = "starter_debt_1",
                        type = "lent",
                        personOrInstitution = "राम शर्मा (साथी)",
                        phoneNumber = "9841234567",
                        amount = 25000.0,
                        startDateBs = "${today.year}-06-15",
                        dueDateBs = "${today.year}-07-30",
                        dueDateAd = "2026-11-15",
                        status = "pending",
                        notes = "घरायसी सापटी ३ महिनाका लागि"
                    ),
                    DebtEntity(
                        id = "starter_debt_2",
                        type = "emi",
                        personOrInstitution = "बजाज पल्सर बाइक किस्ता",
                        phoneNumber = "01-4455667",
                        amount = 180000.0,
                        installmentAmount = 15000.0,
                        totalInstallments = 12,
                        paidInstallments = 4,
                        startDateBs = "${today.year}-03-01",
                        dueDateBs = "${today.year}-07-20",
                        dueDateAd = "2026-11-05",
                        status = "pending",
                        notes = "मासिक किस्ता तिर्नुपर्ने"
                    )
                )
                starterDebts.forEach { db.debtDao().insertDebt(it) }
            }

            // Seed distinct categories for Income and Expense
            val catCount = db.categoryDao().getCount()
            if (catCount == 0) {
                val starterCategories = listOf(
                    // Expense categories
                    CategoryEntity("cat_grocery", "किराना / खाद्यान्न", "Grocery", "expense", "ShoppingCart", "#10B981", false),
                    CategoryEntity("cat_rent", "घर / कोठा भाडा", "House / Room Rent", "expense", "Home", "#8B5CF6", false),
                    CategoryEntity("cat_petrol", "पेट्रोल / डिजेल", "Petrol / Fuel", "expense", "Fuel", "#F59E0B", false),
                    CategoryEntity("cat_milk", "दूध र डेरी", "Milk & Dairy", "expense", "Milk", "#06B6D4", false),
                    CategoryEntity("cat_electricity", "बिजुली र पानी महसुल", "Electricity & Water", "expense", "Zap", "#EAB308", false),
                    CategoryEntity("cat_education", "शिक्षा / स्कुल कलेज", "Education & Fees", "expense", "School", "#EC4899", false),
                    CategoryEntity("cat_health", "स्वास्थ्य र औषधी", "Health & Medical", "expense", "Medical", "#EF4444", false),
                    CategoryEntity("cat_transport", "यातायात / गाडी भाडा", "Transportation", "expense", "DirectionsBus", "#6366F1", false),
                    CategoryEntity("cat_food", "खाना र रेस्टुरेन्ट", "Food & Restaurant", "expense", "Restaurant", "#F97316", false),
                    CategoryEntity("cat_shopping", "किनमेल / लुगाफाटा", "Shopping & Clothes", "expense", "ShoppingBag", "#14B8A6", false),
                    CategoryEntity("cat_other_exp", "अन्य विविध खर्च", "Other Expenses", "expense", "MoreHoriz", "#64748B", false),

                    // Income categories
                    CategoryEntity("cat_salary", "तलब / ज्याला", "Salary", "income", "Work", "#16A34A", false),
                    CategoryEntity("cat_business", "व्यापार / व्यवसाय", "Business Income", "income", "TrendingUp", "#2563EB", false),
                    CategoryEntity("cat_freelance", "फ्रीलान्स / अतिरिक्त काम", "Freelance & Side Gig", "income", "Laptop", "#9333EA", false),
                    CategoryEntity("cat_investment", "लगानी / शेयर लाभांश", "Investment & Dividend", "income", "MonetizationOn", "#EA580C", false),
                    CategoryEntity("cat_rental_inc", "घर/कोठा भाडा आम्दानी", "Rental Income", "income", "VpnKey", "#0D9488", false),
                    CategoryEntity("cat_other_inc", "अन्य आम्दानी", "Other Income", "income", "Savings", "#475569", false)
                )
                starterCategories.forEach { db.categoryDao().insertCategory(it) }
            }

            prefs.edit().putBoolean("is_seeded_v3", true).apply()
        }
    }

    val totalIncome = expenses.filter { it.type == "income" }.sumOf { it.amount }
    val totalExpense = expenses.filter { it.type == "expense" }.sumOf { it.amount }
    val balance = totalIncome - totalExpense
    val today = NepaliCalendar.getCurrentNepaliDate()

    // First-Time Onboarding Gate: Require entering User Name right after app installation
    if (userName.isBlank()) {
        WelcomeOnboardingScreen(
            today = today,
            onSaveName = { enteredName ->
                prefs.edit().putString("user_name", enteredName).apply()
                userName = enteredName
                Toast.makeText(context, "स्वागत छ, $enteredName जी!", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.clickable { showEditNameDialog = true }) {
                        Text(
                            text = "खर्च म्यानेजर (KarchaManager)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "👤 $userName • वि.सं. ${NepaliCalendar.toNepaliDigits(today.year)} ${today.monthNameNp} ${NepaliCalendar.toNepaliDigits(today.day)} गते",
                            fontSize = 11.sp,
                            color = Color(0xFFA7F3D0)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingExpense = null
                        preselectedDate = null
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = Color(0xFF10B981))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 2) {
                        editingDebt = null
                        showAddDebtDialog = true
                    } else {
                        editingExpense = null
                        preselectedDate = null
                        showAddDialog = true
                    }
                },
                containerColor = Color(0xFF10B981),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White
            ) {
                val navItems = listOf(
                    Triple("ड्यासबोर्ड", Icons.Default.Dashboard, 0),
                    Triple("कारोबारहरू", Icons.Default.ReceiptLong, 1),
                    Triple("ऋण/किस्ता", Icons.Default.Payments, 2),
                    Triple("पात्रो (BS)", Icons.Default.CalendarMonth, 3),
                    Triple("विश्लेषण", Icons.Default.BarChart, 4),
                    Triple("सेटिङ", Icons.Default.Settings, 5)
                )

                navItems.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = {
                            Text(
                                label,
                                fontSize = 10.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF10B981),
                            selectedTextColor = Color(0xFF10B981),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8),
                            indicatorColor = Color(0xFF1E293B)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
        ) {
            when (selectedTab) {
                0 -> DashboardTabScreen(
                    userName = userName,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    balance = balance,
                    expenses = expenses,
                    debts = debts,
                    onEditNameClick = { showEditNameDialog = true },
                    onEdit = { exp ->
                        editingExpense = exp
                        showAddDialog = true
                    },
                    onDelete = { expenseToDelete = it },
                    onAddClick = {
                        editingExpense = null
                        preselectedDate = null
                        showAddDialog = true
                    },
                    onNavigateToTransactions = { selectedTab = 1 },
                    onNavigateToDebts = { selectedTab = 2 }
                )
                1 -> TransactionsTabScreen(
                    expenses = expenses,
                    categories = categories,
                    onEdit = { exp ->
                        editingExpense = exp
                        showAddDialog = true
                    },
                    onDelete = { expenseToDelete = it }
                )
                2 -> DebtsTabScreen(
                    debts = debts,
                    onAddClick = {
                        editingDebt = null
                        showAddDebtDialog = true
                    },
                    onEdit = { debt ->
                        editingDebt = debt
                        showAddDebtDialog = true
                    },
                    onPayInstallment = { debt ->
                        coroutineScope.launch {
                            val nextPaid = (debt.paidInstallments ?: 0) + 1
                            val totalInst = debt.totalInstallments ?: 12
                            val newStatus = if (nextPaid >= totalInst) "settled" else debt.status
                            val updated = debt.copy(paidInstallments = nextPaid, status = newStatus)
                            db.debtDao().insertDebt(updated)
                            Toast.makeText(context, "किस्ता भुक्तानी अपडेट भयो ($nextPaid/$totalInst)!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleSettle = { debt ->
                        coroutineScope.launch {
                            val next = if (debt.status == "settled") "pending" else "settled"
                            db.debtDao().updateDebtStatus(debt.id, next)
                            Toast.makeText(context, if (next == "settled") "चुक्ता भयो!" else "बाँकीमा राखियो!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = { debtToDelete = it }
                )
                3 -> CalendarTabScreen(
                    expenses = expenses,
                    onAddForDate = { dateStr ->
                        editingExpense = null
                        preselectedDate = dateStr
                        showAddDialog = true
                    },
                    onEdit = { exp ->
                        editingExpense = exp
                        showAddDialog = true
                    },
                    onDelete = { expenseToDelete = it }
                )
                4 -> ReportsTabScreen(expenses = expenses)
                5 -> SettingsTabScreen(
                    userName = userName,
                    onUpdateUserName = { newName ->
                        prefs.edit().putString("user_name", newName).apply()
                        userName = newName
                        Toast.makeText(context, "नाम अपडेट भयो!", Toast.LENGTH_SHORT).show()
                    },
                    expenses = expenses,
                    categories = categories,
                    onAddCategory = { cat ->
                        coroutineScope.launch {
                            db.categoryDao().insertCategory(cat)
                            Toast.makeText(context, "नयाँ विधा थपियो!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteCategory = { cat ->
                        coroutineScope.launch {
                            db.categoryDao().deleteCategory(cat)
                            Toast.makeText(context, "विधा मेटाइयो!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onClearAll = {
                        coroutineScope.launch {
                            db.expenseDao().clearAll()
                            Toast.makeText(context, "सबै डाटा मेटाइयो।", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    // Edit User Name Dialog
    if (showEditNameDialog) {
        var tempName by remember(userName) { mutableStateOf(userName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("प्रोफाइल नाम परिवर्तन गर्नुहोस्", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("तपाईंको शुभ नाम (Your Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = tempName.trim()
                        if (trimmed.isNotEmpty()) {
                            prefs.edit().putString("user_name", trimmed).apply()
                            userName = trimmed
                            showEditNameDialog = false
                            Toast.makeText(context, "तपाईंको नाम अपडेट भयो!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("सुरक्षित गर्नुहोस्", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("रद्द")
                }
            }
        )
    }

    // Add / Edit Expense Dialog
    if (showAddDialog) {
        AddExpenseDialog(
            initialDate = preselectedDate,
            editingExpense = editingExpense,
            categories = categories,
            onAddCategory = { nameNp, type ->
                coroutineScope.launch {
                    val newCat = CategoryEntity(
                        id = "cat_${System.currentTimeMillis()}",
                        nameNp = nameNp,
                        nameEn = nameNp,
                        type = type,
                        icon = if (type == "expense") "ShoppingCart" else "Work",
                        color = if (type == "expense") "#F43F5E" else "#10B981",
                        isCustom = true
                    )
                    db.categoryDao().insertCategory(newCat)
                    Toast.makeText(context, "नयाँ विधा थपियो!", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                showAddDialog = false
                editingExpense = null
            },
            onSave = { expense ->
                coroutineScope.launch {
                    db.expenseDao().insertExpense(expense)
                    showAddDialog = false
                    editingExpense = null
                    Toast.makeText(context, "कारोबार सुरक्षित भयो!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Delete Confirmation Dialog
    expenseToDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("कारोबार मेटाउने?", fontWeight = FontWeight.Bold) },
            text = { Text("${expense.categoryId} को ${formatRs(expense.amount)} रेकर्ड स्थायी रूपमा मेटाउन निश्चित हुनुहुन्छ?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            db.expenseDao().deleteById(expense.id)
                            expenseToDelete = null
                            Toast.makeText(context, "रेकर्ड मेटाइयो!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("मेटाउनुहोस्")
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("रद्द")
                }
            }
        )
    }

    // Add / Edit Debt Dialog
    if (showAddDebtDialog) {
        AddDebtDialog(
            editingDebt = editingDebt,
            onDismiss = {
                showAddDebtDialog = false
                editingDebt = null
            },
            onSave = { debt ->
                coroutineScope.launch {
                    db.debtDao().insertDebt(debt)
                    showAddDebtDialog = false
                    editingDebt = null
                    Toast.makeText(context, "ऋण/किस्ता रेकर्ड सुरक्षित भयो!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Delete Debt Confirmation Dialog
    debtToDelete?.let { debt ->
        AlertDialog(
            onDismissRequest = { debtToDelete = null },
            title = { Text("ऋण/किस्ता मेटाउने?", fontWeight = FontWeight.Bold) },
            text = { Text("${debt.personOrInstitution} को ${formatRs(debt.amount)} को रेकर्ड स्थायी रूपमा मेटाउन निश्चित हुनुहुन्छ?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            db.debtDao().deleteDebt(debt)
                            debtToDelete = null
                            Toast.makeText(context, "रेकर्ड मेटाइयो!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("मेटाउनुहोस्")
                }
            },
            dismissButton = {
                TextButton(onClick = { debtToDelete = null }) {
                    Text("रद्द")
                }
            }
        )
    }
}

// ----------------- FIRST-TIME WELCOME & NAME ONBOARDING SCREEN -----------------
@Composable
fun WelcomeOnboardingScreen(
    today: NepaliDate,
    onSaveName: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF064E3B), Color(0xFF0F172A))
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF10B981), Color(0xFF0D9488))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "वि.सं. ${NepaliCalendar.toNepaliDigits(today.year)} ${today.monthNameNp} ${NepaliCalendar.toNepaliDigits(today.day)} गते",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF059669),
                    modifier = Modifier
                        .background(Color(0xFFD1FAE5), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Text(
                    text = "नमस्ते! खर्च म्यानेजरमा स्वागत छ 🙏",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "आफ्नो व्यक्तिगत आम्दानी, खर्च, बिल/भौचर फोटो र ऋण-किस्ताको ड्यासबोर्ड तयार गर्न कृपया आफ्नो शुभ नाम लेख्नुहोस्।",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = {
                        nameInput = it
                        if (showError) showError = false
                    },
                    label = { Text("तपाईंको शुभ नाम (Your Full Name) *") },
                    placeholder = { Text("उदा: विजय बलाल") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF10B981))
                    },
                    isError = showError,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (showError) {
                    Text(
                        text = "कृपया अगाडि बढ्न आफ्नो नाम लेख्नुहोस्।",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("✓ केवल नेपाली विक्रम संवत् (वि.सं. / B.S.) पात्रो", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                    Text("✓ बिल / भौचर फोटो एट्याचमेन्ट र ऋण/किस्ता ट्र्याकर", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                    Text("✓ १००% अफलाइन सुरक्षित स्थायी Room SQLite डाटाबेस", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                }

                Button(
                    onClick = {
                        val trimmed = nameInput.trim()
                        if (trimmed.isEmpty()) {
                            showError = true
                        } else {
                            onSaveName(trimmed)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("ड्यासबोर्ड सुरु गर्नुहोस् (Get Started) →", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "© Copyright Bijay Balal",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = "Email: mrbijaybalal@gmail.com",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF059669)
                    )
                }
            }
        }
    }
}

// ----------------- TAB 0: DASHBOARD SCREEN -----------------
@Composable
fun DashboardTabScreen(
    userName: String = "प्रयोगकर्ता",
    totalIncome: Double,
    totalExpense: Double,
    balance: Double,
    expenses: List<ExpenseEntity>,
    debts: List<DebtEntity> = emptyList(),
    onEditNameClick: () -> Unit = {},
    onEdit: (ExpenseEntity) -> Unit = {},
    onDelete: (ExpenseEntity) -> Unit,
    onAddClick: () -> Unit,
    onNavigateToTransactions: () -> Unit,
    onNavigateToDebts: () -> Unit = {}
) {
    val today = NepaliCalendar.getCurrentNepaliDate()
    val displayUser = userName.ifBlank { "प्रयोगकर्ता" }
    val avatarInitial = displayUser.firstOrNull()?.uppercase() ?: "उ"
    val todayExpenses = expenses.filter {
        it.type == "expense" && it.bsYear == today.year && it.bsMonth == today.month && it.bsDay == today.day
    }.sumOf { it.amount }
    val todayIncome = expenses.filter {
        it.type == "income" && it.bsYear == today.year && it.bsMonth == today.month && it.bsDay == today.day
    }.sumOf { it.amount }
    val thisMonthExpenses = expenses.filter {
        it.type == "expense" && it.bsYear == today.year && it.bsMonth == today.month
    }.sumOf { it.amount }
    val thisMonthIncome = expenses.filter {
        it.type == "income" && it.bsYear == today.year && it.bsMonth == today.month
    }.sumOf { it.amount }
    val thisYearExpenses = expenses.filter {
        it.type == "expense" && it.bsYear == today.year
    }.sumOf { it.amount }
    val thisYearIncome = expenses.filter {
        it.type == "income" && it.bsYear == today.year
    }.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Redesigned Personal Greeting & User Profile Card on Dashboard
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFD1FAE5), RoundedCornerShape(22.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF10B981), Color(0xFF0D9488), Color(0xFF4F46E5))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = avatarInitial,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "व्यक्तिगत ड्यासबोर्ड • वि.सं. ${NepaliCalendar.toNepaliDigits(today.year)} ${today.monthNameNp} ${NepaliCalendar.toNepaliDigits(today.day)} गते",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669)
                            )
                            Text(
                                text = "नमस्ते, $displayUser जी! 🙏",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "तपाईंको आज, यो महिना र यो वर्षको आम्दानी–खर्च विवरण",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(
                        onClick = onEditNameClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Name",
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Urgent Debt / EMI Due Date Reminder Banner
        val urgentDebts = debts.filter { it.status == "pending" }.filter {
            val rem = NepaliCalendar.getDaysDifference(it.dueDateBs, today)
            rem.isOverdue || rem.isDueToday || rem.daysRemaining <= 7
        }
        if (urgentDebts.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(20.dp))
                        .clickable { onNavigateToDebts() }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFF59E0B), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ऋण तथा किस्ता भाखा सूचना",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${urgentDebts.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .background(Color(0xFFDC2626), CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            val top = urgentDebts[0]
                            val rem = NepaliCalendar.getDaysDifference(top.dueDateBs, today)
                            Text(
                                text = "${top.personOrInstitution}: भाखा वि.सं. ${top.dueDateBs} (${rem.labelNp})",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFF92400E))
                    }
                }
            }
        }

        // Balance Hero Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0F172A))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "$displayUser को बचत / बाँकी रकम (Net Balance)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA7F3D0)
                            )
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color(0xFF10B981)
                            )
                        }

                        Text(
                            text = formatRs(balance),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        HorizontalDivider(color = Color(0xFF334155), thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("कुल आम्दानी", fontSize = 11.sp, color = Color(0xFFA7F3D0))
                                }
                                Text(
                                    text = formatRs(totalIncome),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("कुल खर्च", fontSize = 11.sp, color = Color(0xFFFECACA))
                                }
                                Text(
                                    text = formatRs(totalExpense),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3 Timeframe Overview Cards (Today, This Month, This Year)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("आज (${today.day} गते)", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("-${formatRs(todayExpenses)}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFDC2626))
                        Text("+${formatRs(todayIncome)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("यो महिना (${today.monthNameNp})", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("-${formatRs(thisMonthExpenses)}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFDC2626))
                        Text("+${formatRs(thisMonthIncome)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("यो वर्ष (${today.year})", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("-${formatRs(thisYearExpenses)}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFDC2626))
                        Text("+${formatRs(thisYearIncome)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                    }
                }
            }
        }

        // Add Expense Button
        item {
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("नयाँ कारोबार थप्नुहोस् (Add Transaction)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Recent Transactions Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "पछिल्ला कारोबारहरू (Recent)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "सबै हेर्नुहोस् (${expenses.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981),
                    modifier = Modifier.clickable { onNavigateToTransactions() }
                )
            }
        }

        if (expenses.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("कुनै कारोबार छैन। नयाँ थप्न माथिको बटन थिच्नुहोस्।", color = Color(0xFF64748B), fontSize = 13.sp)
                }
            }
        } else {
            items(expenses.take(8)) { expense ->
                TransactionItemCard(
                    expense = expense,
                    onEdit = { onEdit(expense) },
                    onDelete = { onDelete(expense) }
                )
            }
        }

        // Copyright & Developer Footer on Dashboard
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "© Copyright Bijay Balal • Email: mrbijaybalal@gmail.com",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF475569)
                )
                Text(
                    text = "खर्च म्यानेजर (KarchaManager) • आजको मिति: वि.सं. २०८३ कार्तिक १४ गते",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

// ----------------- TAB 1: TRANSACTIONS WITH SEARCH, BS YEAR/MONTH & CATEGORY FILTER -----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsTabScreen(
    expenses: List<ExpenseEntity>,
    categories: List<CategoryEntity> = emptyList(),
    onEdit: (ExpenseEntity) -> Unit = {},
    onDelete: (ExpenseEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("ALL") }
    var selectedYearFilter by remember { mutableStateOf<Int?>(null) }
    var selectedMonthFilter by remember { mutableStateOf<Int?>(null) }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

    val availableYears = listOf(null, 2080, 2081, 2082, 2083, 2084, 2085)

    val categoryFilterList = remember(filterType, categories) {
        val list = when (filterType) {
            "EXPENSE" -> categories.filter { it.type == "expense" }.map { it.nameNp }
            "INCOME" -> categories.filter { it.type == "income" }.map { it.nameNp }
            else -> categories.map { it.nameNp }
        }
        listOf("ALL") + list
    }

    val filtered = expenses.filter {
        val matchesSearch = searchQuery.isBlank() ||
                it.categoryId.contains(searchQuery, ignoreCase = true) ||
                it.notes.contains(searchQuery, ignoreCase = true) ||
                it.paymentMethod.contains(searchQuery, ignoreCase = true) ||
                it.bsDate.contains(searchQuery) ||
                it.amount.toString().contains(searchQuery)

        val matchesType = when (filterType) {
            "EXPENSE" -> it.type == "expense"
            "INCOME" -> it.type == "income"
            else -> true
        }

        val matchesYear = selectedYearFilter == null || it.bsYear == selectedYearFilter
        val matchesMonth = selectedMonthFilter == null || it.bsMonth == selectedMonthFilter
        val matchesCat = selectedCategoryFilter == "ALL" || it.categoryId.contains(selectedCategoryFilter)

        matchesSearch && matchesType && matchesYear && matchesMonth && matchesCat
    }

    val filteredTotal = filtered.sumOf { if (it.type == "expense") -it.amount else it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "सम्पूर्ण कारोबार अभिलेख (All History)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("खर्च, विवरण, रकम वा वि.सं. मिति खोज्नुहोस्...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Filter chips: All / Expense / Income
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filterType == "ALL",
                onClick = {
                    filterType = "ALL"
                    selectedCategoryFilter = "ALL"
                },
                label = { Text("सबै (${expenses.size})", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filterType == "EXPENSE",
                onClick = {
                    filterType = "EXPENSE"
                    selectedCategoryFilter = "ALL"
                },
                label = { Text("खर्च मात्र", fontSize = 11.sp) }
            )
            FilterChip(
                selected = filterType == "INCOME",
                onClick = {
                    filterType = "INCOME"
                    selectedCategoryFilter = "ALL"
                },
                label = { Text("आम्दानी मात्र", fontSize = 11.sp) }
            )
        }

        // B.S. Year & Month Filter Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(availableYears) { yr ->
                FilterChip(
                    selected = selectedYearFilter == yr,
                    onClick = { selectedYearFilter = yr },
                    label = { Text(if (yr == null) "सबै वर्ष" else "$yr वि.सं.", fontSize = 10.sp) }
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = selectedMonthFilter == null,
                    onClick = { selectedMonthFilter = null },
                    label = { Text("सबै महिना", fontSize = 10.sp) }
                )
            }
            items(NepaliCalendar.nepaliMonthsNp.size) { idx ->
                val mNum = idx + 1
                FilterChip(
                    selected = selectedMonthFilter == mNum,
                    onClick = { selectedMonthFilter = mNum },
                    label = { Text(NepaliCalendar.nepaliMonthsNp[idx], fontSize = 10.sp) }
                )
            }
        }

        // Category filter chips (Distinctly separated by Expense vs Income)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categoryFilterList) { cat ->
                FilterChip(
                    selected = selectedCategoryFilter == cat,
                    onClick = { selectedCategoryFilter = cat },
                    label = { Text(if (cat == "ALL") "सबै विधा" else cat.split(" ")[0], fontSize = 10.sp) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${filtered.size} कारोबारहरू भेटिए", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
            Text(
                "नेट हिसाब: ${formatRs(filteredTotal)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (filteredTotal >= 0) Color(0xFF059669) else Color(0xFFDC2626)
            )
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("कुनै कारोबार भेटिएन।", color = Color(0xFF64748B), fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered) { expense ->
                    TransactionItemCard(
                        expense = expense,
                        onEdit = { onEdit(expense) },
                        onDelete = { onDelete(expense) }
                    )
                }
            }
        }
    }
}

// ----------------- TAB 3: NEPALI BIKRAM SAMBAT CALENDAR SCREEN -----------------
@Composable
fun CalendarTabScreen(
    expenses: List<ExpenseEntity>,
    onAddForDate: (String) -> Unit,
    onEdit: (ExpenseEntity) -> Unit = {},
    onDelete: (ExpenseEntity) -> Unit
) {
    val today = NepaliCalendar.getCurrentNepaliDate()
    var selectedYear by remember { mutableStateOf(today.year) }
    var selectedMonth by remember { mutableStateOf(today.month) }
    var selectedDay by remember { mutableStateOf(today.day) }

    val availableYears = listOf(2080, 2081, 2082, 2083, 2084, 2085, 2086)
    val daysInThisMonth = NepaliCalendar.daysInMonth2083.getOrElse(selectedMonth - 1) { 30 }
    val monthExpenses = expenses.filter { it.bsYear == selectedYear && it.bsMonth == selectedMonth }
    val monthExpTotal = monthExpenses.filter { it.type == "expense" }.sumOf { it.amount }
    val monthIncTotal = monthExpenses.filter { it.type == "income" }.sumOf { it.amount }

    val selectedDateStr = "${selectedYear}-${selectedMonth.toString().padStart(2, '0')}-${selectedDay.toString().padStart(2, '0')}"
    val dayExpenses = expenses.filter { it.bsDate == selectedDateStr }
    val dayTotal = dayExpenses.sumOf { if (it.type == "expense") -it.amount else it.amount }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Month Header Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "नेपाली पात्रो (वि.सं. पात्रो)",
                                color = Color(0xFFA7F3D0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "वि.सं. $selectedYear ${NepaliCalendar.nepaliMonthsNp[selectedMonth - 1]} महिना",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        TextButton(
                            onClick = {
                                selectedYear = today.year
                                selectedMonth = today.month
                                selectedDay = today.day
                            }
                        ) {
                            Text("आजको मिति", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("यो महिना खर्च: ${formatRs(monthExpTotal)}", color = Color(0xFFFECACA), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("आम्दानी: ${formatRs(monthIncTotal)}", color = Color(0xFFA7F3D0), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Year Selector Carousel
        item {
            Text("वि.सं. वर्ष र महिना छान्नुहोस्:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                items(availableYears) { yr ->
                    FilterChip(
                        selected = selectedYear == yr,
                        onClick = { selectedYear = yr },
                        label = { Text("$yr वि.सं.", fontSize = 11.sp) }
                    )
                }
            }
        }

        // Month Selector Carousel
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NepaliCalendar.nepaliMonthsNp.size) { idx ->
                    val monthNum = idx + 1
                    FilterChip(
                        selected = selectedMonth == monthNum,
                        onClick = {
                            selectedMonth = monthNum
                            if (selectedDay > NepaliCalendar.daysInMonth2083.getOrElse(idx) { 30 }) {
                                selectedDay = 1
                            }
                        },
                        label = { Text(NepaliCalendar.nepaliMonthsNp[idx], fontSize = 11.sp) }
                    )
                }
            }
        }

        // Calendar Grid Header (Days of week)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Weekday headers
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        NepaliCalendar.weekDaysNp.forEach { dayName ->
                            Text(
                                text = dayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dayName == "शनि") Color(0xFFDC2626) else Color(0xFF64748B),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Date cells grid
                    val daysList = (1..daysInThisMonth).toList()
                    val rows = daysList.chunked(7)

                    rows.forEach { rowDays ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            rowDays.forEach { dayNum ->
                                val dateStr = "${selectedYear}-${selectedMonth.toString().padStart(2, '0')}-${dayNum.toString().padStart(2, '0')}"
                                val hasExpense = expenses.any { it.bsDate == dateStr && it.type == "expense" }
                                val hasIncome = expenses.any { it.bsDate == dateStr && it.type == "income" }
                                val isSelected = dayNum == selectedDay
                                val isToday = today.year == selectedYear && today.month == selectedMonth && dayNum == today.day

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isSelected -> Color(0xFF10B981)
                                                isToday -> Color(0xFFD1FAE5)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (isToday && !isSelected) 1.dp else 0.dp,
                                            color = Color(0xFF10B981),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedDay = dayNum },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = dayNum.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> Color.White
                                                isToday -> Color(0xFF047857)
                                                else -> Color(0xFF1E293B)
                                            }
                                        )
                                        if (hasExpense || hasIncome) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) Color.White
                                                        else if (hasExpense) Color(0xFFDC2626)
                                                        else Color(0xFF10B981)
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                            repeat(7 - rowDays.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Selected Date Transactions Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "कारोबार: वि.सं. $selectedYear ${NepaliCalendar.nepaliMonthsNp[selectedMonth - 1]} $selectedDay गते",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "यस दिनको कुल हिसाब: ${formatRs(dayTotal)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (dayTotal >= 0) Color(0xFF059669) else Color(0xFFDC2626)
                    )
                }

                Button(
                    onClick = { onAddForDate(selectedDateStr) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("थप्नुहोस्", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (dayExpenses.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("यस दिनमा कुनै कारोबार रेकर्ड गरिएको छैन।", color = Color(0xFF64748B), fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(dayExpenses) { item ->
                TransactionItemCard(
                    expense = item,
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) }
                )
            }
        }
    }
}

// ----------------- TAB 4: REPORTS & ANALYTICS SCREEN -----------------
@Composable
fun ReportsTabScreen(expenses: List<ExpenseEntity>) {
    val today = NepaliCalendar.getCurrentNepaliDate()
    var selectedPeriod by remember { mutableStateOf("ALL") } // "TODAY", "MONTH", "YEAR", "ALL"
    var breakdownType by remember { mutableStateOf("expense") } // "expense" or "income"

    val periodExpenses = remember(expenses, selectedPeriod) {
        expenses.filter {
            when (selectedPeriod) {
                "TODAY" -> it.bsYear == today.year && it.bsMonth == today.month && it.bsDay == today.day
                "MONTH" -> it.bsYear == today.year && it.bsMonth == today.month
                "YEAR" -> it.bsYear == today.year
                else -> true
            }
        }
    }

    val totalExp = periodExpenses.filter { it.type == "expense" }.sumOf { it.amount }
    val totalInc = periodExpenses.filter { it.type == "income" }.sumOf { it.amount }
    val netBalance = totalInc - totalExp
    val savingsRate = if (totalInc > 0) ((netBalance / totalInc) * 100).toInt() else 0

    // Category breakdown for selected type (expense vs income)
    val activeTotal = if (breakdownType == "expense") totalExp else totalInc
    val categoryTotals = periodExpenses
        .filter { it.type == breakdownType }
        .groupBy { it.categoryId }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    // Payment method breakdown
    val methodTotals = periodExpenses
        .filter { it.type == "expense" }
        .groupBy { it.paymentMethod }
        .mapValues { entry -> entry.value.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("वित्तीय प्रतिवेदन र विश्लेषण (Reports)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text("तपाईंको सम्पूर्ण आम्दानी र खर्चको विस्तृत ग्राफ तथा अनुपात", fontSize = 12.sp, color = Color(0xFF64748B))
        }

        // Period Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val periods = listOf(
                    "ALL" to "सबै समय",
                    "TODAY" to "आज (${today.day} गते)",
                    "MONTH" to "यो महिना (${today.monthNameNp})",
                    "YEAR" to "यो वर्ष (${today.year} वि.सं.)"
                )
                items(periods) { (key, label) ->
                    FilterChip(
                        selected = selectedPeriod == key,
                        onClick = { selectedPeriod = key },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }

        // Summary KPI card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("समग्र हिसाब सारांश (Financial Summary)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("कुल आम्दानी", color = Color(0xFF059669), fontSize = 13.sp)
                        Text(formatRs(totalInc), fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("कुल खर्च", color = Color(0xFFDC2626), fontSize = 13.sp)
                        Text(formatRs(totalExp), fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    }
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("नेट बचत (Remaining)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(formatRs(netBalance), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF0F172A))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("बचत दर (Savings Rate)", color = Color(0xFF64748B), fontSize = 12.sp)
                        Text("$savingsRate%", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 13.sp)
                    }
                }
            }
        }

        // Category Breakdown Header + Toggle (Expense vs Income)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("वर्गअनुसार विवरण (Category Share):", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = breakdownType == "expense",
                        onClick = { breakdownType = "expense" },
                        label = { Text("खर्च", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = breakdownType == "income",
                        onClick = { breakdownType = "income" },
                        label = { Text("आम्दानी", fontSize = 11.sp) }
                    )
                }
            }
        }

        if (categoryTotals.isEmpty()) {
            item {
                Text("यस अवधिमा कुनै रेकर्ड छैन।", color = Color(0xFF64748B), fontSize = 13.sp)
            }
        } else {
            items(categoryTotals) { (cat, amount) ->
                val percentage = if (activeTotal > 0) (amount / activeTotal * 100).toInt() else 0
                val progress = if (activeTotal > 0) (amount / activeTotal).toFloat() else 0f

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cat, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                            Text(
                                formatRs(amount),
                                fontWeight = FontWeight.Bold,
                                color = if (breakdownType == "expense") Color(0xFFDC2626) else Color(0xFF059669),
                                fontSize = 14.sp
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (breakdownType == "expense") Color(0xFFEF4444) else Color(0xFF10B981),
                            trackColor = Color(0xFFF1F5F9)
                        )
                        Text("$percentage% हिस्सा", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }

        // Payment Methods Breakdown Header
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("भुक्तानी माध्यम (Payment Method Share):", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        }

        items(methodTotals) { (method, amount) ->
            val percentage = if (totalExp > 0) (amount / totalExp * 100).toInt() else 0
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(method, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("${formatRs(amount)} ($percentage%)", fontWeight = FontWeight.Bold, color = Color(0xFF334155), fontSize = 13.sp)
                }
            }
        }
    }
}

// ----------------- TAB 5: SETTINGS, CATEGORY MANAGER & DATA BACKUP SCREEN -----------------
@Composable
fun SettingsTabScreen(
    userName: String = "",
    onUpdateUserName: (String) -> Unit = {},
    expenses: List<ExpenseEntity>,
    categories: List<CategoryEntity> = emptyList(),
    onAddCategory: (CategoryEntity) -> Unit = {},
    onDeleteCategory: (CategoryEntity) -> Unit = {},
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    var showConfirmClear by remember { mutableStateOf(false) }
    var selectedCatTab by remember { mutableStateOf("expense") } // "expense" or "income"
    var newCategoryName by remember { mutableStateOf("") }
    var profileNameInput by remember(userName) { mutableStateOf(userName) }

    val displayedCategories = categories.filter { it.type == selectedCatTab }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("सेटिङ, प्रोफाइल तथा डाटा व्यवस्थापन (Settings)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        }

        // User Profile Name Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("प्रयोगकर्ता प्रोफाइल (User Profile)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = profileNameInput,
                            onValueChange = { profileNameInput = it },
                            label = { Text("तपाईंको नाम (Your Name)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val trimmed = profileNameInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    onUpdateUserName(trimmed)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("सेभ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Category Management Section (Add & Delete Separate Expense & Income Categories)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("आम्दानी र खर्च विधा व्यवस्थापन (Category Manager)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    Text("आम्दानी र खर्चका लागि छुट्टाछुट्टै विधा (Category) थप्नुहोस् वा हटाउनुहोस्:", fontSize = 11.sp, color = Color(0xFF64748B))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedCatTab == "expense",
                            onClick = { selectedCatTab = "expense" },
                            label = { Text("खर्च विधाहरू (${categories.count { it.type == "expense" }})", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = selectedCatTab == "income",
                            onClick = { selectedCatTab = "income" },
                            label = { Text("आम्दानी विधाहरू (${categories.count { it.type == "income" }})", fontSize = 12.sp) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            placeholder = {
                                Text(
                                    if (selectedCatTab == "expense") "नयाँ खर्च विधाको नाम..." else "नयाँ आम्दानी विधाको नाम...",
                                    fontSize = 12.sp
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newCategoryName.isNotBlank()) {
                                    val newCat = CategoryEntity(
                                        id = "cat_${System.currentTimeMillis()}",
                                        nameNp = newCategoryName.trim(),
                                        nameEn = newCategoryName.trim(),
                                        type = selectedCatTab,
                                        icon = if (selectedCatTab == "expense") "ShoppingCart" else "Work",
                                        color = if (selectedCatTab == "expense") "#EF4444" else "#10B981",
                                        isCustom = true
                                    )
                                    onAddCategory(newCat)
                                    newCategoryName = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("थप्नुहोस्", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        displayedCategories.forEach { cat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (cat.type == "expense") Color(0xFFEF4444) else Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(cat.nameNp, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                                }
                                IconButton(
                                    onClick = { onDeleteCategory(cat) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Category",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // App Info & Copyright Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("एप जानकारी तथा सर्वाधिकार (App Info & Copyright)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    Text("नाम: खर्च म्यानेजर (KarchaManager)", fontSize = 12.sp, color = Color(0xFF334155))
                    Text("आजको मिति: वि.सं. २०८३ कार्तिक १४ गते", fontSize = 12.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                    Text("मिति प्रणाली: केवल नेपाली विक्रम संवत् (वि.सं. / B.S.)", fontSize = 12.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                    Text("डाटाबेस: Android Room SQLite (100% Offline Permanent Storage)", fontSize = 12.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                    Text("जम्मा रेकर्डहरू: ${expenses.size} कारोबारहरू सुरक्षित छन्", fontSize = 12.sp, color = Color(0xFF334155))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Text("© Copyright Bijay Balal", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                    Text("Email: mrbijaybalal@gmail.com", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
            }
        }

        // Data Safety Info Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("डाटा सुरक्षा (Data Safety & Longevity)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF059669))
                    }
                    Text(
                        "तपाईंले राख्नुभएको प्रत्येक आम्दानी, खर्च, बिल/भौचर फोटो र ऋणको रेकर्ड तपाईंको मोबाइलको सुरक्षित आन्तरिक SQLite डाटाबेसमा सधैंको लागि बस्छ। एप बन्द गर्दा वा फोन रिस्टार्ट गर्दा पनि डाटा कहिल्यै मेटिँदैन।",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Export JSON Backup Button
        item {
            Button(
                onClick = {
                    val json = buildString {
                        append("[\n")
                        expenses.forEachIndexed { i, e ->
                            append("  {\"id\":\"${e.id}\",\"type\":\"${e.type}\",\"amount\":${e.amount},\"category\":\"${e.categoryId}\",\"bsDate\":\"${e.bsDate}\",\"method\":\"${e.paymentMethod}\",\"notes\":\"${e.notes}\"}")
                            if (i < expenses.size - 1) append(",")
                            append("\n")
                        }
                        append("]")
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("KarchaBackup", json)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "सबै डाटा क्लिपबोर्डमा कपी भयो (JSON Backup Copied)!", Toast.LENGTH_LONG).show()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("डाटा ब्याकअप कपी गर्नुहोस् (Export JSON Backup)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Reset Database Button
        item {
            OutlinedButton(
                onClick = { showConfirmClear = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("सबै कारोबार मेटाउनुहोस् (Reset Database)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showConfirmClear) {
        AlertDialog(
            onDismissRequest = { showConfirmClear = false },
            title = { Text("निश्चित हुनुहुन्छ?", fontWeight = FontWeight.Bold) },
            text = { Text("यसले फोनमा भएका सबै पुराना कारोबारहरू मेटाउनेछ। यो कार्य उल्टाउन सकिँदैन।") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAll()
                        showConfirmClear = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("मेटाउनुहोस्")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear = false }) {
                    Text("रद्द")
                }
            }
        )
    }
}

// ----------------- RECEIPT VIEWER DIALOG -----------------
@Composable
fun ReceiptViewerDialog(
    receiptPath: String,
    title: String,
    amount: Double,
    dateBs: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val imageBitmap = remember(receiptPath) {
        loadBitmapFromPathOrBase64(context, receiptPath)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "बिल / भौचर प्रमाण • ${formatRs(amount)} • वि.सं. $dateBs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                if (imageBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 380.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Receipt / Voucher Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.BrokenImage, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("फोटो फेला परेन वा लोड हुन सकेन", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) {
                    Text("बन्द गर्नुहोस् (Close)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ----------------- ITEM CARD & ADD/EDIT DIALOG -----------------
@Composable
fun TransactionItemCard(
    expense: ExpenseEntity,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isExpense = expense.type == "expense"
    var showReceiptViewer by remember { mutableStateOf(false) }

    val receiptThumbBitmap = remember(expense.receiptUri) {
        loadBitmapFromPathOrBase64(context, expense.receiptUri)
    }

    if (showReceiptViewer && !expense.receiptUri.isNullOrBlank()) {
        ReceiptViewerDialog(
            receiptPath = expense.receiptUri,
            title = expense.categoryId,
            amount = expense.amount,
            dateBs = expense.bsDate,
            onDismiss = { showReceiptViewer = false }
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isExpense) Color(0xFFFEE2E2) else Color(0xFFD1FAE5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isExpense) Icons.Default.ShoppingCart else Icons.Default.Payments,
                        contentDescription = null,
                        tint = if (isExpense) Color(0xFFDC2626) else Color(0xFF059669),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = expense.categoryId,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "वि.सं. ${expense.bsDate} • ${expense.paymentMethod}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    if (expense.notes.isNotBlank()) {
                        Text(
                            text = expense.notes,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Receipt / Voucher indicator button if photo is attached
                    if (!expense.receiptUri.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEEF2FF))
                                .clickable { showReceiptViewer = true }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            if (receiptThumbBitmap != null) {
                                Image(
                                    bitmap = receiptThumbBitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            } else {
                                Icon(
                                    Icons.Default.Receipt,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = "बिल / भौचर हेर्नुहोस् 🔍",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (isExpense) "- " else "+ ") + formatRs(expense.amount),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isExpense) Color(0xFFDC2626) else Color(0xFF059669)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    initialDate: String? = null,
    editingExpense: ExpenseEntity? = null,
    categories: List<CategoryEntity> = emptyList(),
    onAddCategory: (String, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
    onSave: (ExpenseEntity) -> Unit
) {
    val context = LocalContext.current
    val today = NepaliCalendar.getCurrentNepaliDate()
    var type by remember { mutableStateOf(editingExpense?.type ?: "expense") }
    var amountText by remember { mutableStateOf(editingExpense?.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var selectedCategory by remember { mutableStateOf(editingExpense?.categoryId ?: "") }
    var customCategory by remember { mutableStateOf("") }
    var isAddingCustomCategory by remember { mutableStateOf(false) }
    var paymentMethod by remember { mutableStateOf(editingExpense?.paymentMethod ?: "eSewa") }
    var notes by remember { mutableStateOf(editingExpense?.notes ?: "") }
    var receiptPath by remember { mutableStateOf<String?>(editingExpense?.receiptUri) }

    val defaultDate = editingExpense?.bsDate ?: initialDate ?: "${today.year}-${today.month.toString().padStart(2, '0')}-${today.day.toString().padStart(2, '0')}"
    var dateText by remember { mutableStateOf(defaultDate) }

    // Launcher for picking bill, voucher, receipt image from device
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val saved = saveReceiptToStorage(context, it)
            receiptPath = saved ?: it.toString()
            Toast.makeText(context, "बिल / भौचर तस्बिर संलग्न भयो!", Toast.LENGTH_SHORT).show()
        }
    }

    val receiptBitmap = remember(receiptPath) {
        loadBitmapFromPathOrBase64(context, receiptPath)
    }

    // Filter categories by selected type (income vs expense)
    val filteredCategories = remember(type, categories) {
        val list = categories.filter { it.type == type }
        if (list.isNotEmpty()) list
        else {
            if (type == "expense") {
                listOf(
                    CategoryEntity("c1", "किराना / खाद्यान्न", "Grocery", "expense"),
                    CategoryEntity("c2", "घर / कोठा भाडा", "Rent", "expense"),
                    CategoryEntity("c3", "पेट्रोल / डिजेल", "Fuel", "expense"),
                    CategoryEntity("c4", "दूध र डेरी", "Dairy", "expense"),
                    CategoryEntity("c5", "खाना र खाजा", "Food", "expense"),
                    CategoryEntity("c6", "बिजुली र पानी", "Utilities", "expense"),
                    CategoryEntity("c7", "शिक्षा / स्कुल", "Education", "expense"),
                    CategoryEntity("c8", "स्वास्थ्य र औषधी", "Medical", "expense"),
                    CategoryEntity("c9", "अन्य विविध खर्च", "Other", "expense")
                )
            } else {
                listOf(
                    CategoryEntity("i1", "तलब / ज्याला", "Salary", "income"),
                    CategoryEntity("i2", "व्यापार / व्यवसाय", "Business", "income"),
                    CategoryEntity("i3", "फ्रीलान्स / अतिरिक्त काम", "Freelance", "income"),
                    CategoryEntity("i4", "लगानी / शेयर लाभांश", "Investment", "income"),
                    CategoryEntity("i5", "घर/कोठा भाडा आम्दानी", "Rent Income", "income"),
                    CategoryEntity("i6", "अन्य आम्दानी", "Other", "income")
                )
            }
        }
    }

    // Auto-select first category if current not in filtered list
    LaunchedEffect(type, filteredCategories) {
        if (selectedCategory.isBlank() || filteredCategories.none { it.nameNp == selectedCategory }) {
            selectedCategory = filteredCategories.firstOrNull()?.nameNp ?: ""
        }
    }

    val paymentMethods = listOf("eSewa", "Khalti", "Cash (नगद)", "Bank Transfer", "ConnectIPS")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingExpense != null) "कारोबार सम्पादन (Edit Transaction)"
                else if (type == "expense") "नयाँ खर्च विवरण (Add Expense)"
                else "नयाँ आम्दानी विवरण (Add Income)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Type selector
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = type == "expense",
                            onClick = { type = "expense" },
                            label = { Text("खर्च (Expense)") }
                        )
                        FilterChip(
                            selected = type == "income",
                            onClick = { type = "income" },
                            label = { Text("आम्दानी (Income)") }
                        )
                    }
                }

                // Amount input with support for Nepali numerals
                item {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("रकम (रू Amount)") },
                        placeholder = { Text("उदा. 1500 वा १५००") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Category selection separated by Expense vs Income
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (type == "expense") "खर्च वर्ग (Expense Category):" else "आम्दानी वर्ग (Income Category):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = if (isAddingCustomCategory) "रद्द गर्नुहोस्" else "+ नयाँ वर्ग थप्नुहोस्",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.clickable { isAddingCustomCategory = !isAddingCustomCategory }
                        )
                    }

                    if (isAddingCustomCategory) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = customCategory,
                                onValueChange = { customCategory = it },
                                placeholder = { Text("नयाँ वर्गको नाम", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    if (customCategory.isNotBlank()) {
                                        onAddCategory(customCategory.trim(), type)
                                        selectedCategory = customCategory.trim()
                                        customCategory = ""
                                        isAddingCustomCategory = false
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("थप्नुहोस्", fontSize = 11.sp)
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            items(filteredCategories) { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat.nameNp,
                                    onClick = { selectedCategory = cat.nameNp },
                                    label = { Text(cat.nameNp.split(" ")[0], fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // Payment method chips
                item {
                    Text("भुक्तानी माध्यम (Payment Method):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        items(paymentMethods) { method ->
                            FilterChip(
                                selected = paymentMethod == method,
                                onClick = { paymentMethod = method },
                                label = { Text(method, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Date input (Exclusively Bikram Sambat B.S.)
                item {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("मिति केवल वि.सं. (YYYY-MM-DD)") },
                        placeholder = { Text("२०८३-०७-१४") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Photo / Bill / Voucher Attachment Section
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "बिल / भौचर / रसिद फोटो (Attach Bill / Voucher):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )

                        if (receiptPath != null) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        if (receiptBitmap != null) {
                                            Image(
                                                bitmap = receiptBitmap,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .background(Color(0xFFDCFCE7), RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Receipt, contentDescription = null, tint = Color(0xFF16A34A))
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("बिल / भौचर फोटो संलग्न गरियो", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                            Text("अफलाइन ग्यालरीबाट सुरक्षित", fontSize = 10.sp, color = Color(0xFF16A34A))
                                        }
                                    }

                                    IconButton(onClick = { receiptPath = null }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("बिल / भौचर फोटो छान्नुहोस् (Attach Bill)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }
                        }
                    }
                }

                // Notes input
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("विवरण / नोट (Description / Notes)") },
                        placeholder = { Text("उदा. घरको सरसामान खरिद") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = NepaliCalendar.parseAmount(amountText)
                    if (amt > 0) {
                        val finalCategory = if (selectedCategory.isNotBlank()) selectedCategory else "अन्य विविध"
                        val parts = dateText.split("-")
                        val yr = parts.getOrNull(0)?.toIntOrNull() ?: today.year
                        val mo = parts.getOrNull(1)?.toIntOrNull() ?: today.month
                        val dy = parts.getOrNull(2)?.toIntOrNull() ?: today.day

                        val newTx = ExpenseEntity(
                            id = editingExpense?.id ?: "tx_${System.currentTimeMillis()}",
                            type = type,
                            amount = amt,
                            categoryId = finalCategory,
                            bsDate = dateText,
                            adDate = editingExpense?.adDate ?: "2026-10-30",
                            bsYear = yr,
                            bsMonth = mo,
                            bsDay = dy,
                            paymentMethod = paymentMethod,
                            notes = notes,
                            receiptUri = receiptPath,
                            createdAt = editingExpense?.createdAt ?: System.currentTimeMillis()
                        )
                        onSave(newTx)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("सुरक्षित गर्नुहोस् (Save)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("रद्द गर्नुहोस्")
            }
        }
    )
}

// ----------------- TAB 2: DEBTS & EMI TAB SCREEN -----------------
@Composable
fun DebtsTabScreen(
    debts: List<DebtEntity>,
    onAddClick: () -> Unit,
    onEdit: (DebtEntity) -> Unit = {},
    onPayInstallment: (DebtEntity) -> Unit = {},
    onToggleSettle: (DebtEntity) -> Unit,
    onDelete: (DebtEntity) -> Unit
) {
    val today = NepaliCalendar.getCurrentNepaliDate()
    var selectedFilter by remember { mutableStateOf("all") }

    val totalLent = debts.filter { it.type == "lent" && it.status == "pending" }.sumOf { it.amount }
    val totalBorrowed = debts.filter { (it.type == "borrowed" || it.type == "emi") && it.status == "pending" }.sumOf { it.amount }

    val filtered = debts.filter {
        when (selectedFilter) {
            "lent" -> it.type == "lent"
            "borrowed" -> it.type == "borrowed"
            "emi" -> it.type == "emi"
            "pending" -> it.status == "pending"
            "settled" -> it.status == "settled"
            else -> true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Summary Cards
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("कुल पाउनुपर्ने (दिएको)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                        Text(formatRs(totalLent), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF065F46))
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("कुल तिर्नुपर्ने (लिएको/किस्ता)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBE123C))
                        Text(formatRs(totalBorrowed), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF9F1239))
                    }
                }
            }
        }

        // Add Debt Button
        item {
            Button(
                onClick = onAddClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("नयाँ ऋण / किस्ता थप्नुहोस् (Add Debt / EMI)", fontWeight = FontWeight.Bold)
            }
        }

        // Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val filters = listOf(
                    "all" to "सबै",
                    "lent" to "दिएको (पाउने)",
                    "borrowed" to "लिएको (तिर्ने)",
                    "emi" to "किस्ता (EMI)",
                    "pending" to "बाँकी",
                    "settled" to "चुक्ता भएको"
                )
                items(filters) { (id, label) ->
                    FilterChip(
                        selected = selectedFilter == id,
                        onClick = { selectedFilter = id },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }

        // List of Debts
        if (filtered.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("कुनै ऋण वा किस्ताको रेकर्ड छैन", fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("दिएको ऋण, लिएको ऋण वा मासिक किस्ताको भाखा ट्र्याक गर्न नयाँ थप्नुहोस्।", fontSize = 11.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            items(filtered) { debt ->
                val isSettled = debt.status == "settled"
                val reminder = NepaliCalendar.getDaysDifference(debt.dueDateBs, today)

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = debt.personOrInstitution,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when (debt.type) {
                                            "lent" -> "दिएको"
                                            "borrowed" -> "लिएको"
                                            else -> "किस्ता"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (debt.type == "lent") Color(0xFF059669) else Color(0xFFD97706),
                                        modifier = Modifier
                                            .background(
                                                if (debt.type == "lent") Color(0xFFECFDF5) else Color(0xFFFFFBEB),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                debt.phoneNumber?.let { ph ->
                                    if (ph.isNotBlank()) {
                                        Text(text = "📞 $ph", fontSize = 11.sp, color = Color(0xFF2563EB))
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = (if (debt.type == "lent") "+ " else "- ") + formatRs(debt.amount),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (debt.type == "lent") Color(0xFF059669) else Color(0xFFDC2626)
                                )
                                Text(
                                    text = if (isSettled) "चुक्ता भएको" else reminder.labelNp,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSettled) Color(0xFF059669) else if (reminder.isOverdue) Color(0xFFDC2626) else Color(0xFFD97706)
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("सुरु मिति: वि.सं. ${debt.startDateBs}", fontSize = 11.sp, color = Color(0xFF64748B))
                            Text("भाखा मिति: वि.सं. ${debt.dueDateBs}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                            if (debt.type == "emi" && debt.installmentAmount != null) {
                                val paid = debt.paidInstallments ?: 0
                                val total = (debt.totalInstallments ?: 12).coerceAtLeast(1)
                                val progress = (paid.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                Text(
                                    text = "मासिक किस्ता: ${formatRs(debt.installmentAmount)} (प्रगति: $paid/$total)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706)
                                )
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFFF59E0B),
                                    trackColor = Color(0xFFFEF3C7)
                                )
                            }
                            if (!debt.notes.isNullOrBlank()) {
                                Text(text = debt.notes, fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (debt.type == "emi" && !isSettled) {
                                TextButton(onClick = { onPayInstallment(debt) }) {
                                    Text("+१ किस्ता तिर्नुहोस्", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                }
                            }
                            TextButton(
                                onClick = { onToggleSettle(debt) }
                            ) {
                                Text(
                                    if (isSettled) "बाँकी राख्नुहोस्" else "चुक्ता भयो ✓",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSettled) Color(0xFF64748B) else Color(0xFF059669)
                                )
                            }
                            IconButton(onClick = { onEdit(debt) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onDelete(debt) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------- ADD / EDIT DEBT / EMI DIALOG -----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDebtDialog(
    editingDebt: DebtEntity? = null,
    onDismiss: () -> Unit,
    onSave: (DebtEntity) -> Unit
) {
    val today = NepaliCalendar.getCurrentNepaliDate()
    var type by remember { mutableStateOf(editingDebt?.type ?: "lent") }
    var personName by remember { mutableStateOf(editingDebt?.personOrInstitution ?: "") }
    var phone by remember { mutableStateOf(editingDebt?.phoneNumber ?: "") }
    var amountText by remember { mutableStateOf(editingDebt?.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var emiAmountText by remember { mutableStateOf(editingDebt?.installmentAmount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var totalInstallmentsText by remember { mutableStateOf(editingDebt?.totalInstallments?.toString() ?: "12") }
    var paidInstallmentsText by remember { mutableStateOf(editingDebt?.paidInstallments?.toString() ?: "0") }
    var startDateText by remember { mutableStateOf(editingDebt?.startDateBs ?: "${today.year}-${today.month.toString().padStart(2, '0')}-${today.day.toString().padStart(2, '0')}") }

    val nextMonth = if (today.month == 12) 1 else today.month + 1
    val nextYear = if (today.month == 12) today.year + 1 else today.year
    var dueDateText by remember { mutableStateOf(editingDebt?.dueDateBs ?: "${nextYear}-${nextMonth.toString().padStart(2, '0')}-${today.day.toString().padStart(2, '0')}") }
    var notes by remember { mutableStateOf(editingDebt?.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingDebt != null) "ऋण / किस्ता सम्पादन (Edit Debt / EMI)" else "नयाँ ऋण / किस्ता थप्नुहोस् (Add Debt / EMI)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Type selector
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = type == "lent",
                            onClick = { type = "lent" },
                            label = { Text("दिएको (पाउने)") }
                        )
                        FilterChip(
                            selected = type == "borrowed",
                            onClick = { type = "borrowed" },
                            label = { Text("लिएको (तिर्ने)") }
                        )
                        FilterChip(
                            selected = type == "emi",
                            onClick = { type = "emi" },
                            label = { Text("किस्ता (EMI)") }
                        )
                    }
                }

                // Person or Institution Name
                item {
                    OutlinedTextField(
                        value = personName,
                        onValueChange = { personName = it },
                        label = { Text(if (type == "emi") "बैंक वा सामानको नाम (जस्तै: बजाज बाइक)" else "व्यक्तिको नाम वा संस्था") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Phone number
                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("सम्पर्क नम्बर (Phone)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Total Amount
                item {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text(if (type == "emi") "जम्मा कर्जा / मूल्य (रू)" else "ऋण रकम (रू)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // If EMI
                if (type == "emi") {
                    item {
                        OutlinedTextField(
                            value = emiAmountText,
                            onValueChange = { emiAmountText = it },
                            label = { Text("मासिक किस्ता रकम (रू)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = totalInstallmentsText,
                                onValueChange = { totalInstallmentsText = it },
                                label = { Text("कुल किस्ता") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = paidInstallmentsText,
                                onValueChange = { paidInstallmentsText = it },
                                label = { Text("तिरिएको किस्ता") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Start Date (B.S. Only)
                item {
                    OutlinedTextField(
                        value = startDateText,
                        onValueChange = { startDateText = it },
                        label = { Text("सुरु मिति केवल वि.सं. (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Due Date (B.S. Only)
                item {
                    OutlinedTextField(
                        value = dueDateText,
                        onValueChange = { dueDateText = it },
                        label = { Text(if (type == "emi") "किस्ता तिर्ने भाखा मिति वि.सं. (YYYY-MM-DD)" else "भाखा फिर्ता गर्ने अन्तिम मिति वि.सं. (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Notes
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("विवरण / नोट (Description / Notes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = NepaliCalendar.parseAmount(amountText)
                    if (personName.isNotBlank() && amt > 0) {
                        val emiAmt = if (type == "emi") NepaliCalendar.parseAmount(emiAmountText) else null
                        val totalInst = if (type == "emi") totalInstallmentsText.toIntOrNull() ?: 12 else null
                        val paidInst = if (type == "emi") paidInstallmentsText.toIntOrNull() ?: 0 else null

                        val newDebt = DebtEntity(
                            id = editingDebt?.id ?: "debt_${System.currentTimeMillis()}",
                            type = type,
                            personOrInstitution = personName.trim(),
                            phoneNumber = phone.trim().ifBlank { null },
                            amount = amt,
                            installmentAmount = emiAmt,
                            totalInstallments = totalInst,
                            paidInstallments = paidInst,
                            startDateBs = startDateText,
                            dueDateBs = dueDateText,
                            dueDateAd = editingDebt?.dueDateAd ?: "2026-11-15",
                            status = editingDebt?.status ?: "pending",
                            notes = notes.trim().ifBlank { null },
                            createdAt = editingDebt?.createdAt ?: System.currentTimeMillis()
                        )
                        onSave(newDebt)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
            ) {
                Text("सुरक्षित गर्नुहोस्", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("रद्द")
            }
        }
    )
}
