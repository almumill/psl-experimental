/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2019 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.experimental.datasplitter.splitstep;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

public class PredicateUniformSplitStep implements SplitStep {
    private static final int NO_GROUP = -1;

    private StandardPredicate target;
    private int numFolds;
    private int groupBy;

    /**
     * Constructor for splitting by GroundTerms
     * @param target Predicate whose groundings to split
     * @param numFolds
     * @param groupBy index of node argument in groundings
     */
    public PredicateUniformSplitStep(StandardPredicate target, int numFolds, int groupBy) {
        this.target = target;
        this.numFolds = numFolds;
        this.groupBy = groupBy;
    }

    /**
     * Constructor for splitting by GroundAtoms. Does not group atoms, instead treats each
     * atom as its own group
     * @param target Predicate whose groundings to split on
     * @param numFolds
     */
    public PredicateUniformSplitStep(StandardPredicate target, int numFolds) {
        this(target, numFolds, NO_GROUP);
    }

    @Override
    public List<Collection<Partition>> getSplits(Database inputDB, Random random) {
        Map<Constant, Set<GroundAtom>> groupMap = new HashMap<Constant, Set<GroundAtom>>();
        Collection<Set<GroundAtom>> groups;
        List<Collection<Partition>> splits = new ArrayList<Collection<Partition>>();

        List<GroundAtom> allAtoms = inputDB.getAllGroundAtoms(target);

        if (groupBy == NO_GROUP) {
            groups = new ArrayList<Set<GroundAtom>>(allAtoms.size());
            for (GroundAtom atom : allAtoms) {
                Set<GroundAtom> group = new HashSet<GroundAtom>();
                group.add(atom);
                groups.add(group);
            }
        } else {
            // group atoms
            for (GroundAtom atom : allAtoms) {
                Constant key = atom.getArguments()[groupBy];
                if (groupMap.get(key) == null) {
                    groupMap.put(key, new TreeSet<GroundAtom>());
                }
                groupMap.get(key).add(atom);
            }
            groups = groupMap.values();
        }

        List<Partition> allPartitions = new ArrayList<Partition>();
        List<Inserter> inserters = new ArrayList<Inserter>();
        for (int i = 0; i < numFolds; i++) {
            Partition nextPartition = inputDB.getDataStore().getNewPartition();
            allPartitions.add(nextPartition);
            inserters.add(inputDB.getDataStore().getInserter(target, nextPartition));
        }

        insertIntoPartitions(groups, inserters, random);

        for (int i = 0; i < numFolds; i++) {
            Set<Partition> partitions = new TreeSet<Partition>();
            for (int j = 0; j < numFolds; j++)
                if (j != i)
                    partitions.add(allPartitions.get(j));
            splits.add(partitions);
        }

        return splits;
    }

    private void insertIntoPartitions(Collection<Set<GroundAtom>> groups,
            List<Inserter> inserters, Random random) {

        ArrayList<Set<GroundAtom>> groupList = new ArrayList<Set<GroundAtom>>(groups.size());
        groupList.addAll(groups);
        Collections.shuffle(groupList, random);

        int j = 0;
        for (Set<GroundAtom> group : groupList) {
            for (GroundAtom atom : group)
                inserters.get(j % numFolds).insertValue(atom.getValue(), (Object []) atom.getArguments());
            j++;
        }
    }

}
