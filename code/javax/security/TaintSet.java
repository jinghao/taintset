package javax.security;
// taintbits is NEVER modified outside of generate(), realloc(), and set() (generate() + functions accessed in builder/buffer)
// offset < 0 only if we don't modify contents of taintbits
// make sure getFirstTaint() + length <= length of parent string
public final class TaintSet {
	// [offset, offset + length) bits of 'value' could be tainted
	private int offset; // the position of the first possibly tainted char indexed from string's offset
	private int length; // number of characters after "start" that could be tainted; must be positive
	private int[] taintbits;
	
	/**
	 * constants for the program. hopefully compiled in.
	 * idioms frequently used in the program use the following values
	 * Examples:
	 * 	x >> cellbits == x / cellsize
	 * 	x & cellmax == x % cellsize
	 * 	1 << cellbits == cellsize
	 */
	
	final static byte cellsize = 32;
	final static byte cellbits = 5;
	final static byte cellmax = cellsize - 1; // 31
	
	public final static TaintSet allTainted = new TaintSet(0, Integer.MAX_VALUE, null);
	
	private TaintSet() {
		// System.out.println("Constructing TaintSet");
	}

	/**
	 * Initializes a taint set for a substring starting at offset with length maxlen
	 * @param set TaintSet of the string that is being substringed
	 * @param offset The index of the original string that the new string begins on
	 * @param maxlen The length of the resultant substring
	 */
	
	public TaintSet(int offset, int length, int[] taintbits) {
		this.offset = offset;
		this.length = length;
		this.taintbits = taintbits;
	}
	
	/*************************
	 * Static Factory Methods
	 *************************/
	
	/**
	 * Combines two strings whose TaintSet objects are b1 and b2 and string lengths are len1 and len2
	 * destructive is true iff you want to only modify b1 (as in Buffer/Builder.append)
	 * capacity is used only when it's destructive
	 */

	private static TaintSet generate(TaintSet b1, int len1, final TaintSet b2, int len2, int capacity) {
		if (b1 != null) {
			// b1 tainted
			if (b2 != null) {
				// both tainted
				
				// both fully tainted, then return allTainted
				if (b1.length >= len1 && b1.offset == 0 && b1.singleInterval()) {
					if (b2.length >= len2 && b2.offset == 0 && b2.singleInterval()) {
						return allTainted;
					}
					
					// if b1 is allTainted then make a new set--don't modify b1
					capacity = 0;
				}
				
				// at least one has an untainted portion
				TaintSet set;
				int b1start = b1.getFirstTaint();
				
				if (capacity > 0) {
					set = b1;
				} else {
					set = new TaintSet();
					set.offset = b1start;
				}
				
				int b1len = len1 - b1start; // length from the first taint to the end of string 1
				int prevb1len = Math.min(b1.length, b1len);
				set.length = b1len + Math.min(b2.getLastTaint(), len2);
				
				// must merge/create taintbits (not a single interval)
				if (b1len > prevb1len || b2.offset > 0 || b1.taintbits != null || b2.taintbits != null) {
					if (capacity == 0) {
						set.taintbits = new int[numCells(set.length)];
						set.fill(0, b1, prevb1len);
					} else if(b1.taintbits == null) {
						// b1 is a single interval, need to convert to bitmap
						set.taintbits = new int[numCells(capacity - set.offset)];
						set.setTainted(b1start, b1start + prevb1len);
					}
					
					set.fill(len1, b2, len2 - b2.getFirstTaint()); // set = set union (b2 + len1)
				}
				
				return set;
			} else {
				// b1 tainted; b2 untainted
				
				// don't want set from b1 to spill over to the new set
				if (b1.getLastTaint() > len1) {
					return new TaintSet(b1.offset, len1 - b1.getFirstTaint(), b1.taintbits);
				} else {
					return b1;
				}
			}
		} else if (b2 != null) {
			// b1 untainted; b2 tainted
			return new TaintSet(len1 + b2.offset, b2.getLength(len2), b2.taintbits);
		} else {
			// both untainted
			return null;
		}
	}
	
