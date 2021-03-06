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

import java.lang.reflect.Member;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;



/**
 * <p>Title: MethodAttribute</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.MethodAttribute</code></p>
 */
public enum MethodAttribute  {
	/** The public modifier */
	PUBLIC(1, "public", true, "pub"),
	/** The private modifier */
	PRIVATE(2, "private", true, "pri"),
	/** The protected modifier */
	PROTECTED(4, "protected", true, "pro"),
	/** The static modifier */
	STATIC(8, "static", true, "st"),
	/** The final modifier */
	FINAL(16, "final", true),
	/** The synchronized modifier */
	SYNCHRONIZED(32, "synchronized", true),
	/** The volatile modifier */
	VOLATILE(64, "volatile", false),
	/** The transient modifier */
	TRANSIENT(128, "transient", false),
	/** The native modifier */
	NATIVE(256, "native", true),
	/** The interface modifier */
	INTERFACE(512, "interface", false),
	/** The abstract modifier */
	ABSTRACT(1024, "abstract", true),
	/** The strict modifier */
	STRICT(2048, "strict", false);
	
	/** A map of the method attribute enums keyed by the lower name and aliases */
	public static final Map<String, MethodAttribute> NAME2ENUM;
	/** Maps the member bitmask to the member */
	public static final Map<Integer, MethodAttribute> MASK2ENUM;
	
	
	/** The default method attribute mask */
	public static final int DEFAULT_MASK;
	
	
	static {
		MethodAttribute[] values = MethodAttribute.values();
		Map<String, MethodAttribute> tmp = new HashMap<String, MethodAttribute>(values.length);
		Map<Integer, MethodAttribute> itmp = new HashMap<Integer, MethodAttribute>(values.length);
		for(MethodAttribute ma: values) {
			itmp.put(ma.mask, ma);
			if(tmp.put(ma.name, ma)!=null) {
				throw new RuntimeException("Duplicate lower name [" + ma.name + "]. Programmer error");
			}
			for(String name: ma.aliases) {
				if(tmp.put(name, ma)!=null) {
					throw new RuntimeException("Duplicate alias [" + name + "]. Programmer error");
				}
			}
		}
		NAME2ENUM = Collections.unmodifiableMap(tmp);
		MASK2ENUM = Collections.unmodifiableMap(itmp);	
		DEFAULT_MASK = enableFor(PUBLIC);
	}
	
	/** An empty MethodAttribute array const */
	public static final MethodAttribute[] EMPTY_INVOPT_ARR = {};
	/** A comma splitter pattern */
	public static final Pattern COMMA_SPLITTER = Pattern.compile(",");
	
	
	
