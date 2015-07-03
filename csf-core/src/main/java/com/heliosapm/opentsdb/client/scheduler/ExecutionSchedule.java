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

import java.util.regex.Pattern;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

/**
 * <p>Title: ExecutionSchedule</p>
 * <p>Description: Defines an execution schedule based on an expression</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.scheduler.ExecutionSchedule</code></p>
 */

public class ExecutionSchedule {
	/** The schedule type */
	final ScheduleType scheduleType;
	/** The period if the type is fixed delay or fixed rate */
	final int period;
	/** The cron expression if the  type is cron */
	final String cron;
	
	/** The default execution schedule of NONE */
	public static final ExecutionSchedule NO_EXEC_SCHEDULE = new ExecutionSchedule("", true);
	
	/** A cache of execution schedule instances keyed by the expression */
	private static final NonBlockingHashMap<String, ExecutionSchedule> instances = new NonBlockingHashMap<String, ExecutionSchedule>();
	
	/** The fixed rate expression matcher */
	public static final Pattern RATE_EXPR = Pattern.compile("r(\\d+)", Pattern.CASE_INSENSITIVE);
	/** The fixed delay expression matcher */
	public static final Pattern DELAY_EXPR = Pattern.compile("d(\\d+)", Pattern.CASE_INSENSITIVE);
	/** The cron expression matcher (with thanks to <a href="http://stackoverflow.com/users/895295/leo">Leo</a> */
	public static final Pattern CRON_EXPR = Pattern.compile("^\\s*($|#|\\w+\\s*=|(\\?|\\*|(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?(?:,(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?)*)\\s+(\\?|\\*|(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?(?:,(?:[0-5]?\\d)(?:(?:-|\\/|\\,)(?:[0-5]?\\d))?)*)\\s+(\\?|\\*|(?:[01]?\\d|2[0-3])(?:(?:-|\\/|\\,)(?:[01]?\\d|2[0-3]))?(?:,(?:[01]?\\d|2[0-3])(?:(?:-|\\/|\\,)(?:[01]?\\d|2[0-3]))?)*)\\s+(\\?|\\*|(?:0?[1-9]|[12]\\d|3[01])(?:(?:-|\\/|\\,)(?:0?[1-9]|[12]\\d|3[01]))?(?:,(?:0?[1-9]|[12]\\d|3[01])(?:(?:-|\\/|\\,)(?:0?[1-9]|[12]\\d|3[01]))?)*)\\s+(\\?|\\*|(?:[1-9]|1[012])(?:(?:-|\\/|\\,)(?:[1-9]|1[012]))?(?:L|W)?(?:,(?:[1-9]|1[012])(?:(?:-|\\/|\\,)(?:[1-9]|1[012]))?(?:L|W)?)*|\\?|\\*|(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?:(?:-)(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?(?:,(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)(?:(?:-)(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?)*)\\s+(\\?|\\*|(?:[0-6])(?:(?:-|\\/|\\,|#)(?:[0-6]))?(?:L)?(?:,(?:[0-6])(?:(?:-|\\/|\\,|#)(?:[0-6]))?(?:L)?)*|\\?|\\*|(?:MON|TUE|WED|THU|FRI|SAT|SUN)(?:(?:-)(?:MON|TUE|WED|THU|FRI|SAT|SUN))?(?:,(?:MON|TUE|WED|THU|FRI|SAT|SUN)(?:(?:-)(?:MON|TUE|WED|THU|FRI|SAT|SUN))?)*)(|\\s)+(\\?|\\*|(?:|\\d{4})(?:(?:-|\\/|\\,)(?:|\\d{4}))?(?:,(?:|\\d{4})(?:(?:-|\\/|\\,)(?:|\\d{4}))?)*))$", Pattern.CASE_INSENSITIVE);
	
