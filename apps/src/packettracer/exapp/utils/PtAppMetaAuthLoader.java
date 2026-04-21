package packettracer.exapp.utils;

import packettracer.exapp.core.BootstrapReport;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PtAppMetaAuthLoader {
    private static final Pattern PT_APP_META_ID_PATTERN = Pattern.compile("<ID>\\s*([^<]+?)\\s*</ID>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PT_APP_META_KEY_PATTERN = Pattern.compile("<KEY>\\s*([^<]+?)\\s*</KEY>", Pattern.CASE_INSENSITIVE);

    private PtAppMetaAuthLoader() {
    }

    public static PtAppMetaAuth tryLoad(BootstrapReport report, String ptAppMetaFilename, Class<?> anchorClass) {
        Set<Path> candidatePaths = collectCandidatePaths(ptAppMetaFilename, anchorClass);

        for (Path candidatePath : candidatePaths) {
            try {
                if (!Files.isRegularFile(candidatePath)) {
                    continue;
                }

                String xmlContent = Files.readString(candidatePath, StandardCharsets.ISO_8859_1);
                String applicationId = extractValue(xmlContent, PT_APP_META_ID_PATTERN);
                String sharedKey = extractValue(xmlContent, PT_APP_META_KEY_PATTERN);

                if (applicationId == null && sharedKey == null) {
                    report.addDetail(String.format("Read %s at %s but did not find usable <ID> or <KEY> values.", ptAppMetaFilename, candidatePath));
                    return null;
                }

                report.addDetail(String.format("Loaded %s authentication fallback candidates from %s (ID=%s, KEY=%s).", ptAppMetaFilename, candidatePath, applicationId == null ? "missing" : "present", sharedKey == null ? "missing" : String.format("present (%d characters)", Integer.valueOf(sharedKey.length()))));
                return new PtAppMetaAuth(candidatePath, applicationId, sharedKey);
            } catch (Throwable throwable) {
                report.addDetail(String.format("Failed to read %s from %s: %s: %s", ptAppMetaFilename, candidatePath, throwable.getClass().getName(), ThrowableUtils.safeMessage(throwable)));
            }
        }

        report.addDetail(String.format("No readable %s file was found while preparing auth fallbacks.", ptAppMetaFilename));
        return null;
    }

    private static Set<Path> collectCandidatePaths(String ptAppMetaFilename, Class<?> anchorClass) {
        LinkedHashSet<Path> candidatePaths = new LinkedHashSet<Path>();
        addCandidate(candidatePaths, Paths.get(ptAppMetaFilename));

        Path codeSourceBase = resolveCodeSourceBaseDirectory(anchorClass);

        if (codeSourceBase != null) {
            addCandidate(candidatePaths, codeSourceBase.resolve(ptAppMetaFilename));

            Path parent = codeSourceBase.getParent();

            if (parent != null) {
                addCandidate(candidatePaths, parent.resolve(ptAppMetaFilename));

                Path grandParent = parent.getParent();

                if (grandParent != null) {
                    addCandidate(candidatePaths, grandParent.resolve(ptAppMetaFilename));
                }
            }
        }

        return candidatePaths;
    }

    private static void addCandidate(Set<Path> candidatePaths, Path candidatePath) {
        try {
            candidatePaths.add(candidatePath.toAbsolutePath().normalize());
        } catch (Throwable throwable) {
            candidatePaths.add(candidatePath.normalize());
        }
    }

    private static Path resolveCodeSourceBaseDirectory(Class<?> anchorClass) {
        try {
            URI location = anchorClass.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path locationPath = Paths.get(location).toAbsolutePath().normalize();
            return Files.isDirectory(locationPath) ? locationPath : locationPath.getParent();
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static String extractValue(String xmlContent, Pattern pattern) {
        Matcher matcher = pattern.matcher(xmlContent);

        if (!matcher.find()) {
            return null;
        }

        String value = matcher.group(1);

        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? null : trimmedValue;
    }

    public static final class PtAppMetaAuth {
        private final Path sourcePath;
        private final String applicationId;
        private final String sharedKey;

        private PtAppMetaAuth(Path sourcePath, String applicationId, String sharedKey) {
            this.sourcePath = sourcePath;
            this.applicationId = applicationId;
            this.sharedKey = sharedKey;
        }

        public String getApplicationId() {
            return applicationId;
        }

        public String getSharedKey() {
            return sharedKey;
        }

        public String describeSource() {
            return String.format("metadata from %s", sourcePath);
        }
    }
}
