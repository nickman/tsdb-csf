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

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;

/**
 * <p>Title: OpenTsdbMetricFactory</p>
 * <p>Description: {@link com.codahale.metrics.MetricRegistry} implementation for OpenTSDB.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OpenTsdbMetricFactory</code></p>
 */

public class OpenTsdbMetricRegistry implements MetricSet {
	
	/** Static class logger */
	protected static final Logger LOG = LogManager.getLogger(OpenTsdbMetricRegistry.class);
	
	/**
	 * Creates a new OpenTsdbMetricFactory
	 */
	public OpenTsdbMetricRegistry() {
		// TODO Auto-generated constructor stub
	}
	
    /**
     * Concatenates elements to form an OpenTSDB metric in the format of a JMX ObjectName.
     *
     * @param name     the first element of the name
     * @param names    the remaining elements of the name
     * @return {@code name} as the ObjectName domain, followed by a colon, then {@code names} concatenated by equals signs
     */
    public static String name(final String name, final String... names) {
    	final StringBuilder builder = new StringBuilder();
    	if(name!=null) {
    		String _name = name.trim();
    		if(!_name.isEmpty()) {
    			builder.append(_name).append(":");
    		}
    	}
        if (names != null) {
            for (String s : names) {
                append(builder, s);
            }
        }
        return builder.toString();
    }
    
    /**
     * Concatenates a class name and elements to form an OpenTSDB metric, eliding any null values or
     * empty strings.
     *
     * @param klass    the first element of the name
     * @param names    the remaining elements of the name
     * @return {@code name} as the ObjectName domain, followed by a colon, then {@code names} concatenated by equals signs
     */
    public static String name(Class<?> klass, String... names) {
        return name(klass.getName(), names);
    }
    
    /**
     * Concatenates a class simple name and elements to form an OpenTSDB metric, eliding any null values or
     * empty strings.
     *
     * @param klass    the first element of the name
     * @param names    the remaining elements of the name
     * @return {@code name} as the ObjectName domain, followed by a colon, then {@code names} concatenated by equals signs
     */
    public static String shortname(Class<?> klass, String... names) {
        return name(klass.getSimpleName(), names);
    }
    
    
    private static void append(final StringBuilder builder, final String partName) {
    	if(partName==null) return;
    	final String part = partName.replace(" ", "");
    	final int blength = builder.length();
    	if(!part.isEmpty()) {
	    	/*
	    	 * could be:
	    	 *  metricname: builder length = 0 (replace '=' with '.' ?)
	    	 * 	pair: A=B, last char can be ':' or ','
	    	 *  value: B if last char is '='
	    	 *  key: A if last char is NOT '=', but can be ':' or ','
	    	 */
	    	
	    	if (blength > 0) {
	    		final char lastc = builder.charAt(blength-1);
	    		final int eqIndex = part.indexOf('=');
	    		if(eqIndex > 1 && (lastc == ':' || lastc == ',') && eqIndex < part.length()-1) {
	    			// pair
	    			builder.append(part).append(",");
	    		} else {
	    			if(eqIndex==0) {
	    				if(lastc == '=') {
	    					// value
	    					builder.append(part).append(",");
	    				} else {
	    					if(lastc != '=' && (lastc == ':' || lastc == ',')) {
	    						// key
	    						builder.append(part).append("=");
	    					}
	    				}
	    			}
	    		}
	    	} else {
	    		// metric name
	    		builder.append(part.replace('=', '.')).append(":");
	    	}
    	}
    	if(blength == builder.length()) {
    		LOG.trace("No assignment for append([{}], [{}])", builder, partName);
    	}
    }
    
	
	
	@Override
	public Map<String, Metric> getMetrics() {
		// TODO Auto-generated method stub
		return null;
	}

}
