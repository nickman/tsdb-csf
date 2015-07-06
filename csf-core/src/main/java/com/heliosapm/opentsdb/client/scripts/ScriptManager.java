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

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

import com.heliosapm.opentsdb.client.classloaders.ClassLoaderRepository;
import com.heliosapm.utils.url.URLHelper;
import com.heliosapm.utils.xml.XMLHelper;

/**
 * <p>Title: ScriptManager</p>
 * <p>Description: Loads, compiles and manages JSR 223 dynamic scripts</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.scripts.ScriptManager</code></p>
 */

public class ScriptManager {
	/** The singleton instance */
	private static volatile ScriptManager instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	/** Instance logger */
	private final Logger log = LogManager.getLogger(getClass());
	/** The registered script engine managers keyed by loader name */
	private final ConcurrentHashMap<String, ScriptEngineManager> scriptEngineManagers= new ConcurrentHashMap<String, ScriptEngineManager>();
	/** The registered scripts keyed by name */
	private final ConcurrentHashMap<String, ScriptEngineFactory> scriptEngineFactories = new ConcurrentHashMap<String, ScriptEngineFactory>();
	/** The registered script engines keyed by their designated logical name */
	private final ConcurrentHashMap<String, ScriptEngine> scriptEngines= new ConcurrentHashMap<String, ScriptEngine>();
	/** The registered compiled scripts keyed by their designated logical name */
	private final ConcurrentHashMap<String, CompiledScript> compiledScripts = new ConcurrentHashMap<String, CompiledScript>();
	/** The registered invocable script engines keyed by their designated logical name */
	private final ConcurrentHashMap<String, Invocable> invocables = new ConcurrentHashMap<String, Invocable>();
	/** The registered source code for simple evals keyed by their designated logical name */
	private final ConcurrentHashMap<String, String> evalSourceCode = new ConcurrentHashMap<String, String>();
	
	
	
