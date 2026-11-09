package com.metrolist.music.geras.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.geras.net.GerasDpi
import com.metrolist.music.geras.ui.GerasCheckbox
import com.metrolist.music.geras.ui.GerasSwitch
import com.metrolist.music.geras.ui.gerasAppear
import com.metrolist.music.geras.ui.gerasBounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Network section: one mode out of three (direct, built-in bypass, own proxy), the proxy server
 * when it is the user's own, which services go through it, and a check that shows whether
 * YouTube and SoundCloud answer with the current setting.
 */
@Composable
fun GerasProxySection() {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    remember { GerasDpi.init(context) }

    var mode by remember { mutableStateOf(GerasDpi.mode) }
    val saved = remember { GerasDpi.custom }
    var type by remember { mutableStateOf(saved.type) }
    var host by remember { mutableStateOf(saved.host) }
    var port by remember { mutableStateOf(if (saved.host.isBlank()) "" else saved.port.toString()) }
    var user by remember { mutableStateOf(saved.user) }
    var pass by remember { mutableStateOf(saved.pass) }
    var method by remember { mutableStateOf(GerasDpi.method) }
    var yt by remember { mutableStateOf(GerasDpi.service("yt")) }
    var sc by remember { mutableStateOf(GerasDpi.service("sc")) }
    var other by remember { mutableStateOf(GerasDpi.service("other")) }
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    val ms = stringResource(R.string.geras_proxy_ms)
    val dead = stringResource(R.string.geras_proxy_dead)
    val resultFmt = stringResource(R.string.geras_proxy_result)

    fun saveCustom() {
        GerasDpi.custom = GerasDpi.Custom(type, host.trim(), port.trim().toIntOrNull() ?: 0, user, pass)
        result = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(stringResource(R.string.geras_proxy_mode))
        Card {
            listOf(
                Triple(0, R.string.geras_proxy_off, R.string.geras_proxy_off_hint),
                Triple(1, R.string.geras_proxy_bypass, R.string.geras_proxy_bypass_hint),
                Triple(2, R.string.geras_proxy_custom, R.string.geras_proxy_custom_hint),
            ).forEachIndexed { i, (m, t, h) ->
                if (i > 0) Divider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .gerasBounce(0.98f)
                        .clickable {
                            mode = m
                            GerasDpi.mode = m
                            result = null
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(t), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Text(stringResource(h), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    GerasCheckbox(checked = mode == m, onCheckedChange = {
                        mode = m
                        GerasDpi.mode = m
                        result = null
                    })
                }
            }
        }

        AnimatedVisibility(mode == 1, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Card(Modifier.padding(top = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            method = (method + 1) % 3
                            GerasDpi.method = method
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                ) {
                    Icon(painterResource(R.drawable.tune), null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.geras_dpi_method), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Text(
                            stringResource(when (method) { 0 -> R.string.geras_dpi_method_c; 1 -> R.string.geras_dpi_method_a; else -> R.string.geras_dpi_method_b }),
                            fontSize = 12.5.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(mode == 2, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(stringResource(R.string.geras_proxy_server))
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "HTTP", 1 to "SOCKS5").forEach { (v, name) ->
                                val on = type == v
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .gerasBounce(0.95f)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (on) scheme.primary else scheme.onSurface.copy(alpha = 0.07f))
                                        .clickable { type = v },
                                ) {
                                    Text(name, fontWeight = FontWeight.Bold, color = if (on) scheme.onPrimary else scheme.onSurface)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field(host, { host = it }, stringResource(R.string.geras_proxy_host), Modifier.weight(1f), KeyboardType.Uri)
                            Field(port, { port = it.filter(Char::isDigit).take(5) }, stringResource(R.string.geras_proxy_port), Modifier.width(96.dp), KeyboardType.Number)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field(user, { user = it }, stringResource(R.string.geras_proxy_user), Modifier.weight(1f))
                            Field(pass, { pass = it }, stringResource(R.string.geras_proxy_pass), Modifier.weight(1f), KeyboardType.Password, secret = true)
                        }
                        Pill(stringResource(R.string.geras_proxy_save), primary = true) {
                            saveCustom()
                            Toast.makeText(context, R.string.geras_proxy_saved, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        AnimatedVisibility(mode != 0, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(stringResource(R.string.geras_proxy_services))
                Card {
                    ServiceRow(stringResource(R.string.geras_proxy_svc_yt), stringResource(R.string.geras_proxy_svc_yt_hint), yt) { yt = it; GerasDpi.setService("yt", it) }
                    Divider()
                    ServiceRow(stringResource(R.string.geras_proxy_svc_sc), stringResource(R.string.geras_proxy_svc_sc_hint), sc) { sc = it; GerasDpi.setService("sc", it) }
                    Divider()
                    ServiceRow(stringResource(R.string.geras_proxy_svc_other), stringResource(R.string.geras_proxy_svc_other_hint), other) { other = it; GerasDpi.setService("other", it) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Pill(stringResource(if (checking) R.string.geras_proxy_checking else R.string.geras_proxy_check), primary = false) {
            if (checking) return@Pill
            if (mode == 2) saveCustom()
            checking = true
            result = null
            scope.launch {
                val r = withContext(Dispatchers.IO) { GerasDpi.check() }
                fun fmt(v: Long?) = v?.let { String.format(ms, it) } ?: dead
                result = String.format(resultFmt, fmt(r["yt"]), fmt(r["sc"]))
                checking = false
            }
        }
        AnimatedVisibility(result != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                result.orEmpty(),
                fontSize = 14.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)))
}

@Composable
private fun ServiceRow(title: String, hint: String, on: Boolean, change: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { change(!on) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Text(hint, fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
        }
        GerasSwitch(checked = on, onCheckedChange = change, accent = scheme.primary, track = scheme.onSurface.copy(alpha = 0.15f))
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, keyboard: KeyboardType = KeyboardType.Text, secret: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.onSurface.copy(alpha = 0.12f),
        ),
        modifier = modifier,
    )
}

@Composable
private fun Pill(text: String, primary: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .gerasBounce(0.96f)
            .clip(RoundedCornerShape(50))
            .background(if (primary) scheme.onSurface else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (primary) scheme.surface else scheme.onSurface)
    }
}
