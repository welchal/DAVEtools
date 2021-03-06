// SLDiagram.java
//
//  Part of DAVE-ML utility suite, written by Bruce Jackson, NASA LaRC
//  <bruce.jackson@nasa.gov>
//  Visit <http://daveml.org> for more info.
//  Latest version can be downloaded from http://dscb.larc.nasa.gov/Products/SW/DAVEtools.html
//  Copyright (c) 2007 United States Government as represented by LAR-17460-1. No copyright is
//  claimed in the United States under Title 17, U.S. Code. All Other Rights Reserved.

package gov.nasa.daveml.dave2sl;

import gov.nasa.daveml.dave.*;	// need most all elements in package
import java.util.ArrayList;
import java.util.Iterator;
import java.io.PrintStream;
import java.io.IOException;

/**
 *
 * Helps generate Simulink diagram of DAVE model.
 *
 * <p>
 * The SLDiagram consists of a rectangular grid of cells, arranged
 * as rows and columns. A simulink block can be contained in a cell,
 * or a cell may be empty. Each cell can contain a single output
 * signal and zero or more input signals. Running below each row and
 * to the left of each column is space for zero or more signal lines
 * connected inputs and outputs.
 *
 *<p> 
 * Modification history: 
 * <ul>
 *  <li>020619: Written EBJ</li>
 *  <li>031220: Substantially modified for DAVE_tools 0.4</li> 
 *  <li>040225: Modified for 0.4 EBJ</li>
 *  <li>040515: Added warnOnClip flag for X-37 ALTV model EBJ</li>
 *  <li>040520: Added resetOuputsWhenDisabled, makeLib and makeEnabledSubSys flags EBJ</li>
 * </ul>
 *
 * @author Bruce Jackson {@link <mailto:bruce.jackson@nasa.gov>}
 * @version 0.9
 *
 **/

public class SLDiagram
{
	/**
	 * Our parent model
	 */

	Model model;

	/**
	 * rows in our diagram
	 */

	ArrayList<SLRowColumnVector> rows;

	/**
	 * columns in our diagram
	 */

	ArrayList<SLRowColumnVector> cols;

	/**
	 * individual cells of our diagram
	 */

	ArrayList<SLCell> cells;

	/**
	 *  SLBlocks representing underlying Blocks
	 */

	ArrayList<SLBlock> slblockList;

	/**
	 *  input block names in seq as they are created in SLBLock
	 */

	ArrayList<String> inputNames;

	/**
	 *  output block names in seq; use for validation script
	 */

	ArrayList<String> outputNames;

	/**
	 *  if set, become chatty
	 */

	boolean verboseFlag;

	/**
	 *  which version of Simulink to write
	 */

	double SLversion;

	/**
	 *  tell Simulink to warn when table input exceeds bounds
	 */

	boolean warnOnClip;

	/**
	 *  tell Simulink to set disabled block outputs to zero
	 */

	boolean resetOutputsWhenDisabled;

	/**
	 *  generate a Library, not a Model
	 */

	boolean makeLib;

	/**
	 *  make the subsystem an enabled subsystem
	 */

	boolean makeEnabledSubSys;

	// Diagram layout parameters

	/**
	 *  padding between block and cell edge (diagram layout parameter)
	 */

	static int padding  = 20;

	/**
	 *  distance from left edge of window (diagram layout parameter)
	 */

	static int xMargin  = 10;

	/**
	 *  distance from top edge of window  (diagram layout parameter)
	 */

	static int yMargin  = 10;

	/**
	 *
	 * Constructor for SLDiagram.
	 *
	 * <p>
	 * When called, the provided <code>Model</code> should contain
	 * a completed, wired network with no dangling lines. Ultimately
	 * this constructor will figure out placement; for now, we use the
	 * one generated by temporary DAVE.createMDL() method. 
	 *
	 * @see gov.nasa.daveml.dave.DAVE
	 * @param theModel <code>gov.nasa.daveml.dave.Model</code> object to handle
	 *
	 **/

