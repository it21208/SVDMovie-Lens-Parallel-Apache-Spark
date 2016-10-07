package edu.berkeley.svdmovielens;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.time.Duration;
import java.util.Scanner;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.SparkConf;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.api.java.JavaPairRDD;
import scala.Tuple2;
import org.apache.spark.serializer.KryoRegistrator;
import org.apache.spark.Accumulator;
import org.apache.spark.api.java.function.PairFunction;

public class SVDMovieLensSparkJava implements Serializable {

    // ------------------------ Serial version Members ---------------------------
    private final int MAX_RATINGS = 100000; // Ratings in entire training set (+1)
    private final int MAX_CUSTOMERS = 943; // Customers in the entire training set (+1)
    private final int MAX_MOVIES = 1682;     // Movies in the entire training set (+1)
    private final int MAX_FEATURES = 64;      // Number of features to use 
    private final int MIN_EPOCHS = 120;      // Minimum number of epochs per feature
    private final int MAX_EPOCHS = 200;       // Max epochs per feature
    private final double MIN_IMPROVEMENT = 0.0001;// Minimum improvement required to continue current feature
    private final double INIT = 0.1;   // Initialization value for features
    private final double LRATE = 0.001; // Learning rate parameter
    private final double K = 0.015; // Regularization parameter used to minimize over-fitting
    private int m_nRatingCount;    // Current number of loaded ratings
    private TrainingData[] m_aRatings = new TrainingData[MAX_RATINGS];  // Array of ratings data
    private Movie[] m_aMovies = new Movie[MAX_MOVIES + 1];  // Array of movie metrics
    private Customer[] m_aCustomers = new Customer[MAX_CUSTOMERS + 1];// Array of customer metrics
    private double[][] m_aMovieFeatures = new double[MAX_FEATURES][MAX_MOVIES + 1]; // Array of features by movie (using floats to save space)
    private double[][] m_aCustFeatures = new double[MAX_FEATURES][MAX_CUSTOMERS + 1]; // Array of features by customer (using floats to save space)
    //-------------------------------- Spark version Members --------------------------
    private final transient String SparkAppName = "SVD MovieLens - Apache Spark";
    private transient SparkConf conf;
    private transient JavaSparkContext sc;
    private final Broadcast<String> TRAINING_FILE_BRDCST;
    private final Broadcast<String> TESTING_FILE_BRDCST;
    private final Broadcast<String> PREDICTIONS_FILE_BRDCST;
    private final Broadcast<Integer> MAX_RATINGS_BRDCST; // Ratings in entire training set (+1)
    private final Broadcast<Integer> MAX_CUSTOMERS_BRDCST; // Customers in the entire training set (+1)
    private final Broadcast<Integer> MAX_MOVIES_BRDCST;   // Movies in the entire training set (+1)
    private final Broadcast<Integer> MAX_FEATURES_BRDCST;  // Number of features to use 
    private final Broadcast<Integer> MIN_EPOCHS_BRDCST;      // Minimum number of epochs per feature
    private final Broadcast<Integer> MAX_EPOCHS_BRDCST;       // Max epochs per feature
    private final Broadcast<Double> MIN_IMPROVEMENT_BRDCST;// Minimum improvement required to continue current feature
    private final Broadcast<Double> INIT_BRDCST;   // Initialization value for features
    private final Broadcast<Double> LRATE_BRDCST; // Learning rate parameter
    private final Broadcast<Double> K_BRDCST; // Regularization parameter used to minimize over-fitting
    private Broadcast<Long> m_nRatingCount_BRDCST;    // Current number of loaded ratings
    private JavaPairRDD<Integer, TrainingData> m_aTrainingRatings_PairRDD;  // RDD of ratings data
    private JavaPairRDD<Integer, Movie> m_aMovies_PairRDD;  // RDD of movie metrics
    private JavaPairRDD<Integer, Customer> m_aCustomers_PairRDD;// RDD of customer metrics  
    private JavaPairRDD<Integer, MatrixRow> m_aMovieFeatures_PairRDD; // RDD of features by movie
    private JavaPairRDD<Integer, MatrixRow> m_aCustFeatures_PairRDD; // RDD of features by customer
    private Accumulator<Double> sq_ACC;
    private Accumulator<MatrixRow> customerFeatureMatrixRow_ACC, movieFeatureMatrixRow_ACC;
    private Broadcast<MatrixRow> customerFeatureMatrixRow_BRDCST, movieFeatureMatrixRow_BRDCST;
    Map<Integer, MatrixRow> mapCustomerFeatures;
    Map<Integer, MatrixRow> mapMovieFeatures;
    private Broadcast<double[][]> m_aCustFeatures_BRDCST;
    private Broadcast<double[][]> m_aMovieFeatures_BRDCST;

