# Based on https://github.com/appveyor/ci/blob/master/scripts/docker-appveyor.psm1

Remove-SmbShare -Name C -ErrorAction SilentlyContinue -Force
Remove-SmbShare -Name D -ErrorAction SilentlyContinue -Force
$password = [Microsoft.Win32.Registry]::GetValue("HKEY_LOCAL_MACHINE\Software\Microsoft\Windows NT\CurrentVersion\Winlogon", "DefaultPassword", '')

Write-Output "Starting Docker..."
& $env:ProgramFiles\Docker\Docker\DockerCli.exe -Start --testftw!928374kasljf039 >$null 2>&1

Write-Output "Configuring sharing..."
& $env:ProgramFiles\Docker\Docker\DockerCli.exe -Mount=C -Username="$env:computername\$env:UserName" -Password="$password" --testftw!928374kasljf039 >$null 2>&1
& $env:ProgramFiles\Docker\Docker\DockerCli.exe -Mount=D -Username="$env:computername\$env:UserName" -Password="$password" --testftw!928374kasljf039 >$null 2>&1
Disable-NetFirewallRule -DisplayGroup "File and Printer Sharing" -Direction Inbound

Write-Output "Starting Docker desktop app..."
Start-Process "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
