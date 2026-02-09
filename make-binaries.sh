#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LUAEXT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$LUAEXT_ROOT/../luanext"

PLATFORMS=(
    "x86_64-unknown-linux-gnu"
    "x86_64-apple-darwin"
    "aarch64-apple-darwin"
    "x86_64-pc-windows-msvc"
)

DEFOLD_PLATFORMS=(
    "x86_64-linux"
    "x86_64-macos"
    "arm64-macos"
    "x86_64-win32"
)

cd "$SCRIPT_DIR"

echo "Building LuaNext binaries for Defold"

for i in "${!PLATFORMS[@]}"; do
    PLATFORM=${PLATFORMS[$i]}
    DEFOLD_PLATFORM=${DEFOLD_PLATFORMS[$i]}

    echo "Building for ${PLATFORM}..."

    cd "$LUAEXT_ROOT/../luanext"

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

    OUTPUT_DIR="luanext/plugins/${DEFOLD_PLATFORM}.zip"
    TEMP_ZIP="build_temp/${DEFOLD_PLATFORM}_temp"

    mkdir -p "${TEMP_ZIP}/bin"
    mv build_temp/luanext-compiler* "${TEMP_ZIP}/bin/" 2>/dev/null || true
    mv build_temp/luanext-lsp* "${TEMP_ZIP}/bin/" 2>/dev/null || true

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
