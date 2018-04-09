package com.example.hbase.bulkload;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.HFileOutputFormat;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.parquet.Log;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.example.ExampleInputFormat;
import org.apache.parquet.hadoop.example.GroupWriteSupport;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.schema.MessageType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created by yilong on 2018/2/4.
 */
public class KVMapperOnParquetIn extends Configured implements Tool {
    private static final Log LOG =
            Log.getLog(KVMapperOnParquetIn.class);

    /*
     * Read a Parquet record
     */

    private static class FieldDescription {
        public String constraint;
        public String type;
        public String name;
    }

    private static class RecordSchema {
        public RecordSchema(String message) {
            fields = new ArrayList<FieldDescription>();
            List<String> elements = Arrays.asList(message.split("\n"));
            Iterator<String> it = elements.iterator();
            while(it.hasNext()) {
                String line = it.next().trim().replace(";", "");;
                System.err.println("RecordSchema read line: " + line);
                if(line.startsWith("optional") || line.startsWith("required")) {
                    String[] parts = line.split(" ");
                    FieldDescription field = new FieldDescription();
                    field.constraint = parts[0];
                    field.type = parts[1];
                    field.name = parts[2];
                    fields.add(field);
                }
            }
        }
        private List<FieldDescription> fields;
        public List<FieldDescription> getFields() {
            return fields;
        }
    }

    public static class MyMap extends Mapper<LongWritable,Group,ImmutableBytesWritable,KeyValue> {
        ImmutableBytesWritable hKey = new ImmutableBytesWritable();
        KeyValue kv;
        final static byte[] SRV_COL_FAM = "f".getBytes();

        private static List<FieldDescription> expectedFields = null;

        @Override
        //public void map(LongWritable longWritable, Group group, OutputCollector<ImmutableBytesWritable, KeyValue> outputCollector, Reporter reporter) throws IOException {
        public void map(LongWritable key, Group group, Context context) throws IOException, InterruptedException {
            if(expectedFields == null) {
                // Get the file schema which may be different from the fields in a particular record) from the input split
                //String fileSchema = ((ParquetInputSplit)context.get());
                //RecordSchema schema = new RecordSchema(fileSchema);
                //expectedFields = schema.getFields();
            }

            hKey.set(String.format("%s:%s", group.getString("name",0),""+group.getInteger("age",0))
                    .getBytes());

            byte[] bytes = ByteBuffer.allocate(4).putInt(group.getInteger("age",0)).array();

            kv = new KeyValue(hKey.get(), SRV_COL_FAM,
                    group.getType().getFields().get(1).getName().getBytes(), bytes);

            String line = group.toString();
            String[] fields = line.split("\n");
            /*Iterator<FieldDescription> it = expectedFields.iterator();
            boolean hasContent = false;
            int i = 0;
            while(it.hasNext()) {
                if(hasContent ) {
                }

                String name = it.next().name;
                if(fields.length > i) {
                    String[] parts = fields[i].split(": ");
                    // We assume proper order, but there may be fields missing
                    if(parts[0].equals(name)) {
                        boolean mustQuote = (parts[1].contains(",") || parts[1].contains("'"));
                        if(mustQuote) {

                        }

                        if(mustQuote) {

                        }

                        hasContent = true;
                        i++;
                    }
                }
            }*/

            if (context != null) {
                context.write(hKey, kv);
            } else {
                LOG.error("outputCollector is null");
            }
        }
    }

    public int run(String[] args) throws Exception {
        Configuration baseconf = new Configuration();

        Job job = new Job(baseconf);

        String inputfile = args[0];
        String outputfile = args[1];
        String hbasetable = args[2];

        Path parquetFilePath = null;
        // Find a file in case a directory was passed
        RemoteIterator<LocatedFileStatus> it = FileSystem.get(baseconf).listFiles(new Path(inputfile), true);
        while(it.hasNext()) {
            FileStatus fs = it.next();
            if(fs.isFile()) {
                parquetFilePath = fs.getPath();
                break;
            }
        }

        if(parquetFilePath == null) {
            LOG.error("No file found for " + inputfile);
            return 1;
        }

        LOG.info("Getting schema from " + parquetFilePath);
        ParquetMetadata readFooter = ParquetFileReader.readFooter(baseconf, parquetFilePath);
        MessageType schema = readFooter.getFileMetaData().getSchema();
        LOG.info(schema);
        GroupWriteSupport.setSchema(schema, job.getConfiguration());

        job.setJarByClass(getClass());
        job.setJobName(getClass().getName());

        job.setMapOutputKeyClass(ImmutableBytesWritable.class);
        job.setMapOutputValueClass(KeyValue.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        job.setMapperClass(MyMap.class);

        job.setInputFormatClass(ExampleInputFormat.class);

        Configuration bhaseConf = HBaseConfiguration.create();

        HTable hTable = new HTable(bhaseConf, hbasetable);
        //Connection conn = ConnectionFactory.createConnection();
        //Table table = conn.getTable(TableName.valueOf(hbasetable));

        // Auto configure partitioner and reducer
        HFileOutputFormat.configureIncrementalLoad(job, hTable);

        FileInputFormat.addInputPath(job, new Path(inputfile));
        FileOutputFormat.setOutputPath(job, new Path(outputfile));

        job.waitForCompletion(true);

        return 0;
    }

    //bin/hbase org.apache.hadoop.hbase.mapreduce.LoadIncrementalHFiles  /user/emp1file/f emp1_hbase
    public static void main(String[] args) throws Exception {
        try {
            int res = ToolRunner.run(new Configuration(), new KVMapperOnParquetIn(), args);
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(255);
        }
    }
}
