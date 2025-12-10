package app.oneleft

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

const val APP_VERSION = "1.0"
const val APP_URL = "https://1left.app"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            OneLeftApp()
        }
    }
}

// ============== THEME ==============

data class AppTheme(
    val name: String,
    val background: Color,
    val card: Color,
    val accent: Color,
    val warning: Color,
    val danger: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

val themes = listOf(
    AppTheme(
        name = "Dark",
        background = Color(0xFF0D0D0D),
        card = Color(0xFF1A1A1A),
        accent = Color(0xFF00D26A),
        warning = Color(0xFFFFBE0B),
        danger = Color(0xFFFF4757),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF888888)
    ),
    AppTheme(
        name = "Light",
        background = Color(0xFFF5F5F5),
        card = Color(0xFFFFFFFF),
        accent = Color(0xFF00B859),
        warning = Color(0xFFE5A800),
        danger = Color(0xFFE5384D),
        textPrimary = Color(0xFF1A1A1A),
        textSecondary = Color(0xFF666666)
    ),
    AppTheme(
        name = "Ocean",
        background = Color(0xFF0A1628),
        card = Color(0xFF152238),
        accent = Color(0xFF00B4D8),
        warning = Color(0xFFFFB703),
        danger = Color(0xFFFF6B6B),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF7B8CA3)
    ),
    AppTheme(
        name = "Purple",
        background = Color(0xFF1A1025),
        card = Color(0xFF2D1B3D),
        accent = Color(0xFFBB86FC),
        warning = Color(0xFFFFB86C),
        danger = Color(0xFFFF5555),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF9580A5)
    )
)

// ============== DATA ==============

data class Transaction(
    val amount: Double,
    val timestamp: Long
)

fun saveTransactions(prefs: android.content.SharedPreferences, transactions: List<Transaction>) {
    val data = transactions.joinToString(";") { "${it.amount},${it.timestamp}" }
    prefs.edit().putString("transactions", data).apply()
}

fun loadTransactions(prefs: android.content.SharedPreferences): List<Transaction> {
    val data = prefs.getString("transactions", "") ?: ""
    if (data.isEmpty()) return emptyList()
    return data.split(";").mapNotNull { item ->
        val parts = item.split(",")
        if (parts.size == 2) {
            Transaction(parts[0].toDoubleOrNull() ?: return@mapNotNull null, parts[1].toLongOrNull() ?: return@mapNotNull null)
        } else null
    }
}

// ============== MAIN APP ==============