    // custom registrator for Kryo Serializer - Static Nested class
    public static class MyClassRegistrator implements KryoRegistrator {

        @Override
        public void registerClasses(Kryo kryo) {
            kryo.register(Customer.class, new FieldSerializer(kryo, Customer.class));
            kryo.register(Movie.class, new FieldSerializer(kryo, Movie.class));
            kryo.register(SparkData.class, new FieldSerializer(kryo, SparkData.class));
            kryo.register(TrainingData.class, new FieldSerializer(kryo, TrainingData.class));
            kryo.register(TestingData.class, new FieldSerializer(kryo, TestingData.class));
            kryo.register(MatrixRow.class, new FieldSerializer(kryo, MatrixRow.class));
            kryo.register(MatrixRowAccumulatorParam.class, new FieldSerializer(kryo, MatrixRowAccumulatorParam.class));
            kryo.register(SVDMovieLensSparkJava.class, new FieldSerializer(kryo, SVDMovieLensSparkJava.class));
        }
    }

    public static void Debug(int num) {
        System.out.printf("|-----------------------> DBG %d\n", num);
    }

    public static void Message(String msg) {
        System.out.printf("|-----> %s <----|\n", msg);
    }

    //----------------------------------------------Constructor-------------- 
    public SVDMovieLensSparkJava() {
        int f;
        // create Spark Configuration object
        conf = new SparkConf().setAppName(this.SparkAppName);
        // Register custom classes with Kryo Registrator
        //conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer");
        //conf.set("spark.kryo.registrator", MyClassRegistrator.class.getName());
        // create Spark Context
        sc = new JavaSparkContext(this.conf);
        TRAINING_FILE_BRDCST = sc.broadcast("/nfs/MovieLens/u.data");
        TESTING_FILE_BRDCST = sc.broadcast("/nfs/MovieLens/u1.test");
        PREDICTIONS_FILE_BRDCST = sc.broadcast("/nfs/MovieLens/u1.predictions");
        MAX_RATINGS_BRDCST = sc.broadcast(100000); // Ratings in entire training set (+1)
        MAX_CUSTOMERS_BRDCST = sc.broadcast(943); // Customers in the entire training set (+1)
        MAX_MOVIES_BRDCST = sc.broadcast(1682);   // Movies in the entire training set (+1)
        MAX_FEATURES_BRDCST = sc.broadcast(64);  // Number of features to use 
        MIN_EPOCHS_BRDCST = sc.broadcast(120);      // Minimum number of epochs per feature
        MAX_EPOCHS_BRDCST = sc.broadcast(200);       // Max epochs per feature
        MIN_IMPROVEMENT_BRDCST = sc.broadcast(0.0001);// Minimum improvement required to continue current feature
        INIT_BRDCST = sc.broadcast(0.1);   // Initialization value for features
        LRATE_BRDCST = sc.broadcast(0.001); // Learning rate parameter
        K_BRDCST = sc.broadcast(0.015); // Regularization parameter used to minimize over-fitting
        // cache broadcast variable values in local variables to speed up
        int MAX_FEATURES = this.MAX_FEATURES_BRDCST.getValue();
        int MAX_CUSTOMERS = this.MAX_CUSTOMERS_BRDCST.getValue();
        int MAX_MOVIES = this.MAX_MOVIES_BRDCST.getValue();
        Double INIT = this.INIT_BRDCST.getValue();
        // Create RDD for Customer Feature matrix, using autogenerated indices
        List<MatrixRow> matrixRowList = new ArrayList<>();
        for (f = 0; f < MAX_FEATURES; f++) {
            // create a vector of MAX_CUSTOMERS with INIT_BRDCST value
            matrixRowList.add(new MatrixRow(Collections.nCopies(MAX_CUSTOMERS, INIT)));
        }
        JavaRDD<MatrixRow> rdd1 = sc.parallelize(matrixRowList);
        this.m_aCustFeatures_PairRDD = rdd1.zipWithIndex().mapToPair((Tuple2<MatrixRow, Long> tuple) -> new Tuple2<Integer, MatrixRow>(Integer.valueOf(tuple._2.intValue()), tuple._1));
        // Create RDDs for Movie Feature matrix, using autogenerated indices
        matrixRowList = new ArrayList<>();
        for (f = 0; f < MAX_FEATURES; f++) {
            // create a vector of MAX_MOVIES with INIT_BRDCST value
            matrixRowList.add(new MatrixRow(Collections.nCopies(MAX_MOVIES, INIT)));
        }
        this.m_aMovieFeatures_PairRDD = sc.parallelize(matrixRowList).zipWithIndex().mapToPair((Tuple2<MatrixRow, Long> tuple) -> new Tuple2<Integer, MatrixRow>(Integer.valueOf(tuple._2.intValue()), tuple._1));
        // create maps from the Customer and Movie Feature RDDs
        mapCustomerFeatures = new HashMap<>(m_aCustFeatures_PairRDD.collectAsMap());
        mapMovieFeatures = new HashMap<>(m_aMovieFeatures_PairRDD.collectAsMap());
        // ------------- Initialize serial version members
        int i;
        for (f = 0; f < this.MAX_FEATURES; f++) {
            for (i = 1; i < this.MAX_MOVIES + 1; i++) {
                this.m_aMovieFeatures[f][i] = this.INIT;
            }
            for (i = 1; i < this.MAX_CUSTOMERS + 1; i++) {
                this.m_aCustFeatures[f][i] = this.INIT;
            }
        }
    }

