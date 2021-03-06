/*****************************************************************************
 * 
 *  This file is part of the XYPlot library. The library allows to draw
 *  data in a x/y diagram using several output media.
 * 
 *  Copyright (C) 1994-2015 EWMK-Soft Eberhard Kuemmel
 *
 *  LICENSE AGREEMENT
 * 
 *  WHEREAS, Eberhard Kuemmel is the owner of valuable intellectual 
 *  property rights relating to the XYPlot and wish to license XYPlot
 *  subject to the terms and conditions set forth below;
 *  
 *  and 
 *
 *  WHEREAS, you ("Licensee") acknowledge that Eberhard Kuemmel has the
 *  right to grant licenses to the intellectual property rights relating
 *  to XYPlot, and that you desire to obtain a license to use XYPlot
 *  subject to the terms and conditions set forth below;
 *  
 *  Eberhard Kuemmel grants Licensee a non-exclusive, non-transferable,
 *  royalty-free license to use XYPlot and related materials without
 *  charge provided the Licensee adheres to all of the terms and conditions
 *  of this Agreement.
 * 
 *  By downloading, using, or copying XYPlot or any portion thereof,
 *  Licensee agrees to abide by the intellectual property laws and all
 *  other applicable laws of Germany, and to all of the terms and
 *  conditions of this Agreement, and agrees to take all necessary steps
 *  to ensure that the terms and conditions of this Agreement are not
 *  violated by any person or entity under the Licensee's control or in
 *  the Licensee's service.
 * 
 *  Licensee shall maintain the copyright and trademark notices on the
 *  materials within or otherwise related to XYPlot, and not alter,
 *  erase, deface or overprint any such notice.
 *  
 *  Licensee hereby grants a royalty-free license to any and all 
 *  derivatives based upon this software code base.
 *  
 *  Licensee may modify the sources of XYPlot for the Licensee's own
 *  purposes. Thus, Licensee may not distribute modified sources of
 *  XYPlot without prior written consent from the authors.
 *  
 *  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 *  INDIRECT,  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 *  HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 *  STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 *  IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE. 
 *
 *****************************************************************************/

package de.ewmksoft.xyplot.core;

/**
 * Data class used by XYPlot class. Data and representation are separated
 * from each other allowing a flexible handling of data.
 *
 */
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import de.ewmksoft.xyplot.core.IXYGraphLib.RGB;

/**
 * XYPlotData class encapsulates the data values shown in the XYPlot
 * 
 * @param maxvalue
 *            Maximum number of data points for this plot. If more data points
 *            are added, the first points are lost (ring buffer)
 */
public class XYPlotData {
	private static Lock accessLock = new ReentrantLock();

	public final static double MAX_DOUBLE_VALUE = 1E30;
	public final static double MIN_DOUBLE_VALUE = -1E30;

	private MinMax yMinMax;
	private double fixedYMinValue;
	private double fixedYMaxValue;
	private XYPlot owner;
	private String axisText;
	private String unitText;
	private volatile ScaleData scaleData;
	private volatile boolean hasFixedYMinValue;
	private volatile boolean hasFixedYMaxValue;
	private int maxNum;
	private int lastIndex;
	private int cursorPos;
	private int visiblePointNum;
	private int lastDrawPointNum;
	private IXYGraphLib.Rect legendRect;
	private volatile boolean autoScale;
	private RGB color;
	private int newValues;
	private boolean otherChanges;
	private ArrayList<DataValue> values;

	public XYPlotData(XYPlot owner, int maxvalue) {
		this.owner = owner;
		maxNum = maxvalue;
		autoScale = true;
		values = new ArrayList<DataValue>(maxNum);
		scaleData = new ScaleData();
		init();
	}

	/**
	 * Clear the data
	 * 
	 * @param owner
	 */
	public void clear() {
		accessLock.lock();
		try {
			values.clear();
			init();
		} finally {
			accessLock.unlock();
		}
	}

	public void setOwner(XYPlot owner) {
		this.owner = owner;
	}