	public SLDiagram( Model theModel )
	{
		int row = 0;	// high water mark
		SLBlock slb = null;
		this.inputNames = new ArrayList<String>(20);	
		this.outputNames = new ArrayList<String>(20);

		// default behavior flags
		this.SLversion = 6.2;
		this.warnOnClip = false;
		this.resetOutputsWhenDisabled = true;
		this.makeLib = false;
		this.makeEnabledSubSys = false;

		if (theModel.isVerbose())
			this.makeVerbose();

		if (this.isVerbose())
			System.out.println("Building diagram in memory...");
		// save pointer to object
		this.model = theModel;


		// Loop through all blocks while
		// 1. convert all blocks to SLblocks
		// 2. assign row & column number to all blocks

		this.slblockList = new ArrayList<SLBlock>( this.model.getNumBlocks() );

		// Starting with input and constant blocks, assign to column
		// 1. Follow signal path and set immediate downstream blocks
		// to minimum of column 2, etc. and recurse. When done, column
		// set up with be complete. Also can propagate row so next
		// input block starts below lowest block of previous block.

		MDLNameList nl = new MDLNameList( this.model.getNumBlocks() );	// create name list for blocks

		if (this.isVerbose())
			System.out.println("Looping through " + this.model.getNumBlocks() 
					+ "-block list to find children, adjust names and set positions.");

		BlockArrayList bal = this.model.getBlocks();
		if ( bal == null ) {
			System.err.println("ERROR: No blocks associated with model!");
			System.exit(0);
		}

		// assign blocks to SLBlocks and gather all SLBlocks to us
		for(Iterator<Block> iBlks = bal.iterator();
		iBlks.hasNext();  )
		{
			Block b = iBlks.next();
			if(b != null) {
				slb = new SLBlock( this, b );
				b.setMask( slb );			// let block know "who's yo daddy"
			} else {
				System.err.println("ERROR: Null Block found while assigning SLBlocks");
				System.exit(0);
			}
			try {
				b.setNameList( nl );			// may change to acceptable SL name
			} catch (Exception e) {
				System.err.println("Warning: Unable to properly name " 
						+ b.getType() + "block '" + b.getName() + "'.");
			}
			this.slblockList.add( slb );
		}

		// find children of all blocks; set inputs/constants to column 1
		for(Iterator<SLBlock> islb = this.slblockList.iterator();
		islb.hasNext();  )
		{
			slb = islb.next();
			slb.findChildren(); 
			Block b = slb.getBlock();
			if ( b == null ) {
				System.err.println("ERROR: Found an SLBlock with no encapsulated Block!");
				System.exit(0);
			}
			if( (b instanceof BlockInput) ||
					(b instanceof BlockMathConstant) ) {
				row = slb.setPosition(row+1, 1);	// recursive routine
			}
		}

		// loop through new slblock list to find dimensions
		int numRows = 1;
		int numCols = 1;
		Iterator<SLBlock> iblk = this.slblockList.iterator();

		while (iblk.hasNext())
		{
			slb = iblk.next();
			int theRow = slb.getRow();
			int theCol = slb.getCol();
			if(theRow > numRows) numRows = theRow;
			if(theCol > numCols) numCols = theCol;
		}

		if(this.isVerbose()) {
			System.out.print(" Found " + numRows + " rows and");
			System.out.println(" " + numCols + " columns.");
		}

		// Create and initialize row & columns for diagram

		this.rows = new ArrayList<SLRowColumnVector>( numRows+1 );	// create arrays for refs to rows...
		this.cols = new ArrayList<SLRowColumnVector>( numCols+1 );	//     ...columns..
		this.cells = new ArrayList<SLCell>( numRows * numCols );	// ...and cells.

		for(int i = 0; i < numRows; i++)		// now create rows and...
			this.rows.add(i, new SLRowColumnVector(numCols+1, true));

		for(int i = 0; i < numCols; i++)		// ...columns themselves.
			this.cols.add(i, new SLRowColumnVector(numRows+1, false));

		// loop through and assign blocks to cells, rows, cols

		iblk = this.slblockList.iterator();	// this resets to start of list
		while (iblk.hasNext()) {
			slb = iblk.next();		// get block
			SLCell cell = new SLCell(slb, this);	// create cell; saves ref to block and diagram
			this.cells.add( cell );			// add cell to list
			int rowIndex = slb.getRow()-1;		// now assign cell to proper...
			int colIndex = slb.getCol()-1;		// row and column...
			SLRowColumnVector rowv = this.rows.get( rowIndex );
			SLRowColumnVector colv = this.cols.get( colIndex );
			rowv.set(colIndex, cell);
			colv.set(rowIndex, cell);
		}
	}

