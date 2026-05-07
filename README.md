# Television for IntelliJ

<!-- Plugin description -->
Bring the blazing speed and elegance of **Television** directly into your JetBrains IDE. 

This plugin integrates [Television](https://github.com/alexpasmantier/television) seamlessly into IntelliJ-based IDEs. It provides a fast, rusty, and highly extensible fuzzy finder TUI—acting as a powerful, keyboard-centric alternative to Telescope or the native "Search Everywhere" dialog.

### Key Features
- **Default Channels:** Instantly search through your workspace files with the `files` channel, or search through file contents using the `text` (grep) channel.
- **Custom Dynamic Channels:** Define your own `tv` channels right from the IDE settings. Want a dedicated channel for `git-files`, `todo-comments`, or `buffers`? You can easily map them.
- **Context-Aware Execution:** Execute your searches globally against the **Project Root**, or narrowly against the **Current File's Directory** (CWD) for scoped finding.
- **Editor Selection Passing:** Optionally pass your current active text selection in the editor directly to Television to use as the initial search input.
- **Immersive TUI:** Television opens instantly in a dedicated, full-screen editor tab, keeping you in the flow without floating popups blocking your code.
- **IdeaVim Ready:** First-class support for IdeaVim. Map your favorite channels directly in your `.ideavimrc` (e.g., `nmap <leader>ff <Action>(Television.Channel.Files)`).
- **Smart IDE Navigation:** Selecting a file or text match in Television instantly closes the TUI and opens the target file right in your IDE's editor, automatically jumping to the exact line and column.

### Why Television?
Television is written in Rust, blazingly fast, and designed to be customized. If you love the workflow of Neovim (nvim) using Telescope but need the power of a full IDE, this plugin brings the best of both worlds: the speed and keyboard-driven ergonomics of a terminal fuzzy finder, combined with IntelliJ's robust code navigation.

### Requirements
This plugin requires the [television](https://github.com/alexpasmantier/television) CLI to be installed and available on your system PATH.

For example, on macOS with Homebrew:
```bash
brew install television
```

Once installed, ensure the default channels are available:
```bash
tv update-channels
```
<!-- Plugin description end -->

## Installation
- Install the plugin from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/31448-television).
- Alternatively, search for **"Television"** (by ckob) directly in your IDE via `Settings` -> `Plugins` -> `Marketplace`.

## Usage
- Search for the actions `Television: Files` and `Television: Text` in the IDE (Double Shift or `Cmd+Shift+A`).
- Head to `Settings -> Tools -> Television` to map new channels, configure their Working Directory, or pass the active editor selection to the command.
- You can assign custom keyboard shortcuts to your configured channels in `Settings -> Keymap`.

### IdeaVim Configuration
If you use IdeaVim, you can map the toggle actions directly in your `.ideavimrc` file. 

For the default channels:
```vim
" Map <leader>ff to open Television file finder
nmap <leader>ff <Action>(Television.Channel.Files)

" Map <leader>fs to open Television text finder (grep)
nmap <leader>fs <Action>(Television.Channel.Text)
```

**Custom Channels:**
Custom channels created in the Settings menu generate predictable Action IDs based on their Title (converted to PascalCase with spaces removed). 
For example, if you create a custom channel titled **"Git Status"**, its Action ID will be `Television.Channel.GitStatus`.

```vim
" Map <leader>fg to your custom 'Git Status' channel
nmap <leader>fg <Action>(Television.Channel.GitStatus)
```
*(Tip: You can also use the `:trackaction` command in IdeaVim to verify the exact ID of any action when you trigger it).*

## How it works
This plugin runs Television in a terminal widget using the `--no-remote` flag and redirects the standard output to an IPC file. It then parses the file path and line number to open the selection directly in the IDE.