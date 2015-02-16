/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2013, Helios Development Group and individual contributors
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
package test.com.heliosapm.base;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

/**
 * <p>Title: ExceptionRecordingAssert</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>test.com.heliosapm.shorthand.ExceptionRecordingAssert</code></p>
 */

public class ExceptionRecordingAssert {
	
	/**  */
	static final Map<Method, Method> ifaceToClassMapping = new ConcurrentHashMap<Method, Method>();
	
	/** If true, records exceptions and suppresses, otherwise throws */
	public static boolean recordingMode = true;
	
	public static IAssert recorder(final AtomicInteger counter, final Map<Integer, String> exceptionContainer) {
		
		return (IAssert) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[]{IAssert.class}, new InvocationHandler(){
			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				try {
					Method m = ifaceToClassMapping.get(method);
					if(m==null) {
						m = Assert.class.getDeclaredMethod(method.getName(), method.getParameterTypes());
						ifaceToClassMapping.put(method, m);
					}
					return m.invoke(null, args);
				} catch (Throwable t) {
					if(!recordingMode) throw t;
					if(t instanceof InvocationTargetException) {
						InvocationTargetException ite = (InvocationTargetException)t;
						exceptionContainer.put(counter.incrementAndGet(), ite.getCause().toString());
					} else {
						exceptionContainer.put(counter.incrementAndGet(), t.toString());
					}
					
					t.printStackTrace(System.err);
					return null;
				}
			}
		});
	}
	
	

}
