# beam-workshop
workshop for apache beam

##installation instructions

Project has maven pom for compilation and deployment.
assuming you have java 8 and maven installed on your machine:
(run java -version to make sure you are running java 8)

* run mvn clean package to download all dependencies
  * Project uses maven-shade-plugin to create an uber job to deply
  * To read more information on dataflow and maven: see https://cloud.google.com/dataflow/docs/quickstarts/quickstart-java-maven
   
Currently we will run on google data flow, and in the future we will see how to deploy on flink and spark

##project
There are two examples in this workshop

###TrafficMaxLaneFlow
This project will read a file from google storage, process it and then write the result to bigquery.
The file for the project can be found at:

* gs://apache-beam-samples/traffic_sensor/Freeways-5Minaa2010-01-01_to_2010-02-15_test2.csv
(this file is small: 622.61 KB, with 10,000 of records)
* gs://apache-beam-samples/traffic_sensor/Freeways-5Minaa2010-01-01_to_2010-02-15.csv
(this file is small: 1.92 GB, with millions of records)


####Pre project understanding
* Knowledge
    * You should have a basic knowledge of apache beam before beginning. For this please see:
      https://beam.apache.org/get-started/beam-overview/
    * For authentication and authorization with google see:
      https://cloud.google.com/dataflow/security-and-permissions


Project steps
* Step 1: copy data as is from storage to bigquery (master code)
    * ReadFileAndExtractTimestamps -> get time for windowing from event
    * ExtractFlowInfoFn -> extract data from csv to class
    * FormatMaxesFn -> convert class to row for writing in bigquery
    * BigQueryIO.writeTableRows() -> write to table using schema FormatMaxesFn.getSchema()

To run the code, use maven to run the main class with parameters 
    
mvn compile exec:java -Dexec.mainClass=com.tikal.turkel.TrafficMaxLaneFlow -Dexec.args="--project=backend-ct \
          --stagingLocation=gs://backend-ct/df/stage/ \
          --tempLocation=gs://backend-ct/df/temp/ \
          --googleCredentials=./src/main/resources/Backend-CT-4641c937bd57.json
          --serviceAccount=beam-workshop-may@backend-ct.iam.gserviceaccount.com \
          --filesToStage=./target/beam-workshop-bundled-1.0-SNAPSHOT.jar \
          --runner=DataflowRunner"

You can view the run result at:
          
https://console.cloud.google.com/dataflow?project=backend-ct


* Step 2: fix parse of data to duplicate each row per lane information    
   * fix method ExtractFlowInfoFn

* Step 3: add window to data, using options.getWindowDuration(), options.getWindowSlideEvery() for sliding window
   * use MaxFlow for the combine per key (Combine.perKey)
    
* Step 4: Pub/Sub

backend-ct one /Users/chaimt/Downloads/traffic_sensor_test2.csv /Users/chaimt/workspace/chaim/beam-workshop/java/src/main/resources/Backend-CT-4641c937bd57.json

--project=backend-ct
--stagingLocation=gs://backend-ct/df/
--tempLocation=gs://backend-ct/df/
--serviceAccount=beam-workshop@backend-ct.iam.gserviceaccount.com
--runner=DataflowRunner


export GOOGLE_APPLICATION_CREDENTIALS=/Users/chaimt/workspace/chaim/beam-workshop/java/src/main/resources/Backend-CT-2b1affb0752f.json

mvn compile exec:java \
          -Dexec.mainClass=com.tikal.beam.TrafficMaxLaneFlow \
          -Dexec.args="--project=backend-ct \
          --stagingLocation=gs://backend-ct/df/ \
          --tempLocation=gs://backend-ct/df/ \
          --serviceAccount=beam-workshop1@backend-ct.iam.gserviceaccount.com \
          --filesToStage=./target/beam-workshop-1.0.0-SNAPSHOT.jar \
          --runner=DataflowRunner"
          
           
          
          --filesToStage=./target/migration-dataflow-bundled-1.0.0-SNAPSHOT.jar \
          --runner=DataflowRunner"                     