	/**
	 *
	 * Sets the flag to generate warnings when breakpoint inputs exceed bounds
	 *
	 **/

	void setWarnOnClip() { this.warnOnClip = true; }


	/**
	 *
	 * Returns the status of the warnings generation option
	 *
	 **/

	boolean getWarnOnClip() { return this.warnOnClip; }


	/**
	 *
	 * Sets the SL version output to 4.0
	 *
	 **/

	void setVerFlag4() { this.SLversion = 4.0; }


	/**
	 *
	 * Returns the status of the SL version output at 4.0 flag
	 *
	 **/

	boolean getVerFlag4() { return this.SLversion == 4.0; }


	/**
	 *
	 * Sets the SL version output to 5.0
	 *
	 **/

	void setVerFlag5() { this.SLversion = 5.0; }


	/**
	 *
	 * Returns the status of the SL version output at 5.0 flag
	 *
	 **/

	boolean getVerFlag5() { return this.SLversion == 5.0; }


	/**
	 *
	 * Sets flag to generate library instead of model
	 *
	 **/

	void setLibFlag() { this.makeLib = true; }


	/**
	 *
	 * Returns status of make library flag
	 *
	 **/

	boolean getLibFlag() { return this.makeLib; }


	/**
	 *
	 * Sets flag to make enabled subsystem
	 *
	 **/

	void setEnabledFlag() { this.makeEnabledSubSys = true; }


	/**
	 *
	 * Returns status of make enabled subsystem flag
	 *
	 **/

	boolean getEnabledFlag() { return this.makeEnabledSubSys; }


	/**
	 * 
	 * Returns the padding for cells in diagram.
	 *
	 **/

	public int getPadding() { return SLDiagram.padding; }


	/**
	 *
	 * Returns the number of inputs to the diagram
	 *
	 **/

	public int getNumInputs() { return this.model.getNumInputBlocks(); }


	/**
	 *
	 * Returns the number of outputs from the diagram
	 *
	 **/

	public int getNumOutputs() { return this.model.getNumOutputBlocks(); }


	/**
	 *
	 * Sets the verbose flag for diagram and all children
	 *
	 **/

	public void makeVerbose() {
		this.verboseFlag = true;
		if( rows != null ) {
			Iterator<SLRowColumnVector> it = rows.iterator();
			while (it.hasNext()) {
				SLRowColumnVector dude = it.next();
				dude.makeVerbose();
			}
		}
		if( cols != null ) {
			Iterator<SLRowColumnVector> it = cols.iterator();
			while (it.hasNext()) {
				SLRowColumnVector dude = it.next();
				dude.makeVerbose();
			}
		}
		if( slblockList != null ) {
			Iterator<SLBlock> it = slblockList.iterator();
			while (it.hasNext()) {
				SLBlock dude = it.next();
				dude.makeVerbose();
			}
		}
	}


	/**
	 *
	 * Indicates status of verbose flag
	 *
	 **/

	public boolean isVerbose() { return this.verboseFlag; }


	/**
	 *
	 * Clears the verbose flag
	 *
	 **/

	public void silence() 
	{ 
		this.verboseFlag = false; 
		if( rows != null ) {
			Iterator<SLRowColumnVector> it = rows.iterator();
			while (it.hasNext()) {
				SLRowColumnVector dude = it.next();
				dude.makeVerbose();
			}
		}
		if( cols != null ) {
			Iterator<SLRowColumnVector> it = cols.iterator();
			while (it.hasNext()) {
				SLRowColumnVector dude = it.next();
				dude.makeVerbose();
			}
		}
		if( slblockList != null ) {
			Iterator<SLBlock> it = slblockList.iterator();
			while (it.hasNext()) {
				SLBlock dude = it.next();
				dude.makeVerbose();
			}
		}
	}


