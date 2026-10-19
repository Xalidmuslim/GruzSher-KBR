package ru.kbr.cargoshare

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Optional offline training profile. No identity documents or passwords. */
internal data class ProfileFields(
    val displayName: String = "",
    val first: String = "",
    val last: String = "",
    val middle: String = "",
    val phone: String = "",
    val email: String = "",
    val city: String = "Нальчик",
    val address: String = "",
    val note: String = ""
)
internal fun validProfilePhone(value: String): Boolean =
    value.isBlank() || Regex("^\\+?[0-9() \\-]{7,22}$").matches(value)
internal fun validProfileEmail(value: String): Boolean =
    value.isBlank() || Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$").matches(value)

internal fun readProfileFields(p: SharedPreferences): ProfileFields =
    ProfileFields(
        displayName = p.getString("profile_alias", "Демо-пользователь") ?: "Демо-пользователь",
        first = p.getString("profile_first", "") ?: "",
        last = p.getString("profile_last", "") ?: "",
        middle = p.getString("profile_middle", "") ?: "",
        phone = p.getString("profile_phone", "") ?: "",
        email = p.getString("profile_email", "") ?: "",
        city = p.getString("profile_city", "Нальчик") ?: "Нальчик",
        address = p.getString("profile_address", "") ?: "",
        note = p.getString("profile_note", "") ?: ""
    )

internal fun storeProfileFields(prefs: SharedPreferences, f: ProfileFields): Boolean {
    if (f.displayName.isBlank() || !validProfilePhone(f.phone) ||
        !validProfileEmail(f.email)) return false
    return prefs.edit().putString("profile_alias", f.displayName.trim())
        .putString("profile_first", f.first.trim())
        .putString("profile_last", f.last.trim())
        .putString("profile_middle", f.middle.trim())
        .putString("profile_phone", f.phone.trim())
        .putString("profile_email", f.email.trim())
        .putString("profile_city", f.city.trim())
        .putString("profile_address", f.address.trim())
        .putString("profile_note", f.note.trim()).commit()
}

@Composable
internal fun ProfileDataForm(prefs: SharedPreferences, onSaved: (String) -> Unit) {
    val initial = remember(prefs) { readProfileFields(prefs) }
    var name by remember(initial) { mutableStateOf(initial.displayName) }
    var first by remember(initial) { mutableStateOf(initial.first) }
    var last by remember(initial) { mutableStateOf(initial.last) }
    var middle by remember(initial) { mutableStateOf(initial.middle) }
    var phone by remember(initial) { mutableStateOf(initial.phone) }
    var email by remember(initial) { mutableStateOf(initial.email) }
    var city by remember(initial) { mutableStateOf(initial.city) }
    var address by remember(initial) { mutableStateOf(initial.address) }
    var note by remember(initial) { mutableStateOf(initial.note) }
    var error by remember { mutableStateOf<String?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }
    Surface(color = PremiumTheme.Mint, shape = RoundedCornerShape(15.dp)) {
        Text("Учебная анкета сохраняется только на этом устройстве в обычных " +
            "настройках Android (без шифрования). Не вводите паспорт, банковские данные " +
            "или другую чувствительную информацию. Подтверждение телефона и email не подключено.",
            modifier = Modifier.padding(13.dp), fontSize = 11.sp, lineHeight = 16.sp,
            color = PremiumTheme.GreenDark)
    }
    @Composable fun field(label: String, value: String, max: Int = 80,
        onChange: (String) -> Unit) {
        OutlinedTextField(value = value, onValueChange = { onChange(it.take(max)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(label) })
    }
    Text("Отображение", fontWeight = FontWeight.Bold, color = PremiumTheme.Text)
    field("Имя на экране", name, 40) { name = it }
    Text("Личные данные (необязательно)", fontWeight = FontWeight.Bold,
        color = PremiumTheme.Text)
    field("Имя", first, 50) { first = it }
    field("Фамилия", last, 50) { last = it }
    field("Отчество", middle, 50) { middle = it }
    Text("Контакты", fontWeight = FontWeight.Bold, color = PremiumTheme.Text)
    field("Телефон", phone, 22) { phone = it }
    field("Электронная почта", email, 100) { email = it }
    Text("Адрес", fontWeight = FontWeight.Bold, color = PremiumTheme.Text)
    field("Город", city, 60) { city = it }
    field("Адрес для связи", address, 140) { address = it }
    OutlinedTextField(value = note, onValueChange = { note = it.take(200) },
        label = { Text("Дополнительные сведения") },
        minLines = 2, modifier = Modifier.fillMaxWidth())
    if (!validProfilePhone(phone)) Text("Укажите телефон в международном формате.",
        color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    if (!validProfileEmail(email)) Text("Некорректный email.",
        color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(modifier = Modifier.fillMaxWidth(),
        enabled = name.isNotBlank() && validProfilePhone(phone) && validProfileEmail(email),
        onClick = {
            val fields = ProfileFields(name, first, last, middle, phone, email, city, address, note)
            if (storeProfileFields(prefs, fields)) onSaved(name.trim())
            else error = "Не удалось сохранить анкету"
        }) { Text("Сохранить профиль") }
    OutlinedButton(onClick = { clearConfirm = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Очистить личные поля")
    }
    if (clearConfirm) {
        AlertDialog(onDismissRequest = { clearConfirm = false },
            title = { Text("Очистить анкету?") },
            text = { Text("Имя, контакты и адрес будут удалены с этого устройства. " +
                "Учебные поездки не затрагиваются.") },
            confirmButton = {
                TextButton(onClick = {
                    val blank = ProfileFields(displayName = "Демо-пользователь")
                    if (storeProfileFields(prefs, blank)) onSaved(blank.displayName)
                    clearConfirm = false
                }) { Text("Очистить") }
            }, dismissButton = {
                TextButton(onClick = { clearConfirm = false }) { Text("Отмена") }
            })
    }
}