	/**
	 * Produce the resultant of concatenating strings S1 and S2
	 * whose TaintSet objects are b1 and b2
	 * 
	 * It creates a single interval (singleInterval()) if possible.
	 * Otherwise it creates a new bitset and populates it using b1 and b2
	 * 
	 * Returns null if both sets are empty
	 * 
	 * @param b1 TaintSet object from the first string
	 * @param len1 Length of the first string
	 * @param b2 TaintSet object from the second string
	 * @return resultant TaintSet, null if no taint
	 */
	
	public static TaintSet generate(final TaintSet b1, int len1, final TaintSet b2, int len2) {
		return generate(b1, len1, b2, len2, 0);
	}
	
	public static TaintSet generate(final TaintSet b1, int len1, final TaintSet b2) {
		return generate(b1, len1, b2, Integer.MAX_VALUE, 0);
	}
	
	/**
	 * Used to substring a string whose TaintSet is 'set'. The substring is [beginIndex, endIndex)
	 * @param set
	 * @param beginIndex
	 * @param endIndex
	 * @return
	 */
	public static TaintSet generate(TaintSet set, int beginIndex, int endIndex) {
		// start off with two special, extremely-common cases
		if (set == null || set == allTainted) {
			return set;
		} else {
			int length = set.overlapLength(beginIndex, endIndex);
			
			// If there's no overlap then clearly there's no taint
			if (length <= 0) {
				return null;
			}
			
			int offset = set.offset - beginIndex;
			
			if (offset <= 0 && set.singleInterval()) {
				if (endIndex <= set.getLastTaint()) {
					return allTainted;
				}
				
				offset = 0; // same as offset = Math.max(offset, 0)
			}
			
			return new TaintSet(offset, length, set.taintbits);
		}
	}
	
	/**
	 * Produce a set according to 'taint', where the first element of 'taint'
	 * corresponds to the 'beginIndex' character of the parent string, up to endIndex
	 * 
	 * @param taint boolean array whose values are true where the characters are tainted
	 * @param beginIndex Index of the character of the parent string that taint[0] corresponds to
	 * @param endIndex Index of the character of the parent string that is the exclusive end of all taints
	 * @return resultant TaintSet, null if no taint
	 */
	
	public static TaintSet generate(boolean[] taint, int beginIndex, int endIndex) {
		if (taint != null) {
			TaintSet set = new TaintSet();
			if (set.initializeTo(taint, beginIndex, endIndex)) {
				return set;
			}
		}
		
		return null;
	}
	
	/**
	 * Produce a set completely from 'taint' (equivalent of a zero-offset taint array from v1)
	 * @param taint Boolean array specifying which characters are tainted
	 * @return resultant TaintSet, null if no taint
	 */
	
	public static TaintSet generate(boolean[] taint) {
		return generate(taint, 0, -1);
	}
	
	/**
	 * Produce a set where [0, len) of the parent string is tainted
	 * @param len Length of the left-aligned taint
	 * @return resultant TaintSet, null if no taint
	 */
	
	public static TaintSet generate(int len) {
		return generate(0, len);
	}
	
	/**
	 * Produce a set where [start, end) of the parent string is tainted
	 * @param start Start of tainting: Values before this are untainted
	 * @param end End of tainting: Values after and including this are untainted
	 * @return resultant TaintSet, null if no taint
	 */
	
	public static TaintSet generate(int start, int end) {
		return start < end ? new TaintSet(start, end - start, null) : null;
	}
	
	// Skip comparison if guaranteed start < end
	public static TaintSet guardedGenerate(int start, int end) {
		return new TaintSet(start, end - start, null);
	}
	
	// FOR TESTING -------------------------------------------
	
	public void printTaintBits() {
		System.out.printf("offset = %d, length = %d\n", offset, length);
		if(taintbits != null) {
			for(int i = 0; i < taintbits.length; i++) {
				System.out.printf("\t%3d: %s\n", i, Integer.toBinaryString(taintbits[i]));
			}
		}
	}
	
	public String toString(int len) {
		boolean[] taintarr = getTaintArray(len);
		char[] taintstr = new char[len];
		for(int i = 0; i < len; i++) {
			taintstr[i] = taintarr[i] ? '1' : '0';
		}
		return new String(taintstr);
	}
	
	public String toString() {
		return toString(length + getFirstTaint());
	}
	
	public boolean equals(Object o) {
		TaintSet set = (TaintSet) o;
		if (set.length != length) {
			return false;
		}

		if (length == Integer.MAX_VALUE) {
			return set.taintbits == null && this.taintbits == null;
		}
		
		return set.toString().equals(toString());
	}
	
