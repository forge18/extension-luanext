# LuaNext Defold Extension - TODO

## Overview

This project implements a Defold extension that integrates the LuaNext transpiler and language server with the Defold game engine.

## Project Structure

```text
luanext-defold-extension/
├── src/main/java/com/defold/extension/pipeline/
│   └── LuaNextTranspiler.java              # ILuaTranspiler implementation
├── build.gradle                            # Gradle build config
├── build_plugin.sh                         # Compiles JAR
├── make-binaries.sh                        # Builds Rust binaries
├── ext.manifest                            # Extension metadata
├── luanext.editor_script                   # LSP registration
├── README.md
├── luanext/                                # Distributed extension package
│   ├── ext.manifest
│   ├── luanext.editor_script
│   ├── plugins/
│   │   ├── share/
│   │   │   └── extension-luanext.jar       # Compiled Java plugin
│   │   └── ${platform}.zip                 # Per-platform compiler binaries
│   │       └── bin/
│   │           ├── luanext-compiler       # Main transpiler
│   │           └── luanext-lsp            # Language server (optional)
│   ├── stdlib/                             # Defold API type definitions
│   │   ├── go.d.luax
│   │   ├── msg.d.luax
│   │   ├── gui.d.luax
│   │   └── ...
│   └── README.md
└── docs/
    └── TODO.md                             # This file
```

---

## Phase 1: Core Java Implementation

- [x] Create `LuaNextTranspiler.java` implementing `ILuaTranspiler` interface
- [x] Implement `getBuildFileResourcePath()` returning `"/.luanextrc"`
- [x] Implement `getSourceExt()` returning `"luax"`
- [x] Implement `transpile()` method with ProcessBuilder
- [x] Add platform detection (Platform enum)
- [x] Implement error parsing (pretty and simple formats)
- [ ] Add tests for transpiler methods

---

## Phase 2: Build Configuration

- [x] Create `build.gradle` with Java plugin
- [x] Add Defold bob-java dependency
- [x] Configure Java 11 source/target compatibility
- [x] Create `build_plugin.sh` script
- [x] Create `make-binaries.sh` script for cross-platform builds

---

## Phase 3: Rust Binary Builds

- [x] Build `luanext-compiler` for x86_64-linux
- [x] Build `luanext-compiler` for x86_64-macos
- [x] Build `luanext-compiler` for arm64-macos
- [ ] Build `luanext-compiler` for x86_64-win32 (cross-compile issue)
- [x] Build `luanext-lsp` for all platforms
- [x] Package binaries into platform-specific zip files
- [x] Set executable permissions on Linux/macOS binaries

---

## Phase 4: LSP Integration

- [x] Create `luanext.editor_script`
- [x] Implement `get_language_servers()` function
- [x] Add platform-specific binary path detection
- [x] Handle Windows .exe extension

---

## Phase 5: Defold API Type Definitions

- [x] Generate `go.d.luax` - GameObject APIs
- [x] Generate `msg.d.luax` - Messaging APIs
- [x] Generate `gui.d.luax` - GUI APIs
- [x] Generate `vmath.d.luax` - Vector math
- [x] Generate `pp.d.luax` - Particle FX
- [x] Generate `sound.d.luax` - Sound
- [x] Generate `physics.d.luax` - Physics
- [x] Generate `sys.d.luax` - System
- [x] Generate `url.d.luax` - URLs
- [x] Generate `hash.d.luax` - Hashing

---

## Phase 6: Metadata and Documentation

- [x] Create `ext.manifest`
- [x] Create `luanext.config.yaml`
- [x] Create main `README.md`
- [x] Update main README with installation and quick start guide
- [x] Add feature list (type checking, LSP, etc.)
- [x] Add troubleshooting section

---

## Phase 7: Testing

### Unit Tests

- [x] Test `getSourceExt()` returns "luax"
- [x] Test `getBuildFileResourcePath()` returns "/.luanextrc"
- [x] Test error parsing for pretty format
- [x] Test error parsing for simple format
- [x] Test platform detection
- [x] Run unit tests (14 tests pass)

---

## Phase 8: Build and Release

### Build Checklist

- [x] Build all platform binaries (Linux x86_64, macOS x86_64/arm64, Windows x86_64)
- [x] Compile Java plugin to `luanext/plugins/share/extension-luanext.jar`
- [x] Copy all type definitions to `luanext/stdlib/`
- [x] Verify `ext.manifest` and `luanext.editor_script` are present
- [x] Ensure README.md is complete
- [x] Run all unit tests (14 tests pass)

### Release Checklist

- [x] Update version number in `build.gradle` and `ext.manifest`
- [x] Update changelog
- [x] Create git tag: `git tag -a v1.0.0 -m "Release v1.0.0"`
- [x] Create GitHub release with `luanext/` directory as zip
- [ ] Submit to Defold Library

---

## Architecture Reference

### Technology Stack

| Component            | Technology | Description                                    |
|----------------------|------------|------------------------------------------------|
| Extension Format     | Defold     | Standard Defold extension structure            |
| Build System         | Gradle     | Java compilation & packaging                   |
| Transpiler Interface | Java       | `com.defold.extension.pipeline.ILuaTranspiler` |
| Compiler             | Rust       | Existing `luanext-cli` crate                   |
| Language Server      | Rust       | Existing `luanext-lsp` crate                   |

### Platform Matrix

| Platform       | Target Triple            | Defold Name  | Binary Name          |
|----------------|--------------------------|--------------|----------------------|
| Linux x86_64   | x86_64-unknown-linux-gnu | x86_64-linux | luanext-compiler     |
| macOS x86_64   | x86_64-apple-darwin      | x86_64-macos | luanext-compiler     |
| macOS ARM64    | aarch64-apple-darwin     | arm64-macos  | luanext-compiler     |
| Windows x86_64 | x86_64-pc-windows-msvc   | x86_64-win32 | luanext-compiler.exe |

---

## References

### Defold Resources

- [Defold Extensions Manual](https://www.defold.com/manuals/extensions/)
- [Defold Editor Scripts Manual](https://www.defold.com/manuals/editor-scripts/)
- [Defold Bob CLI](https://www.defold.com/manuals/bob/)
- [Defold API Reference](https://www.defold.com/ref/stable/)
- [Extension-Teal (Reference Implementation)](https://github.com/defold/extension-teal)

### LuaNext Resources

- [LuaNext Compiler](https://github.com/forge18/luanext)
- [LuaNext LSP](https://github.com/forge18/luanext/tree/main/crates/luanext-lsp)
- [LuaNext Type System](https://github.com/forge18/luanext-parser)

---

## Project Status Summary

### Completed ✓
- Core Java implementation (LuaNextTranspiler.java)
- Build configuration (build.gradle, build_plugin.sh, build-binaries.sh)
- Rust binary builds for all 4 platforms (Linux x86_64, macOS x86_64/arm64, Windows x86_64)
- LSP integration (luanext.editor_script)
- All 10 Defold API type definitions
- Metadata and documentation
- Unit tests (14/14 passing)
- GitHub release v1.0.0 published

### Pending
- Integration tests (requires Defold editor + Java)
- Submit to Defold Library