	private MethodAttribute(int mask, String name, boolean forMethod, String...aliases) {
		this.mask = mask;
		this.name = name;
		this.forMethod = forMethod;
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
	
	/** The bitmask of this modifier */
	public final int mask;
	/** The name of this modifier */
	public final String name;
	/** Indicates if this mask is valid for methods */
	public final boolean forMethod;
	/** A set of aliases for this attribute */
	public final Set<String> aliases;
	
	
	/** The anti mask */
	private static final int PACKAGE_ANTI_MASK = enableFor(EnumSet.complementOf(EnumSet.of(PUBLIC, PROTECTED, PRIVATE)).toArray(new MethodAttribute[0]));
	
	/**
	 * Determines if the passed bitMask is enabled for this attribute
	 * @param bitMask the bitMask to test
	 * @return true if the passed bitMask is enabled for this attribute, false otherwise
	 */
	public boolean isEnabled(int bitMask) {
		return (bitMask & mask)==mask;
	}
	
	/**
	 * Determines if the passed bitmask represents a package protected (default) access modifier
	 * @param mask The bitmask to test
	 * @return true if the passed bitmask represents a package protected (default) access modifier, false otherwise
	 */
	public static boolean isPackageProtected(int mask) {
		return (mask &= ~PACKAGE_ANTI_MASK) == mask;
	}
	
	/**
	 * Returns a bitmask representing the enabled state of the MethodAttributes represented by the passed names, ignoring any invalid names
	 * @param names The names to build the mask with
	 * @return the built mask
	 */
	public static int enableFor(CharSequence...names) {
		return enableFor(true, names);
	}
	
	
	/**
	 * Returns a bitmask representing the enabled state of the MethodAttributes represented by the passed names
	 * @param ignoreInvalids if true, invalid or null names will be ignored
	 * @param names The names to build the mask with
	 * @return the built mask
	 */
	public static int enableFor(boolean ignoreInvalids, CharSequence...names) {
		if(names==null || names.length==0) return 0;
		int mask = 0;
		for(CharSequence cs: names) {
			MethodAttribute ma = forNameOrNull(cs);
			if(ma==null) {
				if(!ignoreInvalids) throw new IllegalArgumentException("The attribute name [" + cs + "] is invalid");
				continue;
			}
			mask = ma.enable(mask);
		}
		return mask;
	}
	
	/**
	 * Indicates if the passed name is a valid MethodAttribute
	 * @param name the name to test
	 * @return true if the passed name is a valid MethodAttribute, false otherwise
	 */
	public static boolean isMethodAttribute(CharSequence name) {
		if(name==null) return false;
		try {
			MethodAttribute.valueOf(name.toString().trim().toUpperCase());
			return true;
		} catch (Exception ex) {
			return false;
		}
	}
	
	/**
	 * Returns the MethodAttribute for the passed name
	 * @param name The name to get the attribute for 
	 * @return the decoded MethodAttribute 
	 */
	public static MethodAttribute forName(CharSequence name) {
		if(name==null) throw new IllegalArgumentException("The passed name was null");
		try {
			return MethodAttribute.valueOf(name.toString().trim().toUpperCase());
		} catch (Exception ex) {
			throw new IllegalArgumentException("The passed name [" + name + "] is not a valud MethodAttribute");
		}		
	}
	
	/**
	 * Returns the MethodAttribute for the passed name or null if not a valid name
	 * @param name The name to get the attribute for 
	 * @return the decoded MethodAttribute or null if not a valid name
	 */
	public static MethodAttribute forNameOrNull(CharSequence name) {
		try {
			return forName(name);
		} catch (Exception ex) {
			return null;
		}		
	}
	
	
	/**
	 * Determines if the passed name represents a for method MethodAttribute
	 * @param name the name to test
	 * @return true if the passed name represents a for method MethodAttribute, false otherwise
	 */
	public static boolean isForMethodAttribute(CharSequence name) {
		MethodAttribute ma = forNameOrNull(name);
		return ma!=null;
	}
	
	/**
	 * Returns an array of the MethodAttributes enabled by the passed mask 
	 * @param mask The mask
	 * @return an array of MethodAttributes
	 */
	public static MethodAttribute[] getEnabled(final int mask) {
		final EnumSet<MethodAttribute> set = EnumSet.noneOf(MethodAttribute.class);
		for(MethodAttribute ot: values()) {
			if((mask & ot.mask) != 0) set.add(ot);
		}
		return set.toArray(new MethodAttribute[set.size()]);
	}	
	
	/**
	 * Decodes the passed expression into an array of MethodAttributes.
	 * Is expected to be a comma separated list of values where each value 
	 * can be either the name of a MethodAttribute member or a MethodAttribute mask.
	 * Normally there would only be one mask, but multiple are supported. 
	 * The expression can contain both representations. 
	 * @param throwIfInvalid If true, will throw an exception if a value cannot be decoded
	 * @param expr The expression to decode
	 * @return an array of represented MethodAttribute members.
	 */

	public static MethodAttribute[] decode(final boolean throwIfInvalid, final CharSequence expr) {
		if(expr==null) return EMPTY_INVOPT_ARR;
		final String sexpr = expr.toString().trim().toUpperCase();
		if(sexpr.isEmpty()) return EMPTY_INVOPT_ARR;
		final EnumSet<MethodAttribute> set = EnumSet.noneOf(MethodAttribute.class);
		final String[] exprFields = COMMA_SPLITTER.split(sexpr);
		for(String s: exprFields) {
			if(isMethodAttributeName(s)) {
				set.add(valueOf(s.trim()));
				continue;
			}
			try {
				int mask = new Double(s).intValue();
				MethodAttribute sm = MASK2ENUM.get(mask);
				if(sm!=null) {
					set.add(sm);
					continue;
				} 
				if(!throwIfInvalid) continue;				
			} catch (Exception ex) {
				if(!throwIfInvalid) continue;
			}
			throw new RuntimeException("Failed to decode MethodAttribute value [" + s + "]");
		}
		return set.toArray(new MethodAttribute[set.size()]);
	}
	
	/**
	 * Decodes the passed expression into an array of MethodAttributes, ignoring any non-decoded values.
	 * Is expected to be a comma separated list of values where each value 
	 * can be either the name of a MethodAttribute member or a MethodAttribute mask (individual or bitmasked).
	 * Normally there would only be one mask, but multiple are supported. 
	 * The expression can contain both representations. 
	 * @param expr The expression to decode
	 * @return an array of represented MethodAttribute members.
	 */
	public static MethodAttribute[] decode(final CharSequence expr) {
		return decode(false, expr);
	}
	
	/**
	 * Decodes the passed expression into a bitmask representing enabled MethodAttribute, ignoring any non-decoded values.
	 * Is expected to be a comma separated list of values where each value 
	 * can be either the name of a MethodAttribute member or a MethodAttribute mask.
	 * Normally there would only be one mask, but multiple are supported. 
	 * The expression can contain both representations. 
	 * @param expr The expression to decode
	 * @return the bit mask of the enabled MethodAttribute members
	 */
	public static int decodeToMask(final CharSequence expr) {
		return enableFor(decode(expr));
	}
	
	/**
	 * Decodes the passed expression into a bitmask representing enabled MethodAttributes
	 * Is expected to be a comma separated list of values where each value 
	 * can be either the name of a MethodAttribute member or a MethodAttribute mask.
	 * Normally there would only be one mask, but multiple are supported. 
	 * The expression can contain both representations. 
	 * @param throwIfInvalid If true, will throw an exception if a value cannot be decoded
	 * @param expr The expression to decode
	 * @return the bit mask of the enabled MethodAttribute members
	 */
	public static int decodeToMask(final boolean throwIfInvalid, final CharSequence expr) {
		return enableFor(decode(throwIfInvalid, expr));
	}
	
	
	
	/**
	 * Indicates if the passed symbol is a valid MethodAttribute member name.
	 * The passed value is trimmed and upper-cased.
	 * @param symbol The symbol to test
	 * @return true if the passed symbol is a valid MethodAttribute member name, false otherwise
	 */
	public static boolean isMethodAttributeName(final CharSequence symbol) {
		if(symbol==null) return false;
		final String name = symbol.toString().trim().toLowerCase();
		if(name.isEmpty()) return false;
		try {
			valueOf(name);
			return true;
		} catch (Exception ex) {
			return false;
		}
	}
	
	
	
	/**
	 * Returns an int as the passed bitMask enabled for this attribute
	 * @param bitMask the bitmask to enable against
	 * @return the passed bitMask enabled for this attribute
	 */
	public int enable(int bitMask) {
		return (bitMask | mask);
	}
	
	/**
	 * Returns an int as the passed bitMask disabled for this attribute
	 * @param bitMask the bitmask to disabled against
	 * @return the passed bitMask disabled for this attribute
	 */
	public int disable(int bitMask) {
		return (bitMask &= ~mask);
	}
	
	
	/**
	 * Determines if the modifier mask of the passed member matches the passed mask
	 * @param mask The mask to compare against
	 * @param member The member to test
	 * @return true if the modifier mask of the passed member matches the passed mask, false otherwise
	 */
	public static boolean match(int mask, Member member) {
		return (mask & member.getModifiers()) == mask;
	}
	
	/**
	 * Returns a bitmask enabled for the passed attributes
	 * @param attributes The attributes to enable
	 * @return the resulting bitmask enabled for the passed attributes
	 */
	public static int enableFor(MethodAttribute...attributes) {
		if(attributes==null || attributes.length==0) return 0;
		int mask = 0;
		for(MethodAttribute attr: attributes) {
			mask = attr.enable(mask);
		}
		return mask;
	}
	
	/**
	 * Determines if the passed mask is enabled for all the passed attributes
	 * @param mask The mask to test
	 * @param attributes The attributes which must all be enabled
	 * @return true if the passed mask is enabled for all the passed attributes, false otherwise
	 */
	public static boolean isEnabledForAll(final int mask, MethodAttribute...attributes) {
		if(attributes==null || attributes.length==0 || mask<1) return false;		
		for(MethodAttribute attr: attributes) {
			if(!attr.isEnabled(mask)) return false;
		}
		return true;
	}
	
	/**
	 * Determines if the passed mask is enabled for any of the passed attributes
	 * @param mask The mask to test
	 * @param attributes The attributes of which at least one must be enabled
	 * @return true if the passed mask is enabled for any of the passed attributes, false otherwise
	 */
	public static boolean isEnabledForAny(final int mask, MethodAttribute...attributes) {
		if(attributes==null || attributes.length==0 || mask<1) return false;		
		for(MethodAttribute attr: attributes) {
			if(attr.isEnabled(mask)) return true;
		}
		return false;
	}
	
	
	
	/**
	 * Returns a bitmask disabled for the passed attributes
	 * @param attributes The attributes to disabled
	 * @return the resulting bitmask disabled for the passed attributes
	 */
	public static int disableFor(MethodAttribute...attributes) {
		if(attributes==null || attributes.length==0) return 0;
		int mask = 0;
		for(MethodAttribute attr: attributes) {
			mask = attr.disable(mask);
		}
		return mask;
	}
	
	
	/**
	 * Returns an array of MethodAttributes for which the passed mask is enabled
	 * @param mask The mask to filter with
	 * @return an array of MethodAttributes
	 */
	public static MethodAttribute[] getAttributes(int mask) {
		Set<MethodAttribute> matches = EnumSet.noneOf(MethodAttribute.class);
		for(MethodAttribute ma: values()){
			if(ma.isEnabled(mask)) matches.add(ma);
		}
		return matches.toArray(new MethodAttribute[matches.size()]);
	}

	
}