```
mvn compile exec:java \
          -Dexec.mainClass=com.tikal.turkel.TrafficMaxLaneFlow \
          -Dexec.args="--project=backend-ct \
          --stagingLocation=gs://backend-ct/df/stage/ \
          --tempLocation=gs://backend-ct/df/temp/ \
          --serviceAccount=beam-workshop-may@backend-ct.iam.gserviceaccount.com \
          --filesToStage=./target/beam-workshop-bundled-1.0-SNAPSHOT.jar \
          --runner=DataflowRunner"

          --inputFile=gs://apache-beam-samples/traffic_sensor/Freeways-5Minaa2010-01-01_to_2010-02-15.csv \
          
mvn compile exec:java \
          -Dexec.mainClass=com.tikal.turkel.TrafficMaxLaneFlow \
          -Dexec.args="--project=backend-ct \
          --stagingLocation=gs://backend-ct/df/stage/ \
          --tempLocation=gs://backend-ct/df/temp/ \
          --googleCredentials=/Users/chaimt/workspace/chaim/beam-workshop/java/src/main/resources/Backend-CT-4641c937bd57.json
          --serviceAccount=beam-workshop-may@backend-ct.iam.gserviceaccount.com \
          --inputFile=gs://apache-beam-samples/traffic_sensor/Freeways-5Minaa2010-01-01_to_2010-02-15.csv \
          --filesToStage=./target/beam-workshop-bundled-1.0-SNAPSHOT.jar \
          --runner=DataflowRunner"
          
          
mvn compile exec:java \
          -Dexec.mainClass=com.tikal.turkel.MinimalWordCount \
          -Dexec.args="--project=backend-ct \
          --stagingLocation=gs://backend-ct/df/stage/ \
          --tempLocation=gs://backend-ct/df/temp/ \
          --serviceAccount=beam-workshop-may@backend-ct.iam.gserviceaccount.com \
          --filesToStage=./target/beam-workshop-bundled-1.0-SNAPSHOT.jar \
          --runner=DataflowRunner"
          
                    --googleCredentials=/Users/chaimt/workspace/chaim/beam-workshop/java/src/main/resources/Backend-CT-2b1affb0752f.json \
          



flink
    -Pflink-runner \
    
              
mvn -Pflink-runner exec:java -Dexec.mainClass=com.tikal.turkel.TrafficMaxLaneFlow \
    -Dexec.args="--runner=FlinkRunner \
      --inputFile=gs://apache-beam-samples/traffic_sensor/Freeways-5Minaa2010-01-01_to_2010-02-15.csv \
      --googleCredentials=/Users/chaimt/workspace/chaim/beam-workshop/java/src/main/resources/Backend-CT-4641c937bd57.json \
      --project=backend-ct \
      --tempLocation=gs://backend-ct/df/temp/ \
      --flinkMaster=http://localhost:6123/ \
      --serviceAccount=beam-workshop1@backend-ct.iam.gserviceaccount.com \
      --filesToStage=target/beam-workshop-bundled-1.0-SNAPSHOT.jar"
      
      
      
      
mvn exec:java -Dexec.mainClass=com.tikal.turkel.WordCount \
    -Pflink-runner \
    -Dexec.args="--runner=FlinkRunner \
      --project=backend-ct \
      --tempLocation=gs://backend-ct/df/temp/ \
      --output=gs://backend-ct/df/out/ \
      --serviceAccount=beam-workshop-may@backend-ct.iam.gserviceaccount.com \
      --flinkMaster=http://localhost:6123/ \
      --filesToStage=target/beam-workshop-bundled-1.0-SNAPSHOT.jar"
      
      
                --stagingLocation=gs://backend-ct/df/stage/ \
                --tempLocation=gs://backend-ct/df/temp/ \
                --serviceAccount=beam-workshop-may@backend-ct.iam.gserviceaccount.com \
                --filesToStage=./target/beam-workshop-bundled-1.0-SNAPSHOT.jar \
                --runner=DataflowRunner"



./bin/flink run -c com.tikal.turkel.TrafficMaxLaneFlow /Users/chaimt/workspace/chaim/beam-workshop/java/target/beam-workshop-bundled-1.0-SNAPSHOT.jar \
      --inputFile=gs://apache-beam-samples/traffic_sensor/Freeways-5Minaa2010-01-01_to_2010-02-15.csv \
            --F=/Users/chaimt/workspace/chaim/beam-workshop/java/src/main/resources/Backend-CT-4641c937bd57.json \
            --project=backend-ct \
            --tempLocation=gs://backend-ct/df/temp/ \
            --serviceAccount=beam-workshop1@backend-ct.iam.gserviceaccount.com 


./bin/flink run -m myJMHost:6123 /Users/chaimt/workspace/chaim/beam-workshop/java/target/beam-workshop-bundled-1.0-SNAPSHOT.jar \
      --project=backend-ct \
      --tempLocation=gs://backend-ct/df/temp/ \
      --output=gs://backend-ct/df/out/ \
      --serviceAccount=beam-workshop-may@backend-ct.iam.gserviceaccount.com

          
