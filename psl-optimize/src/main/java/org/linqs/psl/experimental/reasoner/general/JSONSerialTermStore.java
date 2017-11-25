/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl.experimental.reasoner.general;

// TODO(eriq):
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.reasoner.function.AtomFunctionVariable;
import org.linqs.psl.reasoner.term.MemoryTermStore;
import org.linqs.psl.reasoner.term.TermStore;

import com.google.gson.Gson;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A TermStore that is meant to serialize/deserialize the optimization
 * problem to/from JSON.
 */
public class JSONSerialTermStore extends MemoryTermStore<SimpleTerm> {
	public static final String ID_PRRFIX = "_";

	private BidiMap<RandomVariableAtom, String> variableIds;

	public JSONSerialTermStore() {
		super();

		variableIds = new DualHashBidiMap<RandomVariableAtom, String>();
	}

	/**
	 * Write out the optimiztion problem
	 * (variables, objective, and constraints) to a JSON file.
	 */
	public void serialize(BufferedWriter writer) {
		List<OutputTerm> objectiveSummands = new ArrayList<OutputTerm>();

		for (SimpleTerm term : this) {
			if (term.isHard()) {
				// TODO(eriq)
			} else {
				objectiveSummands.add(new OutputTerm(term));
			}
		}

		Output output = new Output(variableIds.values(), objectiveSummands);

		Gson gson = new Gson();
		gson.toJson(output, writer);
	}

	/**
	 * Read a JSON file describing the solution and
	 * update all the variables (ground atoms).
	 */
	public void deserialize(BufferedReader reader) {
		Gson gson = new Gson();
		Input input = gson.fromJson(reader, Input.class);

		// Update the random variable atoms with their new values.
		for (Map.Entry<String, Double> entry : input.solution.entrySet()) {
			variableIds.getKey(entry.getKey()).setValue(entry.getValue().doubleValue());
		}
	}

	@Override
	public void add(GroundRule rule, SimpleTerm term) {
		for (RandomVariableAtom atom : term.getAtoms()) {
			if (!variableIds.containsKey(atom)) {
				variableIds.put(atom, ID_PRRFIX + variableIds.size());
			}
		}

		super.add(rule, term);
	}

	@Override
	public void clear() {
		variableIds.clear();
		super.clear();
	}

	@Override
	public void close() {
		clear();
		super.close();
	}

	private static class Output {
		public Set<String> variables;

		// TODO(eriq): Better format (json)
		public List<OutputTerm> objectiveSummands;

		// TODO(eriq): constraints
		public List<String> constraints;

		public Output(Set<String> variables, List<OutputTerm> objectiveSummands) {
			this.variables = variables;
			this.objectiveSummands = objectiveSummands;
		}
	}

	private class OutputTerm {
		public double constant;
		public String[] variables;
		public boolean[] signs;
		public boolean squared;
		public double weight;

		public OutputTerm(double constant, String[] variables, boolean[] signs,
				boolean squared, double weight) {
			this.constant = constant;
			this.variables = variables;
			this.signs = signs;
			this.squared = squared;
			this.weight = weight;
		}

		public OutputTerm(SimpleTerm term) {
			variables = new String[term.size()];
			signs = new boolean[term.size()];

			List<RandomVariableAtom> rawAtoms = term.getAtoms();
			List<Boolean> rawSigns = term.getSigns();
			for (int i = 0; i < term.size(); i++) {
				variables[i] = variableIds.get(rawAtoms.get(i));
				signs[i] = rawSigns.get(i).booleanValue();
			}

			constant = term.getConstant();
			squared = term.isSquared();
			weight = term.getWeight();
		}
	}

	private static class Input {
		public Map<String, Double> solution;
	}
}