    //------------------------------Main()------------------------------------
    public static void main(String[] args) throws IOException {
        LocalTime t1, t2, t3, t4, t5;

        t1 = LocalTime.now();
        SVDMovieLensSparkJava engine = new SVDMovieLensSparkJava();
        t2 = LocalTime.now();
        System.out.printf("engine construction duration equals %d seconds\n", Duration.between(t1, t2).getSeconds());
        engine.LoadHistory();
        t3 = LocalTime.now();
        System.out.printf("load history duration equals %d seconds\n", Duration.between(t2, t3).getSeconds());
        engine.CalcFeatures();
        t4 = LocalTime.now();
        System.out.printf("calculation feature duration equals %d seconds\n", Duration.between(t3, t4).getSeconds());
        engine.ProcessTest();
        t5 = LocalTime.now();
        System.out.printf("processing test duration equals %d seconds\n", Duration.between(t4, t5).getSeconds());
        System.out.println("\nDone\n");
    }

    // METHODS
    public void LoadHistory() throws IOException {
        this.ProcessFile(this.TRAINING_FILE_BRDCST);
    }

    private void ProcessFile(Broadcast<String> filename) throws IOException {
        // create initial RDD of string with training data
        JavaRDD<String> trainingFile = this.sc.textFile(filename.value());
        // calculate the number of ratings
        this.m_nRatingCount_BRDCST = this.sc.broadcast(trainingFile.count());
        // create RDD for Customer statistics
        JavaRDD<String[]> columnsTrainingFile = trainingFile.map(line -> line.split("\t"));
        JavaPairRDD<Integer, Integer> columnsCustomersA_
                = columnsTrainingFile.mapToPair((String[] row) -> new Tuple2(Integer.parseInt(row[0]), Integer.parseInt(row[2])));
        JavaPairRDD<Integer, Integer> columnsCustomersA = columnsCustomersA_.reduceByKey((a, b) -> a + b);
        JavaPairRDD<Integer, Integer> columnsCustomersB_
                = columnsTrainingFile.mapToPair((String[] row) -> new Tuple2(Integer.parseInt(row[0]), 1));
        JavaPairRDD<Integer, Integer> columnsCustomersB = columnsCustomersB_.reduceByKey((a, b) -> a + b);
        JavaPairRDD<Integer, Tuple2<Integer, Integer>> columnsCustomers = columnsCustomersB.join(columnsCustomersA);
        this.m_aCustomers_PairRDD = columnsCustomers.mapToPair((Tuple2<Integer, Tuple2<Integer, Integer>> tuple) -> new Tuple2<Integer, Customer>(tuple._1, new Customer(tuple._2._1, tuple._2._2)));
        // create RDD for Movie statistics
        JavaPairRDD<Integer, Integer> columnsMoviesA_
                = columnsTrainingFile.mapToPair((String[] row) -> new Tuple2(Integer.parseInt(row[1]), Integer.parseInt(row[2])));
        JavaPairRDD<Integer, Integer> columnsMoviesA = columnsMoviesA_.reduceByKey((a, b) -> a + b);
        JavaPairRDD<Integer, Integer> columnsMoviesB_
                = columnsTrainingFile.mapToPair((String[] row) -> new Tuple2(Integer.parseInt(row[1]), 1));
        JavaPairRDD<Integer, Integer> columnsMoviesB = columnsMoviesB_.reduceByKey((a, b) -> a + b);
        JavaPairRDD<Integer, Tuple2<Integer, Integer>> columnsMovies = columnsMoviesB.join(columnsMoviesA);
        this.m_aMovies_PairRDD = columnsMovies.mapToPair((Tuple2<Integer, Tuple2<Integer, Integer>> tuple) -> new Tuple2<Integer, Movie>(tuple._1, new Movie(tuple._2._1, tuple._2._2)));
        // create Ratings array with Data objects
        JavaRDD<TrainingData> rddData = columnsTrainingFile.map((String[] row) -> new TrainingData(Integer.parseInt(row[0]), Integer.parseInt(row[1]), Integer.parseInt(row[2])));
        JavaPairRDD<TrainingData, Long> rddIndexedData = rddData.zipWithIndex();
        this.m_aTrainingRatings_PairRDD = rddIndexedData.mapToPair((Tuple2<TrainingData, Long> tuple) -> new Tuple2(tuple._2.intValue(), tuple._1));
        // ------------------ update serial version members
        this.m_nRatingCount = this.m_nRatingCount_BRDCST.getValue().intValue();
        this.m_aCustomers_PairRDD.collectAsMap().forEach((k, v) -> this.m_aCustomers[k] = v);
        this.m_aMovies_PairRDD.collectAsMap().forEach((k, v) -> this.m_aMovies[k] = v);
        this.m_aTrainingRatings_PairRDD.collectAsMap().forEach((k, v) -> this.m_aRatings[k] = v);

    }

