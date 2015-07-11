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
package com.heliosapm.opentsdb.client.jvmjmx.customx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.heliosapm.opentsdb.client.scripts.ScriptManager;

/**
 * <p>Title: TokenResolvers</p>
 * <p>Description: A collection of token resolver classes</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers</code></p>
 */

public abstract class TokenResolvers {
	
	/** The token expression matcher */
	public static final Pattern TOKEN_EXPR_PATTERN = Pattern.compile("\\$(.+?)\\{(.*)\\}");
	/** The index expression matcher */
	public static final Pattern INDEX_EXPR_PATTERN = Pattern.compile("\\$\\{(\\d+)?\\}");
	/** The directive splitter */
	public static final Pattern DIRECTIVE_SPLITTER = Pattern.compile(":");
	/** The raw code resolver args pattern */
	public static final Pattern CODE_SPLITTER = Pattern.compile("([b|a])\\[(.*?)\\]", Pattern.CASE_INSENSITIVE);
	
	/** The prefix indicating the rest of the args is a script name from the script manager */
	public static final String SCRIPT_ACTION = "script";
	/** The prefix indicating the rest of the args is a prefix and/or suffix of raw code to be compiled */
	public static final String CODE_ACTION = "raw";
	
	/** The script manager class name */
	public static final String SCRIPT_MGR_NAME = ScriptManager.class.getName() + ".getInstance()";
	
	/** The script binding name for the focused value being submitted */
	public static final String FOCUSED_VALUE_BINDING_KEY = "_focVal";
	/** The script binding name for the {@link CollectionContext} being submitted */
	public static final String COLLECTION_CONTEXT_BINDING_KEY = "_ctx";
	
	
	/*
	 * Each resolver needs to provide a javassist body for
	 * public String resolve(final String args);
	 */
	
	/**
	 * <p>Title: AbstractTokenResolver</p>
	 * <p>Description: Support base for implementing token resolvers</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.AbstractTokenResolver</code></p>
	 */
	public static abstract class AbstractTokenResolver implements TokenResolver {
		
		/**
		 * Returns the javassist source code representing a script call.
		 * The resolver args expression format is: <b><code>script:&lt;script-name&gt;[:&lt;comma separated args&gt;]</code></b>
		 * @param resolverArgs The resolver arguments which are assumed to start with {@link TokenResolvers#SCRIPT_ACTION}
		 * @param sourceName The CollectionContext focus code fragment  (e.g. <b><code>.value()</code></b> for the current value)
		 * @return The source code implementing the expression
		 */
		public static String resolveScriptCallout(final String resolverArgs, final String sourceName) {
			final String[] frags = DIRECTIVE_SPLITTER.split(resolverArgs.trim(), 3);
			if(frags.length<2 || !SCRIPT_ACTION.equals(frags[0].toLowerCase().trim())) throw new RuntimeException("Invalid directive expression [" + resolverArgs + "]");
			final String scriptName = frags[1].trim();
			final String scriptArgs;
			if(frags.length>2) {
				scriptArgs = ", " + frags[3];
			} else {
				scriptArgs = "";
			}
			final StringBuilder b = new StringBuilder("final java.util.Map<String, Object> _bindings = new java.util.Map<String, Object>(2);\n");
			b.append("_bindings.put(\"").append(FOCUSED_VALUE_BINDING_KEY).append("'}, $1").append(sourceName).append(");\n");
			b.append("_bindings.put(\"").append(COLLECTION_CONTEXT_BINDING_KEY).append("'}, $1);\n");
			b.append(SCRIPT_MGR_NAME).append("return eval(\"").append(scriptName).append("\", _bindings").append(scriptArgs).append(");");
			return b.toString();
		}
		
		/**
		 * Returns the javassist source code representing some custom code prefixed/suffixed around the source value
		 * The resolver args expression format is: <b><code>code:[&lt;b[code]&gt;][&lt;a[code]&gt;]</code></b>. At least one of 
		 * <b>b</b> (before) or <b>a</b> (after) should be specified, but if not, will simply return the source value.  
		 * @param resolverArgs The resolver arguments which are assumed to start with {@link TokenResolvers#CODE_ACTION}
		 * @param sourceName The CollectionContext focus code fragment  (e.g. <b><code>.value()</code></b> for the current value)
		 * @return The source code implementing the expression
		 */
		public static String resolveRawCode(final String resolverArgs, final String sourceName) {
			final Matcher m = CODE_SPLITTER.matcher(resolverArgs);
			final StringBuilder b = new StringBuilder("$").append(sourceName);
			while(m.find()) {
				final String type = m.group(1).toLowerCase();
				final String source = m.group(2);
				if("b".equals(type)) b.insert(0, source);
				else b.append(source);
			}
			return b.insert(0, "try { return ").append("} catch (Throwable t) { throw new RuntimeException(t); }").toString();
		}
		
