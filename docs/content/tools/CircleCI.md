# CircleCI

!!! tip "tl;dr"
    Use a machine executor with an image that has a recent version of Docker.

CircleCI's recent machine executor images include everything batect requires, so all that needs to be done to use batect
with CircleCI is to configure it to use one of those images.

A list of available images is published in the CircleCI documentation [here](https://circleci.com/docs/2.0/configuration-reference/#machine).
batect requires an image with a compatible version of Docker - currently version 17.12 or newer.

Adding the following to your `.circleci/config.yml` file instructs CircleCI to use a machine executor with the 201808-01 image,
which contains Docker 18.06:

```yaml
version: 2

jobs:
  build:
    machine:
      enabled: true
      image: circleci/classic:201808-01
    steps:
      - checkout
      - run: ./batect ...
```

You can see a full example of using batect with CircleCI in
[the Golang sample project](https://github.com/charleskorn/batect-sample-golang).