    double spark_PredictRating(double movieFeature, double customerFeature, int feature, double cache, boolean bTrailing) {
        // Get cached value for old features or default to an average
        double sum = (cache > 0) ? cache : 1;
        // Add contribution of current feature
        sum += movieFeature * customerFeature;
        if (sum > 5) {
            sum = 5;
        }
        if (sum < 1) {
            sum = 1;
        }
        // Add up trailing defaults values
        if (bTrailing) {
            sum += (MAX_FEATURES_BRDCST.getValue() - (feature + 1) - 1) * (INIT_BRDCST.getValue() * INIT_BRDCST.getValue());
            if (sum > 5) {
                sum = 5;
            }
            if (sum < 1) {
                sum = 1;
            }
        }
        return sum;
    }

    class CalculateSQ_InnerClass implements PairFunction<Tuple2<Integer, TrainingData>, Integer, TrainingData> {

        // data members
        int feature;

        // constructor
        public CalculateSQ_InnerClass() {
        }

        // data accessors
        public void setFeature(int feature) {
            this.feature = feature;
        }

        // implementation of interface obligations
        @Override
        public Tuple2<Integer, TrainingData> call(Tuple2<Integer, TrainingData> tuple) throws Exception {
            Integer index = tuple._1;
            TrainingData data = tuple._2;
            int movieId = data.MovieId;
            int custId = data.CustId;
            int rating = data.Rating;
            double cache = data.Cache;
            double cf = customerFeatureMatrixRow_BRDCST.getValue().row.get(custId - 1);
            double mf = movieFeatureMatrixRow_BRDCST.getValue().row.get(movieId - 1);
            /* cache broadcast values in local variables to avoid calling them
               repeatedly and to make code easier to read */
            double INIT = SVDMovieLensSparkJava.this.INIT_BRDCST.getValue();
            double LRATE = SVDMovieLensSparkJava.this.LRATE_BRDCST.getValue();
            double K = SVDMovieLensSparkJava.this.K_BRDCST.getValue();
            double sum = (cache > 0) ? cache : 1;
            // Add contribution of current feature
            sum += mf * cf;
            if (sum > 5) {
                sum = 5;
            }
            if (sum < 1) {
                sum = 1;
            }
            // Add up trailing defaults values
            sum += (MAX_FEATURES_BRDCST.getValue() - feature - 1) * (INIT * INIT);
            if (sum > 5) {
                sum = 5;
            }
            if (sum < 1) {
                sum = 1;
            }
            double err = (1.0 * rating - sum);
            sq_ACC.add(err * err);
            /* I cannot update a single value in a MatrixRow Accumulator, so I
               have to construct an "zero" MatrixRow, update a single item and
               add this MatrixRow to the MatrixRow Accumulator!!
             */
            List<Double> custlst = new ArrayList<>();
            for (int i = 0; i < customerFeatureMatrixRow_BRDCST.getValue().row.size(); i++) {
                custlst.add(0.0);
            }
            custlst.set(custId - 1, LRATE * (err * mf - K * cf));
            if (feature == 1) {
                Message("CalculateSQ_InnerClass.call() - custFeatureRow (" + feature + ", " + custId + ") = " + custlst.get(custId - 1));
            }
            customerFeatureMatrixRow_ACC.add(new MatrixRow(custlst));
            List<Double> movielst = new ArrayList<>();
            for (int i = 0; i < movieFeatureMatrixRow_BRDCST.getValue().row.size(); i++) {
                movielst.add(0.0);
            }
            movielst.set(movieId - 1, LRATE * (err * cf - K * mf));
            movieFeatureMatrixRow_ACC.add(new MatrixRow(movielst));
            return tuple;
        }
    }

