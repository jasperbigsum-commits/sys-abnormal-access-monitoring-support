import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Source-level guard for the symmetric Plan A acceptance-test identifiers. */
public final class AcceptanceIdVerifier {
    private static final Pattern TEST_METHOD = Pattern.compile(
        "(?m)((?:^[ \\t]*@[^\\r\\n]+\\R)+)[ \\t]*(?:(?:public|protected|private)[ \\t]+)?"
            + "void[ \\t]+([A-Za-z_$][A-Za-z0-9_$]*)[ \\t]*\\(");
    private static final Pattern TEST_ANNOTATION = Pattern.compile("(?m)^[ \\t]*@Test[ \\t]*$");
    private static final Pattern DISPLAY_NAME = Pattern.compile(
        "@DisplayName[ \\t]*\\([ \\t]*\"((?:\\\\.|[^\"\\\\])*)\"[ \\t]*\\)");
    private static final Pattern METHOD_ID = Pattern.compile("^(tc|ia)([0-9]{2})_.+");
    private static final Pattern DISPLAY_ID = Pattern.compile("(?<![A-Z0-9-])(TC|IA)-([0-9]{2})(?![0-9])");

    private AcceptanceIdVerifier() {
    }

    public static void main(String[] args) throws Exception {
        Path base = args.length == 0 ? Paths.get(".") : Paths.get(args[0]);
        Path spring2 = base.resolve("spring2-web/src/test/java/io/github/jasper/monitoring/audit/spring2");
        Path spring3 = base.resolve("spring3-web/src/test/java/io/github/jasper/monitoring/audit/spring3");

        List<String> errors = new ArrayList<String>();
        Map<String, String> boot2 = inspect("Boot 2", spring2, errors);
        Map<String, String> boot3 = inspect("Boot 3", spring3, errors);
        Set<String> expected = expectedIds();
        compareWithExpected("Boot 2", boot2.keySet(), expected, errors);
        compareWithExpected("Boot 3", boot3.keySet(), expected, errors);
        if (!boot2.keySet().equals(boot3.keySet())) {
            errors.add("Boot 2 and Boot 3 acceptance ID sets are not symmetric: Boot 2="
                + boot2.keySet() + ", Boot 3=" + boot3.keySet());
        }

        if (!errors.isEmpty()) {
            System.err.println("Acceptance ID verification failed:");
            for (String error : errors) {
                System.err.println(" - " + error);
            }
            throw new IllegalStateException("Acceptance ID verification found " + errors.size() + " error(s)");
        }
        System.out.println("Acceptance ID verification passed: each Boot host uniquely covers "
            + "TC-01..TC-18 and IA-01..IA-12 with matching method names and @DisplayName values.");
    }

    private static Map<String, String> inspect(String host, Path testRoot, List<String> errors) throws IOException {
        Map<String, String> ids = new LinkedHashMap<String, String>();
        if (!Files.isDirectory(testRoot)) {
            errors.add(host + " acceptance test directory is missing: " + testRoot);
            return ids;
        }
        List<Path> sources;
        try (Stream<Path> files = Files.walk(testRoot)) {
            sources = files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith("AcceptanceTest.java"))
                .sorted()
                .collect(Collectors.toList());
        }
        if (sources.isEmpty()) {
            errors.add(host + " contains no *AcceptanceTest.java sources under " + testRoot);
            return ids;
        }
        int testCount = 0;
        for (Path source : sources) {
            testCount += inspectSource(host, source, ids, errors);
        }
        if (testCount == 0) {
            errors.add(host + " acceptance sources contain no @Test methods under " + testRoot);
        }
        return ids;
    }

    private static int inspectSource(String host, Path source, Map<String, String> ids,
                                     List<String> errors) throws IOException {
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        Matcher methods = TEST_METHOD.matcher(text);
        int testCount = 0;
        while (methods.find()) {
            String annotations = methods.group(1);
            if (!TEST_ANNOTATION.matcher(annotations).find()) {
                continue;
            }
            testCount++;
            String method = methods.group(2);
            Matcher methodId = METHOD_ID.matcher(method);
            if (!methodId.matches()) {
                errors.add(host + " @Test method must start with tcXX_ or iaXX_: " + method);
                continue;
            }
            String id = methodId.group(1).toUpperCase() + "-" + methodId.group(2);
            String location = source.getFileName() + "#" + method;
            String previous = ids.put(id, location);
            if (previous != null) {
                errors.add(host + " duplicates " + id + " in " + previous + " and " + location);
            }

            Matcher displayName = DISPLAY_NAME.matcher(annotations);
            if (!displayName.find()) {
                errors.add(host + " " + method + " must declare a one-line @DisplayName containing " + id);
                continue;
            }
            String display = displayName.group(1);
            Matcher displayId = DISPLAY_ID.matcher(display);
            List<String> displayIds = new ArrayList<String>();
            while (displayId.find()) {
                displayIds.add(displayId.group(1) + "-" + displayId.group(2));
            }
            if (!displayIds.equals(Arrays.asList(id))) {
                errors.add(host + " " + method + " @DisplayName must contain exactly " + id
                    + " but contained " + displayIds + ": " + display);
            }
        }
        return testCount;
    }

    private static void compareWithExpected(String host, Set<String> actual, Set<String> expected,
                                            List<String> errors) {
        Set<String> missing = new LinkedHashSet<String>(expected);
        missing.removeAll(actual);
        Set<String> unexpected = new LinkedHashSet<String>(actual);
        unexpected.removeAll(expected);
        if (!missing.isEmpty()) {
            errors.add(host + " is missing acceptance IDs: " + missing);
        }
        if (!unexpected.isEmpty()) {
            errors.add(host + " has unexpected acceptance IDs: " + unexpected);
        }
        if (actual.size() != expected.size()) {
            errors.add(host + " must contain exactly " + expected.size() + " acceptance tests but found "
                + actual.size());
        }
    }

    private static Set<String> expectedIds() {
        Set<String> ids = new LinkedHashSet<String>();
        for (int index = 1; index <= 18; index++) {
            ids.add(String.format("TC-%02d", Integer.valueOf(index)));
        }
        for (int index = 1; index <= 12; index++) {
            ids.add(String.format("IA-%02d", Integer.valueOf(index)));
        }
        return ids;
    }
}