	public static boolean[] getTaintArray(TaintSet set, int len) {
		return set == null ? new boolean[len] : set.getTaintArray(len);
	}
	
	// END FOR TESTING -------------------------------------------

	/**
	 * Returns a zero-offset boolean array whose values map each character of the parent string, by index, to whether it is tainted
	 * @param len Length of the parent string, and thus the length of the resultant boolean taint array
	 * @return a zero-offset boolean array whose values map each character of the parent string, by index, to whether it is tainted
	 */
	
	public boolean[] getTaintArray(int len) {
		boolean[] taintarr = new boolean[len];
		
		for(int i = 0; i < len; i++) {
			taintarr[i] = isTaintedAt(i);
		}
		
		
		return taintarr;
	}
	
	public static TaintSet makeCopy(TaintSet set) {
		if (set == null) {
			return null;
		} else {
			if (set.offset < 0) {
				// assert: set.taintbits != null
				return new TaintSet(0, set.length, new int[numCells(set.length)]).fill(0, set);
			} else {
				return new TaintSet(set.offset, set.length, set.taintbits == null ? null : set.taintbits.clone());
			}
		}
	}
	
	/**
	 * Calculate the maximum number of characters this object can track without reallocating the bitmap.
	 * 
	 * @return Integer.MAX_VALUE if this is single interval (infinitely expandable); otherwise offset + cellsize * taintbits.length
	 */
	
	public int maxSize() {
		return taintbits == null ? Integer.MAX_VALUE : offset + cellsize * taintbits.length;
	}
	
	/**
	 * Ensures set.maxSize() >= size. If that's not the case, reallocates bitmap.
	 * @param set
	 * @param size
	 * @return
	 */
	
	public static TaintSet ensureCapacity(TaintSet set, int size) {
		if (set == null || size <= set.getLastTaint() || size <= set.maxSize()) {
			return set;
		}

		TaintSet set2 = new TaintSet(set.offset, set.length, new int[numCells(size - set.offset)]);
		set2.fill(0, set);

		return set2;
	}
	
	/**
	 * Calls destructive version of generate for concat
	 * @param b1
	 * @param len1
	 * @param b2
	 * @param len2
	 * @param capacity
	 * @return
	 */
	
	public static TaintSet append(TaintSet b1, int len1, final TaintSet b2, int len2, int capacity) {
		return generate(ensureCapacity(b1, capacity), len1, b2, len2, capacity);
	}
	
	public static TaintSet replace(TaintSet set, int start, int end, int length, TaintSet replacement) {
		if (end <= start) {
			return set;
		} else if (set == null) {
			return replacement == null ? null : new TaintSet(replacement.offset + start, replacement.length, replacement.taintbits);
		} else {
			// TODO: Hackish implementation. Concatenating substrings.
			return TaintSet.generate(
					TaintSet.generate(
							TaintSet.generate(set, 0, start),
							start,
							replacement,
							length + start,
							set.getLength() + length),
						length + start,
						TaintSet.generate(set, end, set.getLastTaint()),
						Integer.MAX_VALUE,
						set.getLength() + length
					);
		}
	}
	
	/**
	 * Destructively moves all taints starting at <code>index</code> by <code>size</code> (Used for buffer/builder.insert)
	 * @param set
	 * @param size
	 * @param index	must be non-negative
	 * @return
	 */
	
	public static TaintSet move(TaintSet set, int size, int index) {
		
		assert(index >= 0);
		
		if (set == null || index >= set.getLastTaint() || size == 0) {
			return set;
		} else if (index > set.offset) { // split
			set.length += size;
			
			if (set.taintbits == null) {
				// convert to bitset
				set.taintbits = new int[numCells(set.length)];
				set.setTainted(set.offset, index);
				set.setTainted(index + size, set.getLastTaint());
			} else {
				// TODO
				set = TaintSet.generate(
						TaintSet.generate(set, 0, index),
						index + size,
						TaintSet.generate(set, index, set.getLastTaint())
					);
			}
		} else {
			set.offset += index;
		}
		
		return set;
	}
	
	/**
	 * Destructive: Sets set = Union(b + offset, set), but (b + offset) must be within set (in range)
	 * 
	 * Range of where b fits is empty
	 * @param set
	 * @param offset
	 * @param b
	 * @return
	 */
	
