/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
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
package com.heliosapm.opentsdb.client.aop.annotations;

/**
 * <p>Title: InstrumentedClass</p>
 * <p>Description: Annotation applied to instrumented classes (and removed when the class is restored)</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.annotations.InstrumentedClass</code></p>
 */

public @interface InstrumentedClass {
	/**
	 * A hash code uniquely identifying the combinations of instrumentation options applied to this class.
	 */
	public long longHashCode();
	
	/**
	 * The timestamp of when this annotation was applied
	 */
	public long timestamp();
	
	/**
	 * The shorthand expression used to compile the instrumentation applied to this class
	 */
	public String shorthand();
}
