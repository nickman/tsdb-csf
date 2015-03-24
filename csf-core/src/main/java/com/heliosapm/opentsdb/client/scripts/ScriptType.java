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

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Title: ScriptType</p>
 * <p>Description: Enumerates the invocable interfaces to a JSR 223 script engine</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.scripts.ScriptType</code></p>
 */

public enum ScriptType implements ScriptInvoker {
	/** A compiled script */
	COMPILED("comp", new CompiledScriptInvoker()),
	/** The retrieval of an interface implemented by an invocable script engine */
	INVOCABLE_IFACE("iface", new InterfaceScriptGetter()),
	/** The invocation of a function implemented in an invocable script engine */
	INVOCABLE_FUNCTION("func", new FunctionScriptInvoker()),
	/** The invocation of a method implemented in an invocable script engine */
	INVOCABLE_METHOD("meth", new MethodScriptInvoker()),
	/** Simple script engine eval */
	EVAL("eval", new EvalScriptInvoker());
	
	/** A map of the script types keyed by the short name */
	public static final Map<String, ScriptType> NAME2ENUM;
	
	static {		
		ScriptType[] values = values();
		final Map<String, ScriptType> byName = new HashMap<String, ScriptType>(values.length);
		for(ScriptType st: values) {
			byName.put(st.shortName, st);
		}
		NAME2ENUM = Collections.unmodifiableMap(byName);
	}
	
	private ScriptType(final String shortName, final ScriptInvoker invoker) {
		this.shortName = shortName;
		this.invoker = invoker;
	}
	
	/** The short name for the script type */
	public final String shortName;	
	/** The script invoker for this script type */
	public final ScriptInvoker invoker;
	/** Empty ScriptType arr const */
	private static final ScriptType[] EMPTY_ARR = {};
	
	/**
	 * Decodes the passed type to a ScriptType
	 * @param type The type name which can be the enum member name or the short name
	 * @return the decoded ScriptType
	 */
	public static ScriptType forName(final String type) {
		if(type==null || type.trim().isEmpty()) throw new IllegalArgumentException("The passed type [" + "] is not a valid ScriptType");
		ScriptType st = NAME2ENUM.get(type.trim().toLowerCase());
		if(st==null) {
			try {
				st = ScriptType.valueOf(type.trim().toUpperCase());
			} catch (Exception x) {/* No Op */}
		}
		if(st==null) {
			throw new IllegalArgumentException("The passed type [" + "] is not a valid ScriptType");
		}
		return st;
	}
	
	/**
	 * Splits and parses the passed comma separated string into an array of ScriptTypes.
	 * The comma separated values can be the short name or the enum name 
	 * @param types The string to split
	 * @return a [possibly empty] array of ScriptTypes
	 */
	public static ScriptType[] parse(final String types) {
		if(types==null || types.trim().isEmpty()) return EMPTY_ARR;
		EnumSet<ScriptType> set = EnumSet.noneOf(ScriptType.class);
		for(String s: types.split(",")) {
			s = s.trim();
			ScriptType st = NAME2ENUM.get(s.toLowerCase());
			if(st!=null) {
				set.add(st);
				continue;
			}
			try {
				set.add(ScriptType.valueOf(s.toUpperCase()));
			} catch (Exception x) {/* No Op */}
		}
		if(set.isEmpty()) return EMPTY_ARR;
		return set.toArray(new ScriptType[set.size()]);
	}
	
	/**
	 * Returns a map of (initially false) Booleans keyed by the ScriptType
	 * @return the map
	 */
	public static Map<ScriptType, Boolean> getEnabledMap() {
		final EnumMap<ScriptType, Boolean> map = new EnumMap<ScriptType, Boolean>(ScriptType.class);
		for(ScriptType st: values()) {
			map.put(st, false);
		}
		return map;
	}
	
	/**
	 * Returns an array of the enabled ScriptTypes in the passed map
	 * @param map The map
	 * @return a [possibly empty] array of ScriptTypes
	 */
	public static ScriptType[] getEnabled(final Map<ScriptType, Boolean> map) {
		if(map==null || map.isEmpty()) return EMPTY_ARR;
		EnumSet<ScriptType> set = EnumSet.noneOf(ScriptType.class);
		for(Map.Entry<ScriptType, Boolean> entry: map.entrySet()) {
			if(entry.getValue()) {
				set.add(entry.getKey());
			}
		}
		if(set.isEmpty()) return EMPTY_ARR;
		return set.toArray(new ScriptType[set.size()]);
		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.scripts.ScriptInvoker#invoke(java.lang.String, java.lang.String, java.lang.String, java.util.Map, java.lang.Object[])
	 */
	@Override
	public Object invoke(final String scriptName, final String object, final String functionName, final Map<String, Object> bindings, final Object... args) {
		return invoker.invoke(scriptName, object, functionName, bindings, args);
	}
}
