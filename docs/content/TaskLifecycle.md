# The task lifecycle

When batect runs a task, there are a number of steps that it follows:

First, it determines which tasks to run and what order to run them in, based on the prerequisites of the requested task and any prerequisites of those prerequisites and so on.

Next, for each task, it determines what containers need to be started for that task, based on the dependency relationships between containers.

Then, for every container that makes up the task:

1. It builds or pulls the image required for that container
2. It waits for any containers that this container depends on to be ready - so each dependency must have reported as healthy and completed all setup commands
3. It starts the container and the container's command
4. It waits for the container to report as [healthy](tips/WaitingForDependenciesToBeReady.md)
5. It runs any [setup commands](config/Containers.md#setup_commands), one at a time in the order provided

Once all setup commands have completed, any dependent containers can start.

Once the main container exits, batect then cleans up the containers. This means that, for every container:

1. It waits for all containers that depend on this one to stop
2. It stops this container
3. It removes this container
4. It cleans up any temporary files or folders created for this container
