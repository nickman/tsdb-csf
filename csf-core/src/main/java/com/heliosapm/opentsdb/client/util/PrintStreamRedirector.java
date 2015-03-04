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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Locale;

/**
 * <p>Title: PrintStreamRedirector</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.PrintStreamRedirector</code></p>
 */

public class PrintStreamRedirector {
	/** The actual system out print stream */
	private static final PrintStream psOut = System.out;
	/** The actual system err print stream */
	private static final PrintStream psErr = System.err;
	
	private static final ThreadLocal<PrintStream> tlOut = new ThreadLocal<PrintStream>();
	private static final ThreadLocal<PrintStream> tlErr = new ThreadLocal<PrintStream>();
	
	
	public static void installOut(final PrintStream ps) {
		tlOut.set(ps);
	}
	
	public static void removeOut() {
		tlOut.remove();
	}
	
	public static void installErr(final PrintStream ps) {
		tlErr.set(ps);
	}

	public static void removeErr() {
		tlErr.remove();
	}
	
	private static class RedirectedPrintStream extends PrintStream {
		final PrintStream psDelegate;
		final ThreadLocal<PrintStream> tl;
		
		private PrintStream delegate() {
			final PrintStream _ps = tl.get();
			return _ps==null ? psDelegate : _ps;
		}
		
		/**
		 * Creates a new RedirectedPrintStream
		 * @param ps The redirect delegate
		 * @param tl The thread local to check for an override
		 */
		public RedirectedPrintStream(final PrintStream ps, final ThreadLocal<PrintStream> tl) {
			super(ps);
			this.tl = tl;
			this.psDelegate = ps;
		}
		
		/**
		 * @param b
		 * @throws IOException
		 * @see java.io.FilterOutputStream#write(byte[])
		 */
		public void write(byte[] b) throws IOException {
			delegate().write(b);
		}

		/**
		 * @return
		 * @see java.lang.Object#toString()
		 */
		public String toString() {
			return delegate().toString();
		}

		/**
		 * 
		 * @see java.io.PrintStream#flush()
		 */
		public void flush() {
			delegate().flush();
		}

		/**
		 * 
		 * @see java.io.PrintStream#close()
		 */
		public void close() {
			delegate().close();
		}

		/**
		 * @return
		 * @see java.io.PrintStream#checkError()
		 */
		public boolean checkError() {
			return delegate().checkError();
		}

		/**
		 * @param b
		 * @see java.io.PrintStream#write(int)
		 */
		public void write(int b) {
			delegate().write(b);
		}

		/**
		 * @param buf
		 * @param off
		 * @param len
		 * @see java.io.PrintStream#write(byte[], int, int)
		 */
		public void write(byte[] buf, int off, int len) {
			delegate().write(buf, off, len);
		}

		/**
		 * @param b
		 * @see java.io.PrintStream#print(boolean)
		 */
		public void print(boolean b) {
			delegate().print(b);
		}

		/**
		 * @param c
		 * @see java.io.PrintStream#print(char)
		 */
		public void print(char c) {
			delegate().print(c);
		}

		/**
		 * @param i
		 * @see java.io.PrintStream#print(int)
		 */
		public void print(int i) {
			delegate().print(i);
		}

		/**
		 * @param l
		 * @see java.io.PrintStream#print(long)
		 */
		public void print(long l) {
			delegate().print(l);
		}

		/**
		 * @param f
		 * @see java.io.PrintStream#print(float)
		 */
		public void print(float f) {
			delegate().print(f);
		}

		/**
		 * @param d
		 * @see java.io.PrintStream#print(double)
		 */
		public void print(double d) {
			delegate().print(d);
		}

		/**
		 * @param s
		 * @see java.io.PrintStream#print(char[])
		 */
		public void print(char[] s) {
			delegate().print(s);
		}

		/**
		 * @param s
		 * @see java.io.PrintStream#print(java.lang.String)
		 */
		public void print(String s) {
			delegate().print(s);
		}

		/**
		 * @param obj
		 * @see java.io.PrintStream#print(java.lang.Object)
		 */
		public void print(Object obj) {
			delegate().print(obj);
		}

		/**
		 * 
		 * @see java.io.PrintStream#println()
		 */
		public void println() {
			delegate().println();
		}

		/**
		 * @param x
		 * @see java.io.PrintStream#println(boolean)
		 */
		public void println(boolean x) {
			delegate().println(x);
		}

		/**
		 * @param x
		 * @see java.io.PrintStream#println(char)
		 */
		public void println(char x) {
			delegate().println(x);
		}

		/**
		 * @param x
		 * @see java.io.PrintStream#println(int)
		 */
		public void println(int x) {
			delegate().println(x);
		}

		/**
		 * @param x
		 * @see java.io.PrintStream#println(long)
		 */
		public void println(long x) {
			delegate().println(x);
		}

		/**
		 * @param x
		 * @see java.io.PrintStream#println(float)
		 */
		public void println(float x) {
			delegate().println(x);
		}

		/**
		 * @param x
		 * @see java.io.PrintStream#println(double)
		 */
		public void println(double x) {
			delegate().println(x);
		}

		/**
		 * @param x
		 * @see java.io.PrintStream#println(char[])
		 */
		public void println(char[] x) {
			delegate().println(x);
		}

		/**
		 * @param x
		 * @see java.io.PrintStream#println(java.lang.String)
		 */
		public void println(String x) {
			delegate().println(x);
		}

		/**
		 * @param x
		 * @see java.io.PrintStream#println(java.lang.Object)
		 */
		public void println(Object x) {
			delegate().println(x);
		}

		/**
		 * @param format
		 * @param args
		 * @return
		 * @see java.io.PrintStream#printf(java.lang.String, java.lang.Object[])
		 */
		public PrintStream printf(String format, Object... args) {
			return delegate().printf(format, args);
		}

		/**
		 * @param l
		 * @param format
		 * @param args
		 * @return
		 * @see java.io.PrintStream#printf(java.util.Locale, java.lang.String, java.lang.Object[])
		 */
		public PrintStream printf(Locale l, String format, Object... args) {
			return delegate().printf(l, format, args);
		}

		/**
		 * @param format
		 * @param args
		 * @return
		 * @see java.io.PrintStream#format(java.lang.String, java.lang.Object[])
		 */
		public PrintStream format(String format, Object... args) {
			return delegate().format(format, args);
		}

		/**
		 * @param l
		 * @param format
		 * @param args
		 * @return
		 * @see java.io.PrintStream#format(java.util.Locale, java.lang.String, java.lang.Object[])
		 */
		public PrintStream format(Locale l, String format, Object... args) {
			return delegate().format(l, format, args);
		}

		/**
		 * @param csq
		 * @return
		 * @see java.io.PrintStream#append(java.lang.CharSequence)
		 */
		public PrintStream append(CharSequence csq) {
			return delegate().append(csq);
		}

		/**
		 * @param csq
		 * @param start
		 * @param end
		 * @return
		 * @see java.io.PrintStream#append(java.lang.CharSequence, int, int)
		 */
		public PrintStream append(CharSequence csq, int start, int end) {
			return delegate().append(csq, start, end);
		}

		/**
		 * @param c
		 * @return
		 * @see java.io.PrintStream#append(char)
		 */
		public PrintStream append(char c) {
			return delegate().append(c);
		}
		
		
		
	}
	
	
	/**
	 * Creates a new PrintStreamRedirector
	 */
	private PrintStreamRedirector() {

	}

}
