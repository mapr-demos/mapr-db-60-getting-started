package com.mapr.db.examples;

import org.ojai.Document;
import org.ojai.DocumentStream;
import org.ojai.store.Connection;
import org.ojai.store.DocumentStore;
import org.ojai.store.Driver;
import org.ojai.store.DriverManager;
import org.ojai.store.Query;
import org.ojai.store.QueryCondition;

/**
 * Obtains details of all restaurants that have
 * take-outs and a rating greater than 3.
 *
 * Also calculates the percentage of restaurants that offer take out, out of all restaurants
 * in Las Vegas that have take-out information available.
 */
@SuppressWarnings("Duplicates")
public class OJAI_005_YelpComplexQuery {
    public static final String OJAI_CONNECTION_URL = "ojai:mapr:";
    //Full path including namespace /mapr/<cluster-name>/apps/business
    public static final String TABLE_NAME = "/mapr/maprdemo.mapr.io/apps/business";

    public static void main( String[] args ) {
        try {
            Driver driver = DriverManager.getDriver(OJAI_CONNECTION_URL);

            //Get restaurants in Vegas which have take-out information
            QueryCondition condition = driver.newCondition()
                    .and()
                    .exists("attributes.RestaurantsTakeOut")
                    .is("city", QueryCondition.Op.EQUAL, "Las Vegas")
                    .close()
                    .build();
            System.out.println("Businesses with take-out info: " + condition.toString());

            Query query = driver.newQuery()
                    .select("name", "address", "stars")
                    .where(condition)
                    .orderBy("stars")
                    .build();

            int totalCount = 0;
            try (final Connection connection = DriverManager.getConnection(OJAI_CONNECTION_URL);
                 final DocumentStore store = connection.getStore(TABLE_NAME);
                 final DocumentStream stream = store.findQuery(query)) {
                System.out.println("Query Plan: " + stream.getQueryPlan().asJsonString()); //Log Query Plan for debugging
                System.out.println("Restaurants in Las Vegas with take-out information: ");
                for(Document document : stream) {
                    System.out.println(document.asJsonString());
                    totalCount++;
                }
            }

            /* Get restaurants that have take out and are rated greater than 3 */
            condition = driver.newCondition()
                    .and()
                    .condition(condition)
                    .is("attributes.RestaurantsTakeOut", QueryCondition.Op.EQUAL, true)
                    .is("stars", QueryCondition.Op.GREATER, 3)
                    .close()
                    .build();
            System.out.println("Restaurants with take-outs: " + condition.toString());

            query = driver.newQuery()
                    .select("name", "address", "stars")
                    .where(condition)
                    .build();

            int takeOutCount = 0;
            try (final Connection connection = DriverManager.getConnection(OJAI_CONNECTION_URL);
                 final DocumentStore store = connection.getStore(TABLE_NAME);
                 final DocumentStream stream = store.findQuery(query)) {
                System.out.println("Query Plan: " + stream.getQueryPlan().asJsonString()); //Log Query Plan for debugging
                System.out.println("Restaurants in Las Vegas with take-outs: ");
                for(Document document : stream) {
                    System.out.println(document.asJsonString());
                    takeOutCount++;
                }
            }

            System.out.println("Percentage of restaurants with take-outs that have high rating: "
                    + ((double)takeOutCount / totalCount) * 100 + "%");

        } catch (Exception e) {
            System.err.println("Application failed with " + e.getMessage());
            e.printStackTrace();
        }
    }
}
