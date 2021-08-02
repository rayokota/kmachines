# KMachines - Distributed Fine-Grained Finite State Machines with Kafka

[![Build Status][github-actions-shield]][github-actions-link]
[![Maven][maven-shield]][maven-link]
[![Javadoc][javadoc-shield]][javadoc-link]

[github-actions-shield]: https://github.com/rayokota/kmachines/workflows/build/badge.svg?branch=master
[github-actions-link]: https://github.com/rayokota/kmachines/actions
[maven-shield]: https://img.shields.io/maven-central/v/io.kmachine/kmachines-core.svg
[maven-link]: https://search.maven.org/#search%7Cga%7C1%7Cio.kmachine
[javadoc-shield]: https://javadoc.io/badge/io.kmachine/kmachines-core.svg?color=blue
[javadoc-link]: https://javadoc.io/doc/io.kmachine/kmachines-core

KMachines is a client layer for distributed processing of fine-grained finite state machines with Apache Kafka. 

## Installing

Releases of KMachines are deployed to Maven Central.

```xml
<dependency>
    <groupId>io.kmachine</groupId>
    <artifactId>kmachines-core</artifactId>
    <version>0.0.2</version>
</dependency>
```

For more info on KMachines, see this [blog post](https://yokota.blog/2021/08/02/the-enterprise-is-made-of-events-not-things/).