	public static TaintSet union(TaintSet set, int offset, TaintSet b) {
		if (b != null) {
			// TODO: b might be allTainted
			if (set == null) {
				set = offset == 0 ? b : new TaintSet(b.offset + offset, b.length, b.taintbits);
			} else {
				int new_offset = b.getFirstTaint() + offset;
				int old_length = set.length;
				int old_offset = set.offset;
				
				if (new_offset < set.offset) {
					set.offset = new_offset;
				}
				
				set.length = Math.max(new_offset + b.length, set.offset + set.length) - Math.min(new_offset, set.offset);
				
				// both b and set exist
				if (set.taintbits == null) {
					// TODO: Figure out what I meant by "not fully immersed"; b.length == 0?
					if (set.length > old_length) {
						set.taintbits = new int[numCells(set.length)];
						set.setTainted(old_offset, old_offset + old_length);
						set.fill(offset, b);
					}
				} else {
					set.fill(offset, b);
				}
			}
		}
		return set;
	}
	
	/**
	 * If <code>set</code> is a bitmap and <code>position</code> is within the range of set.taintbits, then set the bit corresponding to the position to value
	 * 
	 * Expands or contracts set.length if necessary.
	 * 
	 * Must call ensureCapacity before; otherwise not guaranteed to work
	 * 
	 * @param set
	 * @param position
	 * @param value
	 */
	
	public static void set(TaintSet set, int position, boolean value) {
		if (set != null && set.taintbits != null && position >= set.getFirstTaint()) {
			position -= set.offset; // position is now the bit offset within taintbits
			
			if (position >= 0 && position < set.taintbits.length * cellsize) {
				if (value) {
					if (position >= set.length) {
						set.length = position + 1;
					}
					
					set.add(position);
				} else {
					if (position + 1 == set.length) {
						set.length = position;
					}
					
					set.remove(position);
				}
			}
		}
	}
	
	/**
	 * Tests whether the char at 'position' relative to the start of the parent string is tainted
	 * @param position index of character of parent string to test
	 * @return true if 'position' is set; false otherwise (or out of range)
	 */
	
	public boolean isTaintedAt(int position) {
		int first = getFirstTaint(); // index relative to first taint
		
		return (position >= first && position < first + length && (singleInterval() || contains(position - offset)));
	}
	
	/**
	 * Tests whether [start, end) overlaps with the taint interval [offset, offset + length)
	 * @param start
	 * @param end
	 * @return
	 */
	
	public boolean overlapsWith(int start, int end) {
		return overlapLength(start, end) > 0;
	}
	
	/**
	 * Computes the length of overlap between [start, end) and the taint interval [offset, offset + length)
	 * @param start
	 * @param end
	 * @return length of overlap
	 */
	
	public int overlapLength(int start, int end) {
		int firsttaint = getFirstTaint();
		return Math.min(end, firsttaint + length) - Math.max(start, firsttaint);
	}
	
	public boolean hasTaintBetween(int start, int end) {
		return overlapsWith(start, end) // the intervals overlap
			&& (singleInterval() || !emptySet(start, end)); // single interval or not an empty set between start and end
	}
	
	/**
	 * Computes the index of the first possibly tainted char in the parent string
	 * Not guaranteed to correspond to an untainted char
	 * @return index of the first possibly tainted char in the parent string
	 */

	public int getFirstTaint() {
		return offset > 0 ? offset : 0;
	}
	
	/**
	 * Computes the index of the last possibly tainted char in the parent string
	 * Not guaranteed to correspond to an untainted char
	 * @return index of the last tainted char in the parent string
	 */
	
	public int getLastTaint() {
		return getFirstTaint() + length;
	}
	
	/**
	 * Maximum length of the taint
	 * @return maximum length of the taint
	 */
	
	public int getLength() {
		return length;
	}
	
	/**
	 * Exact length of the taint, given string length
	 * @return length of the taint
	 */
	
	public int getLength(int strlen) {
		return Math.min(length, strlen - getFirstTaint());
	}

	/**
	 * @return	whether this is using the single interval representation
	 */
	
