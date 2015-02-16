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
package com.heliosapm.opentsdb.client.aop;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: InvocationOption</p>
 * <p>Description: Functional enum for shorthand method invocation options</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.InvocationOption</code></p>
 */
public enum InvocationOption  {
	/** Allows the method injected instrumentation to be invoked reentrantly instead of being disabled if the cflow > 1 */
	ALLOW_REENTRANT("a"),
	/** Disables all instrumentation on the current thread when the instrumented method is invoked */
	DISABLE_ON_TRIGGER("d"),
	/** Creates but does not install the instrumentation */
	START_DISABLED("s"),
	/** The transformation process finds all the visible joinpoints and transforms them.
	 * If {@link #TRANSFORMER_RESIDENT} is not enabled, the transformer will be removed once the batch transform is complete. */
	TRANSFORMER_BATCH("b"),
	/** The transformer stays resident, transforming matching classes as they are initially classloaded */
	TRANSFORMER_RESIDENT("r");
	
	
	
	/** A map of the method attribute enums keyed by the lower name and aliases */
	public static final Map<String, InvocationOption> ALIAS2ENUM;
	
	static {
		InvocationOption[] values = InvocationOption.values();
		Map<String, InvocationOption> tmp = new HashMap<String, InvocationOption>(values.length*2);
		for(InvocationOption ma: values) {
			tmp.put(ma.name().toLowerCase(), ma);
			for(String name: ma.aliases) {
				if(tmp.put(name, ma)!=null) {
					throw new RuntimeException("Duplicate alias [" + name + "]. Programmer error");
				}
			}
		}
		ALIAS2ENUM = Collections.unmodifiableMap(tmp);
	}
	
	
	/**
	 * Indicates if the passed option string indicates that batch transformation should occur. (See {@link #TRANSFORMER_BATCH})
	 * @param opts The option string from the shorthand script
	 * @return true if enabled, false otherwise
	 */
	public static boolean isBatchTransform(String opts) {
		if(opts==null || opts.trim().isEmpty()) return false;
		for(char c: opts.toCharArray()) {
			String opt = new String(new char[]{c});
			if(TRANSFORMER_BATCH.aliases.contains(opt)) return true;
		}
		return false;
	}
	
	/**
	 * Indicates if the passed option string indicates that the class file transformer should stay
	 * resident and continue to transform new classes as they are loaded. (See {@link #TRANSFORMER_RESIDENT})
	 * @param opts The option string from the shorthand script
	 * @return true if enabled, false otherwise
	 */
	public static boolean isResidentTransformer(String opts) {
		if(opts==null || opts.trim().isEmpty()) return false;
		for(char c: opts.toCharArray()) {
			String opt = new String(new char[]{c});
			if(TRANSFORMER_RESIDENT.aliases.contains(opt)) return true;
		}
		return false;
	}
	
	
	
	/**
	 * Indicates if the passed option string indicates that a method should allow reentrant enabled instrumentation
	 * @param opts The option string from the shorthand script
	 * @return true if enabled, false otherwise
	 */
	public static boolean isAllowReentrant(String opts) {
		if(opts==null || opts.trim().isEmpty()) return false;
		for(char c: opts.toCharArray()) {
			String opt = new String(new char[]{c});
			if(ALLOW_REENTRANT.aliases.contains(opt)) return true;
		}
		return false;
	}

	/**
	 * Indicates if the passed option string indicates that a method should disable all instrumentation on the current thread until the method exits
	 * @param opts The option string from the shorthand script
	 * @return true if enabled, false otherwise
	 */
	public static boolean isDisableOnTrigger(String opts) {
		if(opts==null || opts.trim().isEmpty()) return false;
		for(char c: opts.toCharArray()) {
			String opt = new String(new char[]{c});
			if(DISABLE_ON_TRIGGER.aliases.contains(opt)) return true;
		}
		return false;
	}
	
	/**
	 * Indicates if the passed option string indicates that a method's instrumentation should start in a disabled state
	 * @param opts The option string from the shorthand script
	 * @return true if enabled, false otherwise
	 */
	public static boolean isStartDisabled(String opts) {
		if(opts==null || opts.trim().isEmpty()) return false;
		for(char c: opts.toCharArray()) {
			String opt = new String(new char[]{c});
			if(START_DISABLED.aliases.contains(opt)) return true;
		}
		return false;
	}
	

	
	
	private InvocationOption(String...aliases) {
		this.mask = Util.pow2Index(ordinal());
		if(aliases==null || aliases.length==0) {
			this.aliases = Collections.unmodifiableSet(new HashSet<String>(0));
		} else {
			Set<String> s = new HashSet<String>(aliases.length);
			for(String alias: aliases) {
				if(alias==null || alias.trim().isEmpty()) continue;
				s.add(alias.trim().toLowerCase());
			}
			this.aliases = Collections.unmodifiableSet(s);
		}		
	}
	
	/** A set of aliases for this option */
	public final Set<String> aliases;
	
	/** This members mask */
	public final int mask;

	/**
	 * Returns a set containing all of the InvocationOptions represented by the passed names, ignoring invalid options.
	 * @param names The names to build the set with
	 * @return the built set
	 */
	public static Set<InvocationOption> getEnabled(CharSequence...names) {
		return getEnabled(true, names);
	}
	
	
	/**
	 * Returns a set containing all of the InvocationOptions represented by the passed names
	 * @param ignoreInvalids if true, invalid or null names will be ignored
	 * @param names The names to build the set with
	 * @return the built set
	 */
	public static Set<InvocationOption> getEnabled(boolean ignoreInvalids, CharSequence...names) {
		Set<InvocationOption> set = EnumSet.noneOf(InvocationOption.class);
		if(names==null || names.length==0) return set; 
		for(CharSequence cs: names) {
			if(names==null || names.toString().isEmpty()) return set;
			char[] chars = cs.toString().replace(" ", "").toCharArray();
			for(char c: chars) {
				String name = new String(new char[]{c});
				InvocationOption ma = forNameOrNull(name);
				if(ma==null) {
					if(!ignoreInvalids) throw new IllegalArgumentException("The option [" + name + "] is invalid");
					continue;
				}
				set.add(ma);				
			}
		}
		return set;
	}
	
	/**
	 * Indicates if the passed name is a valid InvocationOption
	 * @param name the name to test
	 * @return true if the passed name is a valid InvocationOption, false otherwise
	 */
	public static boolean isInvocationOption(CharSequence name) {
		if(name==null) return false;
		try {
			InvocationOption.valueOf(name.toString().trim().toUpperCase());
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

		

	
	/**
	 * Returns the InvocationOption for the passed name
	 * @param name The name to get the attribute for 
	 * @return the decoded InvocationOption 
	 */
	public static InvocationOption forName(CharSequence name) {
		if(name==null) throw new IllegalArgumentException("The passed name was null");
		try {
			return InvocationOption.valueOf(name.toString().trim().toUpperCase());
		} catch (Exception ex) {
			throw new IllegalArgumentException("The passed name [" + name + "] is not a valid InvocationOption");
		}		
	}
	
	/**
	 * Returns the InvocationOption for the passed name or null if not a valid name
	 * @param name The name to get the attribute for 
	 * @return the decoded InvocationOption or null if not a valid name
	 */
	public static InvocationOption forNameOrNull(CharSequence name) {
		try {
			return forName(name);
		} catch (Exception ex) {
			return null;
		}		
	}



}
