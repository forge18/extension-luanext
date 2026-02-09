# Changelog

## v1.0.0 (2026-02-08)

### Added
- Initial Defold extension implementation
- LuaNext transpiler integration (`.luax` → Lua 5.1)
- Language Server Protocol (LSP) support for editor features
- Defold API type definitions for:
  - `go.*` - GameObject manipulation
  - `msg.*` - Messaging APIs
  - `gui.*` - GUI scripting
  - `vmath.*` - Vector math
  - `pp.*` - Particle FX
  - `sound.*` - Sound
  - `physics.*` - Physics
  - `sys.*` - System APIs
  - `url.*` - URL handling
  - `hash.*` - Hashing

### Platforms
- Linux x86_64
- macOS x86_64
- macOS ARM64

### Features
- Static type checking for LuaNext code
- Automatic compilation during Defold builds
- Editor error reporting
- Platform-specific binary distribution