	public boolean singleInterval() {
		return taintbits == null;
	}
	/*
	public static TaintSet reversed(TaintSet set, int len) {
		if (set == null) {
			return null;
		} else if (set.taintbits == null) {
			return new TaintSet(len - set.offset - set.length, set.length, null);
		} else {
			boolean[] taintarr = set.getTaintArray(len);
			
		}
	}*/
	
	/**
	 * 
	 * @param taint
	 * @param start
	 * @param end
	 * @return allTrue(taint, start, end) if taint != null, false otherwise
	 */
	private boolean initializeTo(boolean[] taint, int start, int end) {
		if (taint == null) {
			return false;
		}
		
		if (end < 0) {
			end = taint.length;
		}
		
		if (start < 0 || start >= end || end > taint.length) {
			return false;
		}
		
		// squeeze start
		while (start < end && !taint[start]) {
			start++;
		}
		// start == end XOR (end > start AND taint[start] == true)
		
		// squeeze end
		do {
			end--;
		} while (end >= start && !taint[end]);
		end++;
		// end == start XOR (end > start AND taint[end - 1] == true)
		
		// end == start XOR (end > start AND taint[end - 1] == true AND taint[start] == true)
		
		if (end == start) {
			return false;
		}
		
		// end > start AND taint[end - 1] == true AND taint[start] == true
		
		length = end - start;
		offset = start;
		
		// must create a bitset from boolean array
		if (!allTrue(taint, start, end)) {
			taintbits = new int[numCells(length)];
			
			for (int i = start; i < end; i++) {
				if (taint[i]) {
					add(i - start);
				}
			}
		}
		
		return true;
	}

	// set the entire [start, end) to true
	private TaintSet setTainted(int start, int end) {
		// get the taintbit bit offset
		int start_bits = start & cellmax;
		int end_bits = end & cellmax;
		
		// now that we have start_bits/end_bits we only need the cell offset
		start = start >> cellbits;
		end = (end - 1) >> cellbits;

		if (start == end) { // only one word to fill
			taintbits[start] |= ~0 >>> start_bits & ~0 << (cellsize - end_bits);
		} else if (start < end) {
			taintbits[start] |= ~0 >>> start_bits;
			
			for (int i = start + 1; i < end; i++) {
				taintbits[i] = ~0;
			}
			
			taintbits[end] |= ~0 << (cellsize - end_bits);
		}
		
		return this;
	}

	// UNION 'set', shifted to the right by 'offset', into 'taintbits'
	private TaintSet fill(int offset, TaintSet set) {
		// assert(set.length > 0 && set.taintbits != null && set.length < Integer.MAX_VALUE);

		// compute information for the start of the source taintbits array
		int start_src = set.getTaintOffset();
		int start_src_bits = start_src & cellmax;
		
		// compute information for the end of the source taintbits array
		int end_src = start_src + set.length;
		int end_src_bits = end_src & cellmax;
		
		// start_dest - start_src
		int difference = (set.getFirstTaint() + offset - this.offset) - start_src; // diff = dest - src => src + diff = dest // TODO: explain better
		int difference_bits = difference & cellmax;
		
		// we have the _bits; we only need the cell indices now
		end_src = (end_src - 1) >> cellbits; // very last source "cell"
		start_src >>= cellbits; // very first source "cell"
		difference >>= cellbits;
		
		if (difference_bits == 0) {
			// fill but don't rotate:
			// Special case because it is done at least half the time when concatenating two strings,
			// the first of which has not been substringed (offset non-negative)
			
			// only one cell to copy. easy
			if (end_src == start_src) {
				taintbits[start_src + difference] |= set.taintbits[start_src] &
					(~0 >>> start_src_bits) & (~0 << (cellsize - end_src_bits)); // mask: >= start_src_bits AND < end_src_bits
			} else {
				// copy the relevant portions of the start cell (by masking out the first start_src_bits bits)
				taintbits[start_src + difference] |= set.taintbits[start_src] & ~0 >>> start_src_bits;
					
				// copy the relevant portions of the second cell (by masking out all but the first end_src_bits bits)
				taintbits[end_src + difference] |= set.taintbits[end_src] & ~0 << (cellsize - end_src_bits);

				// copy set.taintbits[start_src + 1 ... end_src - 1] to taintbits[start_src + difference + 1 ... end_src + difference - 1]
				while (start_src++ < end_src) {
					taintbits[start_src + difference] = set.taintbits[start_src];
				}
			}
		} else {
			int bit_offset_neg = cellsize - difference_bits;

			int start_cell = set.taintbits[start_src] & ~0 >>> start_src_bits;
			int end_cell = set.taintbits[end_src] & ~0 << (cellsize - end_src_bits);
			
			if ((start_cell >>> difference_bits) != 0) {
				taintbits[start_src + difference] |= start_cell >>> difference_bits; // copy over the first part of start_cell
			}
			
			if (start_src != end_src) {
				taintbits[start_src + difference + 1] |= start_cell << bit_offset_neg; // copy over the second part of start_cell
				
				for (int i = start_src + 1; i < end_src; i++) {
					taintbits[i + difference] |= set.taintbits[i] >>> difference_bits; // copy over the first part of set.taintbits[i] to taintbits[i + difference]
					taintbits[i + difference + 1] |= set.taintbits[i] << bit_offset_neg; // copy over the second part of set.taintbits[i] to taintbits[i + difference + 1]
				}

				taintbits[end_src + difference] |= end_cell >>> difference_bits; // copy over the first part of end_cell
			}
			
			if ((end_cell << bit_offset_neg) != 0) {
				taintbits[end_src + difference + 1] |= end_cell << bit_offset_neg; // copy over the second part of end_cell
			}
		}
		
		return this;
	}
	
