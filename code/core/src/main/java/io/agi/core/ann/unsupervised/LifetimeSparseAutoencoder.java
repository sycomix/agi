/*
 * Copyright (c) 2017.
 *
 * This file is part of Project AGI. <http://agi.io>
 *
 * Project AGI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Project AGI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Project AGI.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.agi.core.ann.unsupervised;

import io.agi.core.ann.supervised.BackPropagation;
import io.agi.core.data.Data;
import io.agi.core.data.Ranking;
import io.agi.core.math.Useful;
import io.agi.core.orm.ObjectMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.TreeMap;

/**
 * A variation on "Winner-Take-All Autoencoders" by Alireza Makhzani, Brendan Frey. Their system is a convolutional
 * autoencoder with ReLU and enforced spatial and lifetime sparsity.
 *
 * The changes are that this doesn't have the separate deconvolution process, so is more similar to the K-Sparse auto
 * encoder. However, they warn that this may cause the convolutional use of this to produce lots of compressed copies
 * of the input, and then decompress with deconvolution.
 *
 * Created by dave on 1/07/16.
 */
public class LifetimeSparseAutoencoder extends CompetitiveLearning {

    protected static final Logger logger = LogManager.getLogger();

    public LifetimeSparseAutoencoderConfig _c;
    public Data _inputValues;
    public Data _inputReconstruction;
    public Data _cellWeights;
    public Data _cellBiases1;
    public Data _cellBiases2;
    public Data _cellWeightsVelocity;
    public Data _cellBiases1Velocity;
    public Data _cellBiases2Velocity;
    public Data _cellErrors;
    public Data _cellWeightedSum;
    public Data _cellSpikes;

//    public Data _outputErrors; // was inputGradients
//    public Data _hiddenErrors; // was cellGradients

    public Data _batchOutputOutput;
    public Data _batchOutputInput;
    public Data _batchOutputInputLifetime;
    public Data _batchOutputErrors;
    public Data _batchHiddenInput;
    public Data _batchHiddenWeightedSum;
    public Data _batchHiddenErrors;

    public LifetimeSparseAutoencoder( String name, ObjectMap om ) {
        super( name, om );
    }

    public void setInput( Data input ) {
        _inputValues.copy( input );
    }

    public Data getInput() {
        return _inputValues;
    }

    public void setup( LifetimeSparseAutoencoderConfig c ) {
        _c = c;

        int batchSize = c.getBatchSize();
        int inputs = c.getNbrInputs();
        int w = c.getWidthCells();
        int h = c.getHeightCells();
        int cells = w * h;

        _inputValues = new Data( inputs );
        _inputReconstruction = new Data( inputs );
        _cellWeights = new Data( w, h, inputs );
        _cellBiases1 = new Data( w, h );
        _cellBiases2 = new Data( inputs );
        _cellWeightsVelocity = new Data( w, h, inputs );
        _cellBiases1Velocity = new Data( w, h );
        _cellBiases2Velocity = new Data( inputs );
        _cellErrors = new Data( w, h );
        _cellWeightedSum = new Data( w, h );
        _cellSpikes = new Data( w, h );

//        _outputErrors = new Data( inputs );
//        _hiddenErrors = new Data( w, h );

        _batchOutputOutput = new Data( inputs, batchSize );
        _batchOutputInput = new Data(  cells, batchSize );
        _batchOutputInputLifetime = new Data(  cells, batchSize );
        _batchOutputErrors = new Data( inputs, batchSize );
        _batchHiddenInput = new Data( inputs, batchSize );
        _batchHiddenWeightedSum = new Data( cells, batchSize );
        _batchHiddenErrors = new Data(  cells, batchSize );
    }

    public static int FindOutputSparsity( int sparsity, float outputSparsityFactor ) {
        int outputSparsity = (int)( (float)sparsity * outputSparsityFactor );
        return outputSparsity;
    }

    public void reset() {

        _c.setBatchCount( 0 );
//        _outputErrors.set( 0f );
//        _hiddenErrors.set( 0f );

        _batchOutputInput.set( 0f );
        _batchOutputErrors.set( 0f );
        _batchHiddenInput.set( 0f );
        _batchHiddenErrors.set( 0f );

        _cellWeightsVelocity.set( 0f );
        _cellBiases1Velocity.set( 0f );
        _cellBiases2Velocity.set( 0f );

        float weightsStdDev = _c.getWeightsStdDev();

        for( int i = 0; i < _cellWeights.getSize(); ++i ) {
            double r = _c._r.nextGaussian(); // mean: 0, SD: 1
            r *= weightsStdDev;
            _cellWeights._values[ i ] = (float)r;// / sqRtInputs;
        }

        for( int i = 0; i < _cellBiases1.getSize(); ++i ) {
            double r = _c._r.nextGaussian(); // mean: 0, SD: 1
            r *= weightsStdDev;
            _cellBiases1._values[ i ] = (float)r;
        }

        for( int i = 0; i < _cellBiases2.getSize(); ++i ) {
            double r = _c._r.nextGaussian(); // mean: 0, SD: 1
            r *= weightsStdDev;
            _cellBiases2._values[ i ] = (float)r;
        }
    }