	/**
	 * Nested class for one data value
	 * 
	 */
	public static class DataValue {
		DataValue(double x, double y, boolean border) {
			this.x = x;
			this.y = y;
			this.border = border;
		}

		public boolean border() {
			return border;
		}

		public double x() {
			return x;
		}

		public double y() {
			return y;
		}

		private double x;
		private double y;
		private boolean border;
	}

	/**
	 * Nested class containing all data relevant for the scaling
	 * 
	 * 
	 */
	static public class ScaleData {
		// A true value is the real value, the other values are
		// reduced by the exponent dexpo
		// Example: dexpo is 2 -> True value 200 becomes shown value 2 
		public double lmin; // Last used start value
		public double lmax; // Last used stop value
		public double smin; // Shown value of lowest tick
		public double smax; // Shown value of highest tick
		public double vmin; // Minimum true value on scale
		public double vmax; // Maximum true value on scale
		public double vfactor; // Factor from real to screen values
		public double sdelta; // Difference between two tick labels
		public long dexpo; // Exponent to be shown on the scale
		public long ticks; // number of ticks on the scale
		public int ticktype; // Ticks all 2, 5 or 10 units
		public int vk; // Number of digits before the comma
		public int nk; // Number of digits behind the comma
		public int gk; // Total number of digits in a displayed number
		public int maxticks; // Maximum number of allowed ticks with label
	}

	// /**
	// * Turn off screen updates while using the addValue method
	// *
	// * @param value
	// * true to not update the screen on data changes
	// */
	// protected void setDisableScreenUpdate(boolean value) {
	// disableScreenUpdate = value;
	// if (owner != null && !disableScreenUpdate) {
	// owner.setOutdated();
	// }
	// }

	/**
	 * Add a value to the data ring buffer. If the buffer is full, the oldest
	 * value (lowest x value) will be dropped
	 * 
	 * @param x
	 *            X-Value
	 * @param y
	 *            Y-Value
	 * @return Current number of values in the buffer
	 */
	public int addValue(double x, double y) {
		accessLock.lock();
		try {
			boolean shiftLastDrawPoint = false;
			int usedNum = values.size();
			if (usedNum == 0) {
				yMinMax = new MinMax();
			}
			if (usedNum == maxNum) {
				values.remove(0);
				usedNum--;
				yMinMax.minIndex--;
				yMinMax.maxIndex--;
				if (0 == yMinMax.minIndex || 0 == yMinMax.maxIndex) {
					yMinMax = findMinMax(0, usedNum - 2);
				}
				shiftLastDrawPoint = true;
				if (cursorPos != -1) {
					cursorPos--;
					if (cursorPos < 0) {
						cursorPos = 0;
					}
				}
			}
			values.add(new DataValue(x, y, false));
			if (y > yMinMax.max) {
				yMinMax.max = y;
				yMinMax.maxIndex = usedNum;
			}
			if (y < yMinMax.min) {
				yMinMax.min = y;
				yMinMax.minIndex = usedNum;
			}
			if (shiftLastDrawPoint) {
				int num = getLastDrawPointNum();
				num -= 1;
				setLastDrawPointNum(num);
			}
			newValues++;
		} finally {
			accessLock.unlock();
		}
		if (owner != null) {
			owner.setOutdated();
		}
		return values.size();
	}

