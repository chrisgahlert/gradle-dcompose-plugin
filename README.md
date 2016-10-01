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
* Docker (host) >= 1.10.0 
* **Please note:** Docker for Mac (native) currently doesn't support redirecting standard streams to/from a container.

# Example

An example project says more than a thousand words:
https://github.com/chrisgahlert/gradle-dcompose-sample

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
    // Will delegate to the Builder from
    // https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/core/DefaultDockerClientConfig.java#L317
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

After applying the plugin you can start by defining services:

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
1. `:createDefaultNetwork`
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
    repository = 'user/image'             // The name for the image repository (used for building and pushing)
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
1. `:createDefaultNetwork`
1. `:createMyImageContainer`
1. `:startMyImageContainer`
1. `:test`
1. `:stopMyImageContainer`
1. `:removeMyImageContainer`
1. `:removeMyImageImage`

## Container properties

The container properties are available for both types of services (existing image or custom 
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
| stopTimeout | int<br> *Default: 10* | How long should we wait for a container to stop gracefully (before killing it).
| ignoreExitCode | boolean<br> *Default: false* | Whether the exit code will be checked after running the container. (Only applies if `waitForCommand` is enabled.)
| preserveVolumes | boolean<br> *Default: false* | Whether the container's volumes should be preserved when removing/recreating the container. All volumes will be named in the format "`<dockerPrefix><containerName>__<PathToUpperCase>`".
| command | List&lt;String&gt; | A list of command parts that should be executed when starting the container.<br><br> *Samples:* <br>`['echo', 'hello']`<br>`['sh', '-c', 'echo $ENV']`
| entrypoints | List&lt;String&gt; | A list of entrypoints.<br><br> *Sample:* `['/entrypoint.sh', ...]`
| env | List&lt;String&gt; | A list of environment variables passed into the container.<br><br> *Sample:* `['MY_VAR=test', 'OTHER=test2', ...]`
| workingDir | String | The container working dir the commands/entrypoints should start from.<br><br> *Sample:* `'/home'`
| user | String | The user which should be used to launch the command/entrypoints. <br><br> *Sample:* `'root'`
| readonlyRootfs | Boolean<br> *Default: null* | Whether the container's main filesystem should be readonly. If enabled, only volumes can be written to.
| volumes | List&lt;String&gt; | A list of volumes, which are provided by this container. These are additional to the volumes defined in the image or Dockerfile.<br><br> *Sample:* `['/data1', '/var/log', ...]`
| binds | List&lt;String&gt; | A list of volume bindings. Here you can specify which volumes or which host paths should be mounted in the container.<br><br> *Samples:* `['custom_volume:/data1', '/host/path:/var/log', 'vol:/tmp:rw', ...]`
| volumesFrom | List&lt;Container \| String&gt; | Here you can specify a list of other containers, whose volumes should be mounted. You can pass in the container instance by referencing it by value. If you specify a String, it will be interpreted as an external non-managed container. <br><br> *Sample:* `[database, 'non_managed_container', ...]`
| exposedPorts | List&lt;String&gt; | A list of ports or port ranges, that are available within this container.<br><br> *Sample:* `['8080', '10000-10020', ...]`
| portBindings | List&lt;String&gt; | A list of port bindings. These specify which host ports should be mapped to which container ports. The possible formats are `hostIp:hostPort:containerPort`, `hostIp::containerPort` and `containerPort`. If no host port has been given, a port will be automatically chosen. In order to find a dynamically chosen port, see chapter *Dynamic host ports*. <br><br> *Sample:* `['8080', '10000:8080', '1234-1236:1234-1236/tcp', '127.0.0.1::8080', '192.168.0.1:8080:8080']`
| publishAllPorts | Boolean<br> *Default: null* | Whether all ports of this container should be accessible from other containers.
| links | List&lt;ContainerDependency \| String&gt; | A list of containers, that this container should be linked to. You can create a `ContainerDependency` by calling a Container's `link` method - optionally with an alias. If you specify a String, it will be interpreted as an external non-managed container.<br><br> *Sample:* `[database.link(), database.link('otheralias'), 'non_managed_container', 'non_managed_container:alias',]` <br><br> As of version 0.5.0 linking containers is no longer necessary, as compose-like networks will be created.
| hostName | String | The hostname that should be given to the container instance. <br><br> *Sample:* `'myhost'`
| dns | List&lt;String&gt; | A list of DNS servers. <br><br> *Sample:* `['8.8.8.8', '8.8.4.4', ...]`
| dnsSearch | List&lt;String&gt; | A list of DNS search domains.<br><br> *Sample:* `['mydomain1', 'mydomain2', ...]`
| extraHosts | List&lt;String&gt; | A list of other hosts that will be made available through `/etc/hosts`. <br><br> *Sample:* `['hostname:1.2.3.4', ...]`
| networkMode | String | The network mode passed to Docker. <br><br> *Samples:* <br>`'bridge'`<br>`'none`<br>`'host`
| attachStdin | Boolean<br> *Default: null* | Whether the containers' stdin should be attached. If used in combination with `waitForCommand` it will redirect stdin to `System.in` by default.
| attachStdout | Boolean<br> *Default: null* | Whether the containers' stdout should be attached. If used in combination with `waitForCommand` it will redirect stdout to `System.out` by default.
| attachStderr | Boolean<br> *Default: null* | Whether the containers' stderr should be attached. If used in combination with `waitForCommand` it will redirect stderr to `System.err` by default.
| privileged | Boolean<br> *Default: null* | Whether this container should be started in privileged mode. This will give the container almost the same rights as the host itself. This is useful e.g. for running "Docker in Docker".
| networks | List&lt;String&gt;<br> *Default: \[ network('default') \]* | A list of networks that this container should be connected to. <br><br> *Sample:* `[ network('network1'), network(':otherproject:network2') ]`
| aliases | List&lt;String&gt; <br> *Default: null* | A list of aliases that can be used to reference a container on the same network. <br><br> *Sample:* `['alias1', 'alias2', ...]` <br><br> _The service name will automatically be added as well._
| deploy | boolean<br> *Default: true* | Whether this service should be included when creating a `docker-compose.yml` file by calling the `createComposeFile` task
| dependsOn | List&lt;Service&gt; <br> *Defaut: null* | A list of services that this service depends on. These services will be created/started before this service and stopped/removed after this service. <br><br> *Sample:* `[myService1, service('myService2'), service(':project:myService3')`
| restart | String<br> *Default: null* | The container's restart policy.<br><br> *Sample:* `'on-failure'`, `'on-failure:10'`, `'always'` or `'unless-stopped'`


# Gradle tasks

## Container tasks
For each service definition the following tasks will be created:

| Task name | Depends on | Description |
| --- | --- | --- |
| `build<Name>Image` | - | Build an image based on the `Dockerfile` and `baseDir` definitions. <br><br> _Will be disabled, if the `baseDir` has not been set._ 
| `pull<Name>Image` | - | Pulls an image from the Docker hub. It will be skipped if an image with that name/id already exists. <br><br> _Will be disabled, if the `image` property has not been set._
| `create<Name>Container` | `build<Name>Image`, `pull<Name>Image` <br>for every network: `create<networkName>Network` | Creates or recreates a container based on the service definition. |
| `start<Name>Container` | `create<Name>Container` | Starts a previously created container. If the flag ```waitForCommand``` has been set to ```true``` this task will not complete until the container has stopped running. |
| `stop<Name>Container` | - | Stops a container if it is running. It will be skipped otherwise.
| `remove<Name>Container` | `stop<Name>Container` | Removes a container. It will also remove the container's volumes if ```preserveVolumes``` has not been set to ```true``` |
| `remove<Name>Image` | `remove<Name>Container` | Removes the image locally. This will fail if a container, that is not managed by this plugin is still using this image.


## All tasks

Additionally, the following all-tasks will be created after applying the plugin (regaradless of whether service definitions were added or not):

* `buildImages`
* `pullImages`
* `createNetworks`
* `createContainers`
* `startContainers`
* `stopContainers`
* `removeContainers`
* `removeImages`
* `removeNetworks`

## Copy files from container

In order to copy files from a running (or stopped) container, you can manually create a task to 
copy some files or directories from the container to the host:

```gradle
task copyFiles(type: DcomposeCopyFileFromContainerTask) {
  service = dcompose.database       // Just reference the service from which the files should be copied
  containerPath = '/some/dir/or/file'
  destinationDir = file(...)        // Default: "$buildDir/<taskName>/"
  cleanDestinationDir = true|false  // Default: false. Will remove the entire destination dir! Use with caution!
}
```

## Dynamic host ports

When linking services, you have the option to not specify a host port. Instead Docker will automatically choose 
a host port for you. In order to access this host port later on, you can reference them from the service definition:

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

## Networking

Starting with version 0.5.0 all services within one subproject will be added to the same network.
Therefore linking services is not necessary anymore. You can now define compose like networks:

```gradle
dcompose {
  networks {
    frontend
    backend
    // default is always created and every container will be connected unless networks are defined
  }
  database {
    image = 'mongo:latest'
    networks = [backend]
    aliases = ['backup_database']
  }
  webserver {
    image = 'nginx:latest'
    env = ['PRIMARY_DB_HOST=database', 'SECONDARY_DB_HOST=backup_database']
    networks = [frontend, backend]
  }
  other {
    image = 'busybox:latest'
    networks << frontend
  }
  other2 {
    image = 'busybox:latest'
    networks = [frontend, backend, network('default')] // as 'default' is a reserved keyword you need to reference it by method
  }
```

## Redirecting standard streams

It is possible to redirect a container's stdin/stdout/stderr to custom streams:

```gradle
dcompose {
  cmdApp {
    image = 'ubuntu:latest'
    command = ['sh', '-c', 'echo `wc -w` words']
    waitForCommand = true // This is required
    attachStdout = true
    attachStderr = true
    attachStdin = true
  }
}

startCmdAppContainer {
  // Defining outputs is not necessary as "waitForCommand" causes this task to run always
  doFirst {
    stdIn = new ByteArrayInputStream("Hello world".bytes)    // default: System.in
    stdOut = new FileOutputStream(file("$buildDir/out.txt")) // default: System.out
    stdErr = new FileOutputStream(file("$buildDir/err.txt")) // default: System.err
  }
  doLast {
    stdOut.close()
    stdErr.close()
  }
}
```

## Pushing images and private Registries

It is possible to publish images to a public or private registry:

```gradle
dcompose {
  dockerClientConfig = {
    withRegistryUsername 'dockerHubUser'
    withRegistryPassword 'dockerHubPass'
  }
  registry ('myregistry.com:5000') {
      // Delegated to an instance of 
      // https://github.com/docker-java/docker-java/blob/master/src/main/java/com/github/dockerjava/api/model/AuthConfig.java
      withUsername 'privateRegistryUser'
      withPassword 'privateRegistryPass'
  }
  registry ('myotherregistry.com:5000') {
      // Needs no user/pass
  }

  publicImage {
    baseDir = file('build/docker/pub/')
    repository = 'user/publicImage:latest'            // Will be pushed to Docker hub
  }
  privateImage {
    image = 'mysql:latest'                            // Will be pulled from Docker hub
    repository = 'myregistry.com:5000/awesomesql:1.0' // Will be pushed to custom repo in private registry 
  }
  privatePullImage {
    image = 'myotherregistry.com:5000/customapp'      // Tag 'latest' will be pulled from private registry without login
  }
}
```

## Generate docker-compose.yml for deployment

For every subproject a task named `createComposeFile` will be created. This will create a 
compose file that can be used to deploy your app configuration:

```gradle
dcompose {
  database {
    baseDir = "..."
  }
  databaseTest {
    image = "..."
    deploy = false // Set to false to NOT include this service in the compose file.
  }
}
createComposeFile {
  target = file("$buildDir/docker-compose.yml") // default
    
  // If you need to modify the compose file before it is saved:
  beforeSave { config ->
    config.networks.default = [
      driver: 'overlay'
    ]
  }
}
```

All locally built services will first create an image and then include the image tag
instead of including the build definitions. This aims at providing "deployable" compose
files.

**Please note:** _As of now generating compose files is not supported for cross-project setups._

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

The default syntax for referencing services within a project is to reference them 
with their name like ```dcompose.myService```. In order to also support Multi-project 
setups, there is another syntax: ```dcompose.service(':project:myService')```. This
allows to reference a service from another project. _FYI: Although this looks like a 
Gradle task path it is actually referencing a service within that project.

Starting with version 0.5.0 all services in a subproject will automatically be connected
through the subproject's `default` network. In order to get inter-subproject communication
you need to connect the services to the same network.

```gradle
project(':prjA') {
  apply plugin: 'com.chrisgahlert.gradle-dcompose-plugin'
  
  dcompose {
    networks {
      backend
    }
    server {
      image = '...'
      exposedPorts = ['8080']
      volumes = ['/var/log']
      networks = [backend]
    }
  }
}

project(':prjB') {
  apply plugin: 'com.chrisgahlert.gradle-dcompose-plugin'
  
  dcompose {
    networks {
      frontend 
    }
    client {
      image = '...'
      links = [
        service(':prjA:server').link(),       // Container alias will default to 'server'
        service(':prjA:server').link('alias') // Or you can manually define an alias
      ]
      volumesFrom = [
        service(':prjA:server')
      ]
      networks = [ network(':prjA:backend'), frontend ] // If defined we don't need the links definition
    }
  }
  
  
  task copyFiles(type: DcomposeCopyFileFromContainerTask) {
    service = dcompose.service(':prjA:server')
    containerPath = '/some/dir/or/file'
  }
}
```

*Please note:* When using this plugin together with Gradle's _Configuration on Demand_, executing
a destructive task like _stop\*_ or _remove\*_ will trigger the evaluation of all projects.
