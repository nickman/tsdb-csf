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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.util.Timeout;
import org.w3c.dom.Node;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.heliosapm.opentsdb.client.boot.XMLLoader;
import com.heliosapm.opentsdb.client.logging.LoggingConfiguration;
import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OpenTSDBReporter;
import com.heliosapm.opentsdb.client.opentsdb.Threading;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.opentsdb.client.util.JMXHelper;
import com.heliosapm.opentsdb.client.util.XMLHelper;

/**
 * <p>Title: MBeanObserverSet</p>
 * <p>Description: A set of MBeanObservers with scheduled polling</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSet</code></p>
 */

public class MBeanObserverSet implements Runnable {
	/** Instance logger */
	protected final Logger LOG = LogManager.getLogger(getClass());
	/** The enabled observers */
	protected final Set<BaseMBeanObserver> enabledObservers = new HashSet<BaseMBeanObserver>();
	/** The disabled observers */
	protected final Set<BaseMBeanObserver> disabledObservers = new HashSet<BaseMBeanObserver>();
	/** The poller schedule handle */
	protected Timeout scheduleHandle = null;
	/** Indicates if the set is active */
	protected final AtomicBoolean active = new AtomicBoolean(false);
	/** The target mbean server */
	final RuntimeMBeanServerConnection mbeanServer;
	/** The polling period */
	final long period;
	/** The polling period unit */
	final TimeUnit unit;
	
	
	static {
		LoggingConfiguration.getInstance();
	}
	
	public static void mainx(String[] args) {
		log("Testing MOS");
		System.setProperty("tsdb.http.tsdb.url", "http://10.12.114.48:4241");
		System.setProperty("tsdb.http.compression.enabled", "false");
		System.setProperty(Constants.PROP_JMX_LIVE_GC_TRACING, "true");
		//System.setProperty(Constants.PROP_JMX_HOTSPOT_TRACING, Arrays.toString(MBeanObserver.getHotSpotMBeanShortNames()).replace("[", "").replace("]", ""));
		System.setProperty(Constants.PROP_JMX_HOTSPOT_TRACING, "runtime, memory");
//		System.setProperty(Constants.PROP_JMX_HOTSPOT_RUNTIME, ".*");
		System.setProperty(Constants.PROP_TRACE_TO_STDOUT, "true");
		System.setProperty(Constants.PROP_STDOUT_JSON, "true");
		MetricBuilder.reconfig();
		log("Hotspot MBeans:[" + System.getProperty(Constants.PROP_JMX_HOTSPOT_TRACING) + "]");
		final RuntimeMBeanServerConnection mbs = RuntimeMBeanServerConnection.newInstance(JMXHelper.getHeliosMBeanServer());
		String[] hotspotCounters = ConfigurationReader.confStrArr(Constants.PROP_JMX_HOTSPOT_TRACING, Constants.DEFAULT_JMX_HOTSPOT_TRACING);
		if(hotspotCounters.length > 0 && "*".equals(hotspotCounters[0])) {
			hotspotCounters = MBeanObserver.getHotSpotMBeanShortNames();
		}
		for(String counterName: hotspotCounters) {
			//new HotSpotInternalsBaseMBeanObserver(mbs, true, counterName, "(.*)");
		}
		MBeanObserverSet mos = build(mbs, 5, TimeUnit.SECONDS, true);
		log("MOS enabled with [" + mos.enabledObservers.size() + "] MBeanObservers");
		
		try { Thread.currentThread().join(); } catch (Exception ex) {}
	}
	
	public static void log(Object msg) {
		System.out.println(msg);
	}
	
	public static void main(String[] args) {
		log("Testing XMlInstaller");
		XMLLoader.boot("file:/c:/hprojects/tsdb-csf/csf-core/src/test/resources/configs/platform.xml");
		try { Thread.currentThread().join(); } catch (Exception ex) {}
	}
	
	
	/**
	 * Builds a new MBeanObserverSet from an XMLConfiguration and boots it
	 * @param serverConn The MBeanServerConnection to attach to
	 * @param xmlConfigNode The XMLConfig
	 * @return the built set
	 */
	public static MBeanObserverSet build(final MBeanServerConnection serverConn, final Node xmlConfigNode) {	
		final RuntimeMBeanServerConnection mbeanServer = RuntimeMBeanServerConnection.newInstance(serverConn);
		final long period = XMLHelper.getAttributeByName(xmlConfigNode, "period", 15L);
		final boolean collectorMBeans = XMLHelper.getAttributeByName(xmlConfigNode, "collectormbeans", false);
		final MBeanObserverSet mos = new MBeanObserverSet(mbeanServer, period, TimeUnit.SECONDS);
		final Set<String> includes = new HashSet<String>();
		final Set<String> excludes = new HashSet<String>();
		for(Node n : XMLHelper.getChildNodesByName(xmlConfigNode, "includes", false)) {
			final String s =  XMLHelper.getNodeTextValue(n, "");
			if(s!=null && !s.trim().isEmpty()) {
				includes.add(s.trim());
			}
		}
		for(Node n : XMLHelper.getChildNodesByName(xmlConfigNode, "excludes", false)) {
			final String s =  XMLHelper.getNodeTextValue(n, "");
			if(s!=null && !s.trim().isEmpty()) {
				excludes.add(s.trim());
			}
		}
		boolean hotspotEnabled = false;
		MBeanObserver[] observers = MBeanObserver.filter(includes, excludes);
		for(MBeanObserver mo: observers) {
			BaseMBeanObserver base = mo.getMBeanObserver(mbeanServer, null, collectorMBeans);
			if(base!=null) {
				if(!hotspotEnabled) {
					if(mo.objectName.toString().startsWith("sun.management")) {
						hotspotEnabled = true;
					}
				}
				mos.enabledObservers.add(base);
			}
		}
		if(hotspotEnabled) {
			JMXHelper.registerHotspotInternal(mbeanServer);
		}
		
		
		final MetricRegistry reg = new MetricRegistry();
		for(MetricSet ms: mos.enabledObservers) {
			if(ms==null) continue;
			try {
				reg.registerAll(ms);
			} catch (Exception x) {/* No Op */}
		}
		OpenTSDBReporter reporter = OpenTSDBReporter.forRegistry(reg).build();		
		mos.start();
		reporter.start(period, TimeUnit.SECONDS);		
		return mos;
	}
	