    /*
    class InnerFunctionClass3 implements PairFunction<Tuple2<Integer, Tuple3<Data, MatrixRow, MatrixRow>>, Integer, Tuple3<Data, MatrixRow, MatrixRow>> {

        // data members
        int feature;

        public InnerFunctionClass3() {
        }

        // data accessors
        public void setFeature(int feature) {
            this.feature = feature;
        }

        @Override
        public Tuple2<Integer, Tuple3<Data, MatrixRow, MatrixRow>> call(Tuple2<Integer, Tuple3<Data, MatrixRow, MatrixRow>> tuple) throws Exception {
            Integer index = tuple._1;
            Data data = tuple._2._1();
            int movieId = data.MovieId;
            int custId = data.CustId;
            int rating = data.Rating;
            float cache = data.Cache;
            MatrixRow movieRow = tuple._2._3();
            MatrixRow customerRow = tuple._2._2();
            float mf = movieRow.getRow().get(movieId);
            float cf = customerRow.getRow().get(custId);

            double sum = 1;

            for (int f = 0; f < MAX_FEATURES; f++) {
                
                sum += this.m_aMovieFeatures[f][movieId] * this.m_aCustFeatures[f][custId];
                if (sum > 5) {
                    sum = 5;
                }
                if (sum < 1) {
                    sum = 1;
                }
            }

            Tuple3<Data, MatrixRow, MatrixRow> t3 = new Tuple3<>(data, customerRow, movieRow);
            return new Tuple2<>(index, t3);
        }
    }
     */
    void spark_CalcFeatures() {
        int f, e;
        double /*err,*/ rmse_last = 0.0, rmse = 2.0;
        int movieId;
        double cf, mf;
        long tmp;
        double MIN_EPOCHS = this.MIN_EPOCHS_BRDCST.getValue();
        double MIN_IMPROVEMENT = this.MIN_IMPROVEMENT_BRDCST.getValue();

        // declare inner class variables to use in tranformations
        CalculateSQ_InnerClass funcCalcSQ = new CalculateSQ_InnerClass();
        for (f = 0; f < /*this.MAX_FEATURES*/ 1; f++) {
            Message("Calculating feature " + f);
            // Create an RDD with Data and the MatrixRows from CustomerFeature[f][] and MovieFeature[f][] matrices
            funcCalcSQ.setFeature(f);
            for (e = 0; /*(e < MIN_EPOCHS) || (rmse <= rmse_last - MIN_IMPROVEMENT)*/ e < 10; e++) {
                // Create 2 Broadcast variables for the Customer and Movie MatrixRows to have access to previous values
                customerFeatureMatrixRow_BRDCST = sc.broadcast(mapCustomerFeatures.get(f));
                movieFeatureMatrixRow_BRDCST = sc.broadcast(mapMovieFeatures.get(f));
                // Create the 2 Accumulators for the Customer and Movie MatrixRows
                customerFeatureMatrixRow_ACC = sc.accumulator(mapCustomerFeatures.get(f), new MatrixRowAccumulatorParam());
                movieFeatureMatrixRow_ACC = sc.accumulator(mapMovieFeatures.get(f), new MatrixRowAccumulatorParam());
                this.sq_ACC = sc.accumulator(0.0);
                rmse_last = rmse;
                m_aTrainingRatings_PairRDD = m_aTrainingRatings_PairRDD.mapToPair(funcCalcSQ);
                //m_aRatings.collect();
                tmp = m_aTrainingRatings_PairRDD.count();
                rmse = Math.sqrt(this.sq_ACC.value() / m_nRatingCount_BRDCST.getValue());
                // Update the maps with the accumulated MatrixRows
                mapCustomerFeatures.put(f, customerFeatureMatrixRow_ACC.value());
                mapMovieFeatures.put(f, movieFeatureMatrixRow_ACC.value());
                System.out.printf("|---------> Epoch = %d\tRmse_last-Rmse = %f\n", e, rmse_last - rmse);
            }

            Message("Customer ACCUMULATOR after transformation for Feature " + f + ", Epoch " + e + "\n" + customerFeatureMatrixRow_ACC.value().toString());
            //System.out.printf("|--> custmatrowAccum row 0 is %s\n", customerFeatureMatrixRow_ACC.value().toString());

            // Re-Calculate cache values
            //spark_m_FeatureDataRows = spark_m_FeatureDataRows.mapToPair(funcCalcCaches);
        }
    }

