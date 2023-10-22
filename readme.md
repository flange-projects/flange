# Flange™

_“Managing the perforations.”™_

Flange is a suite of loosely-coupled libraries for managing the seams of your application—or in the case of [Flange Cloud](cloud/), the “perforations” of a distributed, elastic, highly-scalable cloud-native application.

* **Site**: [https://flange.dev/](https://flange.dev/)
* **Code**: Released code is available on GitHub under [`flange-projects`](https://github.com/flange-projects).
* **Issues**: Issues tracked by [Jira](https://globalmentor.atlassian.net/projects/FLANGE).
* **Artifacts**: Flange artifacts are available in the Maven Central Repository in group [dev.flange](https://central.sonatype.com/search?q=g:io.confound).

See the [Flange Examples repository](https://github.com/flange-projects/flange-examples) to dive right into real, working code.

## Flange Projects

### Flange Dependency Injection (DI)

The core Flange components handle dependency injection (DI). Flange DI presents a concise view of dependency injection, focusing on the best practices accumulated over the past two decades, and throwing out the cruft.

Flange lets you choose your own dependency injection container, including:

* Spring
* Guice (_upcoming_)
* Picocontainer (_upcoming_)
* Csardin (Flange's own tiny DI container; _upcoming_)

Flange presents a consistent interface regardless of which container you choose to use, and you can even swap out the implementation at any time. For example, to use Spring as your DI container, simply include the latest `dev.flange:flange-spring-simple-provider` as a dependency (in addition to the core dependency `dev.flange:flange`).

_Flange is in early, rapid development. Currently Flange supports the barest of DI configuration using Spring, but with full constructor injection capabilities. Flange DI support is sufficient for the current implementation of [Flange Cloud](cloud/), the current focus of development._

### Flange Cloud™

_Build a monolith. Deploy cloud native._

[Flange Cloud](cloud/) transparently transforms and deploys a well-designed monolith application to a distributed, elastic, highly-scalable cloud-native application. See its project page for more information.

## Reference

### Configuration

* `flange.env`: The environment such as `dev`, `dev3`, or `dev-jdoe` which the application is using and/or on which the application is deployed.
* `flange.platform`: The platform on which the application is running, such as `aws`; used to load platform-specific dependencies, for example.
* `flange.profiles`: Indicates which Flange application profiles are in effect (i.e. active).
* `flange.stage`: The deployment stage, such as `dev`, `qa`, or `prod`; essentially a category of environment, and usually defined as part of the Flange environment.
