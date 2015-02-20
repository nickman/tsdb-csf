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
package com.heliosapm.opentsdb.client.util;
import java.util.concurrent.atomic.AtomicLong;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

/**
 * <p>Title: JSExpressionEvaluator</p>
 * <p>Description: Utility singleton class to evaluate javascript snippets instead of coding the raw reflection.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.JSExpressionEvaluator</code></p>
 */

public class JSExpressionEvaluator {
	/** The singleton instance */
	private static volatile JSExpressionEvaluator instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	

	
	/** The script engine manager */
	private final ScriptEngineManager sec = new ScriptEngineManager();
	/** The shared JS script engine */
	private final ScriptEngine engine = sec.getEngineByExtension("js");
	/** A binding serial number */
	private final AtomicLong serial  = new AtomicLong(0L);
	
	
	/**
	 * Acquires the singleton JSExpressionEvaluator instance
	 * @return the singleton JSExpressionEvaluator instance
	 */
	public static JSExpressionEvaluator getInstance() {		
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new JSExpressionEvaluator();
				}
			}
		}
		return instance;
	}
	
	private JSExpressionEvaluator() {}

	
	/**
	 * Evaluates the passed JS expression
	 * @param expression The expression to evaluate
	 * @param binds Objects to be bound into the engine for evaluation. Tokens named <b><code>#</i>n</i>#</code></b> will be replaced with service generated unique tokens.
	 * @return the value the expression resolves to 
	 */
	public Object evaluate(CharSequence expression, Object...binds) {
		String script = expression.toString();
		SimpleBindings bindings = new SimpleBindings();		
		if(binds!=null) {			
			for(int i = 0; i < binds.length; i++) {
				String token = String.format("##%s##", i);
				String bind = String.format("__%s__", serial.incrementAndGet());
				script = script.replace(token, bind);
				bindings.put(bind, binds[i]);				
			}
		}
		try {
			return engine.eval(script, bindings);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to evaluate expression [" + script + "]", ex);
		} 		
	}
	
	/**
	 * Quickies tester
	 * @param args None
	 */
	public static void main(String[] args) {
		log("JSExpressionEvaluator Test");
		JSExpressionEvaluator jse = JSExpressionEvaluator.getInstance();
		for(int i = 0; i < 10; i++) {
			for(int x = 3; x > 0; x--) {
				log("Eval of %s + %s: %s", i, x, jse.evaluate("##0## + ##1##", i, x));
			}
		}
		log("Engine Scope:%s", jse.engine.getBindings(ScriptContext.ENGINE_SCOPE).size());
		log("Global Scope:%s", jse.engine.getBindings(ScriptContext.GLOBAL_SCOPE).size());
	}
	
	/**
	 * Simple out formatted logger
	 * @param fmt The format of the message
	 * @param args The message arguments
	 */
	public static void log(String fmt, Object...args) {
		System.out.println(String.format(fmt, args));
	}
}

