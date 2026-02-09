package com.defold.extension.pipeline;

import java.io.File;
import java.util.List;

public class Stubs {

    public interface ILuaTranspiler {
        String getBuildFileResourcePath();
        String getSourceExt();
        List<Issue> transpile(File pluginDir, File sourceDir, File outputDir);
    }

    public static class Issue {
        public final Severity severity;
        public final String resourcePath;
        public final int lineNumber;
        public final String message;

        public Issue(Severity severity, String resourcePath, int lineNumber, String message) {
            this.severity = severity;
            this.resourcePath = resourcePath;
            this.lineNumber = lineNumber;
            this.message = message;
        }

        public enum Severity {
            INFO, WARNING, ERROR
        }
    }
}