	/**
	 *
	 * Returns the cell at a given row and column offset.
	 *
	 * @param rowIndex given row
	 * @param colIndex given column
	 * @return the cell at the given location
	 *
	 **/

	public SLCell getCell( int rowIndex, int colIndex )
	{
		//System.out.println("Looking for cell at [" + rowIndex + "," + colIndex + "]");
		SLRowColumnVector row = this.rows.get( rowIndex );
		//    if(row!=null)
		//	System.out.println("  found row index " + rowIndex);
		//    else
		//	System.out.println("  NO ROW FOUND with index " + rowIndex );
		// return row.get(colIndex);
		SLCell theCell = row.get(colIndex);
		//    if(theCell != null)
		//      System.out.println("  found cell at column index " + colIndex );
		//    else
		//      System.out.println("  NO CELL FOUND on row index " + rowIndex +
		//			   " at column index " + colIndex );
		return theCell;
	}


	/**
	 *
	 * Returns SLCell associated with specified Block.
	 *
	 * @param b <code>Block</code> whose parent SLCell is sought
	 *
	 **/

	public SLCell getCell( SLBlock b )
	{
		int rowIndex = b.getRow()-1;
		int colIndex = b.getCol()-1;

		return this.getCell( rowIndex, colIndex );
	}


	/**
	 *
	 * Returns SLRowColumnVector object representing the specified row
	 *
	 * @param index 0-based offset or index of desired row
	 *
	 **/

	public SLRowColumnVector getRow( int index ) { return this.rows.get(index); }


	/**
	 *
	 * Returns SLRowColumnVector object representing the specified column
	 *
	 * @param index 0-based offset or index of desired column
	 *
	 **/

	public SLRowColumnVector getCol( int index ) { return this.cols.get(index); }


	/**
	 *
	 * Add an input variable name in the right location, so verify
	 * script can specify input vector in the proper sequence
	 *
	 * @param seqNum input block sequence number (1-based)
	 * @param name   input variable name
	 *
	 **/

	public void addInput( int seqNum, String name )
	{
		if (seqNum < 1) {
			System.err.println("Error - input block '" + name + "' sequence number (" + seqNum 
					+ ") is less than 1.");
			System.exit(0);
		}

		// pad out vector until it's long enough
		while ( inputNames.size() < seqNum )
			inputNames.add(null);

		this.inputNames.set( seqNum-1, name );
	}


	/**
	 *
	 * Returns the inputNames vector
	 *
	 **/

	public ArrayList<String> getInputNames() { return this.inputNames; }


	/**
	 *
	 * Add an output variable name in the right location, so verify
	 * script can specify output vector in the proper sequence
	 *
	 * @param seqNum output block sequence number (1-based)
	 * @param name   output variable name
	 *
	 **/

	public void addOutput( int seqNum, String name )
	{
		if (seqNum < 1) {
			System.err.println("Error - output block '" + name + "' sequence number (" + seqNum 
					+ ") is less than 1.");
			System.exit(0);
		}

		// pad out vector until it's long enough
		while ( outputNames.size() < seqNum )
			outputNames.add(null);

		// add string to appropriate position
		this.outputNames.set( seqNum-1, name );
	}


	/**
	 *
	 * Returns the outputNames vector
	 *
	 **/

	public ArrayList<String> getOutputNames() { return this.outputNames; }


	/**
	 *
	 * Create text representation of diagram
	 *
	 **/

