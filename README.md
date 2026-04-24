# Television for IntelliJ

<!-- Plugin description -->
Bring the speed and elegance of **Television** directly into your JetBrains IDE. 

This plugin integrates [Television](https://github.com/alexpasmantier/television) directly in IntelliJ IDEs, providing a fast and extensible fuzzy finder TUI as an alternative to Telescope.

### Features
- **Television Files:** Quickly search through your workspace files using the `files` channel.
- **Television Text (grep):** Search through text in your workspace using the `text` channel.
- **Dynamic Channels:** Define custom `tv` channels (like `git-files` or `todo-comments`) right from the IDE settings.
- **Smart Working Directories:** Execute your channels against the Project Root or the Current File Directory (CWD).
- **Immersive UI:** Toggles Television in a dedicated, full-screen editor tab.
- **Smart Navigation:** Selecting a file instantly opens it right in your IDE's editor at the specified line and column.

<!-- Plugin description end -->

## Installation
- Install the plugin from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/XXX-television).
- Alternatively, search for **"Television"** (by ckob) directly in your IDE via `Settings` -> `Plugins` -> `Marketplace`.

## Usage
- Search for the actions `Television: Files` and `Television: Text` in the IDE (Double Shift or `Cmd+Shift+A`).
- Head to `Settings -> Tools -> Television` to map new channels, configure their Working Directory, or pass the active editor selection to the command.
- You can assign custom keyboard shortcuts to your configured channels in `Settings -> Keymap`.

### IdeaVim Configuration
If you use IdeaVim, you can map the toggle actions in your `.ideavimrc` file. For the default channels:

```vim
" Map <leader>ff to open Television file finder
nmap <leader>ff <Action>(Television.Channel.Files)

" Map <leader>fs to open Television text finder (grep)
nmap <leader>fs <Action>(Television.Channel.Text)
```

*(Note: Custom channels created in Settings will have dynamically generated IDs. You can find these IDs by configuring a shortcut in the IDE Keymap and inspecting your keymap XML, or by using the `trackaction` feature in IdeaVim).*

## Requirements
The extension requires [television](https://github.com/alexpasmantier/television) to be installed and available on your PATH.

For example, on macOS with Homebrew:
```bash
brew install television
```

Once installed, ensure the `files` and `text` channels are available:
```bash
tv update-channels
```

## How it works
This plugin runs Television in a terminal widget using the `--no-remote` flag and redirects the standard output to an IPC file. It then parses the file path and line number to open the selection directly in the IDE.
