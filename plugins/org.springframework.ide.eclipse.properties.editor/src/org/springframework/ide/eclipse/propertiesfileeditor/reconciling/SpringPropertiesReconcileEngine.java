/*******************************************************************************
 * Copyright (c) 2014 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.propertiesfileeditor.reconciling;

import static org.springframework.ide.eclipse.propertiesfileeditor.SpringPropertiesCompletionEngine.ASSIGNABLE_TYPES;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.IPropertiesFilePartitions;
import org.eclipse.jdt.internal.ui.propertiesfileeditor.PropertiesFileEscapes;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.springframework.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.ide.eclipse.propertiesfileeditor.FuzzyMap;
import org.springframework.ide.eclipse.propertiesfileeditor.SpringPropertiesEditorPlugin;
import org.springframework.ide.eclipse.propertiesfileeditor.util.StringUtil;

/**
 * Implements reconciling algorithm for {@link SpringPropertiesReconcileStrategy}.
 * <p>
 * The code in here could have been also part of the {@link SpringPropertiesReconcileStrategy}
 * itself, however isolating it here allows it to me more easily unit tested (no dependencies
 * on ISourceViewer which is difficult to 'mock' in testing harness.
 * 
 * @author Kris De Volder
 */
public class SpringPropertiesReconcileEngine {

	public static abstract class ValueParser {
		abstract Object parse(String str);
	}

	private static final Map<String,ValueParser> VALUE_PARSERS = new HashMap<String, ValueParser>();
	static {
		VALUE_PARSERS.put(Integer.class.getName(), new ValueParser() {
			Object parse(String str) {
				return Integer.parseInt(str);
			}
		});
		VALUE_PARSERS.put(Long.class.getName(), new ValueParser() {
			Object parse(String str) {
				return Long.parseLong(str);
			}
		});
		VALUE_PARSERS.put(Short.class.getName(), new ValueParser() {
			Object parse(String str) {
				return Short.parseShort(str);
			}
		});
		VALUE_PARSERS.put(Double.class.getName(), new ValueParser() {
			Object parse(String str) {
				return Double.parseDouble(str);
			}
		});
		VALUE_PARSERS.put(Float.class.getName(), new ValueParser() {
			Object parse(String str) {
				return Float.parseFloat(str);
			}
		});
		VALUE_PARSERS.put(Boolean.class.getName(), new ValueParser() {
			Object parse(String str) {
				//The 'more obvious' implementation is too liberal and accepts anything as okay.
				//return Boolean.parseBoolean(str);
				str = str.toLowerCase();
				if (str.equals("true")) {
					return true;
				} else if (str.equals("false")) {
					return false;
				}
				throw new IllegalArgumentException("Value should be 'true' or 'false'");
			}
		});
	}

	private FuzzyMap<ConfigurationMetadataProperty> fIndex;

	public interface IProblemCollector {

		void beginCollecting();
		void endCollecting();
		void accept(SpringPropertyProblem springPropertyProblem);

	}

	
	public SpringPropertiesReconcileEngine(FuzzyMap<ConfigurationMetadataProperty> index) {
		fIndex = index;
	}

