package com.mapr.db.examples

import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonProperty}
import com.mapr.db.spark._
import org.apache.spark.{SparkConf, SparkContext}

/**
  * As a part of this demo we load the business table and obtain city with most
  * restaurant rated more than 3 stars.
  */
object SPARK_001_YelpQueryRDD {
  // Specify the table name with a path that includes the full MapR-FS namespace
  // (e.g. /mapr/<cluster-name>/apps/business) or with an abbreviated path
  // (e.g. /apps/business)
  val tableName: String = "/mapr/maprdemo.mapr.io/apps/business"
  def main(args: Array[String]): Unit = {
    val spark = new SparkConf().setAppName("SPARK_001_YelpQueryRDD").setMaster("local[*]")
    val sc = new SparkContext(spark)

    val businessRDD = sc
      .loadFromMapRDB[Business](tableName).
      where(field("stars") > 3)

    // Here's how you compare for equality. Note the three "=" signs:
    sc.loadFromMapRDB[Business](tableName).where(field("stars") === 3).count
    // Comparing for >= is pretty straightforward:
    sc.loadFromMapRDB[Business](tableName).where(field("stars") >= 3).count

    println(businessRDD
      .map(business => (business.city, 1))
      .reduceByKey(_ + _)
      .map(x => (x._2, x._1))
      .sortByKey(ascending = false).first())
  }
}


@JsonIgnoreProperties(ignoreUnknown = true)
case class Business (@JsonProperty("_id") id: String,
                   @JsonProperty("name") name: String,
                   @JsonProperty("review_count") review_count: Int,
                   @JsonProperty("stars") stars: Float,
                   @JsonProperty("address") address: String,
                   @JsonProperty("city") city: String,
                   @JsonProperty("state") state: String)