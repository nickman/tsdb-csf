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

import java.lang.management.ManagementFactory;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.ThreadNameDeterminer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;

import com.heliosapm.opentsdb.client.logging.LoggingConfiguration;

/**
 * <p>Title: Threading</p>
 * <p>Description: Singleton to supply a shared thread pool and scheduler</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.Threading</code></p>
 */

public class Threading {
	/** The singleton instance */
	private static volatile Threading instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** The number of cores available to the JVM */
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	
	/** The shared thread pool */
	private final ExecutorService threadPool;
	


	/** The shared scheduler */
	private final HashedWheelTimer scheduler;
	/** Instance logger */
	private final Logger log = LogManager.getLogger(getClass());
	
	

	/**
	 * Acquires the Threading singleton instance
	 * @return the Threading singleton instance
	 */
	public static Threading getInstance() {		
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					LoggingConfiguration.getInstance();
					instance = new Threading(); 
				}
			}
		}
		return instance;
	}
	
	private Threading() {
		final int tickSize = ConfigurationReader.confInt(Constants.PROP_SCHEDULER_TICK, Constants.DEFAULT_SCHEDULER_TICK);
		final int wheelSize = ConfigurationReader.confInt(Constants.PROP_SCHEDULER_WHEEL, Constants.DEFAULT_SCHEDULER_WHEEL);
		final int poolSize = ConfigurationReader.confInt(Constants.PROP_TPOOL_SIZE, Constants.DEFAULT_TPOOL_SIZE);
		final int qSize = ConfigurationReader.confInt(Constants.PROP_TPOOL_QSIZE, Constants.DEFAULT_TPOOL_QSIZE);
		
		
		
		final ThreadFactory poolFactory = new ThreadFactory(){
			private final AtomicInteger serial = new AtomicInteger(0);
			@Override
			public Thread newThread(final Runnable r) {
				Thread t = new Thread(r, "metrics-opentsdb-worker#" + serial.incrementAndGet());
				t.setDaemon(true);
				return t;
			}
		};
		threadPool = new ThreadPoolExecutor(poolSize, poolSize, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(qSize, false), poolFactory);
		final ThreadFactory schedulerThreadFactory = new ThreadFactory(){
			private final AtomicInteger serial = new AtomicInteger(0);
			@Override
			public Thread newThread(final Runnable r) {
				Thread t = new Thread(r, "metrics-opentsdb-scheduler#" + serial.incrementAndGet());
				t.setDaemon(true);
				return t;
			}
		};
		final ThreadNameDeterminer threadNameDeterminer = new ThreadNameDeterminer() {
			@Override
			public String determineThreadName(String currentThreadName, String proposedThreadName) throws Exception {
				return proposedThreadName + " [" + currentThreadName + "]";
			}
			
		};
		scheduler = new HashedWheelTimer(schedulerThreadFactory, threadNameDeterminer, tickSize, TimeUnit.MILLISECONDS, wheelSize);
		scheduler.start();
		final Thread sdh = new Thread(getClass().getSimpleName() + "ShutDownHook") {
			@Override
			public void run() {
				scheduler.stop();
				threadPool.shutdown();
			}			
		};
		sdh.setDaemon(true);
		Runtime.getRuntime().addShutdownHook(sdh);
		log.info("ThreadPoolAndScheduling Service Initialized\n\tThreadPool Size:{}\n\tThreadPool QueueSize:{}\n\tTick Size: {}\n\tWheel Size: {}", poolSize, qSize, tickSize, wheelSize);
	}
	
	/**
	 * Returns the shared thread pool
	 * @return the shared thread pool
	 */
	public ExecutorService getThreadPool() {
		return threadPool;
	}

	
	/**
	 * Executes the passed task asynchronously
	 * @param task The task to execute
	 * @return a Future representing pending completion of the task
	 */
	public Future<?> async(final Runnable task) {
		return threadPool.submit(task);
	}
	
	/**
	 * Executes the passed callable task asynchronously
	 * @param task The callable task to execute
	 * @return a Future representing pending completion of the task
	 */
	public <T> Future<T> async(final Callable<T> task) {
		return threadPool.submit(task);
	}
	
	
	
	/**
	 * Schedules a task for one time execution some time in the future
	 * @param task The task to execute
	 * @param delayMs The delay until the task should be executed in ms.
	 * @return The timeout handle of the task which can be used to check on the state of the task
	 */
	public Timeout delay(final Runnable task, long delayMs) {
		return delay(task, delayMs, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Schedules a task for one time execution some time in the future
	 * @param task The task to execute
	 * @param delay The delay until the task should be executed
	 * @param unit The unit of the delay
	 * @return The timeout handle of the task which can be used to check on the state of the task
	 */
	public Timeout delay(final Runnable task, long delay, final TimeUnit unit) {
		if(task==null) throw new IllegalArgumentException("The passed task was null");
		if(unit==null) throw new IllegalArgumentException("The passed time unit was null");
		return scheduler.newTimeout(new TimerTask(){
			@Override
			public void run(final Timeout timeout) throws Exception {
				threadPool.execute(task);
			}
		}, delay, unit);
	}
	
	/**
	 * Schedules a task for repeated fixed delay execution on the specified period
	 * @param task The task to execute
	 * @param delay The initial and post execution delay
	 * @param unit The unit of the delay
	 * @return The timeout handle of the task which can be used to check on the state of the task and cancel the task
	 */
	public Timeout schedule(final Runnable task, final long delay, final TimeUnit unit) {
		if(task==null) throw new IllegalArgumentException("The passed task was null");
		if(unit==null) throw new IllegalArgumentException("The passed time unit was null");
		final DelegatingTimeout dt = new DelegatingTimeout(null);
		Timeout actualTimeOut = scheduler.newTimeout(new TimerTask(){
			@Override
			public void run(final Timeout timeout) throws Exception {
				threadPool.execute(new Runnable(){
					@Override
					public void run() {
						try {
							task.run();
						} finally {
							if(!timeout.isCancelled()) {
								dt.setTimeout(((DelegatingTimeout)schedule(task, delay, unit)).timeout);
							}
						}
					}
				});
			}
		}, delay, unit);
		dt.setTimeout(actualTimeOut);
		return dt;
	}
	
	/**
	 * Schedules a task for repeated fixed delay execution on the specified period in ms/
	 * @param task The task to execute
	 * @param delay The initial and post execution delay in ms.
	 * @return The timeout handle of the task which can be used to check on the state of the task and cancel the task
	 */
	public Timeout schedule(final Runnable task, final long delay) {
		return schedule(task, delay, TimeUnit.MILLISECONDS);		
	}
	
	
	private static class DelegatingTimeout implements Timeout {
		Timeout timeout = null;
		
		public DelegatingTimeout(Timeout timeout) {
			this.timeout = timeout;
		}
		
		public void setTimeout(Timeout timeout) {
			if(this.timeout!=null) {
				try { this.timeout.cancel(); } catch (Exception x) {/* No Op */}
			}
			this.timeout = timeout;
		};
		
		
		@Override
		public Timer getTimer() {
			return timeout.getTimer();
		}

		@Override
		public TimerTask getTask() {
			return timeout.getTask();
		}

		@Override
		public boolean isExpired() {
			return timeout.isExpired();
		}

		@Override
		public boolean isCancelled() {
			return timeout.isCancelled();
		}

		@Override
		public void cancel() {
			timeout.cancel();
		}

		
		
	}

}
