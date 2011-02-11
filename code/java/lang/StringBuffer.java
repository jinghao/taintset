package java.lang;

/*
 * Licensed Materials - Property of IBM,
 *     Copyright IBM Corp. 1998, 2007  All Rights Reserved
 */

import java.io.Serializable;

import java.util.Arrays;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.InvalidObjectException;

import javax.security.TaintSet;

/**
 * StringBuffer is a variable size contiguous indexable array of characters. The
 * length of the StringBuffer is the number of characters it contains. The
 * capacity of the StringBuffer is the number of characters it can hold.
 * <p>
 * Characters may be inserted at any position up to the length of the
 * StringBuffer, increasing the length of the StringBuffer. Characters at any
 * position in the StringBuffer may be replaced, which does not affect the
 * StringBuffer length.
 * <p>
 * The capacity of a StringBuffer may be specified when the StringBuffer is
 * created. If the capacity of the StringBuffer is exceeded, the capacity is
 * increased.
 * 
 * @author OTI
 * @version initial
 * 
 * @see String
 */

public final class StringBuffer implements Serializable, CharSequence,
		Appendable {
	private static final long serialVersionUID = 3388685877147921107L;

	private static final int INITIAL_SIZE = 16;
	private int count;
	private char[] value;
	private boolean shared;

	private transient TaintSet taintvalues;

	public boolean logging = false;
	public int[] logs = new int[10];

	/**
	 * Constructs a new StringBuffer using the default capacity.
	 */
	public StringBuffer() {
		this(INITIAL_SIZE);
	}

	/**
	 * Constructs a new StringBuffer using the specified capacity.
	 * 
	 * @param capacity
	 *            the initial capacity
	 */
	public StringBuffer(int capacity) {
		count = 0;
		value = new char[capacity];
		taintvalues = null;
	}

	/**
	 * Constructs a new StringBuffer containing the characters in the specified
	 * string and the default capacity.
	 * 
	 * @param string
	 *            the initial contents of this StringBuffer
	 * @exception NullPointerException
	 *                when string is null
	 */
	public StringBuffer(String string) {
		count = string.length();
		value = new char[count + INITIAL_SIZE];
		string.getChars(0, count, value, 0);
		taintvalues = string.taintvalues; // TODO: make a clone of taintvalues
	}

	public StringBuffer(StringBuffer strbuf, boolean taint) {
		count = strbuf.count;
		value = new char[strbuf.value.length];
		System.arraycopy(strbuf.value, 0, value, 0, count);
		taintvalues = taint ? TaintSet.allTainted : null;

	}

	/**
	 * Adds the character array to the end of this StringBuffer.
	 * 
	 * @param chars
	 *            the character array
	 * @return this StringBuffer
	 * 
	 * @exception NullPointerException
	 *                when chars is null
	 */
	public synchronized StringBuffer append(char chars[]) {
		int newSize = count + chars.length;
		if (newSize > value.length) {
			ensureCapacityImpl(newSize);
		}
		System.arraycopy(chars, 0, value, count, chars.length);

		count = newSize;
		return this;
	}

	/**
	 * Adds the specified sequence of characters to the end of this
	 * StringBuffer.
	 * 
	 * @param chars
	 *            a character array
	 * @param start
	 *            the starting offset
	 * @param length
	 *            the number of characters
	 * @return this StringBuffer
	 * 
	 * @exception IndexOutOfBoundsException
	 *                when <code>length < 0, start < 0</code> or
	 *                <code>start + length > chars.length</code>
	 * @exception NullPointerException
	 *                when chars is null
	 */
	public synchronized StringBuffer append(char chars[], int start, int length) {
		// start + length could overflow, start/length maybe MaxInt
		if (start >= 0 && 0 <= length && length <= chars.length - start) {
			int newSize = count + length;
			if (newSize > value.length) {
				ensureCapacityImpl(newSize);
			}
			System.arraycopy(chars, start, value, count, length);
			count = newSize;
			return this;
		} else
			throw new StringIndexOutOfBoundsException();
	}

	/**
	 * Adds the specified character to the end of this StringBuffer.
	 * 
	 * @param ch
	 *            a character
	 * @return this StringBuffer
	 */
	public synchronized StringBuffer append(char ch) {
		if (count >= value.length) {
			ensureCapacityImpl(count + 1);
		}
		value[count] = ch;
		count++;
		return this;
	}

	/**
	 * Adds the string representation of the specified double to the end of this
	 * StringBuffer.
	 * 
	 * @param value
	 *            the double
	 * @return this StringBuffer
	 */
	public StringBuffer append(double value) {
		return append(String.valueOf(value));
	}

	/**
	 * Adds the string representation of the specified float to the end of this
	 * StringBuffer.
	 * 
	 * @param value
	 *            the float
	 * @return this StringBuffer
	 */
	public StringBuffer append(float value) {
		return append(String.valueOf(value));
	}

	/**
	 * Adds the string representation of the specified integer to the end of
	 * this StringBuffer.
	 * 
	 * @param value
	 *            the integer
	 * @return this StringBuffer
	 */
	public StringBuffer append(int value) {
		return append(Integer.toString(value));
	}

	/**
	 * Adds the string representation of the specified long to the end of this
	 * StringBuffer.
	 * 
	 * @param value
	 *            the long
	 * @return this StringBuffer
	 */
	public StringBuffer append(long value) {
		return append(Long.toString(value));
	}

	/**
	 * Adds the string representation of the specified object to the end of this
	 * StringBuffer.
	 * 
	 * @param value
	 *            the object
	 * @return this StringBuffer
	 */
	public StringBuffer append(Object value) {
		return append(String.valueOf(value));
	}

	/**
	 * Adds the specified string to the end of this StringBuffer.
	 * 
	 * @param string
	 *            the string
	 * @return this StringBuffer
	 */
	public synchronized StringBuffer append(String string) {
		if (string == null)
			string = String.valueOf(string);
		int adding = string.length();
		int newSize = count + adding;
		if (newSize > value.length) {
			ensureCapacityImpl(newSize);
		}
		string.getChars(0, adding, value, count);
		taintvalues = TaintSet.append(taintvalues, count, string.taintvalues, string.length(), capacity());
		count = newSize;
		return this;
	}

	/**
	 * Adds the string representation of the specified boolean to the end of
	 * this StringBuffer.
	 * 
	 * @param value
	 *            the boolean
	 * @return this StringBuffer
	 */
	public StringBuffer append(boolean value) {
		return append(String.valueOf(value));
	}

	/**
	 * Answers the number of characters this StringBuffer can hold without
	 * growing.
	 * 
	 * @return the capacity of this StringBuffer
	 * 
	 * @see #ensureCapacity
	 * @see #length
	 */
	public int capacity() {
		return value.length;
	}

	/**
	 * Answers the character at the specified offset in this StringBuffer.
	 * 
	 * @param index
	 *            the zero-based index in this StringBuffer
	 * @return the character at the index
	 * 
	 * @exception IndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index >= length()</code>
	 */
	public synchronized char charAt(int index) {
		try {
			if (index < count)
				return value[index];
		} catch (IndexOutOfBoundsException e) {
		}
		throw new StringIndexOutOfBoundsException(index);
	}

	/**
	 * Deletes a range of characters.
	 * 
	 * @param start
	 *            the offset of the first character
	 * @param end
	 *            the offset one past the last character
	 * @return this StringBuffer
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>start < 0, start > end</code> or
	 *                <code>end > length()</code>
	 */
	public synchronized StringBuffer delete(int start, int end) {
		if (start >= 0) {
			if (end > count)
				end = count;
			if (end > start) {
				int length = count - end;
				try {
					taintvalues = TaintSet.replace(taintvalues, start, end, 0, null);
					if (!shared) {
						if (length > 0) {
							System.arraycopy(value, end, value, start, length);
						}
					} else {
						char[] newData = new char[value.length];
						if (start > 0) {
							System.arraycopy(value, 0, newData, 0, start);
						}
						if (length > 0) {
							System.arraycopy(value, end, newData, start,
											length);
						}
						value = newData;
						shared = false;
					}
				} catch (IndexOutOfBoundsException e) {
					throw new StringIndexOutOfBoundsException();
				}
				count -= end - start;
				return this;
			}
			if (start == end)
				return this;
		}
		throw new StringIndexOutOfBoundsException();
	}

	/**
	 * Deletes a single character
	 * 
	 * @param location
	 *            the offset of the character to delete
	 * @return this StringBuffer
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>location < 0</code> or
	 *                <code>location >= length()</code>
	 */
	public synchronized StringBuffer deleteCharAt(int location) {
		if (0 <= location && location < count) {
			int length = count - location - 1;
			try {
				taintvalues = TaintSet.replace(taintvalues, location, location + 1, 0, null);
				if (!shared) {
					if (length > 0) {
						System.arraycopy(value, location + 1, value, location,
								length);
					}
				} else {
					char[] newData = new char[value.length];
					boolean[] newTaint = new boolean[value.length];
					if (location > 0) {
						System.arraycopy(value, 0, newData, 0, location);
					}
					if (length > 0) {
						System.arraycopy(value, location + 1, newData,
								location, length);
					}
					value = newData;
					shared = false;
				}
			} catch (IndexOutOfBoundsException e) {
				throw new StringIndexOutOfBoundsException(location);
			}
			count--;
			return this;
		} else
			throw new StringIndexOutOfBoundsException(location);
	}

	/**
	 * Ensures that this StringBuffer can hold the specified number of
	 * characters without growing.
	 * 
	 * @param min
	 *            the minimum number of elements that this StringBuffer will
	 *            hold before growing
	 */
	public synchronized void ensureCapacity(int min) {
		if (min > value.length)
			ensureCapacityImpl(min);
	}

	private void ensureCapacityImpl(int min) {
		int twice = (value.length << 1) + 2;
		char[] newData = new char[min > twice ? min : twice];
		taintvalues = TaintSet.ensureCapacity(taintvalues, twice); // TODO: Check duplicate
		System.arraycopy(value, 0, newData, 0, count);
		value = newData;
		shared = false;

	}

	/**
	 * Copies the specified characters in this StringBuffer to the character
	 * array starting at the specified offset in the character array.
	 * 
	 * @param start
	 *            the starting offset of characters to copy
	 * @param end
	 *            the ending offset of characters to copy
	 * @param buffer
	 *            the destination character array
	 * @param index
	 *            the starting offset in the character array
	 * 
	 * @exception IndexOutOfBoundsException
	 *                when <code>start < 0, end > length(),
	 * 				start > end, index < 0, end - start > buffer.length - index</code>
	 * @exception NullPointerException
	 *                when buffer is null
	 */

	public synchronized void getChars(int start, int end, char[] buffer,
			int index) {
		// NOTE last character not copied!
		try {
			if (start <= count && end <= count) {
				System.arraycopy(value, start, buffer, index, end - start);
				return;
			}
		} catch (IndexOutOfBoundsException e) {
		}
		throw new StringIndexOutOfBoundsException();
	}

	/**
	 * Inserts the character array at the specified offset in this StringBuffer.
	 * 
	 * @param index
	 *            the index at which to insert
	 * @param chars
	 *            the character array to insert
	 * @return this StringBuffer
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index > length()</code>
	 * @exception NullPointerException
	 *                when chars is null
	 */
	public synchronized StringBuffer insert(int index, char[] chars) {
		if (0 <= index && index <= count) {
			move(chars.length, index);
			System.arraycopy(chars, 0, value, index, chars.length);
			count += chars.length;
			return this;
		} else
			throw new StringIndexOutOfBoundsException(index);
	}

	/**
	 * Inserts the specified sequence of characters at the specified offset in
	 * this StringBuffer.
	 * 
	 * @param index
	 *            the index at which to insert
	 * @param chars
	 *            a character array
	 * @param start
	 *            the starting offset
	 * @param length
	 *            the number of characters
	 * @return this StringBuffer
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>length < 0, start < 0,</code>
	 *                <code>start + length > chars.length, index < 0</code> or
	 *                <code>index > length()</code>
	 * @exception NullPointerException
	 *                when chars is null
	 */
	// TODO: Not ready for taintset!
	public synchronized StringBuffer insert(int index, char chars[], int start,
			int length) {
		if (0 <= index && index <= count) {
			// start + length could overflow, start/length maybe MaxInt
			if (start >= 0 && 0 <= length && length <= chars.length - start) {
				move(length, index);
				System.arraycopy(chars, start, value, index, length);
				count += length;
				return this;
			} else
				throw new StringIndexOutOfBoundsException();
		} else
			throw new StringIndexOutOfBoundsException(index);
	}

	/**
	 * Inserts the character at the specified offset in this StringBuffer.
	 * 
	 * @param index
	 *            the index at which to insert
	 * @param ch
	 *            the character to insert
	 * @return this StringBuffer
	 * 
	 * @exception IndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index > length()</code>
	 */
	public synchronized StringBuffer insert(int index, char ch) {
		if (0 <= index && index <= count) {
			move(1, index);
			value[index] = ch;
			TaintSet.set(taintvalues, index, false);
			count++;
			return this;
		} else
			throw new StringIndexOutOfBoundsException(index);
	}

	/**
	 * Inserts the string representation of the specified double at the
	 * specified offset in this StringBuffer.
	 * 
	 * @param index
	 *            the index at which to insert
	 * @param value
	 *            the double to insert
	 * @return this StringBuffer
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index > length()</code>
	 */
	public StringBuffer insert(int index, double value) {
		return insert(index, String.valueOf(value));
	}

	/**
	 * Inserts the string representation of the specified float at the specified
	 * offset in this StringBuffer.
	 * 
	 * @param index
	 *            the index at which to insert
	 * @param value
	 *            the float to insert
	 * @return this StringBuffer
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index > length()</code>
	 */
	public StringBuffer insert(int index, float value) {
		return insert(index, String.valueOf(value));
	}

	/**
	 * Inserts the string representation of the specified integer at the
	 * specified offset in this StringBuffer.
	 * 
	 * @param index
	 *            the index at which to insert
	 * @param value
	 *            the integer to insert
	 * @return this StringBuffer
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index > length()</code>
	 */
	public StringBuffer insert(int index, int value) {
		return insert(index, Integer.toString(value));
	}

	/**
	 * Inserts the string representation of the specified long at the specified
	 * offset in this StringBuffer.
	 * 
	 * @param index
	 *            the index at which to insert
	 * @param value
	 *            the long to insert
	 * @return this StringBuffer
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index > length()</code>
	 */
	public StringBuffer insert(int index, long value) {
		return insert(index, Long.toString(value));
	}

	/**
	 * Inserts the string representation of the specified object at the
	 * specified offset in this StringBuffer.
	 * 
	 * @param index
	 *            the index at which to insert
	 * @param value
	 *            the object to insert
	 * @return this StringBuffer
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index > length()</code>
	 */
	public StringBuffer insert(int index, Object value) {
		return insert(index, String.valueOf(value));
	}

	/**
	 * Inserts the string at the specified offset in this StringBuffer.
	 * 
	 * @param index
	 *            the index at which to insert
	 * @param string
	 *            the string to insert
	 * @return this StringBuffer
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index > length()</code>
	 */
	public synchronized StringBuffer insert(int index, String string) {
		if (0 <= index && index <= count) {
			if (string == null)
				string = String.valueOf(string);
			int min = string.length();
			move(min, index);
			string.getChars(0, min, value, index);
			taintvalues = TaintSet.union(taintvalues, index, string.taintvalues);
			count += min;
			return this;
		} else
			throw new StringIndexOutOfBoundsException(index);
	}

	/**
	 * Inserts the string representation of the specified boolean at the
	 * specified offset in this StringBuffer.
	 * 
	 * @param index
	 *            the index at which to insert
	 * @param value
	 *            the boolean to insert
	 * @return this StringBuffer
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index > length()</code>
	 */
	public StringBuffer insert(int index, boolean value) {
		return insert(index, String.valueOf(value));
	}

	/**
	 * Answers the size of this StringBuffer.
	 * 
	 * @return the number of characters in this StringBuffer
	 */
	public int length() {
		return count;
	}

	private void move(int size, int index) {
		int newSize;
		taintvalues = TaintSet.move(taintvalues, size, index);
		if (value.length - count >= size) {
			if (!shared) {
				System.arraycopy(value, index, value, index + size, count
						- index); // index == count case is no-op
				return;
			}
			newSize = value.length;
		} else {
			int a = count + size, b = (value.length << 1) + 2;
			newSize = a > b ? a : b;
		}

		char[] newData = new char[newSize];
		System.arraycopy(value, 0, newData, 0, index);
		System.arraycopy(value, index, newData, index + size, count - index); // index
																				// ==
																				// count
																				// case
																				// is
																				// no-op
		value = newData;
		shared = false;
	}

	/**
	 * Replace a range of characters with the characters in the specified
	 * String.
	 * 
	 * @param start
	 *            the offset of the first character
	 * @param end
	 *            the offset one past the last character
	 * @param string
	 *            a String
	 * @return this StringBuffer
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>start < 0</code> or <code>start > end</code>
	 */
	public synchronized StringBuffer replace(int start, int end, String string) {
		if (start >= 0) {
			if (end > count)
				end = count;
			if (end > start) {
				int stringLength = string.length();
				int diff = end - start - stringLength;
				if (diff > 0) { // replacing with fewer characters
					if (!shared) {
						System.arraycopy(value, end, value, start
								+ stringLength, count - end); // index == count case is no-op
					} else {
						char[] newData = new char[value.length];
						System.arraycopy(value, 0, newData, 0, start);
						System.arraycopy(value, end, newData, start
								+ stringLength, count - end); // index == count
																// case is no-op

						value = newData;
						shared = false;
					}
				} else if (diff < 0) { // replacing with more characters... need
										// some room
					move(-diff, end);
				} else if (shared) {
					value = (char[]) value.clone();
					shared = false;
				}
				string.getChars(0, stringLength, value, start);

				taintvalues = TaintSet.replace(taintvalues, start, end, stringLength, string.taintvalues);
				
				count -= diff;
				return this;
			}
			if (start == end) {
				if (string == null)
					throw new NullPointerException();
				return insert(start, string);
			}
		}
		throw new StringIndexOutOfBoundsException();
	}

	/**
	 * Reverses the order of characters in this StringBuffer.
	 * 
	 * @return this StringBuffer
	 */
	public synchronized StringBuffer reverse() {
		if (count < 2) {
			return this;
		}
		
		boolean[] taintarr = TaintSet.getTaintArray(taintvalues, count);
		
		if (!shared) {
			int end = count - 1;
			char frontHigh = value[0];
			char endLow = value[end];
			boolean frontHi = taintarr[0];
			boolean endLo = taintarr[end];
			boolean allowFrontSur = true, allowEndSur = true;
			for (int i = 0, mid = count / 2; i < mid; i++, --end) {
				char frontLow = value[i + 1];
				char endHigh = value[end - 1];
				boolean frontLo = taintarr[i + 1];
				boolean endHi = taintarr[end - 1];

				boolean surAtFront = false, surAtEnd = false;
				if (allowFrontSur && frontLow >= 0xdc00 && frontLow <= 0xdfff
						&& frontHigh >= 0xd800 && frontHigh <= 0xdbff) {
					surAtFront = true;
					if (count < 3)
						return this;
				}
				if (allowEndSur && endHigh >= 0xd800 && endHigh <= 0xdbff
						&& endLow >= 0xdc00 && endLow <= 0xdfff) {
					surAtEnd = true;
				}
				allowFrontSur = true;
				allowEndSur = true;
				if (surAtFront == surAtEnd) {
					if (surAtFront) {
						// both surrogates
						value[end] = frontLow;
						value[end - 1] = frontHigh;
						value[i] = endHigh;
						value[i + 1] = endLow;
						frontHigh = value[i + 2];
						endLow = value[end - 2];

						taintarr[end] = frontLo;
						taintarr[end - 1] = frontHi;
						taintarr[i] = endHi;
						taintarr[i + 1] = endLo;
						frontHi = taintarr[i + 2];
						endLo = taintarr[end - 2];

						i++;
						--end;
					} else {
						// neither surrogates
						value[end] = frontHigh;
						value[i] = endLow;
						frontHigh = frontLow;
						endLow = endHigh;

						taintarr[end] = frontHi;
						taintarr[i] = endLo;
						frontHi = frontLo;
						endLo = endHi;
					}
				} else {
					if (surAtFront) {
						// surrogate only at the front
						value[end] = frontLow;
						value[i] = endLow;
						endLow = endHigh;

						taintarr[end] = frontLo;
						taintarr[i] = endLo;
						endLo = endHi;

						allowFrontSur = false;
					} else {
						// surrogate only at the end
						value[end] = frontHigh;
						value[i] = endHigh;
						frontHigh = frontLow;

						taintarr[end] = frontHi;
						taintarr[i] = endHi;
						frontHi = frontLo;

						allowEndSur = false;
					}
				}
			}
			if ((count & 1) == 1 && (!allowFrontSur || !allowEndSur)) {
				value[end] = allowFrontSur ? endLow : frontHigh;
				taintarr[end] = allowFrontSur ? endLo : frontHi;
			}
		} else {
			char[] newData = new char[value.length];
			boolean[] newTaint = new boolean[taintarr.length];
			for (int i = 0, end = count; i < count; i++) {
				char high = value[i];
				boolean hi = taintarr[i];

				if ((i + 1) < count && high >= 0xd800 && high <= 0xdbff) {
					char low = value[i + 1];
					boolean lo = taintarr[i + 1];
					if (low >= 0xdc00 && low <= 0xdfff) {
						newData[--end] = low;
						newTaint[end] = lo;
						i++;
					}
				}
				newData[--end] = high;
				newTaint[end] = hi;
			}
			value = newData;
			taintarr = newTaint;
			shared = false;
		}

		taintvalues = TaintSet.generate(taintarr);
		
		return this;
	}

	/**
	 * Sets the character at the specified offset in this StringBuffer.
	 * 
	 * @param index
	 *            the zero-based index in this StringBuffer
	 * @param ch
	 *            the character
	 * 
	 * @exception IndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index >= length()</code>
	 */
	public synchronized void setCharAt(int index, char ch) {
		if (shared) {
			value = (char[]) value.clone();
			shared = false;
		}
		if (0 <= index && index < count)
			value[index] = ch;
		else
			throw new StringIndexOutOfBoundsException(index);
	}

	/**
	 * Sets the length of this StringBuffer to the specified length. If there
	 * are more than length characters in this StringBuffer, the characters at
	 * end are lost. If there are less than length characters in the
	 * StringBuffer, the additional characters are set to <code>\\u0000</code>.
	 * 
	 * @param length
	 *            the new length of this StringBuffer
	 * 
	 * @exception IndexOutOfBoundsException
	 *                when <code>length < 0</code>
	 * 
	 * @see #length
	 */
	public synchronized void setLength(int length) {
		if (length > value.length) {
			ensureCapacityImpl(length);
		} else if (length > count) {
			// void characters orphaned at the end by delete() or replace()
			Arrays.fill(value, count, length, (char) 0);
		} else if (shared) {
			if (length < 0)
				throw new IndexOutOfBoundsException();
			char[] newData = new char[value.length];
			if (length > 0) {
				System.arraycopy(value, 0, newData, 0, length);
			}
			value = newData;
			shared = false;
		} else if (length < 0)
			throw new IndexOutOfBoundsException();
		count = length;
	}

	/**
	 * Copies a range of characters into a new String.
	 * 
	 * @param start
	 *            the offset of the first character
	 * @return a new String containing the characters from start to the end of
	 *         the string
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>start < 0</code> or
	 *                <code>start > length()</code>
	 */
	public synchronized String substring(int start) {
		if (0 <= start && start <= count) {
			// return new String(value, start, count - start);
			return new String(value, start, count - start, TaintSet.makeCopy(TaintSet.generate(taintvalues, start, count)));
		}
		throw new StringIndexOutOfBoundsException(start);
	}

	/**
	 * Copies a range of characters into a new String.
	 * 
	 * @param start
	 *            the offset of the first character
	 * @param end
	 *            the offset one past the last character
	 * @return a new String containing the characters from start to end - 1
	 * 
	 * @exception StringIndexOutOfBoundsException
	 *                when <code>start < 0, start > end</code> or
	 *                <code>end > length()</code>
	 */
	public synchronized String substring(int start, int end) {
		if (0 <= start && start <= end && end <= count) {
			// return new String(value, start, end - start);
			return new String(value, start, end - start, TaintSet.makeCopy(TaintSet.generate(taintvalues, start, end)));
		}
		throw new StringIndexOutOfBoundsException();
	}

	/**
	 * Answers the contents of this StringBuffer.
	 * 
	 * @return a String containing the characters in this StringBuffer
	 */
	public synchronized String toString() {
		int wasted = value.length - count;
		if (wasted >= 768
				|| (wasted >= INITIAL_SIZE && wasted >= (value.length >> 1))) {
			// return new String(value, 0, count);
			return new String(value, 0, count, taintvalues);
		}
		shared = true;
		// return new String (0, count, value);
		return new String(0, count, value, taintvalues); // TODO: double check
	}

	/*
	 * Return the underlying buffer and set the shared flag.
	 */
	char[] shareValue() {
		shared = true;
		return value;
	}

	TaintSet shareTaintSet() {
		return taintvalues;
	}

	private synchronized void writeObject(ObjectOutputStream stream)
			throws IOException {
		stream.defaultWriteObject();
		logs[0]++;
	}

	private void readObject(ObjectInputStream stream) throws IOException,
			ClassNotFoundException {
		stream.defaultReadObject();
		if (count > value.length)
			// K0199 = Count out of range
			throw new InvalidObjectException(com.ibm.oti.util.Msg
					.getString("K0199")); //$NON-NLS-1$

		shared = false;

		logs[1]++;
	}

	/**
	 * Adds the specified StringBuffer to the end of this StringBuffer.
	 * 
	 * @param sbuffer
	 *            the StringBuffer
	 * @return this StringBuffer
	 * 
	 * @since 1.4
	 */
	public synchronized StringBuffer append(StringBuffer sbuffer) {
		if (sbuffer == null)
			return append((String) null);
		synchronized (sbuffer) {
			int adding = sbuffer.count;
			int newSize = count + adding;
			if (newSize > value.length) {
				ensureCapacityImpl(newSize);
			}
			System.arraycopy(sbuffer.value, 0, value, count, adding);
			taintvalues = TaintSet.append(taintvalues, count, sbuffer.taintvalues, sbuffer.count, capacity());
			count = newSize;
		}
		return this;
	}

	/**
	 * Copies a range of characters into a new String.
	 * 
	 * @param start
	 *            the offset of the first character
	 * @param end
	 *            the offset one past the last character
	 * @return a new String containing the characters from start to end - 1
	 * 
	 * @exception IndexOutOfBoundsException
	 *                when <code>start < 0, start > end</code> or
	 *                <code>end > length()</code>
	 * 
	 * @since 1.4
	 */
	public CharSequence subSequence(int start, int end) {
		return substring(start, end);
	}

	/**
	 * Searches in this StringBuffer for the first index of the specified
	 * character. The search for the character starts at the beginning and moves
	 * towards the end.
	 * 
	 * @param string
	 *            the string to find
	 * @return the index in this StringBuffer of the specified character, -1 if
	 *         the character isn't found
	 * 
	 * @see #lastIndexOf(String)
	 * 
	 * @since 1.4
	 */
	public int indexOf(String string) {
		return indexOf(string, 0);
	}

	/**
	 * Searches in this StringBuffer for the index of the specified character.
	 * The search for the character starts at the specified offset and moves
	 * towards the end.
	 * 
	 * @param subString
	 *            the string to find
	 * @param start
	 *            the starting offset
	 * @return the index in this StringBuffer of the specified character, -1 if
	 *         the character isn't found
	 * 
	 * @see #lastIndexOf(String,int)
	 * 
	 * @since 1.4
	 */
	public synchronized int indexOf(String subString, int start) {
		if (start < 0)
			start = 0;
		int subCount = subString.length();
		if (subCount > 0) {
			if (subCount + start > count)
				return -1;
			char firstChar = subString.charAt(0);
			while (true) {
				int i = start;
				boolean found = false;
				for (; i < count; i++)
					if (value[i] == firstChar) {
						found = true;
						break;
					}
				if (!found || subCount + i > count)
					return -1; // handles subCount > count || start >= count
				int o1 = i, o2 = 0;
				while (++o2 < subCount && value[++o1] == subString.charAt(o2))
					;
				if (o2 == subCount)
					return i;
				start = i + 1;
			}
		} else
			return (start < count || start == 0) ? start : count;
	}

	/**
	 * Searches in this StringBuffer for the last index of the specified
	 * character. The search for the character starts at the end and moves
	 * towards the beginning.
	 * 
	 * @param string
	 *            the string to find
	 * @return the index in this StringBuffer of the specified character, -1 if
	 *         the character isn't found
	 * 
	 * @see #indexOf(String)
	 * 
	 * @since 1.4
	 */
	public synchronized int lastIndexOf(String string) {
		return lastIndexOf(string, count);
	}

	/**
	 * Searches in this StringBuffer for the index of the specified character.
	 * The search for the character starts at the specified offset and moves
	 * towards the beginning.
	 * 
	 * @param subString
	 *            the string to find
	 * @param start
	 *            the starting offset
	 * @return the index in this StringBuffer of the specified character, -1 if
	 *         the character isn't found
	 * 
	 * @see #indexOf(String,int)
	 * 
	 * @since 1.4
	 */
	public synchronized int lastIndexOf(String subString, int start) {
		int subCount = subString.length();
		if (subCount <= count && start >= 0) {
			if (subCount > 0) {
				if (start > count - subCount)
					start = count - subCount; // count and subCount are both >=
												// 1
				char firstChar = subString.charAt(0);
				while (true) {
					int i = start;
					boolean found = false;
					for (; i >= 0; --i)
						if (value[i] == firstChar) {
							found = true;
							break;
						}
					if (!found)
						return -1;
					int o1 = i, o2 = 0;
					while (++o2 < subCount
							&& value[++o1] == subString.charAt(o2))
						;
					if (o2 == subCount)
						return i;
					start = i - 1;
				}
			} else
				return start < count ? start : count;
		} else
			return -1;
	}

	/*
	 * Returns the character array for this StringBuffer.
	 */
	char[] getValue() {
		return value;
	}

	/**
	 * Constructs a new StringBuffer containing the characters in the specified
	 * CharSequence and the default capacity.
	 * 
	 * @param sequence
	 *            the initial contents of this StringBuffer
	 * @exception NullPointerException
	 *                when squence is null
	 * 
	 * @since 1.5
	 */
	public StringBuffer(CharSequence sequence) {
		int size = sequence.length();
		if (size < 0)
			size = 0;
		value = new char[size + 16];
		if (sequence instanceof String) {
			// optimize String for speed
			append((String) sequence);
			return;
		} else if (sequence instanceof StringBuffer) {
			// optimize StringBuffer for synchronization, and speed
			append((StringBuffer) sequence);
			return;
		}
		count = size;
		for (int i = 0; i < size; i++)
			value[i] = sequence.charAt(i);
		logs[2]++;
	}

	/**
	 * Adds the specified CharSequence to the end of this StringBuffer.
	 * 
	 * @param sequence
	 *            the CharSequence
	 * @return this StringBuffer
	 * 
	 * @since 1.5
	 */
	public synchronized StringBuffer append(CharSequence sequence) {
		if (sequence == null)
			return append(String.valueOf(sequence));
		if (sequence instanceof String) {
			// optimize String for speed
			return append((String) sequence);
		} else if (sequence instanceof StringBuffer) {
			// optimize StringBuffer for synchronization, and speed
			return append((StringBuffer) sequence);
		} else if (sequence instanceof StringBuilder) {
			// optimize StringBuilder for speed
			StringBuilder builder = (StringBuilder) sequence;
			return append(builder.getValue(), 0, builder.length());
		}
		int newSize = count + sequence.length();
		if (newSize > count) {
			if (newSize > value.length) {
				ensureCapacityImpl(newSize);
			}
			for (int i = 0; i < sequence.length(); i++)
				value[count + i] = sequence.charAt(i);
			count = newSize;
			logs[3]++;
		}
		return this;
	}

	/**
	 * Adds the specified CharSequence to the end of this StringBuffer.
	 * 
	 * @param sequence
	 *            the CharSequence
	 * @param start
	 *            the offset of the first character
	 * @param end
	 *            the offset one past the last character
	 * @return this StringBuffer
	 * 
	 * @exception IndexOutOfBoundsException
	 *                when <code>start < 0, start > end</code> or
	 *                <code>end > length()</code>
	 * 
	 * @since 1.5
	 */
	public synchronized StringBuffer append(CharSequence sequence, int start,
			int end) {
		if (sequence == null)
			return append(String.valueOf(sequence), start, end);
		if (sequence instanceof String) {
			// optimize String for speed
			return append(((String) sequence).substring(start, end));
		}
		if (start >= 0 && end >= 0 && start <= end && end <= sequence.length()) {
			if (sequence instanceof StringBuffer) {
				// optimize StringBuffer for synchronization, and speed
				synchronized (sequence) {
					return append(((StringBuffer) sequence).value, start, end
							- start);
				}
			} else if (sequence instanceof StringBuilder) {
				// optimize StringBuilder for speed
				return append(((StringBuilder) sequence).getValue(), start, end
						- start);
			}
			int len = end - start;
			if (len > 0) {
				int newSize = count + len;
				if (newSize > value.length) {
					ensureCapacityImpl(newSize);
				}
				for (int i = 0; i < len; i++)
					value[count + i] = sequence.charAt(start + i);
				count = newSize;
				logs[4]++;
			}
		} else
			throw new IndexOutOfBoundsException();
		return this;
	}

	/**
	 * Inserts the CharSequence at the specified offset in this StringBuffer.
	 * 
	 * @param index
	 *            the index at which to insert
	 * @param sequence
	 *            the CharSequence to insert
	 * @return this StringBuffer
	 * 
	 * @exception IndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index > length()</code>
	 * 
	 * @since 1.5
	 */
	public synchronized StringBuffer insert(int index, CharSequence sequence) {
		if (index >= 0 && index <= count) {
			if (sequence == null)
				return insert(index, String.valueOf(sequence));
			if (sequence instanceof String) {
				// optimize String for speed
				return insert(index, (String) sequence);
			} else if (sequence instanceof StringBuffer) {
				// optimize StringBuffer for synchronization, and speed
				synchronized (sequence) {
					return insert(index, ((StringBuffer) sequence).value, 0,
							((StringBuffer) sequence).count);
				}
			} else if (sequence instanceof StringBuilder) {
				// optimize StringBuilder for speed
				StringBuilder builder = (StringBuilder) sequence;
				return insert(index, builder.getValue(), 0, builder.length());
			}
			int min = sequence.length();
			if (min > 0) {
				move(min, index);
				for (int i = 0; i < sequence.length(); i++)
					value[index + i] = sequence.charAt(i);
				count += min;
				logs[5]++;
			}
		} else
			throw new IndexOutOfBoundsException();
		return this;
	}

	/**
	 * Inserts the CharSequence at the specified offset in this StringBuffer.
	 * 
	 * @param index
	 *            the index at which to insert
	 * @param sequence
	 *            the CharSequence to insert
	 * @param start
	 *            the offset of the first character
	 * @param end
	 *            the offset one past the last character
	 * @return this StringBuffer
	 * 
	 * @exception IndexOutOfBoundsException
	 *                when <code>index < 0</code> or
	 *                <code>index > length()</code>, or when
	 *                <code>start < 0, start > end</code> or
	 *                <code>end > length()</code>
	 * 
	 * @since 1.5
	 */
	public synchronized StringBuffer insert(int index, CharSequence sequence,
			int start, int end) {
		if (index >= 0 && index <= count) {
			if (sequence == null)
				return insert(index, String.valueOf(sequence), start, end);
			if (sequence instanceof String) {
				// optimize String for speed
				return insert(index, ((String) sequence).substring(start, end));
			}
			if (start >= 0 && end >= 0 && start <= end
					&& end <= sequence.length()) {
				if (sequence instanceof StringBuffer) {
					// optimize StringBuffer for synchronization, and speed
					synchronized (sequence) {
						return insert(index, ((StringBuffer) sequence).value,
								start, end - start);
					}
				} else if (sequence instanceof StringBuilder) {
					// optimize StringBuilder for speed
					return insert(index, ((StringBuilder) sequence).getValue(),
							start, end - start);
				}
				int min = end - start;
				if (min > 0) {
					move(min, index);
					for (int i = 0; i < min; i++)
						value[index + i] = sequence.charAt(start + i);
					count += min;
					logs[6]++;
				}
				return this;
			}
		}
		throw new IndexOutOfBoundsException();
	}

	/**
	 * Optionally modify the underlying char array to only be large enough to
	 * hold the characters in this StringBuffer.
	 * 
	 * @since 1.5
	 */
	public synchronized void trimToSize() {
		if (!shared && value.length != count) {
			char[] newValue = new char[count];
			System.arraycopy(value, 0, newValue, 0, count);
			value = newValue;
		}
	}

	/**
	 * Returns the Unicode character at the given point.
	 * 
	 * @param index
	 *            the character index
	 * @return the Unicode character value at the index
	 * 
	 * @since 1.5
	 */
	public synchronized int codePointAt(int index) {
		if (index >= 0 && index < count) {
			int high = value[index];
			if ((index + 1) < count && high >= 0xd800 && high <= 0xdbff) {
				int low = value[index + 1];
				if (low >= 0xdc00 && low <= 0xdfff)
					return 0x10000 + ((high - 0xd800) << 10) + (low - 0xdc00);
			}
			return high;
		} else
			throw new StringIndexOutOfBoundsException(index);
	}

	/**
	 * Returns the Unicode character before the given point.
	 * 
	 * @param index
	 *            the character index
	 * @return the Unicode character value before the index
	 * 
	 * @since 1.5
	 */
	public synchronized int codePointBefore(int index) {
		if (index > 0 && index <= count) {
			int low = value[index - 1];
			if (index > 1 && low >= 0xdc00 && low <= 0xdfff) {
				int high = value[index - 2];
				if (high >= 0xd800 && high <= 0xdbff)
					return 0x10000 + ((high - 0xd800) << 10) + (low - 0xdc00);
			}
			return low;
		} else
			throw new StringIndexOutOfBoundsException(index);
	}

	/**
	 * Returns the total Unicode values in the specified range.
	 * 
	 * @param start
	 *            first index
	 * @param end
	 *            last index
	 * @return the total Unicode values
	 * 
	 * @since 1.5
	 */
	public synchronized int codePointCount(int start, int end) {
		if (start >= 0 && start <= end && end <= count) {
			int count = 0;
			for (int i = start; i < end; i++) {
				int high = value[i];
				if (i + 1 < end && high >= 0xd800 && high <= 0xdbff) {
					int low = value[i + 1];
					if (low >= 0xdc00 && low <= 0xdfff)
						i++;
				}
				count++;
			}
			return count;
		} else
			throw new IndexOutOfBoundsException();
	}

	/**
	 * Returns the index of the code point that was offset by
	 * <code>codePointCount</code>.
	 * 
	 * @param start
	 *            the position to offset
	 * @param codePointCount
	 *            the code point count
	 * @return the offset index
	 * 
	 * @since 1.5
	 */
	public synchronized int offsetByCodePoints(int start, int codePointCount) {
		if (start >= 0 && start <= count) {
			int index = start;
			if (codePointCount == 0)
				return start;
			else if (codePointCount > 0) {
				for (int i = 0; i < codePointCount; i++) {
					if (index == count)
						throw new IndexOutOfBoundsException();
					int high = value[index];
					if ((index + 1) < count && high >= 0xd800 && high <= 0xdbff) {
						int low = value[index + 1];
						if (low >= 0xdc00 && low <= 0xdfff)
							index++;
					}
					index++;
				}
			} else {
				for (int i = codePointCount; i < 0; i++) {
					if (index < 1)
						throw new IndexOutOfBoundsException();
					int low = value[index - 1];
					if (index > 1 && low >= 0xdc00 && low <= 0xdfff) {
						int high = value[index - 2];
						if (high >= 0xd800 && high <= 0xdbff)
							index--;
					}
					index--;
				}
			}
			return index;
		} else
			throw new IndexOutOfBoundsException();
	}

	/**
	 * Adds the specified code point to the end of this StringBuffer.
	 * 
	 * @param codePoint
	 *            the code point
	 * @return this StringBuffer
	 * 
	 * @since 1.5
	 */
	public synchronized StringBuffer appendCodePoint(int codePoint) {
		if (codePoint >= 0) {
			if (codePoint < 0x10000) {
				return append((char) codePoint);
			} else if (codePoint < 0x110000) {
				if (count + 2 > value.length) {
					ensureCapacityImpl(count + 2);
				}
				codePoint -= 0x10000;
				value[count] = (char) (0xd800 + (codePoint >> 10));
				value[count + 1] = (char) (0xdc00 + (codePoint & 0x3ff));

				TaintSet.set(taintvalues, count, false);
				TaintSet.set(taintvalues, count + 1, false);

				count += 2;
				return this;
			}
		}
		throw new IllegalArgumentException();
	}

	public void setTaintarr() {
		taintvalues = TaintSet.generate(String.generateTaintarr(count));
	}

}
