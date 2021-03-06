/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.heliosapm.opentsdb.client.opentsdb;

import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Level;

import com.heliosapm.opentsdb.client.logging.LoggingConfiguration;
import com.heliosapm.opentsdb.client.opentsdb.ConnectivityChecker.HTTPMethod;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: Constants</p>
 * <p>Description: HttpMetricsPoster configuration constants</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.Constants</code></p>
 */

public interface Constants {
	/** The JVM's PID */
	public static final String SPID = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
	/** The JVM's host name according to the RuntimeMXBean */
	public static final String HOST = ManagementFactory.getRuntimeMXBean().getName().split("@")[1];
	/** The available core count */
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	/** Indicates if we're running on Windows */
	public static final boolean IS_WIN = System.getProperty("os.name", "").toLowerCase().contains("windows");
	
	/** Indicates if we're running Java 7+ */
	public static final boolean IS_JAVA_7 = Util.loadClassByName("java.lang.management.BufferPoolMXBean", null)!=null;
	/** Indicates if we're running Java 8+ */
	public static final boolean IS_JAVA_8 = Util.loadClassByName("java.util.concurrent.DoubleAdder", null)!=null;
	
	/** The system property config name for pooling HTTP connections (i.e. enable KeepAlive) */
	public static final String PROP_POOL_CONNS = "tsdb.http.connection.pooling";
	/** The default http connection pooling */
	public static final boolean DEFAULT_POOL_CONNS = true;
	/** The system property config name for the maximum number of concurrent buffered metric flushes */
	public static final String PROP_MAX_CN_FLUSH = "tsdb.http.max.flushes";
	/** The default maximum number of concurrent buffered metric flushes */
	public static final int DEFAULT_MAX_CN_FLUSH = CORES;
	
	/** The system property config name for HTTP Payload compression */
	public static final String PROP_COMPRESS = "tsdb.http.compression.enabled";
	/** The default HTTP Payload compression enablement */
	public static final boolean DEFAULT_COMPRESS = true;
	/** The system property config name for the maximum number of retries */
	public static final String PROP_REQUEST_RETRY = "tsdb.http.request.retries";
	/** The default HTTP Payload compression enablement */
	public static final int DEFAULT_REQUEST_RETRY = 2;
	/** The system property config name for the retry delay in ms. */
	public static final String PROP_REQUEST_RETRY_DELAY = "tsdb.http.request.retries.delay";
	/** The default retry delay in ms. */
	public static final int DEFAULT_REQUEST_RETRY_DELAY = 2000;

	// =======================================
	// Metric Sink
	// =======================================
	
	/** The system property config name for the metric sink buffer size threshold trigger */
	public static final String PROP_SINK_SIZE_TRIGGER = "tsdb.metricsink.trigger.size";
	/** The default metric sink buffer size threshold trigger */
	public static final int DEFAULT_SINK_SIZE_TRIGGER = 100;
	/** The system property config name for the metric sink buffer time threshold trigger */
	public static final String PROP_SINK_TIME_TRIGGER = "tsdb.metricsink.trigger.size";
	/** The default metric sink buffer time threshold trigger in ms. */
	public static final long DEFAULT_SINK_TIME_TRIGGER = 5000;
	/** The system property config name for the metric sink input queue size */
	public static final String PROP_SINK_INPUT_QSIZE = "tsdb.metricsink.inputq.size";
	/** The default metric sink input queue size */
	public static final int DEFAULT_SINK_INPUT_QSIZE = 5000;
	/** The system property config name for the metric sink input queue fairness */
	public static final String PROP_SINK_INPUT_QFAIR = "tsdb.metricsink.inputq.fair";
	/** The default metric sink input queue size */
	public static final boolean DEFAULT_SINK_INPUT_QFAIR = false;
	

	// =======================================
	// LongIdOTMetricCache
	// =======================================