@Composable
fun OneLeftApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("1left", Context.MODE_PRIVATE) }

    var balance by remember { mutableStateOf(prefs.getFloat("balance", 0f).toDouble()) }
    var initialLimit by remember { mutableStateOf(prefs.getFloat("initial_limit", 0f).toDouble()) }
    var limitStartDate by remember { mutableStateOf(prefs.getLong("limit_start_date", 0L)) }
    var transactions by remember { mutableStateOf(loadTransactions(prefs)) }
    var themeIndex by remember { mutableStateOf(prefs.getInt("theme", 0)) }

    var inputValue by remember { mutableStateOf("") }
    var isSettingLimit by remember { mutableStateOf(balance == 0.0 && initialLimit == 0.0) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // For Undo functionality
    val snackbarHostState = remember { SnackbarHostState() }
    var lastTransaction by remember { mutableStateOf<Transaction?>(null) }
    var balanceBeforeLastTransaction by remember { mutableStateOf(0.0) }
    val scope = rememberCoroutineScope()

    val theme = themes.getOrElse(themeIndex) { themes[0] }
    val focusManager = LocalFocusManager.current

    // Save on change
    LaunchedEffect(balance, initialLimit, themeIndex, limitStartDate) {
        prefs.edit()
            .putFloat("balance", balance.toFloat())
            .putFloat("initial_limit", initialLimit.toFloat())
            .putLong("limit_start_date", limitStartDate)
            .putInt("theme", themeIndex)
            .apply()
    }

    // % remaining
    val percentLeft = if (initialLimit > 0) (balance / initialLimit * 100).coerceIn(0.0, 100.0) else 0.0
    val balanceColor = when {
        percentLeft > 50 -> theme.accent
        percentLeft > 20 -> theme.warning
        else -> theme.danger
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = theme.card,
                    contentColor = theme.textPrimary,
                    actionColor = theme.accent
                )
            }
        },
        containerColor = theme.background
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .systemBarsPadding()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // History button
                    if (!isSettingLimit && transactions.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.List,
                            contentDescription = "History",
                            tint = theme.textSecondary,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showHistoryDialog = true }
                                .padding(4.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(32.dp))
                    }

                    // Logo
                    Text(
                        text = "1Left",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textSecondary,
                        letterSpacing = 3.sp
                    )

                    // Settings button
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = theme.textSecondary,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showSettingsDialog = true }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (isSettingLimit) {
                    // ===== SET LIMIT SCREEN =====
                    Text(
                        text = "Set your limit",
                        fontSize = 16.sp,
                        color = theme.textSecondary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    AmountInput(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        placeholder = "0",
                        textColor = theme.textPrimary,
                        placeholderColor = theme.textSecondary,
                        fontSize = 64,
                        onDone = {
                            val amount = inputValue.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                balance = amount
                                initialLimit = amount
                                limitStartDate = System.currentTimeMillis()
                                transactions = emptyList()
                                saveTransactions(prefs, transactions)
                                inputValue = ""
                                isSettingLimit = false
                                focusManager.clearFocus()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    ActionButton(
                        text = "Start",
                        color = theme.accent,
                        textColor = Color.Black,
                        enabled = (inputValue.toDoubleOrNull() ?: 0.0) > 0,
                        onClick = {
                            val amount = inputValue.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                balance = amount
                                initialLimit = amount
                                limitStartDate = System.currentTimeMillis()
                                transactions = emptyList()
                                saveTransactions(prefs, transactions)
                                inputValue = ""
                                isSettingLimit = false
                                focusManager.clearFocus()
                            }
                        }
                    )

                } else {
                    // ===== MAIN SCREEN =====

                    // Balance
                    AnimatedContent(
                        targetState = balance,
                        transitionSpec = {
                            slideInVertically { -it } + fadeIn() togetherWith
                                    slideOutVertically { it } + fadeOut()
                        },
                        label = "balance"
                    ) { targetBalance ->
                        Text(
                            text = formatMoney(targetBalance),
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold,
                            color = balanceColor,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(theme.card)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((percentLeft / 100).toFloat())
                                .fillMaxHeight()
                                .background(balanceColor)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "of ${formatMoney(initialLimit)}",
                        fontSize = 14.sp,
                        color = theme.textSecondary
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    // Input field
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "−",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Light,
                            color = theme.textSecondary
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        AmountInput(
                            value = inputValue,
                            onValueChange = { inputValue = it },
                            placeholder = "0",
                            textColor = theme.textPrimary,
                            placeholderColor = theme.textSecondary,
                            fontSize = 40,
                            onDone = {
                                val amount = inputValue.toDoubleOrNull() ?: 0.0
                                if (amount > 0) {
                                    val isOverBudget = amount > balance
                                    val newTransaction = Transaction(amount, System.currentTimeMillis())
                                    balanceBeforeLastTransaction = balance
                                    balance = (balance - amount).coerceAtLeast(0.0)
                                    transactions = transactions + newTransaction
                                    saveTransactions(prefs, transactions)
                                    lastTransaction = newTransaction
                                    inputValue = ""
                                    focusManager.clearFocus()

                                    scope.launch {
                                        val message = if (isOverBudget) {
                                            "−${formatMoney(amount)} (over budget!)"
                                        } else {
                                            "−${formatMoney(amount)}"
                                        }
                                        val result = snackbarHostState.showSnackbar(
                                            message = message,
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed && lastTransaction == newTransaction) {
                                            balance = balanceBeforeLastTransaction
                                            transactions = transactions.filter { it != newTransaction }
                                            saveTransactions(prefs, transactions)
                                            lastTransaction = null
                                        }
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Spend button
                    ActionButton(
                        text = "Spend",
                        color = theme.danger,
                        textColor = Color.White,
                        enabled = (inputValue.toDoubleOrNull() ?: 0.0) > 0,
                        onClick = {
                            val amount = inputValue.toDoubleOrNull() ?: 0.0
                            if (amount > 0) {
                                val isOverBudget = amount > balance
                                val newTransaction = Transaction(amount, System.currentTimeMillis())
                                balanceBeforeLastTransaction = balance
                                balance = (balance - amount).coerceAtLeast(0.0)
                                transactions = transactions + newTransaction
                                saveTransactions(prefs, transactions)
                                lastTransaction = newTransaction
                                inputValue = ""
                                focusManager.clearFocus()

                                scope.launch {
                                    val message = if (isOverBudget) {
                                        "−${formatMoney(amount)} (over budget!)"
                                    } else {
                                        "−${formatMoney(amount)}"
                                    }
                                    val result = snackbarHostState.showSnackbar(
                                        message = message,
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed && lastTransaction == newTransaction) {
                                        // Undo: restore balance and remove transaction
                                        balance = balanceBeforeLastTransaction
                                        transactions = transactions.filter { it != newTransaction }
                                        saveTransactions(prefs, transactions)
                                        lastTransaction = null
                                    }
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    // ===== DIALOGS =====

    // Reset confirmation dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = theme.card,
            titleContentColor = theme.textPrimary,
            textContentColor = theme.textSecondary,
            title = { Text("Reset everything?") },
            text = { Text("This will clear your limit and all transaction history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        balance = 0.0
                        initialLimit = 0.0
                        transactions = emptyList()
                        saveTransactions(prefs, transactions)
                        inputValue = ""
                        isSettingLimit = true
                        showResetDialog = false
                    }
                ) {
                    Text("Reset", color = theme.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }

    // Settings dialog
    if (showSettingsDialog) {
        Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.card)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Theme",
                    fontSize = 14.sp,
                    color = theme.textSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Theme selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    themes.forEachIndexed { index, t ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(t.background)
                                .clickable { themeIndex = index }
                                .then(
                                    if (index == themeIndex) {
                                        Modifier.background(
                                            color = t.accent.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(t.accent)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Reset button
                if (!isSettingLimit) {
                    TextButton(
                        onClick = {
                            showSettingsDialog = false
                            showResetDialog = true
                        }
                    ) {
                        Text("Reset limit", color = theme.danger)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Close button — main action
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Close", color = theme.textPrimary, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Divider
                HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.2f))

                Spacer(modifier = Modifier.height(12.dp))

                // About section — 'shallow' at the bottom
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "1Left v$APP_VERSION · ",
                        fontSize = 12.sp,
                        color = theme.textSecondary.copy(alpha = 0.5f)
                    )
                    Text(
                        text = APP_URL.removePrefix("https://"),
                        fontSize = 12.sp,
                        color = theme.accent.copy(alpha = 0.7f),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(APP_URL))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    // History dialog
    if (showHistoryDialog) {
        Dialog(onDismissRequest = { showHistoryDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(theme.card)
                    .padding(24.dp)
            ) {
                Text(
                    text = "History",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // The first line --- the date the limit was set
                    if (limitStartDate > 0) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Started: ${formatMoney(initialLimit)}",
                                    color = theme.accent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatDate(limitStartDate),
                                    color = theme.textSecondary,
                                    fontSize = 14.sp
                                )
                            }

                            if (transactions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = theme.textSecondary.copy(alpha = 0.2f))
                            }
                        }
                    }

                    // Transactions
                    items(transactions.reversed()) { transaction ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "−${formatMoney(transaction.amount)}",
                                color = theme.danger,
                                fontSize = 16.sp
                            )
                            Text(
                                text = formatDate(transaction.timestamp),
                                color = theme.textSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Empty list
                    if (transactions.isEmpty() && limitStartDate == 0L) {
                        item {
                            Text(
                                text = "No transactions yet",
                                color = theme.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { showHistoryDialog = false },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", color = theme.textSecondary)
                }
            }
        }
    }
}

// ============== COMPONENTS ==============

@Composable
fun AmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textColor: Color,
    placeholderColor: Color,
    fontSize: Int,
    onDone: () -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = { newValue ->
            val filtered = newValue.filter { it.isDigit() || it == '.' }
            if (filtered.count { it == '.' } <= 1) {
                onValueChange(filtered)
            }
        },
        textStyle = TextStyle(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Start
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        cursorBrush = SolidColor(textColor),
        modifier = Modifier.widthIn(min = 80.dp, max = 240.dp),
        decorationBox = { innerTextField ->
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box {
                    innerTextField()
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.Bold,
                            color = placeholderColor.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun ActionButton(
    text: String,
    color: Color,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = textColor,
            disabledContainerColor = color.copy(alpha = 0.3f),
            disabledContentColor = textColor.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .height(52.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============== UTILS ==============

fun formatMoney(amount: Double): String {
    return if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        String.format("%.2f", amount)
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}