    // Calculate features matrices (Cannot be parallelized!) - Driver only
    void CalcFeatures() {
        int f, e, i, custId;
        TrainingData rating;
        double err, p, sq, rmse_last = 0.0, rmse = 2.0;
        int movieId;
        double cf, mf;

        for (f = 0; f < this.MAX_FEATURES; f++) {
            System.out.printf("--- Calculating feature: %d ---\n", f);
            // Keep looping until you have passed a minimum number 
            // of epochs or have stopped making significant progress 
            for (e = 0; (e < this.MIN_EPOCHS) || (rmse <= rmse_last - this.MIN_IMPROVEMENT); e++) {
                sq = 0;
                rmse_last = rmse;
                for (i = 0; i < this.m_nRatingCount; i++) {
                    rating = this.m_aRatings[i];
                    movieId = rating.MovieId;
                    custId = rating.CustId;
                    // Predict rating and calc error
                    p = PredictRating(movieId, custId, f, rating.Cache, true);
                    err = (1.0 * rating.Rating - p);
                    sq += err * err;
                    // Cache off old feature values
                    cf = m_aCustFeatures[f][custId];
                    mf = m_aMovieFeatures[f][movieId];
                    // Cross-train the features
                    m_aCustFeatures[f][custId] += (LRATE * (err * mf - K * cf));
                    m_aMovieFeatures[f][movieId] += (LRATE * (err * cf - K * mf));
                }
                rmse = Math.sqrt(sq / m_nRatingCount);
            }
            // Cache off old predictions
            for (i = 0; i < this.m_nRatingCount; i++) {
                rating = m_aRatings[i];
                rating.Cache = PredictRating(rating.MovieId, rating.CustId, f, rating.Cache, false);
            }
        }
        // initialize broadcast matrices to make them available in executors
        m_aCustFeatures_BRDCST = sc.broadcast(m_aCustFeatures);
        m_aMovieFeatures_BRDCST = sc.broadcast(m_aMovieFeatures);
    }

