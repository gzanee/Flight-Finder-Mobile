package it.gzanee.flightfinder.ui.preview

import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Light Mode",
    device = "spec:width=1080px,height=2160px,dpi=480",
    showBackground = true,
)
@Preview(
    name = "Dark Mode",
    device = "spec:width=1080px,height=2160px,dpi=480",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
annotation class DevicePreview
