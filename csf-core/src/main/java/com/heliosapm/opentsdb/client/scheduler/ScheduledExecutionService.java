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
package com.heliosapm.opentsdb.client.scheduler;

import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.ObjectName;

import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.jmx.JMXManagedScheduler;
import com.heliosapm.utils.jmx.JMXManagedThreadPool;

/**
 * <p>Title: ScheduledExecutionService</p>
 * <p>Description: Advanced task scheduling services</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.scheduler.ScheduledExecutionService</code></p>
 */

public class ScheduledExecutionService implements UncaughtExceptionHandler, RejectedExecutionHandler {
	/** The singleton instance */
	private static volatile ScheduledExecutionService instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** The MBean ObjectName for the execution service thread pool */
	public static final ObjectName EXEC_THREAD_POOL_OBJECT_NAME = JMXHelper.objectName("com.heliosapm.execution:service=ExecutionThreadPool");
	/** The MBean ObjectName for the execution service scheduler */
	public static final ObjectName SCHEDULER_OBJECT_NAME = JMXHelper.objectName("com.heliosapm.execution:service=SchedulerThreadPool");

	/** The number of CPU cores available to the JVM */
	public static final int CORES = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
	
	/** Instance logger */
	private final Logger log = LoggerFactory.getLogger(getClass());	
	/** The execution thread pool */
	private final JMXManagedThreadPool threadPool;
	/** The execution scheduler */
	private final JMXManagedScheduler scheduler;
	
	
	/**
	 * Acquires and returns the ScheduledExecutionService singleton instance
	 * @return the ScheduledExecutionService singleton instance
	 */
	public static ScheduledExecutionService getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new ScheduledExecutionService();
					instance.log.info("Initialized ScheduledExecutionService");
				}
			}
		}
		return instance;
	}

	/**
	 * Creates a new ScheduledExecutionService
	 * FIXME: implement configuration for these thread pools
	 */
	public ScheduledExecutionService() {
		threadPool = new JMXManagedThreadPool(EXEC_THREAD_POOL_OBJECT_NAME, "ExecutionThreadPool", CORES, CORES, 1024, 60000, 100, 90);
		threadPool.setRejectedExecutionHandler(this);
		threadPool.setUncaughtExceptionHandler(this);
		scheduler = new JMXManagedScheduler(SCHEDULER_OBJECT_NAME, "SchedulerThreadPool", CORES, true);
	}
	
	
	/**
	 * Schedules the fixed rate execution of a command 
	 * @param command The runnable to schedule
	 * @param period The fixed rate period
	 * @param unit The unit of the fixed rate period
	 * @return the schedule handle
	 */
	public <T> ScheduledFuture<T> scheduleFixedRateExecution(final Runnable command, final long period, final TimeUnit unit) {
		if(command==null) throw new IllegalArgumentException("The passed command was null");		
		if(period < 1) throw new IllegalArgumentException("The period was invalid:" + period);
		return (ScheduledFuture<T>) scheduler.scheduleAtFixedRate(command, 2000, TimeUnit.MILLISECONDS.convert(period, unit), TimeUnit.MILLISECONDS);				
	}
	
	/**
	 * Schedules the fixed delay execution of a command 
	 * @param command The runnable to schedule
	 * @param period The fixed delay period
	 * @param unit The unit of the fixed delay period
	 * @return the schedule handle
	 */
	public <T> ScheduledFuture<T> scheduleFixedDelayExecution(final Runnable command, final long period, final TimeUnit unit) {
		if(command==null) throw new IllegalArgumentException("The passed command was null");		
		if(period < 1) throw new IllegalArgumentException("The period was invalid:" + period);
		return (ScheduledFuture<T>) scheduler.scheduleWithFixedDelay(command, 2000, TimeUnit.MILLISECONDS.convert(period, unit), TimeUnit.MILLISECONDS);				
	}
	
	
	
	
	
	/**
	 * Schedules the passed task for execution in accordance with the passed cron expression
	 * @param command The task to schedule
	 * @param cron The cron expression. (See {@link CronExpression})
	 * @return a handle to the schedule
	 */
	public <T> ScheduledFuture<T> scheduleWithCron(final Callable<T> command, String cron) {
		try {
			return new CronScheduledFuture<T>(command, new CronExpression(cron));
		} catch (Exception ex) {
			throw new RuntimeException("Failed to schedule task [" + command + "] with cron expression [" + cron + "]", ex);
		}
	}
	

	
	

	/**
	 * {@inheritDoc}
	 * @see java.util.concurrent.RejectedExecutionHandler#rejectedExecution(java.lang.Runnable, java.util.concurrent.ThreadPoolExecutor)
	 */
	@Override
	public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
		
	}

	/**
	 * {@inheritDoc}
	 * @see java.lang.Thread.UncaughtExceptionHandler#uncaughtException(java.lang.Thread, java.lang.Throwable)
	 */
	@Override
	public void uncaughtException(final Thread t, final Throwable e) {
		
	}
	
	
	/**
	 * <p>Title: CronScheduledFuture</p>
	 * <p>Description: Task wrapper to support Cron based scheduling with a standard java Scheduler</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.jmx.execution.ScheduledExecutionService.CronScheduledFuture</code></p>
	 * @param <T> The expected return type from the scheduled task
	 */
	class CronScheduledFuture<T> implements Callable<T>, ScheduledFuture<T> {
		/** The scheduled task */
		protected final Callable<T> command;
		/** The cron expression managing the execution */
		protected final CronExpression cex;
		/** The scheduled thingy for the next scheduled execution */
		protected final AtomicReference<ScheduledFuture<T>> nextExecutionFuture = new AtomicReference<ScheduledFuture<T>>(null);
		/** Indicates if the schedule has been canceled */
		protected final AtomicBoolean cancelled = new AtomicBoolean(false);
		/**
		 * Creates a new CronScheduleFuture
		 * @param command The repeating command
		 * @param cex The cron expression
		 */
		public CronScheduledFuture(Callable<T> command, CronExpression cex) {
			this.command = command;
			this.cex = cex;
			Long nextExec = nextExecutionTime();
			if(nextExec==null) {
				cancel(true);
				return;
			}
			nextExecutionFuture.set(scheduler.schedule(this, nextExec, TimeUnit.MILLISECONDS));
		}
		
		/**
		 * Returns the next valid execution time in ms.UTC after now
		 * @return the next valid execution time or null if there isn't one
		 */
		public Long nextExecutionTime() {
			Date nextExec = cex.getNextValidTimeAfter(new Date());
			if(nextExec==null) return null;
			return nextExec.getTime();
		}

		/**
		 * {@inheritDoc}
		 * @see java.util.concurrent.Callable#call()
		 */
		@Override
		public T call() throws Exception {
			T result = command.call();
			if(!cancelled.get()) {
				synchronized(cancelled) {
					if(!cancelled.get()) {
						Long nextExec = nextExecutionTime();
						if(nextExec==null) {
							cancel(true);							
						} else {
							nextExecutionFuture.set(scheduler.schedule(this, nextExec, TimeUnit.MILLISECONDS));
						}												
					}
				}
			}
			return result;
		}

		/**
		 * Returns the next execution future
		 * @return the nextExecutionFuture
		 */
		public ScheduledFuture<T> getNextExecutionFuture() {
			return nextExecutionFuture.get();
		}

		/**
		 * Sets the next execution future
		 * @param nextExecutionFuture the nextExecutionFuture to set
		 */
		public void setNextExecutionFuture(ScheduledFuture<T> nextExecutionFuture) {
			ScheduledFuture<T> prior = this.nextExecutionFuture.getAndSet(nextExecutionFuture);
			if(prior!=null) {
				prior.cancel(true);
			}
		}

		/**
		 * {@inheritDoc}
		 * @see java.util.concurrent.Delayed#getDelay(java.util.concurrent.TimeUnit)
		 */
		@Override
		public long getDelay(TimeUnit unit) {
			ScheduledFuture<T> sf = nextExecutionFuture.get();
			if(sf==null) throw new RuntimeException("No scheduled task in state", new Throwable());						
			return sf.getDelay(unit);
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		@Override
		public int compareTo(Delayed delayed) {
			ScheduledFuture<T> sf = nextExecutionFuture.get();
			if(sf==null) throw new RuntimeException("No scheduled task in state", new Throwable());			
			return sf.compareTo(delayed);
		}

		/**
		 * {@inheritDoc}
		 * @see java.util.concurrent.Future#cancel(boolean)
		 */
		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			if(cancelled.compareAndSet(false, true)) {
				ScheduledFuture<T> sf = nextExecutionFuture.get();
				if(sf!=null) {
					sf.cancel(mayInterruptIfRunning);
				}				
			}
			return true;
		}

		/**
		 * {@inheritDoc}
		 * @see java.util.concurrent.Future#isCancelled()
		 */
		@Override
		public boolean isCancelled() {
			return cancelled.get();
		}

		/**
		 * {@inheritDoc}
		 * @see java.util.concurrent.Future#isDone()
		 */
		@Override
		public boolean isDone() {
			ScheduledFuture<T> sf = nextExecutionFuture.get(); 
			return sf == null || sf.isDone();
		}

		/**
		 * {@inheritDoc}
		 * @see java.util.concurrent.Future#get()
		 */
		@Override
		public T get() throws InterruptedException, ExecutionException {
			ScheduledFuture<T> sf = nextExecutionFuture.get();
			if(sf==null) throw new ExecutionException("No scheduled task in state", new Throwable());
			return sf.get();
		}

		/**
		 * {@inheritDoc}
		 * @see java.util.concurrent.Future#get(long, java.util.concurrent.TimeUnit)
		 */
		@Override
		public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			ScheduledFuture<T> sf = nextExecutionFuture.get();
			if(sf==null) throw new ExecutionException("No scheduled task in state", new Throwable());			
			return sf.get(timeout, unit);
		}
	}
	

}
