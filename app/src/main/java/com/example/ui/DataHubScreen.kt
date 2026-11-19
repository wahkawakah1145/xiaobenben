package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.BudgetSettings
import com.example.viewmodel.LedgerViewModel

enum class DataHubTab(val label: String) {
    IMPORT("手动导入"),
    LOCAL_BACKUP("本地备份"),
    WEBDAV("WebDAV")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataHubScreen(
    settings: BudgetSettings,
    viewModel: LedgerViewModel,
    initialTab: DataHubTab = DataHubTab.IMPORT,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    LaunchedEffect(initialTab) {
        if (initialTab == DataHubTab.LOCAL_BACKUP) {
            selectedTab = DataHubTab.LOCAL_BACKUP
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DataHubTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DataHubTab.entries.size
                        ),
                        modifier = Modifier.testTag("data_hub_tab_${tab.name}")
                    ) {
                        Text(tab.label)
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                DataHubTab.IMPORT -> ManualImportScreen(
                    viewModel = viewModel,
                    onShowMessage = onShowMessage
                )
                DataHubTab.LOCAL_BACKUP -> LocalBackupScreen(
                    settings = settings,
                    viewModel = viewModel,
                    onShowMessage = onShowMessage
                )
                DataHubTab.WEBDAV -> WebDavSettingsScreen(
                    viewModel = viewModel,
                    onShowMessage = onShowMessage
                )
            }
        }
    }
}