	public void reconcile(IDocument doc, IProblemCollector problemCollector, IProgressMonitor mon) {
		if (fIndex==null || fIndex.isEmpty()) {
			//don't report errors when index is empty, simply don't check (otherwise we will just reprot 
			// all properties as errors, but this not really useful information since the cause is
			// some problem putting information about properties into the index.
			return;
		}
		problemCollector.beginCollecting();
		try {
			ITypedRegion[] regions = TextUtilities.computePartitioning(doc, IPropertiesFilePartitions.PROPERTIES_FILE_PARTITIONING, 0, doc.getLength(), true);
			if (regions!=null && regions.length>0) {
				mon.beginTask("Reconciling Spring Properties", regions.length);
				for (int i = 0; i < regions.length; i++) {
					ITypedRegion r = regions[i];
					try {
						String type = r.getType();
						if (IDocument.DEFAULT_CONTENT_TYPE.equals(type)) {
							String fullName = doc.get(r.getOffset(), r.getLength()).trim();
							IRegion trimmedRegion = r;
							if (fullName.length()<r.getLength()) {
								String paddedName = doc.get(r.getOffset(), r.getLength());
								int start = paddedName.indexOf(fullName);
								trimmedRegion = new Region(r.getOffset()+start, fullName.length()); 
							}
							if (fullName.isEmpty()) {
								if (!isAssigned(doc, r)) {
									//empty 'properties' are okay if not being assigned to. This just means that
									// there are empty sections in the props file and this is okay.
									continue;
								}
							}
							ConfigurationMetadataProperty validProperty = findLongestValidProperty(fullName);
							if (validProperty!=null) {
								if (validProperty.getId().length()==fullName.length()) {
									//exact match. Do not complain about key, but try to reconcile assigned value
									reconcileType(doc, validProperty, regions, i, problemCollector);
								} else { //found a 'validPrefix' which is shorter than the fullName.
									//check if it looks okay to continue with sub-properties at based on property type
									String validPrefix = validProperty.getId();
									if (ASSIGNABLE_TYPES.contains(validProperty.getType())) {
										problemCollector.accept(new SpringPropertyProblem(
												"Supbproperties are invalid for property "+
														"'"+validPrefix+"' with type '"+validProperty.getType()+"'", 
														trimmedRegion.getOffset()+validPrefix.length(),
														trimmedRegion.getLength()-validPrefix.length()
												));
									} else { //type is not a known directly assignable type
										//accessing sub-properties with '.' is probably ok in this case. 
										// So do not complain
									}
								}
							} else { //validProperty==null
								//The name is invalid, with no 'prefix' of the name being a valid property name.
								ConfigurationMetadataProperty similarEntry = fIndex.findLongestCommonPrefixEntry(fullName);
								String validPrefix = StringUtil.commonPrefix(similarEntry.getId(), fullName);
								problemCollector.accept(new SpringPropertyProblem("'"+fullName+"' is an unknown property."+suggestSimilar(similarEntry, validPrefix, fullName), 
										trimmedRegion.getOffset()+validPrefix.length(), trimmedRegion.getLength()-validPrefix.length()));
							} //end: validProperty==null
						}
						//TODO: check value types.
					} catch (Exception e) {
						SpringPropertiesEditorPlugin.log(e);
					}
				} //end: for regions
			}
		} catch (Throwable e2) {
			SpringPropertiesEditorPlugin.log(e2);
		} finally {
			problemCollector.endCollecting();
		}
	}

	private void reconcileType(IDocument doc, ConfigurationMetadataProperty validProperty, ITypedRegion[] regions, int i, IProblemCollector problems) {
		String expectType = validProperty.getType();
		ValueParser parser = getValueParser(expectType);
		if (parser!=null) {
			String escapedValue = getAssignedValue(doc, regions, i);
			IRegion errorRegion = null;
			if (escapedValue==null) {
				int charPos = lastNonWhitespaceCharOfRegion(doc, regions[i]);
				if (charPos>=0) {
					errorRegion = new Region(charPos, 1);
				}
			} else { //paddedValue!=null
				try {
					String valueStr = PropertiesFileEscapes.unescape(escapedValue.trim());
					parser.parse(valueStr);
				} catch (Exception e) {
					errorRegion = regions[i+1]; //i+1 must be in range, otherwise paddedValue would be null
					//Try to shrink errorRegion to demarkate the value String more precisely
					try {
						int endChar = lastNonWhitespaceCharOfRegion(doc, errorRegion);
						if (endChar>=0) {
							int startChar = firstNonWhitespaceCharOfRegion(doc, errorRegion);
							if (startChar>=0) {
								char assign = doc.getChar(startChar);
								if (assign==':'||assign=='=') {
									startChar = firstNonWhitespaceCharOfRegion(doc, new Region(startChar+1, endChar-startChar));
								}
							}
							if (startChar>=0) {
								errorRegion = new Region(startChar, endChar-startChar+1);
							}
						}
					} catch (Exception e2) {
						SpringPropertiesEditorPlugin.log(e2);
					}
				}
			}
			if (errorRegion!=null) {
				problems.accept(new SpringPropertyProblem("Expecting '"+shortTypeName(expectType)+"' for property '"+validProperty.getId()+"'", errorRegion.getOffset(), errorRegion.getLength()));
			}
		}
	}