    public void update() {
        boolean learn = _c.getLearn();
        update( learn );
    }

//    protected void encode() {
//        int sparsity = _c.getSparsityOutput();
//        encode( _inputValues, _cellWeights, _cellBiases1, _cellWeightedSum, _cellSpikes, sparsity );
//    }

    public static Collection< Integer > encode(
        Data inputValues,
        Data cellWeights,
        Data cellBiases1,
        Data cellWeightedSum,
        Data cellSpikes,
        int sparsity
    ) {
        // Hidden layer (forward pass)
        TreeMap< Float, ArrayList< Integer > > ranking = new TreeMap< Float, ArrayList< Integer > >();

        int inputs = inputValues.getSize();//_c.getNbrInputs();
        int cells = cellSpikes.getSize();//_c.getNbrCells();
//        int sparsity = _c.getSparsity();

        for( int c = 0; c < cells; ++c ) {
            float sum = 0.f;

            for( int i = 0; i < inputs; ++i ) {

                int offset = c * inputs +i;

                float input = inputValues._values[ i ];
                float weight = cellWeights._values[ offset ];
                float product = input * weight;
                sum += product;
            }

            float bias = cellBiases1._values[ c ];

            sum += bias;

            cellWeightedSum._values[ c ] = sum;

            Ranking.add( ranking, sum, c );
        }

        // Hidden Layer Nonlinearity: Make all except top k cells zero.
        boolean findMaxima = true;
        int maxRank = sparsity;
        ArrayList< Integer > activeCells = Ranking.getBestValues( ranking, findMaxima, maxRank );
        cellSpikes.set( 0.f );
        for( Integer c : activeCells ) {
            float transfer = cellWeightedSum._values[ c ]; // otherwise zero
            cellSpikes._values[ c ] = transfer;
        }

        return activeCells;
    }

    public static Collection< Integer > encodeWithMask(
            Data inputValues,
            Data cellMask,
            Data cellWeights,
            Data cellBiases1,
            Data cellWeightedSum,
            Data cellSpikes,
            int sparsity
    ) {
        // Hidden layer (forward pass)
        TreeMap< Float, ArrayList< Integer > > ranking = new TreeMap< Float, ArrayList< Integer > >();

        int inputs = inputValues.getSize();//_c.getNbrInputs();
        int cells = cellSpikes.getSize();//_c.getNbrCells();
//        int sparsity = _c.getSparsity();

        for( int c = 0; c < cells; ++c ) {
            float sum = 0.f;

            for( int i = 0; i < inputs; ++i ) {

                int offset = c * inputs +i;

                float input = inputValues._values[ i ];
                float weight = cellWeights._values[ offset ];
                float product = input * weight;
                sum += product;
            }

            float bias = cellBiases1._values[ c ];

            sum += bias;

            cellWeightedSum._values[ c ] = sum;

            // only rank the values of cells that are masked-in
            float maskValue = cellMask._values[ c ];
            if( maskValue > 0f ) {
                Ranking.add( ranking, sum, c );
            }
        }

        // Hidden Layer Nonlinearity: Make all except top k cells zero.
        boolean findMaxima = true;
        int maxRank = sparsity;
        ArrayList< Integer > activeCells = Ranking.getBestValues( ranking, findMaxima, maxRank );
        cellSpikes.set( 0.f );
        for( Integer c : activeCells ) {
            float transfer = cellWeightedSum._values[ c ]; // otherwise zero
            cellSpikes._values[ c ] = transfer;
        }

        return activeCells;
    }

    public void update( boolean learn ) {
//        encode();
        int sparsityOutput = _c.getSparsityOutput();
        encode( _inputValues, _cellWeights, _cellBiases1, _cellWeightedSum, _cellSpikes, sparsityOutput );


        // Output layer (forward pass)
        // dont really need to do this if not learning.
        _inputReconstruction.setSize( _inputValues._dataSize ); // copy the size of the current input
        decode( _c, _cellWeights, _cellBiases2, _cellSpikes, _inputReconstruction ); // for output

        // don't go any further unless learning is enabled
        if( !learn ) {
            return;
        }

        int sparsityTraining = _c.getSparsity();
        Data cellWeightedSumTraining = new Data( _cellWeightedSum._dataSize );
        Data cellSpikesTraining = new Data( _cellSpikes._dataSize );
        encode( _inputValues, _cellWeights, _cellBiases1, cellWeightedSumTraining, cellSpikesTraining, sparsityTraining );

        Data hiddenLayerInput = _inputValues;
        Data hiddenLayerWeightedSum = cellWeightedSumTraining;//_cellWeightedSum;
        Data outputLayerInput = cellSpikesTraining;//_cellSpikes;

        train( hiddenLayerInput, hiddenLayerWeightedSum, outputLayerInput );
    }

