package oarlib.solver.impl;

import java.util.Collection;

import oarlib.core.Problem;
import oarlib.core.Route;
import oarlib.core.Solver;

public class UCPPSolver extends Solver{

	public UCPPSolver(Problem instance) throws IllegalArgumentException {
		super(instance);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected Collection<Route> solve() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Problem.Type getProblemType() {
		return Problem.Type.UNDIRECTED_CHINESE_POSTMAN;
	}

}