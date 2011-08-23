/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gov.nasa.daveml.dave;

import java.io.IOException;
import java.io.StringWriter;
import junit.framework.TestCase;

import org.jdom.Element;

/**
 *
 * @author Austin
 */
public class BlockMathLogicTest extends TestCase {
    
    protected Model _notModel;
    protected Model _logicModel;
    protected Signal _outputSignal;
    protected Signal _notOutputSignal;
    protected Signal _value1Signal;
    protected Signal _value2Signal;
    protected Signal _value3Signal;
    protected String _value1SignalID;
    protected String _value2SignalID;
    protected String _value3SignalID;
    protected BlockMathConstant _value1Block;
    protected BlockMathConstant _value2Block;
    protected BlockMathConstant _value3Block;
    protected BlockMathLogic _block;
    protected BlockMathLogic _notBlock;
    protected String routineName = "TestBlockMathLogic";
    
    private final double TRUE = 1;
    private final double FALSE = 0;
    private final double EPS = 0.000001;
    
    public BlockMathLogicTest(String testName) {
        super(testName);
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        
        // don't need input signal - can create const block and signal in one step later
    	_logicModel   = new Model(3,3);
        _notModel     = new Model(3,3);
	   	
	// build a logic calculation
	//      <apply>
	//        <and/>
	//        <ci>PB</ci>
	//        <ci>BSPAN</ci>
	//      </apply>            <!---- and(PB,BSPAN) -->
    	
		
	// first, build the upstream constant blocks and signals
	_value1Block = new BlockMathConstant( "1", _logicModel );
	_value2Block = new BlockMathConstant( "1", _logicModel );
        _value3Block = new BlockMathConstant( "1", _notModel);
	_value1SignalID = new String("PB");
	_value2SignalID = new String("BSPAN");
        _value3SignalID = new String("notSignalID");
	_value1Signal = new Signal("PB",    _value1SignalID, "d_s", 1, _logicModel);
	_value2Signal = new Signal("BSPAN", _value2SignalID, "ft", 1, _logicModel);
	_value3Signal = new Signal("notSignal", _value3SignalID, "ns", 1, _notModel);
        _value1Block.addOutput(_value1Signal);
	_value2Block.addOutput(_value2Signal);
        _value3Block.addOutput(_value3Signal);
		
	// create downstream signal
	_outputSignal = new Signal("outputSignal", _logicModel);
        _notOutputSignal = new Signal("notOutputSignal", _notModel);

	// build JDOM from XML snippet
    	Element theValue1 = new Element("ci");	// add numeric constant
    	theValue1.addContent( "PB" );
    	
    	Element theValue2 = new Element("ci");
    	theValue2.addContent( "BSPAN" );
        
        Element theValue3 = new Element("ci");
        theValue2.addContent( "notSignal" );

    	Element theOpElement = new Element("and");
        Element theNotElement = new Element("not");

    	Element applyElement = new Element("apply");
    	applyElement.addContent( theOpElement );
       	applyElement.addContent( theValue1 );
       	applyElement.addContent( theValue2 );
        
        Element applyNotElement = new Element("apply");
        applyNotElement.addContent( theNotElement );
        applyNotElement.addContent( theValue3 );
        
   	
    	// create Logic block
	_block     = new BlockMathLogic( applyElement, _logicModel );
        
        // create Not block
        _notBlock = new BlockMathLogic( applyNotElement, _notModel );
				
	// hook up inputs to block
	_block.addInput(_value1Signal,1);
	_block.addInput(_value2Signal,2);
        
        // hook up inputs to not block
        _notBlock.addInput(_value3Signal);
		
	// hook up output to block
	_block.addOutput(_outputSignal);
        
        // hook up output to block
        _notBlock.addOutput(_notOutputSignal);
				
	try {
		_logicModel.initialize();
	} catch (DAVEException e) {
		fail("problem initializing model (logic) in " + routineName 
				+ ": " + e.getMessage());
	}
        try {
		_notModel.initialize();
	} catch (DAVEException e) {
		fail("problem initializing model (not) in " + routineName 
				+ ": " + e.getMessage());
	}
    }

    /**
     * Test of getFuncType method, of class BlockMathLogic.
     */
    public void testGetFuncType() {
        String expResult = "and";
        String result = _block.getFuncType();
        assertEquals(expResult, result);
    }

    /**
     * Test of describeSelf method, of class BlockMathLogic.
     */
    public void testDescribeSelf() throws Exception {
        StringWriter writer = new StringWriter();
        try {
            _block.describeSelf(writer);
	} catch (IOException e) {
            assertTrue(false);
            e.printStackTrace();
	}
	assertEquals( "Block \"and_3\" has two inputs (PB, BSPAN)," +
                    " one output (outputSignal), value [1.0] and is a logic math block.", 
                    writer.toString() );
    }

    /**
     * Test of update method, of class BlockMathLogic.
     */
    public void testUpdate() throws Exception {
        String routineName = "BlockMathLogicTest::testUpdate()";
        
        assertTrue(checkNot(FALSE, false));
        assertFalse(checkNot(TRUE, false));
        assertTrue(checkLogic(TRUE, TRUE, "and", false));
        assertFalse(checkLogic(TRUE, FALSE, "and", false));
        assertTrue(checkLogic(TRUE, TRUE, "or", false));
        assertTrue(checkLogic(TRUE, FALSE, "or", false));
        assertFalse(checkLogic(FALSE, FALSE, "or", false));
        assertTrue(checkLogic(TRUE, FALSE, "xor", false));
        assertFalse(checkLogic(TRUE, TRUE, "xor", false));
        assertFalse(checkLogic(TRUE, FALSE, "xor", false));
        
        assertFalse(checkLogic(TRUE, TRUE, "meh", true));
        
        
    }
    
    private boolean checkLogic(double n1, double n2, String relation, boolean expectException)
    {
        // set operand values
    	_value1Block.setValue( n1 );
    	_value2Block.setValue( n2 );
    	
    	// set relationship test
    	try {
            _block.setFunction(relation);
	} catch (DAVEException e1) {
            if (!expectException)
                fail("Unexpected exception in " + routineName +
		".checkRelation for ["+ n1 + 
		" " + relation + " " + n2 + "]: " + e1.getMessage() );
	}

	// run model
    	try {
            _logicModel.cycle();
    	} catch (DAVEException e) {
			fail("Unexpected exception in " + routineName +
					".checkRelation for ["+ n1 + 
					" " + relation + " " + n2 + "]: " + e.getMessage() );
    	}
    	
    	// check result
    	return (_block.getValue() == 1);
    }
    
    private boolean checkNot(double value, boolean expectException)
    {
        // set operand value
    	_value3Block.setValue( value );
    	
    	// set relationship test
    	try {
            _notBlock.setFunction("not");
	} catch (DAVEException e1) {
            if (!expectException)
                fail("Unexpected exception in " + routineName +
		".checkRelation for [not " + value + " ]: " + 
                e1.getMessage() );
	}

	// run model
    	try {
            _notModel.cycle();
    	} catch (DAVEException e) {
            fail("Unexpected exception in " + routineName +
            ".checkRelation for [not " + value +" ]: " + e.getMessage() );
    	}
    	
    	// check result
    	return (_notBlock.getValue() == 1);
    }
}

