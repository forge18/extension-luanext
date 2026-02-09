package com.defold.extension.pipeline;

import java.io.*;
import java.util.*;
import java.util.regex.*;

public class LuaNextTranspiler implements Stubs.ILuaTranspiler {

    @Override
    public String getBuildFileResourcePath() {
        return "/.luanextrc";
    }

    @Override
    public String getSourceExt() {
        return "luax";
    }

    @Override
    public List<Stubs.Issue> transpile(File pluginDir, File sourceDir, File outputDir) {
        List<Stubs.Issue> issues = new ArrayList<>();

        try {
            Platform platform = Platform.getHostPlatform();
            String binaryPath = pluginDir.getAbsolutePath() + "/plugins/bin/"
                    + platform.getPair() + "/bin/luanext-compiler";
            if (platform == Platform.X86_64_WIN32) {
                binaryPath += ".exe";
            }

            File buildConfig = new File(sourceDir, getBuildFileResourcePath().substring(1));
            if (!buildConfig.exists()) {
                issues.add(new Stubs.Issue(
                    Stubs.Issue.Severity.WARNING,
                    getBuildFileResourcePath(),
                    1,
                    "No .luanextrc configuration file found. Using compiler defaults."
                ));
            }

            List<String> command = new ArrayList<>();
            command.add(binaryPath);
            command.add("compile");
            command.add("--target=5.1");
            command.add("--config=" + buildConfig.getAbsolutePath());
            command.add("--output-dir=" + outputDir.getAbsolutePath());

            File[] sourceFiles = sourceDir.listFiles((dir, name) ->
                name.endsWith("." + getSourceExt()));

            if (sourceFiles != null) {
                for (File file : sourceFiles) {
                    command.add(file.getAbsolutePath());
                }
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(sourceDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            issues.addAll(parseErrors(output.toString(), sourceDir));

        } catch (Exception e) {
            issues.add(new Stubs.Issue(
                Stubs.Issue.Severity.ERROR,
                getBuildFileResourcePath(),
                1,
                "Failed to run LuaNext compiler: " + e.getMessage()
            ));
        }

        return issues;
    }

    private List<Stubs.Issue> parseErrors(String output, File sourceDir) {
        List<Stubs.Issue> issues = new ArrayList<>();

        Pattern prettyPattern = Pattern.compile(
            "(error|warning|info) \\[(.+):(\\d+):(\\d+)\\]:\\s*(.*?)(?:\\s*\\[(\\w+)\\])?$"
        );

        Pattern simplePattern = Pattern.compile(
            "(.+):(\\d+):(\\d+):\\s*(error|warning|info):\\s*(.*?)(?:\\s*\\[(\\w+)\\])?$"
        );

        String[] lines = output.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher matcher = prettyPattern.matcher(line);
            if (matcher.matches()) {
                Stubs.Issue.Severity severity = parseSeverity(matcher.group(1));
                String filePath = matcher.group(2);
                int lineNum = Integer.parseInt(matcher.group(3));
                int colNum = Integer.parseInt(matcher.group(4));
                String message = matcher.group(5).trim();

                String resourcePath = toResourcePath(filePath, sourceDir);
                if (resourcePath != null) {
                    issues.add(new Stubs.Issue(severity, resourcePath, lineNum, message));
                }
                continue;
            }

            matcher = simplePattern.matcher(line);
            if (matcher.matches()) {
                String filePath = matcher.group(1);
                int lineNum = Integer.parseInt(matcher.group(2));
                int colNum = Integer.parseInt(matcher.group(3));
                Stubs.Issue.Severity severity = parseSeverity(matcher.group(4));
                String message = matcher.group(5).trim();

                String resourcePath = toResourcePath(filePath, sourceDir);
                if (resourcePath != null) {
                    issues.add(new Stubs.Issue(severity, resourcePath, lineNum, message));
                }
            }
        }

        return issues;
    }

    private Stubs.Issue.Severity parseSeverity(String severity) {
        switch (severity.toLowerCase()) {
            case "error": return Stubs.Issue.Severity.ERROR;
            case "warning": return Stubs.Issue.Severity.WARNING;
            case "info": return Stubs.Issue.Severity.INFO;
            default: return Stubs.Issue.Severity.ERROR;
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
