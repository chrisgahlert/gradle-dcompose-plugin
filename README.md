# gradle-dcompose-plugin

When working with Gradle projects, there has always been a "technology break" when using Docker. First you 
would need to run a build like ```gradle assemble``` and afterwards ```docker-compose up``` in order to test 
your code locally.

This plugin aims at fully integrating the Docker container management into the Gradle build itself thus 
eleminating the need for docker-compose. It uses Gradle's UP-TO-DATE checks in order to determine whether a 
container should be recreated.


# Prerequisites

This plugin requires: 
* Gradle >= 2.8 (older versions possibly working, but not tested)
* Docker (host) >= 1.12.0 (older versions possibly working, but not tested)
* **Please note:** Docker for Mac (native) currently doesn't support redirecting standard streams to/from a container.

However, you don't need the Docker commandline tools nor the docker-compose command to be installed, as this plugin talks directly to the Docker host effectively replacing them.

# Documentation
For the full documentation head over to the [wiki](https://github.com/chrisgahlert/gradle-dcompose-plugin/wiki)!

# Example

An example project says more than a thousand words:
https://github.com/chrisgahlert/gradle-dcompose-sample

# Quickstart

Make sure that you have Docker installed. Also make sure that the environment variable `DOCKER_HOST` is correctly
populated if using either a remote Docker daemon or something like Docker toolbox.

**Tip:** If you are using Docker for Win/Mac and have trouble authenticating against a private registry: Try 
disabling the option to store password in the OS' keychain (via Settings -> General)!

Next, add the following to your build.gradle file:

```gradle
plugins {
  id "com.chrisgahlert.gradle-dcompose-plugin" version "0.10.0-alpha1"
}

dcompose {
  web {
    image = 'nginx:latest'
    portBindings = ['8080:80']
  }
}
```

Now launch the build with `gradle startWebContainer` and head over to http://localhost:8080

# Advanced example 

#### src/main/docker/Dockerfile

```dockerfile
FROM 'nginx:latest'
COPY index.html /www/
```

#### build.gradle
```gradle
dcompose {
  networks {
    frontend
    backend
  }
  
  dbData {
    image = 'mongo:latest'
    volumes = ['/var/lib/mongodb']
    preserveVolumes = true
  }
  db {
    image = 'mongo:latest'
    volumesFrom = [dbData]
    networks = [backend]
    aliases = ['mongo_db']
  }
  cache {
    image = 'redis:latest'
    networks = [backend]
  }
  web {
    buildFiles = project.copySpec {
      from 'src/main/www/' // Contains index.html
      from 'src/main/docker/'
    }
    env = ['MONGO_HOST=mongo_db', 'REDIS_HOST=cache']
    repository = 'someuser/mywebimage:latest'
    networks = [frontend, backend]
  }
}

```

#### Running

Launch the build by running `gradle startWebContainer`. This should automatically 
pull/create/start all required containers. Whenever something changes, you just need 
to re-run this build and everything, that needs to be recreated will be.

# Mentions

* Blog entry [Using a containerized database for testing Spring Boot applications](http://sanchezdale.me/using-a-containerized-database-for-testing-spring-boot-applications/) by Daniel Sanchez
* Blog entry [Don't use In-Memory Databases (like H2) for Tests](https://blog.philipphauer.de/dont-use-in-memory-databases-tests-h2/) by Philipp Hauer
* Talk [TESTUMGEBUNGEN VERSCHIFFEN - DOCKER FÜR INTEGRATIONSTESTS](https://consulting.hildebrandt.tk/vortraege/docker-fuer-integrationstests/slides/index.html#/67) by Stefan Hildebrandt   