	public void describeSelf( PrintStream printer)
	{
		final int width = 3;

		// print header

		printer.println();
		//    printer.println("Text representation of Simulink diagram:");
		//    printer.println();

		// print column cabletray count
		printer.print("    ");	// margin
		printer.print(" ");		// space
		printer.print("    ");	// margin
		for( int k = 0; k < width; k++) 
			printer.print(" ");
		printer.print("    ");	// margin
		for(int j = 0; j < cols.size(); j++)
		{
			SLRowColumnVector col = cols.get(j);
			printer.print(col.cableTray.size());	// need to set size to 2
			printer.print("    ");	// margin
			for( int k = 0; k < width; k++) 
				printer.print(" ");
			printer.print("    ");	// margin
		}
		for(int i = 0; i < rows.size(); i++)
		{
			SLRowColumnVector row = rows.get(i);
			printer.println();
			printer.print("    *");
			for(int j = 0; j < cols.size(); j++)
			{
				SLCell cell = row.get(j);
				if(cell != null)
				{
					SLBlock b = cell.getBlock();
					printer.print("    ");
					if(b == null)
						for( int k = 0; k < width; k++)
							printer.print(" ");
					else
					{
						String s = b.getName();
						if (s.length() < width)
						{
							printer.print(s);
							for( int k = 0; k < (width-s.length()); k++)
								printer.print(" ");
						}
						else
							printer.print(s.substring(0,width));
					}
				}
				else	// if cell is null
				{
					printer.print("    ");
					for( int k = 0; k < width; k++) printer.print(" ");
				}
				printer.print("    *");
			}
			printer.print("\n" + row.cableTray.size());
		}
		printer.println();
		printer.println();
	}


	/**
	 *
	 * Writes out Simulink model-building script to writer and necessary data statements to mWriter.
	 *
	 * @param writer SLFileWriter to output model diagram build script
	 * @param mWriter MatFileWriter to output data
	 *
	 * @throws java.io.IOException
	 *
	 **/

	public void createModel( SLFileWriter writer, MatFileWriter mWriter ) 
	throws IOException
	{
		// update row & column info in blocks

		for( int i = 0; i < rows.size(); i++ )
		{
			SLRowColumnVector row = rows.get(i);
			for( int j = 0; j < cols.size(); j++ )
			{
				SLCell cell = row.get(j);
				if( cell != null)
				{
					SLBlock b = cell.getBlock();
					int oldRow = b.getRow();
					int oldCol = b.getCol();
					if(oldRow != i+1) 
						System.err.println("WARNING: Had to update row number in block " + b.getName() + 
								" to " + (i+1) + "; was " + oldRow);
					if(oldCol != j+1) 
						System.err.println("WARNING: Had to update column number in block " + b.getName() + 
								" to " + (j+1) + "; was " + oldCol);
					b.setRowCol( i+1, j+1 );	// convert from offset to index
				}
			}
		}

		// write out blocks to model-building script and data to Mat file

		int rowOffset = xMargin;
		for(int rowIndex = 0; rowIndex < this.rows.size(); rowIndex++)
		{
			// adjust row offset for half row height
			int rowSize = this.getRow( rowIndex ).getSize();	// includes padding around block
			int y = rowOffset + rowSize/2;

			int colOffset = yMargin;
			for( int colIndex = 0; colIndex < this.cols.size(); colIndex++ )
			{
				// adjust column offset for half column width
				int colSize = this.getCol( colIndex ).getSize();	// includes padding
				int x = colOffset + colSize/2;				// probably is redundant

				SLCell theCell = this.getCell( rowIndex, colIndex );
				if( theCell != null )
				{
					SLBlock theBlock = theCell.getBlock();
					if( theBlock != null )
					{
						theBlock.createM( writer, x, y );
						theBlock.writeMat( mWriter );
					}
				}
				colOffset = colOffset + colSize;
			}
			rowOffset = rowOffset + rowSize;
		}

		// add lines

		// convert generic Signals to SLSignals

		SignalArrayList sigs = model.getSignals();

		Iterator<Signal> isig = sigs.iterator();
		while (isig.hasNext())
		{
			Signal oldSig = isig.next();
			SLSignal newSig = new SLSignal( oldSig, this );	// convert to SLSig
			newSig.createAddLine( writer );			// write add_line to .m file
		}
	}
}

