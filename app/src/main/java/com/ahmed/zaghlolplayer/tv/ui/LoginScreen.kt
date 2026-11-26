package com.ahmed.zaghlolplayer.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmed.zaghlolplayer.tv.AppViewModel
import com.ahmed.zaghlolplayer.tv.LoginKind

@Composable
fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .border(
                2.dp,
                if (focused) ZaghlolColors.Accent else ZaghlolColors.Border,
                RoundedCornerShape(12.dp)
            )
            .background(ZaghlolColors.Panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            textStyle = TextStyle(color = ZaghlolColors.Text, fontSize = 19.sp),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(text = placeholder, color = ZaghlolColors.Dim, fontSize = 19.sp)
                }
                inner()
            }
        )
    }
}

@Composable
fun LoginScreen(vm: AppViewModel, onPickM3uFile: () -> Unit) {
    val state by vm.state.collectAsState()
    var m3uUrl by remember { mutableStateOf("") }
    var m3uName by remember { mutableStateOf("") }
    var xServer by remember { mutableStateOf("") }
    var xUser by remember { mutableStateOf("") }
    var xPass by remember { mutableStateOf("") }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 120.dp, vertical = 32.dp)
    ) {
        ZLogo(96.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Zaghlol Player",
            color = ZaghlolColors.Text,
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(text = "زغلول بلاير — مشغل IPTV", color = ZaghlolColors.Muted, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(28.dp))

        // Method selector
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip("رابط M3U", state.loginMethod == "m3u") { vm.setLoginMethod("m3u") }
            Chip("ملف M3U", state.loginMethod == "m3ufile") { vm.setLoginMethod("m3ufile") }
            Chip("Xtream Codes", state.loginMethod == "xtream") { vm.setLoginMethod("xtream") }
            Chip("قنوات تجريبية", state.loginMethod == "demo") { vm.setLoginMethod("demo") }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth(0.75f)) {
            when (state.loginMethod) {
                "m3u" -> {
                    LoginTextField(m3uUrl, { m3uUrl = it }, "https://example.com/playlist.m3u")
                    Spacer(modifier = Modifier.height(12.dp))
                    LoginTextField(m3uName, { m3uName = it }, "اسم الاشتراك (اختياري)")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton(
                            text = if (state.busy) "جارٍ التحميل... (${state.busySecs} ث)" else "تحميل القائمة",
                            onClick = { vm.loginM3u(m3uUrl, m3uName) },
                            enabled = !state.busy
                        )
                        GhostButton("اختبار الاتصال") { vm.testM3u(m3uUrl) }
                    }
                }
                "m3ufile" -> {
                    Text(
                        text = "اختر ملف قائمة M3U من جهاز التلفزيون (USB أو التخزين الداخلي).",
                        color = ZaghlolColors.Muted,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PrimaryButton("اختر ملف M3U", onClick = onPickM3uFile, enabled = !state.busy)
                }
                "xtream" -> {
                    LoginTextField(xServer, { xServer = it }, "http://server.com:8080")
                    Spacer(modifier = Modifier.height(12.dp))
                    LoginTextField(xUser, { xUser = it }, "اسم المستخدم")
                    Spacer(modifier = Modifier.height(12.dp))
                    LoginTextField(xPass, { xPass = it }, "كلمة المرور", password = true)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton(
                            text = if (state.busy) "جارٍ التحميل... (${state.busySecs} ث)" else "تسجيل الدخول",
                            onClick = { vm.loginXtream(xServer, xUser, xPass) },
                            enabled = !state.busy
                        )
                        GhostButton("اختبار الاتصال") { vm.testXtream(xServer, xUser, xPass) }
                    }
                }
                "demo" -> {
                    Text(
                        text = "جرّب المشغل من غير اشتراك — 5 قنوات مجانية (العربية، الجزيرة، Red Bull TV، DW، بث تجريبي).",
                        color = ZaghlolColors.Muted,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    PrimaryButton("تشغيل القنوات التجريبية", onClick = { vm.loginDemo() }, enabled = !state.busy)
                }
            }

            ErrorText(state.loginError)
            if (state.testReport.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ZaghlolColors.Panel, RoundedCornerShape(12.dp))
                        .border(1.dp, ZaghlolColors.Border, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = state.testReport,
                        color = if (state.testOk == true) ZaghlolColors.Good else ZaghlolColors.Text,
                        fontSize = 17.sp,
                        lineHeight = 26.sp
                    )
                }
            }
        }

        // Saved logins
        if (state.logins.isNotEmpty()) {
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "حسابات محفوظة",
                color = ZaghlolColors.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(0.75f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(0.75f)
            ) {
                for (login in state.logins) {
                    val kindName = when (login.kind) {
                        LoginKind.M3U_URL -> "رابط M3U"
                        LoginKind.M3U_FILE -> "ملف M3U"
                        LoginKind.XTREAM -> "Xtream Codes"
                        LoginKind.DEMO -> "تجريبي"
                    }
                    val sub = when (login.kind) {
                        LoginKind.XTREAM -> login.server
                        LoginKind.M3U_URL -> login.m3uUrl
                        else -> ""
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ZaghlolColors.Panel, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .tvClickable { vm.savedLogin(login.id) }
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = login.name, color = ZaghlolColors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(text = "$kindName — $sub", color = ZaghlolColors.Muted, fontSize = 15.sp)
                        }
                        GhostButton("✕") { vm.deleteLogin(login.id) }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Zaghlol Player v1.0.2 — للتلفزيون",
            color = ZaghlolColors.Dim,
            fontSize = 15.sp
        )
    }
}