	/** The system property config name for the LongIdOTMetricCache initial cache size */
	public static final String PROP_OPT_CACHE_INIT_SIZE = "tsdb.optcache.initsize";
	/** The default LongIdOTMetricCache initial cache size */
	public static final int DEFAULT_OPT_CACHE_INIT_SIZE = 512;
	/** The system property config name for the LongIdOTMetricCache space for speed option */
	public static final String PROP_OPT_CACHE_SPACE_FOR_SPEED = "tsdb.optcache.space4speed";
	/** The default LongIdOTMetricCache space for speed option*/
	public static final boolean DEFAULT_OPT_CACHE_SPACE_FOR_SPEED = false;
	
	
	/** The system property config name for the http request timeout */
	public static final String PROP_REQUEST_TIMEOUT = "tsdb.http.request.timeout";
	/** The default http request timeout in ms. */
	public static final int DEFAULT_REQUEST_TIMEOUT = 1500;
	/** The system property config name for the http connection timeout */
	public static final String PROP_CONNECTION_TIMEOUT = "tsdb.http.connection.timeout";
	/** The default http connection timeout in ms */
	public static final int DEFAULT_CONNECTION_TIMEOUT = 5000;
	/** The system property config name for the opentsdb http endpoint */
	public static final String PROP_TSDB_URL = "tsdb.http.tsdb.url";
	/** The default opentsdb http endpoint */
	public static final String DEFAULT_TSDB_URL = "http://localhost:4242";
	/** The system property config name for the reconnect period */
	public static final String PROP_RECONNECT_PERIOD = "tsdb.http.connection.retry";
	/** The default reconnect period in seconds */
	public static final int DEFAULT_RECONNECT_PERIOD = 5;
	/** The system property config name for the submission batch size */
	public static final String PROP_BATCH_SIZE = "tsdb.http.batch.size";
	/** The default submission batch size */
	public static final int DEFAULT_BATCH_SIZE = 100;
	/** The system property config name for the offline metric persistence file-system directory */
	public static final String PROP_OFFLINE_DIR = LoggingConfiguration.PROP_OFFLINE_DIR;
	/** The default offline metric persistence file pattern */
	public static final String DEFAULT_OFFLINE_DIR = LoggingConfiguration.DEFAULT_OFFLINE_DIR;
	/** The system property config name for the maximum size of an offline metric persistence file  */
	public static final String PROP_OFFLINE_FILE_MAXSIZE = "tsdb.http.offline.maxsize";
	/** The default maximum size of an offline metric persistence file */
	public static final int DEFAULT_OFFLINE_FILE_MAXSIZE = 2048000;

	
	/** The system property config name for the http proxy to use */
	public static final String PROP_CONNECTION_PROXY = "tsdb.http.connection.proxy";
	/** The default http proxy */
	public static final String DEFAULT_CONNECTION_PROXY = "";
	/** The system property config name for the http proxy user name */
	public static final String PROP_CONNECTION_PROXY_USER = "tsdb.http.connection.proxy.user";
	/** The default http proxy user name*/
	public static final String DEFAULT_CONNECTION_PROXY_USER = "";
	/** The system property config name for the http proxy user password */
	public static final String PROP_CONNECTION_PROXY_PW = "tsdb.http.connection.proxy.pw";
	/** The default http proxy user password */
	public static final String DEFAULT_CONNECTION_PROXY_PW = "";

	
	/** The system property config name for the shared thread pool size  */
	public static final String PROP_TPOOL_SIZE = "tsdb.threadpool.size";
	/** The default pool size in the shared thread pool */
	public static final int DEFAULT_TPOOL_SIZE = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors()*2+2;
	
	/** The system property config name for the shared thread pool submission queue size  */
	public static final String PROP_TPOOL_QSIZE = "tsdb.threadpool.qsize";
	/** The default submission queue size in the shared thread pool. */
	public static final int DEFAULT_TPOOL_QSIZE = 1024;

	/** The system property config name for the tick size in the shared scheduler  */
	public static final String PROP_SCHEDULER_TICK = "tsdb.scheduler.size.ticks";
	/** The default the tick size in the shared scheduler in ms. */
	public static final int DEFAULT_SCHEDULER_TICK = 500;

	/** The system property config name for the wheel size in the shared scheduler  */
	public static final String PROP_SCHEDULER_WHEEL = "tsdb.scheduler.size.wheel";
	/** The default wheel size in the shared scheduler in ms. */
	public static final int DEFAULT_SCHEDULER_WHEEL = 60 * 60 * 2;

	//======================================================================================================================
	//     Tracing
	//======================================================================================================================
	
