/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tikal.turkel;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;
import com.tikal.turkel.common.ExampleBigQueryTableOptions;
import com.tikal.turkel.common.ExampleOptions;
import com.tikal.turkel.common.ExamplePubsubTopicAndSubscriptionOptions;
import com.tikal.turkel.common.ExampleUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.avro.reflect.Nullable;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.DefaultCoder;
import org.apache.beam.sdk.extensions.sql.BeamSql;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.options.*;
import org.apache.beam.sdk.transforms.*;
import org.apache.beam.sdk.transforms.windowing.SlidingWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.Row;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A Beam Example that runs in both batch and streaming modes with traffic sensor data.
 * You can configure the running mode by setting {@literal --streaming} to true or false.
 *
 * <p>Concepts: The batch and streaming runners, sliding windows,
 * use of the AvroCoder to encode a custom class, and custom Combine transforms.
 *
 * <p>This example analyzes traffic sensor data using SlidingWindows. For each window,
 * it finds the lane that had the highest flow recorded, for each sensor station. It writes
 * those max values along with auxiliary info to a BigQuery table.
 *
 * <p>The pipeline reads traffic sensor data from {@literal --inputFile}.
 *
 * <p>The example is configured to use the default BigQuery table from the example common package
 * (there are no defaults for a general Beam pipeline).
 * You can override them by using the {@literal --bigQueryDataset}, and {@literal --bigQueryTable}
 * options. If the BigQuery table do not exist, the example will try to create them.
 *
 * <p>The example will try to cancel the pipelines on the signal to terminate the process (CTRL-C)
 * and then exits.
 */
public class TrafficMaxLaneFlow {

    static final int WINDOW_DURATION = 60;  // Default sliding window duration in minutes
    static final int WINDOW_SLIDE_EVERY = 5;  // Default window 'slide every' setting in minutes

    /**
     * Sets up and starts streaming pipeline.
     *
     * @throws IOException if there is a problem setting up resources
     */
    public static void main(String[] args) throws IOException {
        TrafficMaxLaneFlowOptions options = PipelineOptionsFactory.fromArgs(args)
                .withValidation()
                .as(TrafficMaxLaneFlowOptions.class);
        System.getProperties().setProperty("GOOGLE_APPLICATION_CREDENTIALS", options.getGoogleCredentials());
        options.setBigQuerySchema(FormatMaxesFn.getSchema());
        // Using ExampleUtils to set up required resources.
        ExampleUtils exampleUtils = new ExampleUtils(options);
        exampleUtils.setup();

        Pipeline pipeline = Pipeline.create(options);
        TableReference tableRef = new TableReference();
        tableRef.setProjectId(options.getProject());
        tableRef.setDatasetId(options.getBigQueryDataset());
        tableRef.setTableId(options.getBigQueryTable());

        ExamplePubsubTopicAndSubscriptionOptions pubsubOptions =
                options.as(ExamplePubsubTopicAndSubscriptionOptions.class);

        PCollection<String> readLines = options.isStreaming() ?
                pipeline.apply("ReadLines pubsub", PubsubIO.readStrings().fromTopic(pubsubOptions.getPubsubTopic()))
                :
                pipeline.apply("ReadLines", new ReadFileAndExtractTimestamps(options.getInputFile()));

        PCollection<KV<String, LaneInfo>> lanes = readLines
                .apply(ParDo.of(new ExtractFlowInfoFn()))
                .apply(
                        Window.into(
                                SlidingWindows.of(Duration.standardMinutes(options.getWindowDuration()))
                                        .every(Duration.standardMinutes(options.getWindowSlideEvery()))));

        PCollection<TableRow> maxRows = options.isSql() ?
                lanes.apply(new LaneFlow())
                :
                lanes.apply(new MaxLaneFlow());

        maxRows.apply(BigQueryIO.writeTableRows().to(tableRef).withSchema(FormatMaxesFn.getSchema()));

        // Run the pipeline.
//        PipelineResult result =
        pipeline.run();

        // ExampleUtils will try to cancel the pipeline and the injector before the program exists.
//    exampleUtils.waitToFinish(result);
    }

    /**
     * Extract the timestamp field from the input string, and use it as the element timestamp.
     */
    static class ExtractTimestamps extends DoFn<String, String> {
        private static final DateTimeFormatter dateTimeFormat =
                DateTimeFormat.forPattern("MM/dd/yyyy HH:mm:ss");

