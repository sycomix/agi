/*
 * Copyright (c) 2016.
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

package io.agi.framework.entities;

import io.agi.core.data.Data;
import io.agi.core.data.Data2d;
import io.agi.core.data.DataSize;
import io.agi.core.orm.ObjectMap;
import io.agi.core.util.FileUtil;
import io.agi.framework.DataFlags;
import io.agi.framework.Entity;
import io.agi.framework.Framework;
import io.agi.framework.Node;
import io.agi.framework.persistence.DataJsonSerializer;
import io.agi.framework.persistence.models.ModelData;
import io.agi.framework.persistence.models.ModelEntity;
import io.agi.framework.references.DataRef;

import java.io.File;
import java.util.Collection;

/**
 * Creates a rolling window of values from an Input Data, captured over time. The window is updated every update() with a
 * new value from the Input. The oldest value is discarded.
 * <p/>
 * Created by dave on 2/04/16.
 */
public class VectorSeriesEntity extends Entity {

    public static final String ENTITY_TYPE = "vector-series";

    public static final String INPUT = "input";
    public static final String OUTPUT = "output";
    public static final String OUTPUT_ACCUMULATE = "output-accumulate";

    public VectorSeriesEntity( ObjectMap om, Node n, ModelEntity model ) {
        super( om, n, model );
    }

    public void getInputAttributes( Collection< String > attributes ) {
        attributes.add( INPUT );
    }

    public void getOutputAttributes( Collection< String > attributes, DataFlags flags ) {

        VectorSeriesEntityConfig config = ( VectorSeriesEntityConfig ) _config;

        attributes.add( OUTPUT );

        if( config.encoding.equals( DataJsonSerializer.ENCODING_SPARSE_BINARY ) ) {
            flags.putFlag( OUTPUT, DataFlags.FLAG_SPARSE_BINARY );
        }
        else if( config.encoding.equals( DataJsonSerializer.ENCODING_SPARSE_REAL ) ) {
            flags.putFlag( OUTPUT, DataFlags.FLAG_SPARSE_REAL );
        }
        else { // remove existing flag, if any
            flags.removeFlag( OUTPUT, DataFlags.FLAG_SPARSE_BINARY );
            flags.removeFlag( OUTPUT, DataFlags.FLAG_SPARSE_REAL );
        }

        attributes.add( OUTPUT_ACCUMULATE );

    }

    public Class getConfigClass() {
        return VectorSeriesEntityConfig.class;
    }

    protected void doUpdateSelf() {

        // Get all the parameters:
        VectorSeriesEntityConfig config = ( VectorSeriesEntityConfig ) _config;

        Data input = getData( INPUT );

        if( input == null ) {
            return; // nothing to log yet
        }

        Data outputAccumulate = getDataLazyResize( OUTPUT_ACCUMULATE, new DataSize( input._dataSize) ); // same size as input

        if( config.reset ) {
            outputAccumulate.set( 0f ); //config.valueAccumulate = 0;
            input.copy( outputAccumulate );  //config.value = 0;
            config.countAccumulate = 0;
        }

        if( !config.learn ) {
            setData( OUTPUT_ACCUMULATE, outputAccumulate );
            return; // don't append or update the output data except when "learning" ie accumulating
        }

        // always accumulate new value
        outputAccumulate.add( input ); //config.valueAccumulate += newValue; // ie only this value, if not accumulating
        config.countAccumulate += 1; // i.e. 1, if first value or period = 1

        boolean append = false;

        if( config.countAccumulate >= config.periodAccumulate ) {
            // old count: 0, period: 1 result: append, including new value
            // old count: 0, period: 2 result: acc
            // old count: 1, period: 2 result: append, including new value
            // old count: 1, period: 3 result: acc
            // old count: 2, period: 3 result: append, including new value
            append = true;
        }

        if( append ) { // add new value
            //System.err.println( "Append to log: " + getName() );
            input.copy( outputAccumulate ); //config.value = config.valueAccumulate;
            outputAccumulate.set( 0f ); //config.valueAccumulate = 0;
            config.countAccumulate = 0;
            setData( OUTPUT_ACCUMULATE, outputAccumulate );
        }
        else { // output the accumulated value only
            setData( OUTPUT_ACCUMULATE, outputAccumulate );
            return; // don't append to output
        }


        // OK to get here means we want to append a new value, stored in OUTPUT_ACCUMULATE
        Data oldOutput = getData( OUTPUT );

        if( config.period < 0 ) { // keep infinite history
            int oldLength = Data2d.accumulatedVectorCount( oldOutput );

            if( ( config.flushPeriod >= 0 ) && ( oldLength >= config.flushPeriod ) ) { // truncate and flush?
                if( oldLength >= config.period ) { // truncate and flush?
                    String key = getKey( OUTPUT );
                    write( key, oldOutput, config );
                    oldOutput = null; // causes it to be forgotten
                }
            }
        }

        Data newOutput = Data2d.accumulateVectors( input, config.period, oldOutput );
        setData( OUTPUT, newOutput );
    }

    protected static void write( String key, Data accumulated, VectorSeriesEntityConfig config ) {

        String filePathName = config.writeFilePath + File.separator + config.writeFilePrefix + "_" + config.age + "." + config.writeFileExtension;

        _logger.info( "Writing Datas: " + key + " to file: " + filePathName );

        StringBuilder sb = new StringBuilder( 100 );
        //ModelData md = new ModelData( key, accumulated, config.encoding );

        DataRef dataRef = new DataRef( key, config.encoding, null, accumulated );
        ModelData md = new ModelData();
        if( md.serialize( dataRef ) ) {
            md.toString( sb );

            boolean append = false;
            boolean b = FileUtil.WriteFileMemoryEfficient( filePathName, sb, append ); // write the file efficiently
            if( !b ) {
                _logger.error( "Unable to write some Data objects to file." );
            }
        }
        else {
            _logger.error( "Unable to serialize some Data objects to file." );
        }

    }

}
