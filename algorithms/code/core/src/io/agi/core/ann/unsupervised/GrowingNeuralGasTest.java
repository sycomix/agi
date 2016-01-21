package io.agi.core.ann.unsupervised;

import io.agi.core.data.Data;
import io.agi.core.math.RandomInstance;
import io.agi.core.orm.Callback;
import io.agi.core.orm.CallbackCollection;
import io.agi.core.orm.ObjectMap;
import io.agi.core.orm.UnitTest;

/**
 * Created by dave on 11/01/16.
 */
public class GrowingNeuralGasTest implements UnitTest, Callback {

    public static void main( String[] args ) {
        GrowingNeuralGasTest t = new GrowingNeuralGasTest();
        t.test( args );
    }

    public static final String ALGORITHM = "competitive-learning";

    public void test( String[] args ) {

        int epochs = 100;
        int batch = 100;
        int randomSeed = 1;
        int inputs = 2; // x and y.
        int widthCells = 10;
        int heightCells = 10;

        float meanErrorThreshold = 0.01f;

        float noiseMagnitude = 0.0f; //0.f;//0.005f;
        float learningRate = 0.02f;
        float learningRateNeighbours = 0.01f;
        int edgeMaxAge = 400;
        float stressLearningRate = 0.1f;
        float stressThreshold = 0.03f;
        int growthInterval = 5;

        RandomInstance.setSeed(randomSeed); // make the tests repeatable
        ObjectMap om = ObjectMap.GetInstance();
        GrowingNeuralGasConfig gngc = new GrowingNeuralGasConfig();

        gngc.setup(
                om, ALGORITHM, inputs, widthCells, heightCells, learningRate, learningRateNeighbours, noiseMagnitude, edgeMaxAge, stressLearningRate, stressThreshold, growthInterval );

        GrowingNeuralGas cl = new GrowingNeuralGas( ALGORITHM, om );
        cl.setup(gngc);

//        CallbackCollection cc = new CallbackCollection();
//
//        cc.add( this );
//        cc.add( cl );

        run( epochs, batch, meanErrorThreshold );
    }

    public GrowingNeuralGas getAlgorithm() {
        ObjectMap om = ObjectMap.GetInstance();
        GrowingNeuralGas gng = (GrowingNeuralGas)om.get( ALGORITHM );
        return gng;
    }

    public float getDiscreteRandom() {
        float gridSize = 6.f;
        float x = (float)RandomInstance.random() * ( gridSize - 0.f );
        int nx = (int)x;
        x = (float)nx;
        x /= gridSize;
        x += ((1.f / ( gridSize )) * 0.5f );
        return x;
    }

    public void call() {
        // 1. generate input data
        float x = getDiscreteRandom();
        float y = getDiscreteRandom();

        // 2. update algorithm
        GrowingNeuralGas cl = getAlgorithm();
        cl._inputValues._values[ 0 ] = x;
        cl._inputValues._values[ 1 ] = y;
        cl.call();
    }

    public int run( int epochs, int batch, float meanErrorThreshold ) {

        // perform tests in batches until the mean error for a batch is below threshold.
        // Otherwise, fail test.
        for( int epoch = 0; epoch < epochs; ++epoch ) {

            float sumError = 0.f;

            for( int test = 0; test < batch; ++test ) {
                float error = step();
                sumError += error;
            }

            float meanError = 0.f;

            if( sumError > 0.f ) {
                meanError = sumError / (float)batch;
            }

            System.out.println( "Epoch: " +epoch+ " Mean error: " + meanError );

            if( meanError < meanErrorThreshold ) {
                System.out.println( "Success: Error below threshold for epoch." );
                return 0;
            }
        }

        System.out.println( "Failure: Error did not go below threshold for any epoch." );
        return -1;
    }

    public float step() {

        call();

        GrowingNeuralGas cl = getAlgorithm();

        Data input = cl.getInput();
        Data weights = cl._cellWeights;

        int inputs = input.getSize();
        int bestCell = cl.getBestCell();

        float sumError = 0.f;

        for( int i = 0; i < inputs; ++i ) {
            int offset = bestCell * inputs + i;
            float x = input._values[ i ];
            float w = weights._values[ offset ];
            float d = Math.abs( x - w );
            sumError += d;
        }

        float errorValue = sumError / (float)inputs;

        float x = input._values[ 0 ];
        float y = input._values[ 1 ];

//        System.out.println( "Input: " +x+ "," +y+ " Error: " + errorValue );

        return errorValue;
    }

}
/*
public class CompetitiveLearningDemoPanel extends IterativelyPaintablePanel {//JPanel implements Callback, MouseListener {

    int _markSize = 16;
    int _size = 100;
    CompetitiveLearning _cl;
    CompetitiveLearningDemo _cld;

    public CompetitiveLearningDemoPanel() {

    }

    public void setup(
            int size,
            CompetitiveLearningDemo cld ) {
        _size = size;
        _cl = cld._cl;
        _cld = cld;
    }

    public Dimension getPreferredSize() {
        return new Dimension( getContentWidth(), getContentHeight() );
    }

    public int getContentHeight() {
        return _size;
    }

    public int getContentWidth() {
        return _size;
    }

    public void paintComponent( Graphics g ) {
        Graphics2D g2d = (Graphics2D)g;
        paint( g2d );
    }

    public void paint( Graphics2D g2d ) {

        // paint all the weights
        // then paint the current point
        g2d.setColor( Color.DARK_GRAY );
        int cw = getContentWidth();
        int ch = getContentHeight();
        g2d.fillRect( 0,0, cw, ch );

        g2d.setStroke( new BasicStroke( 2 ) );

        ValueRange errorRange = _cl._cellErrors.getValueRange();

        int cells = _cl._c._w * _cl._c._h;
        int inputs = _cl._c._i;
        int halfMark = _markSize / 2;

        for( int y = 0; y < _cl._c._h; ++y ) {
            for( int x = 0; x < _cl._c._w; ++x ) {

                int cell = y * _cl._c._w + x;

                float wx = _cl._cellWeights._values[ cell * inputs +0 ];
                float wy = _cl._cellWeights._values[ cell * inputs +1 ];

                float mask = _cl._cellMask._values[ cell ];
                float activity = _cl._cellActivity._values[ cell ];
                float error = (float)(( _cl._cellErrors._values[ cell ] - errorRange._min ) / errorRange.range() );

                int activityByte = Maths.realUnit2Byte( activity );
                int errorByte = Maths.realUnit2Byte( error );
                int maskByte = Maths.realUnit2Byte( mask );

                int px = (int)( wx * _size );
                int py = (int)( wy * _size );
                g2d.setColor( new Color( errorByte, maskByte, 0 ) );
                g2d.fillOval( px-halfMark, py-halfMark, _markSize, _markSize );
                g2d.setColor( new Color( activityByte, activityByte, 0 ) );
                g2d.drawOval( px-halfMark, py-halfMark, _markSize, _markSize );
            }
        }

        float wx = _cl._inputValues._values[ 0 ];
        float wy = _cl._inputValues._values[ 1 ];
        int px = (int)( wx * _size );
        int py = (int)( wy * _size );

        g2d.setColor( Color.CYAN );
        g2d.drawLine( px-halfMark, py, px+halfMark, py );
        g2d.drawLine( px, py-halfMark, px, py+halfMark );
    }
}*/