        @ProcessElement
        public void processElement(DoFn<String, String>.ProcessContext c) throws Exception {
            String[] items = c.element().split(",");
            if (items.length > 0) {
                try {
                    String timestamp = items[0];
                    c.outputWithTimestamp(c.element(), new Instant(dateTimeFormat.parseMillis(timestamp)));
                } catch (IllegalArgumentException e) {
                    // Skip the invalid input.
                }
            }
        }
    }

    /**
     * Options supported by {@link TrafficMaxLaneFlow}.
     *
     * <p>Inherits standard configuration options.
     */
    public interface TrafficMaxLaneFlowOptions extends StreamingOptions, ExampleOptions, ExampleBigQueryTableOptions {
        @Description("Path of the file to read from")
        @Default.String("gs://apache-beam-samples/traffic_sensor/"
                + "Freeways-5Minaa2010-01-01_to_2010-02-15_test2.csv")
        String getInputFile();

        void setInputFile(String value);

        @Description("Numeric value of sliding window duration, in minutes")
        @Default.Integer(WINDOW_DURATION)
        Integer getWindowDuration();

        void setWindowDuration(Integer value);

        @Description("Numeric value of window 'slide every' setting, in minutes")
        @Default.Integer(WINDOW_SLIDE_EVERY)
        Integer getWindowSlideEvery();

        void setWindowSlideEvery(Integer value);

        @Validation.Required
        String getGoogleCredentials();

        void setGoogleCredentials(String value);

        boolean isSql();
        void setSql(boolean value);


    }

    /**
     * A custom 'combine function' used with the Combine.perKey transform. Used to find the max lane
     * flow over all the data points in the Window. Extracts the lane flow from the input string and
     * determines whether it's the max seen so far. We're using a custom combiner instead of the Max
     * transform because we want to retain the additional information we've associated with the flow
     * value.
     */
    public static class MaxFlow implements SerializableFunction<Iterable<LaneInfo>, LaneInfo> {
        @Override
        public LaneInfo apply(Iterable<LaneInfo> input) {
            Integer max = 0;
            LaneInfo maxInfo = new LaneInfo();
            for (LaneInfo item : input) {
                Integer flow = item.getLaneFlow();
                if (flow != null && (flow >= max)) {
                    max = flow;
                    maxInfo = item;
                }
            }
            return maxInfo;
        }
    }


    static class FormatRowFn extends DoFn<Row, TableRow> {
        @ProcessElement
        public void processElement(ProcessContext c) {
            Row element = c.element();
            TableRow row = new TableRow()
//                    .set("station_id", element.getInt32(station_id))
                    .set("direction", element.getString("direction"))
//                    .set("freeway", laneInfo.getFreeway())
//                    .set("lane_max_flow", laneInfo.getLaneFlow())
//                    .set("lane", laneInfo.getLane())
//                    .set("avg_occ", laneInfo.getLaneAO())
//                    .set("avg_speed", laneInfo.getLaneAS())
//                    .set("total_flow", laneInfo.getTotalFlow())
//                    .set("recorded_timestamp", laneInfo.getRecordedTimestamp())
                    .set("window_timestamp", c.timestamp().toString());
            c.output(row);
        }
    }

    /**
     * Format the results of the Max Lane flow calculation to a TableRow, to save to BigQuery.
     * Add the timestamp from the window context.
     */
    static class FormatMaxesFn extends DoFn<KV<String, LaneInfo>, TableRow> {
        @ProcessElement
        public void processElement(ProcessContext c) {
            LaneInfo laneInfo = c.element().getValue();
            TableRow row = new TableRow()
                    .set("station_id", c.element().getKey())
                    .set("direction", laneInfo.getDirection())
                    .set("freeway", laneInfo.getFreeway())
                    .set("lane_max_flow", laneInfo.getLaneFlow())
                    .set("lane", laneInfo.getLane())
                    .set("avg_occ", laneInfo.getLaneAO())
                    .set("avg_speed", laneInfo.getLaneAS())
                    .set("total_flow", laneInfo.getTotalFlow())
                    .set("recorded_timestamp", laneInfo.getRecordedTimestamp())
                    .set("window_timestamp", c.timestamp().toString());
            c.output(row);
        }

