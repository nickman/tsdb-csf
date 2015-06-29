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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import javax.management.AttributeValueExp;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.StringValueExp;
import javax.management.ValueExp;

import org.w3c.dom.Node;

import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.AndInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.AnySubstringInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.AttrInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.OrInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.BetweenInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.ClassAttrInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.DivInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.EqInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.FinalSubstringInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.GeqInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.GtInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.InInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.InitialSubstringInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.IsInstanceOfInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.LeqInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.LtInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.MatchInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.MinusInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.NotInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.PlusInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.TimesInvoker;
import com.heliosapm.opentsdb.client.jvmjmx.customx.Invokers.ValueExpInvoker;

/**
 * <p>Title: QueryDecode</p>
 * <p>Description: Functional enumeration for static query builder methods in {@link Query}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecode</code></p>
 */

public enum QueryDecode implements QueryDecodeInvoker<Object> {
	/** Represents the {@link Query#isInstanceOf(javax.management.StringValueExp)} static method */
	ISINSTANCEOF(new IsInstanceOfInvoker(), false, 1, QueryExp.class, StringValueExp.class),
	/** Represents the {@link Query#geq(javax.management.ValueExp,javax.management.ValueExp)} static method */
	GEQ(new GeqInvoker(), false, 2, QueryExp.class, ValueExp.class, ValueExp.class),
	/** Represents the {@link Query#leq(javax.management.ValueExp,javax.management.ValueExp)} static method */
	LEQ(new LeqInvoker(), false, 2, QueryExp.class, ValueExp.class, ValueExp.class),
	/** Represents the {@link Query#between(javax.management.ValueExp,javax.management.ValueExp,javax.management.ValueExp)} static method */
	BETWEEN(new BetweenInvoker(), false, 3, QueryExp.class, ValueExp.class, ValueExp.class, ValueExp.class),
	/** Represents the {@link Query#classattr()} static method */
	CLASSATTR(new ClassAttrInvoker(), true, 0, AttributeValueExp.class),
	/** Represents the {@link Query#initialSubString(javax.management.AttributeValueExp,javax.management.StringValueExp)} static method */
	INITIALSUBSTRING(new InitialSubstringInvoker(), false, 2, QueryExp.class, AttributeValueExp.class, StringValueExp.class),
	/** Represents the {@link Query#anySubString(javax.management.AttributeValueExp,javax.management.StringValueExp)} static method */
	ANYSUBSTRING(new AnySubstringInvoker(), false, 2, QueryExp.class, AttributeValueExp.class, StringValueExp.class),
	/** Represents the {@link Query#finalSubString(javax.management.AttributeValueExp,javax.management.StringValueExp)} static method */
	FINALSUBSTRING(new FinalSubstringInvoker(), false, 2, QueryExp.class, AttributeValueExp.class, StringValueExp.class),
	/** Represents the {@link Query#not(javax.management.QueryExp)} static method */
	NOT(new NotInvoker(), false, 1, QueryExp.class, QueryExp.class),
	/** Represents the {@link Query#attr(java.lang.String,java.lang.String)} static method */
	ATTRC(AttrInvoker.INSTANCE, true, 2, AttributeValueExp.class, String.class, String.class),
	/** Represents the {@link Query#attr(java.lang.String)} static method */
	ATTR(AttrInvoker.INSTANCE, true, 1, AttributeValueExp.class, String.class),
	/** Represents the {@link Query#plus(javax.management.ValueExp,javax.management.ValueExp)} static method */
	PLUS(new PlusInvoker(), false, 2, ValueExp.class, ValueExp.class, ValueExp.class),
	/** Represents the {@link Query#minus(javax.management.ValueExp,javax.management.ValueExp)} static method */
	MINUS(new MinusInvoker(), false, 2, ValueExp.class, ValueExp.class, ValueExp.class),
	/** Represents the {@link Query#div(javax.management.ValueExp,javax.management.ValueExp)} static method */
	DIV(new DivInvoker(), false, 2, ValueExp.class, ValueExp.class, ValueExp.class),
	/** Represents the {@link Query#times(javax.management.ValueExp,javax.management.ValueExp)} static method */
	TIMES(new TimesInvoker(), false, 2, ValueExp.class, ValueExp.class, ValueExp.class),
	/** Represents the {@link Query#gt(javax.management.ValueExp,javax.management.ValueExp)} static method */
	GT(new GtInvoker(), false, 2, QueryExp.class, ValueExp.class, ValueExp.class),
	/** Represents the {@link Query#lt(javax.management.ValueExp,javax.management.ValueExp)} static method */
	LT(new LtInvoker(), false, 2, QueryExp.class, ValueExp.class, ValueExp.class),
	/** Represents the {@link Query#in(javax.management.ValueExp,javax.management.ValueExp[])} static method */
	IN(new InInvoker(), false, 2, QueryExp.class, ValueExp.class, ValueExp[].class),
	/** Represents the {@link Query#eq(javax.management.ValueExp,javax.management.ValueExp)} static method */
	EQ(new EqInvoker(), false, 2, QueryExp.class, ValueExp.class, ValueExp.class),
	/** Represents the {@link Query#match(javax.management.AttributeValueExp,javax.management.StringValueExp)} static method */
	MATCH(new MatchInvoker(), false, 2, QueryExp.class, AttributeValueExp.class, StringValueExp.class),
	/** Represents the {@link Query#and(javax.management.QueryExp,javax.management.QueryExp)} static method */
	AND(new AndInvoker(), false, 2, QueryExp.class, QueryExp.class, QueryExp.class),
	/** Represents the {@link Query#or(javax.management.QueryExp,javax.management.QueryExp)} static method */
	OR(new OrInvoker(), false, 2, QueryExp.class, QueryExp.class, QueryExp.class),
	/** Represents the multi-typed <b><code>value</code></b> methods in {@link Query} */
	VALUE(new ValueExpInvoker(), true, 2, ValueExp.class, String.class, String.class);

	
	
