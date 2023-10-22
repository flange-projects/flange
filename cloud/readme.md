# Flange Cloud™

_Design a monolith. Deploy cloud native._

Flange Cloud transparently transforms and deploys a well-designed monolith application to a distributed, elastic, highly-scalable cloud-native application. Flange merely requires that your application architecture have well-placed “perforations” reflecting a modular design—which in itself is a good practice.

### A “Perforated Monolith”

A “perforated monolith” is a single application that can be run as an independent unit, while still following good architectural principles such as separation of concerns and loose coupling. It follows the best practice of programming to interfaces, while additionally minding latency and possible error conditions at interface boundaries (“perforations”) that potentially represent distributed communication.

The term “perforation” comes from [Jason Katzer](https://www.jasonkatzer.com/) in his book [_Learning Serverless_](https://www.oreilly.com/library/view/learning-serverless/9781492057000/). Although Flange was designed independently, and was already implemented as a working proof of concept without knowledge of the specific content in that book, Jason's recommendation for “perforating a monolith” nevertheless aligns precisely with the philosophy of Flange Cloud:

> [A monolith application designed with a] clear separation of concerns and a well-defined and specified contract between the … components; … would be **perforated for future separation** when it would inevitably be required to be split out of the monolith. _[emphasis added]_
>
> You can build your monolith with the patterns of microservices but without their plumbing and overhead. … Take all of the principles espoused in this book: clean separation of concerns, loosely coupled and highly independent services, and consistent interfaces. Keep these in the same monolithic app, and never compromise on these rules. The result will be a monolith that is baked to perfection and ready to be carved up later.
>
> [_Learning Serverless, Chapter 2. Microservices_](https://www.oreilly.com/library/view/learning-serverless/9781492057000/) (O'Reilly, 2020)

In this description Jason gives no indication that “splitting … the monolith” could be anything but the painful, error-prone drudgework it has always been historically. Flange changes all that! Flange allows you to keep your well-architected, perforated monolith—automatically “spliting” your monolith and deploying it transparently to the cloud as cloud-native components. Flange will add the “plumbing” as needed.

* You can run and test your perforated monolith  on your machine as much as much as you like.
* You can tell Flange to deploy your perforated monolith to the cloud, where you can run it directly from your cloud platform.
* You can even tell Flange to run your application driver component locally, yet have it transparently access the distributed components “across the perforations” to the cloud.

_Flange is in early, rapid development. Currently Flange supports deployment of services as Cloud Functions (FaaS) on the Amazon Web Services (AWS) cloud platform._

### Cloud Components

* Cloud Function (FaaS) Services
* Container Services (_upcoming_)
* Cloud Function (FaaS) APIs
* RESTful APIs (_upcoming_)
* Queues (_upcoming_)

### Cloud Platforms

* Amazon Web Services (AWS)
* Google Cloud (_upcoming_)
* Microsoft Azure (_upcoming_)

## Reference

### Configuration

* `flange.env`: The environment such as `dev`, `dev3`, or `dev-jdoe` which the application is using and/or on which the application is deployed.
* `flange.platform`: The platform on which the application is running, such as `aws`; used to load platform-specific dependencies, for example.
* `flange.profiles`: Indicates which Flange application profiles are in effect (i.e. active).
* `flange.stage`: The deployment stage, such as `dev`, `qa`, or `prod`; essentially a category of environment, and usually defined as part of the Flange environment.

### AWS-Specific Configuration

* `flange.aws.profile`: Indicates an AWS [named profile](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html#cli-configure-files-using-profiles) to use when running locally.
