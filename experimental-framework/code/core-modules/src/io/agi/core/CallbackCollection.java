package io.agi.core;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by dave on 12/09/15.
 */
public class CallbackCollection implements Callback {

    public ArrayList< Callback > _cbs = new ArrayList< Callback >();

    public boolean _enabled = true;

    public CallbackCollection() {

    }

    public void add( int index, Callback cb ) {
        _cbs.add( index, cb );
    }

    public void add( Callback cb ) {
        _cbs.add( cb );
    }

    /**
     * Won't add more than once, based on object reference
     * @param cb
     */
    public void addLazy( Callback cb ) {
        if( _cbs.contains( cb ) ) {
            return;
        }
        _cbs.add( cb );
    }

    public Collection< Callback > get() {
        return _cbs;
    }

    @Override public void call() {

        if( !_enabled ) {
            return;
        }

        for( Callback cb : _cbs ) {
            cb.call();
        }
    }
}