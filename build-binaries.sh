#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXTENSION_DIR="$SCRIPT_DIR"
LUAEXT_SOURCE_DIR="$EXTENSION_DIR/_build-temp"

echo "=== LuaNext Binary Build Script ==="

if [ -d "$LUAEXT_SOURCE_DIR" ]; then
    rm -rf "$LUAEXT_SOURCE_DIR"
fi

mkdir -p "$LUAEXT_SOURCE_DIR"

echo "Cloning luanext repositories..."
cd "$LUAEXT_SOURCE_DIR"
git clone https://github.com/forge18/luanext.git .
git clone https://github.com/forge18/luanext-sourcemap.git crates/luanext-sourcemap 2>/dev/null || true
git clone https://github.com/forge18/luanext-typechecker.git crates/luanext-typechecker 2>/dev/null || true
git clone https://github.com/forge18/luanext-parser.git crates/luanext-parser 2>/dev/null || true
git clone https://github.com/forge18/luanext-runtime.git crates/luanext-runtime 2>/dev/null || true
git clone https://github.com/forge18/luanext-core.git crates/luanext-core 2>/dev/null || true
git clone https://github.com/forge18/luanext-lsp.git crates/luanext-lsp 2>/dev/null || true
git clone https://github.com/forge18/luanext-cli.git crates/luanext-cli 2>/dev/null || true

echo "Building Rust binaries..."

HOST_PLATFORM=$(uname -s)
HOST_ARCH=$(uname -m)

ALL_PLATFORMS=(
    "x86_64-unknown-linux-gnu:x86_64-linux"
    "x86_64-apple-darwin:x86_64-macos"
    "aarch64-apple-darwin:arm64-macos"
)

PLATFORMS=()
DEFOLD_PLATFORMS=()

for entry in "${ALL_PLATFORMS[@]}"; do
    IFS=':' read -r target defold_name <<< "$entry"
    PLATFORMS+=("$target")
    DEFOLD_PLATFORMS+=("$defold_name")
done

echo "Building for all platforms: ${PLATFORMS[*]}"

for i in "${!PLATFORMS[@]}"; do
    PLATFORM=${PLATFORMS[$i]}
    DEFOLD_PLATFORM=${DEFOLD_PLATFORMS[$i]}

    echo "Building for ${PLATFORM}..."

    cd "$LUAEXT_SOURCE_DIR"

    unset CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER
    unset CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER
    unset CARGO_TARGET_X86_64_PC_WINDOWS_MSVC_LINKER
    unset CC
    unset CXX

    BUILD_CMD="cargo"

    if [ "${PLATFORM}" = "x86_64-unknown-linux-gnu" ]; then
        BUILD_CMD="cargo zigbuild --target x86_64-unknown-linux-gnu --release --bin luanext --bin luanext-lsp"
    elif [ "${PLATFORM}" = "aarch64-unknown-linux-gnu" ]; then
        BUILD_CMD="cargo zigbuild --target aarch64-unknown-linux-gnu --release --bin luanext --bin luanext-lsp"
    elif [ "${PLATFORM}" = "x86_64-pc-windows-msvc" ]; then
        BUILD_CMD="cargo zigbuild --target x86_64-pc-windows-msvc --release --bin luanext --bin luanext-lsp"
    else
        BUILD_CMD="cargo build --release --bin luanext --bin luanext-lsp"
    fi

    if [ "${DEFOLD_PLATFORM}" = "x86_64-win32" ]; then
        $BUILD_CMD
        cp target/${PLATFORM}/release/luanext.exe /tmp/luanext-compiler.exe
        cp target/${PLATFORM}/release/luanext-lsp.exe /tmp/luanext-lsp.exe
    else
        $BUILD_CMD
        cp target/${PLATFORM}/release/luanext /tmp/luanext-compiler 2>/dev/null || cp target/release/luanext /tmp/luanext-compiler
        cp target/${PLATFORM}/release/luanext-lsp /tmp/luanext-lsp 2>/dev/null || cp target/release/luanext-lsp /tmp/luanext-lsp
    fi

    OUTPUT_DIR="$EXTENSION_DIR/luanext/plugins/${DEFOLD_PLATFORM}.zip"
    TEMP_ZIP="/tmp/${DEFOLD_PLATFORM}_temp"

    mkdir -p "${TEMP_ZIP}/bin"
    cp /tmp/luanext-compiler* "${TEMP_ZIP}/bin/" 2>/dev/null || true
    cp /tmp/luanext-lsp* "${TEMP_ZIP}/bin/" 2>/dev/null || true

    if [ "${DEFOLD_PLATFORM}" != "x86_64-win32" ]; then
        chmod +x "${TEMP_ZIP}/bin/luanext-compiler"
        chmod +x "${TEMP_ZIP}/bin/luanext-lsp"
    fi

    cd "${TEMP_ZIP}"
    zip -r "${OUTPUT_DIR}" -q *
    cd "$LUAEXT_SOURCE_DIR"

    rm -rf "${TEMP_ZIP}"
    rm -f /tmp/luanext-compiler* /tmp/luanext-lsp*

    echo "Created ${OUTPUT_DIR}"
done

cd "$EXTENSION_DIR"

echo "Cleaning up source code..."
rm -rf "$LUAEXT_SOURCE_DIR"

echo "=== Build Complete ==="
echo "Binaries are ready in luanext/plugins/"