    public void train(
        Data hiddenLayerInput,
        Data hiddenLayerWeightedSum,
        Data outputLayerInput ) {
        int batchSize = _c.getBatchSize();
        int batchCount = _c.getBatchCount();

        Data hiddenLayerWeightedSumBatch = _batchHiddenWeightedSum;
        Data hiddenLayerInputBatch = _batchHiddenInput;
        Data hiddenLayerErrorBatch = _batchHiddenErrors;
        Data outputLayerInputBatch = _batchOutputInput;
        Data outputLayerInputBatchLifetime = _batchOutputInputLifetime;
        Data outputLayerErrorBatch = _batchOutputErrors;
        Data outputLayerOutputBatch = _batchOutputOutput;

        batchAccumulate(
                _c,
                hiddenLayerInput,
                hiddenLayerWeightedSum,
                outputLayerInput,
//                outputLayerOutput,
                hiddenLayerInputBatch,
                hiddenLayerWeightedSumBatch,
                outputLayerInputBatch,
//                outputLayerOutputBatch,
                batchCount );

        // decide whether to learn or accumulate more gradients first (mini batch)
        batchCount += 1;

        if( batchCount < batchSize ) { // e.g. if was zero, then becomes 1, then we clear it and apply the gradients
            _c.setBatchCount( batchCount );
            return; // end update
        }

        // add the winning cells from each column PLUS the
        // Also calculates the output layer output
        boolean batchSelectGreedy = false;
        if( batchSelectGreedy ) {
            batchSelectHiddenCells(
                    this._c,
                    this._cellWeights,
                    this._cellBiases2,
                    hiddenLayerWeightedSumBatch, // raw unfiltered output of hidden layer cells
                    outputLayerInputBatch, // original winning cells
                    outputLayerInputBatchLifetime, // calculated: original winning cells AND lifetime sparsity winning cells
                    outputLayerOutputBatch ); //
        }
        else {
            // batch select max error
            boolean trainWinners = true;
            batchSelectHiddenCellsWithMask(
                    this,
                    this._cellWeights,
                    this._cellBiases2,
                    hiddenLayerInputBatch,
                    null, // hidden layer mask batch
                    hiddenLayerWeightedSumBatch, // raw unfiltered output of hidden layer cells
                    outputLayerInputBatch, // original winning cells
                    outputLayerInputBatchLifetime, // calculated: original winning cells AND lifetime sparsity winning cells
                    outputLayerOutputBatch,
                    trainWinners );
        }

        batchBackpropagateError(
                _c,
                _cellWeights,
                hiddenLayerInputBatch,
                hiddenLayerErrorBatch, // calculated
                outputLayerInputBatchLifetime,
                outputLayerErrorBatch, // calculated
                outputLayerOutputBatch );

        batchTrain(
                _c,
                _cellWeights,
                _cellWeightsVelocity,
                _cellBiases1,
                _cellBiases2,
                _cellBiases1Velocity,
                _cellBiases2Velocity,
                hiddenLayerInputBatch,
                hiddenLayerErrorBatch,
//                outputLayerInputBatch, ???? bug ????
                outputLayerInputBatchLifetime,
                outputLayerErrorBatch );

        _c.setBatchCount( 0 );

        // Clear the accumulated gradients
        hiddenLayerInputBatch.set( 0f );
        hiddenLayerErrorBatch.set( 0f );
        hiddenLayerWeightedSumBatch.set( 0f );

        outputLayerInputBatch.set( 0f );
        outputLayerInputBatchLifetime.set( 0f );
        outputLayerErrorBatch.set( 0f );
        outputLayerOutputBatch.set( 0f );
    }

//    Data outputLayerOutputBatch = _batchOutputOutput;

    public static void batchTrain(
            LifetimeSparseAutoencoderConfig config,
            Data cellWeights,
            Data cellWeightsVelocity,
            Data cellBiases1,
            Data cellBiases2,
            Data cellBiases1Velocity,
            Data cellBiases2Velocity,
            Data hiddenLayerInputBatch,
            Data hiddenLayerErrorBatch,
            Data outputLayerInputBatch,
            Data outputLayerErrorBatch ) {

        float learningRate = config.getLearningRate();
        float momentum = config.getMomentum();
        int inputs = config.getNbrInputs();
        int cells = config.getNbrCells();
        int batchSize = config.getBatchSize();

        // now gradient descent in the hidden->output layer
        int inputSize = cells;
        int layerSize = inputs;
        boolean weightsInputMajor = true;

        KSparseAutoencoder.StochasticGradientDescent(
                inputSize, layerSize, batchSize, learningRate, momentum, weightsInputMajor,
                outputLayerInputBatch, outputLayerErrorBatch,
                cellWeights, cellWeightsVelocity, cellBiases2, cellBiases2Velocity );

        // now gradient descent in the input->hidden layer. can't skip this because we need to update the biases
        inputSize = inputs;
        layerSize = cells;
        weightsInputMajor = false;

        KSparseAutoencoder.StochasticGradientDescent(
                inputSize, layerSize, batchSize, learningRate, momentum, weightsInputMajor,
                hiddenLayerInputBatch, hiddenLayerErrorBatch,
                cellWeights, cellWeightsVelocity, cellBiases1, cellBiases1Velocity );

//        System.err.println( "Age: " + this._c.getAge() + " Sparsity: " + k  + " vMax = " + vMax );
    }

