/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package io.agi.core.data;

import io.agi.core.math.RandomInstance;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A FloatArray with an associated DataSize object that describes the array as
 * an N-dimensional Hyperrectangle.
 * 
 * @author dave
 */
public class Data extends FloatArray2 {

    public DataSize _d = null;

    public Data( int size ) {
        super( size );
        _d = DataSize.create( size );
    }

    public Data ( int width, int height ) {
        DataSize d = DataSize.create( width, height );
        setSize( d );
    }

    public Data ( int width, int height, int depth ) {
        DataSize d = DataSize.create( width, height, depth );
        setSize( d );
    }
    
    public Data( DataSize d ) {
        setSize( d );
    }

    public Data( Data d ) {
        _d = new DataSize( d._d );
        copy( d );
    }

    public void setSize( DataSize d ) {
        _d = d;
        int size = _d.getVolume();
        setSize( size );
    }

    public float get( Coordinate2 c ) {
        int offset = c.offset();
        if( offset < _values.length ) {
            return _values[ offset ];
        }
        return 0.f;
    }
    
    public void set( Coordinate2 c, float value ) {
        int offset = c.offset();
        _values[ offset ] = value;
    }
    
    public Coordinate2 begin() {
        return new Coordinate2( _d );
    }

    public Coordinate2 end() {
        Coordinate2 c = new Coordinate2( _d );
                   c.setMax();
        return c;
    }

    public Data translate( Coordinate2 translations ) {

        Data v = new Data( _d );

        Coordinate2 c1 = begin();

        int offset = 0;

        do { // for-each( value in volume )
            float value = _values[ offset ]; // don't want linearization again and validity checks

            ++offset;

            Coordinate2 c2 = new Coordinate2( c1 );
            c2.translate( translations );

            v.set( c2, value ); // checks for validity
        } while( c1.next() ); // for-each( value in volume )

        return v;
    }

    public Collection< Coordinate2 > maxN( int n ) {
        Data v = new Data( _d );

        float min = Float.MIN_VALUE;

        ArrayList< Coordinate2 > al = new ArrayList< Coordinate2 >();

        while( al.size() < n ) {
            Coordinate2 c = v.maxAt();

            al.add( c );

            v.set( c, min );
        }

        return al;
    }

    public Collection< Coordinate2 > minN( int n ) {
        Data v = new Data( _d );

        float max = Float.MAX_VALUE;

        ArrayList< Coordinate2 > al = new ArrayList< Coordinate2 >();

        while( al.size() < n ) {
            Coordinate2 c = v.minAt();

            al.add( c );

            v.set( c, max );
        }

        return al;
    }

    public Coordinate2 maxAt() {

        float max = Float.MIN_VALUE;

        Coordinate2 c = begin();
        Coordinate2 cMax = c;

        do { // for-each( value in volume )
            float value = get( c );

            if( value > max ) {
                max = value;
                cMax = new Coordinate2( c );
            }
        } while( c.next() ); // for-each( value in volume )

        return cMax;
    }

    public Coordinate2 minAt() {

        float min = Float.MAX_VALUE;

        Coordinate2 c = begin();
        Coordinate2 cMin = c;

        do { // for-each( value in volume )
            float value = get( c );

            if( value < min ) {
                min = value;
                cMin = new Coordinate2( c );
            }
        } while( c.next() ); // for-each( value in volume )

        return cMin;
    }

    public Coordinate2 random() {  // select a voxel proportional to its value as a weight, over all values

        // first sum the values, and handle the all-zero case as totally random:
        Coordinate2 c = begin();

        c.randomize();
        return c;
    }

    public ArrayList< Coordinate2 > roulette( int samples ) {  // select a voxel proportional to its value as a weight, over all values
        // first sum the values, and handle the all-zero case as totally random:

        ArrayList< Coordinate2 > al = new ArrayList< Coordinate2 >();
        
        double sum = sum();

        for( int i = 0; i < samples; ++i ) {
            if( sum <= 0.0 ) {
                Coordinate2 c = begin();
                c.randomize();
                al.add( c );
            }
            else {
                Coordinate2 c = roulette( sum );
                al.add( c );
            }
        }

        return al;
    }

    public Coordinate2 roulette() {  // select a voxel proportional to its value as a weight, over all values
        return roulette( sum() );
    }

    public Coordinate2 roulette( double sum ) {  // select a voxel proportional to its value as a weight, over all values

        // first sum the values, and handle the all-zero case as totally random:
        Coordinate2 c = begin();

        if( sum <= 0.0 ) {
            c.randomize();
            return c;
        }

        // OK so now do a roulette selection:
        double random = RandomInstance.random() * sum;
        double accumulated = 0.0;
        
        do { // for-each( value in volume )
            double value = get( c );

            accumulated += value;

            if( accumulated >= random ) {
                return c;
            }
        } while( c.next() ); // for-each( value in volume )

        return end(); // shouldn't happen!
    }

    public float sumSubVolume( int dimensionsExcluded, Coordinate2 included ) {

        // First we assume the user has specified the indices in all excluded
        // dimensions using the param "included".
        // e.g. in 5-d if dimsExcluded = 3, included = 12300
        // Then we compute a second coordinate which is 1 position beyond the
        // included range (ie the first excluded coordinate).
        // excluded should be 12400
        Coordinate2 excluded = new Coordinate2( included );
        Coordinate2 offset   = new Coordinate2( _d );
                   offset._indices[ dimensionsExcluded -1 ] = 1;

        excluded.add( offset );

        // Compute the offsets that these coordinates define, and apply within
        // this range:
        int offset1 = included.offset();
        int offset2 = excluded.offset();

        float sum = 0.0f;

        while( offset1 < offset2 ) {
            sum += _values[ offset1 ];
            ++offset1;
        }

        return sum;
    }

    public void mulSubVolume( int dimensionsExcluded, Coordinate2 included, float value ) {

        Coordinate2 excluded = new Coordinate2( included );
        Coordinate2 offset   = new Coordinate2( _d );
                   offset._indices[ dimensionsExcluded -1 ] = 1;

        excluded.add( offset );

        // Compute the offsets that these coordinates define, and apply within
        // this range:
        int offset1 = included.offset();
        int offset2 = excluded.offset();

        while( offset1 < offset2 ) {
            _values[ offset1 ] *= value;
            ++offset1;
        }
    }

    public void scaleSubVolume( int dimensionsExcluded, Coordinate2 included, float total ) {

        // formula:
        // x = x / sum
        // as reciprocal:
        // x = x * (1/sum)
        float sum = sumSubVolume( dimensionsExcluded, included );

        if( sum <= 0.0f ) {
            return;
        }

        float reciprocal = total / sum;

        mulSubVolume( dimensionsExcluded, included, reciprocal );
    }
    
    public int getVolumeExcluding( String invariantDimension ) {
        int volume = getSize();
        int size = _d.getSize( invariantDimension );

        if( size == 0 ) {
            return volume;
        }

        volume /= size;
        
        return volume;
    }

}