    // Reads in parallel the test file and produces the predictions file in parallel
    void ProcessTest() throws IOException {
        long cnt; // number of test ratings
        double sum; // sum of Abs difference between Rating and PredictionRating
        // read test file and create RDD of TrainingData items
        JavaRDD<TrainingData> c_testingData_RDD
                = this.sc.textFile(this.TESTING_FILE_BRDCST.getValue()).
                map(line -> line.split("\t")).
                map((String[] row) -> new TrainingData(Integer.parseInt(row[0]), Integer.parseInt(row[1]), Integer.parseInt(row[2])));
        // transform RDD of TrainingData to RDD of TestingData items with our predictions
        JavaRDD<TestingData> testingDataRDD
                = c_testingData_RDD.
                map((TrainingData t) -> new TestingData(t, PredictRating(t.CustId, t.MovieId)));
        // count test ratings
        cnt = testingDataRDD.count();
        // sum the abs differences between ratings and predictionratings
        sum = testingDataRDD.map(t -> t.diff()).reduce((a, b) -> a + b);
        // save RDD of TestingData to prediction file
        testingDataRDD.saveAsTextFile(this.PREDICTIONS_FILE_BRDCST.getValue());
        System.out.printf("\n---------------------------------------------\nNumber of predictions : %d\nAvg Abs(diff) : %f\n", cnt, sum / cnt);
    }

    // Used by sequential computations in CalcFeatures()
    double PredictRating(int movieId, int custId, int feature, double cache, boolean bTrailing) {
        // Get cached value for old features or default to an average
        double sum = (cache > 0) ? cache : 1;
        // Add contribution of current feature
        sum += this.m_aMovieFeatures[feature][movieId] * m_aCustFeatures[feature][custId];
        if (sum > 5) {
            sum = 5;
        }
        if (sum < 1) {
            sum = 1;
        }
        // Add up trailing defaults values
        if (bTrailing) {
            sum += (MAX_FEATURES - feature - 1) * (INIT * INIT);
            if (sum > 5) {
                sum = 5;
            }
            if (sum < 1) {
                sum = 1;
            }
        }
        return sum;
    }

    // Used by RDD transformation
    double PredictRating(int custId, int movieId) {
        double sum = 1;
        int f = 0;

        for (f = 0; f < this.MAX_FEATURES_BRDCST.getValue(); f++) {
            sum += this.m_aMovieFeatures_BRDCST.getValue()[f][movieId] * this.m_aCustFeatures_BRDCST.getValue()[f][custId];
            if (sum > 5) {
                sum = 5;
            }
            if (sum < 1) {
                sum = 1;
            }
        }
        return sum;
    }
}
