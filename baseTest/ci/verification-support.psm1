Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-TestkitSourceFingerprint {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$WorkspaceRoot)

    $sourceRoot = [System.IO.Path]::GetFullPath($WorkspaceRoot)
    $sourceFiles = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
    foreach ($sourceName in @('pom.xml', '.editorconfig', 'mvnw', 'mvnw.cmd', 'verify-framework.ps1', 'verify-lifecycle.ps1')) {
        $sourcePath = Join-Path $sourceRoot $sourceName
        if (Test-Path -LiteralPath $sourcePath -PathType Leaf) { $sourceFiles.Add((Get-Item -LiteralPath $sourcePath)) }
    }
    foreach ($sourceModule in @('testkit-parent', 'testkit-bom', 'base-test-toolkit', 'enterprise-adapters', 'java-api-lab', 'enterprise-team-example')) {
        $sourcePom = Join-Path $sourceRoot "$sourceModule/pom.xml"
        if (Test-Path -LiteralPath $sourcePom -PathType Leaf) { $sourceFiles.Add((Get-Item -LiteralPath $sourcePom)) }
        $sourceDirectory = Join-Path $sourceRoot "$sourceModule/src"
        if (Test-Path -LiteralPath $sourceDirectory -PathType Container) {
            foreach ($sourceFile in Get-ChildItem -LiteralPath $sourceDirectory -Recurse -File) { $sourceFiles.Add($sourceFile) }
        }
    }
    foreach ($sourceDirectoryName in @('ci', 'config', '.mvn')) {
        $sourceDirectory = Join-Path $sourceRoot $sourceDirectoryName
        if (Test-Path -LiteralPath $sourceDirectory -PathType Container) {
            foreach ($sourceFile in Get-ChildItem -LiteralPath $sourceDirectory -Recurse -File) { $sourceFiles.Add($sourceFile) }
        }
    }
    $sourceEntries = @($sourceFiles | ForEach-Object {
        $sourceRelativePath = [System.IO.Path]::GetRelativePath($sourceRoot, $_.FullName).Replace('\', '/')
        $sourceHash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$sourceRelativePath`:$sourceHash"
    })
    [System.Array]::Sort($sourceEntries, [System.StringComparer]::Ordinal)
    $sourceBytes = [System.Text.Encoding]::UTF8.GetBytes(($sourceEntries -join "`n"))
    $sourceDigest = [System.Security.Cryptography.SHA256]::HashData($sourceBytes)
    [System.Convert]::ToHexString($sourceDigest).ToLowerInvariant()
}

function New-TestkitArtifactRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$WorkspaceRoot,
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Kind
    )
    $artifactItem = Get-Item -LiteralPath $Path
    [ordered]@{
        kind = $Kind
        path = [System.IO.Path]::GetRelativePath($WorkspaceRoot, $artifactItem.FullName).Replace('\', '/')
        sha256 = (Get-FileHash -LiteralPath $artifactItem.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        sizeBytes = $artifactItem.Length
    }
}

function Assert-TestkitFullVerificationOptions {
    [CmdletBinding()]
    param([AllowNull()][AllowEmptyString()][string[]]$ArgumentSources = @())

    foreach ($argumentSource in $ArgumentSources) {
        if ($argumentSource -match '(?i)(skipTests|maven\.test\.skip|skipITs|spotless\.skip|cyclonedx\.skip|(?:^|\s)-(?:P|pl|rf|am|amd)\S*|--(?:projects|resume-from|also-make)\b|(?:^|\s)-D(?:test|groups|excludedGroups|surefire\.|testng\.|testkit\.))') {
            throw 'Inherited test selection or verification bypass options are not allowed; use Maven directly for focused runs'
        }
    }
}

Export-ModuleMember -Function Get-TestkitSourceFingerprint, New-TestkitArtifactRecord, Assert-TestkitFullVerificationOptions
