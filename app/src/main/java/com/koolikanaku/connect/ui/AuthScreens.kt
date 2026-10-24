package com.koolikanaku.connect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.House
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.koolikanaku.R
import com.koolikanaku.connect.model.Role
import com.koolikanaku.ui.AppLanguage
import com.koolikanaku.ui.components.DarkModeToggle
import com.koolikanaku.ui.components.LanguageToggle

private fun t(ml: Boolean, en: String, malayalam: String) = if (ml) malayalam else en

/**
 * extra_feature.md Section 9: "Welcome / Sign in". Phone + 6-digit PIN (Section 16: no SMS OTP
 * on this backend), plus the escape hatch back to the pure offline estimator that never needs
 * an account (Section 4.1 "Use without an account").
 */
@Composable
fun WelcomeSignInScreen(
    isSignUp: Boolean,
    onToggleMode: () -> Unit,
    phoneText: String,
    onPhoneChange: (String) -> Unit,
    pinText: String,
    onPinChange: (String) -> Unit,
    nameText: String,
    onNameChange: (String) -> Unit,
    error: String?,
    onSubmit: () -> Unit,
    onUseWithoutAccount: () -> Unit,
    language: AppLanguage = AppLanguage.EN,
    onLanguageChange: (AppLanguage) -> Unit = {},
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val ml = language == AppLanguage.ML

    Scaffold(modifier = modifier) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(tween(550, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(550, easing = FastOutSlowInEasing)) { it / 3 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp)) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 10.dp,
                            modifier = Modifier.size(96.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.app_logo),
                                contentDescription = stringResource(R.string.app_name),
                                modifier = Modifier.fillMaxSize().padding(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text("KooliKanaku", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text("കൂലിക്കണക്ക്", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(tween(550, delayMillis = 90, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(550, delayMillis = 90, easing = FastOutSlowInEasing)) { it / 3 }
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(22.dp)) {
                                AnimatedVisibility(
                                    visible = isSignUp,
                                    enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                                    exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
                                ) {
                                    OutlinedTextField(
                                        value = nameText, onValueChange = onNameChange,
                                        label = { Text(t(ml, "Your name", "നിങ്ങളുടെ പേര്")) }, singleLine = true,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                                    )
                                }
                                OutlinedTextField(
                                    value = phoneText, onValueChange = onPhoneChange,
                                    label = { Text(t(ml, "Phone number", "ഫോൺ നമ്പർ")) },
                                    leadingIcon = { Text("+91", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                                )
                                OutlinedTextField(
                                    value = pinText, onValueChange = onPinChange,
                                    label = { Text(if (isSignUp) t(ml, "Create a 6-digit PIN", "6 അക്ക പിൻ ഉണ്ടാക്കുക") else t(ml, "6-digit PIN", "6 അക്ക പിൻ")) },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                AnimatedVisibility(
                                    visible = error != null,
                                    enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                                ) {
                                    Text(
                                        error.orEmpty(), color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(22.dp))
                                Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                                    Text(
                                        if (isSignUp) t(ml, "Create account", "അക്കൗണ്ട് ഉണ്ടാക്കുക") else t(ml, "Sign in", "സൈൻ ഇൻ ചെയ്യുക"),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                TextButton(onClick = onToggleMode, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                                    Text(
                                        if (isSignUp) t(ml, "Already have an account? Sign in", "അക്കൗണ്ട് ഉണ്ടോ? സൈൻ ഇൻ ചെയ്യുക")
                                        else t(ml, "New here? Create an account", "പുതിയ ആളാണോ? അക്കൗണ്ട് ഉണ്ടാക്കുക")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(onClick = onUseWithoutAccount, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                            Text(t(ml, "Use without an account", "അക്കൗണ്ട് ഇല്ലാതെ ഉപയോഗിക്കുക"))
                        }
                        Text(
                            t(ml, "Goes straight to the offline estimator. No sign-in, no network, ever.", "നേരിട്ട് ഓഫ്‌ലൈൻ എസ്റ്റിമേറ്ററിലേക്ക് പോകും. സൈൻ ഇൻ വേണ്ട, ഇന്റർനെറ്റ് വേണ്ട."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp, bottom = 28.dp)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LanguageToggle(language = language, onLanguageChange = onLanguageChange)
                DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
            }
        }
    }
}

/** extra_feature.md Section 4.1: role chosen once at sign-up, stored in the profile. */
@Composable
fun RoleSelectScreen(
    onPick: (Role) -> Unit,
    error: String? = null,
    onBack: () -> Unit = {},
    darkTheme: Boolean = false,
    onToggleDarkTheme: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = padding.calculateTopPadding() + 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) { Text("< Back") }
                        DarkModeToggle(darkTheme = darkTheme, onToggle = onToggleDarkTheme)
                    }
                    Text("Who are you?", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "This can't be changed later without signing out.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    AnimatedVisibility(
                        visible = error != null,
                        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                    ) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
            item {
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(tween(420, easing = FastOutSlowInEasing)) + slideInVertically(tween(420, easing = FastOutSlowInEasing)) { it / 4 }
                ) {
                    RoleCard(
                        icon = Icons.Outlined.CleaningServices,
                        title = "I'm a worker",
                        subtitle = "I clean homes and want to find jobs nearby.",
                        onClick = { onPick(Role.WORKER) }
                    )
                }
            }
            item {
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(tween(420, delayMillis = 90, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(420, delayMillis = 90, easing = FastOutSlowInEasing)) { it / 4 }
                ) {
                    RoleCard(
                        icon = Icons.Outlined.House,
                        title = "I'm an owner",
                        subtitle = "I own a home and want to find a worker.",
                        onClick = { onPick(Role.OWNER) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "roleCardScale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
