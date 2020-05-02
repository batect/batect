# Seq

!!! tip "tl;dr"
    Take a look at the [Seq sample project](https://github.com/batect/batect-sample-seq) for an example of running Seq to capture
    logs from your application during local development.

[Seq](https://datalust.co/seq) is a log aggregation and viewing tool that's perfect for browsing logs during local development.

You can launch an instance of Seq with the following batect configuration (note that this has a flaw explained [below](#preventing-the-loss-of-logs-on-startup)):

```yaml
containers:
  sqelf:
    image: datalust/sqelf:2.0.270
    ports:
      - 12201:12201/udp
    environment:
      SEQ_ADDRESS: http://seq:5341
    dependencies:
      - seq

  seq:
    image: datalust/seq:5.1.3364
    ports:
      - 9000:80
    environment:
      ACCEPT_EULA: Y
```

This will make the Seq UI available at [http://localhost:9000](http://localhost:9000) on your local machine, and allows submitting
logs to Seq using the GELF protocol on UDP port 12201.

You can combine this with Docker's GELF log driver to automatically send logs to Seq, for example:

```yaml
containers:
  sqelf:
    # ... configuration as above

  seq:
    # ... configuration as above

  app:
    # ... other configuration
    log_driver: gelf
    log_options:
      gelf-address: udp://localhost:12201
    dependencies:
      - sqelf
```

!!! warning
    Docker does not support streaming logs to the console when using the GELF log driver. Instead, you'll receive an error message such
    as `Error attaching: configured logging driver does not support reading`.

    This does not affect the operation of your containers, only the ability to see the output in the console.

## Preventing the loss of logs on startup

If you use the configuration above, depending on how quickly your application starts, you may lose some of the first log messages it emits.

The solution to this is to [specify a healthcheck](../tips/WaitingForDependenciesToBeReady.md) for the Seq container. For example, you can
replace the image with this Dockerfile to add a healthcheck:

```dockerfile
FROM datalust/seq:5.1.3364

RUN apt-get update && apt-get install -y --no-install-recommends curl

HEALTHCHECK --interval=2s CMD curl "http://localhost/api" --fail --show-error --silent
```

And then reference that Dockerfile (saved as `.batect/seq/Dockerfile`) in your batect configuration:

```yaml
containers:
  # ... other containers

  seq:
    build_directory: .batect/seq
    ports:
      - 9000:80
    environment:
      ACCEPT_EULA: Y

```
