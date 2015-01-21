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

import java.util.Map;
import java.util.Set;


/**
 * <p>Title: HttpMetricsPosterMXBean</p>
 * <p>Description: JMX MXBean interface for {@link HttpMetricsPoster}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.HttpMetricsPosterMXBean</code></p>
 */

public interface HttpMetricsPosterMXBean {
	
	/** Notification type for initial connection */
	public static final String NOTIF_CONNECTED = "opentsdb.connected";
	/** Notification type for reconnection */
	public static final String NOTIF_RECONNECTED = "opentsdb.reconnected";
	/** Notification type for disconnection */
	public static final String NOTIF_DISCONNECTED = "opentsdb.disconnected";
	/** Notification type for gzip auto-disabled */
	public static final String NOTIF_GZIP_DISABLED = "opentsdb.nogzip";
	
	/**
	 * Returns the total number of successfully sent metrics
	 * @return the total number of successfully sent metrics
	 */
	public long getSentMetrics();
	
	/**
	 * Returns a set of all the metric names in the registry
	 * @param recurse true to recurse through metric sets, false for top level only
	 * @return all the metric names in the registry
	 */
	public Set<String> dumpMetricNames(boolean recurse);	
	
	/**
	 * Returns the total number of metrics in all OpenTsdbReported registries
	 * @return the total number of metrics
	 */
	public int getMetricCount();	
	
	/**
	 * Determines if the connectivity checker is connected
	 * @return true if the connectivity checker is connected, false otherwise
	 */
	public boolean isConnected();
	
	/**
	 * Indicates if the metrics poster is hard down.
	 * If this value is true, metric submissions are automatically buffered.
	 * @return true if the metrics poster is hard down, false otherwise.
	 */
	public boolean isHardDown();
	
	/**
	 * Returns the total number of buffered metrics
	 * @return the total number of buffered metrics
	 */
	public long getBufferedMetrics();
	
	/**
	 * Returns the last successful metric send elapsed time in ms.
	 * @return the last successful metric send elapsed time
	 */
	public long getLastSendTime();
	
	/**
	 * Returns the number of pending metric stores
	 * @return the number of pending metric stores
	 */
	public long getPendingMetricStores();
	
	/**
	 * Indicates if compression is enabled
	 * @return true if compression is enabled, false otherwise
	 */
	public boolean isEnableCompression();


	/**
	 * Sets the enabled state of request compression
	 * @param enableCompression true to enable false to disable
	 */
	public void setEnableCompression(final boolean enableCompression);


	/**
	 * Returns the failed request retry count
	 * @return the failed request retry count
	 */
	public int getRetryCount();
	
	/**
	 * Returns the current batch size controlling how many metrics the OpenTsdbReporter
	 * sends at one time.
	 * @return the batch size
	 */
	public int getBatchSize();
	


	/**
	 * Sets the the failed request retry count
	 * @param retryCount the retryCount to set
	 */
	public void setRetryCount(final int retryCount);


	/**
	 * Returns the failed request retry delay in ms
	 * @return the failed request retry delay
	 */
	public int getRequestRetryDelay();


	/**
	 * Sets the failed request retry delay in ms
	 * @param requestRetryDelay failed request retry delay
	 */
	public void setRequestRetryDelay(final int requestRetryDelay);


	/**
	 * Returns the request timeout in ms
	 * @return the requestTimeout
	 */
	public int getRequestTimeout();


	/**
	 * Returns the connection timeout in ms. 
	 * @return the connectionTimeout
	 */
	public int getConnectionTimeout();


	/**
	 * Returns the current TSDB Http metric submission endpoint 
	 * @return the current TSDB Http metric submission endpoint
	 */
	public String getTsdbUrl();


	/**
	 * Sets the TSDB Http metric submission endpoint 
	 * @param tsdbUrl the tsdbUrl to set
	 */
	public void setTsdbUrl(final String tsdbUrl);


	/**
	 * Sets the submission batch size
	 * @param batchSize the batch size to set
	 */
	public void setBatchSize(final int batchSize);				

	/**
	 * Returns the name of the offline persistence directory
	 * @return the name of the offline persistence directory
	 */
	public String getOfflineDir();
	
	/**
	 * Returns the number of offline files
	 * @return the number of offline files
	 */
	public int getOfflineFileCount();
	
	/**
	 * Returns the number of offline metric collections within the offline files
	 * @return the number of offline metric collections
	 */
	public int getOfflineEntryCount();

	/**
	 * Returns the current offline file name
	 * @return the current offline file name
	 */
	public String getOfflineFile();
	
	
	/**
	 * Returns the current offline file size
	 * @return the current offline file size, or -1L if there is no current file
	 */
	public long getOfflineFileSize();
	
	/**
	 * Returns the current offline file entry count
	 * @return the current offline file entry count, or -1 if there is no current file
	 */
	public int getOfflineFileEntries();
	
	/**
	 * Returns the current offline file compression rate
	 * @return the current offline file compression rate, or -1d if there is no current file
	 */
	public double getOfflineFileCompressionRate();
	
	/**
	 * Indicates if the current offline file is compressed
	 * @return true if the current offline file is compressed, false if not or there is no current file
	 */
	public boolean isOfflineFileCompressed();
	
	/**
	 * Returns the count of successful metric collection flushes
	 * @return the count of successful metric collection flushes
	 */
	public long getFlushSuccessCount();

	/**
	 * Returns the count of failed metric collection flushes
	 * @return the count of failed metric collection flushes
	 */
	public long getFlushFailedCount();
	
	/**
	 * Returns the count of bad content metric collection flushes
	 * @return the count of bad content collection flushes
	 */
	public long getFlushBadContentCount();
	

	
	/**
	 * Returns the count of successfully submitted metrics based on OpenTSDB responses.
	 * @return the count of successfully submitted metrics or -1 if trackResponses is disabled.
	 */
	public long getSuccessfulMetrics();


	/**
	 * Returns the count of failed (bad) submitted metrics based on OpenTSDB responses.
	 * @return the count of failed (bad) submitted metrics 
	 */
	public long getFailedMetrics();
	


	
	/**
	 * Returns the currently configured submitter host name
	 * @return the submitter host name
	 */
	public String getHostName();
	
	/**
	 * Returns the currently configured submitter app name
	 * @return the submitter app name
	 */
	public String getAppName();
	
	/**
	 * Sets the configured submitter host name
	 * @param hostName the submitter host name to set 
	 */
	public void setHostName(String hostName);
	
	/**
	 * Sets the configured submitter app name
	 * @param appName the submitter app name to set
	 */
	public void setAppName(String appName);
	
	/**
	 * Returns the configured global tags
	 * @return the configured global tags
	 */
	public Map<String, String> getGlobalTags();
	
	/**
	 * Returns the maximum number of concurrent flushes
	 * @return the maximum number of concurrent flushes
	 */	
	public int getMaxConcurrentFlushes();


	/**
	 * Sets the maximum number of concurrent flushes
	 * @param maxConcurrentFlushes the maximum number of concurrent flushes
	 */
	public void setMaxConcurrentFlushes(final int maxConcurrentFlushes);
	
	/**
	 * Returns the currently installed put response handler
	 * @return the currently installed put response handler
	 */
	public String getPutResponseHandlerName();
	
	/**
	 * Sets the current put response handler
	 * @param handler the name of the response handler to use
	 */
	public void setPutResponseHandlerName(final String handler);
	
	
}
