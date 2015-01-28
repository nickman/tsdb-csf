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

import static com.heliosapm.opentsdb.client.util.Util.clean;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.jboss.netty.buffer.ChannelBuffer;

import com.heliosapm.opentsdb.client.name.AgentName;


/**
 * @author Sean Scanlon <sean.scanlon@gmail.com>
 *         <p/>
 *         Representation of a metric.
 *         
 * 		   registry.register(name(getClass().getSimpleName(), "type=HeapUsed", "mod=5"), mod5HeapUsed);        
 *         KitchenSink.type=HeapUsed.mod=5.value:app=ptms, host=pp-wk-nwhi-01.cpex.com
 *         			--  KitchenSink.type=HeapUsed.mod=5.value
 *         auto-host/app tags
 *         
 */
public class OpenTsdbMetric {
	/** Metric type key */
	public static final String CMTYPE = "cmtype";
	/** Dot split pattern */
	protected static final Pattern DOT_SPLITTER = Pattern.compile("\\.");
	
	/** Maps the full MetricsRegistry name to the OpenTSDB metric name */	
	protected static final ConcurrentHashMap<String, String> mapToMetricName = new ConcurrentHashMap<String, String>(); 
	
    private OpenTsdbMetric() {
    }

    public static Builder named(final String name) {
//    	final String rewrittenName = 
    	final Builder builder = rewriteName(name);
    	//OpenTsdb.getInstance().addOpenTsdbMetricName(named(builder.metric.metric, builder.metric.tags));
        return builder;
    }
    
    public static String prefix(final String prefix, String...components) {
    	StringBuilder b = new StringBuilder();
    	if(prefix!=null && !prefix.trim().isEmpty()) b.append(clean(prefix)).append(".");
    	if(components.length!=0) {
    		for(String s: components) {
    			if(s!=null && !s.trim().isEmpty()) b.append(clean(s)).append(".");
    		}
    	}
    	if(b.length()>0) b.deleteCharAt(b.length()-1);
    	return b.toString();
    }
    