	/**
	 * Acquires and returns the ScriptManager singleton instance
	 * @return the ScriptManager singleton instance
	 */
	public static ScriptManager getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new ScriptManager();
				}
			}
		}
		return instance;
	}
	
	private ScriptManager() {
		
	}
	
	/**
	 * Returns an implementation of an interface using functions or script objects compiled in the interpreter
	 * @param scriptName The name of the registered invocable script
	 * @param objectName The compiled object name if getting an interface instance from a scripted object, null otherwise
	 * @param clazz The type of the interface to return an implementation of 
	 * @return the interface implementation
	 * @param <T> The assumed type of the returned object
	 */
	public <T> T getInterface(final String scriptName, final String objectName, Class<T> clazz) {
		if(scriptName==null || scriptName.trim().isEmpty()) throw new IllegalArgumentException("The passed script name was null or empty"); 
		if(clazz==null) throw new IllegalArgumentException("The passed class was null or empty");
		Invocable se = invocables.get(scriptName.trim());
		if(se==null) throw new RuntimeException("No invocable named [" + scriptName + "] is registered");
		if(objectName != null) {
			Object compiledObject = ((ScriptEngine)se).get(objectName.trim());
			if(compiledObject==null) throw new RuntimeException("No compiled object named [" + compiledObject + "] found in script [" + scriptName + "]");
			return se.getInterface(compiledObject, clazz);
		}
		return se.getInterface(clazz);
	}
	
	/**
	 * Returns an implementation of an interface using functions compiled in the interpreter
	 * @param scriptName The name of the registered invocable script
	 * @param clazz The type of the interface to return an implementation of 
	 * @return the interface implementation
	 * @param <T> The assumed type of the returned object
	 */
	public <T> T getInterface(final String scriptName, Class<T> clazz) {		
		return getInterface(scriptName, null, clazz);
	}
	
	/**
	 * Invokes the named compiled script
	 * @param scriptName The name of the registered compiled script
	 * @param bindings An optional bindings map
	 * @param args An optional array of positional arguments
	 * @return the return value of the compiled script invocation
	 */
	public Object invoke(final String scriptName, final Map<String, Object> bindings, final Object...args) {
		if(scriptName==null || scriptName.trim().isEmpty()) throw new IllegalArgumentException("The passed script name was null or empty");
		CompiledScript cs = compiledScripts.get(scriptName.trim());
		if(cs==null) throw new RuntimeException("No compiled script named [" + scriptName + "] is registered");
		final SimpleBindings sb;
		if(bindings!=null) {
			sb = new SimpleBindings(bindings);
		} else {
			sb = new SimpleBindings();
		}
		if(args!=null && args.length > 0) {
			sb.put(ScriptEngine.ARGV, args);
		}
		try {
			return cs.eval(sb);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to invoke compiled script [" + scriptName + "]", ex);
		}
	}
	
	/**
	 * Invokes a method on a script compiled object
	 * @param scriptName The script name
	 * @param object The object name
	 * @param method The method name
	 * @param bindings The optional bindings (which will be passed as the first positional argument if present)
	 * @param args The remaining optional positional arguments
	 * @return the return value of the method
	 */
	public Object invokeMethod(final String scriptName, final String object, final String method, final Map<String, Object> bindings, final Object...args) {
		if(scriptName==null || scriptName.trim().isEmpty()) throw new IllegalArgumentException("The passed script name was null or empty");
		if(object==null || object.trim().isEmpty()) throw new IllegalArgumentException("The passed object name was null or empty");
		if(method==null || method.trim().isEmpty()) throw new IllegalArgumentException("The passed method name was null or empty");
		try {
			final Invocable se = invocables.get(scriptName);
			final Object clz = ((ScriptEngine)se).get(object);
			Object[] arguments = new Object[((bindings==null||bindings.isEmpty()) ? 0 : 1) + (args==null ? 0 : args.length)];
			if((bindings==null||bindings.isEmpty())) {
				arguments[0] = bindings;
			}
			System.arraycopy(args, 0, arguments, (bindings==null||bindings.isEmpty() ? 0 : 1), args.length);
			return se.invokeMethod(clz, method, arguments);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to invoke method [" + scriptName + "/" + object + "/" + method + "]", ex);
		}
	}
	
	/**
	 * Invokes a function in the script engine
	 * @param scriptName The script name
	 * @param function The function name
	 * @param bindings The optional bindings (which will be passed as the first positional argument if present)
	 * @param args The remaining optional positional arguments
	 * @return the return value of the method
	 */
	public Object invokeFunction(final String scriptName, final String function, final Map<String, Object> bindings, final Object...args) {
		if(scriptName==null || scriptName.trim().isEmpty()) throw new IllegalArgumentException("The passed script name was null or empty");
		if(function==null || function.trim().isEmpty()) throw new IllegalArgumentException("The passed function name was null or empty");
		try {
			final Invocable se = invocables.get(scriptName);
			Object[] arguments = new Object[((bindings==null||bindings.isEmpty()) ? 0 : 1) + (args==null ? 0 : args.length)];
			if((bindings==null||bindings.isEmpty())) {
				arguments[0] = bindings;
			}
			System.arraycopy(args, 0, arguments, (bindings==null||bindings.isEmpty() ? 0 : 1), args.length);
			return se.invokeFunction(function, arguments);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to invoke method [" + scriptName + "/" + function + "]", ex);
		}
	}
	
	
	/**
	 * Invokes the named compiled script
	 * @param scriptName The name of the registered compiled script
	 * @param args An optional array of positional arguments
	 * @return the return value of the compiled script invocation
	 */
	public Object invoke(final String scriptName, final Object...args) {
		return invoke(scriptName, null, args);
	}
	
	/**
	 * Evals the named script
	 * @param scriptName The name of the registered compiled script
	 * @param bindings An optional bindings map
	 * @param args An optional array of positional arguments
	 * @return the return value of the evaled script invocation
	 */
	public Object eval(final String scriptName, final Map<String, Object> bindings, final Object...args) {
		if(scriptName==null || scriptName.trim().isEmpty()) throw new IllegalArgumentException("The passed script name was null or empty");
		ScriptEngine se = scriptEngines.get(scriptName.trim());
		if(se==null) throw new RuntimeException("No script engine named [" + scriptName + "] is registered");
		String source = evalSourceCode.get(scriptName.trim());
		if(source==null) throw new RuntimeException("No source code for script named [" + scriptName + "] is registered");
		final SimpleBindings sb;
		if(bindings!=null) {
			sb = new SimpleBindings(bindings);
		} else {
			sb = new SimpleBindings();
		}
		sb.put(ScriptEngine.ARGV, args);
		try {
			return se.eval(source, sb);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to eval script [" + scriptName + "]", ex);
		}
	}

	/**
	 * Evals the named script
	 * @param scriptName The name of the registered compiled script
	 * @param args An optional array of positional arguments
	 * @return the return value of the evaled script invocation
	 */
	public Object eval(final String scriptName, final Object...args) {
		return eval(scriptName, null, args);
	}
	
	/**
	 * Loads an agent XML configuration node
	 * @param configNode A script config node
	 * <pre><script name="foo" src="URL" ext="js" classpath=""></pre>
	 * @return The number of scripts that were loaded
	 */
	public int load(final Node configNode) {
		int cnt = 0;
		if(configNode==null) return cnt;
		if("script".equals(configNode.getNodeName())) {
			try {
				installScript(configNode);
				cnt++;
			} catch (Exception ex) {
				log.warn("Failed to prepare script for node [{}]", XMLHelper.renderNode(configNode), ex);				
			}			
		} else if("scripts".equals(configNode.getNodeName())) {
			for(Node scriptNode: XMLHelper.getChildNodesByName(configNode, "script", false)) {
				try {
					installScript(scriptNode);
					cnt++;
				} catch (Exception ex) {
					log.warn("Failed to prepare script for node [{}]", XMLHelper.renderNode(scriptNode), ex);
					continue;
				}
			}						
		}
		return cnt;
	}
	
	/**
	 * Installs the script engine in the passed script node
	 * @param scriptNode The script node
	 */
	protected void installScript(final Node scriptNode) {
		final String name = XMLHelper.getAttributeByName(scriptNode, "name", "").trim();
		String srcUrl = XMLHelper.getAttributeByName(scriptNode, "src", "").trim();
		String extension = XMLHelper.getAttributeByName(scriptNode, "extension", "").trim();
		String classpath = XMLHelper.getAttributeByName(scriptNode, "classpath", "SYSTEM").trim();
		String inlineSource = XMLHelper.getNodeTextValue(scriptNode, "").trim();		
		String sourceCode = null;
		// ========= Does the source have a name ?
		if(name.isEmpty()) {
			log.warn("No name defined for scriptNode node [{}]", XMLHelper.renderNode(scriptNode));
			return;
		}
		// ========= Try to get the named script engine
		ScriptEngine engine = scriptEngines.get(name);
		if(engine==null) {
			
			
			// ========= Do we have a source definition ?
			if(srcUrl.isEmpty() && inlineSource.isEmpty()) {
				log.warn("No source defined via URL or inline for script node [{}]", XMLHelper.renderNode(scriptNode));
				return;
			}
			// ========= Get the source
			if(inlineSource.isEmpty()) {
				if(!URLHelper.isValidURL(srcUrl)) {
					log.warn("Invalid source URL in script node [{}]", XMLHelper.renderNode(scriptNode));
					return;
				}
				final URL sourceURL = URLHelper.toURL(srcUrl);
				// TODO: a mime/type might be in the response headers if URL is http.
				sourceCode = URLHelper.getTextFromURL(URLHelper.toURL(srcUrl), 2000, 2000);
				if(extension.isEmpty()) {
					extension = URLHelper.getExtension(sourceURL);
					if(extension!=null) {
						extension = extension.trim().toLowerCase();
					} else {
						extension = ""; // reset back to blank
					}
				}
			}		
			// ========= Make sure we have an extension
			if(extension.isEmpty()) {
				log.warn("No source code extension for script node [{}]", XMLHelper.renderNode(scriptNode));
				return;			
			}
			// ========= Try getting a ScriptEngineFactory for the extension
			ScriptEngineFactory sef = scriptEngineFactories.get(extension);
			if(sef==null) {
				synchronized(scriptEngineFactories) {
					sef = scriptEngineFactories.get(extension);
					if(sef==null) {
						// ========= Get the SEM for the defined classpath
						ScriptEngineManager sem = scriptEngineManagers.get(classpath);
						if(sem==null) {
							sem = new ScriptEngineManager(ClassLoaderRepository.getInstance().getClassLoader(classpath));
							ScriptEngineManager existing = scriptEngineManagers.putIfAbsent(classpath, sem);
							if(existing!=null) sem = existing;
							else {
								// ========= Install and index all found script engine factories
								installScriptEngineFactories(sem);
							}
						}
						sef = scriptEngineFactories.get(extension);					
					}
				}
			}
			if(sef==null) {
				log.warn("Failed to load ScriptEngineFactory for script node [{}]", XMLHelper.renderNode(scriptNode));
				return;						
			}
			// ========= Create the SE
			engine = sef.getScriptEngine();
			scriptEngines.put(name, engine);
		}
		// ========= Prepare the script
		
		int installs = 0;
		// ========= Compiled Function
		if(engine instanceof Compilable) {
			try {
				CompiledScript cs = ((Compilable)engine).compile(sourceCode);
				compiledScripts.put(name, cs);
				installs++;
			} catch (Exception x) {/* No Op */}
		}
		// ========= Invocable Functions
		if(engine instanceof Invocable) {
			invocables.put(name, (Invocable)engine);
			installs++;
		}
		if(installs==0) {
			evalSourceCode.put(name, sourceCode);			
		}
		// ========= Simple Eval
		try {
			engine.eval(sourceCode);
			installs++;
		} catch (Exception x) {/* No Op */}

		// evalSourceCode
		if(installs==0) log.warn("Could not install script node [{}]", XMLHelper.renderNode(scriptNode));
		else log.info("Installed script node [{}]", name);
	}
	
	
	/**
	 * Installs all the factories found by the passed script engine manager
	 * @param sem The script engine manager to install the factories for
	 */
	protected void installScriptEngineFactories(final ScriptEngineManager sem) {
		for(final ScriptEngineFactory sef : sem.getEngineFactories()) {
			int keys = 0;
			for(String shortName: sef.getNames()) {
				if(scriptEngineFactories.putIfAbsent(shortName, sef)==null) keys++;
			}
			for(String ext: sef.getExtensions()) {
				if(scriptEngineFactories.putIfAbsent(ext, sef)==null) keys++;
			}
			for(String mimeType: sef.getMimeTypes()) {
				if(scriptEngineFactories.putIfAbsent(mimeType, sef)==null) keys++;
			}	
			log.info("Installed [{}] keys for ScriptEngineFactory: [{}/v.{}]", keys, sef.getEngineName(), sef.getEngineVersion());
		}
	}
	
	
}
