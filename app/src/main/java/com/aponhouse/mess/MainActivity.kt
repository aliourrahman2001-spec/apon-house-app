package com.aponhouse.mess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

private val Green = Color(0xFF166534)
private val PaleGreen = Color(0xFFE8F5E9)
private val Ink = Color(0xFF17251B)
private val db get() = FirebaseFirestore.getInstance()
private val auth get() = FirebaseAuth.getInstance()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AponTheme { AponApp() } }
    }
}

@Composable
private fun AponTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            secondary = Color(0xFF4D7C0F),
            background = Color(0xFFF7FAF7),
            surface = Color.White,
            onSurface = Ink
        ),
        content = content
    )
}

private data class RowDoc(val id: String, val data: Map<String, Any>) {
    fun str(key: String, fallback: String = "") = data[key]?.toString() ?: fallback
    fun number(key: String) = (data[key] as? Number)?.toDouble() ?: 0.0
}

@Composable
private fun AponApp() {
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { currentUser = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val user = currentUser
    if (user == null) {
        LoginScreen()
    } else {
        var profile by remember(user.uid) { mutableStateOf<Map<String, Any>?>(null) }
        var profileLoaded by remember(user.uid) { mutableStateOf(false) }
        DisposableEffect(user.uid) {
            val reg = db.collection("members").document(user.uid).addSnapshotListener { snap, _ ->
                profile = snap?.data
                profileLoaded = true
            }
            onDispose { reg.remove() }
        }
        when {
            !profileLoaded -> CenterMessage("অ্যাকাউন্ট যাচাই হচ্ছে…")
            profile == null -> PendingScreen("আপনার সদস্য প্রোফাইল পাওয়া যায়নি। Owner-কে আপনার account approve করতে বলুন।")
            profile?.get("role")?.toString() == "pending" -> PendingScreen("আপনার আবেদন জমা হয়েছে। Owner অনুমতি দিলে অ্যাপ ব্যবহার করতে পারবেন।")
            else -> MainHome(user.uid, profile ?: emptyMap())
        }
    }
}

@Composable
private fun LoginScreen() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var createMode by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("APON HOUSE", style = MaterialTheme.typography.headlineMedium, color = Green, fontWeight = FontWeight.ExtraBold)
        Text("Mess Management", color = Color.Gray)
        Spacer(Modifier.height(26.dp))
        if (createMode) OutlinedTextField(name, { name = it }, label = { Text("আপনার নাম") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Button(
            enabled = !busy && email.isNotBlank() && password.length >= 6 && (!createMode || name.isNotBlank()),
            onClick = {
                busy = true; message = ""
                if (createMode) {
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { result ->
                            val uid = result.user?.uid ?: return@addOnSuccessListener
                            db.collection("members").document(uid).set(
                                mapOf("uid" to uid, "displayName" to name.trim(), "email" to email.trim(), "role" to "pending", "active" to false, "createdAt" to Timestamp.now())
                            ).addOnSuccessListener { message = "Account তৈরি হয়েছে। Owner approval প্রয়োজন।"; busy = false }
                                .addOnFailureListener { message = it.localizedMessage ?: "Profile save হয়নি"; busy = false }
                        }
                        .addOnFailureListener { message = it.localizedMessage ?: "Account তৈরি হয়নি"; busy = false }
                } else {
                    auth.signInWithEmailAndPassword(email.trim(), password)
                        .addOnSuccessListener { busy = false }
                        .addOnFailureListener { message = it.localizedMessage ?: "Login হয়নি"; busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "অপেক্ষা করুন…" else if (createMode) "Account তৈরি করুন" else "Login") }
        TextButton(onClick = { createMode = !createMode; message = "" }) { Text(if (createMode) "আগে account আছে? Login" else "নতুন সদস্য? Account তৈরি করুন") }
        if (message.isNotBlank()) Text(message, color = Green, modifier = Modifier.padding(top = 8.dp))
        Text("Owner আগে Firebase Console থেকে নিজের account তৈরি করে members/{UID}-তে role = owner সেট করবেন।", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 22.dp))
    }
}

@Composable
private fun PendingScreen(message: String) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.HourglassTop, null, tint = Green, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp)); Text("Apon House", style = MaterialTheme.typography.headlineSmall, color = Green)
        Spacer(Modifier.height(8.dp)); Text(message)
        Spacer(Modifier.height(18.dp)); OutlinedButton(onClick = { auth.signOut() }) { Text("Logout") }
    }
}