	/**
	 * Builds an MBeanObserver set for the platform mbeans
	 * @param mbeanServer The target MBeanServer
	 * @param period The polling period
	 * @param unit The polling period unit
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 * @return the built MBeanObserverSet
	 */
	public static MBeanObserverSet build(final MBeanServer mbeanServer, final long period, final TimeUnit unit, final boolean publishObserverMBean) {
		return build(RuntimeMBeanServerConnection.newInstance(mbeanServer), period, unit, publishObserverMBean);
	}
	
	/**
	 * Builds an MBeanObserver set for the platform mbeans
	 * @param mbeanServer The target MBeanServer
	 * @param period The polling period
	 * @param unit The polling period unit
	 * @param publishObserverMBean If true, an observer management MBean will be registered
	 * @return the built MBeanObserverSet
	 */
	public static MBeanObserverSet build(final RuntimeMBeanServerConnection mbeanServer, final long period, final TimeUnit unit, final boolean publishObserverMBean) {
		final MBeanObserverSet mos = new MBeanObserverSet(mbeanServer, period, unit);		
		BaseMBeanObserver observer = new ClassLoadingMBeanObserver(mbeanServer, null, publishObserverMBean);
		
		mos.enabledObservers.add(observer);
		final Map<String, String> tags = observer.getTags();
		mos.enabledObservers.remove(observer);
		MBeanObserver[] hotspotObservers = MBeanObserver.hotspotObservers(ConfigurationReader.conf(Constants.PROP_JMX_HOTSPOT_TRACING, Constants.DEFAULT_JMX_HOTSPOT_TRACING));
		
		if(hotspotObservers!=null && hotspotObservers.length > 0) {
			JMXHelper.registerHotspotInternal();
			for(MBeanObserver mob: hotspotObservers) {
				mos.enabledObservers.add(mob.getMBeanObserver(mbeanServer, tags, publishObserverMBean));
			}
		}
//		mos.enabledObservers.add(new CompilationMBeanObserver(mbeanServer, tags, publishObserverMBean));
//		mos.enabledObservers.add(new GarbageCollectorMBeanObserver(mbeanServer, tags, publishObserverMBean));
//		mos.enabledObservers.add(new MemoryCollectorMBeanObserver(mbeanServer, tags, publishObserverMBean));
//		mos.enabledObservers.add(new MemoryPoolsCollectorMBeanObserver(mbeanServer, tags, publishObserverMBean));
//		mos.enabledObservers.add(new OperatingSystemCollectorMBeanObserver(mbeanServer, tags, publishObserverMBean));
//		mos.enabledObservers.add(new ThreadingCollectorMBeanObserver(mbeanServer, tags, publishObserverMBean));
//		mos.enabledObservers.add(new HotSpotInternalsBaseMBeanObserver(mbeanServer, publishObserverMBean, tags, "runtime", "sun.rt._sync_(.*)"));
		mos.start();
		final MetricRegistry reg = new MetricRegistry();
		for(MetricSet ms: mos.enabledObservers) {
			if(ms==null) continue;
			reg.registerAll(ms);
		}
		OpenTSDBReporter reporter = OpenTSDBReporter.forRegistry(reg).build();
		//ConsoleReporter creporter = ConsoleReporter.forRegistry(reg).build();
		reporter.start(10, TimeUnit.SECONDS);
		//creporter.start(10, TimeUnit.SECONDS);
		return mos;
	}
	
	MBeanObserverSet(final RuntimeMBeanServerConnection mbeanServer, final long period, final TimeUnit unit) {
		this.mbeanServer = mbeanServer;
		this.period = period;
		this.unit = unit;
	}

	public void start() {
		if(active.compareAndSet(false, true)) {
			scheduleHandle = Threading.getInstance().schedule(this, 1, period, unit);
		}
	}
	
	public void stop() {
		if(active.compareAndSet(true, false)) {
			scheduleHandle.cancel();
			scheduleHandle = null;
		}
	}
	/**
	 * {@inheritDoc}
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		Iterator<BaseMBeanObserver> iter = enabledObservers.iterator();
		while(iter.hasNext()) {
			final BaseMBeanObserver observer = iter.next();
			try {
				observer.run();
			} catch (Exception ex) {
				iter.remove();
				disabledObservers.add(observer);
				LOG.error("MBeanObserverSet Collection Failure", ex);
			}
		}
	}
	
	
}
