package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AnyTaskDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.toDomain
import com.example.domain.model.AppThemeMode
import com.example.domain.model.TaskList
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.theme.AnyTaskTheme

class AnyTaskWidgetConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val prefs = PreferencesManager(this)
        val db = AnyTaskDatabase.getInstance(this)

        setContent {
            var lists by remember { mutableStateOf<List<TaskList>>(emptyList()) }
            var selectedListId by remember { mutableStateOf(prefs.getWidgetListId(appWidgetId)) }
            var darkGlass by remember { mutableStateOf(prefs.isWidgetDarkGlass(appWidgetId)) }

            LaunchedEffect(Unit) {
                AnyTaskDatabase.ensureDefaultListExists(db)
                lists = db.taskListDao().getActiveListsOnce().map { it.toDomain() }
            }

            AnyTaskTheme(themeMode = if (darkGlass) AppThemeMode.DARK else AppThemeMode.LIGHT) {
                LiquidGlassBackground(isDark = darkGlass) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .padding(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Customize Liquid Glass Widget",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Dark Glass Surface",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = if (darkGlass) "Deep frosted slate" else "Luminous ice frost",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = darkGlass,
                                        onCheckedChange = { darkGlass = it },
                                        modifier = Modifier.testTag("widget_dark_glass_switch")
                                    )
                                }
                            }

                            Text(
                                text = "Task List to Display",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LiquidGlassSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                LazyColumn(modifier = Modifier.padding(8.dp)) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedListId = null }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = selectedListId == null,
                                                onClick = { selectedListId = null }
                                            )
                                            Text(
                                                text = "All Task Lists",
                                                modifier = Modifier.padding(start = 8.dp),
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                    items(lists, key = { it.id }) { list ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedListId = list.id }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = selectedListId == list.id,
                                                onClick = { selectedListId = list.id }
                                            )
                                            Text(
                                                text = list.name,
                                                modifier = Modifier.padding(start = 8.dp),
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = {
                                    prefs.saveWidgetConfig(appWidgetId, selectedListId, darkGlass)
                                    val manager = AppWidgetManager.getInstance(this@AnyTaskWidgetConfigureActivity)
                                    AnyTaskWidgetProvider.updateWidget(
                                        this@AnyTaskWidgetConfigureActivity,
                                        manager,
                                        appWidgetId
                                    )
                                    @Suppress("DEPRECATION")
                                    manager.notifyAppWidgetViewDataChanged(appWidgetId, com.example.R.id.widget_task_list)
                                    val resultValue = Intent().putExtra(
                                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                                        appWidgetId
                                    )
                                    setResult(RESULT_OK, resultValue)
                                    finish()
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("save_widget_button")
                            ) {
                                Text(
                                    text = "Add Glass Widget",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