	/** The system property config name to make the tracer print to StdOut rather than send to OpenTSDB */
	public static final String PROP_TRACE_TO_STDOUT = "tsdb.trace.stdout";
	/** The default stdout tracing enablement */
	public static final boolean DEFAULT_TRACE_TO_STDOUT = false;
	
	/** The system property config name to make the tracer append to the agent's log in addition to other endpoints.
	 * The vaue of the property is the name of the logger to log to, disabling log tracing if null or empty. */
	public static final String PROP_TRACE_LOGNAME = "tsdb.trace.logname";
	/** The default trace logging logger name */
	public static final String DEFAULT_TRACE_LOGNAME = "";
	
	
	/** The system property config name to make the tracer print in JSON when outputing to StdOut */
	public static final String PROP_STDOUT_JSON = "tsdb.trace.stdout.json";
	/** The default stdout json tracing enablement */
	public static final boolean DEFAULT_STDOUT_JSON = true;
	
	//======================================================================================================================
	//     Response Tracking
	//======================================================================================================================
	/** The system property config name for the OpenTSDB response handler for successful and failed metric submissions  */
	public static final String PROP_PUT_RESPONSE_HANDLER = "tsdb.response.handler";
	/** The default OpenTSDB response tracking handler */
	public static final OpenTsdbPutResponseHandler DEFAULT_PUT_RESPONSE_HANDLER = OpenTsdbPutResponseHandler.ERRORS;
	
	/** The system property config name for the logging level to log bad metrics at */
	public static final String PROP_BAD_METRICS_LEVEL = "tsdb.response.handler.badlevel";
	/** The default level to log bad metrics at */
	public static final Level DEFAULT_BAD_METRICS_LEVEL = Level.INFO;
	
	//======================================================================================================================
	
	
	/** The system property config name for the metric submitting host name  */
	public static final String PROP_HOST_NAME = "tsdb.id.host";
	/** The system property config name for the metric submitting app name  */
	public static final String PROP_APP_NAME = "tsdb.id.app";
	/** The system property config name for a system property or env var that specifies the app name.
	 * If the prop value starts with <b><code>e:</code></b>, will inspect the environment, otherwise,
	 * looks for a system prop. 
	 */ 
	public static final String SYSPROP_APP_NAME = "tsdb.id.app.prop";
	/** The system property config name for reading a remote app name  */
	public static final String REMOTE_PROP_APP_NAME = "remote.tsdb.id.app";
	/** The system property config name for reading a remote host name  */
	public static final String REMOTE_PROP_HOST_NAME = "remote.tsdb.id.host";
	
	/** The system property config name for a prop where the value is a JS script that will compute the app name */ 
	public static final String JS_APP_NAME = "tsdb.id.app.js";
	/** The system property config name for a prop where the value is a JS script that will compute remote app name */ 
	public static final String REMOTE_JS_APP_NAME = "remote.tsdb.id.app.js";
	/** The system property config name for a prop where the value is a JS script that will compute remote host name */ 
	public static final String REMOTE_JS_HOST_NAME = "remote.tsdb.id.host.js";
	
	/** The name of the file used to lock the metric persistence directory */
	public static final String LOCK_FILE_NAME = ".tsdb.lock";
	
	/** The system property config name for additional JVM wide standard tags for all submitted metrics
	 * in addition to <b><code>host</code></b> and <b><code>app</code></b>. Format should be comma separated values:
	 * <b><code>key1=val1,key2=val2,keyn=valn</code></b>. 
	 */
	public static final String PROP_EXTRA_TAGS = "tsdb.tags.extra";
	
    /** The global tag name for the host */
    public static final String HOST_TAG = "host";
    /** The global tag name for the app */
    public static final String APP_TAG = "app";
    
    
    /** Comma separated Key/Value pattern splitter */
    public static final Pattern KVP_PATTERN = Pattern.compile("\\s*?([^,\\s].*?)\\s*?=\\s*?([^,\\s].*?)\\s*?");

	/** The system property config name for the OpenTsdbMetricRegistry metric map size */
	public static final String PROP_REG_MAP_SIZE = "tsdb.reg.mapsize";    
	/** The default OpenTsdbMetricRegistry metric map size */
	public static final int DEFAULT_REG_MAP_SIZE = 32;

	
	