        /**
         * Defines the BigQuery schema used for the output.
         */
        static TableSchema getSchema() {
            List<TableFieldSchema> fields = new ArrayList<>();
            fields.add(new TableFieldSchema().setName("station_id").setType("STRING"));
            fields.add(new TableFieldSchema().setName("direction").setType("STRING"));
            fields.add(new TableFieldSchema().setName("freeway").setType("STRING"));
            fields.add(new TableFieldSchema().setName("lane_max_flow").setType("INTEGER"));
            fields.add(new TableFieldSchema().setName("lane").setType("STRING"));
            fields.add(new TableFieldSchema().setName("avg_occ").setType("FLOAT"));
            fields.add(new TableFieldSchema().setName("avg_speed").setType("FLOAT"));
            fields.add(new TableFieldSchema().setName("total_flow").setType("INTEGER"));
            fields.add(new TableFieldSchema().setName("window_timestamp").setType("TIMESTAMP"));
            fields.add(new TableFieldSchema().setName("recorded_timestamp").setType("STRING"));
            TableSchema schema = new TableSchema().setFields(fields);
            return schema;
        }
    }

    static class LaneFlow
            extends PTransform<PCollection<KV<String, LaneInfo>>, PCollection<TableRow>> {
        @Override
        public PCollection<TableRow> expand(PCollection<KV<String, LaneInfo>> flowInfo) {

            PCollection<Row> apply = flowInfo.apply(BeamSql.query("select max(LaneFlow) from PCOLLECTION"));
            PCollection<TableRow> results = apply.apply(
                    ParDo.of(new FormatRowFn()));
            return results;
        }
    }

    static class MaxLaneFlow
            extends PTransform<PCollection<KV<String, LaneInfo>>, PCollection<TableRow>> {
        @Override
        public PCollection<TableRow> expand(PCollection<KV<String, LaneInfo>> flowInfo) {
            PCollection<KV<String, LaneInfo>> flowMaxes = flowInfo.apply(Combine.perKey(new MaxFlow()));
            PCollection<TableRow> results = flowMaxes.apply(
                    ParDo.of(new FormatMaxesFn()));
            return results;
        }
    }

    static class ReadFileAndExtractTimestamps extends PTransform<PBegin, PCollection<String>> {
        private final String inputFile;

        public ReadFileAndExtractTimestamps(String inputFile) {
            this.inputFile = inputFile;
        }

        @Override
        public PCollection<String> expand(PBegin begin) {
            return begin
                    .apply(TextIO.read().from(inputFile))
                    .apply(ParDo.of(new ExtractTimestamps()));
        }
    }

    /**
     * This class holds information about each lane in a station reading, along with some general
     * information from the reading.
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @DefaultCoder(AvroCoder.class)
    static class LaneInfo {
        @Nullable String stationId;
        @Nullable String lane;
        @Nullable String direction;
        @Nullable String freeway;
        @Nullable String recordedTimestamp;
        @Nullable Integer laneFlow;
        @Nullable Integer totalFlow;
        @Nullable Double laneAO;
        @Nullable Double laneAS;
    }

    /**
     * Extract flow information for each of the 8 lanes in a reading, and output as separate tuples.
     * This will let us determine which lane has the max flow for that station over the span of the
     * window, and output not only the max flow from that calculation, but other associated
     * information. The number of lanes for which data is present depends upon which freeway the data
     * point comes from.
     */
    static class ExtractFlowInfoFn extends DoFn<String, KV<String, LaneInfo>> {

        @ProcessElement
        public void processElement(ProcessContext c) {
            String[] items = c.element().split(",");
            if (items.length < 48) {
                // Skip the invalid input.
                return;
            }
            // extract the sensor information for the lanes from the input string fields.
            String timestamp = items[0];
            String stationId = items[1];
            String freeway = items[2];
            String direction = items[3];
            Integer totalFlow = tryIntParse(items[7]);
            for (int i = 1; i <= 8; ++i) {
                Integer laneFlow = tryIntParse(items[6 + 5 * i]);
                Double laneAvgOccupancy = tryDoubleParse(items[7 + 5 * i]);
                Double laneAvgSpeed = tryDoubleParse(items[8 + 5 * i]);
                if (laneFlow == null || laneAvgOccupancy == null || laneAvgSpeed == null) {
                    return;
                }
                LaneInfo laneInfo = new LaneInfo(stationId, "lane" + i, direction, freeway, timestamp,
                        laneFlow, totalFlow, laneAvgOccupancy, laneAvgSpeed);
                c.output(KV.of(stationId, laneInfo));
            }
        }
    }

    private static Integer tryIntParse(String number) {
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double tryDoubleParse(String number) {
        try {
            return Double.parseDouble(number);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
