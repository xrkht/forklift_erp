import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckCodeQuality {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path JAVA_MAIN_ROOT = PROJECT_ROOT.resolve("src/main/java");
    private static final Path STATIC_ROOT = PROJECT_ROOT.resolve("src/main/resources/static");
    private static final Path SCRIPTS_ROOT = PROJECT_ROOT.resolve("scripts");

    private static final int MAX_NEW_JAVA_LINES = 700;
    private static final int MAX_NEW_JS_LINES = 650;
    private static final int MAX_NEW_CSS_LINES = 1200;

    private static final Map<String, Integer> HISTORICAL_LINE_BASELINES = Map.of(
            "src/main/resources/static/assets/app.js", 6883,
            "src/main/resources/static/assets/app.css", 7,
            "src/main/java/com/example/forklift_erp/config/DemoDataInitializer.java", 978
    );

    private static final Pattern LITERAL_PREAUTHORIZE = Pattern.compile("@PreAuthorize\\s*\\(\\s*\"");
    private static final Pattern NAKED_ROLE_SPEL = Pattern.compile("has(?:Any)?Role\\s*\\(\\s*['\"]");
    private static final Pattern PERMISSION_CODE_LITERAL = Pattern.compile(
            "\"(?:vehicle|part|repair|config|replace|stock|log|user):(?:write|read|adjust|admin)\""
    );
    private static final Pattern FETCH_PATTERN = Pattern.compile("\\bfetch\\s*\\(");
    private static final Pattern FUNCTION_PATTERN = Pattern.compile("(?:async\\s+)?function\\s+([A-Za-z0-9_$]+)\\s*\\(");
    private static final Pattern DUPLICATE_ASYNC_FUNCTION = Pattern.compile("\\basync\\s+async\\s+function\\b");

    private static final Set<String> FETCH_ALLOWED_FILES = Set.of(
            "sw.js",
            "assets/modules/downloads.js",
            "assets/modules/session.js"
    );
    private static final Set<String> APP_FETCH_HELPERS = Set.of(
            "fetchProtectedBlob",
            "downloadInvoice",
            "downloadContract",
            "downloadProtectedFile"
    );

    public static void main(String[] args) throws Exception {
        List<String> failures = new ArrayList<>();

        checkControllerPreAuthorizeConstants(failures);
        checkNakedRoleSpel(failures);
        checkPermissionCodeLiterals(failures);
        checkControlledFetchUsage(failures);
        checkFrontendSyntaxGuards(failures);
        checkLargeFiles(failures);

        if (!failures.isEmpty()) {
            String separator = System.lineSeparator();
            throw new IOException("Code quality checks failed:" + separator + String.join(separator, failures));
        }
        System.out.println("Code quality checks passed");
    }

    private static void checkControllerPreAuthorizeConstants(List<String> failures) throws IOException {
        Path controllerRoot = JAVA_MAIN_ROOT.resolve("com/example/forklift_erp/controller");
        for (Path file : collectFiles(controllerRoot, ".java")) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = LITERAL_PREAUTHORIZE.matcher(source);
            while (matcher.find()) {
                failures.add("- Controller @PreAuthorize must use PermissionCodes constants: "
                        + location(file, source, matcher.start()));
            }
        }
    }

    private static void checkNakedRoleSpel(List<String> failures) throws IOException {
        for (Path file : collectFiles(JAVA_MAIN_ROOT, ".java")) {
            if (isPermissionDefinitionFile(file)) {
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = NAKED_ROLE_SPEL.matcher(source);
            while (matcher.find()) {
                failures.add("- Role SpEL must live in PermissionCodes: " + location(file, source, matcher.start()));
            }
        }
    }

    private static void checkPermissionCodeLiterals(List<String> failures) throws IOException {
        for (Path file : collectFiles(JAVA_MAIN_ROOT, ".java")) {
            if (isPermissionDefinitionFile(file) || isPermissionBootstrapFile(file)) {
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = PERMISSION_CODE_LITERAL.matcher(source);
            while (matcher.find()) {
                failures.add("- Permission code literals must use PermissionCodes constants: "
                        + location(file, source, matcher.start()));
            }
        }
    }

    private static void checkControlledFetchUsage(List<String> failures) throws IOException {
        for (Path file : collectFiles(STATIC_ROOT, ".js")) {
            String relativePath = STATIC_ROOT.relativize(file).toString().replace('\\', '/');
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = FETCH_PATTERN.matcher(source);
            while (matcher.find()) {
                if (isAllowedFetch(relativePath, source, matcher.start())) {
                    continue;
                }
                failures.add("- Bare fetch must go through session.js or an approved binary download helper: "
                        + relativePath + ":" + lineNumber(source, matcher.start()));
            }
        }
    }

    private static void checkLargeFiles(List<String> failures) throws IOException {
        List<Path> files = new ArrayList<>();
        files.addAll(collectFiles(JAVA_MAIN_ROOT, ".java"));
        files.addAll(collectFiles(SCRIPTS_ROOT, ".java"));
        files.addAll(collectFiles(STATIC_ROOT.resolve("assets"), ".js"));
        files.addAll(collectFiles(STATIC_ROOT.resolve("assets"), ".css"));

        for (Path file : files) {
            String relativePath = relativePath(file);
            int lines = Files.readAllLines(file, StandardCharsets.UTF_8).size();
            Integer baseline = HISTORICAL_LINE_BASELINES.get(relativePath);
            if (baseline != null) {
                if (lines > baseline) {
                    failures.add("- Historical large file grew past baseline " + baseline + " lines: "
                            + relativePath + " has " + lines + " lines");
                }
                continue;
            }
            int limit = lineLimit(file);
            if (limit > 0 && lines > limit) {
                failures.add("- New large file exceeds " + limit + " line limit: "
                        + relativePath + " has " + lines + " lines");
            }
        }
    }

    private static void checkFrontendSyntaxGuards(List<String> failures) throws IOException {
        for (Path file : collectFiles(STATIC_ROOT, ".js")) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = DUPLICATE_ASYNC_FUNCTION.matcher(source);
            while (matcher.find()) {
                failures.add("- Duplicate async function keyword breaks browser module parsing: "
                        + location(file, source, matcher.start()));
            }
        }
    }

    private static boolean isPermissionDefinitionFile(Path file) {
        return file.getFileName().toString().equals("PermissionCodes.java");
    }

    private static boolean isPermissionBootstrapFile(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith("DataInitializer.java");
    }

    private static boolean isAllowedFetch(String relativePath, String source, int offset) {
        if (FETCH_ALLOWED_FILES.contains(relativePath)) {
            return true;
        }
        if (!"assets/app.js".equals(relativePath)) {
            return false;
        }
        return APP_FETCH_HELPERS.contains(enclosingFunctionName(source, offset));
    }

    private static String enclosingFunctionName(String source, int offset) {
        Matcher matcher = FUNCTION_PATTERN.matcher(source);
        String functionName = null;
        int bodyStart = -1;
        int bodyEnd = -1;
        while (matcher.find() && matcher.start() < offset) {
            int openBrace = source.indexOf('{', matcher.end());
            if (openBrace < 0 || openBrace > offset) {
                continue;
            }
            functionName = matcher.group(1);
            bodyStart = openBrace;
            bodyEnd = findMatchingBrace(source, openBrace);
        }
        if (functionName == null || offset < bodyStart || offset > bodyEnd) {
            return null;
        }
        return functionName;
    }

    private static int findMatchingBrace(String source, int openBrace) {
        int depth = 0;
        for (int index = openBrace; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return source.length();
    }

    private static int lineLimit(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) {
            return MAX_NEW_JAVA_LINES;
        }
        if (name.endsWith(".js")) {
            return MAX_NEW_JS_LINES;
        }
        if (name.endsWith(".css")) {
            return MAX_NEW_CSS_LINES;
        }
        return 0;
    }

    private static List<Path> collectFiles(Path root, String suffix) throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return files;
        }
        ArrayDeque<Path> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Path current = stack.pop();
            try (var stream = Files.list(current)) {
                for (Path child : stream.sorted().toList()) {
                    if (Files.isDirectory(child)) {
                        stack.push(child);
                    } else if (Files.isRegularFile(child) && child.getFileName().toString().endsWith(suffix)) {
                        files.add(child);
                    }
                }
            }
        }
        return files;
    }

    private static String location(Path file, String source, int offset) {
        return relativePath(file) + ":" + lineNumber(source, offset);
    }

    private static String relativePath(Path file) {
        return PROJECT_ROOT.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static int lineNumber(String source, int offset) {
        int line = 1;
        for (int index = 0; index < offset && index < source.length(); index++) {
            if (source.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }
}