    public static Builder rewriteName(final String name) {
    	if(name==null || name.trim().isEmpty()) {
    		return null;  // what else ?
    	}
    	String[] dots = DOT_SPLITTER.split(name.trim());
    	Map<String, String> tags = new TreeMap<String, String>();
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
    				if(CMTYPE.equalsIgnoreCase(key)) continue;
    				tags.put(key, value);
    			}
    		}
    	}
    	if(!hasMn) return null;
    	String metricName = b.deleteCharAt(b.length()-1).toString();
    	return new Builder(metricName.replace("..", ".")).withTags(tags);
    }
    
    /**
     * Builds an OpenTsdbMetric name 
     * @param metricName The metric name
     * @param tags The metric tags
     * @return the built name
     */
    @SuppressWarnings("rawtypes")
	public static String named(final String metricName, final Map tags) {
    	final StringBuilder b = new StringBuilder();
    	if(metricName!=null) b.append(clean(metricName)).append(".");
    	if(tags!=null && !tags.isEmpty()) {
    		Set<Map.Entry<Object, Object>> entrySet = tags.entrySet();
    		for(Map.Entry entry: entrySet) {
    			b.append(clean(entry.getKey().toString())).append("=").append(clean(entry.getValue().toString())).append(".");
    		}
    	}
    	if(b.length()>0) b.deleteCharAt(b.length()-1);
    	return b.toString();
    }
    
    /**
     * Builds an OpenTsdbMetric name 
     * @param metricName The metric name
     * @param tagPairs <b><code>=</code></b> separated tag key/value pairs
     * @return the built name
     */
    
	public static String named(final String metricName, final String...tagPairs) {
    	Map<String, String> tags = new TreeMap<String, String>();
    	for(String s: tagPairs) {
    		s = s.trim();
    		int index = s.indexOf('=');
    		if(index==0) continue;
    		String key = clean(s.substring(0, index));
    		String value = clean(s.substring(index+1));
    		if(!key.isEmpty() && !value.isEmpty()) {
    			tags.put(key, value);
    		}
    	}
    	return named(metricName, tags);
    }
    

    private String metric;

    private Long timestamp;

    private Object value;

    private Map<String, String> tags = new HashMap<String, String>();

    @Override
    public boolean equals(Object o) {

        if (o == this) {
            return true;
        }

        if (!(o instanceof OpenTsdbMetric)) {
            return false;
        }

        final OpenTsdbMetric rhs = (OpenTsdbMetric) o;

        return equals(metric, rhs.metric)
                && equals(timestamp, rhs.timestamp)
                && equals(value, rhs.value)
                && equals(tags, rhs.tags);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{metric, timestamp, value, tags});
    }

    public static class Builder {

        private final OpenTsdbMetric metric;

        public Builder(String name) {
            this.metric = new OpenTsdbMetric();
            metric.metric = name;
        }

        public OpenTsdbMetric build() {
            return metric;
        }

        public Builder withValue(final Object value) {
            metric.value = value;
            return this;
        }

        public Builder withTimestamp(final Long timestamp) {
            metric.timestamp = timestamp;
            return this;
        }

        public Builder withTags(final Map<String, String> tags) {
            if (tags != null) {
                metric.tags.putAll(tags);
            }
            return this;
        }
    }


    @Override
    public String toString() {
        return this.getClass().getSimpleName()
                + "->metric: " + metric
                + ",value: " + value
                + ",timestamp: " + timestamp
                + ",tags: " + tags;
    }
    
    /**
     * Renders this metric in a new buffer
     * @return the rendered metric
     */
    public StringBuilder toJSON() {
    	return toJSON(null);
    }
    
    /**
     * Renders this metric into the passed buffer, or a new buffer if the passed buffer is null
     * @param buff The optional buffer to render into
     * @return the rendered metric
     */
    public StringBuilder toJSON(final StringBuilder buff) {
    	final StringBuilder b;
    	if(buff==null) b = new StringBuilder();
    	else b = buff;
    	b.append("{\"metric\":\"")
    		.append(metric).append("\",")
    		.append("\"timestamp\":").append(timestamp).append(",")
    		.append("\"value\":").append(value).append(",")
    		.append("\"tags\": {");
    	for(Map.Entry<String, String> gtag: AgentName.getInstance().getGlobalTags().entrySet()) {
    		if(!tags.containsKey(gtag.getKey())) {
    			b.append("\"").append(gtag.getKey()).append("\":\"").append(gtag.getValue()).append("\",");
    		}
    	}

    	for(Map.Entry<String, String> tag: tags.entrySet()) {
    		b.append("\"").append(tag.getKey()).append("\":\"").append(tag.getValue()).append("\",");    		
    	}
    	return b.deleteCharAt(b.length()-1).append("}}");    	
    }
    
    /**
     * Renders the passed set of metrics
     * @param metrics The metrics to render
     * @return A buffer containing the metrics JSON rendering
     */
    public static StringBuilder setToJSON(final Set<OpenTsdbMetric> metrics) {
    	if(metrics==null || metrics.isEmpty()) return new StringBuilder("[]");
    	StringBuilder b = new StringBuilder(1024).append("[");
    	final int last = metrics.size()-1;
    	int index = 0;
    	for(OpenTsdbMetric m: metrics) {
    		m.toJSON(b);
    		if(index != last) {
    			b.append(",");
    		}
    		index++;
    	}
    	return b.append("]");
    }
    
    private static final Charset UTF8 = Charset.forName("UTF-8");
    
    private static final byte[] JSON_OPEN_ARR = "[".getBytes(UTF8);
    private static final byte[] JSON_CLOSE_ARR = "]".getBytes(UTF8);
    private static final byte[] JSON_COMMA = ",".getBytes(UTF8);
    
    /**
     * Writes the passed set of metrics into the passed buffer
     * @param metrics The metrics to render
     * @param buffer The buffer to write to
     * @return The number of metrics written
     */
    public static int writeToBuffer(final Set<OpenTsdbMetric> metrics, final ChannelBuffer buffer) {
    	if(metrics==null || metrics.isEmpty()) return 0;
    	buffer.writeBytes(JSON_OPEN_ARR);
    	final int last = metrics.size()-1;
    	int index = 0;
    	for(OpenTsdbMetric m: metrics) {
    		buffer.writeBytes(m.toJSON().toString().getBytes(UTF8));
    		if(index != last) {
    			buffer.writeBytes(JSON_COMMA);
    		}
    		index++;
    	}
    	buffer.writeBytes(JSON_CLOSE_ARR);
    	return index;
    }
    

    public String getMetric() {
        return metric;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public Object getValue() {
        return value;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    private boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }
}

/*
{
    "metric": "sys.cpu.nice",
    "timestamp": 1346846400,
    "value": 18,
    "tags": {
       "host": "web01",
       "dc": "lga"
    }
}
 */