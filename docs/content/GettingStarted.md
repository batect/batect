# Getting started tutorial

In this tutorial, we'll create a TypeScript-based API that makes calls to another API and uses Yarn to manage NPM packages, and use
batect to manage our development environment and tasks.

This tutorial is based on the [TypeScript sample project](https://github.com/batect/batect-sample-typescript), and assumes some
basic familiarity with Docker and Yarn.

It should take 15-20 minutes to complete.

## Installation

Before you begin, follow the [setup instructions](Setup.md) to setup batect.

## First task

Let's start by defining our very first task. As is tradition, we'll be creating a "hello world" task.

In batect, there are two major concepts:

* **Tasks** define commands to run and how to run them - for example, building your application, running tests or deploying your
  application.

* **Containers** define the environment tasks run in - for example, the Docker image used, the folders mounted from your local
  machine and the ports exposed back to your local machine.

Both tasks and containers are defined in a YAML configuration file, normally called `batect.yml`. Let's start by defining our build
environment container, `build-env`:

```yaml
containers:
  build-env:
    image: node:14.3.0
```

`build-env` uses the publicly-available `node` image, and specifies the particular version of the image to use.

!!! tip
    It's a good idea to specify a particular image tag (eg. `14.3.0` in our example above) rather than using `latest` - this
    ensures everyone using your configuration runs the same image.

We can use that container to define our `hello-world` task:

```yaml hl_lines="5-10"
containers:
  build-env:
    image: node:14.3.0

tasks:
  hello-world:
    description: Say hello to everyone
    run:
      container: build-env
      command: echo 'Hello world!'
```

Then we can run our task with `./batect hello-world`:

```
Running hello-world...
build-env: running echo 'Hello world!'

Hello world!

hello-world finished with exit code 0 in 1.2s.
```

Congratulations! You've successfully configured and run your first batect task.

It's worth spending a moment to explain what batect just did:

* First, batect loaded our configuration from `batect.yml`.
* It then checked if the `node:14.3.0` image had already been pulled, and if it was not already pulled, pulled it.
* Next, it started our `build-env` container, which ran our hello world command.
* Once the container finished, it then cleaned up the container, leaving nothing running.

There's one more thing we can check: `./batect --list-tasks`. `--list-tasks` doesn't run a task - instead, it prints all the available
tasks in our configuration file, including any [`description`](config/Tasks.md#description) or [`group`](config/Tasks.md#group).

Let's try running `./batect --list-tasks` (or `./batect -T` for short) now:

```
Available tasks:
- hello-world: Say hello to everyone
```

There's nothing too surprising here, given we just created our configuration file. However, as our project grows, this can be very useful
for someone who is unfamiliar with our project and wants to understand what tasks they can perform.

## First running application

So we have our first task, but it's not exactly earth-shattering. Let's fix that by creating our TypeScript application.

Normally, we could run `yarn init` and then `yarn add typescript` to do this, but then we're using the version of Yarn installed on our
machine, if there even is one.

It would be much better if we could use the version of Yarn available in our `build-env` container - then we don't need to install
anything, and we don't have to worry about using different versions.

To do that, let's create a `shell` task that starts a shell in our build environment:

```yaml
# ... other configuration omitted for clarity

tasks:
  shell:
    description: Start a shell in the development environment
    run:
      container: build-env
      command: bash
```

We can run this with `./batect shell`, then run `yarn init .` and `yarn add typescript` to create a `package.json` with a reference to
TypeScript. However, if you exit the shell with `exit`, after batect finishes cleaning up, you'll notice there's no `package.json` in
our project directory.

What happened to `package.json`? By default, containers started with batect share nothing with the host machine, so `package.json` was
lost when the container was removed by batect. The benefit of this is that containers are as isolated as possible, making tasks run
consistently across different machines, even different operating systems.

However, complete isolation isn't particularly useful - we need to keep these files around, and we'll need to be able to share our
source code with the container soon as well.

Let's mount our project directory into `build-env` with [`volumes`](config/Containers.md#volumes):

```yaml hl_lines="4-8"
containers:
  build-env:
    image: node:14.3.0
    volumes:
      - local: .
        container: /code
        options: cached
    working_directory: /code

# ... tasks omitted for clarity
```

With this change, when `build-env` starts, the project directory (the directory containing `batect.yml`) will be mounted into the
container at `/code`. Setting `working_directory` to `/code` means that the container will start in that directory by default.

If you start a shell with `./batect shell` and run `yarn init .` and `yarn add typescript` again, you'll notice that this time,
`package.json` has been saved to your local machine.

!!! tip
    If you're using macOS or Windows, you may have noticed that `yarn add typescript` took longer than normal. This is due to the
    overhead introduced by using `node_modules` from your local machine inside the container.

    Unfortunately, the performance of Docker volume mounts on macOS and Windows isn't great, and even with `options: cached`, the
    difference can be noticeable.

    The solution is to use a batect cache for the `node_modules` folder, for example:

    ```yaml hl_lines="8-10"
    containers:
      build-env:
        image: node:14.3.0
        volumes:
          - local: .
            container: /code
            options: cached
          - type: cache
            container: /code/node_modules
            name: node_modules
        working_directory: /code
    ```

    This cache persists between each task run and doesn't incur the same performance penalty as mounting a local directory.

    There's more details about this in the [I/O performance section](tips/Performance.md) of the documentation.

With that out of the way, let's create a basic HTTP API. Create a file called `index.ts` in the same directory as our `batect.yml`
with the following contents:

```ts
import * as express from "express";

const app = express();
const port = 8080;

app.get("/", (req, res) => {
  res.send("Hello from the API!");
});

app.listen(port, () => {
  console.log(`Listening on port ${port}.`);
});
```

We'll need a few more Yarn packages - start another shell and run `yarn add @types/express express ts-node` to add the remaining
dependencies.

Let's run our application and see it in action. Add a `run` task to `batect.yml`:

```yaml
tasks:
  run:
    description: Run the application
    run:
      container: build-env
      command: yarn exec ts-node index.ts
      ports:
        - local: 8080
          container: 8080

# ... other configuration omitted for clarity
```

Our `command` uses Yarn and `ts-node` to run our TypeScript application, and maps port 8080 on our local machine to port 8080 on the
container. This means that if we go to [http://localhost:8080](http://localhost:8080) on our local machine, we'll see our "Hello from
the API!" message.

batect also supports mapping ports in container definitions, however, given we only need the port when running the application,
we've mapped the port as part of the task definition. Both have exactly the same end result.

!!! note
    We're using `ts-node` in this tutorial because it's simple and convenient. A production-grade application would compile TypeScript
    down to JavaScript using `tsc` and run it using `node`. We'll change this below.

We're pretty much done now - we can run our application in a consistent, isolated environment with a single command: `./batect run`.

However, there's one more thing we should do to make it easy for others to start using our project. With the current setup, people
using our project will need to run `./batect shell` and then run `yarn install` to download the NPM packages we're using. It would be
much better if there was a batect task they could run that did this for them, so let's add one:

```yaml
tasks:
  setup:
    description: Install dependencies needed to build and run the application
    run:
      container: build-env
      command: yarn install

# ... other configuration omitted for clarity
```

Now, when someone wants to start using our project, they just need to run `./batect setup` once, then `./batect run` to start the
application - easy!

## First dependency

Now that we've got a basic API up and running, let's expand our API to include calling an external service - and expand our batect setup
to orchestrate setting up both our application and the external service. We're going to enhance our 'hello world' message with a joke of
the day.

Let's start by adding the joke service to our `batect.yml`:

```yaml hl_lines="4-5"
containers:
  # ... build-env omitted for clarity

  joke-service:
    image: yesinteractive/dadjokes
```

We'll also need to tell batect to start `joke-service` when it runs our application. We can do this by adding it as a dependency for the
`run` task:

```yaml hl_lines="4-5"
tasks:
  run:
    description: Run the application
    dependencies:
      - joke-service
    run:
      container: build-env
      command: yarn exec ts-node index.ts
      ports:
        - local: 8080
          container: 8080
```

Finally, we need to update our application to call the service and return the joke in our message. The joke service responds to `GET /`
requests with jokes, so let's call that endpoint in `index.ts`:

```typescript hl_lines="1 7-19"
import fetch from "node-fetch";
import * as express from "express";

const app = express();
const port = 8080;

app.get("/", async (req, res) => {
  const response = await fetch("http://joke-service");

  if (!response.ok) {
    res.sendStatus(503);
    res.send(`Joke service call failed with HTTP ${response.status} (${response.statusText})`);
    return;
  }

  const responseBody = await response.json();

  res.send(`Hello from the API! The joke of the day is: ${responseBody.Joke.Opener} ${responseBody.Joke.Punchline}`);
});

app.listen(port, () => {
  console.log(`Listening on port ${port}.`);
});
```

We now have a dependency on the `node-fetch` package, so run `yarn add node-fetch @types/node-fetch` from a shell to add it to `package.json`.

Finally, we can run the application with `./batect run`, and then open [http://localhost:8080](http://localhost:8080) to see our new message,
complete with joke:

```
Hello from the API! The joke of the day is: I made a belt out of watches once... It was a waist of time.
```

You might be wondering why we didn't have to expose any ports on the `joke-service` container, and how we could use `joke-service` as a hostname
to address that container. Under the hood, when batect starts a task, it also creates a [Docker network](https://docs.docker.com/network/bridge/)
and adds all containers in the task to that network. This allows them to address each other by name (eg. making HTTP requests to `joke-service`)
and access any port without explicitly exposing the port or exposing it on the host machine.

!!! tip
    This same technique can be used to run integration or end-to-end tests against dependencies like external services or databases in consistent,
    isolated environments.

    Take a look at the [sample projects](SampleProjects.md) for some examples of this. batect can also help
    [manage the startup order of dependencies](tips/WaitingForDependenciesToBeReady.md) to reduce flakiness and retries in tests.

## First prerequisite

The final major feature of batect that we'll explore is the concept of [prerequisites](config/Tasks.md#prerequisites).

These allow you to declare that one task requires another to run first. For example, maybe your application needs to be compiled before
it is run, some data needs to be generated before the tests are executed, or a number of tasks should be run as part of a pre-commit check.

To see this in action, let's add a task to compile our TypeScript down to JavaScript:

```yaml
  build:
    description: Build the application
    run:
      container: build-env
      command: yarn exec tsc -- --outDir build --sourceMap --strict index.ts
```

We can now change our `run` task to run `build` and then run that built JavaScript:

```yaml hl_lines="3-4 7"
  run:
    description: Run the application
    prerequisites:
      - build
    run:
      container: build-env
      command: yarn exec node -- build/index.js
      ports:
        - local: 8080
          container: 8080
```

Voila! Running `./batect run` now runs the `build` task, then starts `run` to use our freshly built code.

!!! note
    We're using `yarn exec` to start `node` in `run` to workaround issues with `node` not responding to signals such as Ctrl+C when running
    in a container. There are more details on this in the [Node usage section](tools/Node.md#issues-with-signals-not-being-handled-correctly)
    of the batect documentation, including another solution to the issue.

## Summary

We've finished our whirlwind tour of batect. We can now easily setup, build and run our application in an isolated, consistent, repeatable
way, and others can quickly start working with our project just by running `./batect --list-tasks`.

## Where next?

* Take a look at the many [sample projects](SampleProjects.md) for inspiration for your own projects
* Dive into the comprehensive [reference for configuration options](config/Overview.md)
