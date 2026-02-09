# LuaNext Defold Extension Implementation Guide

## Executive Summary

This document provides complete technical specifications for implementing a Defold extension that integrates the LuaNext transpiler and language server with the Defold game engine. The extension enables developers to write typed LuaNext code (`.luax` files) that automatically compile to Lua 5.1 compatible with Defold's runtime.

**Key Components:**
1. **Java Transpiler Wrapper** (~200 LOC) - Implements `ILuaTranspiler` interface
2. **Cross-Platform Binaries** - Pre-compiled Rust executables for all Defold platforms
3. **LSP Integration** - Optional language server for editor features
4. **Defold API Type Definitions** - `.d.luax` files for complete type safety

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Defold Transpiler System](#defold-transpiler-system)
3. [File Structure](#file-structure)
4. [Java Implementation](#java-implementation)
5. [Rust Binary Builds](#rust-binary-builds)
6. [LSP Integration](#lsp-integration)
7. [Defold API Type Definitions](#defold-api-type-definitions)
8. [Build Automation](#build-automation)
9. [Testing Strategy](#testing-strategy)
10. [Documentation](#documentation)

---

## Architecture Overview

### High-Level Flow

```
Defold Editor/CLI
       │
       ├── Bob Build System
       │      │
       │      └── pluginDir/ILuaTranspiler.scan()
       │             │
       │             └── LuaNextTranspiler.transpile()
       │                    │
       │                    └── ProcessBuilder → luanext-compiler
       │                           │
       │                           └── Read stdout → Parse errors
       │                                  │
       │                                  └── Return List<Issue>
       │
       └── Editor Script Hooks
              │
              └── get_language_servers()
                     │
                     └── luanext-lsp (stdio)
```

### Technology Stack

| Component | Technology | Description |
|-----------|------------|-------------|
| Transpiler Interface | Java | `com.defold.extension.pipeline.ILuaTranspiler` |
| Compiler | Rust | Existing `luanext-cli` crate |
| Language Server | Rust | Existing `luanext-lsp` crate |
| Build System | Gradle | Java compilation & packaging |
| Extension Format | Defold | Standard Defold extension structure |

---

## Defold Transpiler System

### The ILuaTranspiler Interface

**Location:** `com.defold.extension.pipeline.ILuaTranspiler` (built into Defold)

**Three Required Methods:**

```java
public interface ILuaTranspiler {
    /**
     * Returns the project-relative path to the configuration file
     * @return Path starting with "/" (e.g., "/tlconfig.lua", "/.luanextrc")
     */
    String getBuildFileResourcePath();

    /**
     * Returns the file extension without dot
     * @return Extension string (e.g., "tl", "ts", "luax")
     */
    String getSourceExt();

    /**
     * Transpiles source files to Lua
     * @param pluginDir Directory containing unpacked plugins and binaries
     * @param sourceDir Directory containing all source files + build config
     * @param outputDir Directory where .lua files should be written
     * @return List of issues (errors/warnings/info)
     */
    List<Issue> transpile(File pluginDir, File sourceDir, File outputDir);
}
```

### The Issue Class

```java
public final class Issue {
    public final Severity severity;      // INFO, WARNING, ERROR
    public final String resourcePath;    // Project-relative, starts with "/"
    public final int lineNumber;         // 1-indexed
    public final String message;

    public enum Severity {
        INFO, WARNING, ERROR
    }
}
```

### Platform Enumeration

```java
public enum Platform {
    X86_64_LINUX("x86_64-linux", "linux", "amd64"),
    X86_64_MACOS("x86_64-macos", "darwin", "amd64"),
    ARM64_MACOS("arm64-macos", "darwin", "arm64"),
    X86_64_WIN32("x86_64-win32", "windows", "amd64");

    private final String pair;
    private final String os;
    private final String arch;

    public String getPair() { return pair; }
    public static Platform getHostPlatform() { /* implementation */ }
}
```

---

## File Structure

### Repository Layout

```
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
    ├── implementation-guide.md            # This file
    └── api-specification.md                # Defold API documentation
```

---

## Java Implementation

### 1. LuaNextTranspiler.java

**File:** `src/main/java/com/defold/extension/pipeline/LuaNextTranspiler.java`

```java
package com.defold.extension.pipeline;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class LuaNextTranspiler implements ILuaTranspiler {

    @Override
    public String getBuildFileResourcePath() {
        return "/.luanextrc";
    }

    @Override
    public String getSourceExt() {
        return "luax";
    }

    @Override
    public List<Issue> transpile(File pluginDir, File sourceDir, File outputDir) {
        List<Issue> issues = new ArrayList<>();

        try {
            // Find compiler binary for current platform
            Platform platform = Platform.getHostPlatform();
            String binaryPath = pluginDir.getAbsolutePath() + "/plugins/bin/"
                    + platform.getPair() + "/bin/luanext-compiler";
            if (platform == Platform.X86_64_WIN32) {
                binaryPath += ".exe";
            }

            // Check if build config exists
            File buildConfig = new File(sourceDir, getBuildFileResourcePath().substring(1));
            if (!buildConfig.exists()) {
                issues.add(new Issue(
                    Issue.Severity.WARNING,
                    getBuildFileResourcePath(),
                    1,
                    "No .luanextrc configuration file found. Using compiler defaults."
                ));
            }

            // Build command arguments
            List<String> command = new ArrayList<>();
            command.add(binaryPath);
            command.add("compile");
            command.add("--target=5.1");
            command.add("--config=" + buildConfig.getAbsolutePath());
            command.add("--output-dir=" + outputDir.getAbsolutePath());

            // Add all source files
            File[] sourceFiles = sourceDir.listFiles((dir, name) ->
                name.endsWith("." + getSourceExt()));

            if (sourceFiles != null) {
                for (File file : sourceFiles) {
                    command.add(file.getAbsolutePath());
                }
            }

            // Execute compiler
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(sourceDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            // Parse errors from output
            issues.addAll(parseErrors(output.toString(), sourceDir));

        } catch (Exception e) {
            issues.add(new Issue(
                Issue.Severity.ERROR,
                getBuildFileResourcePath(),
                1,
                "Failed to run LuaNext compiler: " + e.getMessage()
            ));
        }

        return issues;
    }

    private List<Issue> parseErrors(String output, File sourceDir) {
        List<Issue> issues = new ArrayList<>();

        // Pretty format pattern: error [/path/file:15:8]: message [code]
        Pattern prettyPattern = Pattern.compile(
            "(error|warning|info) \\[(.+):(\\d+):(\\d+)\\]:\\s*(.*?)(?:\\s*\\[(\\w+)\\])?$"
        );

        // Simple format pattern: /path/file:15:8: error: message [code]
        Pattern simplePattern = Pattern.compile(
            "(.+):(\\d+):(\\d+):\\s*(error|warning|info):\\s*(.*?)(?:\\s*\\[(\\w+)\\])?$"
        );

        String[] lines = output.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Try pretty format first
            Matcher matcher = prettyPattern.matcher(line);
            if (matcher.matches()) {
                Issue.Severity severity = parseSeverity(matcher.group(1));
                String filePath = matcher.group(2);
                int lineNum = Integer.parseInt(matcher.group(3));
                int colNum = Integer.parseInt(matcher.group(4));
                String message = matcher.group(5).trim();
                // Code is in group(6) if present

                String resourcePath = toResourcePath(filePath, sourceDir);
                if (resourcePath != null) {
                    issues.add(new Issue(severity, resourcePath, lineNum, message));
                }
                continue;
            }

            // Try simple format
            matcher = simplePattern.matcher(line);
            if (matcher.matches()) {
                String filePath = matcher.group(1);
                int lineNum = Integer.parseInt(matcher.group(2));
                int colNum = Integer.parseInt(matcher.group(3));
                Issue.Severity severity = parseSeverity(matcher.group(4));
                String message = matcher.group(5).trim();

                String resourcePath = toResourcePath(filePath, sourceDir);
                if (resourcePath != null) {
                    issues.add(new Issue(severity, resourcePath, lineNum, message));
                }
            }
        }

        return issues;
    }

    private Issue.Severity parseSeverity(String severity) {
        switch (severity.toLowerCase()) {
            case "error": return Issue.Severity.ERROR;
            case "warning": return Issue.Severity.WARNING;
            case "info": return Issue.Severity.INFO;
            default: return Issue.Severity.ERROR;
        }
    }

    private String toResourcePath(String absolutePath, File sourceDir) {
        try {
            String sourceAbs = sourceDir.getCanonicalPath();
            String fileAbs = new File(absolutePath).getCanonicalPath();

            if (fileAbs.startsWith(sourceAbs)) {
                String relative = fileAbs.substring(sourceAbs.length());
                return "/" + relative.replace(File.separatorChar, '/');
            }
        } catch (IOException e) {
            // Fall through
        }
        return null;
    }

    enum Platform {
        X86_64_LINUX("x86_64-linux"),
        X86_64_MACOS("x86_64-macos"),
        ARM64_MACOS("arm64-macos"),
        X86_64_WIN32("x86_64-win32");

        private final String pair;

        Platform(String pair) {
            this.pair = pair;
        }

        public String getPair() {
            return pair;
        }

        public static Platform getHostPlatform() {
            String os = System.getProperty("os.name").toLowerCase();
            String arch = System.getProperty("os.arch").toLowerCase();

            if (os.contains("windows")) {
                return X86_64_WIN32;
            } else if (os.contains("mac") || os.contains("darwin")) {
                return arch.contains("aarch64") ? ARM64_MACOS : X86_64_MACOS;
            } else {
                return X86_64_LINUX;
            }
        }
    }
}
```

### 2. Platform Detection Notes

The `Platform.getHostPlatform()` method handles:
- **Windows**: Always x86_64-win32 (Defold doesn't support ARM Windows)
- **Linux**: Always x86_64-linux (Defold doesn't support ARM Linux)
- **macOS**: Both x86_64 and arm64 (Apple Silicon)

---

## Build Configuration

### build.gradle

**File:** `build.gradle`

```gradle
plugins {
    id 'java'
}

group = 'com.defold.extension'
version = '1.0.0'

repositories {
    mavenCentral()
    maven {
        url "https://maven.pkg.github.com/defold/defold"
        credentials {
            username = System.getenv("GITHUB_TOKEN")
            password = ""
        }
    }
}

dependencies {
    // Defold build system (provides ILuaTranspiler interface)
    implementation 'com.dynamo.cr:bob-java:1.2.0'

    testImplementation 'junit:junit:4.13.2'
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

jar {
    manifest {
        attributes(
            'Implementation-Title': 'LuaNext Transpiler Extension',
            'Implementation-Version': project.version
        )
    }
}

tasks.withType(JavaCompile) {
    options.encoding = 'UTF-8'
}

tasks.build.dependsOn ':makeBinaries'

task makeBinaries(type: Exec) {
    workingDir projectDir
    commandLine 'bash', 'make-binaries.sh'
}
```

---

## Rust Binary Builds

### 1. make-binaries.sh

**File:** `make-binaries.sh`

```bash
#!/bin/bash
set -e

LUAEXT_VERSION=$(grep '^version' ../Cargo.toml | head -1 | cut -d'"' -f2)
PLATFORMS=(
    "x86_64-unknown-linux-gnu"
    "x86_64-apple-darwin"
    "aarch64-apple-darwin"
    "x86_64-pc-windows-msvc"
)

# Compiler target triples for cross-compilation
COMPILER_TARGETS=(
    "x86_64-unknown-linux-gnu"
    "x86_64-apple-darwin"
    "aarch64-apple-darwin"
    "x86_64-pc-windows-msvc"
)

# Defold platform names
DEFOLD_PLATFORMS=(
    "x86_64-linux"
    "x86_64-macos"
    "arm64-macos"
    "x86_64-win32"
)

echo "Building LuaNext binaries for Defold v${LUAEXT_VERSION}"

# Build compiler
echo "Building luanext-compiler..."
for i in "${!PLATFORMS[@]}"; do
    PLATFORM=${PLATFORMS[$i]}
    DEFOLD_PLATFORM=${DEFOLD_PLATFORMS[$i]}

    echo "Building for ${PLATFORM}..."

    if [ "${DEFOLD_PLATFORM}" = "x86_64-win32" ]; then
        cargo build --release --target ${PLATFORM} --bin luanext
        cp target/${PLATFORM}/release/luanext.exe build_temp/luanext-compiler.exe
        cargo build --release --target ${PLATFORM} --bin luanext-lsp
        cp target/${PLATFORM}/release/luanext-lsp.exe build_temp/luanext-lsp.exe
    else
        cargo build --release --target ${PLATFORM} --bin luanext
        cp target/${PLATFORM}/release/luanext build_temp/luanext-compiler
        cargo build --release --target ${PLATFORM} --bin luanext-lsp
        cp target/${PLATFORM}/release/luanext-lsp build_temp/luanext-lsp
    fi

    # Create platform-specific zip
    OUTPUT_DIR="luanext/plugins/${DEFOLD_PLATFORM}.zip"
    TEMP_ZIP="build_temp/${DEFOLD_PLATFORM}_temp"

    mkdir -p "${TEMP_ZIP}/bin"
    mv build_temp/luanext-compiler* "${TEMP_ZIP}/bin/" 2>/dev/null || true
    mv build_temp/luanext-lsp* "${TEMP_ZIP}/bin/" 2>/dev/null || true

    # Set executable permissions on Linux/macOS
    if [ "${DEFOLD_PLATFORM}" != "x86_64-win32" ]; then
        chmod +x "${TEMP_ZIP}/bin/luanext-compiler"
        chmod +x "${TEMP_ZIP}/bin/luanext-lsp"
    fi

    cd "${TEMP_ZIP}"
    zip -r "../../${OUTPUT_DIR}" -q * 2>/dev/null || true
    cd ../..

    rm -rf "build_temp/${DEFOLD_PLATFORM}_temp"
done

rm -rf build_temp

echo "Binary builds complete!"
```

### 2. build_plugin.sh

**File:** `build_plugin.sh`

```bash
#!/bin/bash
set -e

# Build Java plugin
echo "Building Java plugin..."
./gradlew clean build jar

# Copy JAR to plugins directory
mkdir -p luanext/plugins/share
cp build/libs/extension-luanext-*.jar luanext/plugins/share/extension-luanext.jar

echo "Plugin build complete!"
```

---

## LSP Integration

### luanext.editor_script

**File:** `luanext.editor_script`

```lua
local M = {}

function M.get_language_servers()
    -- Detect platform
    local platform = editor.platform
    local path = "build/plugins/extension-luanext/plugins/bin/" .. platform .. "/bin/luanext-lsp"

    -- Add .exe extension on Windows
    if platform:sub(-#"win32") == "win32" then
        path = path .. ".exe"
    end

    return {{
        languages = {"luanext"},
        command = {path}
    }}
end

return M
```

---

## Defold API Type Definitions

### Directory Structure

```
luanext/stdlib/
├── go.d.luax          # GameObject APIs
├── msg.d.luax         # Messaging APIs
├── gui.d.luax         # GUI APIs
├── vmath.d.luax       # Vector math
├── pp.d.luax          # Particle FX
├── sound.d.luax       # Sound
├── physics.d.luax     # Physics
├── sys.d.luax         # System
├── url.d.luax         # URLs
├── hash.d.luax        # Hashing
└── ...
```

### Example: go.d.luax

**File:** `luanext/stdlib/go.d.luax`

```luanext
-- Defold GameObject API type definitions
-- Generated from Defold API reference: https://www.defold.com/ref/stable/go/

declare namespace go
    -- Object retrieval
    function get(id: string | hash | url): go.Object
    function get_position(id: hash | url): vmath.vector3
    function set_position(id: string | hash | url, position: vmath.vector3 | vmath.vector4): void
    function set_rotation(id: string | hash | url, rotation: vmath.quat | vmath.vector3 | vmath.vector4): void
    function get_rotation(id: hash | url): vmath.quat
    function set_scale(id: string | hash | url, scale: vmath.vector3 | vmath.vector4 | number): void
    function get_scale(id: hash | url): vmath.vector3
    function delete(id: string | hash | url, recursive: boolean?): void

    -- Animation
    function cancel(id: hash | url, playback: hash): void
    function animate(id: hash | url, property: string, playback: go.Playback, to: number | vmath.vector3 | vmath.vector4, easing: go.Easing?, duration: number?, delay: number?): hash
    function animate(id: hash | url, property: string, playback: go.Playback, animation: string): hash

    -- Properties
    function get(id: string | hash | url, property: string | hash): any
    function set(id: string | hash | url, property: string | hash, value: any): boolean

    -- Messages
    function post(id: string | hash | url, message_id: string | hash, message: table): void
    function post(msg_id: string | hash, ...): void

    -- Container
    function acquire_input_focus(id: string | hash | url): boolean
    function release_input_focus(id: string | hash | url): boolean

    -- Factories
    function factory(resource_id: string | hash): go.Factory
end

-- Type definitions
declare type go.Object = { id: hash }
declare type go.Factory = { resource: hash }

 declare type go.Playback =
    "LOOP_FORWARD" |
    "LOOP_BACKWARD" |
    "LOOP_PINGPONG" |
    "ONCE_FORWARD" |
    "ONCE_BACKWARD" |
    "ONCE_PINGPONG"

declare type go.Easing =
    "LINEAR" |
    "INSINE" |
    "OUTSINE" |
    "INOUTSINE" |
    "OUTINSINE" |
    "INQUAD" |
    "OUTQUAD" |
    "INOUTQUAD" |
    "OUTINQUAD" |
    "INCUBIC" |
    "OUTCUBIC" |
    "INOUTCUBIC" |
    "OUTINCUBIC" |
    "INQUART" |
    "OUTQUART" |
    "INOUTQUART" |
    "OUTINQUART" |
    "INQUINT" |
    "OUTQUINT" |
    "INOUTQUINT" |
    "OUTINQUINT" |
    "INEXPO" |
    "OUTEXPO" |
    "INOUTEXPO" |
    "OUTINEXPO" |
    "INCIRC" |
    "OUTCIRC" |
    "INOUTCIRC" |
    "OUTINCIRC" |
    "INBACK" |
    "OUTBACK" |
    "INOUTBACK" |
    "OUTINBACK" |
    "INELASTIC" |
    "OUTELASTIC" |
    "INOUTELASTIC" |
    "OUTINELASTIC" |
    "INBOUNCE" |
    "OUTBOUNCE" |
    "INOUTBOUNCE" |
    "OUTINBOUNCE"
```

### Example: msg.d.luax

**File:** `luanext/stdlib/msg.d.luax`

```luanext
-- Defold Messaging API
declare namespace msg
    -- Message posting
    function post(url: string | url | hash, message_id: string | hash, message: table): void

    -- URL construction
    function url(): url
end

-- URL type (core Defold type)
declare type url = {
    socket: string,
    path: string,
    fragment: string
}
```

### Example: vmath.d.luax

**File:** `luanext/stdlib/vmath.d.luax`

```luanext
-- Defold Vector Math API
declare namespace vmath
    -- Vector3
    function vector3(x: number?, y: number?, z: number?): vmath.vector3
    function vector3(v: vmath.vector3): vmath.vector3

    -- Vector4
    function vector4(x: number?, y: number?, z: number?, w: number?): vmath.vector4
    function vector4(v: vmath.vector4): vmath.vector4

    -- Quaternion
    function quat(): vmath.quat
    function quat(x: number, y: number, z: number, w: number): vmath.quat
    function quat(q: vmath.quat): vmath.quat

    -- Matrix4
    function matrix4(): vmath.matrix4
    function matrix4(m: vmath.matrix4): vmath.matrix4

    -- Operations
    function normalize(v: vmath.vector3 | vmath.vector4): vmath.vector3 | vmath.vector4
    function length(v: vmath.vector3 | vmath.vector4): number
    function dot(v1: vmath.vector3 | vmath.vector4, v2: vmath.vector3 | vmath.vector4): number
    function cross(v1: vmath.vector3, v2: vmath.vector3): vmath.vector3
    function lerp(v1: vmath.vector3 | vmath.vector4, v2: vmath.vector3 | vmath.vector4, t: number): vmath.vector3 | vmath.vector4
end

declare type vmath.vector3 = {
    x: number,
    y: number,
    z: number
}

declare type vmath.vector4 = {
    x: number,
    y: number,
    z: number,
    w: number
}

declare type vmath.quat = {
    x: number,
    y: number,
    z: number,
    w: number
}

declare type vmath.matrix4 = {
    c0: vmath.vector4,
    c1: vmath.vector4,
    c2: vmath.vector4,
    c3: vmath.vector4
}
```

---

## Project Configuration Files

### ext.manifest

**File:** `ext.manifest`

```yaml
name: LuaNextExtension
```

### luanext.config.yaml

**File:** `luanext/luanext.config.yaml`

```yaml
compilerOptions:
  target: "5.1"              # Lua 5.1 for Defold compatibility
  outDir: "./"
  sourceMap: true
  strict: true
  noEmitOnError: true

include:
  - "**/*.luax"

exclude:
  - "stdlib/**/*.luax"
```

---

## Documentation

### README.md

**File:** `README.md`

```markdown
# LuaNext for Defold

Typed LuaNext language support for Defold game engine.

## Installation

1. Open your Defold project
2. Go to **Assets → Library**
3. Add dependency: `https://github.com/your-org/luanext-defold`
4. Click **Fetch Assets**

## Quick Start

1. Create a `.luanextrc` configuration file in your project root:

\`\`\`yaml
compilerOptions:
  target: "5.1"
  strict: true

include:
  - "**/*.luax"
\`\`\`

2. Create a `.luax` file (e.g., `main/scripts/game.luax`):

\`\`\`luanext
local velocity: vmath.vector3 = vmath.vector3()

function init(self: typeof(self))
    print("Game initialized")
end

function update(self: typeof(self), dt: number)
    velocity.y = velocity.y - 9.81 * dt
    go.set_position(go.get(self.id), velocity)
end
\`\`\`

3. Use it from your regular Lua script:

\`\`\`lua
local game = require("main.scripts.game")

function init(self)
    game.init(self)
end

function update(self, dt)
    game.update(self, dt)
end
\`\`\`

## Features

- ✅ Static type checking
- ✅ Editor error reporting
- ✅ Language Server Protocol (autocomplete, hover, go-to-definition)
- ✅ Defold API type definitions included
- ✅ Automatic compilation to Lua 5.1
- ✅ Cross-platform support

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
```

---

## Testing Strategy

### 1. Unit Tests

Java unit tests for transpiler wrapper:

**File:** `src/test/java/com/defold/extension/pipeline/LuaNextTranspilerTest.java`

```java
package com.defold.extension.pipeline;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LuaNextTranspilerTest {

    @Test
    public void testGetSourceExt() {
        LuaNextTranspiler transpiler = new LuaNextTranspiler();
        assertEquals("luax", transpiler.getSourceExt());
    }

    @Test
    public void testGetBuildFileResourcePath() {
        LuaNextTranspiler transpiler = new LuaNextTranspiler();
        assertEquals("/.luanextrc", transpiler.getBuildFileResourcePath());
    }

    @Test
    public void testErrorParsing() {
        String errorOutput = "" +
            "error [test.luax:15:8]: Type mismatch expected number found string [E1001]\n" +
            "warning [test.luax:20:5]: Unused variable x [W2001]";

        LuaNextTranspiler transpiler = new LuaNextTranspiler();
        // Mock sourceDir
        File sourceDir = new File("/tmp/test");

        // Access private method via reflection or make protected
        List<Issue> issues = transpiler.parseErrors(errorOutput, sourceDir);

        assertEquals(2, issues.size());
        assertEquals(Issue.Severity.ERROR, issues.get(0).severity);
        assertEquals("/test.luax", issues.get(0).resourcePath);
        assertEquals(15, issues.get(0).lineNumber);
        assertTrue(issues.get(0).message.contains("Type mismatch"));
    }

    @Test
    public void testPlatformDetection() {
        // This will vary by actual test environment
        Platform platform = LuaNextTranspiler.Platform.getHostPlatform();
        assertNotNull(platform);
    }
}
```

### 2. Integration Tests

Test with actual Defold projects:

1. **Simple project**: Create a minimal Defold project with `.luax` files
2. **Error reporting**: Inject type errors and verify editor shows them
3. **LSP features**: Test autocomplete, hover, go-to-definition
4. **CLI build**: Test `bob.jar build` command
5. **Cross-platform**: Test on Linux, macOS, Windows

### 3. Test Files

Create test `.luax` files:

**File:** `test/resoures/test_basic.luax`

```luanext
-- Basic type checking test
const x: number = 42
local y: string = "hello"

function test(a: number, b: string): boolean
    return a > 0
end
```

**File:** `test/resources/test_errors.luax`

```luanext
-- This should produce type errors
const foo: number = "wrong type"        -- Error
local bar: string = 123                 -- Error

function test(a: number, b: string): boolean
    return a > "5"                       -- Error: comparison with string
end
```

---

## Build Automation

Crieate a complete build script:

### build.sh

**File:** `build.sh`

```bash
#!/bin/bash
set -e

echo "Building LuaNext Defold Extension..."

# Create necessary directories
mkdir -p luanext/plugins/share
mkdir -p luanext/stdlib

# Step 1: Build Java plugin
echo "Building Java plugin..."
./build_plugin.sh

# Step 2: Build Rust binaries
echo "Building Rust binaries..."
./make-binaries.sh

# Step 3: Copy metadata files
echo "Copying metadata files..."
cp ext.manifest luanext/
cp luanext.editor_script luanext/
cp README.md luanext/

# Step 4: Create type definitions (if not already created)
if [ ! -f "luanext/stdlib/go.d.luax" ]; then
    echo "Warning: Type definitions not found in stdlib/"
    echo "Please generate them from Defold API documentation"
fi

echo "Build complete!"
echo "Extension package is ready in: ./luanext/"
```

---

## Release Checklist

- [ ] All platform binaries built (Linux x86_64, macOS x86_64/arm64, Windows x86_64)
- [ ] Java plugin compiled to `luanext/plugins/share/extension-luanext.jar`
- [ ] All type definitions in `luanext/stdlib/`
- [ ] `ext.manifest` and `luanext.editor_script` present
- [ ] README.md complete
- [ ] Tested in Defold editor (build works, errors show)
- [ ] Tested with Defold CLI (bob works)
- [ ] LSP provides autocomplete/hover features
- [ ] All unit tests pass
- [ ] Integration tests pass on 3+ platforms
- [ ] Version number updated
- [ ] Changelog updated

---

## Extension Distribution

### Creating the Release

1. **Version bump**: Update version in `build.gradle` and `ext.manifest`
2. **Tag release**: `git tag -a v1.0.0 -m "Release v1.0.0"`
3. **Create GitHub release**: Upload `luanext/` directory as zip
4. **Publish to Defold**: Update library entry on Defold website

### Defold Library Entry

Submit to Defold Library:
- Name: `LuaNext`
- Description: "Typed LuaNext language for Defold"
- URL: `https://github.com/your-org/luanext-defold`
- Tags: `lua`, `types`, `language`, `transpiler`

---

## References

### Official Defold Resources

- [Defold Extensions Manual](https://www.defold.com/manuals/extensions/)
- [Defold Editor Scripts Manual](https://www.defold.com/manuals/editor-scripts/)
- [Defold Bob CLI](https://www.defold.com/manuals/bob/)
- [Defold API Reference](https://www.defold.com/ref/stable/)
- [Extension-Teal (Reference Implementation)](https://github.com/defold/extension-teal)

### Defold Codebase

- [ILuaTranspiler Interface](https://github.com/defold/defold/blob/master/com.dynamo.cr/com.dynamo.cr.bob/src/com/defold/extension/pipeline/ILuaTranspiler.java)
- [Transpiler Build Integration](https://github.com/defold/defold/blob/master/editor/src/clj/editor/code/transpilers.clj)
- [LSP Integration](https://github.com/defold/defold/blob/master/editor/src/clj/editor/editor_extensions.clj)

### LuaNext Resources

- [LuaNext Compiler](https://github.com/forge18/luanext)
- [LuaNext LSP](https://github.com/forge18/luanext/tree/main/crates/luanext-lsp)
- [LuaNext Type System](https://github.com/forge18/luanext-parser)

---

## Appendices

### Appendix A: Error Format Examples

**Pretty Format (with colors):**
```
\x1b[31merror\x1b[0m [test.luax:15:8]: Type mismatch: expected number, found string [E1001]
    return "not a number"
           ^^^^^^^^^^^^^^
```

**Simple Format (machine-readable):**
```
test.luax:15:8: error: Type mismatch: expected number, found string [E1001]
test.luax:20:5: warning: Unused variable: x [W2001]
```

### Appendix B: Platform Matrix

| Platform | Target Triple | Defold Name | Binary Name |
|----------|--------------|------------|-------------|
| Linux x86_64 | x86_64-unknown-linux-gnu | x86_64-linux | luanext-compiler |
| macOS x86_64 | x86_64-apple-darwin | x86_64-macos | luanext-compiler |
| macOS ARM64 | aarch64-apple-darwin | arm64-macos | luanext-compiler |
| Windows x86_64 | x86_64-pc-windows-msvc | x86_64-win32 | luanext-compiler.exe |

### Appendix C: Gradle Dependencies

**Required Defold dependency:**
```gradle
implementation 'com.dynamo.cr:bob-java:1.2.0'
```

This package provides:
- `com.defold.extension.pipeline.ILuaTranspiler` interface
- `com.defold.extension.pipeline.Issue` class
- Integration with Defold build system

**Maven Repository:** `https://maven.pkg.github.com/defold/defold`

**Authentication:** GITHUB_TOKEN environment variable required

---

## Implementation Order

Recommended implementation sequence:

1. **Phase 1**: Create Java `LuaNextTranspiler.java` (core functionality)
2. **Phase 2**: Set up `build.gradle` and compile Java to JAR
3. **Phase 3**: Create `make-binaries.sh` and build cross-platform Rust binaries
4. **Phase 4**: Create `luanext.editor_script` for LSP integration
5. **Phase 5**: Generate type definitions for core Defold APIs (go, msg, vmath)
6. **Phase 6**: Create metadata files (ext.manifest, README.md)
7. **Phase 7**: Write unit tests for Java code
8. **Phase 8**: Integration testing with actual Defold projects
9. **Phase 9**: Documentation (README.md, API docs)
10. **Phase 10**: Build, test, and release

---

## Common Issues and Solutions

### Issue: "Class not found: ILuaTranspiler"

**Solution:** Ensure Defold bob dependency is included and GITHUB_TOKEN is set:
```bash
export GITHUB_TOKEN=your_github_token
```

### Issue: Binaries not found at runtime

**Solution:** Check that zip files have correct structure:
```
x86_64-linux.zip
└── bin/
    ├── luanext-compiler
    └── luanext-lsp
```

### Issue: LSP not starting

**Solution:** Verify platform detection in `luanext.editor_script` and binary path:
```lua
print(debug.traceback())  -- Debug platform detection
```

### Issue: Type definitions not resolving

**Solution:** Ensure `include` paths in `luanext.config.yaml` point to `stdlib/` directory.

---

**End of Implementation Guide**
