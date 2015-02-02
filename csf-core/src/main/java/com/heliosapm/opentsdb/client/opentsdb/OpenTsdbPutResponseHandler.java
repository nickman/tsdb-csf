/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package com.heliosapm.opentsdb.client.opentsdb;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * <p>Title: OpenTsdbPutResponseHandler</p>
 * <p>Description: Enumerates the options for handling OpenTSDB <b>put</b> call responses.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OpenTsdbPutResponseHandler</code></p>
 */

public enum OpenTsdbPutResponseHandler implements PutResponseHandler {
	/** Do nothing. Aborts the response handling as soon as the status is received. */
	NOTHING("", new NoopResponseHandler()),
	/** Tracks the counts of successful and failed metric submissions */
	COUNTS("?summary", new SummaryResponseHandler()),
	/** Tracks the counts like {@link #COUNTS} and also extracts all the invalid metric errors, suppressing their future submissions */
	ERRORS("?details", new DetailedResponseHandler());
	
	private OpenTsdbPutResponseHandler(final String putSignature, final PutResponseHandler handler) {
		this.putSignature = putSignature;
		this.handler = handler;
	}
	
	/** Empty counts const */
	private static final int[] EMPTY_COUNTS = {0,0};
	/** Regex matching a summary response from OpenTSDB after posting a put */
	public static final Pattern SUMMARY_PATTERN = Pattern.compile("\\{\"failed\":(\\d+),\"success\":(\\d+)\\}");
	/** Regex matching a details response from OpenTSDB after posting a put with no errors */
	public static final Pattern DETAILS_PATTERN = Pattern.compile("\\{\"errors\":\\[\\],\"failed\":(\\d+),\"success\":(\\d+)\\}");
	/** Regex matching a details response from OpenTSDB after posting a put with no errors */
	public static final Pattern DETAILS_WITH_ERRORS_PATTERN = Pattern.compile("\\{\"errors\":(\\[.+?\\]),\"failed\":(\\d+),\"success\":(\\d+)\\}");

	
	/** The JSON key for the success count */
	public static final String SUCCESS_KEY = "success";
	/** The JSON key for the failed count */
	public static final String FAILED_KEY = "failed";
	/** The JSON key for the error array */
	public static final String ERRORS_KEY = "errors";
	
	/** Comma separated valid names */
	public static final String VALID_NAMES;
	
	static {
		StringBuilder b = new StringBuilder();
		for(OpenTsdbPutResponseHandler h: OpenTsdbPutResponseHandler.values()) {
			b.append(h.name()).append(",");
		}
		VALID_NAMES = b.deleteCharAt(b.length()-1).toString();
	}
	
	/** The HTTP put path for this option */
	public final String putSignature;	
	/** The response handler for this option */
	public final PutResponseHandler handler;
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.PutResponseHandler#process(int, org.jboss.netty.buffer.ChannelBuffer)
	 */
	@Override
	public int[] process(final int responseCode, final ChannelBuffer content) {		
		return handler.process(responseCode, content);
	}
	
	
	/**
	 * <p>Title: SummaryResponseHandler</p>
	 * <p>Description: The put response handler for summary responses</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OpenTsdbPutResponseHandler.SummaryResponseHandler</code></p>
	 */
	public static class SummaryResponseHandler implements PutResponseHandler {
		final Logger log = LogManager.getLogger(getClass());
		@Override
		public int[] process(final int responseCode, final ChannelBuffer content) {
			return doSummaryStats(content, log);
		}	
	}
	
	/**
	 * <p>Title: DetailedResponseHandler</p>
	 * <p>Description: The put response handler for detailed responses</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OpenTsdbPutResponseHandler.DetailedResponseHandler</code></p>
	 */
	public static class DetailedResponseHandler implements PutResponseHandler {
		final Logger log = LogManager.getLogger(getClass());
		@Override
		public int[] process(final int responseCode, final ChannelBuffer content) {
			return doDetailedStats(responseCode, content, log);
		}	
	}

