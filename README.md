# tsdb-csf

This projects is forked from [metrics-opentsdb](https://github.com/sps/metrics-opentsdb) with thanks to [Sean Scanlon](mailto:sean.scanlon@gmail.com) for initiating it.

CSF is a java library to provide an agent that combines implementations of the following:
* An asynchronous HTTP client for sending metrics to an [OpenTSDB](http://opentsdb.net) endpoint.
* Embedded CodaHale/DropWizard [Metrics](http://dropwizard.io/) for collecting and accumulating metrics with OpenTSDB friendly Metrics extensions to assist in generating metric name tagging by converting the native flat metric formats to OpenTSDBs tag based format.
* A high speed persistence mechanism to store metrics to disk when the agent can't reach the OpenTSDB endpoint and flush the store when connectivity resumes.
* JMX exposed metric coverage of the agent's status and activity.
 

### More Detail on Features
* High performance file system metric storage and retrieval store to save collected metrics when the OpenTSDB endpoint is inaccessible.
* Background configurable OpenTSDB non-intrusive connectivity monitor. Issues JMX notifications on initial connect, disconnect and reconnect events.
* Automatic (configurable override) **host** and **app** tag values discovery to identify the provenance of all submitted metrics. If tags are provided, automatic tags are ignored to support monitoring of a remote app or service
* Bad metric name detection and suppression.
* Extensive JMX instrumentation.
* Automatically generated hearbeat metric generation. Optional app start and clean shutdown OpenTSDB annotation generation and submission.
* Fast and efficient GZIP enabled HTTP metric submission using Netty based [AsyncHttpClient](https://github.com/AsyncHttpClient/async-http-client). GZIP automatically disengaged if OpenTSDB does not support GZIP. (OpenTSDB <= 2.1).
* Tested with [Bosun](http://bosun.org).
* See (RoadMap).

### Getting Started (Build)
- Clone the project (on most planets, this means ```git clone https://github.com/nickman/tsdb-csf.git```)
- Navigate to the clone's directory
- Build with *maven3*: ```mvn clean install```
 
Public maven repo pending.

### Getting Started (Code)

*Note:*  References herein to "Metrics" (with the upper case M, yeah ?) refer to CodaHale/DropWizard [Metrics](http://dropwizard.io/), while references to "metrics" refer to several things and must be considered in context.

Here's a step-by-step of the current version of [KitchenSink](https://github.com/nickman/tsdb-csf/blob/master/csf-core/src/test/java/com/heliosapm/opentsdb/client/KitchenSink.java) which contrives one of each of the main metric types in CodaHale's Metrics.

##### Abbreviated java **main**:
```java
   public static void main(String[] args) {
      KitchenSink ks = new KitchenSink();
      System.out.println("KitchenSink Started");
   }
```

##### Declare Metrics Using OpenTSDB Style Namespaces:
Here we create a new MetricRegistry and define a set of metrics to register in it.
Nothing new to Metrics users here, except the OpenTSDB tag friendly names. (More on this below)
```java
	// Create a new MetricRegistry
	final MetricRegistry registry = new MetricRegistry();
	// Create a new Gauge Metric
	final Gauge<Long> cacheSizeGauge = new Gauge<Long>() {
		@Override
		public Long getValue() {		
			final Random random = new Random(System.currentTimeMillis());
			return new Long(Math.abs(random.nextInt(1000)));
		}
	};
	// Create a new Counter Metric
	final Counter evictions = registry.counter(name(getClass().getSimpleName(), "evictions", "cmtype=Counter", "op=cache-evictions", "service=cacheservice"));
	// Create a new Histogram Metric
	final Histogram resultCounts = registry.histogram(name(getClass().getSimpleName(), "resultCounts", "cmtype=Histogram", "op=cache-lookup", "service=cacheservice"));
	// Create a new Meter Metric
	final Meter lookupRequests = registry.meter(name(getClass().getSimpleName(), "lookupRequests",  "cmtype=Meter", "op=cache-lookup", "service=cacheservice"));
	// Create a new Timer Metric
	final Timer timer = registry.timer(name(getClass().getSimpleName(), "evictelapsed", "cmtype=Timer", "op=cache-evictions", "service=cacheservice"));
```
A traditional Metrics metric name for, say the *cacheSizeGauge* gauge, would look something like this:

<dd><code>KitchenSink.cacheservice.cache-size</code></dd>

OpenTSDB metrics, however, are *mapped*, or *tagged* formatted metrics. A fully qualified metric name consists of a **metric name** which broadly describes what the value is, and one or more tags which are name/value pairs that provide specificity as to the provenance of the metric value. Here's a classic and simple example (slightly modified from a Bosun created metric):

<dd><code>cpu.percpu:host=appserver9,type=idle,cpu=7</code></dd>


From this we divine:
* The *metric name* is **cpu.percpu** which means we're looking at CPU utilization on a *per cpu* basis (as opposed to aggregated CPU stats). 
* The host that this CPU resides on is **appserver9**.
* Of all the CPUs on the host **appserver9**, this metric is referring CPU **#7**.

If you're not familiar with this, I would shoot over to [OpenTSDB](http://opentsdb.net) and read their documentation on [Understanding Metrics and Time Series](http://opentsdb.net/docs/build/html/user_guide/query/timeseries.html)

One of the big motivations for starting this project was to provide a means of using Metrics while retaining very granular control of how OpenTSDB metric names are managed. Sensibly defined and implemented OpenTSDB metric names, especially tags, are of paramount importance as concerns the functionality and performance of OpenTSDB. 'nuff said ?

Back at the KitchenSink, we're using the MetricRegistry's static *name* method to generate what ends up being a fairly detailed (albeit contrived) OpenTSDB metric name (going forward, the OMN). Once again, here's the counter Metric creation:

```java
	// Create a new Counter Metric
	final Counter evictions = 
			registry.counter(name(getClass().getSimpleName(), 
					"evictions", 
					"op=cache-evictions", 
					"service=cacheservice"));
```

* The simple class name ("KitchenSink") becomes the first part of the OMN's *metric* portion. This is not strictly a popular naming convention, but this is contrived.
* The string "evictions" is not a tag by virtue of it's lack of a "=" symbol. Therefore, it is appended to the end of the existing OMN metric, bringing us to a metric of ```KitchenSink.evictions```. 
* The remaining strings to be appended are tags (yah, the '=') so they are appended as such.

The basis for the OMN is now ```KitchenSink.evictions:op=cache-evictions,service=cacheservice```, but we're not done yet. There's 2 more additions:

1. When the [OpenTsdbReporter](https://github.com/nickman/tsdb-csf/blob/master/csf-core/src/main/java/com/heliosapm/opentsdb/client/opentsdb/OpenTsdbReporter.java) kicks in, as most Metric Reporters do, all Metrics it reports for are decorated with some additional data indicating the meaning of the data. In some cases, there are more than one OMNs generated for each Metric. (Consider Timer which generates 15 metrics). In the case of Counter, there's a one-to-one, but the standard operating procedure for Metric reporters is to append the suffix **count** to indicate that the supplied value is in fact a count of something. The OpenTsdbReporter appends these informational suffixes to the end of the OMN metric name.l
2. tsdb-csf encourages each OMN to be tagged with a **host** and an **app** so that the source of the metrics is clear. There are several options as to how the values for these tags can be arrived at, but the defaults are supplied by a class called [AgentName](https://github.com/nickman/tsdb-csf/blob/master/csf-core/src/main/java/com/heliosapm/opentsdb/client/name/AgentName.java) which does a pretty decent job by itself, IMHO.
 
After these two additions, the OMN used to report the Counter metric will be: ```KitchenSink.evictions.count:app=KitchenSink,host=hserval,op=cache-evictions,service=cacheservice```

See [AllKitchenSinkMetrics](https://github.com/nickman/tsdb-csf/wiki/AllKitchenSinkMetrics) for a list of all the OMNs generated by KitchenSink.

For our efforts, here's how OpenTSDB sees it:

![](https://github.com/nickman/tsdb-csf/wiki/img/Selection_001.png)




### Licensing
TSDB-CSF retains the original license of [metrics-opentsdb](https://github.com/sps/metrics-opentsdb) which is [Apache 2.0](https://github.com/sps/metrics-opentsdb/blob/master/LICENSE.txt).
 
 .... and there are a handful of source files that have the wrong header. I will fix them....