    public static void batchAccumulate(
            LifetimeSparseAutoencoderConfig config,
            Data hiddenLayerInput,
            Data hiddenLayerWeightedSum,
            Data outputLayerInput,
//            Data outputLayerOutput,

            Data hiddenLayerInputBatch,
            Data hiddenLayerWeightedSumBatch,
            Data outputLayerInputBatch,
//            Data outputLayerOutputBatch,

            int batchIndex ) {

        int inputs = config.getNbrInputs();
        int cells = config.getNbrCells();

        // accumulate the error gradients and inputs over the batch
        int b = batchIndex;

        for( int i = 0; i < cells; ++i ) {
            float r = outputLayerInput._values[ i ];
            int batchOffset = b * cells + i;
            outputLayerInputBatch._values[ batchOffset ] = r;
        }

        for( int i = 0; i < inputs; ++i ) {
            float r = hiddenLayerInput._values[ i ];
            int batchOffset = b * inputs + i;
            hiddenLayerInputBatch._values[ batchOffset ] = r;
        }

        for( int i = 0; i < cells; ++i ) {
            float r = hiddenLayerWeightedSum._values[ i ];
            int batchOffset = b * cells + i;
            hiddenLayerWeightedSumBatch._values[ batchOffset ] = r;
        }
    }

    public static void batchBackpropagateError(
            LifetimeSparseAutoencoderConfig config,
            Data cellWeights,
            Data hiddenLayerInputBatch,
            Data hiddenLayerErrorBatch, // calculated
            Data outputLayerInputBatch,
            Data outputLayerErrorBatch, // calculated
            Data outputLayerOutputBatch ) {

        int inputs = config.getNbrInputs();
        int cells = config.getNbrCells();
        int batchSize = config.getBatchSize();

//        float minValE = 0f;
//        float maxValE = 0f;
//        float minValD = 0f;
//        float maxValD = 0f;
//        float minValW = 0f;
//        float maxValW = 0f;

        for( int b = 0; b < batchSize; ++b ) {

            // OUTPUT LAYER
            // d output layer
            for( int i = 0; i < inputs; ++i ) {
                int batchOffset = b * inputs + i;
                float target = hiddenLayerInputBatch._values[ batchOffset ]; // y
                float output = outputLayerOutputBatch._values[ batchOffset ]; // a
                float error = output - target; // == d^L
                //float weightedSum = output; // z
                float derivative = 1f;//(float)TransferFunction.logisticSigmoidDerivative( weightedSum );
                outputLayerErrorBatch._values[ batchOffset ] = error * derivative; // eqn 30
//                maxValE = Math.max( maxValE, error );
//                minValE = Math.min( minValE, error );
            }

            // HIDDEN LAYER
            // compute gradient in hidden units. Derivative is either 1 or 0 depending whether the cell was filtered.
            for( int c = 0; c < cells; ++c ) { // computing error for each "input"
                float sum = 0.f;
                int batchOffsetCell = b * cells + c;

                float transferTopK = outputLayerInputBatch._values[ batchOffsetCell ];
                float derivative = 1f;//(float)TransferFunction.logisticSigmoidDerivative( weightedSum );

                if( transferTopK > 0f ) { // if was cell active
                    for( int i = 0; i < inputs; ++i ) {
                        //int offset = j * K + k; // K = inputs, storage is all inputs adjacent
                        int offset = c * inputs + i;
                        float w = cellWeights._values[ offset ];
                        //float d = dOutput._values[ i ]; // d_j i.e. partial derivative of loss fn with respect to the activation of j
                        int batchOffsetInput = b * inputs + i;
                        float d = outputLayerErrorBatch._values[ batchOffsetInput ]; // d_j i.e. partial derivative of loss fn with respect to the activation of j
                        float product = d * w;// + ( l2R * w );
//                        maxValD = Math.max( maxValD, d );
//                        minValD = Math.min( minValD, d );
//                        maxValW = Math.max( maxValW, w );
//                        minValW = Math.min( minValW, w );
                        product = BackPropagation.ClipErrorGradient( product, 10.f );

                        // TODO add gradient clipping
                        if( Useful.IsBad( product ) ) {
                            String error = "Autoencoder error derivative update produced a bad value: " + product;
                            logger.error( error );
                            logger.traceExit();
                            System.exit( -1 );
                        }

                        sum += product;
                    }

                    // with linear neurons, derivative is 1, but here it is nonlinear now
                    sum *= derivative;  // eqn (BP2)
                }
                // else: derivative is zero when filtered

                //dHidden._values[ c ] = sum;
                hiddenLayerErrorBatch._values[ batchOffsetCell ] = sum;
            } // cells
        } // batch index
//        System.err.println( "Batch gradient E range : " + minValE + " / " + maxValE + " W range: " + minValW + " / " + maxValW + " D range: " + minValD + " / " + maxValD );
    }

