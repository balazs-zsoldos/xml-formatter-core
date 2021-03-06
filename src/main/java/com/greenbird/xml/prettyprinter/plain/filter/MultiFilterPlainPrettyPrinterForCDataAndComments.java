/***************************************************************************
 * Copyright 2014 greenbird Integration Technology, http://www.greenbird.com/
 *
 * This file is part of the 'xml-formatter' project available at
 * http://greenbird.github.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.greenbird.xml.prettyprinter.plain.filter;

import java.util.Arrays;

/**
 * 
 * XML Pretty Printer. Produces conformant XML with the exception of an additional newline at the start of the file.
 * 
 * @author Thomas Rorvik Skjolberg
 *
 */

public class MultiFilterPlainPrettyPrinterForCDataAndComments extends AbstractMultiFilterPrettyPrinter {

	private final boolean cdata;
	private final boolean comment;

	public MultiFilterPlainPrettyPrinterForCDataAndComments(boolean cdata, boolean comment, boolean declaration, String[] anonymizes, String[] prunes, char indentationCharacter, int indentationMultiplier) {
		super(declaration, anonymizes, prunes, indentationCharacter, indentationMultiplier);
		this.cdata = cdata;
		this.comment = comment;
	}


	public boolean process(final char[] chars, int offset, int length, final StringBuilder buffer) {
		
		/**
		 *
		 * Implementation note: cdata + comments characters handled on end element, not in uniform way,
		 * so keep track of character type.
		 */
		
		final int bufferLength = buffer.length();
		
		final int[] elementMatches = new int[elementFilters.length];
		final int[] attributeMatches = new int[attributeFilters.length];
		
		boolean anon = false;
		
		CharactersType characterType = CharactersType.NONE;

		char[][] indentations = this.indentations;

		// use length as the end index
		length += offset;

		int sourceStart = offset;

		int level = 0;
	
		Type type = Type.NEITHER;
		
		try {
			while(offset < length) {
	
				if(chars[offset] == '<') {
					switch(chars[offset + 1]) {
						case '/' : {  // end tag
							level--;
							if(type != Type.INCREMENT) {
								// 2 or more endish elements
								// flush bytes
								if(sourceStart < offset) {
									buffer.append(chars, sourceStart, offset - sourceStart);
									sourceStart = offset;
								}
								
								buffer.append(indentations[level]);
							} else {
								type = Type.DECREMENT;
								// characters: text node, comment or cdata
								
								if(sourceStart < offset) {
									if(characterType == CharactersType.NONE) {
										if(anon) {
											buffer.append(FILTER_ANONYMIZE_MESSAGE);

											sourceStart = offset;
										}
									} else if(characterType == CharactersType.CDATA) {
										if(anon) {
											buffer.append(FILTER_ANONYMIZE_MESSAGE);

											sourceStart = offset - 3; // keep ]]>
										}
									} else {
										// keep all for comments
									}
									
								}
							}
							if(level < elementFilterStart.length) {
								anon = constrainMatches(elementMatches, level);
							} else {
								anon = false;
							}
							if(level < attributeFilterStart.length) {
								constrainAttributeMatches(attributeMatches, level);
							}
							
							offset = scanBeyondEndElement(chars, offset, length);
							
							// complete end element
							buffer.append(chars, sourceStart, offset - sourceStart);
							sourceStart = offset;
							
							characterType = CharactersType.NONE;

							continue;
						}
						case '!': {
							// complete cdata and comments so nodes
							
							if(chars[offset + 2] == '-') {
								// look for -->

								if(sourceStart < offset) {
									buffer.append(chars, sourceStart, offset - sourceStart);
									sourceStart = offset;
								}

								buffer.append(indentations[level]);

								if(!comment) {
									offset = scanBeyondComment(chars, offset, length);

									// complete comment
									buffer.append(chars, sourceStart, offset - sourceStart);
									sourceStart = offset;

									type = Type.DECREMENT;

									continue;
								}

								type = Type.DECREMENT;

							} else if(chars[offset + 2] == '[') {
								if(offset + 12 >= length) {
									return false;
								}

								offset += 9; // skip <![CDATA[
								
								// flush <![CDATA[
								buffer.append(chars, sourceStart, offset - sourceStart);
								sourceStart = offset;

								if(!cdata) {
									offset = scanBeyondCDataEnd(chars, offset + 3, length); // skip ]]>

									if(offset - 3 - sourceStart > 0) {
										if(anon) {
											buffer.append(FILTER_ANONYMIZE_MESSAGE);
											
											sourceStart = offset - 3; // keep ]]>
										}
									}

									// complete cdata
									buffer.append(chars, sourceStart, offset - sourceStart);
									sourceStart = offset;

									characterType = CharactersType.NONE;
								} else {
									// counts as characters or xml
									// handle on end tag
									characterType = CharactersType.CDATA;
								}								
								
								continue;
							} else {
								// assume entity declaration
								// look for >
								
								offset = scanBeyondDTDEnd(chars, offset, length);
								type = Type.DECREMENT;
								
								// complete entity declaration
								buffer.append(chars, sourceStart, offset - sourceStart);
								sourceStart = offset;

								continue;
							}
							break;						
						}
						case '?' : {
							// processing instruction
							// indentate as start elements
	
							if(sourceStart < offset) {
								buffer.append(chars, sourceStart, offset - sourceStart);
								sourceStart = offset;
							}
	
							offset = scanBeyondProcessingInstruction(chars, offset, length);
							
							// <?xml version="1.0"?>
							if(level == 0 && !declaration && isXMLDeclaration(chars, sourceStart, length)) {
								// skip the whole XML declaration
								sourceStart = offset;
							} else {
								buffer.append(indentations[level]);
	
								// complete processing instruction
								buffer.append(chars, sourceStart, offset - sourceStart);
								sourceStart = offset;
								
								type = Type.DECREMENT;
							}	
							
							characterType = CharactersType.NONE;

							continue;
						} 
						default : {
							// start element
							// flush bytes
							if(sourceStart < offset) {
								buffer.append(chars, sourceStart, offset - sourceStart);
								sourceStart = offset;
							}
		
							if(level >= indentations.length) {
								indentations = ensureCharCapacity(level + 8);
							}
		
							buffer.append(indentations[level]);
		
							level++;

							boolean prune = false;
							anon = false;
							
							// scan to end of local name
							offset++; // skip <
							while(offset < length) {
								if(chars[offset] == ':') {
									// ignore namespace
									buffer.append(chars, sourceStart, offset - sourceStart);
									sourceStart = offset;
								} else if(isEndOfLocalName(chars[offset])) {
									break;
								}
								offset++;
							}

							
							if(level < elementFilterStart.length) {
								// match again any higher filter
								if(matchElements(chars, offset, sourceStart, level, elementMatches)) {
									for(int i = elementFilterStart[level]; i < elementFilterEnd[level]; i++) {
										if(elementMatches[i] == level) {
											// matched
											if(elementFilters[i].filterType == FilterType.ANON) {
												anon = true;
											} else if(elementFilters[i].filterType == FilterType.PRUNE) {
												prune = true;
											}
										}
									}
								}
							}
							
							if(anyElementFilters != null) {
								FilterType filterType = matchAnyElements(chars, offset, sourceStart);
								if(filterType == FilterType.ANON) {
									anon = true;
								} else if(filterType == FilterType.PRUNE) {
									prune = true;
								}
							}
							
							if(level < attributeFilterStart.length) {
								offset = filterAttributes(chars, offset, length, buffer, sourceStart, level, attributeMatches);
							} else {
								offset = scanBeyondStartElementEnd(chars, offset, length);
								
								// complete start tag
								buffer.append(chars, sourceStart, offset - sourceStart);
							}

							sourceStart = offset;

							if(chars[offset - 2] == '/') {
								// empty element

								level--;
								
								// constrain matches
								if(level < elementFilterStart.length) {
									anon = constrainMatches(elementMatches, level);
								} else {
									anon = false;
								}
								if(level < attributeFilterStart.length) {
									constrainAttributeMatches(attributeMatches, level);
								}
								type = Type.DECREMENT;
							} else if(prune) {
								offset = skipSubtree(chars, offset, length);

								if(level >= indentations.length) {
									indentations = ensureCharCapacity(level + 8);
								}
			
								buffer.append(indentations[level]);

								buffer.append(FILTER_PRUNE_MESSAGE);
								
								type = Type.DECREMENT;
							} else {
								type = Type.INCREMENT;
							}
							
							sourceStart = offset;
							
							characterType = CharactersType.NONE;
							
							continue;
						}
					}	
				}
				
				offset++;
			}

			if(level != 0) {
				buffer.setLength(bufferLength);
				
				return false;
			}

			if(sourceStart < length) {
				buffer.append(chars, sourceStart, length - sourceStart);
			}
			
		} catch(Exception e) {
			buffer.setLength(bufferLength);
			
			return false;
		}
		return true;
	}


	public boolean isPrettyPrintCData() {
		return cdata;
	}
	
	public boolean isPrettyPrintComments() {
		return comment;
	}


	@Override
	public String toString() {
		return "MultiFilterPlainPrettyPrinterForCDataAndComments [cdata=" + cdata + ", comment=" + comment + ", anonymizes=" + Arrays.toString(anonymizes) + ", prunes=" + Arrays.toString(prunes)
				+ ", declaration=" + declaration + "]";
	}

	
}