	private String shortTypeName(String type) {
		if (type.startsWith("java.lang.")) {
			return type.substring("java.lang.".length());
		}
		return type;
	}

	private ValueParser getValueParser(String type) {
		return VALUE_PARSERS.get(type);
	}

	/**
	 * Compute location of the last character in the region that is not a whitespace character.
	 */
	private int lastNonWhitespaceCharOfRegion(IDocument doc, IRegion errorRegion) {
		try {
			int pos = errorRegion.getOffset()+errorRegion.getLength()-1;
			while (pos>=errorRegion.getOffset()&&Character.isWhitespace(doc.getChar(pos))) {
				pos--;
			}
			if (pos>=errorRegion.getOffset()) {
				return pos;
			}
		} catch (Exception e) {
			SpringPropertiesEditorPlugin.log(e);
		}
		return -1;
	}
	
	private int firstNonWhitespaceCharOfRegion(IDocument doc, IRegion errorRegion) {
		try {
			int pos = errorRegion.getOffset();
			int end = errorRegion.getOffset()+errorRegion.getLength();
			while (pos<end&&Character.isWhitespace(doc.getChar(pos))) {
				pos++;
			}
			if (pos<end) {
				return pos;
			}
		} catch (Exception e) {
			SpringPropertiesEditorPlugin.log(e);
		}
		return -1;
	}

	

	/**
	 * Extract the 'assigned' value represented as String from document. 
	 * 
	 * @param doc The document
	 * @param regions Regions in the document
	 * @param i Target region (i.e. points at the 'key' region for which we want to find assigned value
	 */
	private String getAssignedValue(IDocument doc, ITypedRegion[] regions, int i) {
		try {
			int valueRegionIndex = i+1;
			if (i<regions.length) {
				ITypedRegion valueRegion = regions[valueRegionIndex];
				if (valueRegion.getType()==IPropertiesFilePartitions.PROPERTY_VALUE) {
					String regionText = doc.get(valueRegion.getOffset(), valueRegion.getLength());
					//region text includes 
					//  potential padding with whitespace. 
					//  the ':' or '=' (if its there).
					regionText = regionText.trim();
					if (!regionText.isEmpty()) {
						char assignment = regionText.charAt(0);
						if (assignment==':'||assignment=='=') {
							//strip of 'assignment' and also more whitepace which might occur 
							//after it.
							regionText = regionText.substring(1).trim(); //
						}
					}
					return regionText;
				}
			}
		} catch (Exception e) {
			SpringPropertiesEditorPlugin.log(e);
		}
		return null;
	}

	private String suggestSimilar(ConfigurationMetadataProperty similarEntry, String validPrefix, String fullName) {
		int matchedChars = validPrefix.length();
		int wrongChars = fullName.length()-matchedChars;
		if (wrongChars<matchedChars) {
			return " Did you mean '"+similarEntry.getId()+"'?";
		} else {
			return "";
		}
	}

	/**
	 * Check that there is an assignment char directly following the given region.
	 */
	private boolean isAssigned(IDocument doc, IRegion r) {
		try {
			char c = doc.getChar(r.getOffset()+r.getLength());
			//Note either a '=' or a ':' can be used to assign properties.
			return c==':' || c=='=';
		} catch (BadLocationException e) {
			//happens if looking for assignment char outside the document
			return false;
		}
	}

	/**
	 * Find the longest known property that is a prefix of the given name. Here prefix does not mean
	 * 'string prefix' but a prefix in the sense of treating '.' as a kind of separators. So 
	 * 'prefix' is not allowed to end in the middle of a 'segment'.
	 */
	private ConfigurationMetadataProperty findLongestValidProperty(String name) {
		int endPos = name.length();
		ConfigurationMetadataProperty prop = null;
		while (endPos>0 && prop==null) {
			prop = fIndex.get(name.substring(0, endPos));
			if (prop==null) {
				endPos = name.lastIndexOf('.', endPos-1);
			}
		}
		return prop;
	}


}