		/**
		 * Generates the javassist code to build a string from a compound name.
		 * @param expression The expression to parse
		 * @param sourceName The expression that represents the code for the source string being parsed at runtime 
		 * (like a literal or a parameterless method name call that returns a string)  
		 * @param delimeter The delimeter to parse the source
		 * @return The source code implementing the expression
		 */
		public static String resolvedIndexed(final String expression, final String sourceName, final String delimeter) {
			if(expression==null || expression.trim().isEmpty()) return "return \"\";";
			final StringBuilder b = new StringBuilder("\nfinal String[] __indexSource = ").append(sourceName).append(".split(\"").append(delimeter).append("\");\nreturn \"\"");
			String[] tmp = INDEX_EXPR_PATTERN.split(expression);
			final ArrayList<String> literals = new ArrayList<String>();
			final boolean startsWithToken;
			final int litCount;			
			if(tmp.length>0 && tmp[0].isEmpty()) {
				startsWithToken = true;
				litCount = tmp.length-1;				
			} else {
				startsWithToken = false;
				litCount = tmp.length;
			}
			for(int i = (startsWithToken ? 1 : 0); i < litCount; i++) {
				literals.add(tmp[i]);
			}
			final ArrayList<Integer> indexes = new ArrayList<Integer>(litCount);
			final Matcher m = INDEX_EXPR_PATTERN.matcher(expression);			
			while(m.find()) {
				indexes.add(Integer.parseInt(m.group(1)));
			}
			final Iterator<Integer> tokenIter = indexes.iterator();
			final Iterator<String> literalIter = literals.iterator();
			if(startsWithToken) {
				// ===================================================================================
				//   TODO:  replace the string concats with a string builder
				// ===================================================================================
				while(tokenIter.hasNext()) {
					b.append(" + __indexSource[").append(tokenIter.next()).append("]");
					if(literalIter.hasNext()) {
						b.append(" + \"").append(literalIter.next()).append("\"");
					}
				}
				while(literalIter.hasNext()) {
					b.append(" + \"").append(literalIter.next()).append("\"");
				}
			} else {
				while(literalIter.hasNext()) {
					b.append(" + \"").append(literalIter.next()).append("\"");
					if(tokenIter.hasNext()) {
						b.append(" + __indexSource[").append(tokenIter.next()).append("]");
					}
				}
				while(tokenIter.hasNext()) {
					b.append(" + __indexSource[").append(tokenIter.next()).append("]");
				}								
			}			
			return b.append(";").toString();			
		}
	}

	/**
	 * <p>Title: AttributeNameTokenResolver</p>
	 * <p>Description: At it's most basic, this resolver returns the attribute name of the attribute in focus.
	 * However, in the case of composite data types, the attribute name in focus will be a compound name in the form
	 * of sub-names delimited by a "/". e.g. a memory pool's committed usage would be <b><code>Usage/committed</code></b>.
	 * Therefore, the resolver will accept arguments that contain index tokens that will be resolved against the 
	 * compound name as a String array.</p> 
	 * <p>In the example above, the attribute name array would be:
	 * <b><code>{Usage, committed}</code></b> where an index token of <b><code>${0}</code></b> would represent <b>"Usage"</b>
	 * and an index token of <b><code>${1}</code></b> would represent <b>"committed"</b>.</p> 
	 * <p>An expression <b><code>${1}Memory${0}</code></b> would evaluate to <b>"committedMemoryUsage"</b>.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.AttributeNameTokenResolver</code></p>
	 */
	public static class AttributeNameTokenResolver extends AbstractTokenResolver {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver#resolve(java.lang.String)
		 */
		@Override
		public String resolve(final String args) {
			if(args==null || args.trim().isEmpty()) {
				return "return $1.name();";
			}
			return resolvedIndexed(args, "$1.name()", "/");
		}
	}
	
