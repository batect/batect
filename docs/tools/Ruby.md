# Ruby

## Bundler

You can see an example of configuring and using Ruby and Bundler with batect in the [Ruby sample project](https://github.com/charleskorn/batect-sample-ruby).

### Caching dependencies

{% hint style='tip' %}
**tl;dr**: set the `BUNDLE_PATH` environment variable to a directory within your mounted code directory, otherwise you'll have to download your dependencies every 
time the build runs 
{% endhint %}

By default, Bundler downloads all of your application's dependencies to the `~/.bundle` directory. However, because batect destroys all of your containers once
the task finishes, this directory is lost at the end of every task run - which means that Bundler will have to download all of your dependencies again, 
significantly slowing down the build.

The solution to this is to set the `BUNDLE_PATH` environment variable to a directory that persists between builds. 

If you're already mounting your application's code into the container, then the simplest thing to do is to set `BUNDLE_PATH` to a directory within that mounted
directory. For example, if you're mounting your application's code into the container at `/code`, set `BUNDLE_PATH` to `/code/.bundle-cache`.

