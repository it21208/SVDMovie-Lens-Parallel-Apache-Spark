package edu.berkeley.svdmovielens;

import java.io.Serializable;

public class Movie implements Serializable {
    //------------------------------DATA MEMBERS ------------
    public int RatingCount = 0;
    public int RatingSum = 0;

    public Movie() {}
    
    public Movie(int RatingCount, int RatingSum) {
        this.RatingCount = RatingCount;
        this.RatingSum = RatingSum;
    }

    public int getRatingCount() {
        return RatingCount;
    }

    public void setRatingCount(int RatingCount) {
        this.RatingCount = RatingCount;
    }

    public int getRatingSum() {
        return RatingSum;
    }

    public void setRatingSum(int RatingSum) {
        this.RatingSum = RatingSum;
    }
   
    //------------------------------ METHODS ------------
    public double RatingAvg()
    {
        return RatingSum / (1.0 * RatingCount);
    }
    public double PseudoAvg()
    {
        return (3.23 * 25 + RatingSum) / (25.0 + RatingCount);
    }
    
    @Override
    public String toString() {
        return "Movie [ratingCount=" + RatingCount + ", ratingSum=" + RatingSum + "]";
    }    
}
