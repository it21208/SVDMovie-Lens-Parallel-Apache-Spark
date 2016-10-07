/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.berkeley.svdmovielens;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author tioannid
 */
public class MatrixRow implements Serializable {

    // Data Members
    List<Double> row;

    // Constructor
    public MatrixRow(List<Double> row) {
        this.row = row;
    }
    
    // Data Accessors
    public List<Double> getRow() {
        return row;
    }

    // Methods
    public int size() {
        return this.row.size();
    }
    
    @Override
    public String toString() {
        String s = "|-<";
        s = s + String.valueOf(this.row.size()) + ">";
        Double F;
        Iterator<Double> it = this.row.iterator();
        while (it.hasNext()) {
            F = it.next();
            s += (F.toString() + "\t");
        }
        return s;
    }
}
