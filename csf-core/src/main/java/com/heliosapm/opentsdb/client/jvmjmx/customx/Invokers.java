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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.management.AttributeValueExp;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.StringValueExp;
import javax.management.ValueExp;

import org.w3c.dom.Node;

import com.heliosapm.utils.xml.XMLHelper;

/**
 * <p>Title: Invokers</p>
 * <p>Description: A bunch of invokers</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers</code></p>
 */

public abstract class Invokers {
	
	/**
	 * <p>Title: AbstractQueryDecodeInvoker</p>
	 * <p>Description: </p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker</code></p>
	 * @param <T> The return type of the invoker
	 */

	public static abstract class AbstractQueryDecodeInvoker<T> implements QueryDecodeInvoker<T> {

		/**
		 * Creates a new AbstractQueryDecodeInvoker
		 */
		AbstractQueryDecodeInvoker() {

		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public T invoke(final Node... xmlArgs) {
			throw new RuntimeException("QueryDecode type is terminal so invoke is not supported");		
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecodeInvoker#eval(java.util.Map)
		 */
		@Override
		public T eval(Map<String, String> attributes) {
			throw new RuntimeException("QueryDecode type is not terminal so eval from [" + this.getClass().getName() + "] with [" + attributes + "] is not supported");
		}
		
		/**
		 * Resolves a non terminal node
		 * @param xmlArgs The child nodes to resolve from
		 * @return the return value
		 */
		protected static Object resolve(final Node...xmlArgs) {
			if(xmlArgs==null || xmlArgs.length==0) return null;
			Object[] resolved = new Object[xmlArgs.length];
			for(int i = 0; i < xmlArgs.length; i++) {
				final Node node = xmlArgs[i];			
				final QueryDecode q = QueryDecode.forName(node.getNodeName());
				if(q.isTerminal()) {
					resolved[i] = q.eval(XMLHelper.getAttributeMap(node));
				} else {
					resolved[i] = q.invoke(toArray(node));
				}
			}
			if(resolved.length==1) return resolved[0];
			return resolved;
		}
		
		/**
		 * Returns the child nodes of the passed node as an array
		 * @param node The node to get the children for
		 * @return an array of child nodes
		 */
		public static Node[] toArray(final Node node) {
			final List<Node> list = XMLHelper.getElementChildNodes(node);			
			return list.toArray(new Node[list.size()]);
		}

	}
	
	
	/**
	 * <p>Title: ValueExpInvoker</p>
	 * <p>Description: A QueryDecodeInvoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#VALUE}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.ValueExpInvoker</code></p>
	 */

	public static class ValueExpInvoker extends AbstractQueryDecodeInvoker<ValueExp> {
		
		/** The value key */
		public static final String VALUE_KEY = "v";
		/** The type key */
		public static final String TYPE_KEY = "t";
		
		/**
		 * <p>Title: ValueParser</p>
		 * <p>Description: Defines a {@link ValueType}'s conversion to the native type for a string</p> 
		 * <p>Company: Helios Development Group LLC</p>
		 * @author Whitehead (nwhitehead AT heliosdev DOT org)
		 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.ValueExpInvoker.ValueParser</code></p>
		 */
		public static interface ValueParser {
			/**
			 * Parses the passed string to the native type
			 * @param value The string to parse
			 * @return the parsed result
			 */
			public ValueExp parse(String value);
		}
		
		/**
		 * <p>Title: ValueType</p>
		 * <p>Description: Enumerates the recognized types</p> 
		 * <p>Company: Helios Development Group LLC</p>
		 * @author Whitehead (nwhitehead AT heliosdev DOT org)
		 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.ValueExpInvoker.ValueType</code></p>
		 */
		public static enum ValueType implements ValueParser {
			/** Boolean type */
			BOOLEAN{@Override public ValueExp parse(final String v){return Query.value(Boolean.parseBoolean(v.trim()));}},
			/** Double type */
			DOUBLE{@Override public ValueExp parse(final String v){return Query.value(Double.parseDouble(v.trim()));}},
			/** Float type */
			FLOAT{@Override public ValueExp parse(final String v){return Query.value(Float.parseFloat(v.trim()));}},
			/** Integer type */
			INT{@Override public ValueExp parse(final String v){return Query.value(Integer.parseInt(v.trim()));}},
			/** Long type */
			LONG{@Override public ValueExp parse(final String v){return Query.value(Long.parseLong(v.trim()));}},
			/** Numeric type */
			NUMBER{@Override public ValueExp parse(final String v){return Query.value(new Double(v.trim()));}},
			/** String type */
			STRING{@Override public ValueExp parse(final String v){return Query.value(v.trim());}};
		}
		
		private static final String ATTR_TYPES = Arrays.toString(ValueType.values());

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecodeInvoker#eval(java.util.Map)
		 */
		@Override
		public ValueExp eval(final Map<String, String> attributes) {
			final String type = attributes.get(TYPE_KEY);
			final String value = attributes.get(VALUE_KEY);
			if(type==null || type.trim().isEmpty()) throw new RuntimeException("The type attribute was null or empty");
			if(value==null || value.trim().isEmpty()) throw new RuntimeException("The value attribute was null or empty");
			final ValueType vt;
			try {
				vt = ValueType.valueOf(type.trim().toUpperCase());
			} catch (Exception ex) {
				throw new RuntimeException("The type attribute [" + type + "] was not recognized. Valid types are: " + ATTR_TYPES);
			}		
			return vt.parse(value);
		}
	}	

	/**
	 * <p>Title: IsInstanceOfInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#ISINSTANCEOF}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.IsInstanceOfInvoker</code></p>
	 */

	public static class IsInstanceOfInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {		
			return Query.isInstanceOf((StringValueExp)resolve(xmlArgs));
		}
	}
	
