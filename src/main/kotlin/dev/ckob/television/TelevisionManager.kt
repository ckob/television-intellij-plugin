package dev.ckob.television

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.EnvironmentUtil
import org.jetbrains.plugins.terminal.ShellStartupOptions
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import java.beans.PropertyChangeListener
import java.io.File
import javax.swing.JComponent
import kotlin.concurrent.thread
import kotlin.math.max

object TelevisionManager {
    private val OUTPUT_FILE_KEY = com.intellij.openapi.util.Key.create<File>("television.outputFile")
    private val WATCH_THREAD_KEY = com.intellij.openapi.util.Key.create<Thread>("television.watchThread")
    
    fun toggleTelevision(project: Project, channel: String, initialInput: String? = null, workingDirOverride: String? = null) {
        val editorManager = FileEditorManager.getInstance(project)
        
        // Close existing Television tabs
        val existingFiles = editorManager.openFiles.filterIsInstance<TelevisionVirtualFile>()
        existingFiles.forEach { editorManager.closeFile(it) }

        setupIpc(project, channel)

        val outputFile = project.getUserData(OUTPUT_FILE_KEY)

        val title = "Television - ${channel.replaceFirstChar { it.uppercase() }}"
        val file = TelevisionVirtualFile(title, channel, outputFile!!.absolutePath, workingDirOverride ?: project.basePath, initialInput)
        editorManager.openFile(file, true)
    }
    
    private fun setupIpc(project: Project, channel: String) {
        var outputFile = project.getUserData(OUTPUT_FILE_KEY)
        if (outputFile == null || !outputFile.exists()) {
            val suffix = "${System.currentTimeMillis()}-${ProcessHandle.current().pid()}"
            val tmpDir = System.getProperty("java.io.tmpdir")
            outputFile = File(tmpDir, "television-intellij-output-$suffix.tmp")
            outputFile.createNewFile()
            project.putUserData(OUTPUT_FILE_KEY, outputFile)
        } else {
            outputFile.writeText("")
        }
        
        startIpcWatcher(project, outputFile)
    }
    
    private fun startIpcWatcher(project: Project, file: File) {
        var watchThread = project.getUserData(WATCH_THREAD_KEY)
        watchThread?.interrupt()
        
        watchThread = thread(start = true, isDaemon = true) {
            try {
                var lastModified = file.lastModified()
                while (!Thread.currentThread().isInterrupted && !project.isDisposed) {
                    val currentModified = file.lastModified()
                    if (currentModified > lastModified) {
                        val content = file.readText().trim()
                        if (content.isNotEmpty()) {
                            file.writeText("")
                            lastModified = file.lastModified()
                            handleIpcMessage(project, content)
                        } else {
                            lastModified = currentModified
                        }
                    }
                    Thread.sleep(50)
                }
            } catch (_: InterruptedException) {
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        project.putUserData(WATCH_THREAD_KEY, watchThread)
    }
    
    private fun handleIpcMessage(project: Project, content: String) {
        content.lines().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split(":")
            var filePath = line
            var lineNum = 0
            var colNum = 0
            
            if (parts.size > 1) {
                val lastPart = parts.last()
                val secondLastPart = parts[parts.size - 2]
                
                if (lastPart.toIntOrNull() != null && secondLastPart.toIntOrNull() != null) {
                    // format is file:line:col
                    lineNum = secondLastPart.toInt()
                    colNum = lastPart.toInt()
                    filePath = parts.dropLast(2).joinToString(":")
                } else if (lastPart.toIntOrNull() != null) {
                    // format is file:line
                    lineNum = lastPart.toInt()
                    filePath = parts.dropLast(1).joinToString(":")
                }
            }

            ApplicationManager.getApplication().invokeLater {
                val file = File(if (File(filePath).isAbsolute) filePath else "${project.basePath}/$filePath")
                val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)

                if (virtualFile != null) {
                    val descriptor = OpenFileDescriptor(project, virtualFile, max(0, lineNum - 1), max(0, colNum - 1))
                    FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                    descriptor.navigate(true)
                }
            }
        }
    }
}

class TelevisionVirtualFile(
    val title: String,
    val channel: String,
    val outputFilePath: String,
    val workingDirectory: String?,
    val initialInput: String? = null
) : LightVirtualFile(title) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TelevisionVirtualFile) return false
        return channel == other.channel
    }

    override fun hashCode(): Int {
        return channel.hashCode()
    }
}

class TelevisionEditor(
    private val project: Project,
    private val virtualFile: TelevisionVirtualFile
) : UserDataHolderBase(), FileEditor {

    private val terminalWidget: com.intellij.terminal.ui.TerminalWidget

    init {
        val terminalRunner = TerminalToolWindowManager.getInstance(project).terminalRunner
        val envs = EnvironmentUtil.getEnvironmentMap().toMutableMap()
        
        val customExecutable = TelevisionSettingsState.instance.customExecutablePath
        val tvPath = customExecutable.ifBlank {
            PathEnvironmentVariableUtil.findInPath("tv", envs["PATH"], null)?.absolutePath ?: "tv"
        }
        
        val inputArgs = if (virtualFile.initialInput != null) {
            " --input \"${virtualFile.initialInput.replace("\"", "\\\"")}\""
        } else {
            ""
        }
        
        val tvCommand = "\"$tvPath\" --no-remote --keybindings \"enter=\\\"confirm_selection\\\"\" ${virtualFile.channel}$inputArgs > \"${virtualFile.outputFilePath}\""
        
        val shellCommand = if (SystemInfo.isWindows) {
            val scriptFile = File(System.getProperty("java.io.tmpdir"), "television-run-${System.currentTimeMillis()}.cmd")
            scriptFile.writeText("@echo off\r\n$tvCommand\r\nif %ERRORLEVEL% NEQ 0 pause\r\n")
            listOf("cmd.exe", "/C", scriptFile.absolutePath)
        } else {
            listOf("sh", "-c", "$tvCommand || { echo \"\"; echo \"Process exited with error. Press Enter to close...\"; read dummy; }")
        }
        
        val startupOptions = ShellStartupOptions.Builder()
            .workingDirectory(virtualFile.workingDirectory)
            .shellCommand(shellCommand)
            .envVariables(envs)
            .build()
            
        terminalWidget = terminalRunner.startShellTerminalWidget(this, startupOptions, true)
        
        terminalWidget.addTerminationCallback({
            ApplicationManager.getApplication().invokeLater {
                FileEditorManager.getInstance(project).closeFile(virtualFile)
            }
        }, this)
    }

    override fun getComponent(): JComponent = terminalWidget.component
    override fun getPreferredFocusedComponent(): JComponent = terminalWidget.preferredFocusableComponent
    override fun getName(): String = virtualFile.title
    override fun getFile(): VirtualFile = virtualFile
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun dispose() {}
    override fun setState(state: FileEditorState) {}
}

class TelevisionEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is TelevisionVirtualFile
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return TelevisionEditor(project, file as TelevisionVirtualFile)
    }

    override fun getEditorTypeId(): String = "TelevisionEditor"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
