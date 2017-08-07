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
public class TestingData extends TrainingData {

    // data members
    public double PredictRating;

    // constructor
    public TestingData(int CustId, int MovieId, int Rating, double PredictRating) {
        super(CustId, MovieId, Rating);
        this.PredictRating = PredictRating;
    }

    public TestingData(TrainingData data, double PredictRating) {
        super(data.CustId, data.MovieId, data.Rating);
        this.PredictRating = PredictRating;
    }

    // methods
    public double diff() {
        return Math.abs(Rating - PredictRating);
    }

    @Override
    public String toString() {
        return CustId + "\t" + MovieId + "\t" + Rating + "\t" + PredictRating + "\t" + this.diff();
    }
}
