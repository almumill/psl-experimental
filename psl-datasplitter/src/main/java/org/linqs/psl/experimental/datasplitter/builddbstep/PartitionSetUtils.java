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
package org.linqs.psl.experimental.datasplitter.builddbstep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.linqs.psl.database.Partition;

public class PartitionSetUtils {

    public static List<Partition> invertPartitions(Collection<Partition> partitions, Set<Partition> allPartitions){
        List<Partition> invertedPartition = new ArrayList<Partition>();
        for(Partition p: allPartitions){
            if(!partitions.contains(p)){
                invertedPartition.add(p);
            }
        }
        return invertedPartition;
    }


    public static Set<Partition> collectSets(List<Collection<Partition>> partitionList){
        Set<Partition> allPartitions = new HashSet<Partition>();
        for(Collection<Partition> pL : partitionList){
            allPartitions.addAll(pL);
        }
        return allPartitions;
    }
}
