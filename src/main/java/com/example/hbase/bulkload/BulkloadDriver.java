package com.example.hbase.bulkload;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.HFileOutputFormat2;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

/**
 * HBase bulk import example<br>
 * Data preparation MapReduce job driver
 * <ol>
 * <li>args[0]: HDFS input path
 * <li>args[1]: HDFS output path
 * <li>args[2]: HBase table name
 * </ol>
 */
public class BulkloadDriver {
  public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    args = new GenericOptionsParser(conf, args).getRemainingArgs();

    /*
     * NBA Final 2010 game 1 tip-off time (seconds from epoch) 
     * Thu, 03 Jun 2010 18:00:00 PDT
     */
    conf.setInt("epoch.seconds.tipoff", 1275613200);
    conf.set("hbase.table.name", args[2]);
    
    // Load hbase-site.xml 
    HBaseConfiguration.addHbaseResources(conf);

    Job job = new Job(conf, "HBase Bulk Import Example");
    job.setJarByClass(KVMapperOnCSVIn.class);

    job.setMapperClass(KVMapperOnCSVIn.class);
    job.setMapOutputKeyClass(ImmutableBytesWritable.class);
    job.setMapOutputValueClass(KeyValue.class);

    job.setInputFormatClass(TextInputFormat.class);

    //HTable hTable = new HTable(args[2]);
    Connection conn = ConnectionFactory.createConnection(conf);
    Table table = conn.getTable(TableName.valueOf(args[2]));
    
    // Auto configure partitioner and reducer
    //HFileOutputFormat2.configureIncrementalLoad(job, hTable);
    HFileOutputFormat2.configureIncrementalLoadMap(job, table);

    FileInputFormat.addInputPath(job, new Path(args[0]));
    FileOutputFormat.setOutputPath(job, new Path(args[1]));

    job.waitForCompletion(true);
  }
}