	/**
	 * <p>Title: EqInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#EQ}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.EqInvoker</code></p>
	 */

	public static class EqInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.eq((ValueExp)exps[0], (ValueExp)exps[1]);
		}
	}
	
	/**
	 * <p>Title: LeqInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#LEQ}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.LeqInvoker</code></p>
	 */

	public static class LeqInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.leq((ValueExp)exps[0], (ValueExp)exps[1]);
		}
	}
	
	/**
	 * <p>Title: GeqInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#GEQ}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.GeqInvoker</code></p>
	 */

	public static class GeqInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.geq((ValueExp)exps[0], (ValueExp)exps[1]);
		}
	}
	
	/**
	 * <p>Title: GtInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#GT}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.GtInvoker</code></p>
	 */

	public static class GtInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.gt((ValueExp)exps[0], (ValueExp)exps[1]);
		}
	}

	/**
	 * <p>Title: LtInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#LT}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.LtInvoker</code></p>
	 */

	public static class LtInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.geq((ValueExp)exps[0], (ValueExp)exps[1]);
		}
	}
	
	/**
	 * <p>Title: InInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#IN}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.InInvoker</code></p>
	 */

	public static class InInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			final int size = exps.length;
			final ValueExp[] vexps = new ValueExp[size-1];
			for(int i = 1; i < size; i++) {
				vexps[i-1] = (ValueExp)exps[i];
			}
			return Query.in((ValueExp)exps[0], vexps);
		}
	}
	
	/**
	 * <p>Title: MatchInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#MATCH}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.MatchInvoker</code></p>
	 */

	public static class MatchInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.match((AttributeValueExp)exps[0], (StringValueExp)exps[1]);
		}
	}
	
	
	
	/**
	 * <p>Title: BetweenInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#BETWEEN}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.BetweenInvoker</code></p>
	 */

	public static class BetweenInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.between((ValueExp)exps[0], (ValueExp)exps[1], (ValueExp)exps[2]);
		}
	}
	
	/**
	 * <p>Title: ClassAttrInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#CLASSATTR}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.ClassAttrInvoker</code></p>
	 */

	public static class ClassAttrInvoker extends AbstractQueryDecodeInvoker<AttributeValueExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public AttributeValueExp invoke(final Node... xmlArgs) {
			return Query.classattr();
		}
	}
	
	/**
	 * <p>Title: InitialSubstringInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#INITIALSUBSTRING}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.InitialSubstringInvoker</code></p>
	 */

	public static class InitialSubstringInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.initialSubString((AttributeValueExp)exps[0], (StringValueExp)exps[1]);
		}
	}
	
	/**
	 * <p>Title: AnySubstringInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#ANYSUBSTRING}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.AnySubstringInvoker</code></p>
	 */

	public static class AnySubstringInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.anySubString((AttributeValueExp)exps[0], (StringValueExp)exps[1]);
		}
	}
	
	/**
	 * <p>Title: FinalSubstringInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#FINALSUBSTRING}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.FinalSubstringInvoker</code></p>
	 */

	public static class FinalSubstringInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.finalSubString((AttributeValueExp)exps[0], (StringValueExp)exps[1]);
		}
	}
	
	
	/**
	 * <p>Title: DivInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#DIV}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.DivInvoker</code></p>
	 */

	public static class DivInvoker extends AbstractQueryDecodeInvoker<ValueExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public ValueExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.div((ValueExp)exps[0], (ValueExp)exps[1]);
		}
	}
	
	/**
	 * <p>Title: PlusInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#PLUS}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.PlusInvoker</code></p>
	 */

	public static class PlusInvoker extends AbstractQueryDecodeInvoker<ValueExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public ValueExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.plus((ValueExp)exps[0], (ValueExp)exps[1]);
		}
	}
	
	/**
	 * <p>Title: MinusInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#MINUS}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.MinusInvoker</code></p>
	 */

	public static class MinusInvoker extends AbstractQueryDecodeInvoker<ValueExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public ValueExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.minus((ValueExp)exps[0], (ValueExp)exps[1]);
		}
	}

	/**
	 * <p>Title: TimesInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#TIMES}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.TimesInvoker</code></p>
	 */

	public static class TimesInvoker extends AbstractQueryDecodeInvoker<ValueExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public ValueExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.times((ValueExp)exps[0], (ValueExp)exps[1]);
		}
	}

	
	/**
	 * <p>Title: AndInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#AND}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.AndInvoker</code></p>
	 */

	public static class AndInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.and((QueryExp)exps[0], (QueryExp)exps[1]);
		}
	}
	
	/**
	 * <p>Title: NotInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#NOT}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.NotInvoker</code></p>
	 */

	public static class NotInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object ret = resolve(xmlArgs);
			return Query.not((QueryExp)ret);
		}
	}
	
	/**
	 * <p>Title: AttrInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#ATTRC}
	 * and {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#ATTR}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.AttrInvoker</code></p>
	 */

	public static class AttrInvoker extends AbstractQueryDecodeInvoker<AttributeValueExp> {
		/** Static re-usable instance */
		public static final AttrInvoker INSTANCE = new AttrInvoker();
//		/**
//		 * {@inheritDoc}
//		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
//		 */
//		@Override
//		public AttributeValueExp invoke(final Node... xmlArgs) {
//			final Object exp = resolve(xmlArgs);
//			if(exp.getClass().isArray()) {
//				return Query.attr((String)exp);
//			}
//			final Object[] exps = (Object[])exp;
//			return Query.attr((String)exps[0], (String)exps[1]);
//		}
//	}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.AbstractQueryDecodeInvoker#eval(java.util.Map)
		 */
		@Override
		public AttributeValueExp eval(Map<String, String> attributes) {
			final String className = attributes.get("c");
			final String name = attributes.get("v");
			if(name==null) throw new RuntimeException("The 'name' attribute with a key of 'v' was null");
			if(className==null || className.trim().isEmpty()) {
				return Query.attr(name);
			} 
			return Query.attr(className.trim(), name);
		}
	
	}
	
	/**
	 * <p>Title: OrInvoker</p>
	 * <p>Description: QueryDecode invoker for {@link com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode#OR}</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.qinvokers.Invokers.OrInvoker</code></p>
	 */

	public static class OrInvoker extends AbstractQueryDecodeInvoker<QueryExp> {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.AbstractQueryDecodeInvoker#invoke(org.w3c.dom.Node[])
		 */
		@Override
		public QueryExp invoke(final Node... xmlArgs) {
			final Object[] exps = (Object[]) resolve(xmlArgs);
			return Query.or((QueryExp)exps[0], (QueryExp)exps[1]);
		}
	}


}
