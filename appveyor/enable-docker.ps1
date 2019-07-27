# Based on https://github.com/appveyor/ci/blob/master/scripts/docker-appveyor.psm1, adapted to use C: instead of D:

Remove-SmbShare -Name C -ErrorAction SilentlyContinue -Force
$password = "ABC" + [guid]::NewGuid().ToString() + "!"
$securePassword = ConvertTo-SecureString $password -AsPlainText -Force
Get-LocalUser -Name $env:UserName | Set-LocalUser -Password $securePassword
& $env:ProgramFiles\Docker\Docker\DockerCli.exe -Start --testftw!928374kasljf039 >$null 2>&1
& $env:ProgramFiles\Docker\Docker\DockerCli.exe -Mount=C -Username="$env:computername\$env:UserName" -Password="$password" --testftw!928374kasljf039 >$null 2>&1
Disable-NetFirewallRule -DisplayGroup "File and Printer Sharing" -Direction Inbound