	/** The character set used by OpenTSDB */
	public static final Charset CHARSET = Charset.forName("ISO-8859-1");
	/** The UTF8 character set */
	public static final Charset UTF8 = Charset.forName("UTF-8");
	
	/** The system property config name for the HTTP endpoint check query path */
	public static final String PROP_CHECK_ENDPOINT = "tsdb.http.check.path";
	/** The default HTTP endpoint check query path */
	public static final String DEFAULT_CHECK_ENDPOINT = "/api/version";

	/** The system property config name for the HTTP endpoint check period in seconds */
	public static final String PROP_CHECK_PERIOD = "tsdb.http.check.period";
	/** The default HTTP endpoint check query path */
	public static final int DEFAULT_CHECK_PERIOD = 5;
	
	/** The system property config name for the HTTP endpoint check HTTP method */
	public static final String PROP_CHECK_METHOD = "tsdb.http.check.method";
	/** The default HTTP endpoint check HTTP method */
	public static final HTTPMethod DEFAULT_CHECK_METHOD = HTTPMethod.GET;
	
	/** The system property config name for the heartbeat metric submssion period  in seconds. Less than 1 disables */
	public static final String PROP_HEARTBEAT_PERIOD = "tsdb.heartbeat.period";
	/** The default hearbeat period in seconds */
	public static final int DEFAULT_HEARTBEAT_PERIOD = 5;
	
	/** The system property config name for the value to send in heartbeat metrics */
	public static final String PROP_HEARTBEAT_VALUE = "tsdb.heartbeat.value";
	/** The default hearbeat metric value */
	public static final int DEFAULT_HEARTBEAT_VALUE = 100;
	
	
	/** The system property config name for the heartbeat metric name. Tags will be the host and app. */
	public static final String PROP_HEARTBEAT_METRIC = "tsdb.heartbeat.metric";
	/** The default heartbeat metric name */
	public static final String DEFAULT_HEARTBEAT_METRIC = "heartbeat";

	/** The system property config name for the time reporting granularity where true is seconds, false is milliseconds. */
	public static final String PROP_TIME_IN_SEC = "tsdb.time.seconds";
	/** The default hearbeat period in seconds */
	public static final boolean DEFAULT_TIME_IN_SEC = true;

	/** The system property config name for the on heap OTMetric guava cache spec. */
	public static final String PROP_OTMETRIC_CACHE_SPEC = "tsdb.otmetric.cachespec";
	/** The default on heap OTMetric guava cache spec */
	public static final String DEFAULT_OTMETRIC_CACHE_SPEC = String.format("concurrencyLevel=%s,initialCapacity=512,maximumSize=4096,recordStats", CORES);

	// =======================================
	// Instrumentation Providers
	// =======================================
	/** The system property name for the default domain of the target MBeanServer */
	public static final String PROP_INSTR_PROV_DOMAIN = "tsdb.aop.instr.domain";
	/** The system property name for the object name of the target mbean */
	public static final String PROP_INSTR_PROV_ON = "tsdb.aop.instr.on";
	/** The system property name for the attribute name of the attribute providing the Instrumentation */
	public static final String PROP_INSTR_PROV_ATTR = "tsdb.aop.instr.attr";
	/** The system property name for the class name containing the instrumentation in a static field */
	public static final String PROP_INSTR_PROV_CLASS = "tsdb.aop.instr.statclass";
	/** The system property name for the static field containing the instrumentation */
	public static final String PROP_INSTR_PROV_FIELD = "tsdb.aop.instr.statfield";

	
	// =======================================
	// JMX Domain Properties
	// =======================================

	/** The system property name for the default-domain of the default MBeanServer  */
	public static final String PROP_JMX_DOMAIN_PROPERTY = "tsdb.jmx.server.domain";
	/** The default default-domain of the default MBeanServer which is the platform MBeanServer */
	public static final String DEFAULT_JMX_DOMAIN_PROPERTY = "DefaultDomain";
	/** The system property config name for the JMX domain where csf MBeans are registered */
	public static final String PROP_JMX_DOMAIN = "tsdb.jmx.domain";
	/** The default JMX domain where csf MBeans are registered */
	public static final String DEFAULT_JMX_DOMAIN = "DefaultDomain"; //"com.heliosapm.tsdb";

