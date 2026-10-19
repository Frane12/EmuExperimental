# DRNAS r64 private native Windows ARM64 build environment.
# Import VS2026 vcvarsall arm64 into the current PowerShell process (no spoofed VSCMD_VER).
$ErrorActionPreference = 'Stop'
$arch=[System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
if ($arch -ne 'Arm64') { throw "Native ARM64 runner required; got $arch" }
$vswhere=Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if (!(Test-Path -LiteralPath $vswhere)) { throw 'VS installer discovery missing' }
$inst=@(& $vswhere -latest -version '[18.0,19.0)' -products '*' -property installationPath)
if ($LASTEXITCODE -ne 0 -or !$inst) { throw 'Visual Studio 2026 missing' }
$vcvars=Join-Path $inst[0].Trim() 'VC\Auxiliary\Build\vcvarsall.bat'
if (!(Test-Path -LiteralPath $vcvars)) { throw "vcvarsall.bat missing: $vcvars" }
$command='call "' + $vcvars + '" arm64 >nul && set'
$lines=@(& cmd.exe /d /s /c $command)
if ($LASTEXITCODE -ne 0) { throw 'vcvarsall arm64 failed' }
$imported=0
foreach($line in $lines) {
  $idx=$line.IndexOf('=')
  if ($idx -le 0) { continue }
  $key=$line.Substring(0,$idx)
  if ($key.StartsWith('=')) { continue }
  [Environment]::SetEnvironmentVariable($key,$line.Substring($idx+1),'Process')
  $imported++
}
if ($imported -lt 25) { throw "Only $imported environment variables imported from vcvarsall" }
if ($env:VSCMD_ARG_TGT_ARCH -ne 'arm64') { throw "Wrong VS target arch: $env:VSCMD_ARG_TGT_ARCH" }
if ($env:VSCMD_ARG_HOST_ARCH -ne 'arm64') { throw "Wrong VS host arch: $env:VSCMD_ARG_HOST_ARCH" }
if (!$env:VSCMD_VER) { throw 'Missing real VS VSCMD_VER from vcvarsall' }
Write-Host "Native ARM64 VS2026 environment imported ($imported vars); VSCMD_VER=$env:VSCMD_VER"
