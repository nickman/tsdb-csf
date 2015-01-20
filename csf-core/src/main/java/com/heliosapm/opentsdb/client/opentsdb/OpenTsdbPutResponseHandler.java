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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	public static final Pattern DETAILS_WITH_ERRORS_PATTERN = Pattern.compile("\\{\"errors\":\\[(.+?)\\],\"failed\":(\\d+),\"success\":(\\d+)\\}");

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
	 * @see com.heliosapm.opentsdb.client.opentsdb.PutResponseHandler#process(int, java.lang.StringBuilder)
	 */
	@Override
	public int[] process(int responseCode, StringBuilder content) {		
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
		@Override
		public int[] process(final int responseCode, final StringBuilder content) {
			return doSummaryStats(content);
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
		@Override
		public int[] process(final int responseCode, final StringBuilder content) {
			return doDetailedStats(content);
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
		public int[] process(final int responseCode, final StringBuilder content) {
			return EMPTY_COUNTS;
		}	
	}
	
	
	/**
	 * Processes the summary stats from the http put response
	 * @param sb The response content
	 * @return an array of result counts (failed:0, success:1)
	 */
	public static int[] doSummaryStats(final StringBuilder sb) {
		int[] counts = null;
		Matcher m = SUMMARY_PATTERN.matcher(sb);
		if(m.matches()) {
			counts = getCountsFromMatcher(m);
		}
		return counts==null ? EMPTY_COUNTS : counts;
	}
	
	/**
	 * Extracts the failiure and success counts and the error json from the passed details response and increments the counters
	 * @param sb The response stringy which the errs are put into if there are any. Otherwise it is truncated to zero length
	 * @return the counts of failed submissions ([0]) and successful submissions ([1]).
	 */
	public static int[] doDetailedStats(final StringBuilder sb) {
		int[] counts = null;
		Matcher m = DETAILS_PATTERN.matcher(sb);
		if(m.matches()) {
			// means no failures
			counts = getCountsFromMatcher(m);
			sb.setLength(0);			
		} else {
			m = DETAILS_WITH_ERRORS_PATTERN.matcher(sb);
			if(m.matches()) {
				// we got fails, yo				
				counts = getCountsFromMatcher(m);
				String s = m.group(1).trim();
//				System.out.println("==============\n" + s + "\n==============");
//				try {
//					System.out.println(new JSONObject(s).toString(2));
//				} catch (Exception ex) {}
				sb.setLength(0);
				sb.delete(0, sb.length()-1).append(s);
				// index the bad metric names and filter them
				// TODO:
				/*
				 * Bad metric responses look like this:
						{
						  "datapoint": {
						    "tags": {
						      "service": "cacheservice",
						      ",attr": "cache-size"
						    },
						    "timestamp": 1421536767,
						    "metric": "KitchenSink.value",
						    "value": "572"
						  },
						  "error": "Invalid tag name (\",attr\"): illegal character: ,"
						}
				 */
				// build the original [bad] metric name
				// figure out which registry it's in amd yank it
				// send jmx notification with simplified message:  BAD METRIC: XXX, ERROR: YYY
				
			}
		}
		return counts==null ? EMPTY_COUNTS : counts;
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
