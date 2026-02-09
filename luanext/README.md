# LuaNext for Defold

Typed LuaNext language support for Defold game engine.

## Installation

1. Open your Defold project
2. Go to **Assets → Library**
3. Add dependency: `https://github.com/forge18/extension-luanext.git`
4. Click **Fetch Assets**

## Quick Start

1. Create a `.luanextrc` configuration file in your project root:

```yaml
compilerOptions:
  target: "5.1"
  strict: true

include:
  - "**/*.luax"
```

2. Create a `.luax` file (e.g., `main/scripts/game.luax`):

```luanext
local velocity: vmath.vector3 = vmath.vector3()

function init(self: typeof(self))
    print("Game initialized")
end

function update(self: typeof(self), dt: number)
    velocity.y = velocity.y - 9.81 * dt
    go.set_position(go.get(self.id), velocity)
end
```

3. Use it from your regular Lua script:

```lua
local game = require("main.scripts.game")

function init(self)
    game.init(self)
end

function update(self, dt)
    game.update(self, dt)
end
```

## Features

- Static type checking
- Editor error reporting
- Language Server Protocol (autocomplete, hover, go-to-definition)
- Defold API type definitions included
- Automatic compilation to Lua 5.1
- Cross-platform support

## Type Definitions

The extension includes type definitions for all Defold APIs:

- `go.*` - GameObject manipulation
- `msg.*` - Message passing
- `gui.*` - GUI scripting
- `vmath.*` - Vector math
- `sys.*` - System APIs
- And many more...

## Build Integration

- **Editor Builds**: Automatic when you save files
- **CLI Builds (bob)**: Automatic when building via Defold command-line tools

## Troubleshooting

### "No .luanextrc configuration file found"

Create a `.luanextrc` file in your project root with valid YAML configuration.

### Type definitions not found

Make sure the extension is properly installed. Type definitions are included in `stdlib/` directory.

### LSP not working

Restart the Defold editor after installing the extension. The LSP is loaded via `luanext.editor_script`.

## License

MIT License - See LICENSE file for details

## Links

- [LuaNext Documentation](https://luanext.dev)
- [Defold Manual](https://www.defold.com/manuals/)
- [Defold Forum](https://forum.defold.com/)
