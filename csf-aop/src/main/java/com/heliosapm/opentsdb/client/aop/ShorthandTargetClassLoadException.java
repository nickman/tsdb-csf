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
package com.heliosapm.opentsdb.client.aop;

/**
 * <p>Title: ShorthandTargetClassLoadException</p>
 * <p>Description: Exception thrown when the Shorthand compiler cannot load the specified target class</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.ShorthandTargetClassLoadException</code></p>
 */

public class ShorthandTargetClassLoadException extends ShorthandParseFailureException {

	/**  */
	private static final long serialVersionUID = -7728604146889170893L;

	/**
	 * Creates a new ShorthandTargetClassLoadException
	 * @param message The failure message
	 * @param expression The expression that failed
	 * @param cause The underlying cause
	 */
	public ShorthandTargetClassLoadException(String message, String expression, Throwable cause) {
		super(message, expression, cause);
	}
	
	/**
	 * Creates a new ShorthandTargetClassLoadException
	 * @param message The failure message
	 * @param expression The expression that failed
	 */
	public ShorthandTargetClassLoadException(String message, String expression) {
		super(message, expression);
	}

}
