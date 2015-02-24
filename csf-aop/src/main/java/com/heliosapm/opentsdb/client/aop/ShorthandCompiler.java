/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2015, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package com.heliosapm.opentsdb.client.aop;

import java.lang.reflect.Member;

import com.heliosapm.opentsdb.client.aop.naming.MetricNameCompiler;
import com.heliosapm.opentsdb.client.aop.naming.MetricNameProvider;

/**
 * <p>Title: ShorthandCompiler</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.ShorthandCompiler</code></p>
 */

public class ShorthandCompiler {
	/** The singleton instance */
	private static volatile ShorthandCompiler instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/**
	 * Acquires and returns the ShorthandCompiler singleton instance
	 * @return the ShorthandCompiler singleton instance
	 */
	public static ShorthandCompiler getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new ShorthandCompiler();
				}
			}
		}
		return instance;
	}

	/**
	 * Creates a new ShorthandCompiler
	 */
	private ShorthandCompiler() {}
	
	/**
	 * Acquires a metric name provider for the passed fefines Shorthand joinpoint
	 * @param clazz The class the joinpoint is in
	 * @param member The class member the joinpoint is on
	 * @param nameTemplate The metric naming template
	 * @return The metric name provider
	 */
	public MetricNameProvider getNameProvider(final Class<?> clazz, final Member member, final String nameTemplate) {
		return MetricNameCompiler.getMetricNameProvider(clazz, member, nameTemplate);
	}

}
