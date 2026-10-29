package com.personalrecords.api.backup;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.h2.tools.RunScript;

public final class DatabaseBackupOperations {

    private static final String FILE_URL_PREFIX = "jdbc:h2:file:";
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final Pattern IF_EXISTS = Pattern.compile("(?i)(?:^|;)IFEXISTS=([^;]+)");
    private static final Pattern CIPHER = Pattern.compile("(?i)(?:^|;)CIPHER=");
    private static final Pattern HMAC_SIGNATURE = Pattern.compile("[0-9a-f]{64}");
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_HMAC_KEY_BYTES = 32;
    private static final long MINIMUM_FREE_SPACE = 10L * 1024 * 1024;
    private static final long EXTRA_FREE_SPACE = 1024L * 1024;

    private DatabaseBackupOperations() {
    }

    public static Path createBackup(
            String sourceUrl, String username, String password, Path backupDirectory, String hmacKey)
            throws Exception {
        byte[] key = decodeHmacKey(hmacKey);
        Path sourceFile = databaseFile(sourceUrl);
        Path sourceDataFile = databaseDataFile(sourceFile);
        if (!Files.isRegularFile(sourceDataFile)) {
            throw new IOException("The configured H2 database file does not exist.");
        }
        Path normalizedBackupDirectory = backupDirectory.toAbsolutePath().normalize();
        if (normalizedBackupDirectory.startsWith(sourceFile.getParent())) {
            throw new IOException("The backup directory must be outside the active database directory.");
        }
        Files.createDirectories(normalizedBackupDirectory);
        checkAvailableSpace(sourceDataFile, normalizedBackupDirectory);

        Path partial = Files.createTempFile(normalizedBackupDirectory, "personalrecords-backup-", ".partial.sql");
        Path destination = normalizedBackupDirectory.resolve(backupFilename());
        Path partialSignature = Path.of(partial + ".hmac");
        Path signatureDestination = Path.of(destination + ".hmac");
        boolean signaturePublished = false;
        boolean backupPublished = false;
        try {
            try (Connection connection = DriverManager.getConnection(
                    existingDatabaseUrl(sourceUrl), username, password);
                    Statement statement = connection.createStatement()) {
                String scriptPath = partial.toString().replace("'", "''");
                statement.execute("SCRIPT TO '" + scriptPath + "'");
            }
            writeSignature(partial, partialSignature, key);
            validateBackup(partial, hmacKey);
            publishWithoutOverwrite(partialSignature, signatureDestination);
            signaturePublished = true;
            publishWithoutOverwrite(partial, destination);
            backupPublished = true;
            return destination;
        } catch (IOException | RuntimeException exception) {
            if (signaturePublished && !backupPublished) {
                Files.deleteIfExists(signatureDestination);
            }
            throw exception;
        } finally {
            Files.deleteIfExists(partial);
            Files.deleteIfExists(partialSignature);
        }
    }

