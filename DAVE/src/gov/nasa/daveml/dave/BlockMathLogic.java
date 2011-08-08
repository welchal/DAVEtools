// BlockMathLogic
//
//  Part of DAVE-ML utility suite, written by Bruce Jackson, NASA LaRC
//  <bruce.jackson@nasa.gov>
//  Visit <http://daveml.org> for more info.
//  Latest version can be downloaded from http://dscb.larc.nasa.gov/Products/SW/DAVEtools.html
//  Copyright (c) 2007 United States Government as represented by LAR-17460-1. No copyright is
//  claimed in the United States under Title 17, U.S. Code. All Other Rights Reserved.

package gov.nasa.daveml.dave;

/**
 *
 * <p> Extrema math function block </p>
 * <p> 2011-07-26 Bruce Jackson <mailto:bruce.jackson@nasa.gov> </p>
 *
 **/

import org.jdom.Element;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Iterator;

/**
 *
 * <p> The MathMinmax block provides min, max functions </p>
 *
 **/

public abstract class BlockMathLogic extends BlockMath
{
    /**
     * Defined supported functions, for speed of execution
     **/

    private static final int UNK   = 0;
    private static final int NOT   = 1;
    private static final int AND   = 2;
    private static final int OR    = 3;
    private static final int XOR   = 4;
 
    private static final int FALSE = 0;
    private static final int TRUE  = 1;

    String funcType;    // can be "not", "and", "or", or "xor"
    int op;             // can be 1 = NOT, 2 = AND, 3 = OR, or 4 = XOR
    
    /**
     *
     * <p> Constructor for Logic Block <p>
     *
     * @param applyElement Reference to <code>org.jdom.Element</code>
     * containing "apply" element
     * @param m         The parent <code>Model</code>
     *
     **/

    @SuppressWarnings("unchecked")
    public BlockMathLogic( Element applyElement, Model m )
    {
        // Initialize superblock elements
        super("pending", "logic", m);
        this.funcType = null;
        this.op = UNK;

        // Parse parts of the Apply element
        List<Element> kids = applyElement.getChildren();
        Iterator<Element> ikid = kids.iterator();

        // first element should be our type; also use for name
        Element first = ikid.next();
        try {
            this.setFunction( first.getName () );
        } catch (DAVEException e) {
            System.err.println("Error - BlockMathLogic constructor called with" +
                               " unknown element type:" + first.getName());
        }
        String blockType = first.getName();
        this.setName( blockType + "_" + m.getNumBlocks() );
        
        // take appropriate action based on type
        if(blockType.equals("not") || blockType.equals("and") || 
                blockType.equals("or") || blockType.equals("xor")) {
                this.genInputsFromApply(ikid, 1);
        } else {
            System.err.println("Error - BlockMathLogic constructor called with" +
                               " unknown element type:" + blockType);
        }
    }

    private void setFunction(String functionType) throws DAVEException {
    	this.funcType = functionType;
        this.setName( funcType + "_" + this.ourModel.getNumBlocks() );
        
        // take appropriate action based on type
        if(funcType.equals("not")) {
            this.op = NOT;
            this.myType = "not function";
        } else if (funcType.equals("and")) {
            this.op = AND;
            this.myType = "and function";
        } else if (funcType.equals("or")) {
            this.op = OR;
            this.myType = "or function";
        } else if (funcType.equals("xor")) {
            this.op = XOR;
            this.myType = "xor function";
        } else 
           throw new DAVEException("Unrecognized operator " + this.funcType 
        		   + " in call to BlockMathLogic.setFunction() method." );
    }
    
    
    /**
     * Returns the extrema function desired.
     * @return String containing function type ("not", "and, "or" or "xor")
     */
    public String getFuncType() {
        return funcType;
    }

    /**
     *
     * <p> Generates description of self </p>
     *
     * @throws <code>IOException</code>
     **/

    @Override
    public void describeSelf(Writer writer) throws IOException
    {
        super.describeSelf(writer);
        writer.write(" and is a logic math block.");
    }

    /**
     *
     * <p> Implements update() method </p>
     * @throws DAVEException
     *
     **/

    public void update() throws DAVEException
    {
        int requiredNumInputs;
        Iterator<Signal> theInputs;
        double[] inputVals;
        Signal theInput;
        int index;

        boolean verbose = this.isVerbose();

        if (verbose) {
            System.out.println();
            System.out.println("Method update() called for logic block '" + this.getName() + "'");
        }
 
        // Check to see if correct number of inputs
        if (this.inputs.size() < 1)
            throw new DAVEException("Math " + this.myType + " block " + this.myName + " has no input.");

        // check type of operation to see if have required number of inputs
        // op 1 (NOT) requires 1 input, all others require 2 or more
        requiredNumInputs = 1;
        if (this.op == NOT && this.inputs.size() > requiredNumInputs)
            throw new DAVEException("Math " + this.myType + " block " + this.myName + " has too many inputs.");
        else if (this.inputs.size() < 2)
            throw new DAVEException("Math " + this.myType + " block " + this.myName + " has too few inputs.");
        
        // Check to see if inputs are ready
        theInputs = this.inputs.iterator();
        inputVals = new double[this.inputs.size()];

        index = 0;
        while (theInputs.hasNext()) {
            theInput = theInputs.next();
            if (!theInput.sourceReady()) {
                if (verbose)
                    System.out.println(" Upstream signal '" + theInput.getName() + "' is not ready.");
                return;
            } else {
                inputVals[index] = theInput.sourceValue();
                if (verbose)
                    System.out.println(" Input #" + index + " value is " + inputVals[index]);
            }
            index++;
        }
        
        // Perform NOT operation or set this.value to first input
        int input = (inputVals[0] < 0.5) ? FALSE : TRUE;
        int inputSum = input;
        this.value = Double.NaN;
        
        for(int i = 1; i<inputVals.length; i++) {
            input = (inputVals[i] < 0.5) ? FALSE : TRUE;                
            inputSum += input;
        }
        
        switch (this.op){
            case NOT: 
                this.value = Math.abs(inputSum - 1); 
            case AND:
                this.value = (inputSum == inputVals.length) ? TRUE : FALSE;
            case OR:
                this.value = (inputSum > 0) ? TRUE : FALSE;
            case XOR:
                this.value = (inputSum % 2 != 0) ? TRUE : FALSE;
        }

        if (this.value == Double.NaN)
            throw new DAVEException("Unrecognized operator " + this.funcType + " in block " + this.getName());


        // record current cycle counter
        resultsCycleCount = ourModel.getCycleCounter();

    }
}
