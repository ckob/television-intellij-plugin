package dev.ckob.television

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class TelevisionDynamicAction(private val config: TvChannelConfig) : AnAction("Television: ${config.name}") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        var input: String? = null
        if (config.passSelection) {
            val editor = e.getData(CommonDataKeys.EDITOR)
            input = editor?.selectionModel?.selectedText?.takeIf { it.isNotEmpty() }
        }
        
        var workingDirOverride: String? = null
        if (config.workingDirMode == WorkingDirectoryMode.CURRENT_FILE) {
            val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            workingDirOverride = virtualFile?.let { if (it.isDirectory) it.path else it.parent?.path }
        }
        
        TelevisionManager.toggleTelevision(project, config.channelCmd, input, workingDirOverride)
    }
}

object TelevisionActionRegistrar {
    val registeredActionIds = mutableListOf<String>()

    fun registerActions() {
        val actionManager = ActionManager.getInstance()
        val toolsGroup = actionManager.getAction("ToolsMenu") as? DefaultActionGroup

        // Unregister previously dynamically registered actions
        registeredActionIds.forEach { id ->
            val action = actionManager.getAction(id)
            if (action != null && toolsGroup != null) {
                toolsGroup.remove(action)
            }
            actionManager.unregisterAction(id)
        }
        registeredActionIds.clear()

        // Register new actions from settings
        val state = TelevisionSettingsState.instance
        val usedIds = mutableSetOf<String>()
        
        state.channels.forEach { config ->
            var safeName = config.name.replace(Regex("[^a-zA-Z0-9]"), "-").replace(Regex("-+"), "-").trim('-')
            if (safeName.isEmpty()) safeName = "CustomAction"
            var baseId = safeName
            var counter = 1
            while (usedIds.contains(baseId)) {
                baseId = "$safeName-$counter"
                counter++
            }
            usedIds.add(baseId)
            config.id = baseId
            
            val actionId = "Television.Channel.${config.id}"
            val action = TelevisionDynamicAction(config)
            
            actionManager.registerAction(actionId, action)
            toolsGroup?.add(action)
            registeredActionIds.add(actionId)
        }
    }
}

class TelevisionStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            if (TelevisionActionRegistrar.registeredActionIds.isEmpty()) {
                TelevisionActionRegistrar.registerActions()
            }
        }
    }
}