	private QueryDecode(final QueryDecodeInvoker<?> decoder, final boolean terminal, final int cnt, final Class<?> returnType, final Class<?>...types) {
		this.argCount = cnt;
		this.returnType = returnType;
		this.types = types;
		this.terminal = terminal;
		this.decoder = decoder;
	}
	
	/** A set of QueryDecodes that return QueryExp instances */
	public static final Set<QueryDecode> QUERYEXP_DECODES;
	/** A set of QueryDecodes that return ValueExp instances */
	public static final Set<QueryDecode> VALUEEXP_DECODES;
	/** A set of QueryDecodes that return AttributeValueExp instances */
	public static final Set<QueryDecode> ATTRVALUEEXP_DECODES = Collections.unmodifiableSet(EnumSet.of(ATTR, ATTRC, CLASSATTR));
	
	static {
		final QueryDecode[] decodes = QueryDecode.values();
		EnumSet<QueryDecode> tmpQExp = EnumSet.noneOf(QueryDecode.class);
		EnumSet<QueryDecode> tmpVExp = EnumSet.noneOf(QueryDecode.class);
		for(QueryDecode q: decodes) {
			if(q.returnType.equals(QueryExp.class)) tmpQExp.add(q);
		}
		QUERYEXP_DECODES = Collections.unmodifiableSet(tmpQExp);
		VALUEEXP_DECODES = Collections.unmodifiableSet(tmpVExp);
	}
	
	
	/** Indicates if the QueryDecode is terminal, i.e. there's no lower level evals */
	private final boolean terminal;
	/** The argument count */
	private final int argCount;
	/** The return type */
	private final Class<?> returnType;	
	/** The parameter types */
	private final Class<?>[] types;
	/** The decoder */
	private final QueryDecodeInvoker<? extends Object> decoder; 
	
	/**
	 * Returns the QueryDecode represented by the passed XML node
	 * @param xmlNode The xml node 
	 * @return the QueryDecode or null if it was not matched
	 */
	public static QueryDecode getNodeType(final Node xmlNode) {
		if(xmlNode==null) throw new IllegalArgumentException("The passed node was null");
		return forNameOrNull(xmlNode.getNodeName());
	}
	
	/**
	 * Returns the QueryDecode for the passed name
	 * @param name The name to decode which is trimmed and upper cased.
	 * @return The decoded QueryDecode
	 * @throws IllegalArgumentException if the name is null, or empty or cannot be decoded to a QueryDecode
	 */
	public static QueryDecode forName(final String name) {
		if(name==null || name.trim().isEmpty()) throw new IllegalArgumentException("The passed name was null or empty");
		try {
			return valueOf(name.trim().toUpperCase());
		} catch (Exception ex) {
			throw new IllegalArgumentException("The passed name [" + name + "] was not a valid QueryCode");
		}
	}
	
	/**
	 * Returns the QueryDecode for the passed name or null if the name cannot be decoded
	 * @param name The name to decode which is trimmed and upper cased.
	 * @return The decoded QueryDecode or null if the name cannot be decoded
	 */
	public static QueryDecode forNameOrNull(final String name) {
		try {
			return forName(name);
		} catch (Exception ex) {
			return null;
		}
	}
	
	/**
	 * Determines if the passed name is a valid QueryDecode
	 * @param name The name to test 
	 * @return true if the passed name is a valid QueryDecode, false otherwise
	 */
	public static boolean isQueryDecode(final String name) {
		return forNameOrNull(name)!=null;
	}
	
	/**
	 * Returns the invocation argument count
	 * @return the invocation argument count
	 */
	public int getArgCount() {
		return argCount;
	}
	
	/**
	 * Returns the parameter types
	 * @return the parameter types
	 */
	public Class<?>[] getTypes() {
		return types.clone();
	}
	
	/**
	 * Returns the return type
	 * @return the return type
	 */
	public Class<?> getReturnType() {
		return returnType;
	}

	/**
	 * Indicates if the QueryDecode is terminal, i.e. there's no lower level evals
	 * @return true if the QueryDecode is terminal, false otherwise
	 */
	public boolean isTerminal() {
		return terminal;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecodeInvoker#invoke(org.w3c.dom.Node[])
	 */
	@Override
	public Object invoke(final Node... xmlArgs) {
		return decoder.invoke(xmlArgs);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecodeInvoker#eval(java.util.Map)
	 */
	@Override
	public Object eval(final Map<String, String> attributes) {
		return decoder.eval(attributes);
	}
	
	
}
