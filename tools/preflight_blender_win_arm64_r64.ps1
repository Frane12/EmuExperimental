# Read-only Windows ARM64 build readiness check for Blender 4.5.14 r64.
# NEVER downloads, configures, compiles, installs or packages anything.
param(
  [Parameter(Mandatory=$true)]
  [string] $BlenderSource,
  [switch] $RequireLibraries
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function Check-Result([string]$Label, [bool]$Ok, [string]$Detail) {
  if ($Ok) {
    Write-Host "[OK] $Label - $Detail" -ForegroundColor Green
  } else {
    Write-Host "[BLOCKED] $Label - $Detail" -ForegroundColor Yellow
    $script:blocked = $true
  }
}
$script:blocked = $false
$source = (Resolve-Path -LiteralPath $BlenderSource -ErrorAction Stop).Path
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$patch = Join-Path $repoRoot 'patches\blender-4.5.14-mali-vulkan-optional-features-r64.patch'
Write-Host "DRNAS Blender v4.5.14 ARM64 toolchain preflight (read-only)"
Write-Host "Source: $source"
Write-Host "DRNAS:  $repoRoot"

$git = Get-Command git.exe -ErrorAction SilentlyContinue
$cmake = Get-Command cmake.exe -ErrorAction SilentlyContinue
Check-Result 'Git' ($null -ne $git) 'git.exe must be installed'
Check-Result 'CMake' ($null -ne $cmake) 'cmake.exe must be installed'
if ($null -ne $cmake) { & $cmake.Source --version | Select-Object -First 1 }
if ($null -eq $git) { throw "Cannot verify exact Blender source without git.exe" }

$tag = (& $git.Source -C $source rev-parse 'refs/tags/v4.5.14^{commit}' 2>$null | Select-Object -First 1)
$head = (& $git.Source -C $source rev-parse HEAD 2>$null | Select-Object -First 1)
Check-Result 'Blender upstream tag' (![string]::IsNullOrWhiteSpace($tag) -and $head -eq $tag) "HEAD=$head (tag=$tag)"
$dirty = @(& $git.Source -C $source status --porcelain)
Check-Result 'Clean source checkout' ($dirty.Count -eq 0) 'run preflight before applying patch; avoid untracked build files'
Check-Result 'Source patch exists' (Test-Path -LiteralPath $patch -PathType Leaf) $patch
if ((Test-Path -LiteralPath $patch) -and $head -eq $tag -and $dirty.Count -eq 0) {
  & $git.Source -C $source apply --check -- $patch
  $patchApplied = $?
  Check-Result 'git apply --check' $patchApplied 'no files modified'
}

# Only check a full VS2022 installation with native Windows ARM64 compiler assets.
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$vsPath = $null
if (Test-Path $vswhere) {
  $vsPath = (& $vswhere -latest -products '*' -version '[17.0,18.0)' -property installationPath | Select-Object -First 1)
}
Check-Result 'Visual Studio 2022' (![string]::IsNullOrWhiteSpace($vsPath)) 'VS2022 Community/Build Tools with C++ and ARM64 components'
if ($vsPath) {
  $armCl = @(Get-ChildItem -Path (Join-Path $vsPath 'VC\Tools\MSVC') -Recurse -Filter cl.exe -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match 'bin\\Hostx64\\arm64\\cl\.exe$' })
  Check-Result 'MSVC ARM64 targeting files' ($armCl.Count -gt 0) 'VC Tools ARM64 (linker/lib/sdk support for ClangCl)'
  $vsDev = Join-Path $vsPath 'Common7\Tools\VsDevCmd.bat'
  Check-Result 'VS developer environment' (Test-Path -LiteralPath $vsDev) 'VsDevCmd.bat; use x64 host tools'
}
$clang = Get-Command clang-cl.exe -ErrorAction SilentlyContinue
if ($null -eq $clang -and $vsPath) {
  $candidate = Join-Path $vsPath 'VC\Tools\Llvm\x64\bin\clang-cl.exe'
  if (Test-Path -LiteralPath $candidate) {
    $clang = @{ Source = $candidate }
  }
}
if ($null -eq $clang) {
  $llvmCandidate = Join-Path $env:ProgramFiles 'LLVM\bin\clang-cl.exe'
  if (Test-Path -LiteralPath $llvmCandidate) { $clang = @{ Source = $llvmCandidate } }
}
$clangVer = $null
if ($null -ne $clang) {
  $out = (& $clang.Source --version | Select-Object -First 1)
  Write-Host "Clang: $out"
  $match = [regex]::Match($out, '(?:clang version |version )([0-9]+\.[0-9]+(?:\.[0-9]+)?)')
  if ($match.Success) { $clangVer = [version]$match.Groups[1].Value }
}
Check-Result 'ClangCl version' ($null -ne $clangVer -and $clangVer -ge [version]'20.1.0') 'LLVM/ClangCl >=20.1.0 recommended for reproducible Blender 4.5 Windows ARM64'
$sdkRoot = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\Lib'
$sdkLibs = @()
if (Test-Path -LiteralPath $sdkRoot) {
  $sdkLibs = @(Get-ChildItem $sdkRoot -Directory | Where-Object {
    (Test-Path (Join-Path $_.FullName 'um\arm64')) -and
    (Test-Path (Join-Path $_.FullName 'ucrt\arm64'))
  })
}
Check-Result 'Windows SDK ARM64' ($sdkLibs.Count -gt 0) 'Windows 10/11 SDK with ARM64 UCRT and UM libraries'
$libs = Join-Path $source 'lib\windows_arm64'
$haveLibs = Test-Path -LiteralPath $libs -PathType Container
if ($RequireLibraries) {
  Check-Result 'Blender windows_arm64 dependencies' $haveLibs 'download exact upstream dependency submodule via make.bat update (several GB)'
} else {
  Write-Host ("[INFO] Blender windows_arm64 dependencies: " + $(if ($haveLibs) {'found'} else {'not yet staged; expected during a future dependency setup'}))
}
Write-Host ""
Write-Host "A future approved build would use, from a VS x64 Developer Prompt:"
Write-Host "  cd $source"
Write-Host "  git apply `"$patch`""
Write-Host "  make.bat arm64 2022 clang full nobuild  [CMake configure only]"
Write-Host "  make.bat arm64 2022 clang full          [COMPILE/INSTALL - requires explicit approval]"
Write-Host ""
if ($script:blocked) { throw "Preflight BLOCKED. No source/build/APK files were changed." }
Write-Host "Preflight READY for toolchain preparation. No compilation was performed." -ForegroundColor Green
