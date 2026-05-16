$ErrorActionPreference = "Stop"

$Root = (Get-Location).Path
$JavaRoot = Join-Path $Root "src/main/java"
$LangRoot = Join-Path $Root "src/main/resources/assets/wavedefense/lang"

$UsedKeys = [System.Collections.Generic.HashSet[string]]::new()
$LiteralFindings = [System.Collections.Generic.List[object]]::new()
$AllowedBlankKeys = [System.Collections.Generic.HashSet[string]]::new()
[void]$AllowedBlankKeys.Add("wavedefense.auto.text_da39a3ee")

Get-ChildItem -Path $JavaRoot -Recurse -Filter *.java | ForEach-Object {
    $File = $_.FullName
    $Text = Get-Content -LiteralPath $File -Raw -Encoding UTF8

    [regex]::Matches($Text, '(?:Component\.)?translatable\(\s*"([^"]+)"') | ForEach-Object {
        [void]$UsedKeys.Add($_.Groups[1].Value)
    }

    $Lines = $Text -split "\r?\n"
    for ($i = 0; $i -lt $Lines.Length; $i++) {
        $Line = $Lines[$i]
        if ($Line.Contains("Component.literal(") -or ($Line -match 'draw(?:Centered)?String\([^,\n]+,\s*"')) {
            $Relative = $File.Substring($Root.Length).TrimStart("\", "/").Replace("\", "/")
            $LiteralFindings.Add([pscustomobject]@{
                File = $Relative
                Line = $i + 1
                Text = $Line.Trim()
            })
        }
    }
}

$Failed = $false

Get-ChildItem -Path $LangRoot -Filter *.json | ForEach-Object {
    $File = $_.FullName
    $Json = Get-Content -LiteralPath $File -Raw -Encoding UTF8 | ConvertFrom-Json
    $Properties = @($Json.PSObject.Properties)
    $Names = [System.Collections.Generic.HashSet[string]]::new()
    $Properties | ForEach-Object { [void]$Names.Add($_.Name) }
    $Missing = @($UsedKeys | Where-Object { -not $Names.Contains($_) })
    $Empty = @($Properties | Where-Object { -not $AllowedBlankKeys.Contains($_.Name) -and $_.Value -is [string] -and $_.Value.Trim().Length -eq 0 } | ForEach-Object { $_.Name })

    Write-Host "$($_.Name): keys=$($Properties.Count), missing=$($Missing.Count), empty=$($Empty.Count)"

    if ($Missing.Count -gt 0 -or $Empty.Count -gt 0) {
        $Failed = $true
        $Missing | Select-Object -First 25 | ForEach-Object { Write-Host "  missing: $_" }
        $Empty | Select-Object -First 25 | ForEach-Object { Write-Host "  empty: $_" }
    }
}

Write-Host "used translatable keys: $($UsedKeys.Count)"
Write-Host "literal/raw text findings: $($LiteralFindings.Count)"

$LiteralFindings | Select-Object -First 80 | ForEach-Object {
    Write-Host "$($_.File):$($_.Line): $($_.Text)"
}

if ($Failed) { exit 1 }
