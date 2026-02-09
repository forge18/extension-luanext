package com.defold.extension.pipeline;

import org.junit.Test;
import java.io.File;
import java.util.List;
import static org.junit.Assert.*;

public class LuaNextTranspilerTest {

    private LuaNextTranspiler transpiler = new LuaNextTranspiler();

    @Test
    public void testGetSourceExt() {
        assertEquals("luax", transpiler.getSourceExt());
    }

    @Test
    public void testGetBuildFileResourcePath() {
        assertEquals("/.luanextrc", transpiler.getBuildFileResourcePath());
    }

    @Test
    public void testPlatformEnumValues() {
        LuaNextTranspiler.Platform[] platforms = LuaNextTranspiler.Platform.values();
        assertEquals(4, platforms.length);

        assertEquals("x86_64-linux", LuaNextTranspiler.Platform.X86_64_LINUX.getPair());
        assertEquals("x86_64-macos", LuaNextTranspiler.Platform.X86_64_MACOS.getPair());
        assertEquals("arm64-macos", LuaNextTranspiler.Platform.ARM64_MACOS.getPair());
        assertEquals("x86_64-win32", LuaNextTranspiler.Platform.X86_64_WIN32.getPair());
    }

    @Test
    public void testPlatformDetection() {
        LuaNextTranspiler.Platform platform = LuaNextTranspiler.Platform.getHostPlatform();
        assertNotNull(platform);
        assertNotNull(platform.getPair());
    }

    @Test
    public void testParseSeverityError() {
        assertEquals(Stubs.Issue.Severity.ERROR, parseSeverity("error"));
    }

    @Test
    public void testParseSeverityWarning() {
        assertEquals(Stubs.Issue.Severity.WARNING, parseSeverity("warning"));
    }

    @Test
    public void testParseSeverityInfo() {
        assertEquals(Stubs.Issue.Severity.INFO, parseSeverity("info"));
    }

    @Test
    public void testParseSeverityUnknownDefaultsToError() {
        assertEquals(Stubs.Issue.Severity.ERROR, parseSeverity("unknown"));
    }

    @Test
    public void testParseErrorsPrettyFormat() {
        String output = "error [/path/to/test.luax:15:8]: Type mismatch expected number found string [E1001]\n" +
                        "warning [/path/to/test.luax:20:5]: Unused variable x [W2001]";

        File sourceDir = new File("/path/to");
        List<Stubs.Issue> issues = parseErrors(output, sourceDir);

        assertEquals(2, issues.size());

        Stubs.Issue error = issues.get(0);
        assertEquals(Stubs.Issue.Severity.ERROR, error.severity);
        assertEquals("/test.luax", error.resourcePath);
        assertEquals(15, error.lineNumber);
        assertTrue(error.message.contains("Type mismatch"));

        Stubs.Issue warning = issues.get(1);
        assertEquals(Stubs.Issue.Severity.WARNING, warning.severity);
        assertEquals("/test.luax", warning.resourcePath);
        assertEquals(20, warning.lineNumber);
        assertTrue(warning.message.contains("Unused variable"));
    }

    @Test
    public void testParseErrorsSimpleFormat() {
        String output = "/path/to/test.luax:15:8: error: Type mismatch expected number found string [E1001]\n" +
                        "/path/to/test.luax:20:5: warning: Unused variable x [W2001]";

        File sourceDir = new File("/path/to");
        List<Stubs.Issue> issues = parseErrors(output, sourceDir);

        assertEquals(2, issues.size());

        Stubs.Issue error = issues.get(0);
        assertEquals(Stubs.Issue.Severity.ERROR, error.severity);
        assertEquals("/test.luax", error.resourcePath);
        assertEquals(15, error.lineNumber);
    }

    @Test
    public void testParseErrorsEmptyOutput() {
        String output = "";
        File sourceDir = new File("/tmp");
        List<Stubs.Issue> issues = parseErrors(output, sourceDir);
        assertEquals(0, issues.size());
    }

    @Test
    public void testParseErrorsIgnoresEmptyLines() {
        String output = "error [/path/to/test.luax:10:1]: Some error\n\n\nwarning [/path/to/test.luax:15:1]: Another warning\n";

        File sourceDir = new File("/path/to");
        List<Stubs.Issue> issues = parseErrors(output, sourceDir);

        assertEquals(2, issues.size());
    }

    @Test
    public void testToResourcePath() {
        File sourceDir = new File("/project/src");
        String result = toResourcePath("/project/src/test.luax", sourceDir);
        assertEquals("/test.luax", result);
    }

    @Test
    public void testToResourcePathReturnsNullForOutsideSourceDir() {
        File sourceDir = new File("/project/src");
        String result = toResourcePath("/other/path/file.luax", sourceDir);
        assertNull(result);
    }

    private Stubs.Issue.Severity parseSeverity(String severity) {
        try {
            java.lang.reflect.Method method = LuaNextTranspiler.class.getDeclaredMethod("parseSeverity", String.class);
            method.setAccessible(true);
            return (Stubs.Issue.Severity) method.invoke(transpiler, severity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<Stubs.Issue> parseErrors(String output, File sourceDir) {
        try {
            java.lang.reflect.Method method = LuaNextTranspiler.class.getDeclaredMethod("parseErrors", String.class, File.class);
            method.setAccessible(true);
            return (List<Stubs.Issue>) method.invoke(transpiler, output, sourceDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String toResourcePath(String absolutePath, File sourceDir) {
        try {
            java.lang.reflect.Method method = LuaNextTranspiler.class.getDeclaredMethod("toResourcePath", String.class, File.class);
            method.setAccessible(true);
            return (String) method.invoke(transpiler, absolutePath, sourceDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
