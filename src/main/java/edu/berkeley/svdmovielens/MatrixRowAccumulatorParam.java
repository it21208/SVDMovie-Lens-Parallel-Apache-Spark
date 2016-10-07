/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.berkeley.svdmovielens;

import java.util.ArrayList;
import java.util.List;
import org.apache.spark.AccumulatorParam;

class MatrixRowAccumulatorParam implements AccumulatorParam<MatrixRow> {

    @Override
    public MatrixRow zero(MatrixRow initialValue) {
        List<Double> lst = new ArrayList<>();
        for (int i = 0; i < initialValue.size(); i++) {
            lst.add(0.0);
        }
        return new MatrixRow(lst);
    }

    @Override
    public MatrixRow addInPlace(MatrixRow t, MatrixRow t1) {
        return addAccumulator(t, t1);
    }

    @Override
    public MatrixRow addAccumulator(MatrixRow t, MatrixRow t1) {
        // need to create a brand new List<> and MatrixRow in order to work!!!
        List<Double> newLst = new ArrayList<>(),
                    tlst = t.getRow(), 
                    t1lst = t1.getRow();
        for (int i=0; i<t.size(); i++) {
            newLst.add(tlst.get(i) + t1lst.get(i));
        }
        return new MatrixRow(newLst);
    }
}