    public static void backpropagateError(
            LifetimeSparseAutoencoderConfig config,
            Data cellWeights,
            Data hiddenLayerInput,
            Data hiddenLayerError, // calculated
            Data outputLayerInput,
            Data outputLayerError, // calculated
            Data outputLayerOutput ) {

        int inputs = config.getNbrInputs();
        int cells = config.getNbrCells();
        int b = 0;

            // OUTPUT LAYER
            // d output layer
            for( int i = 0; i < inputs; ++i ) {
                int batchOffset = b * inputs + i;
                float target = hiddenLayerInput._values[ batchOffset ]; // y
                float output = outputLayerOutput._values[ batchOffset ]; // a
                float error = output - target; // == d^L
                //float weightedSum = output; // z
                float derivative = 1f;//(float)TransferFunction.logisticSigmoidDerivative( weightedSum );
                outputLayerError._values[ batchOffset ] = error * derivative; // eqn 30
            }

            // HIDDEN LAYER
            // compute gradient in hidden units. Derivative is either 1 or 0 depending whether the cell was filtered.
            for( int c = 0; c < cells; ++c ) { // computing error for each "input"
                float sum = 0.f;
                int batchOffsetCell = b * cells + c;

                float transferTopK = outputLayerInput._values[ batchOffsetCell ];
                float derivative = 1f;//(float)TransferFunction.logisticSigmoidDerivative( weightedSum );

                if( transferTopK > 0f ) { // if was cell active
                    for( int i = 0; i < inputs; ++i ) {
                        //int offset = j * K + k; // K = inputs, storage is all inputs adjacent
                        int offset = c * inputs + i;
                        float w = cellWeights._values[ offset ];
                        //float d = dOutput._values[ i ]; // d_j i.e. partial derivative of loss fn with respect to the activation of j
                        int batchOffsetInput = b * inputs + i;
                        float d = outputLayerError._values[ batchOffsetInput ]; // d_j i.e. partial derivative of loss fn with respect to the activation of j
                        float product = d * w;// + ( l2R * w );
//                        maxValD = Math.max( maxValD, d );
//                        minValD = Math.min( minValD, d );
//                        maxValW = Math.max( maxValW, w );
//                        minValW = Math.min( minValW, w );
                        product = BackPropagation.ClipErrorGradient( product, 10.f );

                        // TODO add gradient clipping
                        if( Useful.IsBad( product ) ) {
                            String error = "Autoencoder error derivative update produced a bad value: " + product;
                            logger.error( error );
                            logger.traceExit();
                            System.exit( -1 );
                        }

                        sum += product;
                    }

                    // with linear neurons, derivative is 1, but here it is nonlinear now
                    sum *= derivative;  // eqn (BP2)
                }
                // else: derivative is zero when filtered

                //dHidden._values[ c ] = sum;
                hiddenLayerError._values[ batchOffsetCell ] = sum;
            } // cells

    }

    /**
     * Selects idle cells and trains them towards the batch input samples they were most similar to.
     * @param config
     * @param cellWeights
     * @param cellBiases2
     * @param hiddenLayerActivityBatch
     * @param hiddenLayerSpikesBatch
     * @param outputLayerInputBatch
     * @param outputLayerOutputBatch
     */
    public static void batchSelectHiddenCells(
            LifetimeSparseAutoencoderConfig config,
            Data cellWeights,
            Data cellBiases2,
            Data hiddenLayerActivityBatch, // pre-binarization of winners ie weighted sums
            Data hiddenLayerSpikesBatch, // original winning cells
            Data outputLayerInputBatch, // calculated
            Data outputLayerOutputBatch ) { // calculated

        // filter all except top-k activations for
        int inputs = config.getNbrInputs();
        int cells = config.getNbrCells();
        int batchSize = config.getBatchSize();
        int sparsityLifetime = config.getSparsityLifetime(); // different, because related to batch size

        outputLayerInputBatch.set( 0f );

        // add all the spikes we found based on winner-take-all
        for( int b = 0; b < batchSize; ++b ) {
            for( int i = 0; i < cells; ++i ) {
                int batchOffset = b * cells + i;
                float r = hiddenLayerSpikesBatch._values[ batchOffset ];
                if( r > 0f ) {
                    float transfer = hiddenLayerActivityBatch._values[ batchOffset ];
//                    outputLayerInputBatch._values[ batchOffset ] = 1f;
                    outputLayerInputBatch._values[ batchOffset ] = transfer;
                }
            }
        }

        // accumulate the error gradients and inputs over the batch
        if( sparsityLifetime > 0 ) {
            for( int i = 0; i < cells; ++i ) {

                // find the top k
                TreeMap< Float, ArrayList< Integer > > ranking = new TreeMap< Float, ArrayList< Integer > >();

                for( int b = 0; b < batchSize; ++b ) {
                    int batchOffset = b * cells + i;
                    float r = hiddenLayerActivityBatch._values[ batchOffset ];
                    Ranking.add( ranking, r, b );
                }

                // rank the batch responses for this hidden unit
                HashSet< Integer > bestBatchIndices = new HashSet< Integer >();
                int maxRank = sparsityLifetime;
                boolean findMaxima = true; // biggest activity
                Ranking.getBestValuesRandomTieBreak( ranking, findMaxima, maxRank, bestBatchIndices, config._r );

                // Set hidden activation to zero for all other batch indices, and 1 for the best
                for( int b = 0; b < batchSize; ++b ) {
                    int batchOffset = b * cells + i;
                    float oldActivity = outputLayerInputBatch._values[ batchOffset ];
                    float newActivity = oldActivity;
                    if( bestBatchIndices.contains( b ) ) {
                        float transfer = hiddenLayerActivityBatch._values[ batchOffset ];
                        //                    newActivity = 1f;
                        newActivity = transfer;
                    }

                    // should get the old value here.. ie the winner PLUS the lifetime sparsity bits
                    outputLayerInputBatch._values[ batchOffset ] = newActivity;
                }
            }
        }

        // Now calculate the output based on this new pattern of hidden layer activity
        Data outputLayerInput = new Data( cells );
        Data outputLayerOutput = new Data( inputs );

        for( int b = 0; b < batchSize; ++b ) {
            int offsetThis = 0;
            int offsetThat = b * cells;
            outputLayerInput.copyRange( outputLayerInputBatch, offsetThis, offsetThat, cells );
            decode( config, cellWeights, cellBiases2, outputLayerInput, outputLayerOutput ); // for output
            offsetThis = b * inputs;
            offsetThat = 0;
            outputLayerOutputBatch.copyRange( outputLayerOutput, offsetThis, offsetThat, inputs );
        }

    }

