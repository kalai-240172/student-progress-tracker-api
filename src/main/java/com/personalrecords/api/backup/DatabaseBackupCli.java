package com.personalrecords.api.backup;

import java.nio.file.Path;

public final class DatabaseBackupCli {

    private DatabaseBackupCli() {
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                throw new IllegalArgumentException("An operation is required.");
            }
            String sourceUrl = envOrDefault(
                    "PERSONAL_RECORDS_DB_URL",
                    "jdbc:h2:file:" + Path.of(System.getProperty("user.home"), ".personal-records",
                            "personalrecords").toAbsolutePath().normalize()
                            + ";DB_CLOSE_ON_EXIT=FALSE");
            String username = envOrDefault("PERSONAL_RECORDS_DB_USERNAME", "sa");
            String password = envOrDefault("PERSONAL_RECORDS_DB_PASSWORD", "");
            String hmacKey = System.getenv("PERSONAL_RECORDS_BACKUP_HMAC_KEY");

            switch (args[0]) {
                case "backup" -> {
                    if (args.length != 2) {
                        throw new IllegalArgumentException("Backup requires a destination directory.");
                    }
                    Path backup = DatabaseBackupOperations.createBackup(
                            sourceUrl, username, password, Path.of(args[1]), hmacKey);
                    System.out.println("Signed backup created and verified: " + backup);
                }
                case "validate" -> {
                    if (args.length != 2) {
                        throw new IllegalArgumentException("Validation requires a backup file.");
                    }
                    DatabaseBackupOperations.validateBackup(Path.of(args[1]), hmacKey);
                    System.out.println("H2 SQL backup format and HMAC signature are valid.");
                }
                case "restore-to-temporary" -> {
                    if (args.length != 3) {
                        throw new IllegalArgumentException(
                                "Temporary recovery requires a backup file and a new file-based H2 URL.");
                    }
                    Path restoredFile = DatabaseBackupOperations.restoreToTemporaryDatabase(
                            sourceUrl, username, password, Path.of(args[1]), args[2], hmacKey);
                    System.out.println("Temporary recovery database created: " + restoredFile);
                    System.out.println("The configured source database was not modified.");
                }
                default -> throw new IllegalArgumentException("Unsupported operation.");
            }
        } catch (Exception exception) {
            String reason = exception instanceof java.io.IOException || exception instanceof IllegalArgumentException
                    ? exception.getMessage()
                    : "Check that the application is stopped and the configured H2 URL and credentials are correct ("
                            + exception.getClass().getSimpleName() + ").";
            System.err.println("Database backup/recovery failed: " + reason
                    + " The configured source database is never overwritten.");
            System.exit(1);
        }
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }
}
