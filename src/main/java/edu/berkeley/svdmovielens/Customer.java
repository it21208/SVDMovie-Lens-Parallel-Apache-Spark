package edu.berkeley.svdmovielens;

import java.io.Serializable;

public class Customer implements Serializable {
    //------------------------------DATA MEMBERS ------------  
    public int RatingCount = 0;
    public int RatingSum = 0;
    
    public Customer() {}
    
    public Customer(int RatingCount, int RatingSum) {
        this.RatingCount = RatingCount;
        this.RatingSum = RatingSum;
    }

    public void setRatingCount(int RatingCount) {
        this.RatingCount = RatingCount;
    }

    public void setRatingSum(int RatingSum) {
        this.RatingSum = RatingSum;
    }

    public int getRatingCount() {
        return RatingCount;
    }

    public int getRatingSum() {
        return RatingSum;
    }
     
    @Override
    public String toString() {
        return "Customer [ratingCount=" + RatingCount + ", ratingSum=" + RatingSum + "]";
    }
}