    /**
     * Selects idle cells and trains them towards the batch input samples that had the greatest reconstruction error.
     * @param encoder
     * @param cellWeights
     * @param cellBiases2
     * @param hiddenLayerInputBatch
     * @param hiddenLayerMaskBatch
     * @param hiddenLayerWeightedSumsBatch
     * @param hiddenLayerSpikesBatch
     * @param outputLayerInputBatch
     * @param outputLayerOutputBatch
     * @param trainWinners
     */
    public static void batchSelectHiddenCellsWithMask(
            LifetimeSparseAutoencoder encoder,
            Data cellWeights,
            Data cellBiases2,
            Data hiddenLayerInputBatch,
            Data hiddenLayerMaskBatch, // mask 1 = cell is allowed to fire for given input
            Data hiddenLayerWeightedSumsBatch, // pre-binarization of winners ie weighted sums
            Data hiddenLayerSpikesBatch, // original winning cells
            Data outputLayerInputBatch, // calculated
            Data outputLayerOutputBatch,
            boolean trainWinners ) { // calculated

        // filter all except top-k activations for
        LifetimeSparseAutoencoderConfig config = encoder._c;
        int inputs = config.getNbrInputs();
        int cells = config.getNbrCells();
        int batchSize = config.getBatchSize();
        int sparsityLifetime = config.getSparsityLifetime(); // different, because related to batch size

        outputLayerInputBatch.set( 0f );


        // work out which inputs in the batch caused the greatest reconstruction error.
        // these are our targets for idle cells
        TreeMap< Float, ArrayList< Integer > > sumReconstructionErrorRanking = new TreeMap< Float, ArrayList< Integer > >();
        for( int b = 0; b < batchSize; ++b ) {
            float sumErrorSq = 0f;

            for( int i = 0; i < inputs; ++i ) {
                int batchOffset = b * inputs + i;
                float  inputValue = hiddenLayerInputBatch._values[ batchOffset ];
                float outputValue = outputLayerOutputBatch._values[ batchOffset ];

                float errorSq = ( inputValue - outputValue ) * ( inputValue - outputValue );
                sumErrorSq += errorSq;
            }
            Ranking.add( sumReconstructionErrorRanking, sumErrorSq, b );
        }

        ArrayList< Integer > maxErrorBatchIndices = new ArrayList< Integer >();
        int maxRank1 = batchSize;//sparsityLifetime; // i.e. up to X inputs will be trained for by all inactive or underactive cells
        boolean findMaxima1 = true; // find max error
        Ranking.getBestValuesRandomTieBreak( sumReconstructionErrorRanking, findMaxima1, maxRank1, maxErrorBatchIndices, config._r );


        // train all idle cells towards all the inputs that had the worst reconstruction error
        for( int cell = 0; cell < cells; ++cell ) {

            // count the number of "wins" each cell has had
            int wins = 0;
            for( int b = 0; b < batchSize; ++b ) {
                int batchOffset = b * cells + cell;
                float r = hiddenLayerSpikesBatch._values[ batchOffset ];
                if( r > 0f ) {
                    ++wins;
                    float activity = hiddenLayerWeightedSumsBatch._values[ batchOffset ];
                    // pre-add or make cells individually responsible?
                    if( trainWinners ) {
                        outputLayerInputBatch._values[ batchOffset ] = activity; // <---- pre-add existing wins
                    }
                }
            }

            // rank the batch responses for this hidden unit
            // say batchSparsity = 5. Wins = 2.
            // extraWins = 5 - 2 = 3
            int extraWins = Math.max( 0, sparsityLifetime - wins );
            int leastActiveIdx = 0;

            while( extraWins > 0 ) {
                for( ; leastActiveIdx < maxErrorBatchIndices.size(); ++leastActiveIdx ) {
                    int b = maxErrorBatchIndices.get( leastActiveIdx );
                    int batchOffset = b * cells + cell;

                    // mask cells that can't respond to this input
                    if( hiddenLayerMaskBatch != null ) {
                        float mask = hiddenLayerMaskBatch._values[ batchOffset ];
                        if( mask < 1f ) {
                            continue; // this cell not allowed to fire
                        }
                    }

                    // this cell CAN fire for this batch index
                    float transfer = hiddenLayerWeightedSumsBatch._values[ batchOffset ];
                    outputLayerInputBatch._values[ batchOffset ] = transfer;
                    --extraWins;
                }
                break; // couldn't find enough valid samples to train this cell on this batch
            }
        }


        // DECODE
        // Now calculate the output based on this new pattern of hidden layer activity
        Data outputLayerInput = new Data( cells );
        Data outputLayerOutput = new Data( inputs );

        for( int b = 0; b < batchSize; ++b ) {
            int offsetThis = 0;
            int offsetThat = b * cells;
            outputLayerInput.copyRange( outputLayerInputBatch, offsetThis, offsetThat, cells );
            encoder.decode( config, cellWeights, cellBiases2, outputLayerInput, outputLayerOutput ); // for output
            offsetThis = b * inputs;
            offsetThat = 0;
            outputLayerOutputBatch.copyRange( outputLayerOutput, offsetThis, offsetThat, inputs );
        }

    }

/*    public static void batchSelectHiddenCellsMinActivity(
            LifetimeSparseAutoencoderConfig config,
            Data cellWeights,
            Data cellBiases2,
            Data hiddenLayerActivityBatch, // pre-binarization of winners ie weighted sums
            Data hiddenLayerSpikesBatch, // original winning cells
            Data outputLayerInputBatch, // calculated
            Data outputLayerOutputBatch ) { // calculated

        // filter all except top-k activations for
        int inputs = config.getNbrInputs();
        int cells = config.getNbrCells();
        int batchSize = config.getBatchSize();
        int sparsityLifetime = config.getSparsityLifetime(); // different, because related to batch size

        outputLayerInputBatch.set( 0f );

        // figure out which inputs were least recognized
        TreeMap< Float, ArrayList< Integer > > sumHiddenActivityRanking = new TreeMap< Float, ArrayList< Integer > >();
        for( int b = 0; b < batchSize; ++b ) {
            float sum = 0f;
            for( int i = 0; i < cells; ++i ) {
                int batchOffset = b * cells + i;
                // Consider only the winning set?
                float r = hiddenLayerSpikesBatch._values[ batchOffset ];
                if( r > 0f ) {
                    float activity = hiddenLayerActivityBatch._values[ batchOffset ];
                    sum += activity;
                }
            }
            Ranking.add( sumHiddenActivityRanking, sum, b );
        }

        HashSet< Integer > leastActiveBatchIndices = new HashSet< Integer >();
        int maxRank1 = sparsityLifetime; // i.e. up to X inputs will be trained for by all inactive or underactive cells
        boolean findMaxima1 = false; // min activity suggests poorly modelled or under-represented input
        Ranking.getBestValuesRandomTieBreak( sumHiddenActivityRanking, findMaxima1, maxRank1, leastActiveBatchIndices, config._r );

        // for each cell, work out which of the batch input samples that were poorly responded to that it matches best.
        // train towards these.
        for( int i = 0; i < cells; ++i ) {

            // count the number of "wins" each cell has had
            int wins = 0;
            for( int b = 0; b < batchSize; ++b ) {
                int batchOffset = b * cells + i;
                float r = hiddenLayerSpikesBatch._values[ batchOffset ];
                if( r > 0f ) {
                    ++wins;
                    float activity = hiddenLayerActivityBatch._values[ batchOffset ];
                    outputLayerInputBatch._values[ batchOffset ] = activity; // pre-add existing wins
                }
            }

            TreeMap< Float, ArrayList< Integer > > cellHiddenActivityRanking = new TreeMap< Float, ArrayList< Integer > >();
            for( Integer b : leastActiveBatchIndices ) {
                int batchOffset = b * cells + i;
                float r = hiddenLayerActivityBatch._values[ batchOffset ];
                Ranking.add( cellHiddenActivityRanking, r, b );
            }

            // rank the batch responses for this hidden unit
            // say batchSparsity = 5. Wins = 2.
            // extraWins = 5 - 2 = 3
            int extraWins = Math.max( 0, sparsityLifetime - wins );
            if( extraWins < 1 ) {
                continue; // we pre-added the genuine wins so don't need to do anything
            }

            HashSet< Integer > cellMostActiveBatchIndices = new HashSet< Integer >();
            int maxRank2 = extraWins;
            boolean findMaxima2 = true; // biggest activity
            Ranking.getBestValuesRandomTieBreak( cellHiddenActivityRanking, findMaxima2, maxRank2, cellMostActiveBatchIndices, config._r );

            // Set hidden activation to zero for all other batch indices, and 1 for the best
            for( int b = 0; b < batchSize; ++b ) {
                int batchOffset = b * cells + i;
                float oldActivity = outputLayerInputBatch._values[ batchOffset ];
                float newActivity = oldActivity;
                if( cellMostActiveBatchIndices.contains( b ) ) {
                    float transfer = hiddenLayerActivityBatch._values[ batchOffset ];
//                    newActivity = 1f;
                    newActivity = transfer;
                }

                outputLayerInputBatch._values[ batchOffset ] = newActivity;
            }
        }

        // Now calculate the output based on this new pattern of hidden layer activity
        Data outputLayerInput = new Data( cells );
        Data outputLayerOutput = new Data( inputs );

        for( int b = 0; b < batchSize; ++b ) {
            int offsetThis = 0;
            int offsetThat = b * cells;
            outputLayerInput.copyRange( outputLayerInputBatch, offsetThis, offsetThat, cells );
            decode( config, cellWeights, cellBiases2, outputLayerInput, outputLayerOutput ); // for output
            offsetThis = b * inputs;
            offsetThat = 0;
            outputLayerOutputBatch.copyRange( outputLayerOutput, offsetThis, offsetThat, inputs );
        }

    }*/

//    public static void StochasticGradientDescent(
//            int inputSize,
//            int layerSize,
//            int batchSize,
//            float learningRate,
//            float momentum,
//            boolean weightsInputMajor,
//            Data batchInput,
//            Data batchErrors,
//            Data weights,
//            Data weightsVelocity,
//            Data biases,
//            Data biasesVelocity ) {
//
//        boolean useMomentum = false;
//        if( momentum != 0f ) {
//            useMomentum = true;
//        }
//
//        float miniBatchNorm = 1f / (float)batchSize;
//
//        for( int c = 0; c < layerSize; ++c ) { // computing error for each "input"
//
//            for( int i = 0; i < inputSize; ++i ) {
//
//                // foreach( batch sample )
//                for( int b = 0; b < batchSize; ++b ) {
//
//                    // tied weights
//                    int weightsOffset = c * inputSize + i;
//                    if( weightsInputMajor ) {
//                        weightsOffset = i * layerSize + c;
//                    }
//
//                    int inputOffset = b * inputSize + i;
//                    int errorOffset = b * layerSize + c;
//
//                    //float errorGradient = _cellGradients._values[ c ];
//                    float errorGradient = batchErrors._values[ errorOffset ];
//
//                    //float a = _inputValues._values[ i ];
//                    float a = batchInput._values[ inputOffset ];
//                    float wOld = weights._values[ weightsOffset ];
//                    float wDelta = learningRate * miniBatchNorm * errorGradient * a;
//
//                    if( useMomentum ) {
//                        // Momentum
//                        float wNew = wOld - wDelta;
//
//                        if( Useful.IsBad( wNew ) ) {
//                            String error = "Autoencoder weight update produced a bad value: " + wNew;
//                            logger.error( error );
//                            logger.traceExit();
//                            System.exit( -1 );
//                        }
//
//                        weights._values[ weightsOffset ] = wNew;
//                    } else {
//                        // Momentum
//                        float vOld = weightsVelocity._values[ weightsOffset ];
//                        float vNew = ( vOld * momentum ) - wDelta;
//                        float wNew = wOld + vNew;
//
//                        if( Useful.IsBad( wNew ) ) {
//                            String error = "Autoencoder weight update produced a bad value: " + wNew;
//                            logger.error( error );
//                            logger.traceExit();
//                            System.exit( -1 );
//                        }
//
//                        weights._values[ weightsOffset ] = wNew;
//                        weightsVelocity._values[ weightsOffset ] = vNew;
//                    } // momentum
//                } // batch
//            } // inputs
//
//            for( int b = 0; b < batchSize; ++b ) {
//                int errorOffset = b * layerSize + c;
//                float errorGradient = batchErrors._values[ errorOffset ];
//
//                float bOld = biases._values[ c ];
//                float bDelta = learningRate * miniBatchNorm * errorGradient;
//
//                if( useMomentum ) {
//                    float vOld = biasesVelocity._values[ c ];
//                    float vNew = ( vOld * momentum ) - bDelta;
//                    float bNew = bOld + vNew;
//
//                    biases._values[ c ] = bNew;
//                    biasesVelocity._values[ c ] = vNew;
//                } else {
//                    float bNew = bOld - bDelta;
//
//                    biases._values[ c ] = bNew;
//                }
//            }
//        }
//    }

    public void decode(
            Data hiddenActivity,
            Data inputReconstruction ) {
        decode( _c, _cellWeights, _cellBiases2, hiddenActivity, inputReconstruction );
    }

    public static void decode(
            LifetimeSparseAutoencoderConfig config,
            Data cellWeights,
            Data cellBiases2,
            Data hiddenActivity,
            Data inputReconstruction ) {
        int inputs = config.getNbrInputs();
        int cells = config.getNbrCells();

        for( int i = 0; i < inputs; ++i ) {
            float sum = 0.f;

            for( int c = 0; c < cells; ++c ) {

                float response = hiddenActivity._values[ c ];// _cellTransferTopK._values[ c ];

                int offset = c * inputs +i;
                float weight = cellWeights._values[ offset ];
                float product = response * weight;
                sum += product;
            }

            float bias = cellBiases2._values[ i ];

            sum += bias;

            inputReconstruction._values[ i ] = sum;
        }

    }
}