	/**
	 * Exchange all values in the current ring buffer. The number of values is
	 * not changes and also not the x values
	 * 
	 * @param y
	 *            Array with new y values
	 */
	public void changeValues(double[] y) {
		accessLock.lock();
		try {
			yMinMax = new MinMax();
			for (int i = 0; i < Math.min(values.size(), y.length); ++i) {
				DataValue dv = values.get(i);
				dv.y = y[i];
				if (dv.y > yMinMax.max) {
					yMinMax.max = dv.y;
				}
				if (dv.y < yMinMax.min) {
					yMinMax.min = dv.y;
				}
			}
			otherChanges = true;
		} finally {
			accessLock.unlock();
		}
		if (owner != null) {
			owner.setOutdated();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.ewmksoft.xyplot.IXYPlotData#setPause()
	 */

	public void setPause() {
		accessLock.lock();
		try {
			int pos = values.size();
			if (pos > 0) {
				DataValue dataValue = values.get(pos - 1);
				dataValue.border = true;
			}
		} finally {
			accessLock.unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.ewmksoft.xyplot.IXYPlotData#getValue(int)
	 */
	public DataValue getValue(int index) {
		if (index >= 0 && index < values.size()) {
			return values.get(index);
		}
		return new DataValue(0, 0, false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.ewmksoft.xyplot.IXYPlotData#setLegendText(java.lang.String)
	 */
	public void setLegendText(String text) {
		boolean changed = true;
		if (axisText != null) {
			changed = !axisText.equals(text);
		}
		axisText = new String(text);
		if (changed && owner != null) {
			otherChanges = true;
			owner.setOutdated();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.ewmksoft.xyplot.IXYPlotData#getLegendText()
	 */
	public String getLegendText() {
		String result = "-";
		if (axisText != null) {
			result = axisText;
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.ewmksoft.xyplot.IXYPlotData#setUnit(java.lang.String)
	 */
	public void setUnit(String text) {
		boolean changed = true;
		if (unitText != null) {
			changed = !unitText.equals(text);
		}
		unitText = new String(text);
		if (changed && owner != null) {
			otherChanges = true;
			owner.setOutdated();
		}
	}

	/**
	 * Get the unit string of the data set
	 * 
	 * @return Unit string
	 */
	public String getUnit() {
		String result = "-";
		if (unitText != null) {
			result = unitText;
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.ewmksoft.xyplot.IXYPlotData#length()
	 */
	public int length() {
		return values.size();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.ewmksoft.xyplot.IXYPlotData#isAutoScale()
	 */
	public boolean isAutoScale() {
		return autoScale;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.ewmksoft.xyplot.IXYPlotData#setAutoScale(boolean)
	 */
	public void setAutoScale(boolean autoScale) {
		this.autoScale = autoScale;
	}

	/**
	 * Set manual minimum, maximum values for y axis *
	 * 
	 * @param min
	 *            Minimum y value
	 * @param max
	 *            Maximum y value
	 */
	public void setManualScale(double min, double max) {
		boolean changed = (min != fixedYMinValue || max != fixedYMaxValue);
		hasFixedYMinValue = true;
		hasFixedYMaxValue = true;
		fixedYMinValue = min;
		fixedYMaxValue = max;
		setAutoScale(false);
		if (changed && owner != null) {
			otherChanges = true;
			owner.setOutdated();
		}
	}

	/**
	 * Make sure that this minimum value is always visible on the y axis *
	 * 
	 * @param value
	 *            Any value
	 */
	public void setManualScaleMin(double value) {
		boolean changed = value != fixedYMinValue;
		hasFixedYMinValue = true;
		hasFixedYMaxValue = false;
		fixedYMinValue = value;
		if (changed && owner != null) {
			otherChanges = true;
			owner.setOutdated();
		}
	}

	/**
	 * Make sure that this value is always visible on the y axis *
	 * 
	 * @param value
	 *            Any value
	 */
	public void clearManualScale() {
		hasFixedYMinValue = false;
		hasFixedYMaxValue = false;
		if (owner != null) {
			otherChanges = true;
			owner.setOutdated();
		}
	}

	/**
	 * Set number of visible points
	 * 
	 * @param visiblePointNum
	 */
	public void setVisiblePointNum(int visiblePointNum) {
		this.visiblePointNum = visiblePointNum;
	}

	/**
	 * Get number of visible points
	 * 
	 * @return
	 */
	public int getVisiblePointNum() {
		return visiblePointNum;
	}

	/**
	 * @param lastDrawPoint
	 *            the lastDrawPoint to set
	 */
	public void setLastDrawPointNum(int lastDrawPoint) {
		if (lastDrawPoint >= 0 && lastDrawPoint < values.size()) {
			lastDrawPointNum = lastDrawPoint;
		}
	}

	/**
	 * @return the lastDrawPoint
	 */
	public int getLastDrawPointNum() {
		int result = lastDrawPointNum;
		lastDrawPointNum = lastIndex;
		lastIndex = values.size();
		return result;
	}

	/**
	 * Get a reference to the nested object containing the scaling data
	 * 
	 * @return Pointer to scale object
	 */
	public ScaleData getScaleData() {
		return scaleData;
	}

	/**
	 * Get the position (index) of the cursor within the data set
	 * 
	 * @return Cursor position
	 */
	public int getCursorPos() {
		return cursorPos;
	}

	/**
	 * Set the position of the cursor (index)
	 * 
	 * @param cursorPos
	 *            New cursor position
	 * @return true if position has changed
	 */
	public boolean setCursorPos(int cursorPos) {
		boolean result = false;
		if (cursorPos < 0)
			cursorPos = 0;
		if (cursorPos >= length())
			cursorPos = length() - 1;
		result = (this.cursorPos != cursorPos);
		this.cursorPos = cursorPos;
		return result;
	}

	/**
	 * Hide cursor
	 * 
	 */
	public void hideCursor() {
		cursorPos = -1;
	}

	/**
	 * Get the position and size of the area where the description of the data
	 * set is located within the global legend area.
	 * 
	 * @return Rectangle containing the legend area
	 */
	public IXYGraphLib.Rect getLegendRect() {
		return legendRect;
	}

	/**
	 * Set the rectangle where the legend of the data set is displayed in the
	 * global legend area of the plot
	 * 
	 * @param r
	 *            Rectangle containing the legend
	 */
	public void setLegendRect(IXYGraphLib.Rect r) {
		legendRect = r;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.ewmksoft.xyplot.IXYPlotData#getXMax()
	 */
	public double getXMax() {
		double result = 0;
		accessLock.lock();
		try {
			int index = values.size() - 1;
			if (index >= 0) {
				result = values.get(index).x;
			}
		} finally {
			accessLock.unlock();
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.ewmksoft.xyplot.IXYPlotData#getXMin()
	 */
	public double getXMin() {
		double result = 0;
		accessLock.lock();
		try {
			if (values.size() > 0) {
				result = values.get(0).x;
			}
		} finally {
			accessLock.unlock();
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.ewmksoft.xyplot.IXYPlotData#getYMax()
	 */
	public double getYMax() {
		accessLock.lock();
		double result = yMinMax.max;
		if (hasFixedYMaxValue && yMinMax.max < fixedYMaxValue) {
			result = fixedYMaxValue;
		}
		accessLock.unlock();
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.ewmksoft.xyplot.IXYPlotData#getYMin()
	 */
	public double getYMin() {
		accessLock.lock();
		double result = yMinMax.min;
		if (hasFixedYMinValue && yMinMax.min > fixedYMinValue) {
			result = fixedYMinValue;
		}
		accessLock.unlock();
		return result;
	}

	/**
	 * Return true if new data have been added since the last call of this
	 * method
	 * 
	 * @return
	 */
	public boolean hasNewValues() {
		boolean result = false;
		if (newValues > 0) {
			newValues = 0;
			result = true;
		}
		return result;
	}

	/**
	 * Returns true is legend or scaling has been changed sine last call of this
	 * method
	 * 
	 * @return
	 */
	public boolean hasOtherChanges() {
		boolean result = otherChanges;
		otherChanges = false;
		return result;
	}

	/**
	 * Locate the closest data index from a given x value
	 * 
	 * @return > 0 Array index -1 No index found
	 */
	public int locateIndexFromXValue(double xvalue) {
		int xpos = -1;
		accessLock.lock();
		try {
			int maxIndex = values.size() - 1;
			if (maxIndex >= 0) {
				DataValue dvmax = values.get(maxIndex);
				DataValue dv0 = values.get(0);
				double last_diff = dvmax.x - dv0.x;
				if (xvalue > dvmax.x) {
					xpos = maxIndex;
				} else if (xvalue < dv0.x) {
					xpos = 0;
				} else {
					// find closest point in data array
					for (int i = 0; i <= maxIndex; ++i) {
						double diff = Math.abs(values.get(i).x - xvalue);
						if (diff > last_diff) {
							break;
						}
						xpos = i;
						last_diff = diff;
					}
				}
			}
		} finally {
			accessLock.unlock();
		}
		return xpos;
	}

	/**
	 * Print some information about scale data
	 */
	public void print() {
		XYPlotData.ScaleData sd = scaleData;
		System.out.println("Min Scale: " + sd.smin);
		System.out.println("Max Scale: " + sd.smax);
		System.out.println("Range: " + (sd.smax - sd.smin));
		System.out.println("Min Calc: " + sd.vmin);
		System.out.println("Max Calc: " + sd.vmax);
		System.out.println("DExpo: " + sd.dexpo);
		System.out.println("Delta:" + sd.sdelta);
		System.out.println("TickType:" + sd.ticktype);
		System.out.println("Ticks:" + sd.ticks + " (" + sd.maxticks + ")");
		System.out.println("Range:" + sd.ticks * sd.sdelta);
		System.out.println("VK:   " + sd.vk);
		System.out.println("NK:   " + sd.nk);
		System.out.println("--------------------------------------------");
	}

	static void lock() {
		accessLock.lock();
	}

	static void unlock() {
		accessLock.unlock();
	}

	public MinMax getYRange(int minIndex, int maxIndex) {
		MinMax result = null;
		if (minIndex >= 0 && maxIndex >= 0) {
			accessLock.lock();
			try {
				result = findMinMax(minIndex, maxIndex);
			} finally {
				accessLock.unlock();
			}
		} else {
			result = getYRange();
		}
		return result;
	}

	public MinMax getYRange() {
		MinMax result = new MinMax(getYMin(), getYMax());
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.ewmksoft.xyplot.IXYPlotData#setColor(de.ewmksoft.xyplot.IXYGraphLib
	 * .RGB)
	 */
	public void setColor(RGB color) {
		this.color = color;
	}

	/**
	 * @return the color
	 */
	public RGB getColor() {
		return color;
	}

	/**
	 * Inner class to hold min/max data
	 */

	public class MinMax {
		MinMax() {
			max = MIN_DOUBLE_VALUE;
			min = MAX_DOUBLE_VALUE;
			minIndex = 0;
			maxIndex = 0;
		}

		MinMax(double min, double max) {
			this.min = min;
			this.max = max;
			this.minIndex = 0;
			this.maxIndex = 0;
		}

		public double getMin() {
			return min;
		}

		public double getMax() {
			return max;
		}

		private double min;
		private double max;
		private int minIndex;
		private int maxIndex;
	}

	/**
	 * Function to find minimum and maximum y values.
	 * 
	 * @param minIndex
	 * @param maxIndex
	 */
	private MinMax findMinMax(int minIndex, int maxIndex) {
		MinMax result = new MinMax();
		result.max = MIN_DOUBLE_VALUE;
		result.min = MAX_DOUBLE_VALUE;
		if (minIndex > maxIndex) {
			int tmp = minIndex;
			minIndex = maxIndex;
			maxIndex = tmp;
		}
		if (minIndex < 0) {
			minIndex = 0;
		}
		if (maxIndex >= values.size()) {
			maxIndex = values.size() - 1;
		}
		for (int i = minIndex; i <= maxIndex; ++i) {
			DataValue dv = values.get(i);
			double vy = dv.y;
			if (vy > result.max) {
				result.max = vy;
				result.maxIndex = i;
			}
			if (vy < result.min) {
				result.min = vy;
				result.minIndex = i;
			}
		}
		return result;
	}

	private void init() {
		cursorPos = -1;
		visiblePointNum = 0;
		yMinMax = new MinMax();
		yMinMax.min = 0;
		yMinMax.max = 0;
		lastIndex = 0;
		newValues = 0;
		lastDrawPointNum = 0;
		scaleData.smin = 0;
		scaleData.smax = 0;
	}

}
