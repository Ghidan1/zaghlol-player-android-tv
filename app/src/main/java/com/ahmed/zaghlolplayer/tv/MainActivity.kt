package com.ahmed.zaghlolplayer.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ahmed.zaghlolplayer.tv.ui.LoginScreen
import com.ahmed.zaghlolplayer.tv.ui.MainScreen
import com.ahmed.zaghlolplayer.tv.ui.ZaghlolTvTheme

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val pickM3u = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loginM3uFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            ZaghlolTvTheme {
                val state by vm.state.collectAsState()
                BackHandler(
                    enabled = state.movieDialog != null || state.serieDialog != null ||
                        state.focusMode || (state.screen == Screen.MAIN && state.tab != AppTab.LIVE)
                ) {
                    when {
                        state.movieDialog != null || state.serieDialog != null -> vm.dismissDialogs()
                        state.focusMode -> vm.setFocusMode(false)
                        state.screen == Screen.MAIN && state.tab != AppTab.LIVE -> vm.setTab(AppTab.LIVE)
                    }
                }
                when (state.screen) {
                    Screen.LOGIN -> LoginScreen(vm) { pickM3u.launch(arrayOf("*/*")) }
                    Screen.MAIN -> MainScreen(vm)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        vm.saveCurrentResume()
    }
}
