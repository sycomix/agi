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

package io.agi.framework.entities.reinforcement_learning;

import io.agi.core.ann.reinforcement.EpsilonGreedyQLearningPolicy;
import io.agi.core.ann.reinforcement.QLearning;
import io.agi.core.ann.reinforcement.QLearningConfig;
import io.agi.core.data.Data;
import io.agi.core.data.DataSize;
import io.agi.core.orm.ObjectMap;
import io.agi.framework.DataFlags;
import io.agi.framework.Entity;
import io.agi.framework.Node;
import io.agi.framework.persistence.models.ModelEntity;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by dave on 2/06/17.
 */
public class EpsilonGreedyEntity extends Entity {

    public static final String ENTITY_TYPE = "epsilon-greedy";

    public static final String INPUT_STATES_NEW  = "input-states-new";
    public static final String INPUT_ACTIONS_QUALITY = "input-actions-quality";
    public static final String OUTPUT_ACTIONS  = "output-actions";

    public EpsilonGreedyEntity( ObjectMap om, Node n, ModelEntity model ) {
        super( om, n, model );
    }

    public void getInputAttributes( Collection< String > attributes ) {
        attributes.add( INPUT_STATES_NEW );
        attributes.add( INPUT_ACTIONS_QUALITY );
    }

    public void getOutputAttributes( Collection< String > attributes, DataFlags flags ) {

        attributes.add( OUTPUT_ACTIONS );
        flags.putFlag( OUTPUT_ACTIONS, DataFlags.FLAG_SPARSE_BINARY );
    }

    @Override
    public Class getConfigClass() {
        return EpsilonGreedyEntityConfig.class;
    }

    protected void doUpdateSelf() {

        EpsilonGreedyEntityConfig config = ( EpsilonGreedyEntityConfig ) _config;

        // Do nothing unless the input is defined
        Data inputS = getData( INPUT_STATES_NEW );
        Data inputAQ = getData( INPUT_ACTIONS_QUALITY );
        Data outputA = new Data( inputAQ._dataSize );

        EpsilonGreedyQLearningPolicy egp = new EpsilonGreedyQLearningPolicy();
        egp.setup( _r, config.epsilon );
        egp._learn = config.learn;
        egp.selectActions( inputS, inputAQ, outputA ); // select one action

        setData( OUTPUT_ACTIONS, outputA );
    }

}