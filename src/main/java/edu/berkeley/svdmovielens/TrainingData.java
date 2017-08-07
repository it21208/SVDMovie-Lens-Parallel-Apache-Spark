/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.berkeley.svdmovielens;

/**
 *
 * @author Alexandros Ioannidis
 */
public class TrainingData extends SparkData {

    public double Cache = 0;

    public TrainingData(int CustId, int MovieId, int Rating) {
        super(CustId, MovieId, Rating);
    }
    public double getCache() {
        return Cache;
    }

    public void setCache(double Cache) {
        this.Cache = Cache;
    }
    
    @Override
    public String toString() {
        return "TrainingData [custId=" + CustId + ", MovieId=" + MovieId + ", Rating=" + Rating + ", Cache=" + Cache + "]";
    }     
}