	/**
	 * Unions set + offset into 'this'
	 * @param offset
	 * @param set
	 * @param maxlen
	 * @return
	 */
	
	private TaintSet fill(int offset, TaintSet set, int maxlen) {
		if (set.length == 0) {
			return null;
		}
		
		if (set.taintbits == null) {
			offset += set.offset - this.offset;
			return setTainted(offset, offset + Math.min(set.length, maxlen));
		} else {
			return fill(offset, set);
		}
		
	}
	
	private boolean emptySet(int start, int end) {		
		return emptySet(taintbits, Math.max(start - offset, 0), Math.max(end - offset, 0));
	}
	
	
	/*****************************************************************************
	 * The behaviors for the following methods are undefined when singleInterval()
	 * If they are ever called when singleInterval(), there is a bug,
	 * since they are private methods
	 *****************************************************************************/
	
	// index in taintbits that contains the first relevant taint bit
	private int getTaintOffset() {
		return offset < 0 ? -offset : 0;
	}
	
	// Set bit *position - offset* to true
	private void add(int position) {
		taintbits[position >>> cellbits] |= 0x80000000 >>> (position & cellmax);
	}
	
	// Set bit *position - offset* to false
	private void remove(int position) {
		taintbits[position >>> cellbits] &= ~(0x80000000 >>> (position & cellmax));
	}
	
	// Tests bit *position - offset*
	private boolean contains(int position) {
		return (taintbits[position >>> cellbits] & (0x80000000 >>> (position & cellmax))) != 0;
	}

	/*****************************************************************************
	 * Helper methods
	 *****************************************************************************/
	
	// True iff taint[start .. end-1] are all true, or represents empty interval
	// Warning: doesn't check bounds
	private static boolean allTrue(boolean[] taint, int start, int end) {
		for (int i = start; i < end; i++) {
			if (!taint[i]) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * @return	Number of integers needed to store <code>len</code> bits
	 */
	
	private static int numCells(int len) {
		return (len + cellmax) >>> cellbits;
	}
	
	/**
	 * Determines whether bits start .. end-1 are empty (all off)
	 * @param taintbits
	 * @param start
	 * @param end
	 * @return true iff bits start .. end-1 in taintbits are 0
	 */
	
	private static boolean emptySet(int[] taintbits, int start, int end) {
		int end_bits = end & cellmax;
		int start_bits = start & cellmax;
		end >>= cellbits;
		start = (start + cellmax) >> cellbits;
		
		// check whole portions first since they're easy
		for (int i = start; i < end; i++) {
			if (taintbits[i] != 0) {
				return false;
			}
		}
		
		// start_bits != 0 so first cell (not examined above) must be checked
		if (start_bits > 0 && taintbits[start - 1] != 0 && (taintbits[start - 1] << start_bits) != 0) {
			return false;
		}
		
		// TODO: double check
		if (end_bits > 0 && taintbits[end] != 0 && (taintbits[end] >>> (cellsize - start_bits)) != 0) {
			return false;
		}
		
		return true;
	}
}