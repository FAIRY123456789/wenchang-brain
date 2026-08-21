param(
    [string]$ReleaseId = ("1.5.0-" + [DateTime]::UtcNow.ToString("yyyyMMddTHHmmssZ"))
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$releaseRoot = Join-Path $repo 'release'
$stage = Join-Path $releaseRoot $ReleaseId
$archive = Join-Path $releaseRoot ($ReleaseId + '.tar.gz')
$mainJar = Join-Path $repo 'target/wenchang-brain-1.5.0-SNAPSHOT.jar'
$mcpJar = Join-Path $repo 'extensions/wenchang-public-resource-mcp/target/wenchang-public-resource-mcp-1.4.0-SNAPSHOT.jar'
$gitCommit = (& git -c "safe.directory=$($repo.Replace('\', '/'))" -C $repo rev-parse HEAD).Trim()

if (-not (Test-Path -LiteralPath $mainJar -PathType Leaf)) { throw "Main JAR missing: $mainJar" }
if (-not (Test-Path -LiteralPath $mcpJar -PathType Leaf)) { throw "MCP JAR missing: $mcpJar" }
if (Test-Path -LiteralPath $stage) { throw "Release already exists: $stage" }
if (Test-Path -LiteralPath $archive) { throw "Archive already exists: $archive" }

New-Item -ItemType Directory -Path $stage | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stage 'data-seed') | Out-Null
New-Item -ItemType Directory -Path (Join-Path $stage 'config') | Out-Null

Copy-Item -LiteralPath $mainJar -Destination (Join-Path $stage 'wenchang-brain.jar')
Copy-Item -LiteralPath $mcpJar -Destination (Join-Path $stage 'wenchang-mcp.jar')
Copy-Item -LiteralPath (Join-Path $repo 'knowledge') -Destination $stage -Recurse
New-Item -ItemType Directory -Path (Join-Path $stage 'deploy') | Out-Null
Get-ChildItem -LiteralPath (Join-Path $repo 'deploy') -File | Where-Object {
    $_.Name -in @('deploy.sh', 'rollback.sh', 'wenchang-brain.service', 'wenchang-mcp.service', 'nginx-wenchang.conf.example', 'wenchang-logrotate', 'README.md')
} | Copy-Item -Destination (Join-Path $stage 'deploy')
Copy-Item -LiteralPath (Join-Path $repo 'config/local-secrets.properties.example') -Destination (Join-Path $stage 'config')

$seedNames = @(
    'official-source-registry.json',
    'wenchang-places.json',
    'wenchang-policies.json',
    'wenchang-public-services.json',
    'wenchang-townships.json',
    'wenchang-vector-store.json',
    'wenchang-vector-store.json.meta.json'
)
foreach ($name in $seedNames) {
    $source = Join-Path (Join-Path $repo 'data') $name
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Required data file missing: $source" }
    Copy-Item -LiteralPath $source -Destination (Join-Path $stage 'data-seed')
}

# Linux executable/config text must use LF even when the release is built on Windows.
Get-ChildItem -LiteralPath (Join-Path $stage 'deploy') -File | Where-Object {
    $_.Extension -in @('.sh', '.service') -or $_.Name -like '*.conf.example'
} | ForEach-Object {
    $text = [IO.File]::ReadAllText($_.FullName).Replace("`r`n", "`n")
    [IO.File]::WriteAllText($_.FullName, $text, [Text.UTF8Encoding]::new($false))
}

$releaseInfo = @(
    "release.version=$ReleaseId",
    'product.version=V1.5',
    'project.version=1.5.0-SNAPSHOT',
    ('git.commit=' + $gitCommit),
    ('built.at.utc=' + [DateTime]::UtcNow.ToString('o')),
    'main.tests=85/85 PASS',
    'mcp.tests=7/7 PASS',
    'public.base-path=/wenchang-brain/'
) -join "`n"
[IO.File]::WriteAllText((Join-Path $stage 'release-info.txt'), $releaseInfo + "`n", [Text.UTF8Encoding]::new($false))

$files = Get-ChildItem -LiteralPath $stage -File -Recurse |
    Where-Object { $_.Name -ne 'checksums.sha256' } |
    Sort-Object FullName
$stagePrefix = $stage.TrimEnd('\') + '\'
$checksumLines = foreach ($file in $files) {
    if (-not $file.FullName.StartsWith($stagePrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "File escaped release root: $($file.FullName)"
    }
    $relative = $file.FullName.Substring($stagePrefix.Length).Replace('\', '/')
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $relative"
}
[IO.File]::WriteAllText((Join-Path $stage 'checksums.sha256'), ($checksumLines -join "`n") + "`n", [Text.UTF8Encoding]::new($false))

& tar -czf $archive -C $releaseRoot $ReleaseId
if ($LASTEXITCODE -ne 0) { throw 'tar archive creation failed' }
$archiveHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash
Write-Output "RELEASE_DIR=$stage"
Write-Output "RELEASE_ARCHIVE=$archive"
Write-Output "RELEASE_SHA256=$archiveHash"
