# Windows

## Containers can't see contents of mounted directories

!!! tip "tl;dr"
    If containers can't see the contents of mounted directories, make sure the credentials Docker is using to access them are up-to-date.

Due to [an issue in Docker for Windows](https://github.com/docker/for-win/issues/25#issuecomment-381409350), if your Windows account credentials change
(eg. because your password expires and you change it), Docker does not detect the change and does not show an error message when it later goes to use these
credentials to mount directories into containers.

The solution is to update the credentials Docker for Windows has stored - right-click on the Docker icon in the notification area, choose Settings, go to Shared Drives
and click 'Reset credentials'.