    public static void validateBackup(Path backupFile, String hmacKey) throws IOException {
        byte[] key = decodeHmacKey(hmacKey);
        Path normalized = backupFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized) || Files.size(normalized) == 0) {
            throw new IOException("The selected backup is missing, unreadable, or empty.");
        }
        if (!normalized.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql")) {
            throw new IOException("The selected backup must be an H2 SQL script with a .sql extension.");
        }
        byte[] prefix;
        try (var input = Files.newInputStream(normalized)) {
            prefix = input.readNBytes(4096);
        }
        String header = new String(prefix, StandardCharsets.UTF_8);
        if (!header.startsWith("-- H2 ")) {
            throw new IOException("The selected file does not have the expected H2 SQL script header.");
        }
        verifySignature(normalized, key);
    }

    public static Path restoreToTemporaryDatabase(
            String sourceUrl,
            String backupUsername,
            String backupPassword,
            Path backupFile,
            String temporaryDatabaseUrl,
            String hmacKey) throws Exception {
        validateBackup(backupFile, hmacKey);
        Path sourceFile = databaseFile(sourceUrl);
        Path temporaryFile = databaseFile(temporaryDatabaseUrl);
        String temporaryName = temporaryFile.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!temporaryName.contains("restore") && !temporaryName.contains("recovery")) {
            throw new IOException("The temporary database name must contain 'restore' or 'recovery'.");
        }
        if (temporaryFile.startsWith(sourceFile.getParent())) {
            throw new IOException("The temporary database must be outside the active database directory.");
        }
        if (temporaryFile.equals(sourceFile)) {
            throw new IOException("Recovery into the active database is not allowed.");
        }
        if (hasDatabaseFiles(temporaryFile)) {
            throw new IOException("The temporary database already exists; it will not be overwritten.");
        }

        Files.createDirectories(temporaryFile.getParent());
        checkAvailableSpace(backupFile, temporaryFile.getParent());
        RunScript.execute(temporaryDatabaseUrl, backupUsername, backupPassword, backupFile.toAbsolutePath().toString(),
                StandardCharsets.UTF_8, false);
        Path restoredFile = Path.of(temporaryFile + ".mv.db");
        if (!Files.isRegularFile(restoredFile) || Files.size(restoredFile) == 0) {
            throw new IOException("The H2 script completed without producing a non-empty temporary database.");
        }
        return restoredFile;
    }

    static Path databaseFile(String url) throws IOException {
        if (url == null || !url.regionMatches(true, 0, FILE_URL_PREFIX, 0, FILE_URL_PREFIX.length())) {
            throw new IOException("Only embedded file-based H2 databases are supported; memory and server URLs are refused.");
        }
        if (CIPHER.matcher(url).find()) {
            throw new IOException("Encrypted H2 URLs require an operator-specific backup procedure and are refused.");
        }
        String location = url.substring(FILE_URL_PREFIX.length());
        int optionsStart = location.indexOf(';');
        if (optionsStart >= 0) {
            location = location.substring(0, optionsStart);
        }
        if (location.isBlank()) {
            throw new IOException("The H2 file database URL does not include a database path.");
        }
        if (location.startsWith("~/") || location.startsWith("~\\")) {
            location = System.getProperty("user.home") + location.substring(1);
        }
        Path file = Path.of(location);
        if (!file.isAbsolute()) {
            file = Path.of(System.getProperty("user.dir")).resolve(file);
        }
        return file.toAbsolutePath().normalize();
    }

    static String backupFilename() {
        return "personalrecords-backup-" + FILE_TIMESTAMP.format(Instant.now()) + "-"
                + UUID.randomUUID().toString().replace("-", "") + ".sql";
    }

    static void publishWithoutOverwrite(Path source, Path destination) throws IOException {
        if (Files.exists(destination)) {
            throw new IOException("The destination already exists; existing backups are never overwritten.");
        }
        Files.move(source, destination);
    }

    private static byte[] decodeHmacKey(String base64Key) throws IOException {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IOException("Set PERSONAL_RECORDS_BACKUP_HMAC_KEY to a secret Base64 key before using backups.");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException exception) {
            throw new IOException("PERSONAL_RECORDS_BACKUP_HMAC_KEY must contain a valid Base64 key.", exception);
        }
        if (key.length < MINIMUM_HMAC_KEY_BYTES) {
            throw new IOException("The backup integrity key must decode to at least 32 bytes.");
        }
        return key;
    }

    private static void writeSignature(Path backupFile, Path signatureFile, byte[] key) throws IOException {
        String signature = calculateSignature(backupFile, key);
        Files.writeString(signatureFile, signature, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
    }

    private static void verifySignature(Path backupFile, byte[] key) throws IOException {
        Path signatureFile = Path.of(backupFile + ".hmac");
        if (!Files.isRegularFile(signatureFile)
                || !Files.isReadable(signatureFile)
                || Files.size(signatureFile) != 64) {
            throw new IOException("The backup is missing its valid HMAC signature file.");
        }
        String suppliedSignature = Files.readString(signatureFile, StandardCharsets.US_ASCII);
        if (!HMAC_SIGNATURE.matcher(suppliedSignature).matches()) {
            throw new IOException("The backup HMAC signature file has an invalid format.");
        }
        byte[] expected = HexFormat.of().parseHex(calculateSignature(backupFile, key));
        byte[] supplied = HexFormat.of().parseHex(suppliedSignature);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new IOException("The backup HMAC signature does not match; the backup is untrusted or modified.");
        }
    }

    private static String calculateSignature(Path backupFile, byte[] key) throws IOException {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            try (InputStream input = Files.newInputStream(backupFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    mac.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException exception) {
            throw new IOException("HMAC-SHA-256 is unavailable; the backup could not be authenticated.", exception);
        }
    }

    private static String existingDatabaseUrl(String url) throws IOException {
        Matcher matcher = IF_EXISTS.matcher(url);
        if (matcher.find()) {
            if (!"TRUE".equalsIgnoreCase(matcher.group(1))) {
                throw new IOException("The source URL must not disable H2's IFEXISTS protection.");
            }
            return url;
        }
        return url + ";IFEXISTS=TRUE";
    }

    private static void checkAvailableSpace(Path sourceFile, Path backupDirectory) throws IOException {
        FileStore fileStore = Files.getFileStore(backupDirectory);
        long sourceSize = Files.size(sourceFile);
        long estimatedRequired = sourceSize > (Long.MAX_VALUE - EXTRA_FREE_SPACE) / 2
                ? Long.MAX_VALUE
                : Math.max(MINIMUM_FREE_SPACE, sourceSize * 2 + EXTRA_FREE_SPACE);
        if (fileStore.getUsableSpace() < estimatedRequired) {
            throw new IOException("There may not be enough free space for an SQL backup (estimated "
                    + estimatedRequired + " bytes required).");
        }
    }

    private static boolean hasDatabaseFiles(Path baseFile) {
        return Files.exists(Path.of(baseFile + ".mv.db"))
                || Files.exists(Path.of(baseFile + ".trace.db"))
                || Files.exists(Path.of(baseFile + ".lock.db"))
                || Files.exists(Path.of(baseFile + ".temp.db"));
    }

    private static Path databaseDataFile(Path baseFile) {
        return Path.of(baseFile + ".mv.db");
    }
}
