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

/**
 * <p>Title: ScheduleType</p>
 * <p>Description: Enumerates the scheduling types</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.scheduler.ScheduleType</code></p>
 */

public enum ScheduleType {
	/** Fixed rate scheduling */
	FIXED_RATE,
	/** Fixed delay scheduling */
	FIXED_DELAY,
	/** Cron scheduling */
	CRON,
	/** No Schedule */
	NONE;
}
