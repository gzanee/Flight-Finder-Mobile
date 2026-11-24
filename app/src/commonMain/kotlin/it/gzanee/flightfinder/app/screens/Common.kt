package it.gzanee.flightfinder.app.screens

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import it.gzanee.flightfinder.app.ui.colors

@Composable
fun Screen(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    val c = colors
    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Text("←", color = c.primary, fontSize = 22.sp) }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.card, titleContentColor = c.text),
            )
        },
        content = content,
    )
}
