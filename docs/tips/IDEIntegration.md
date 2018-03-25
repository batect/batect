# IDE integration

{% hint style='tip' %}
**tl;dr**: some IDEs can't provide their advanced features (eg. code completion) when using batect, but there are solutions
{% endhint %}

Many IDEs rely on having the development environment installed locally in order to provide features like code completion,
analysis and tool integration. (For example, a Ruby IDE might need access to a Ruby runtime, and a Java IDE might need
the target JVM to be installed.) However, if you're using batect, then all of this is in a container and so the IDE can't
access it.

Some solutions for this include:

* Some of the JetBrains family of products natively supports using a SDK or runtime from a container (PyCharm and RubyMine
  are known to work, although notably IntelliJ does not currently support this). There's more information on how to configure
  this in the [PyCharm docs](https://www.jetbrains.com/help/pycharm/configuring-remote-interpreters-via-docker.html) and
  [RubyMine docs](https://www.jetbrains.com/help/ruby/configuring-remote-interpreters-via-docker.html).
* You could run a text-based editor such as Vim or Emacs in a container (managed by batect, of course) that has your
  required runtime components installed alongside it.

(Have you tried something else that worked? Or do you use another IDE or text editor that supports using runtimes inside a
container? Please [submit a PR](https://github.com/charleskorn/batect/pulls) to add to the list above.)