@Composable
private fun CenterMessage(value: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(); Text(value, modifier = Modifier.padding(top = 90.dp)) }
}

private val isAdminRole = setOf("owner", "monthly_manager", "monitor")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainHome(uid: String, profile: Map<String, Any>) {
    val role = profile["role"]?.toString() ?: "member"
    val isAdmin = role in isAdminRole
    val displayName = profile["displayName"]?.toString() ?: profile["email"]?.toString() ?: "সদস্য"
    var page by remember { mutableStateOf("home") }
    var members by remember { mutableStateOf(listOf<RowDoc>()) }
    var meals by remember { mutableStateOf(listOf<RowDoc>()) }
    var guests by remember { mutableStateOf(listOf<RowDoc>()) }
    var payments by remember { mutableStateOf(listOf<RowDoc>()) }
    var expenses by remember { mutableStateOf(listOf<RowDoc>()) }
    var settings by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var loadError by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        val regs = mutableListOf<ListenerRegistration>()
        fun watch(collection: String, update: (List<RowDoc>) -> Unit) {
            regs += db.collection(collection).addSnapshotListener { snap, err ->
                if (err != null) loadError = err.localizedMessage ?: "ডাটা লোড হয়নি"
                else update(snap?.documents?.map { RowDoc(it.id, it.data ?: emptyMap()) } ?: emptyList())
            }
        }
        watch("members") { members = it }
        watch("dailyMeals") { meals = it }
        watch("guestMeals") { guests = it }
        watch("payments") { payments = it }
        watch("expenses") { expenses = it }
        regs += db.collection("settings").document("current").addSnapshotListener { snap, err ->
            if (err != null) loadError = err.localizedMessage ?: "Settings load হয়নি" else settings = snap?.data ?: emptyMap()
        }
        onDispose { regs.forEach { it.remove() } }
    }

    val mealIncome = payments.filter { it.str("category") == "meal" }.sumOf { it.number("amount") }
    val otherIncome = payments.filter { it.str("category") != "meal" }.sumOf { it.number("amount") }
    val expenseTotal = expenses.sumOf { it.number("amount") }
    val totalIncome = mealIncome + otherIncome
    val activeMembers = members.filter { it.str("role") != "pending" && it.data["active"] != false }.sortedBy { it.str("displayName").lowercase(Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("APON HOUSE", fontWeight = FontWeight.Bold); Text(displayName, style = MaterialTheme.typography.labelSmall, color = Color.Gray) } },
                actions = { IconButton(onClick = { auth.signOut() }) { Icon(Icons.Default.Logout, "Logout") } }
            )
        },
        bottomBar = {
            NavigationBar {
                listOf("home" to Icons.Default.Home, "meals" to Icons.Default.Restaurant, "guests" to Icons.Default.People, "payments" to Icons.Default.Payments, "more" to Icons.Default.Menu).forEach { (key, icon) ->
                    NavigationBarItem(selected = page == key, onClick = { page = key }, icon = { Icon(icon, null) }, label = { Text(when(key) { "home" -> "হোম"; "meals" -> "মিল"; "guests" -> "গেস্ট"; "payments" -> "পেমেন্ট"; else -> "আরও" }) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (loadError.isNotBlank()) Text(loadError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            when (page) {
                "home" -> DashboardScreen(totalIncome, expenseTotal, totalIncome - expenseTotal, members.size, meals.size, guests.size, payments, expenses)
                "meals" -> MealsScreen(uid, displayName, meals, settings)
                "guests" -> GuestsScreen(uid, displayName, guests)
                "payments" -> PaymentsScreen(uid, displayName, payments)
                "expenses" -> ExpensesScreen(uid, displayName, isAdmin, expenses)
                "duties" -> DutiesScreen(activeMembers, settings)
                "settings" -> SettingsScreen(settings, isAdmin)
                "reports" -> ReportsScreen(payments, expenses, meals, guests)
                else -> MoreScreen(
                    uid, displayName, isAdmin, activeMembers, settings, expenses, meals, guests, payments,
                    onExpenses = { page = "expenses" }, onDuties = { page = "duties" }, onSettings = { page = "settings" }, onReports = { page = "reports" }, onBack = { page = "home" }
                )
            }
        }
    }
}

@Composable
private fun DashboardScreen(income: Double, expense: Double, balance: Double, memberCount: Int, mealCount: Int, guestCount: Int, payments: List<RowDoc>, expenses: List<RowDoc>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("আজকের মেস ড্যাশবোর্ড", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("সদস্য", memberCount.toString(), Modifier.weight(1f))
            SummaryCard("মিল এন্ট্রি", mealCount.toString(), Modifier.weight(1f))
            SummaryCard("গেস্ট এন্ট্রি", guestCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("মোট আয়", money(income), Modifier.weight(1f))
            SummaryCard("মোট ব্যয়", money(expense), Modifier.weight(1f))
        }
        Card(colors = CardDefaults.cardColors(containerColor = PaleGreen), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) { Text("বর্তমান ব্যালেন্স", color = Green); Text(money(balance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Green) }
        }
        Text("সাম্প্রতিক পেমেন্ট", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        payments.take(5).forEach { RecordLine(it.str("memberName", "সদস্য"), "${it.str("date")} • ${it.str("category")}", money(it.number("amount"))) }
        Text("সাম্প্রতিক খরচ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        expenses.take(5).forEach { RecordLine(it.str("category", "খরচ"), it.str("date"), money(it.number("amount"))) }
        Text("নোট: এখানে আয় ও ব্যয় সংরক্ষিত এন্ট্রি থেকে হিসাব হচ্ছে; বকেয়া ও স্বয়ংক্রিয় জরিমানা আলাদা মডিউল হিসেবে পরের ধাপে যুক্ত করতে হবে।", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(12.dp)) { Text(label, color = Color.Gray, style = MaterialTheme.typography.labelMedium); Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Green) } }
}

@Composable
private fun MealsScreen(uid: String, name: String, records: List<RowDoc>, settings: Map<String, Any>) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var breakfast by remember { mutableStateOf(true) }
    var afternoon by remember { mutableStateOf(true) }
    var dinner by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    val todayRecords = records.filter { it.str("uid") == uid || it.str("memberName") == name }.sortedByDescending { it.str("date") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("দৈনিক মিল", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(date, { date = it }, label = { Text("তারিখ (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        MealCheck("সকাল • ৳${settings.valueInt("breakfastRate", 10)}", breakfast) { breakfast = it }
        MealCheck("বিকাল • ৳${settings.valueInt("afternoonRate", 25)}", afternoon) { afternoon = it }
        MealCheck("রাত • ৳${settings.valueInt("dinnerRate", 10)}", dinner) { dinner = it }
        Button(onClick = {
            val rates = mapOf("breakfast" to settings.valueInt("breakfastRate",10), "afternoon" to settings.valueInt("afternoonRate",25), "dinner" to settings.valueInt("dinnerRate",10))
            val amount = (if (breakfast) rates.getValue("breakfast") else 0) + (if (afternoon) rates.getValue("afternoon") else 0) + (if (dinner) rates.getValue("dinner") else 0)
            db.collection("dailyMeals").add(mapOf("uid" to uid, "memberName" to name, "date" to date, "breakfast" to breakfast, "afternoon" to afternoon, "dinner" to dinner, "amount" to amount, "createdAt" to Timestamp.now()))
                .addOnSuccessListener { message = "মিল সংরক্ষণ হয়েছে • ৳$amount" }.addOnFailureListener { message = it.localizedMessage ?: "সংরক্ষণ হয়নি" }
        }, modifier = Modifier.fillMaxWidth()) { Text("মিল সংরক্ষণ করুন") }
        if (message.isNotBlank()) Text(message, color = Green)
        DividerLine()
        Text("আপনার মিলের রেকর্ড", fontWeight = FontWeight.Bold)
        todayRecords.take(30).forEach { RecordLine(it.str("date"), "${if(it.data["breakfast"] == true) "সকাল " else ""}${if(it.data["afternoon"] == true) "বিকাল " else ""}${if(it.data["dinner"] == true) "রাত" else ""}", money(it.number("amount"))) }
    }
}

@Composable
private fun MealCheck(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card { Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, onChange); Text(text) } }
}

@Composable
private fun GuestsScreen(uid: String, name: String, records: List<RowDoc>) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var guestName by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("1") }
    var amount by remember { mutableStateOf("45") }
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("গেস্ট মিল", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(date, { date = it }, label = { Text("তারিখ") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(guestName, { guestName = it }, label = { Text("অতিথির নাম") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(count, { count = it.filter(Char::isDigit) }, label = { Text("গেস্ট মিল সংখ্যা") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("মোট টাকা") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            db.collection("guestMeals").add(mapOf("uid" to uid, "memberName" to name, "guestName" to guestName.trim(), "count" to (count.toIntOrNull() ?: 1), "amount" to (amount.toDoubleOrNull() ?: 0.0), "date" to date, "createdAt" to Timestamp.now()))
                .addOnSuccessListener { message = "গেস্ট মিল সংরক্ষণ হয়েছে" }.addOnFailureListener { message = it.localizedMessage ?: "সংরক্ষণ হয়নি" }
        }, enabled = guestName.isNotBlank() && (count.toIntOrNull() ?: 0) > 0, modifier = Modifier.fillMaxWidth()) { Text("গেস্ট মিল যোগ করুন") }
        if (message.isNotBlank()) Text(message, color = Green)
        DividerLine(); Text("গেস্ট মিলের তালিকা", fontWeight = FontWeight.Bold)
        records.sortedByDescending { it.str("date") }.take(50).forEach { RecordLine("${it.str("memberName")} • ${it.str("guestName")}", "${it.str("date")} • ${it.data["count"] ?: 0} মিল", money(it.number("amount"))) }
    }
}

@Composable
private fun PaymentsScreen(uid: String, name: String, records: List<RowDoc>) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("meal") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("পেমেন্ট জমা", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(date, { date = it }, label = { Text("তারিখ") }, modifier = Modifier.fillMaxWidth())
        Text("পেমেন্টের ধরন")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("meal" to "মিল", "room_rent" to "রুম ভাড়া", "other" to "অন্যান্য").forEach { (v, label) -> FilterChip(selected = category == v, onClick = { category = v }, label = { Text(label) }) }
        }
        OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("টাকার পরিমাণ") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("নোট (ঐচ্ছিক)") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            db.collection("payments").add(mapOf("uid" to uid, "memberName" to name, "category" to category, "amount" to (amount.toDoubleOrNull() ?: 0.0), "date" to date, "note" to note, "createdAt" to Timestamp.now()))
                .addOnSuccessListener { message = "পেমেন্ট এন্ট্রি সংরক্ষণ হয়েছে" }.addOnFailureListener { message = it.localizedMessage ?: "সংরক্ষণ হয়নি" }
        }, enabled = (amount.toDoubleOrNull() ?: 0.0) > 0, modifier = Modifier.fillMaxWidth()) { Text("পেমেন্ট জমা দিন") }
        if (message.isNotBlank()) Text(message, color = Green)
        DividerLine(); Text("আপনার পেমেন্ট তালিকা", fontWeight = FontWeight.Bold)
        records.filter { it.str("uid") == uid }.sortedByDescending { it.str("date") }.take(40).forEach { RecordLine(it.str("date"), it.str("category"), money(it.number("amount"))) }
    }
}

@Composable
private fun MoreScreen(uid: String, name: String, isAdmin: Boolean, members: List<RowDoc>, settings: Map<String, Any>, expenses: List<RowDoc>, meals: List<RowDoc>, guests: List<RowDoc>, payments: List<RowDoc>, onExpenses: () -> Unit, onDuties: () -> Unit, onSettings: () -> Unit, onReports: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("অন্যান্য ব্যবস্থাপনা", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        MenuCard("খরচ / বাজার / বিল", "Current, Wi-Fi, খালা, বাজার ও অন্যান্য খরচ", onExpenses, isAdmin)
        MenuCard("ডিউটি রোটেশন", "বাজার প্রতি ২ দিন • টয়লেট প্রতি ১০ দিন", onDuties, true)
        MenuCard("মাসিক রিপোর্ট", "আয়, ব্যয়, পেমেন্ট ও মিল এন্ট্রি", onReports, true)
        MenuCard("সেটিংস ও চার্জ", "মিল রেট, মাসিক চার্জ, রুম ভাড়া ও জরিমানা", onSettings, isAdmin)
        Text("আপনার Role: ${if(isAdmin) "Admin" else "Member"}", color = Green, fontWeight = FontWeight.Bold)
        Text("Monitor এবং Monthly Manager-কে Admin permission দেওয়া হয়েছে।", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        OutlinedButton(onClick = onBack) { Text("হোমে ফিরুন") }
    }
}

@Composable
private fun MenuCard(title: String, subtitle: String, action: () -> Unit, enabled: Boolean) {
    Card(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            Icon(if (enabled) Icons.Default.ChevronRight else Icons.Default.Lock, null, tint = Green)
        }
    }
}

@Composable
private fun ExpensesScreen(uid: String, name: String, isAdmin: Boolean, records: List<RowDoc>) {
    var category by remember { mutableStateOf("বাজার") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var message by remember { mutableStateOf("") }
    if (!isAdmin) { CenterMessage("শুধু Admin খরচ যোগ করতে পারবেন"); return }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("মেসের খরচ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(date, { date = it }, label = { Text("তারিখ") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(category, { category = it }, label = { Text("খরচের ধরন: Current / Wi-Fi / খালা / বাজার") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("টাকা") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("বিবরণ") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            db.collection("expenses").add(mapOf("category" to category.trim(), "amount" to (amount.toDoubleOrNull() ?: 0.0), "note" to note, "date" to date, "createdBy" to uid, "createdByName" to name, "createdAt" to Timestamp.now()))
                .addOnSuccessListener { message = "খরচ সংরক্ষণ হয়েছে" }.addOnFailureListener { message = it.localizedMessage ?: "সংরক্ষণ হয়নি" }
        }, enabled = category.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0, modifier = Modifier.fillMaxWidth()) { Text("খরচ যোগ করুন") }
        if (message.isNotBlank()) Text(message, color = Green)
        DividerLine(); Text("খরচের রেকর্ড", fontWeight = FontWeight.Bold)
        records.sortedByDescending { it.str("date") }.take(60).forEach { RecordLine(it.str("category"), "${it.str("date")} • ${it.str("note")}", money(it.number("amount"))) }
    }
}

@Composable
private fun DutiesScreen(members: List<RowDoc>, settings: Map<String, Any>) {
    val list = members.sortedBy { it.str("displayName").lowercase(Locale.getDefault()) }
    val bazarStart = parseDate(settings["bazarStartDate"]?.toString())
    val cleanStart = parseDate(settings["cleaningStartDate"]?.toString())
    val bazar = dutyNow(list, bazarStart, settings.valueInt("bazarIntervalDays", 2))
    val clean = dutyNow(list, cleanStart, settings.valueInt("cleaningIntervalDays", 10))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("ডিউটি রোটেশন", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        DutyCard("বাজার ডিউটি", bazar.first, bazar.second, bazar.third, settings.valueInt("bazarIntervalDays",2))
        DutyCard("টয়লেট পরিষ্কার", clean.first, clean.second, clean.third, settings.valueInt("cleaningIntervalDays",10))
        if (list.isEmpty()) Text("কোনো active member পাওয়া যায়নি। সদস্যদের profile approve করতে হবে।")
        Text("Rotation list: ${list.joinToString(" → ") { it.str("displayName", "নাম নেই") }}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text("বর্তমান ও পরবর্তী দায়িত্ব নির্ধারণের জন্য Settings-এ শুরু তারিখ দিন। সদস্যদের নামের ক্রম অনুযায়ী rotation হবে।", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

private data class DutyInfo(val current: String, val next: String, val nextDate: String)
private fun dutyNow(members: List<RowDoc>, start: LocalDate, interval: Int): Triple<String, String, String> {
    if (members.isEmpty()) return Triple("সদস্য যোগ হয়নি", "সদস্য যোগ হয়নি", "—")
    val days = ChronoUnit.DAYS.between(start, LocalDate.now()).coerceAtLeast(0)
    val cycle = (days / interval.coerceAtLeast(1)).toInt()
    val currentIndex = cycle % members.size
    val nextIndex = (currentIndex + 1) % members.size
    val daysUntilNext = interval.coerceAtLeast(1) - (days % interval.coerceAtLeast(1))
    val nextDate = LocalDate.now().plusDays(daysUntilNext)
    return Triple(members[currentIndex].str("displayName", "নাম নেই"), members[nextIndex].str("displayName", "নাম নেই"), nextDate.toString())
}
private fun parseDate(value: String?): LocalDate = try { LocalDate.parse(value) } catch (_: Exception) { LocalDate.now() }

@Composable
private fun DutyCard(title: String, current: String, next: String, nextDate: String, interval: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Green)
            Text("বর্তমান দায়িত্ব: $current")
            Text("পরবর্তী দায়িত্ব: $next", fontWeight = FontWeight.ExtraBold, color = Green)
            Text("পরবর্তী তারিখ: $nextDate • প্রতি $interval দিন", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun SettingsScreen(settings: Map<String, Any>, isAdmin: Boolean) {
    if (!isAdmin) { CenterMessage("শুধু Admin Settings পরিবর্তন করতে পারবেন"); return }
    var monthly by remember(settings) { mutableStateOf(settings.valueInt("monthlyMealCharge", 1500).toString()) }
    var room by remember(settings) { mutableStateOf(settings.valueInt("roomRentDefault", 1000).toString()) }
    var breakfast by remember(settings) { mutableStateOf(settings.valueInt("breakfastRate", 10).toString()) }
    var afternoon by remember(settings) { mutableStateOf(settings.valueInt("afternoonRate", 25).toString()) }
    var dinner by remember(settings) { mutableStateOf(settings.valueInt("dinnerRate", 10).toString()) }
    var cday by remember(settings) { mutableStateOf(settings.valueInt("cleaningIntervalDays", 10).toString()) }
    var bday by remember(settings) { mutableStateOf(settings.valueInt("bazarIntervalDays", 2).toString()) }
    var cleanStart by remember(settings) { mutableStateOf(settings["cleaningStartDate"]?.toString() ?: LocalDate.now().toString()) }
    var bazarStart by remember(settings) { mutableStateOf(settings["bazarStartDate"]?.toString() ?: LocalDate.now().toString()) }
    var fine5 by remember(settings) { mutableStateOf(settings.valueInt("fineBy5", 10).toString()) }
    var fine10 by remember(settings) { mutableStateOf(settings.valueInt("fineBy10", 20).toString()) }
    var fine15 by remember(settings) { mutableStateOf(settings.valueInt("fineBy15", 50).toString()) }
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("মেসের Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("মাসিক চার্জ", fontWeight = FontWeight.Bold)
        SettingField("মাসিক Meal Charge (৳)", monthly) { monthly = it }
        SettingField("Default Room Rent (৳)", room) { room = it }
        Text("প্রতি মিলের হার", fontWeight = FontWeight.Bold)
        SettingField("সকাল (৳)", breakfast) { breakfast = it }; SettingField("বিকাল (৳)", afternoon) { afternoon = it }; SettingField("রাত (৳)", dinner) { dinner = it }
        Text("ডিউটি", fontWeight = FontWeight.Bold)
        SettingField("টয়লেট পরিষ্কারের বিরতি (দিন)", cday) { cday = it }; SettingField("বাজার ডিউটির বিরতি (দিন)", bday) { bday = it }
        SettingField("টয়লেট rotation শুরুর তারিখ", cleanStart) { cleanStart = it }; SettingField("বাজার rotation শুরুর তারিখ", bazarStart) { bazarStart = it }
        Text("জরিমানা", fontWeight = FontWeight.Bold)
        SettingField("৫ তারিখের জরিমানা (৳)", fine5) { fine5 = it }; SettingField("১০ তারিখের জরিমানা (৳)", fine10) { fine10 = it }; SettingField("১৫ তারিখের জরিমানা (৳)", fine15) { fine15 = it }
        Button(onClick = {
            val data = mapOf(
                "monthlyMealCharge" to monthly.toIntSafe(1500), "roomRentDefault" to room.toIntSafe(1000),
                "breakfastRate" to breakfast.toIntSafe(10), "afternoonRate" to afternoon.toIntSafe(25), "dinnerRate" to dinner.toIntSafe(10),
                "cleaningIntervalDays" to cday.toIntSafe(10), "bazarIntervalDays" to bday.toIntSafe(2),
                "cleaningStartDate" to cleanStart, "bazarStartDate" to bazarStart,
                "fineBy5" to fine5.toIntSafe(10), "fineBy10" to fine10.toIntSafe(20), "fineBy15" to fine15.toIntSafe(50),
                "updatedAt" to Timestamp.now()
            )
            db.collection("settings").document("current").set(data, SetOptions.merge())
                .addOnSuccessListener { message = "Settings সংরক্ষণ হয়েছে" }.addOnFailureListener { message = it.localizedMessage ?: "সংরক্ষণ হয়নি" }
        }, modifier = Modifier.fillMaxWidth()) { Text("Settings Save করুন") }
        if (message.isNotBlank()) Text(message, color = Green)
        Text("ডিফল্ট পেমেন্ট নিয়ম: ৫ তারিখে ৳৫০০, ১০ তারিখে মোট ৳১,৫০০, ১৫ তারিখে Meal + Room Rent ৳২,৫০০। Fine amount উপরে পরিবর্তনযোগ্য। স্বয়ংক্রিয় fine posting এখনো চালু নয়।", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun SettingField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun ReportsScreen(payments: List<RowDoc>, expenses: List<RowDoc>, meals: List<RowDoc>, guests: List<RowDoc>) {
    val income = payments.sumOf { it.number("amount") }
    val out = expenses.sumOf { it.number("amount") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("মেসের সারাংশ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        RecordLine("মোট Payment", "${payments.size}টি এন্ট্রি", money(income))
        RecordLine("মোট Expense", "${expenses.size}টি এন্ট্রি", money(out))
        RecordLine("ব্যালেন্স", "আয় - ব্যয়", money(income - out))
        RecordLine("মোট Meal Entry", "${meals.size}টি", "")
        RecordLine("মোট Guest Entry", "${guests.size}টি", "")
        Text("এই রিপোর্ট সব সংরক্ষিত রেকর্ডের মোট দেখায়; মাস অনুযায়ী ফিল্টার ও PDF Export পরবর্তী উন্নয়নে যোগ করতে হবে।", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun RecordLine(title: String, subtitle: String, amount: String) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); if (subtitle.isNotBlank()) Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
        if (amount.isNotBlank()) Text(amount, fontWeight = FontWeight.Bold, color = Green)
    } }
}

@Composable
private fun DividerLine() { HorizontalDivider(color = Color(0xFFE5E7EB), modifier = Modifier.padding(vertical = 4.dp)) }
private fun money(v: Double) = "৳" + String.format(Locale.US, "%.0f", v)
private fun Map<String, Any>.valueInt(key: String, fallback: Int) = (this[key] as? Number)?.toInt() ?: fallback
private fun String.toIntSafe(fallback: Int) = toIntOrNull()?.coerceAtLeast(0) ?: fallback
