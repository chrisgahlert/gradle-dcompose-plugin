# gradle-dcompose-plugin

When working with Gradle projects, there has always been a "technology break" when using Docker. First you 
would need to run a build like ```gradle assemble``` and afterwards ```docker-compose up``` in order to test 
your code locally.

This plugin aims at fully integrating the Docker container management into the Gradle build itself thus 
eleminating the need for docker-compose. It uses Gradle's UP-TO-DATE checks in order to determine whether a 
container should be recreated.

# Prerequisites

This plugin requires: 
* Gradle >= 2.0
* Docker (host) >= 1.8.1 (older versions possibly work but have not been tested)


# Usage

To apply the plugin, see the Gradle plugin page:
https://plugins.gradle.org/plugin/com.chrisgahlert.gradle-dcompose-plugin

When running the build, you will need to make sure, Docker is properly configured. The easiest way
is to just use environment variables. The plugin will automatically look for the following Docker 
environment variables:

* DOCKER_HOST (e.g. `tcp://localhost:2376`)
* DOCKER_TLS_VERIFY (e.g. `0`)
* DOCKER_CERT_PATH

You can easily set these variables with the help of this shell script (when using docker-machine): 
`eval "$(docker-machine env default)"`

In order to customise the Docker Client configuration programmatically, you can include the
following configuration in your build script:

```gradle
dcompose {
  dockerClientConfig = {
    // Will delegate to the DockerClientConfigBuilder from
    // https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/core/DockerClientConfig.java#L348
    withDockerHost 'tcp://somehost:1234'
    withTlsVerify false
  }
}
```

The evaluation order of properties will be (in descending order of precedence):

