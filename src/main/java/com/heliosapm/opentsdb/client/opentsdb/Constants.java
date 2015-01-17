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

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * <p>Title: Constants</p>
 * <p>Description: HttpMetricsPoster configuration constants</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.Constants</code></p>
 */

/**
 * <p>Title: Constants</p>
 * <p>Description: </p> 
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
	public static final boolean DEFAULT_COMPRESS = false;
	/** The system property config name for the maximum number of retries */
	public static final String PROP_REQUEST_RETRY = "tsdb.http.request.retries";
	/** The default HTTP Payload compression enablement */
	public static final int DEFAULT_REQUEST_RETRY = 2;
	/** The system property config name for the retry delay in ms. */
	public static final String PROP_REQUEST_RETRY_DELAY = "tsdb.http.request.retries.delay";
	/** The default retry delay in ms. */
	public static final int DEFAULT_REQUEST_RETRY_DELAY = 2000;
	
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
	public static final String PROP_OFFLINE_DIR = "tsdb.http.offline.dir";
	/** The default offline metric persistence file pattern */
	public static final String DEFAULT_OFFLINE_DIR = System.getProperty("user.home") + File.separator + ".tsdb-metrics";
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
	//     Response Tracking
	//======================================================================================================================
	/** The system property config name for tracking OpenTSDB responses for successful and failed metric submissions  */
	public static final String PROP_TRACK_RESPONSES = "tsdb.responses.track";
	/** The default OpenTSDB response tracking (true to track, false otherwise) */
	public static final boolean DEFAULT_TRACK_RESPONSES = true;
	/** The system property config name for tracking OpenTSDB response counts only */
	public static final String PROP_TRACK_COUNTS_ONLY = "tsdb.responses.track.countsonly";
	/** The default counts only OpenTSDB response tracking (true to track counts only, false to get invalid metrics) */
	public static final boolean DEFAULT_TRACK_COUNTS_ONLY = true;
	/** The system property config name for supressing metrics reported as invalid in OpenTSDB responses */
	public static final String PROP_SUPPRESS_BAD_METRICS = "tsdb.responses.suppress";
	/** The default bad metrics suppression based on OpenTSDB failure responses tracking (true to supress, false otherwise). 
	 * Requires {@link #DEFAULT_TRACK_RESPONSES} to be <b>true</b> and {@link #DEFAULT_TRACK_COUNTS_ONLY} to be <b>false</b>.
	 * Has some overhead if enabled.
	 */
	public static final boolean DEFAULT_SUPPRESS_BAD_METRICS = false;
	
	/** The system property config name for the logging level to log bad metrics at */
	public static final String PROP_BAD_METRICS_LEVEL = "tsdb.responses.track.badlevel";
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
	/** The system property config name for a prop where the value is a JS script that will compute the app name */ 
	public static final String JS_APP_NAME = "tsdb.id.app.js";
	
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
	
	
	
	
	/** The character set used by OpenTSDB */
	public static final Charset CHARSET = Charset.forName("ISO-8859-1");  //"UTF-8"
	
	
	
}
