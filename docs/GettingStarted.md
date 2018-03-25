# Getting started tutorial

The samples shown below are taken from the [Java sample project](https://github.com/charleskorn/batect-sample-java).

## Installation

Before you begin, follow the [installation steps](Installation.md) to setup batect.

## First steps: build environment

To start, we're going to configure a simple build environment, where you can build your application and run unit
tests. This example is for a Java project that uses Gradle, and assumes that you already have Gradle set up for your project.

1. Create a `batect.yml` configuration file in the root of your project. For example:

    ```yaml
    containers:
      build-env:
        image: openjdk:8u141-jdk
        volumes:
          - local: .
            container: /code
            options: cached
          - local: .gradle-cache
            container: /root/.gradle
            options: cached
        working_directory: /code
        environment:
          - GRADLE_OPTS=-Dorg.gradle.daemon=false

    tasks:
      build:
        description: Build the application.
        run:
          container: build-env
          command: ./gradlew assembleDist

      unitTest:
        description: Run the unit tests.
        run:
          container: build-env
          command: ./gradlew test
    ```

   There's a bit going on here, so let's break it down:

   * `project_name`: the name of your project.
   * `containers`: here we define the different containers that your application needs.
     At the moment, we just have our one build environment container, `build-env`.
        * We tell batect which Docker image to use (`image`).
        * We tell it to mount the project (`.`, the current directory) into the container at `/code`, and to start
          the container in that directory (`working_directory`).
        * We also mount `.gradle-cache` into the container as `/root/.gradle` - this allows Gradle to cache
          dependencies between builds, rather than downloading them on every single run. (You probably want to
          add this directory to your `.gitignore`.)
        * We use `:cached` mode for the mounts to improve performance on OS X (see
          [this page](tips/Performance.md#io-performance) for more information). This has no effect on other operating systems.
        * We disable the [Gradle daemon](https://docs.gradle.org/current/userguide/gradle_daemon.html), as running
          it is pointless given that we create a new container for every run.
   * `tasks`: we define our two tasks, one for building the application, and another for running the unit tests.
     These just run the existing Gradle tasks within the build environment we just defined.

     You can define whatever tasks you want - common other tasks you might like to add include one that starts a
     shell in the build environment (eg. one with `command: bash`) and another that automatically runs the unit
     tests whenever the code is changed (eg. `command: ./gradlew --continuous test`).

   For more information on `batect.yml`, consult the [documentation](config/Overview.md).

2. Run `./batect --list-tasks`, and you'll see the tasks that we just defined:

    ```
    Available tasks:
    - build: Build the application.
    - unitTest: Run the unit tests.
    ```

3. Run `./batect build` and batect will pull the image used for your build environment, start it and run Gradle within it.
   (Note that this may take a while the first time as the Docker image must be downloaded first.)

4. Similarly, if you run `./batect unitTest`, batect will start a build environment, run your unit tests within it, and then
   clean up the build environment.

That's it! Your builds and unit tests now run in an isolated and consistent build environment, and you can easily change the
configuration of your build environment without having to install or configure anything manually on every developer or CI machine.

## Taking it further: integration and journey test environments

So we've set up an isolated and repeatable build environment. However, where batect really shines is setting up integration and
journey test environments - environments that require spinning up real (or fake) versions of dependencies such as databases or downstream services.

Let's imagine our application just has one dependency, a Postgres database. We can define a Docker image for this with a Dockerfile:

```dockerfile
FROM postgres:9.6.2
```

Save this as `dev-infrastructure/database/Dockerfile`.

So far, so good - this is just like what we had before for the build environment. However, this will start an empty Postgres database,
and our application probably needs at least a database and a table or two. Create a SQL script called `create-structure.sql` that creates
your database tables and save it in the `dev-infrastructure/database` folder you just created.

We can then take advantage of a feature of the [standard Postgres image](https://hub.docker.com/r/library/postgres/) to have this SQL
script run when the container starts. Any `.sql` file in the `/docker-entrypoint-initdb.d` directory will automatically be run when
the container starts, so if we copy our `create-structure.sql` script into that directory in the image, then whenever it is started,
our database structure will be created. So our Dockerfile now looks like:

```dockerfile
FROM postgres:9.6.2

COPY create-structure.sql /docker-entrypoint-initdb.d/
```

There's one last thing we need to think about though. When Docker starts our database container, all we know is that the container has
started - we have no way to know if the database is actually ready for use. If we want to run tests against our database, we don't want
to start running those tests until it's actually ready to use. While Postgres is usually pretty fast to start up, it's not instantaneous,
and other things can take anywhere from a few moments to a minute or two to start up and be ready. We can use
[Docker's health check feature](https://docs.docker.com/engine/reference/builder/#healthcheck) to indicate when a container is ready for use.

In our case, we can take [the health check script](https://github.com/charleskorn/batect-sample-java/tree/master/dev-infrastructure/database/health-check.sh)
from the sample project and copy it into our `dev-infrastructure/database` folder. All it does is try to issue a simple query against the
database - if that succeeds, we can assume that the database is up and running. (There's
[a collection of sample health check scripts provided by Docker](https://github.com/docker-library/healthcheck/) you can use.)

Then we need to tell Docker where to find our health check script, so we need to add it to our Dockerfile:

```dockerfile
FROM postgres:9.6.2

RUN mkdir -p /tools
COPY health-check.sh /tools/
HEALTHCHECK --interval=2s CMD /tools/health-check.sh

COPY create-structure.sql /docker-entrypoint-initdb.d/
```

So, now we have a Dockerfile that describes how to start up our database, and how to tell when it's ready for use. Now we just need to configure
batect to run our tests.

First of all, let's define our database container:

```yaml
containers:

  ...

  database:
    build_directory: dev-infrastructure/database
    environment:
      - POSTGRES_USER=international-transfers-service-user
      - POSTGRES_PASSWORD=TheSuperSecretPassword
      - POSTGRES_DB=international-transfers-service
```

This uses the environment variables defined by the Postgres image to set the username, password and database name to use to connect to it. We
could have specified them in the Dockerfile with `ENV` statements, but this works as well.

Then we just need to define our integration test task:

```yaml
tasks:

  ...

  integrationTest:
    description: Run the integration tests.
    run:
      container: build-env
      command: ./gradlew integrationTest
    start:
      - database

```

This is just like the build and unit test tasks we defined before, but we now also specify our database container in `start`. batect will start
any containers listed in `start` and wait for them to become healthy before starting the container given in `run`.

Under the covers, batect will also create an isolated network for all of the task's containers, so that they can communicate with one another
without interfering with anything else on your machine. (They'll still have access to the internet and anything else they could access if they
were running directly on your machine though.) This means that the integration tests just need to connect to the host `database` with the username
`international-transfers-service-user` and password `TheSuperSecretPassword`, and Docker will automatically forward that to the database container.

And, after your tests have finished, batect will then remove all the containers it started, leaving your machine in the same state it was before
you started.

Similarly, if we want to run some journey tests that test our application end-to-end, we just need to create a Dockerfile for our application,
then define it in `batect.yml`:

```yaml
containers:

  ...

  international-transfers-service:
    build_directory: dev-infrastructure/international-transfers-service
    dependencies:
      - database
```

...and then add a task:

```yaml
tasks:

  ...

  journeyTest:
    description: Run the journey tests.
    run:
      container: build-env
      command: ./gradlew journeyTest
    start:
      - international-transfers-service
    prerequisites:
      - build

```

Note that in this case, we specify that the database container is a dependency of the application container - this means that batect will first
start the database container and wait for it to become healthy, then start the application and wait for it to become healthy, and then run the
journey tests. We also specify that the build task should run before starting the journey tests - this is so that when we start the application,
we start the most recent version of it.

## Where next?

There's a comprehensive [reference page for the configuration file](config/Overview.md), and a number of [sample applications](SampleProjects.md)
you can take a look at.
