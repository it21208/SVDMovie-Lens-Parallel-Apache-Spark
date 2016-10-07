package edu.berkeley.svdmovielens;

import java.io.Serializable;

public class SparkData implements Serializable {
//------------------------------DATA MEMBERS ------------  
    public int CustId;
    public int MovieId;
    public int Rating;
    
    public SparkData() {}

    public SparkData(int CustId, int MovieId, int Rating) {
        this.CustId = CustId;
        this.MovieId = MovieId;
        this.Rating = Rating;
    }
    
    public int getCustId() {
        return CustId;
    }

    public void setCustId(int CustId) {
        this.CustId = CustId;
    }

    public int getMovieId() {
        return MovieId;
    }

    public void setMovieId(int MovieId) {
        this.MovieId = MovieId;
    }

    public int getRating() {
        return Rating;
    }

    public void setRating(int Rating) {
        this.Rating = Rating;
    }

    @Override
    public String toString() {
        return "SparkData [custId=" + CustId + ", MovieId=" + MovieId + ", Rating=" + Rating + "]";
    }    
}