	/** 
	 * Creates a new ExecutionSchedule from the passed expression where the understood expressions are:<ul>
	 * 	<li>A fixed <b>rate</b> schedule in seconds as <b><code>"r&lt;number of seconds&gt;"</code></b>. e.g. <b><code>r10</code></b> for every 10 seconds.</li>
	 *  <li>A fixed <b>delay</b> schedule in seconds as <b><code>"d&lt;number of seconds&gt;"</code></b>. e.g. <b><code>d10</code></b> for every 10 seconds.</li>
	 *  <li>A cron expression. e.g. <b><code>"0 0 14-6 ? * FRI-MON"</code></b>.</li>
	 * </ul>
	 * <p>An empty (but not null) string implies {@link ScheduleType#NONE}.
	 * @param scheduleExpression The schedule expression
	 * @param defaultToNone If true, defaults the schedule to {@link ScheduleType#NONE} if the schedule expression is invalid.
	 * Otherwise throws an exception.
	 * @return The execution schedule
	 */
	public static ExecutionSchedule getInstance(final String scheduleExpression, final boolean defaultToNone) {
		if(scheduleExpression==null) throw new IllegalArgumentException("The passed Schedule Expression was null");
		final String _scheduleExpression = scheduleExpression.trim();
		ExecutionSchedule es = instances.get(_scheduleExpression);
		if(es==null) {
			synchronized(instances) {
				es = instances.get(_scheduleExpression);
				if(es==null) {
					es = new ExecutionSchedule(scheduleExpression, defaultToNone);
				}
			}
		}
		return es;
	}
	
	/**
	 * Invokes {@link #getInstance(String, boolean)} with a true default to None 
	 * @param scheduleExpression The schedule expression
	 * @return the execution schedule
	 */
	public static ExecutionSchedule getInstance(final String scheduleExpression) {
		return getInstance(scheduleExpression, true);
	}

	/**
	 * Creates a new ExecutionSchedule from the passed expression where the understood expressions are:<ul>
	 * 	<li>A fixed <b>rate</b> schedule in seconds as <b><code>"r&lt;number of seconds&gt;"</code></b>. e.g. <b><code>r10</code></b> for every 10 seconds.</li>
	 *  <li>A fixed <b>delay</b> schedule in seconds as <b><code>"d&lt;number of seconds&gt;"</code></b>. e.g. <b><code>d10</code></b> for every 10 seconds.</li>
	 *  <li>A cron expression. e.g. <b><code>"0 0 14-6 ? * FRI-MON"</code></b>.</li>
	 * </ul>
	 * <p>An empty (but not null) string implies {@link ScheduleType#NONE}.
	 * @param scheduleExpression The schedule expression
	 */
	private ExecutionSchedule(final String scheduleExpression, final boolean defaultToNone) {
		if(scheduleExpression==null) throw new IllegalArgumentException("The passed Schedule Expression was null");
		
		if(scheduleExpression.isEmpty()) {
			scheduleType = ScheduleType.NONE;
			cron = null;
			period = -1;			
		} else {
			if(RATE_EXPR.matcher(scheduleExpression).matches()) {
				scheduleType = ScheduleType.FIXED_RATE;
				cron = null;
				period = Integer.parseInt(scheduleExpression.substring(1));
			} else if(DELAY_EXPR.matcher(scheduleExpression).matches()) {
				scheduleType = ScheduleType.FIXED_DELAY;
				cron = null;
				period = Integer.parseInt(scheduleExpression.substring(1));
			} else if(CRON_EXPR.matcher(scheduleExpression).matches()) {
				scheduleType = ScheduleType.CRON;
				cron = scheduleExpression;
				period = -1;
			} else {
				if(defaultToNone) {
					scheduleType = ScheduleType.NONE;
					cron = null;
					period = -1;								
				} else {
					throw new IllegalArgumentException("Failed to interpret schedule expression [" + scheduleExpression + "]");
				}
			}
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		switch(scheduleType) {
			case CRON:
				return "cron: " + cron;
			case FIXED_DELAY:
				return "fixed delay:" + period + " sec.";
			case FIXED_RATE:
				return "fixed rate:" + period + " sec.";
			case NONE:
				return "None";
			default:
				return "None";
		}
	}

	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cron == null) ? 0 : cron.hashCode());
		result = prime * result + period;
		result = prime * result
				+ ((scheduleType == null) ? 0 : scheduleType.hashCode());
		return result;
	}

	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ExecutionSchedule other = (ExecutionSchedule) obj;
		if (cron == null) {
			if (other.cron != null)
				return false;
		} else if (!cron.equals(other.cron))
			return false;
		if (period != other.period)
			return false;
		if (scheduleType != other.scheduleType)
			return false;
		return true;
	}

	/**
	 * Returns the schedule type for this schedule
	 * @return the schedule type for this schedule
	 */
	public final ScheduleType getScheduleType() {
		return scheduleType;
	}

	/**
	 * Returns the execution period in seconds
	 * @return the execution period in seconds if this is a fixed rate or fixed delay schedule, -1 otherwise
	 */
	public final int getPeriod() {
		return period;
	}

	/**
	 * Returns the cron schedule expression
	 * @return the cron schedule expression if this is a cron schedule, null otherwise
	 */
	public final String getCron() {
		return cron;
	}
	
	

}
