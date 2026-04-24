package dev.ckob.television

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.CollectionListModel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.util.UUID
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

enum class WorkingDirectoryMode(val displayName: String) {
    PROJECT_ROOT("Project Root"),
    CURRENT_FILE("Current File Dir");

    override fun toString(): String = displayName
}

class TvChannelConfig {
    var id: String = UUID.randomUUID().toString()
    var name: String = ""
    var channelCmd: String = ""
    var passSelection: Boolean = false
    var workingDirMode: WorkingDirectoryMode = WorkingDirectoryMode.PROJECT_ROOT
    
    fun copy() = TvChannelConfig().also {
        it.id = this.id
        it.name = this.name
        it.channelCmd = this.channelCmd
        it.passSelection = this.passSelection
        it.workingDirMode = this.workingDirMode
    }
    
    override fun toString(): String = name.ifEmpty { "Unnamed Action" }
}

@State(
    name = "dev.ckob.television.TelevisionSettingsState",
    storages = [Storage("TelevisionPlugin.xml")]
)
class TelevisionSettingsState : PersistentStateComponent<TelevisionSettingsState> {
    var customExecutablePath: String = ""
    var channels: MutableList<TvChannelConfig> = mutableListOf(
        TvChannelConfig().apply { name = "Files"; channelCmd = "files"; passSelection = false; workingDirMode = WorkingDirectoryMode.PROJECT_ROOT },
        TvChannelConfig().apply { name = "Text"; channelCmd = "text"; passSelection = true; workingDirMode = WorkingDirectoryMode.PROJECT_ROOT }
    )

    override fun getState(): TelevisionSettingsState = this
    override fun loadState(state: TelevisionSettingsState) {
        this.customExecutablePath = state.customExecutablePath
        if (state.channels.isNotEmpty()) {
            this.channels = state.channels
        }
    }

    companion object {
        val instance: TelevisionSettingsState
            get() = ApplicationManager.getApplication().getService(TelevisionSettingsState::class.java)
    }
}

class TelevisionSettingsConfigurable : Configurable {
    private var mainPanel: JPanel? = null
    private val state = TelevisionSettingsState.instance
    
    private val listModel = CollectionListModel<TvChannelConfig>()
    private val jbList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    
    private var detailPanel: DialogPanel? = null
    private var currentSelection: TvChannelConfig? = null
    private var executablePathTextField = TextFieldWithBrowseButton()

    override fun createComponent(): JComponent {
        executablePathTextField.addBrowseFolderListener(
            com.intellij.openapi.ui.TextBrowseFolderListener(
                FileChooserDescriptor(true, false, false, false, false, false).withTitle("Select Television Executable"),
                null
            )
        )

        detailPanel = panel {
            row("Action Name:") {
                textField()
                    .bindText(
                        { currentSelection?.name ?: "" },
                        { currentSelection?.name = it; jbList.repaint() }
                    )
                    .align(AlignX.FILL)
            }
            row("Channel Command:") {
                textField()
                    .bindText(
                        { currentSelection?.channelCmd ?: "" },
                        { currentSelection?.channelCmd = it }
                    )
                    .align(AlignX.FILL)
            }
            row {
                checkBox("Pass Editor Selection")
                    .bindSelected(
                        { currentSelection?.passSelection ?: false },
                        { currentSelection?.passSelection = it }
                    )
            }
            row("Working Directory:") {
                comboBox(WorkingDirectoryMode.entries.toList())
                    .bindItem(
                        { currentSelection?.workingDirMode ?: WorkingDirectoryMode.PROJECT_ROOT },
                        { if (it != null) currentSelection?.workingDirMode = it }
                    )
            }
        }.apply {
            isEnabled = false
        }

        jbList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                detailPanel?.apply() // apply pending changes to old selection
                currentSelection = jbList.selectedValue
                detailPanel?.reset() // load values from new selection
                detailPanel?.isEnabled = currentSelection != null
            }
        }

        val toolbarDecorator = ToolbarDecorator.createDecorator(jbList)
            .setAddAction {
                val newConfig = TvChannelConfig().apply { name = "New Action"; channelCmd = "channel" }
                listModel.add(newConfig)
                jbList.selectedIndex = listModel.size - 1
            }
            .setRemoveAction {
                val selectedIndex = jbList.selectedIndex
                if (selectedIndex != -1) {
                    listModel.remove(selectedIndex)
                }
            }
            .createPanel()

        val splitter = OnePixelSplitter(false, 0.3f).apply {
            firstComponent = toolbarDecorator
            secondComponent = detailPanel
        }

        val settingsPanel = panel {
            row("Television executable path:") {
                cell(executablePathTextField)
                    .align(AlignX.FILL)
                    .comment("Optional: Absolute path to the tv executable. If left empty, the plugin searches your system PATH.")
            }
            row {
                button("Restore Defaults") {
                    listModel.removeAll()
                    listModel.add(TvChannelConfig().apply { name = "Files"; channelCmd = "files"; passSelection = false; workingDirMode = WorkingDirectoryMode.PROJECT_ROOT })
                    listModel.add(TvChannelConfig().apply { name = "Text"; channelCmd = "text"; passSelection = true; workingDirMode = WorkingDirectoryMode.PROJECT_ROOT })
                }
            }
            row {
                comment("Define custom actions here. These will appear in the IDE Action search and can be mapped to keyboard shortcuts. Action names will be automatically prefixed with 'Television: '.")
            }
        }

        mainPanel = JPanel(BorderLayout()).apply {
            add(settingsPanel, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
        }
        
        reset()
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        if (executablePathTextField.text != state.customExecutablePath) return true
        
        detailPanel?.apply() // Make sure current input is applied to the object before comparing
        
        val currentChannels = listModel.items
        if (currentChannels.size != state.channels.size) return true
        
        for (i in currentChannels.indices) {
            if (currentChannels[i].name != state.channels[i].name ||
                currentChannels[i].channelCmd != state.channels[i].channelCmd ||
                currentChannels[i].passSelection != state.channels[i].passSelection ||
                currentChannels[i].workingDirMode != state.channels[i].workingDirMode) {
                return true
            }
        }
        return false
    }

    override fun apply() {
        detailPanel?.apply()
        state.customExecutablePath = executablePathTextField.text
        state.channels = listModel.items.map { it.copy() }.toMutableList()
        TelevisionActionRegistrar.registerActions()
    }

    override fun reset() {
        executablePathTextField.text = state.customExecutablePath
        listModel.removeAll()
        listModel.add(state.channels.map { it.copy() })
        if (listModel.size > 0) jbList.selectedIndex = 0
    }

    override fun getDisplayName(): String = "Television"
}
