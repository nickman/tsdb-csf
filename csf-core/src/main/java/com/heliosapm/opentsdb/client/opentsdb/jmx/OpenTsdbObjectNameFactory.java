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

package com.heliosapm.opentsdb.client.opentsdb.jmx;

import static com.heliosapm.opentsdb.client.util.Util.clean;

import java.util.Hashtable;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.ObjectNameFactory;

/**
 * <p>Title: OpenTsdbObjectNameFactory</p>
 * <p>Description: {@link ObjectNameFactory} to build tag useful JMX ObjectNames.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jmx.OpenTsdbObjectNameFactory</code></p>
 */

public class OpenTsdbObjectNameFactory implements ObjectNameFactory {
	/** Metric type key */
	public static final String CMTYPE = "cmtype";
	/** Dot split pattern */
	protected static final Pattern DOT_SPLITTER = Pattern.compile("\\.");

	private static final Logger LOG = LoggerFactory.getLogger(OpenTsdbObjectNameFactory.class);
	
	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.ObjectNameFactory#createName(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public ObjectName createName(final String type, final String domain, final String name) {
		try {
	    	if(name==null || name.trim().isEmpty()) {
	    		return null;  // what else ?
	    	}
	    	String[] dots = DOT_SPLITTER.split(name.trim());
	    	Hashtable<String, String> tags = new Hashtable<String, String>(4);
	    	StringBuilder b = new StringBuilder();
	    	boolean hasMn = false;
	    	for(String s: dots) {
	    		int index = s.indexOf('=');
	    		if(index==-1) {
	    			b.append(clean(s)).append(".");
	    			hasMn = true;
	    		} else {
	    			String key = clean(s.substring(0, index));
	    			String value = clean(s.substring(index+1));
	    			if(!key.isEmpty() && !value.isEmpty()) {
//	    				if(CMTYPE.equalsIgnoreCase(key)) continue;
	    				tags.put(key, value);
	    			}
	    		}
	    	}
	    	if(!hasMn) return null;
	    	String metricName = b.deleteCharAt(b.length()-1).toString();
	    	return new ObjectName(metricName, tags);
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
			LOG.error("Failed to create ObjectName from [{}],[{}],[{}]", type, domain, name);
			throw new RuntimeException("Failed to create ObjectName", ex);
		}
	}

}
