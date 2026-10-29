param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Backup", "Validate", "RestoreToTemporary")]
    [string] $Action,
    [string] $BackupPath,
    [string] $BackupDirectory,
    [string] $TemporaryDatabaseUrl
)

$ErrorActionPreference = "Stop"
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$wrapper = Join-Path $repositoryRoot "mvnw.cmd"

if ([string]::IsNullOrWhiteSpace($env:PERSONAL_RECORDS_BACKUP_HMAC_KEY)) {
    throw "Set PERSONAL_RECORDS_BACKUP_HMAC_KEY to your secret Base64 key from a trusted secret store."
}

if ([string]::IsNullOrWhiteSpace($BackupDirectory)) {
    if (-not [string]::IsNullOrWhiteSpace($env:PERSONAL_RECORDS_BACKUP_DIR)) {
        $BackupDirectory = $env:PERSONAL_RECORDS_BACKUP_DIR
    } else {
        $BackupDirectory = Join-Path $HOME ".personal-records-backups"
    }
}
$BackupDirectory = [IO.Path]::GetFullPath($BackupDirectory)

function Assert-OutsideRepository([string] $Path, [string] $Description) {
    $resolvedPath = [IO.Path]::GetFullPath($Path).TrimEnd([IO.Path]::DirectorySeparatorChar) +
        [IO.Path]::DirectorySeparatorChar
    $resolvedRoot = [IO.Path]::GetFullPath($repositoryRoot).TrimEnd([IO.Path]::DirectorySeparatorChar) +
        [IO.Path]::DirectorySeparatorChar
    if ($resolvedPath.StartsWith($resolvedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Description must be outside the Git repository."
    }
}

if ($Action -eq "Backup") {
    Assert-OutsideRepository $BackupDirectory "Backup directory"
    New-Item -ItemType Directory -Path $BackupDirectory -Force | Out-Null
}

if ($Action -eq "Validate" -and [string]::IsNullOrWhiteSpace($BackupPath)) {
    throw "-BackupPath is required for validation."
}
if ($Action -eq "RestoreToTemporary") {
    if ([string]::IsNullOrWhiteSpace($BackupPath)) {
        throw "-BackupPath is required for recovery."
    }
    if ([string]::IsNullOrWhiteSpace($TemporaryDatabaseUrl)) {
        throw "-TemporaryDatabaseUrl is required; recovery never targets the configured database."
    }
    if (-not $TemporaryDatabaseUrl.StartsWith("jdbc:h2:file:", [StringComparison]::OrdinalIgnoreCase)) {
        throw "The temporary recovery URL must use embedded file-based H2, not a memory or server database."
    }
    $tempPath = ($TemporaryDatabaseUrl.Substring("jdbc:h2:file:".Length) -split ";", 2)[0]
    if (-not [IO.Path]::IsPathRooted($tempPath)) {
        $tempPath = Join-Path $repositoryRoot $tempPath
    }
    Assert-OutsideRepository $tempPath "Temporary database"
}

$classpathFile = Join-Path $env:TEMP ("personalrecords-classpath-" + [Guid]::NewGuid().ToString("N") + ".txt")
try {
    Push-Location $repositoryRoot
    try {
        & $wrapper -q -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=$classpathFile" "-Dmdep.includeScope=runtime"
        if ($LASTEXITCODE -ne 0) {
            throw "Maven could not compile the backup helper or resolve the project's H2 runtime."
        }
    } finally {
        Pop-Location
    }
    $dependencyClasspath = [IO.File]::ReadAllText($classpathFile).Trim()
    $javaClasspath = (Join-Path $repositoryRoot "target\classes") + [IO.Path]::PathSeparator + $dependencyClasspath
    $java = (Get-Command java.exe -ErrorAction Stop).Source

    switch ($Action) {
        "Backup" {
            & $java -cp $javaClasspath com.personalrecords.api.backup.DatabaseBackupCli backup $BackupDirectory
        }
        "Validate" {
            & $java -cp $javaClasspath com.personalrecords.api.backup.DatabaseBackupCli validate ([IO.Path]::GetFullPath($BackupPath))
        }
        "RestoreToTemporary" {
            & $java -cp $javaClasspath com.personalrecords.api.backup.DatabaseBackupCli restore-to-temporary ([IO.Path]::GetFullPath($BackupPath)) $TemporaryDatabaseUrl
        }
    }
    if ($LASTEXITCODE -ne 0) {
        throw "The database backup/recovery operation failed. Review the safe error above."
    }
} finally {
    if (Test-Path -LiteralPath $classpathFile) {
        Remove-Item -LiteralPath $classpathFile -Force
    }
}
