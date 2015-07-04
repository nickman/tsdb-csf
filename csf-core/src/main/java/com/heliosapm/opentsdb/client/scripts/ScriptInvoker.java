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
package com.heliosapm.opentsdb.client.scripts;

import java.util.Map;

/**
 * <p>Title: ScriptInvoker</p>
 * <p>Description: Invoker for a specified {@link ScriptType}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.scripts.ScriptInvoker</code></p>
 */

public interface ScriptInvoker {
	/** The key under which the target interface class should be bound */
	public static final String CLASS_KEY = "javax.script.iface.class";

	/**
	 * Invokes the named script
	 * @param scriptName The script name
	 * @param object The name of the bound object containing the target function
	 * @param functionName The name of the function to invoke
	 * @param bindings The invocation bindings
	 * @param args The optional positional args
	 * @return the return value of the invocation
	 */
	public Object invoke(final String scriptName, final String object, final String functionName, final Map<String, Object> bindings, final Object...args);
	
	
	public static class EvalScriptInvoker implements ScriptInvoker {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.scripts.ScriptInvoker#invoke(java.lang.String, java.lang.String, java.lang.String, java.util.Map, java.lang.Object[])
		 */
		@Override
		public Object invoke(final String scriptName, final String object, final String functionName, final Map<String, Object> bindings, final Object... args) {
			return ScriptManager.getInstance().eval(scriptName, bindings, args);
		}
	}
	
	public static class CompiledScriptInvoker implements ScriptInvoker {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.scripts.ScriptInvoker#invoke(java.lang.String, java.lang.String, java.lang.String, java.util.Map, java.lang.Object[])
		 */
		@Override
		public Object invoke(final String scriptName, final String object, final String functionName, final Map<String, Object> bindings, final Object... args) {
			return ScriptManager.getInstance().invoke(scriptName, bindings, args);
		}
	}
	
	public static class MethodScriptInvoker implements ScriptInvoker {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.scripts.ScriptInvoker#invoke(java.lang.String, java.lang.String, java.lang.String, java.util.Map, java.lang.Object[])
		 */
		@Override
		public Object invoke(final String scriptName, final String object, final String functionName, final Map<String, Object> bindings, final Object... args) {
			return ScriptManager.getInstance().invokeMethod(scriptName, object, functionName, bindings, args);
		}
	}
	
	public static class FunctionScriptInvoker implements ScriptInvoker {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.scripts.ScriptInvoker#invoke(java.lang.String, java.lang.String, java.lang.String, java.util.Map, java.lang.Object[])
		 */
		@Override
		public Object invoke(final String scriptName, final String object, final String functionName, final Map<String, Object> bindings, final Object... args) {
			return ScriptManager.getInstance().invokeFunction(scriptName, functionName, bindings, args);
		}
	}
	
	public static class InterfaceScriptGetter implements ScriptInvoker {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.scripts.ScriptInvoker#invoke(java.lang.String, java.lang.String, java.lang.String, java.util.Map, java.lang.Object[])
		 */
		@Override
		public Object invoke(final String scriptName, final String object, final String functionName, final Map<String, Object> bindings, final Object... args) {
			Class<?> clazz = (Class<?>)bindings.get(CLASS_KEY); 
			if(object==null || object.trim().isEmpty()) {
				return ScriptManager.getInstance().getInterface(scriptName, clazz);
			}
			return ScriptManager.getInstance().getInterface(scriptName, object, clazz);
		}
	}
	
	
}
