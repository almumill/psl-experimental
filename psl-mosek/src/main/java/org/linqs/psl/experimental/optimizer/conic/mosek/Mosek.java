/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.experimental.optimizer.conic.mosek;

import org.linqs.psl.config.Config;
import org.linqs.psl.experimental.optimizer.conic.ConicProgramSolver;
import org.linqs.psl.experimental.optimizer.conic.program.ConeType;
import org.linqs.psl.experimental.optimizer.conic.program.ConicProgram;
import org.linqs.psl.experimental.optimizer.conic.program.LinearConstraint;
import org.linqs.psl.experimental.optimizer.conic.program.NonNegativeOrthantCone;
import org.linqs.psl.experimental.optimizer.conic.program.RotatedSecondOrderCone;
import org.linqs.psl.experimental.optimizer.conic.program.SecondOrderCone;
import org.linqs.psl.experimental.optimizer.conic.program.Variable;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;
import mosek.Env;
import mosek.Task;
import mosek.solveform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Mosek implements ConicProgramSolver {
	private static final Logger log = LoggerFactory.getLogger(Mosek.class);

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "mosek";

	/**
	 * Key for double property. The IPM will iterate until the relative duality gap
	 * is less than its value. Corresponds to the mosek.dparam.intpnt_tol_rel_gap
	 * and mosek.dparam.intpnt_co_tol_rel_gap parameters.
	 */
	public static final String DUALITY_GAP_THRESHOLD_KEY = CONFIG_PREFIX + ".dualitygap";
	public static final double DUALITY_GAP_THRESHOLD_DEFAULT = 1e-8;

	/**
	 * Key for double property. The IPM will iterate until the primal infeasibility
	 * is less than its value. Corresponds to the mosek.dparam.intpnt_tol_pfeas
	 * and mosek.dparam.intpnt_co_tol_pfeas parameters.
	 */
	public static final String PRIMAL_FEASIBILITY_THRESHOLD_KEY = CONFIG_PREFIX + ".primalfeasibility";
	public static final double PRIMAL_FEASIBILITY_THRESHOLD_DEFAULT = 1e-8;

	/**
	 * Key for double property. The IPM will iterate until the dual infeasibility
	 * is less than its value. Corresponds to the mosek.dparam.intpnt_tol_dfeas
	 * and mosek.dparam.intpnt_co_tol_dfeas parameters.
	 */
	public static final String DUAL_FEASIBILITY_THRESHOLD_KEY = CONFIG_PREFIX + ".dualfeasibility";
	public static final double DUAL_FEASIBILITY_THRESHOLD_DEFAULT = 1e-8;

	/**
	 * Key for integer property. Controls the number of threads employed by the
	 * interior-point optimizer. If set to a positive number Mosek will use this
	 * number of threads. If set to zero, the number of threads used will equal
	 * the number of cores detected on the machine. Corresponds to the
	 * mosek.iparam.intpnt_num_threads parameter.
	 */
	public static final String NUM_THREADS_KEY = CONFIG_PREFIX + ".numthreads";
	public static final int NUM_THREADS_DEFAULT = 0;

	/**
	 * Key for solveform property. Controls whether Mosek will solve
	 * the problem as given ("primal"), swap the primal and dual ("dual", if supported),
	 * or be free to choose either ("free").
	 */
	public static final String SOLVE_FORM_KEY = CONFIG_PREFIX + ".solveform";
	public static final String SOLVE_FORM_DEFAULT = solveform.free.toString();

	private ConicProgram program;
	private final double dualityGap;
	private final double pFeasTol;
	private final double dFeasTol;
	private final int numThreads;
	private final solveform solveForm;

	private static final List<ConeType> supportedCones = new ArrayList<ConeType>(3);
	static {
		supportedCones.add(ConeType.NonNegativeOrthantCone);
		supportedCones.add(ConeType.SecondOrderCone);
		supportedCones.add(ConeType.RotatedSecondOrderCone);
	}

	private Env environment;

	public Mosek() {
		environment = new mosek.Env();

		program = null;
		dualityGap = Config.getDouble(DUALITY_GAP_THRESHOLD_KEY, DUALITY_GAP_THRESHOLD_DEFAULT);
		pFeasTol = Config.getDouble(PRIMAL_FEASIBILITY_THRESHOLD_KEY, PRIMAL_FEASIBILITY_THRESHOLD_DEFAULT);
		dFeasTol = Config.getDouble(DUAL_FEASIBILITY_THRESHOLD_KEY, DUAL_FEASIBILITY_THRESHOLD_DEFAULT);
		numThreads = Config.getInt(NUM_THREADS_KEY, NUM_THREADS_DEFAULT);
		solveForm = solveform.valueOf(Config.getString(SOLVE_FORM_KEY, SOLVE_FORM_DEFAULT));
	}

	@Override
	public boolean supportsConeTypes(Collection<ConeType> types) {
		return supportedCones.containsAll(types);
	}

	@Override
	public void setConicProgram(ConicProgram p) {
		program = p;
	}

	@Override
	public void solve() {
		if (program == null) {
			throw new IllegalStateException("No conic program has been set.");
		}

		program.checkOutMatrices();
		SparseCCDoubleMatrix2D A = program.getA();
		DoubleMatrix1D x = program.getX();
		DoubleMatrix1D b = program.getB();
		DoubleMatrix1D c = program.getC();

		try {
			// Initialize task.
			Task task = new Task(environment, A.rows(), A.columns());
			task.putcfix(0.0);
			task.set_Stream(mosek.streamtype.log, new MsgClass());

			task.putdouparam(mosek.dparam.intpnt_tol_rel_gap, dualityGap);
			task.putdouparam(mosek.dparam.intpnt_co_tol_rel_gap, dualityGap);
			task.putdouparam(mosek.dparam.intpnt_tol_pfeas, pFeasTol);
			task.putdouparam(mosek.dparam.intpnt_co_tol_pfeas, pFeasTol);
			task.putdouparam(mosek.dparam.intpnt_tol_dfeas, dFeasTol);
			task.putdouparam(mosek.dparam.intpnt_co_tol_dfeas, dFeasTol);

			task.putintparam(mosek.iparam.num_threads, numThreads);
			task.putintparam(mosek.iparam.intpnt_solve_form, solveForm.value);

			// Create the variables and sets the objective coefficients.
			task.appendvars((int)x.size());
			for (int i = 0; i < x.size(); i++) {
				task.putcj(i, c.getQuick(i));
			}

			// Processes NonNegativeOrthantCones.
			for (NonNegativeOrthantCone cone : program.getNonNegativeOrthantCones()) {
				int index = program.getIndex(cone.getVariable());
				task.putvarbound(index, mosek.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
			}

			// Processes SecondOrderCones.
			for (SecondOrderCone cone : program.getSecondOrderCones()) {
				int[] indices = new int[cone.getN()];
				int i = 1;
				for (Variable v : cone.getVariables()) {
					int index = program.getIndex(v);
					if (v.equals(cone.getNthVariable())) {
						indices[0] = index;
						task.putvarbound(index, mosek.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
					} else {
						indices[i++] = index;
						task.putvarbound(index, mosek.boundkey.fr, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
					}
				}
				task.appendcone(mosek.conetype.quad, 0.0, indices);
			}

			// Process RotatedSecondOrderCones.
			for (RotatedSecondOrderCone cone : program.getRotatedSecondOrderCones()) {
				int[] indices = new int[cone.getN()];
				int i = 2;
				for (Variable v : cone.getVariables()) {
					int index = program.getIndex(v);
					if (v.equals(cone.getNthVariable())) {
						indices[0] = index;
						task.putvarbound(index, mosek.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
					} else if (v.equals(cone.getNMinus1stVariable())) {
						indices[1] = index;
						task.putvarbound(index, mosek.boundkey.lo, 0.0, Double.POSITIVE_INFINITY);
					} else {
						indices[i++] = index;
						task.putvarbound(index, mosek.boundkey.fr, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
					}
				}
				task.appendcone(mosek.conetype.rquad, 0.0, indices);
			}

			// Set the linear constraints.
			Set<LinearConstraint> constraints = program.getConstraints();
			task.appendcons(constraints.size());

			Map<Variable, Double> variables;
			int listIndex, constraintIndex;
			int[] indexList;
			double[] valueList;
			for (LinearConstraint con : constraints) {
				constraintIndex = program.getIndex(con);
				variables = con.getVariables();
				indexList = new int[variables.size()];
				valueList = new double[variables.size()];
				listIndex = 0;
				for (Map.Entry<Variable, Double> e : variables.entrySet()) {
					indexList[listIndex] = program.getIndex(e.getKey());
					valueList[listIndex++] = e.getValue();
				}
				task.putarow(constraintIndex, indexList, valueList);
				task.putconbound(constraintIndex, mosek.boundkey.fx,b.getQuick(constraintIndex), b.getQuick(constraintIndex));
			}

			// Solves the program.
			task.putobjsense(mosek.objsense.minimize);
			log.debug("Starting optimization with {} variables and {} constraints.", A.columns(), A.rows());

			task.optimize();

			log.debug("Completed optimization");
			task.solutionsummary(mosek.streamtype.msg);

			mosek.solsta solsta[] = new mosek.solsta[1];
			task.getsolsta(mosek.soltype.itr, solsta);

			double[] solution = new double[A.columns()];
			task.getsolutionslice(
					mosek.soltype.itr,  // Interior solution.
					mosek.solitem.xx,  // Which part of solution.
					0,  // Index of first variable.
					A.columns(),  // Index of last variable+1
					solution);

			switch(solsta[0]) {
			case optimal:
			case near_optimal:
			case unknown:
				// Store solution in conic program.
				x.assign(solution);
				program.checkInMatrices();
				if (mosek.solsta.unknown.equals(solsta[0])) {
					log.warn("Mosek solution status unknown.");
				}
				break;
			case dual_infeas_cer:
			case prim_infeas_cer:
			case near_dual_infeas_cer:
			case near_prim_infeas_cer:
				throw new IllegalStateException("Infeasible.");
			default:
				throw new IllegalStateException("Other solution status.");
			}

			task.dispose();

		} catch (mosek.MosekException e) {
			// Catch both Error and Warning.
			throw new AssertionError(e);
		}
	}

	private static class MsgClass extends mosek.Stream {
		public MsgClass () {
			super ();
		}
		public void stream (String msg) {
			log.debug(msg.trim());
		}
	}
}
