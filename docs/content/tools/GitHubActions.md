# GitHub Actions

!!! tip "tl;dr"
    Use the `ubuntu-18.04` runner.

[GitHub Actions' Ubuntu 18.04 runners](https://help.github.com/en/actions/automating-your-workflow-with-github-actions/software-installed-on-github-hosted-runners#ubuntu-1804-lts)
come pre-installed with everything needed to run batect.

To use the Ubuntu 18.04 runner, specify `runs-on: ubuntu:18.04` in your configuration file. For example:

```yaml
jobs:
  build:
    name: "Build"
    runs-on: ubuntu-18.04
    steps:
      - uses: actions/checkout@v1
      - name: Build application
        run: ./batect build
```
