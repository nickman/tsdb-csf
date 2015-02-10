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

import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.util.Timeout;

import com.heliosapm.opentsdb.client.logging.LoggingConfiguration;
import com.heliosapm.opentsdb.client.opentsdb.Threading;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;

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
	
	public static void main(String[] args) {
		log("Testing MOS");
		MBeanObserverSet mos = build(RuntimeMBeanServerConnection.newInstance(ManagementFactory.getPlatformMBeanServer()), 5, TimeUnit.SECONDS);
		log("MOS enabled with [" + mos.enabledObservers.size() + "] MBeanObservers");
		try { Thread.currentThread().join(); } catch (Exception ex) {}
	}
	
	public static void log(Object msg) {
		System.out.println(msg);
	}
	
	public static MBeanObserverSet build(final RuntimeMBeanServerConnection mbeanServer, final long period, final TimeUnit unit) {
		final MBeanObserverSet mos = new MBeanObserverSet(mbeanServer, period, unit);		
		BaseMBeanObserver observer = new ClassLoadingMBeanObserver(mbeanServer, null);
		mos.enabledObservers.add(observer);
		final Map<String, String> tags = observer.getTags();
		mos.enabledObservers.add(new CompilationMBeanObserver(mbeanServer, tags));
		mos.enabledObservers.add(new GarbageCollectorMBeanObserver(mbeanServer, tags));
		mos.enabledObservers.add(new MemoryCollectorMBeanObserver(mbeanServer, tags));
		mos.enabledObservers.add(new MemoryPoolsCollectorMBeanObserver(mbeanServer, tags));
		mos.start();
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
