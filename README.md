# tsdb-csf

This projects is forked from [metrics-opentsdb](https://github.com/sps/metrics-opentsdb) with thanks to [Sean Scanlon](mailto:sean.scanlon@gmail.com) for initiating it.

CSF is a java library to provide an agent that combines implementations of the following:
* An asynchronous HTTP client for sending metrics to an [OpenTSDB](http://opentsdb.net) endpoint.
* Embedded CodaHale/DropWizard [Metrics](http://dropwizard.io/) for collecting and accumulating metrics with OpenTSDB friendly Metrics extensions to assist in generating metric name tagging by converting the native flat metric formats to OpenTSDBs tag based format.
* A high speed persistence mechanism to store metrics to disk when the agent can't reach the OpenTSDB endpoint and flush the store when connectivity resumes.
* JMX exposed metric coverage of the agent's status and activity.
 

### More Detail on Features
TBD

### Getting Started
- Clone the project (on most planets, this means ```git clone https://github.com/nickman/tsdb-csf```)
- Navigate to the clone's directory
- Build with *maven3*: ```mvn clean install```
 
Public maven repo pending.


### Licensing
TSDB-CSF retains the original license of [metrics-opentsdb](https://github.com/sps/metrics-opentsdb) which is [Apache 2.0](https://github.com/sps/metrics-opentsdb/blob/master/LICENSE.txt).