	/**
	 * <p>Title: ObjectNameDomainTokenResolver</p>
	 * <p>Description: Resolves to the current ObjectName's domain name or a subscript of it.
	 * See {@link AttributeNameTokenResolver} for a more in depth outline of the subscripting.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.ObjectNameDomainTokenResolver</code></p>
	 */
	public static class ObjectNameDomainTokenResolver extends AbstractTokenResolver {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver#resolve(java.lang.String)
		 */
		@Override
		public String resolve(final String args) {
			if(args==null || args.trim().isEmpty()) {
				return "return $1.objectName().getDomain();";
			}
			return resolvedIndexed(args, "$1.objectName().getDomain()", "\\.");
		}		
	}

	
	/**
	 * <p>Title: AttributeValueTokenResolver</p>
	 * <p>Description: Resolves to the current attribute value or the evaluation of a directive against it.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.AttributeValueTokenResolver</code></p>
	 */
	public static class AttributeValueTokenResolver extends AbstractTokenResolver {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver#resolve(java.lang.String)
		 */
		@Override
		public String resolve(final String args) {			
			if(args==null || args.trim().isEmpty()) {
				return "return $1.value();";
			}
			final String cmd = args.trim();
			final int cindex = cmd.indexOf(':');
			if(cindex==-1) throw new RuntimeException("Invalid [" + getClass().getSimpleName() + "] resolver args: [" + args + "]");
			final String directive = cmd.substring(0, cindex).toLowerCase();
			if(SCRIPT_ACTION.equals(directive)) {
				return resolveScriptCallout(args, ".value()");
			} else if(CODE_ACTION.equals(directive)) {
				return resolveRawCode(args, ".value()");
			} else {
				throw new RuntimeException("Invalid [" + getClass().getSimpleName() + "] resolver args: [" + args + "]");
			}
		}
	}
	
	/**
	 * <p>Title: MBeanOperationTokenResolver</p>
	 * <p>Description: Resolves an MBean operation invocation against the current ObjectName and MBeanServer.
	 * The optional resolver arguments are treated as raw code which is passed as invocation arguments</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.MBeanOperationTokenResolver</code></p>
	 */
	public static class MBeanOperationTokenResolver extends AbstractTokenResolver {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver#resolve(java.lang.String)
		 */
		@Override
		public String resolve(final String args) {			
			throw new UnsupportedOperationException("MBeanOperationTokenResolver Not Implemented Yet");
		}
	}
	
	/**
	 * <p>Title: ObjectNameKeyPropertyTokenResolver</p>
	 * <p>Description: Resolves to the property value of the current ObjectName where the resolver argument is the key</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.ObjectNameKeyPropertyTokenResolver</code></p>
	 */
	public static class ObjectNameKeyPropertyTokenResolver extends AbstractTokenResolver {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver#resolve(java.lang.String)
		 */
		@Override
		public String resolve(final String args) {
			if(args==null || args.trim().isEmpty()) throw new IllegalArgumentException("The passed ObjectName property key was null or empty");
			final String key = args.trim();
			return "final String v = $1.objectName().getKeyProperty(\"" + key + "\");\nif(v==null return \"\"; else return v;";
		}
		
	}
	
	/**
	 * <p>Title: ObjectNamePropertyExpansionTokenResolver</p>
	 * <p>Description: Resolves to the full key/value pairs of the current ObjectName where the resolver argument is the key</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.ObjectNamePropertyExpansionTokenResolver</code></p>
	 */
	public static class ObjectNamePropertyExpansionTokenResolver extends AbstractTokenResolver {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver#resolve(java.lang.String)
		 */
		@Override
		public String resolve(final String args) {
			return "return $1.objectName().getKeyPropertyListString()";
		}		
	}
	
	/**
	 * <p>Title: ScriptInvocationTokenResolver</p>
	 * <p>Description: Passes the CollectionContext to a named script.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.ScriptInvocationTokenResolver</code></p>
	 */
	public static class ScriptInvocationTokenResolver extends AbstractTokenResolver {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver#resolve(java.lang.String)
		 */
		@Override
		public String resolve(final String args) {
			final String scriptName = args.trim();
			final StringBuilder b = new StringBuilder("final java.util.Map _bindings = java.util.Collections.singletonMap(\"").append(COLLECTION_CONTEXT_BINDING_KEY).append("\", \"").append(scriptName).append("\");\n");
			b.append("return ").append(SCRIPT_MGR_NAME).append(".eval(\"").append(scriptName).append("\", _bindings, (Object[])null);");
			return b.toString();
		}		
	}
	
	/**
	 * <p>Title: DescriptorValueTokenResolver</p>
	 * <p>Description: Returns the value of the named MBean descriptor</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.DescriptorValueTokenResolver</code></p>
	 */
	public static class DescriptorValueTokenResolver extends AbstractTokenResolver {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver#resolve(java.lang.String)
		 */
		@Override
		public String resolve(final String args) {
			return "return $1.metaData().get(com.heliosapm.opentsdb.client.jvmjmx.customx.MBeanFeature.DESCRIPTOR).get(\"" + args + "\");\n"; 
		}		
	}
	
	public static void main(String[] args) {
		log("TokenTest");
		TokenResolver resolver = new AttributeNameTokenResolver();
//		log(resolver.resolve(""));
		String s = resolver.resolve("Hello ${1} in galaxy ${2342} !");
		resolver = new ScriptInvocationTokenResolver();
		s = resolver.resolve("floo.js");
		log(s);
	}
	
	public static void log(Object msg) {
		System.out.println(msg);
	}

}