	/**
	 * <p>Title: NoopResponseHandler</p>
	 * <p>Description: A no op put response handler for not handling responses</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OpenTsdbPutResponseHandler.NoopResponseHandler</code></p>
	 */
	public static class NoopResponseHandler implements PutResponseHandler {
		@Override
		public int[] process(final int responseCode, final ChannelBuffer content) {
			return EMPTY_COUNTS;
		}	
	}
	
	
	/**
	 * Processes the summary stats from the http put response
	 * @param content The response content
	 * @param log The logger to log any errors with
	 * @return an array of result counts (failed:0, success:1)
	 */
	public static int[] doSummaryStats(final ChannelBuffer content, final Logger log) {
		final int[] results = new int[]{0,0};
		try {
			final JSONObject j = new JSONObject(content.toString(Constants.UTF8));
			results[0] = j.getInt(FAILED_KEY);
			results[1] = j.getInt(SUCCESS_KEY);
		} catch (Exception ex) {
			log.error("Failed to process response", ex);
		}
		return results;
	}
	
	/**
	 * Extracts the failiure and success counts and the error json from the passed details response and increments the counters
	 * @param responseCode The HTTP response code
	 * @param content The response buffer 
	 * @param log The logger to log with
	 * @return the counts of failed submissions ([0]) and successful submissions ([1]).
	 */
	public static int[] doDetailedStats(final int responseCode, final ChannelBuffer content, final Logger log) {
		final int[] results = new int[]{0,0};
		String contentStr = null;
		try {
			contentStr = content.toString(Constants.UTF8);
			if(responseCode==400) {
				if(couldBeGzipIssue(responseCode, contentStr)) {
					log.error("Auto disabled http post gzip");
					HttpMetricsPoster.getInstance().autoDisableGZip();
				}
			}
			final JSONObject j = new JSONObject(contentStr);
			results[0] = j.getInt(FAILED_KEY);
			results[1] = j.getInt(SUCCESS_KEY);			
			processErrors(j.optJSONArray(ERRORS_KEY), log);
		} catch (Exception ex) {
			log.warn("Failed to process response {}", contentStr, ex);
		}
		return results;
	}
	
	
	
	/**
	 * Handles reported errors in the metrics post response
	 * @param arr The JSON array of errors
	 * @param log The logger to log the errors with
	 */
	protected static void processErrors(final JSONArray arr, final Logger log) {
		if(arr==null || arr.length()<1) return;
		try {
			final int sz = arr.length();
			for(int i = 0; i < sz; i++) {
				JSONObject dp = arr.getJSONObject(i).getJSONObject("datapoint");
				log.error("BAD METRIC: metric:{}, tags:{}", dp.get("metric"), dp.get("tags"));
				// TODO: build a metric name that can be used to filter submissions 
				// so these don't get sent again.
			}
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
	}
	
	/**
	 * Determines if the put error response indicates that the server does not support gzip
	 * @param responseCode The HTTP response code
	 * @param content The response content
	 * @return true if the error has the signature of a non-gzip-supporting server
	 */
	public static boolean couldBeGzipIssue(final int responseCode, final String content) {
		return (
				HttpMetricsPoster.getInstance().isEnableCompression() &&
				responseCode == 400 &&
				(content.contains("JsonParseException") || content.contains("Unable to parse the given JSON")) &&
				containsMulti(content)				
		);
	}
	
	/**
	 * Determines if the passed content contains multi-byte characters
	 * @param content The content to test
	 * @return true if the passed content contains multi-byte characters, false otherwise
	 */
	public static boolean containsMulti(final String content) {
		if(content==null || content.isEmpty()) return false;
		final char[] chars = content.toCharArray();		
		Arrays.sort(chars);
		return (chars[chars.length-1] > Byte.MAX_VALUE);
	}
	
	
	
	/**
	 * Extracts the failure and success counts from the passed matcher
	 * @param m The matcher containing the counts
	 * @return the counts
	 */
	public static int[] getCountsFromMatcher(final Matcher m) {
		final int grpCount = m.groupCount();
		final int[] counts = new int[2];
		try {
			counts[0] = Integer.parseInt(m.group(grpCount==3 ? 2 : 1));
			counts[1] = Integer.parseInt(m.group(grpCount==3 ? 3 : 2));			
		} catch (Exception x) {
			return null;
		}					
		return counts;		
	}
	

}