	// ============================================
	// Local JVM Platform JMX Collection Properties
	// ============================================	

	/** The system property config name for the live GC event tracing */
	public static final String PROP_JMX_LIVE_GC_TRACING = "tsdb.jmx.livegc";
	/** The default JMX domain where csf MBeans are registered */
	public static final boolean DEFAULT_JMX_LIVE_GC_TRACING = false;
	
	/** The system property config name for enabling tracing of hotspot internals 
	 * Valid values are: <ul>
	 * <li><b><code>Compilation</code></b></li>
	 * <li><b><code>Classloading</code></b></li>
	 * <li><b><code>Runtime</code></b></li>
	 * <li><b><code>Threading</code></b></li>
	 * <li><b><code>Memory</code></b></li>
	 * */
	public static final String PROP_JMX_HOTSPOT_TRACING = "tsdb.jmx.hotspot";
	/** The default hotspot mbean short names enabled for tracing */
	public static final String[] DEFAULT_JMX_HOTSPOT_TRACING = {};
	
	/** The default counter name pattern */
	public static final String HOTSPOT_DEFAULT_PATTERN = "!";
	
	/** The configuration property name for hotspot compilation internal counter names to trace */
	public static final String PROP_JMX_HOTSPOT_COMPILATION = "tsdb.jmx.hotspot.compilation";
	/** The default hotspot compilation internal counter names to trace */
	public static final String DEFAULT_JMX_HOTSPOT_COMPILATION = "sun\\.ci\\.(.*)";
	/** The configuration property name for hotspot classloading internal counter names to trace */
	public static final String PROP_JMX_HOTSPOT_CLASSLOADING = "tsdb.jmx.hotspot.classloading";
	/** The default hotspot classloading internal counter names to trace */
	public static final String DEFAULT_JMX_HOTSPOT_CLASSLOADING = "sun\\.cls\\.(.*)";
	/** The configuration property name for hotspot runtime internal counter names to trace */
	public static final String PROP_JMX_HOTSPOT_RUNTIME = "tsdb.jmx.hotspot.runtime";
	/** The default hotspot runtime internal counter names to trace */
	public static final String DEFAULT_JMX_HOTSPOT_RUNTIME = "sun\\.rt\\.(?:_sync_)?(.*)";
	/** The configuration property name for hotspot threading internal counter names to trace */
	public static final String PROP_JMX_HOTSPOT_THREADING = "tsdb.jmx.hotspot.threading";
	/** The default hotspot threading internal counter names to trace */
	public static final String DEFAULT_JMX_HOTSPOT_THREADING = "java\\.threads\\.(.*)";
	/** The configuration property name for hotspot memory internal counter names to trace */
	public static final String PROP_JMX_HOTSPOT_MEMORY = "tsdb.jmx.hotspot.memory";
	/** The default hotspot memory internal counter names to trace */
	public static final String DEFAULT_JMX_HOTSPOT_MEMORY = "sun\\.gc\\.(?:(policy)|(tlab))\\.(.*)";
	

	
	
	//======================================================================================================================
	//     Java Agent Props
	//======================================================================================================================

	
	
	
	//======================================================================================================================
	//     Misc Props
	//======================================================================================================================	
	/** The configuration property name for forcing all trace content to lower case  */
	public static final String PROP_FORCE_LOWER_CASE = "tsdb.trace.lc";
	/** The default force all lower case tracing */
	public static final boolean DEFAULT_FORCE_LOWER_CASE = true;
	/** The configuration property name for only tracing the short host name (rather than the FQN) */
	public static final String PROP_USE_SHORT_HOSTNAMES = "tsdb.hostname.short";
	/** The default short host name */
	public static final boolean DEFAULT_USE_SHORT_HOSTNAMES = true;
	

	//======================================================================================================================
	//     Shortand Compiler Props
	//======================================================================================================================
	/** The system property name for the compiler tolerance of the shorthand compiler */
	public static final String PROP_SHORTHAND_TOLERANT_PROPERTY = "tsdb.aop.shorthand.tolerant";
	/** The default default-domain of the default MBeanServer which is the platform MBeanServer */
	public static final boolean DEFAULT_SHORTHAND_TOLERANT_PROPERTY = false;
	
	
}
