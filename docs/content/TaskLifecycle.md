# The task lifecycle

When batect runs a task, there are a number of steps that it follows:

First, it determines which tasks to run and what order to run them in, based on the prerequisites of the requested task and any prerequisites of those prerequisites and so on.

Next, for each task, it determines what containers need to be started for that task, based on the dependency relationships between containers.

Then, it then creates a Docker network for the task, and runs [cache initialisation](tips/Performance.md) if required.

In parallel to setting up the network and caches, for every container that makes up the task:

1. It builds or pulls the image required for this container
2. It waits for the task network to be ready
3. It waits for cache initialisation to complete, if this container mounts any caches
4. It waits for any containers that this container depends on to be ready - so each dependency must have reported as healthy and completed all setup commands
5. It starts the container and the container's command
6. It waits for the container to report as [healthy](tips/WaitingForDependenciesToBeReady.md)
7. It runs any [setup commands](config/Containers.md#setup_commands), one at a time in the order provided

Once all setup commands have completed, any dependent containers can start.

Once the main container exits, batect then cleans up the containers. This means that, for every container:

1. It waits for all containers that depend on this one to stop
2. It stops this container
3. It removes this container
4. It cleans up any temporary files or folders created for this container

Once all containers are removed, the task network is removed and then the task is complete.
