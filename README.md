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
* Docker (host) >= 1.9.0

# Apply plugin

See https://plugins.gradle.org/plugin/com.chrisgahlert.gradle-dcompose-plugin

# Usage

When running the build, it will automatically look for the following Docker environment 
variables:

* DOCKER_HOST (e.g. `tcp://localhost:2376`)
* DOCKER_TLS_VERIFY (e.g. `1` or `0`)
* DOCKER_CERT_PATH

You can easily set these variables with the help of this shell script (when using docker-machine): 
`eval "$(docker-machine env default)"`

After applying the plugin you can start by defining containers:

## Use existing image

```
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

```
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

```
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
| portBindings | List&lt;String&gt; | A list of port bindings. These specify which host ports should be mapped to which container ports. The possible formats are `hostIp:hostPort:containerPort`, `hostIp::containerPort` and `containerPort`. If no host port has been given, a port will be automatically chosen. <br><br> *Sample:* `['8080', '10000:8080', '1234-1236:1234-1236/tcp', '127.0.0.1::8080', '192.168.0.1:8080:8080']`
| publishAllPorts | Boolean<br> *Default: null* | Whether all ports of this container should be accessible from other containers.
| links | List&lt;ContainerDependency \| String&gt; | A list of containers, that this container should be linked to. You can create a `ContainerDependency` by calling a Container's `link` method - optionally with an alias. If you specify a String, it will be interpreted as an external non-managed container.<br><br> *Sample:* `[database.link(), database.link('otheralias'), 'non_managed_container', 'non_managed_container:alias',]`
| hostName | String | The hostname that should be given to the container instance. <br><br> *Sample:* `'myhost'`
| dns | List&lt;String&gt; | A list of DNS servers. <br><br> *Sample:* `['8.8.8.8', '8.8.4.4', ...]`
| dnsSearch | List&lt;String&gt; | A list of DNS search domains.<br><br> *Sample:* `['mydomain1', 'mydomain2', ...]`
| extraHosts | List&lt;String&gt; | A list of other hosts that will be made available through `/etc/hosts`. <br><br> *Sample:* `['hostname:1.2.3.4', ...]`
| networkMode | String | The network mode passed to Docker. <br><br> *Samples:* <br>`'bridge'`<br>`'none`<br>`'host`
| attachStdin | Boolean<br> *Default: null* | *See Docker documentation*
| attachStdout | Boolean<br> *Default: null* | *See Docker documentation*
| attachStderr | Boolean<br> *Default: null* | *See Docker documentation*
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

```
task copyFiles(type: DcomposeCopyFileFromContainerTask) {
  container = dcompose.database     // Just reference the container from which the files should be copied
  containerPath = '/some/dir/or/file'
  destinationDir = file(...)        // Default: "$buildDir/<TaskName>/"
  cleanDestinationDir = true|false  // Default: false. Will remove the entire destination dir! Use with caution!
}
```

# Advanced example (not tested)

#### src/main/docker/Dockerfile

```
FROM 'nginx'
COPY index.html /www/
```

#### build.gradle
```
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
    links = [db.link('mongo__db'), cache.link()]
    env = ['MONGO_HOST=mongo_db', 'REDIS_HOST=cache']
    tag = 'someuser/mywebimage:latest'
  }
}

task copyDockerData(type: Copy) {
  from 'src/main/www'
  from 'src/main/docker/'
  into "$buildDir/docker/"
}
buildWebImage.dependsOn copyDockerData
```

#### Running

Launch the build by running `gradle startWebContainer`. This should automatically 
pull/create/start all required containers. Whenever something changes, you just need 
to re-run this build and everything, that needs to be recreated will be.

# Limitations

* As of now Multi-Project-Support has not been (fully) integrated yet.
* Theoretically this plugin should have support for Gradle's continuous mode. However 
this has not been tested.