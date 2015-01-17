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

package com.heliosapm.opentsdb.client.opentsdb;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;


/**
 * <p>Title: AnnotationBuilder</p>
 * <p>Description: Fluent style OpenTSDB Annotation builder</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.AnnotationBuilder</code></p>
 */

public class AnnotationBuilder {
	/** Unix epoch timestamp, in seconds, marking the time when the annotation event should be recorded */
	protected final int startTime;
	/** An optional Unix epoch timestamp end time for the event if it has completed or been resolved */
	protected int endTime = -1;
	/** A TSUID if the annotation is associated with a timeseries. This may be null or empty if the note was for a global event */
	protected String tsuid = null;
	/** A brief description of the event */
	protected StringBuilder description = null;
	/** Detailed notes about the event */
	protected StringBuilder notes = null;
	/** A key/value map to store custom fields and values */
	protected Map<String, String> custom = new LinkedHashMap<String, String>();
	
	
	/**
	 * Creates a new AnnotationBuilder
	 * @param startTime The annotation start time in seconds (Unix epoch timestamp)
	 */
	public AnnotationBuilder(final int startTime) {
		if(startTime < 1) throw new IllegalArgumentException("Invalid start time:" + startTime);
		this.startTime = startTime;
	}
	
	/**
	 * Creates a new AnnotationBuilder with a start time of current
	 */
	public AnnotationBuilder() {
		this((int)TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
	}

	/**
	 * Sets the annotation end time
	 * @param endTime the endTime to set
	 * @return this AnnotationBuilder
	 */
	public final AnnotationBuilder setEndTime(final int endTime) {
		if(endTime < 1) throw new IllegalArgumentException("Invalid end time:" + endTime);
		this.endTime = endTime;		
		return this;
	}

	/**
	 * Sets the annotation tsduid
	 * @param tsuid the tsuid to set
	 * @return this AnnotationBuilder
	 */
	public final AnnotationBuilder setTSUID(final String tsuid) {
		if(tsuid==null || tsuid.trim().isEmpty()) throw new IllegalArgumentException("The passed TSUID was null or empty");
		this.tsuid = tsuid;
		return this;
	}

	/**
	 * Appends a description fragment to the annotation
	 * @param description the description fragment to append
	 * @return this AnnotationBuilder
	 */
	public final AnnotationBuilder setDescription(final String description) {
		if(description==null || description.trim().isEmpty()) throw new IllegalArgumentException("The passed description fragment was null or empty");
		if(this.description == null) this.description = new StringBuilder();
		else this.description.append("\n");
		this.description.append(description.trim());		
		return this;
	}

	/**
	 * Appends a note to the annotation
	 * @param notes the notes to append to the annotation
	 * @return this AnnotationBuilder
	 */
	public final AnnotationBuilder setNotes(final String notes) {
		if(notes==null || notes.trim().isEmpty()) throw new IllegalArgumentException("The passed note was null or empty");
		if(this.notes==null) this.notes = new StringBuilder();
		else this.notes.append("\n");
		this.notes.append(notes.trim());		
		return this;
	}

	/**
	 * Adds a custom key/value pair
	 * @param key The cutom key
	 * @param value The cutom value
	 * @return this AnnotationBuilder
	 */
	public final AnnotationBuilder setCustom(final String key, final String value) {
		if(key==null || key.trim().isEmpty()) throw new IllegalArgumentException("The passed key was null or empty");
		if(value==null || value.trim().isEmpty()) throw new IllegalArgumentException("The passed value was null or empty");
		this.custom.put(key.trim(), value.trim());		
		return this;
	}
	
	/**
	 * Builds and returns the annotation
	 * @return the built annotation
	 */
	public final TSDBAnnotation build() {
		return new TSDBAnnotation(this);
	}
	
	
	
	/**
	 * <p>Title: TSDBAnnotation</p>
	 * <p>Description: A representation of an OpenTSDB Annotation which will serialize into the correct JSON to be HTTP Posted</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.AnnotationBuilder.TSDBAnnotation</code></p>
	 */
	public static class TSDBAnnotation { 
		/** Unix epoch timestamp, in seconds, marking the time when the annotation event should be recorded */
		protected final int startTime;
		/** An optional Unix epoch timestamp end time for the event if it has completed or been resolved */
		protected final int endTime;
		/** A TSUID if the annotation is associated with a timeseries. This may be null or empty if the note was for a global event */
		protected final String tsuid;
		/** A brief description of the event */
		protected final String description;
		/** Detailed notes about the event */
		protected final String notes;
		/** A key/value map to store custom fields and values */
		protected final Map<String, String> custom;
		
		/**
		 * Creates a new TSDBAnnotation
		 * @param annotationBuilder The anotation builder
		 */
		TSDBAnnotation(final AnnotationBuilder annotationBuilder) {
			startTime = annotationBuilder.startTime;
			endTime = annotationBuilder.endTime;
			tsuid = annotationBuilder.tsuid == null ? null : annotationBuilder.tsuid.trim(); 
			description = annotationBuilder.description == null ? null : annotationBuilder.description.toString().trim();  
			notes = annotationBuilder.notes == null ? null : annotationBuilder.notes.toString().trim();
			custom = annotationBuilder.custom.isEmpty() ? null : new LinkedHashMap<String, String>(annotationBuilder.custom);
		}
		
		/**
		 * Generates the JSON representation for this annotation
		 * @param indent The pretty print indent
		 * @return the JSON representation for this annotation
		 */
		public String toJSON(final int indent) {
			final JSONObject json = new JSONObject();
			json.put("startTime", startTime);
			if(endTime!=-1) json.put("endTime", endTime);
			if(tsuid!=null) json.put("tsuid", tsuid.trim());
			if(description!=null) json.put("description", description.trim());
			if(notes!=null) json.put("notes", notes.trim());
			if(custom!=null && !custom.isEmpty()) {
				final JSONObject cmap = new JSONObject();
				for(Map.Entry<String, String> c: custom.entrySet()) {
					cmap.put(c.getKey().trim(), c.getValue().trim());
				}
				json.put("custom", cmap);
			}
			return json.toString(indent);
		}
		
		/**
		 * Generates the JSON representation for this annotation with a zero indent
		 * @return the JSON representation for this annotation
		 */
		public String toJSON() {
			return toJSON(0);
		}
		

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return toJSON(2);
		}
		
	}

}
