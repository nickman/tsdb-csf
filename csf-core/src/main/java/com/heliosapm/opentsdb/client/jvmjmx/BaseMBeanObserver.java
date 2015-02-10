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

package com.heliosapm.opentsdb.client.jvmjmx;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.EpochClock;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: BaseMBeanObserver</p>
 * <p>Description: The base MBean monitor implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.BaseMBeanObserver</code></p>
 * FIXME:  need standard helpers to build unique delta keys so we don't have collisions between different instances.
 */

public abstract class BaseMBeanObserver implements NotificationListener, NotificationFilter, Runnable, MetricSet {
	/**  */
	private static final long serialVersionUID = -8583616842316152417L;
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());
	/** The MBeanObserver defining what we're collecting */
	protected final MBeanObserver mbeanObserver;
	/** The MBeanServer we're connecting from */
	protected final RuntimeMBeanServerConnection mbs;
	/** The attributes being collected keyed by the object names being monitored */
	protected final Map<ObjectName, String[]> objectNamesAttrs;
	/** The tags common to all metrics submitted from this observer */
	protected final Map<String, String> tags = new LinkedHashMap<String, String>();
	/** A refresh timer */
	protected final Timer timer = new Timer();
	/** A meter of collection exceptions */
	protected final Meter collectExceptions = new Meter();
	/** Indicates if this observer is actively polling */
	protected final AtomicBoolean active = new AtomicBoolean(false); 
	/** A long delta tracker */
	protected final NonBlockingHashMap<String, long[]> longDeltas = new NonBlockingHashMap<String, long[]>();
	/** The clock to get the current time with */
	protected final Clock clock;	
	/** The metrics that belong to this observer */
	protected final Set<OTMetric> groupMetrics = new HashSet<OTMetric>();
	
	
	/** The attribute mask */
	protected int attributeMask = -1;
	/** The attribute names we're collecting */
	protected String[] attributeNames = null;
	
	
	
	
	/**
	 * Creates a new BaseMBeanObserver
	 * @param jmxConnector The JMXConnector that will supply an MBeanServerConnection
	 * @param mbeanObserver The MBeanObserver defining what we're collecting
	 * @param tags The tags common to all metrics submitted from this observer
	 */
	protected BaseMBeanObserver(final JMXConnector jmxConnector, final MBeanObserver mbeanObserver, final Map<String, String> tags) {
		this(RuntimeMBeanServerConnection.newInstance(jmxConnector), mbeanObserver, tags);
	}
	
	/**
	 * Creates a new BaseMBeanObserver
	 * @param mbeanServerConn The MBeanServerConnection to monitor
	 * @param mbeanObserver The MBeanObserver defining what we're collecting
	 * @param tags The tags common to all metrics submitted from this observer
	 */
	protected BaseMBeanObserver(final MBeanServerConnection mbeanServerConn, final MBeanObserver mbeanObserver, final Map<String, String> tags) {
		mbs = RuntimeMBeanServerConnection.newInstance(mbeanServerConn);
		this.mbeanObserver = mbeanObserver;
		if(tags!=null && !tags.isEmpty()) {
			for(Map.Entry<String, String> entry: tags.entrySet()) {
				String key = Util.clean(entry.getKey());
				String value = Util.clean(entry.getValue());
				if(key!=null && !key.isEmpty() && value!=null && !value.isEmpty()) {
					this.tags.put(key, value);
				}
			}
		}
		Set<ObjectName> objectNames = mbs.queryNames(mbeanObserver.objectName, null);
		objectNamesAttrs = new HashMap<ObjectName, String[]>(objectNames.size());
		for(ObjectName on: objectNames) {
			int attributeMask = mbeanObserver.getMaskFor(mbs.getMBeanInfo(on).getAttributes());
			objectNamesAttrs.put(on, mbeanObserver.getAttributeNames(attributeMask));
		}
		initializeAgentName();
		clock = ConfigurationReader.confBool(Constants.PROP_TIME_IN_SEC, Constants.DEFAULT_TIME_IN_SEC) ? EpochClock.INSTANCE : Clock.defaultClock();
	}
	
	/**
	 * If the target MBeanServer is not in-VM and either of the agent name tags has not been supplied,
	 * this is where we try to figure out what the missing tags should be to represent the remote endpoint.
	 */
	void initializeAgentName() {
		if(!mbs.isInVM() && (!tags.containsKey(Constants.APP_TAG) || !tags.containsKey(Constants.HOST_TAG))) {
			// FIXME: The agent name discovery should be configurable
			final RemoteAgentName ran = new DefaultRemoteAgentName();
			if(!tags.containsKey(Constants.APP_TAG)) {
				tags.put(Constants.APP_TAG, ran.getAppName(mbs));
			}
			if(!tags.containsKey(Constants.HOST_TAG)) {
				tags.put(Constants.HOST_TAG, ran.getHostName(mbs));
			}			
		}		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.MetricSet#getMetrics()
	 */
	@Override
	public Map<String, Metric> getMetrics() {  ///  MAKE THE CLASSNAME A TAG
		final Map<String, Metric> metrics = new HashMap<String, Metric>(2);
		metrics.put("jmx.collections.type=" + getClass().getSimpleName(), timer);
		metrics.put("jmx.exceptions.type=" + getClass().getSimpleName(), collectExceptions);
		return metrics;
	}
	
	/**
	 * Returns the default group name which uniquely identifies the target MBeanServer and ObjectName (which could be a pattern).
	 * @return the default group name 
	 */
	protected String getDefaultGroupName() {
		return mbs.getMBeanServerId() + "/" + mbeanObserver.objectName.toString();
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		final Context ctx = timer.time();
		try {
			refresh();
			ctx.stop();
		} catch (Exception ex) {
			collectExceptions.mark();
		} 
	}
	
	/**
	 * Polls the target MBeanServer for this MBeanObserver's target data.
	 * FIXME:  Allow the impl to override which attributes are repeatedly polled for
	 */
	protected void refresh() {
		final Map<ObjectName, Map<String, Object>> map = new HashMap<ObjectName, Map<String, Object>>(objectNamesAttrs.size());
		for(Map.Entry<ObjectName, String[]> entry: objectNamesAttrs.entrySet()) {
			map.put(entry.getKey(), mbs.getAttributeMap(entry.getKey(), entry.getValue()));
		}
		active.set(accept(map, clock.getTime(), elapsed("BaseElapsedTime")));
		
	}
	
	/**
	 * Callback to the concrete observer when data has been collected
	 * @param data The data as Maps of collected data keyed by the attribute name within a map keyed by the ObjectName of the MBean collected from\
	 * @param currentTime The current time according to the configured clock
	 * @param elapsedTime The elapsed time since the prior accept in ns.
	 * @return true to continue polling, false otherwise
	 */
	protected abstract boolean accept(final Map<ObjectName, Map<String, Object>> data, final long currentTime, final long elapsedTime);
	
	
	/**
	 * Builds a delta key from the passed sub keys.
	 * The key will automatically be prefixed with the MBeanServerId
	 * @param subKeys The subkeys to append
	 * @return the delta key
	 */
	protected String deltaKey(final Object...subKeys) {
		final StringBuilder b = new StringBuilder(mbs.getMBeanServerId());
		if(subKeys!=null && subKeys.length>0) {
			for(Object subKey: subKeys) {
				if(subKey==null) continue;
				b.append("/").append(subKey.toString());
			}
		}
		return b.toString();
	}
	
	/**
	 * Callback before polling starts to ensure the observer should be scheduled
	 * @param mbs The MBeanServer connection
	 * @param objectNamesAttrs The attribute names being collected keyed by the ObjectName of the target MBean
	 * @return true to contine, false otherwise
	 */
	protected boolean initialize(final RuntimeMBeanServerConnection mbs, final Map<ObjectName, String[]> objectNamesAttrs) {
		return true;
	}

	/**
	 * Indicates if this observer is actively polling
	 * @return true if this observer is actively polling, false otherwise
	 */
	public boolean isActive() {
		return active.get();
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationFilter#isNotificationEnabled(javax.management.Notification)
	 */
	@Override
	public boolean isNotificationEnabled(final Notification notification) {
		return false;
	}

	/**
	 * {@inheritDoc}
	 * @see javax.management.NotificationListener#handleNotification(javax.management.Notification, java.lang.Object)
	 */
	@Override
	public void handleNotification(final Notification notification, final Object handback) {

	}

	/**
	 * Returns the MBeanObserver
	 * @return the mbeanObserver
	 */
	public MBeanObserver getMbeanObserver() {
		return mbeanObserver;
	}

	/**
	 * Returns a map of the attributes being collected keyed by the object names being monitored
	 * @return a map of attribute names keyed by ObjectNames
	 */
	public Map<ObjectName, String[]> getObjectNamesAttrs() {
		return Collections.unmodifiableMap(objectNamesAttrs);
	}

	/**
	 * Returns the configured tags
	 * @return the tags
	 */
	public Map<String, String> getTags() {
		return tags;
	}

	/**
	 * Returns the collection timer
	 * @return the timer
	 */
	public Timer getTimer() {
		return timer;
	}

	/**
	 * Returns the exception meter
	 * @return the collectExceptions
	 */
	public Meter getCollectExceptions() {
		return collectExceptions;
	}

	/**
	 * Computes a delta between the named passed sample and the prior sample for the same name.
	 * @param name The name of the delta
	 * @param sample The new sample
	 * @param defaultValue The value to return if there was no prior sample
	 * @return the computed delta which could be null
	 */
	protected Long delta(final String name, final long sample, final Long defaultValue) {
		final long[] samp = new long[]{sample};
		long[] state = longDeltas.putIfAbsent(name, samp);
		if(state==null) {
//			log.info("Initialized Delta [{}]: {}", name, sample);
			return defaultValue;
		}
		state = longDeltas.replace(name, samp);
		long delta = sample - state[0];
//		if(delta!=0) {
//			log.info("Calc Delta [{}]: state:{}, sample:{}, delta:{}", name, state[0], sample, delta);
//		}
		return delta;
	}
	
	/**
	 * Computes a delta between the named passed sample and the prior sample for the same name.
	 * @param name The name of the delta
	 * @param sample The new sample
	 * @return the computed delta which could be null
	 */
	protected Long delta(final String name, final long sample) {
		return delta(name, sample, null);
	}
	

	/**
	 * Computes the delta between the current time and the prior call for the same elapsed name
	 * @param name The name of the elapsed time context
	 * @return the elapsed time which could be -1L if this is the first call
	 */
	protected long elapsed(final String name) {
		return delta(name, System.nanoTime(), -1L);
	}
	
	
	/**
	 * Calculates a percent
	 * @param part The part 
	 * @param whole The whole
	 * @return The percentage that the part is of the whole
	 */
	protected int percent(double part, double whole) {
		if(part==0d || whole==0d) return 0;
		double p = part/whole*100;
		return (int) Math.round(p);
	}
}