1. The ```dockerClientConfig``` closure
2. System properties (e.g. ```gradle startSomeContainer -DDOCKER_HOST=tcp://somehost:1234```)
3. Environment variables (e.g. ```export DOCKER_HOST=tcp://somehost:1234; gradle startSomeContainer```
4. A properties file in ```$HOME/.docker-java.properties```


For a complete documentation see the configuration options of the docker-java project:
https://github.com/docker-java/docker-java#documentation

Please also make sure, that the Project has the Maven Central Repository or a mirror added. This is needed to download the Docker Java Client dependencies.

After applying the plugin you can start by defining containers:

## Use existing image

```gradle
dcompose {
  database {
    image = 'busybox:1.24.2'   // Required
    
    command = ['sleep', '300'] // Optional, if provided by image
  }
}

test {
  dependsOn startDatabaseContainer
  finalizedBy removeDatabaseContainer
}
```

Executing `gradle test` will result in the following tasks being executed:

1. `:pullDatabaseImage`
1. `:createDatabaseContainer`
1. `:startDatabaseContainer`
1. `:test`
1. `:stopDatabaseContainer`
1. `:removeDatabaseContainer`

## Build custom image from Dockerfile

```gradle
dcompose {
  myImage {
    baseDir = file('docker/')             // Required; will monitor directory for changes
    dockerFilename = 'Dockerfile'         // Optional
    tag = 'myImage'                       // Optional
    memory = 1000000L                     // Optional
    memswap = 500000L                     // Optional
    cpushares = '...'                     // Optional
    cpusetcpus = '...'                    // Optional
    buildArgs = [arg1: 'val1', ...]       // Optional
    forceRemoveImage = true|false|null    // Optional
    noPruneParentImages = true|false|null // Optional
    buildNoCache = true|false|null        // Optional
    buildRemove = true|false|null         // Optional
    buildPull = true|false|null           // Optional
    
    command = ['sh', '-c', 'echo abc']    // Optional, if provided by Dockerfile
  }
}

test {
  dependsOn startMyImageContainer
  finalizedBy removeMyImageImage
}
```

Executing `gradle test` will result in the following tasks being executed:

1. `:buildMyImageImage`
1. `:createMyImageContainer`
1. `:startMyImageContainer`
1. `:test`
1. `:stopMyImageContainer`
1. `:removeMyImageContainer`
1. `:removeMyImageImage`

## Runtime properties

The runtime properties are available for both types of containers (existing image or custom 
built image). They are all optional and can be applied like this:

```gradle
dcompose {
  myName {
    // image = '...' OR baseDir = file('...')
    
    runtimeProperty = ... // See list below
  }
}
```

| Property | Type | Description |
| --- | --- | --- |
| waitForCommand | boolean<br> *Default: false* | Whether the `start<Name>Container` task should wait until the container is not running anymore.
| waitTimeout | int<br> *Default: 0* | How long should we wait for the command to finish before failing. <br> &lt;=0 *Wait forever*<br> &gt;0 *Timeout in seconds*
| ignoreExitCode | boolean<br> *Default: false* | Whether the exit code will be checked after running the container. (Only applies if `waitForCommand` is enabled.)
| preserveVolumes | boolean<br> *Default: false* | Whether the container's volumes should be preserved when removing/recreating the container. All volumes will be named in the format "`<dockerPrefix><containerName>__<PathToUpperCase>`".
| command | List&lt;String&gt; | A list of command parts that should be executed when starting the container.<br><br> *Samples:* <br>`['echo', 'hello']`<br>`['sh', '-c', 'echo $ENV']`
| entrypoints | List&lt;String&gt; | A list of entrypoints.<br><br> *Sample:* `['/entrypoint.sh', ...]`
| env | List&lt;String&gt; | A list of environment variables passed into the container.<br><br> *Sample:* `['MY_VAR=test', 'OTHER=test2', ...]`
| workingDir | String | The container working dir the commands/entrypoints should start from.<br><br> *Sample:* `'/home'`
| user | String | The user which should be used to launch the command/entrypoints. <br><br> *Sample:* `'root'`
| readonlyRootfs | Boolean<br> *Default: null* | Whether the container's main filesystem should be readonly. If enabled, only volumes can be written to.
| volumes | List&lt;String&gt; | A list of volumes, which are provided by this container. These are additional to the volumes defined in the image or Dockerfile.<br><br> *Sample:* `['/data1', '/var/log', ...]`
| binds | List&lt;String&gt; | A list of container bindings. Here you can specify which volumes or which host paths should be mounted in the container.<br><br> *Samples:* `['custom_volume:/data1', '/host/path:/var/log', 'vol:/tmp:rw', ...]`
| volumesFrom | List&lt;Container \| String&gt; | Here you can specify a list of other containers, which volumes should be mounted. You can pass in the container instance by referencing it by value. If you specify a String, it will be interpreted as an external non-managed container. <br><br> *Sample:* `[database, 'non_managed_container', ...]`
| exposedPorts | List&lt;String&gt; | A list of ports or port ranges, that are available within this container.<br><br> *Sample:* `['8080', '10000-10020', ...]`
| portBindings | List&lt;String&gt; | A list of port bindings. These specify which host ports should be mapped to which container ports. The possible formats are `hostIp:hostPort:containerPort`, `hostIp::containerPort` and `containerPort`. If no host port has been given, a port will be automatically chosen. In order to find a dynamically chosen port, see chapter *Dynamic host ports*. <br><br> *Sample:* `['8080', '10000:8080', '1234-1236:1234-1236/tcp', '127.0.0.1::8080', '192.168.0.1:8080:8080']`
| publishAllPorts | Boolean<br> *Default: null* | Whether all ports of this container should be accessible from other containers.
| links | List&lt;ContainerDependency \| String&gt; | A list of containers, that this container should be linked to. You can create a `ContainerDependency` by calling a Container's `link` method - optionally with an alias. If you specify a String, it will be interpreted as an external non-managed container.<br><br> *Sample:* `[database.link(), database.link('otheralias'), 'non_managed_container', 'non_managed_container:alias',]`
| hostName | String | The hostname that should be given to the container instance. <br><br> *Sample:* `'myhost'`
| dns | List&lt;String&gt; | A list of DNS servers. <br><br> *Sample:* `['8.8.8.8', '8.8.4.4', ...]`
| dnsSearch | List&lt;String&gt; | A list of DNS search domains.<br><br> *Sample:* `['mydomain1', 'mydomain2', ...]`
| extraHosts | List&lt;String&gt; | A list of other hosts that will be made available through `/etc/hosts`. <br><br> *Sample:* `['hostname:1.2.3.4', ...]`
| networkMode | String | The network mode passed to Docker. <br><br> *Samples:* <br>`'bridge'`<br>`'none`<br>`'host`
| attachStdin | Boolean<br> *Default: null* | Whether the containers' stdin should be attached. Actually providing a stream is currently not support by the Docker library.
| attachStdout | Boolean<br> *Default: null* | Whether the containers' stdout should be attached. If used in combination with `waitForCommand` it will redirect stdout to `System.out` by default.
| attachStderr | Boolean<br> *Default: null* | Whether the containers' stderr should be attached. If used in combination with `waitForCommand` it will redirect stderr to `System.err` by default.
| privileged | Boolean<br> *Default: null* | Whether this container should be started in privileged mode. This will give the container almost the same rights as the host itself. This is useful e.g. for running "Docker in Docker".


# Gradle tasks

## Container tasks
For each container definition the following tasks will be created:

| Task name | Depends on | Description |
| --- | --- | --- |
| `build<Name>Image` | - | Build an image based on the `Dockerfile` and `baseDir` definitions. <br><br> _Only created if the `baseDir` (and optionally the `dockerFilename`) property is provided_ 
| `pull<Name>Image` | - | Pulls an image from the Docker hub. It will be skipped if an image with that name/id already exists. <br><br> _Only created if the `image` property is provided_
| `create<Name>Container` | `build<Name>Image` OR `pull<Name>Image` | Creates or recreates a container based on the container definition. |
| `start<Name>Container` | `create<Name>Container` | Starts a previously created container. If the flag ```waitForCommand``` has been set to ```true``` this task will not complete until the container has stopped running. |
| `stop<Name>Container` | - | Stops a container if it is running. It will be skipped otherwise.
| `remove<Name>Container` | `stop<Name>Container` | Removes a container. It will also remove the container's volumes if ```preserveVolumes``` has not been set to ```true``` |
| `remove<Name>Image` | `remove<Name>Container` | Removes the image locally. This will fail if a container, that is not managed by this plugin is still using this image.


## All tasks

Additionally, the following all-tasks will be created after applying the plugin (regaradless of whether container definitions were added or not):

* `buildImages`
* `pullImages`
* `createContainers`
* `startContainers`
* `stopContainers`
* `removeContainers`
* `removeImages`

## Copy files from container

In order to copy files from a running (or stopped) container, you can manually create a task to 
copy some files or directories from the container to the host:

```gradle
task copyFiles(type: DcomposeCopyFileFromContainerTask) {
  container = dcompose.database     // Just reference the container from which the files should be copied
  containerPath = '/some/dir/or/file'
  destinationDir = file(...)        // Default: "$buildDir/<taskName>/"
  cleanDestinationDir = true|false  // Default: false. Will remove the entire destination dir! Use with caution!
}
```

## Dynamic host ports

When linking containers, you have the option to not specify a host port. Instead Docker will automatically choose 
a host port for you. In order to access this host port later on, you can reference them from the container definition:

```gradle
dcompose {
  database {
    image = 'mysql:latest'
    exposedPorts = ['3306'] // Optional, as already provided by image
    portBindings = ['3306']
  }
}

task runTestsAgainstDatabase(type: Test) {
  dependsOn 'startDatabaseContainer'
  
  doFirst {
    // The method findHostPort can only be called AFTER the container start task has executed. 
    // That's why it is wrapped in the doFirst block. This also has the advantage, that if the
    // dynamic port changes, it will not be influencing the test task's UP-TO-DATE check.
    systemProperty 'mysql.port', dcompose.database.findHostPort(3306)
    
    // For convenience:
    // If docker-host is using a linux socket, "localhost" will be assumed. Otherwise the hostname provided
    // via "tcp://hostname:port" will be parsed an returned.
    systemProperty 'mysql.host', dcompose.database.dockerHost
  }
  doLast {
    // For newer Gradle versions it is important to remove the "dynamic" system properties before
    // task execution ends as they will be persisted for future UP-TO-DATE checks.
    systemProperties.remove 'mysql.port'
    systemProperties.remove 'mysql.host'
  }
}
```

## Redirecting stdout/stderr

It is possible to redirect a container's stdout/stderr to custom streams:

```gradle
dcompose {
  cmdApp {
    image = 'ubuntu:latest'
    command = 'echo hello from stdout'
    waitForCommand = true // This is required
    attachStdout = true
    attachStderr = true
  }
}

startCmdAppContainer {
  // Defining outputs is not necessary as "waitForCommand" causes this task to run always
  doFirst {
    stdOut = new FileOutputStream(file("$buildDir/out.txt"))
    stdErr = System.err // default
  }
  doLast {
    stdOut.close()
  }
}
```

# Advanced example (not tested)

#### src/main/docker/Dockerfile

```dockerfile
FROM 'nginx:latest'
COPY index.html /www/
```

#### build.gradle
```gradle
dcompose {
  dbData {
    image = 'mongo:latest'
    volumes = ['/var/lib/mongodb']
    preserveVolumes = true
  }
  db {
    image = 'mongo:latest'
    volumesFrom = [dbData]
  }
  cache {
    image = 'redis:latest'
  }
  web {
    baseDir = file("$buildDir/docker/")
    links = [db.link('mongo_db'), cache.link()]
    env = ['MONGO_HOST=mongo_db', 'REDIS_HOST=cache']
    tag = 'someuser/mywebimage:latest'
  }
}

task copyDockerData(type: Copy) {
  from 'src/main/www/' // Contains index.html
  from 'src/main/docker/'
  into "$buildDir/docker/"
}
buildWebImage.dependsOn copyDockerData
```

#### Running

Launch the build by running `gradle startWebContainer`. This should automatically 
pull/create/start all required containers. Whenever something changes, you just need 
to re-run this build and everything, that needs to be recreated will be.

# Multi-project support

The default syntax for referencing containers within a project is to reference them 
with their name like ```dcompose.myContainer```. In order to also support Multi-project 
setups, there is another syntax: ```dcompose.container(':project:myContainer')```. This
allows to reference a container from another project. _FYI: Although this looks like a 
Gradle task path it is actually referencing a container within that project._

```gradle
project(':prjA') {
  apply plugin: 'com.chrisgahlert.gradle-dcompose-plugin'
  
  dcompose {
    server {
      image = '...'
      exposedPorts = ['8080']
      volumes = ['/var/log']
    }
  }
}

project(':prjB') {
  apply plugin: 'com.chrisgahlert.gradle-dcompose-plugin'
  
  dcompose {
    client {
      image = '...'
      links = [
        container(':prjA:server').link(),       // Container alias will default to 'server'
        container(':prjA:server').link('alias') // Or you can manually define an alias
      ]
      volumesFrom = [
        container(':prjA:server')
      ]
    }
  }
  
  
  task copyFiles(type: DcomposeCopyFileFromContainerTask) {
    container = dcompose.container(':prjA:server')
    containerPath = '/some/dir/or/file'
  }
}
```

*Please note:* When using this plugin together with Gradle's _Configuration on Demand_, executing
a destructive task like _stop\*_ or _remove\*_ will trigger the evaluation of all projects.
