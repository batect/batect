/*
   Copyright 2017-2021 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.docker

import kotlinx.serialization.Serializable

// This list is based on http://man7.org/linux/man-pages/man7/capabilities.7.html.
@Serializable
enum class Capability {
    AUDIT_CONTROL,
    AUDIT_READ,
    AUDIT_WRITE,
    BLOCK_SUSPEND,
    CHOWN,
    DAC_OVERRIDE,
    DAC_READ_SEARCH,
    FOWNER,
    FSETID,
    IPC_LOCK,
    IPC_OWNER,
    KILL,
    LEASE,
    LINUX_IMMUTABLE,
    MAC_ADMIN,
    MAC_OVERRIDE,
    MKNOD,
    NET_ADMIN,
    NET_BIND_SERVICE,
    NET_BROADCAST,
    NET_RAW,
    SETGID,
    SETFCAP,
    SETPCAP,
    SETUID,
    SYS_ADMIN,
    SYS_BOOT,
    SYS_CHROOT,
    SYS_MODULE,
    SYS_NICE,
    SYS_PACCT,
    SYS_PTRACE,
    SYS_RAWIO,
    SYS_RESOURCE,
    SYS_TIME,
    SYS_TTY_CONFIG,
    SYSLOG,
    WAKE_ALARM,
    ALL;
}
