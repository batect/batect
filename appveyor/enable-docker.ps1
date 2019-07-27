# Based on https://github.com/appveyor/ci/blob/master/scripts/docker-appveyor.psm1, adapted to use C: instead of D:

Remove-SmbShare -Name C -ErrorAction SilentlyContinue -Force
$deUsername = 'DockerExchange'
$dePsw = "ABC" + [guid]::NewGuid().ToString() + "!"
$secDePsw = ConvertTo-SecureString $dePsw -AsPlainText -Force
Get-LocalUser -Name $deUsername | Set-LocalUser -Password $secDePsw
& $env:ProgramFiles\Docker\Docker\DockerCli.exe -Start --testftw!928374kasljf039 >$null 2>&1
& $env:ProgramFiles\Docker\Docker\DockerCli.exe -Mount=C -Username="$env:computername\$deUsername" -Password="$dePsw" --testftw!928374kasljf039 >$null 2>&1
Disable-NetFirewallRule -DisplayGroup "File and Printer Sharing" -Direction Inbound
