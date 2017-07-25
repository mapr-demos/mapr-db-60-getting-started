package com.mapr.db.samples;

import org.ojai.Document;
import org.ojai.DocumentStream;
import org.ojai.store.Connection;
import org.ojai.store.DocumentStore;
import org.ojai.store.DriverManager;
import org.ojai.store.Query;
import org.ojai.store.QueryCondition;

import java.util.UUID;

public class OJAI_001_YelpSimpleQuery {


  public static void main(String[] args) {

    System.out.println("==== Start Application ===");

    // Create an OJAI connection to MapR cluster
    Connection connection = DriverManager.getConnection("ojai:mapr:");
    // Get an instance of OJAI
    DocumentStore store = connection.getStore("/apps/user");

    Query query = connection.newQuery()
            .select("name", "yelping_since", "support") // projection
            .where( connection.newCondition().is("support", QueryCondition.Op.EQUAL, "gold").build()    ) // condition
            .build();

    long startTime = System.currentTimeMillis();
    int counter = 0;
    DocumentStream stream = store.findQuery(query);
    for (Document userDocument : stream) {
      // Print the OJAI Document
      System.out.println("\t"+ userDocument.asJsonString());
      counter++;
    }
    long endTime = System.currentTimeMillis();


    System.out.println(String.format("\t %d found in %d ms", counter, (endTime-startTime) ));


    // Close this instance of OJAI DocumentStore
    store.close();

    // close the OJAI connection and release any resources held by the connection
    connection.close();

    System.out.println("==== End Application ===");
  }


}
