package com.personalrecords.api.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseBackupOperationsTests {

    private static final String HMAC_KEY = Base64.getEncoder().encodeToString(
            "test-only-hmac-key-with-at-least-thirty-two-bytes".getBytes(StandardCharsets.UTF_8));
    private static final String WRONG_HMAC_KEY = Base64.getEncoder().encodeToString(
            "different-test-key-with-at-least-thirty-two-bytes".getBytes(StandardCharsets.UTF_8));

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsUniqueVerifiedSqlBackupAndRestoresItToANewDatabase() throws Exception {
        Path sourceBase = temporaryDirectory.resolve("active").resolve("personalrecords");
        Files.createDirectories(sourceBase.getParent());
        String sourceUrl = "jdbc:h2:file:" + sourceBase + ";DB_CLOSE_ON_EXIT=FALSE";
        try (var connection = DriverManager.getConnection(sourceUrl, "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE backup_probe (id INTEGER PRIMARY KEY, detail VARCHAR(40))");
            statement.execute("INSERT INTO backup_probe VALUES (7, 'café study 📚')");
        }

        Path backupDirectory = temporaryDirectory.resolve("backups");
        Path backup = DatabaseBackupOperations.createBackup(sourceUrl, "sa", "", backupDirectory, HMAC_KEY);
        Path secondBackup = DatabaseBackupOperations.createBackup(sourceUrl, "sa", "", backupDirectory, HMAC_KEY);

        assertTrue(Pattern.matches(
                "personalrecords-backup-\\d{8}T\\d{6}\\.\\d{3}Z-[0-9a-f]{32}\\.sql",
                backup.getFileName().toString()));
        assertNotEquals(backup, secondBackup);
        assertTrue(Files.size(backup) > 0);
        assertTrue(Files.isRegularFile(Path.of(backup + ".hmac")));
        DatabaseBackupOperations.validateBackup(backup, HMAC_KEY);

        Path restoredBase = temporaryDirectory.resolve("recovery").resolve("personalrecords-restore-check");
        assertThrows(IOException.class, () -> DatabaseBackupOperations.restoreToTemporaryDatabase(
                sourceUrl, "sa", "", backup, "jdbc:h2:file:" + restoredBase + ";DB_CLOSE_ON_EXIT=FALSE",
                WRONG_HMAC_KEY));
        assertFalse(Files.exists(Path.of(restoredBase + ".mv.db")));
        Path restoredFile = DatabaseBackupOperations.restoreToTemporaryDatabase(
                sourceUrl, "sa", "", backup, "jdbc:h2:file:" + restoredBase + ";DB_CLOSE_ON_EXIT=FALSE", HMAC_KEY);
        assertTrue(Files.isRegularFile(restoredFile));
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:file:" + restoredBase + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "");
                var result = connection.createStatement().executeQuery("SELECT detail FROM backup_probe WHERE id = 7")) {
            assertTrue(result.next());
            assertEquals("café study 📚", result.getString(1));
        }
        assertTrue(Files.isRegularFile(Path.of(sourceBase + ".mv.db")));
    }

    @Test
    void restoresConfiguredH2CredentialsFromTheScriptWithoutPassingThemToTheNewDatabase() throws Exception {
        Path sourceBase = temporaryDirectory.resolve("credential-source").resolve("personalrecords");
        Files.createDirectories(sourceBase.getParent());
        String sourceUrl = "jdbc:h2:file:" + sourceBase + ";DB_CLOSE_ON_EXIT=FALSE";
        try (var connection = DriverManager.getConnection(sourceUrl, "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("ALTER USER SA SET PASSWORD 'unit-test-password'");
            }
        }
        try (var connection = DriverManager.getConnection(sourceUrl, "sa", "unit-test-password")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE credential_probe (id INTEGER PRIMARY KEY)");
                statement.execute("INSERT INTO credential_probe VALUES (19)");
            }
        }

        Path backup = DatabaseBackupOperations.createBackup(
                sourceUrl, "sa", "unit-test-password", temporaryDirectory.resolve("credential-backups"), HMAC_KEY);
        Path restoreBase = temporaryDirectory.resolve("credential-recovery")
                .resolve("personalrecords-restore-credentials");
        DatabaseBackupOperations.restoreToTemporaryDatabase(
                sourceUrl, "sa", "unit-test-password", backup,
                "jdbc:h2:file:" + restoreBase + ";DB_CLOSE_ON_EXIT=FALSE", HMAC_KEY);

        try (var connection = DriverManager.getConnection(
                "jdbc:h2:file:" + restoreBase + ";DB_CLOSE_ON_EXIT=FALSE", "sa", "unit-test-password");
                var result = connection.createStatement().executeQuery("SELECT id FROM credential_probe")) {
            assertTrue(result.next());
            assertEquals(19, result.getInt(1));
        }
    }

    @Test
    void rejectsMissingSourceAndMemoryDatabaseUrlsWithoutCreatingSource() {
        Path missingBase = temporaryDirectory.resolve("missing").resolve("source");
        String missingUrl = "jdbc:h2:file:" + missingBase;

        assertThrows(IOException.class, () -> DatabaseBackupOperations.createBackup(
                missingUrl, "sa", "", temporaryDirectory.resolve("backups"), HMAC_KEY));
        assertFalse(Files.exists(Path.of(missingBase + ".mv.db")));
        assertThrows(IOException.class, () -> DatabaseBackupOperations.databaseFile(
                "jdbc:h2:mem:personalrecords-test"));
        assertThrows(IOException.class, () -> DatabaseBackupOperations.databaseFile(
                "jdbc:h2:file:" + missingBase + ";CIPHER=AES"));
    }

    @Test
    void refusesInvalidBackupAndNeverOverwritesAnExistingDestination() throws Exception {
        Path invalid = temporaryDirectory.resolve("invalid.sql");
        Files.writeString(invalid, "not an H2 script");
        assertThrows(IOException.class, () -> DatabaseBackupOperations.validateBackup(invalid, HMAC_KEY));

        Path staged = temporaryDirectory.resolve("staged.sql");
        Path destination = temporaryDirectory.resolve("existing.sql");
        Files.writeString(staged, "-- H2 2.4.240;\n");
        Files.writeString(destination, "preserve this file");

        assertThrows(IOException.class, () -> DatabaseBackupOperations.publishWithoutOverwrite(staged, destination));
        assertEquals("preserve this file", Files.readString(destination));
        assertTrue(Files.exists(staged));
    }

    @Test
    void refusesAnExistingRestoreTargetAndTheConfiguredSourceAsRecoveryDestination() throws Exception {
        Path sourceBase = temporaryDirectory.resolve("active").resolve("personalrecords");
        Files.createDirectories(sourceBase.getParent());
        String sourceUrl = "jdbc:h2:file:" + sourceBase;
        try (var connection = DriverManager.getConnection(sourceUrl, "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE guard_probe (id INTEGER)");
            }
        }
        Path backup = DatabaseBackupOperations.createBackup(
                sourceUrl, "sa", "", temporaryDirectory.resolve("backup-store"), HMAC_KEY);
        Path existingBase = temporaryDirectory.resolve("other").resolve("personalrecords-restore-existing");
        Files.createDirectories(existingBase.getParent());
        Path existingDatabase = Path.of(existingBase + ".mv.db");
        Files.write(existingDatabase, new byte[] {1, 2, 3});

        assertThrows(IOException.class, () -> DatabaseBackupOperations.restoreToTemporaryDatabase(
                sourceUrl, "sa", "", backup, "jdbc:h2:file:" + existingBase, HMAC_KEY));
        assertEquals(3, Files.size(existingDatabase));
        assertThrows(IOException.class, () -> DatabaseBackupOperations.restoreToTemporaryDatabase(
                sourceUrl, "sa", "", backup, sourceUrl, HMAC_KEY));
        assertTrue(Files.isRegularFile(Path.of(sourceBase + ".mv.db")));
    }

    @Test
    void rejectsMissingWrongAndTamperedBackupSignatures() throws Exception {
        Path sourceBase = temporaryDirectory.resolve("signed-source").resolve("personalrecords");
        Files.createDirectories(sourceBase.getParent());
        String sourceUrl = "jdbc:h2:file:" + sourceBase;
        try (var connection = DriverManager.getConnection(sourceUrl, "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE signed_probe (id INTEGER PRIMARY KEY)");
                statement.execute("INSERT INTO signed_probe VALUES (29)");
            }
        }

        Path backup = DatabaseBackupOperations.createBackup(
                sourceUrl, "sa", "", temporaryDirectory.resolve("signed-backups"), HMAC_KEY);
        Path signature = Path.of(backup + ".hmac");
        String originalScript = Files.readString(backup, StandardCharsets.UTF_8);
        String originalSignature = Files.readString(signature, StandardCharsets.US_ASCII);

        assertThrows(IOException.class, () -> DatabaseBackupOperations.validateBackup(backup, WRONG_HMAC_KEY));
        Files.writeString(backup, originalScript + "\n-- modified after signing\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> DatabaseBackupOperations.validateBackup(backup, HMAC_KEY));

        Files.writeString(backup, originalScript, StandardCharsets.UTF_8);
        Files.writeString(signature, originalSignature, StandardCharsets.US_ASCII);
        Files.delete(signature);
        assertThrows(IOException.class, () -> DatabaseBackupOperations.validateBackup(backup, HMAC_KEY));

        Path restoreBase = temporaryDirectory.resolve("signed-recovery").resolve("personalrecords-restore-guard");
        assertThrows(IOException.class, () -> DatabaseBackupOperations.restoreToTemporaryDatabase(
                sourceUrl, "sa", "", backup, "jdbc:h2:file:" + restoreBase, HMAC_KEY));
        assertFalse(Files.exists(Path.of(restoreBase + ".mv.db")));
    }

    @Test
    void rejectsWeakOrMalformedHmacKeys() throws Exception {
        Path backup = temporaryDirectory.resolve("invalid-key.sql");
        Files.writeString(backup, "-- H2 2.4.240;\n");
        assertThrows(IOException.class, () -> DatabaseBackupOperations.validateBackup(backup, null));
        assertThrows(IOException.class, () -> DatabaseBackupOperations.validateBackup(backup, "not-base64"));
        assertThrows(IOException.class, () -> DatabaseBackupOperations.validateBackup(
                backup, Base64.getEncoder().encodeToString(new byte[8])));
    }
}
