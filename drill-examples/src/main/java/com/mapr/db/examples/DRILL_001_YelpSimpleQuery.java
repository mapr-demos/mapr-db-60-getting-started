package com.mapr.db.examples;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Sample JDBC based application to obtain details of all businesses that have
 * at least 100 reviews and a rating greater than 3.
 */
public class DRILL_001_YelpSimpleQuery {
    public static String JDBC_DRIVER = "org.apache.drill.jdbc.Driver";
    public static String TABLE_NAME = "/mapr/maprdemo.mapr.io/apps/business";
    
    /**
     * Can specify connection URL in 2 ways.
     * 1. Connect to Zookeeper - "jdbc:drill:zk=<hostname/host-ip>:5181/drill/<cluster-name>-drillbits"
     * 2. Connect to Drillbit - "jdbc:drill:drillbit=<hostname>"
     */
    private static String DRILL_JDBC_URL = "jdbc:drill:drillbit=maprdemo";

    public static void main( String[] args ) {
        try {
            Class.forName(JDBC_DRIVER);
            //Username and password have to be provided to obtain connection.
            //Ensure that the user provided is present in the cluster / sandbox
            Connection connection = DriverManager.getConnection(DRILL_JDBC_URL, "root", "");

            Statement statement = connection.createStatement();

            final String sql = "SELECT b.`name`, b.`address`, b.`city`, b.`state`, b.`stars` " +
                    "FROM dfs.`" + TABLE_NAME + "` b " +
                    "WHERE b.`review_count` >= 100 AND b.`stars` > 3.5 " +
                    "ORDER BY b.`stars` DESC";
            System.out.println("Query: " + sql);

            ResultSet result = statement.executeQuery(sql);

            while(result.next()){
                System.out.println("{\"Name\": \"" + result.getString(1) + "\", "
                        + "\"Address\": \"" + result.getString(2) + "\", "
                        + "\"City\": \"" + result.getString(3) + "\", "
                        + "\"State\": \"" + result.getString(4) + "\", "
                        + "\"Stars\": " + result.getString(5) + "}");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
