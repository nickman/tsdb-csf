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

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.util.Timeout;

import com.codahale.metrics.Clock;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reporter;

/**
 * <p>Title: OpenTSDBReporter</p>
 * <p>Description: Metrics reporter optimized for OpenTSDB OTMetrics.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OpenTSDBReporter</code></p>
 */

public class OpenTSDBReporter implements Reporter, Closeable {
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());
	/** Started flag */
	protected final AtomicBoolean started = new AtomicBoolean(false);
	/** The OpenTsdb reference */
	protected final OpenTsdb opentsdb = OpenTsdb.getInstance();
	/** A clock for providing time stamps */
	protected final Clock clock;
	/** An optional prefix to add to all metrics reported out of this reporter */
	protected final String prefix;
	/** A handle to this reporter's scheduler */
	protected Timeout scheduleHandle = null;
	/** The metric registry this reporter is reporting from */
	protected final MetricRegistry registry;
	/** A map of tags to add to all metrics reported out of this reporter */
	protected final Map<String, String> tags;
    /** An optional metric filter to be selective about which metrics are reported */
    protected final MetricFilter metricFilter;
    /** The rate conversion unit */
    protected final TimeUnit rateUnit;
    /** The duration conversion unit */
    protected final TimeUnit durationUnit;
    /** The duration modifier factor */
    protected final double durationFactor;
    /** The rate modifier factor */
    protected final double rateFactor;
	
	
	
	/**
	 * Creates a new OpenTSDBReporter
	 * @param registry The metric registry this reporter is reporting from
	 * @param clock A clock for providing time stamps
	 * @param prefix An optional prefix to add to all metrics reported out of this reporter
	 * @param rateUnit The rate conversion unit
	 * @param durationUnit The duration conversion unit
	 * @param filter An optional metric filter to be selective about which metrics are reported
	 */
	public OpenTSDBReporter(final MetricRegistry registry, final Clock clock, final String prefix, final TimeUnit rateUnit, final TimeUnit durationUnit, final MetricFilter filter) {
        tags = new TreeMap<String, String>();
        this.clock = clock;
        this.prefix = prefix;
        this.registry = registry;
        this.metricFilter = filter;
        this.rateUnit = rateUnit;
        this.durationUnit = durationUnit;
        this.durationFactor = 1.0 / durationUnit.toNanos(1);
        this.rateFactor = rateUnit.toSeconds(1);
        
    }

	/**
	 * {@inheritDoc}
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub

	}
	
    /**
     * Starts the reporter on the specified period
     * @param period The time period the reporter should run on
     * @param unit The unit of the period
     */
    public void start(final long period, final TimeUnit unit) {
    	if(started.compareAndSet(false, true)) {
	    	final Runnable r = new Runnable() {
	            @Override
	            public void run() {
	                try {
	                    report();
	                } catch (RuntimeException ex) {
	                    log.error("RuntimeException thrown from {}#report. Exception was suppressed.", OpenTsdbReporter.this.getClass().getSimpleName(), ex);
	                }
	            }
	        };
	        scheduleHandle = Threading.getInstance().schedule(r, period, unit);
	        log.debug("Started scheduled task for Registry {}", registry.getNames().toString());
    	}
    }
    
    /**
     * Stops the reporter
     */
    public void stop() {
    	if(started.compareAndSet(true, false)) {
	    	if(scheduleHandle!=null) {
	    		scheduleHandle.cancel();
	    		LOG.debug("Stopped scheduled task for Registry {}", registry.getNames().toString());
	    	}
    	}    	
    }
	
	
	public void report() {
		
	}

}
