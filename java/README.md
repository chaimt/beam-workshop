# beam-workshop
workshop for apache beam

## installation instructions

### prerequisites
    * java 8
    * IDE: intellij
    * maven
    * basic understanding of beam:
        * https://www.meetup.com/full-stack-developer-il/events/245825253/
        * https://www.youtube.com/watch?v=WIRzGJQZbGI
        * https://docs.google.com/presentation/d/1T-UkVP0sFOwjZ1ZZVzFj6DzwreYYismN5miDayfakNA/edit?usp=sharing

Project has maven pom for compilation and deployment.
assuming you have java 8 and maven installed on your machine:
(run java -version to make sure you are running java 8)

* run mvn clean package to download all dependencies
  * Project uses maven-shade-plugin to create an uber job to deploy
  * To read more information on dataflow and maven: see https://cloud.google.com/dataflow/docs/quickstarts/quickstart-java-maven
   
Currently we will run on google data flow, and in the future we will see how to deploy on flink and spark

## Project
There are two examples in this workshop

### TrafficMaxLaneFlow
This project will read a file from google storage, process it and then write the result to bigquery.
The file for the project can be found at:

* gs://apache-beam-samples/traffic_sensor/Freeways-5Minaa2010-01-01_to_2010-02-15_test2.csv
(this file is small: 622.61 KB, with 10,000 of records)
* gs://apache-beam-samples/traffic_sensor/Freeways-5Minaa2010-01-01_to_2010-02-15.csv
(this file is small: 1.92 GB, with millions of records)


#### Pre project understanding
* Knowledge
    * You should have a basic knowledge of apache beam before beginning. For this please see:
      https://beam.apache.org/get-started/beam-overview/
    * For authentication and authorization with google see:
      https://cloud.google.com/dataflow/security-and-permissions


* Running project in cloud
    ```
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
    ```

Project steps
* Step 1: copy data as is from storage to bigquery (master code)
    * ReadFileAndExtractTimestamps -> get time for windowing from event
    * ExtractFlowInfoFn -> extract data from csv to class
    * FormatMaxesFn -> convert class to row for writing in bigquery
    * BigQueryIO.writeTableRows() -> write to table using schema FormatMaxesFn.getSchema()

* Step 2: fix parse of data to duplicate each row per lane information    
   * fix method ExtractFlowInfoFn

* Step 3: add window to data, using options.getWindowDuration(), options.getWindowSlideEvery() for sliding window
   * use MaxFlow for the combine per key (Combine.perKey)
    
* Step 4: Pub/Sub
    * replace ReadFileAndExtractTimestamps -> with PubSub reader
    
    ```
    to inject data into the pubsub run the following command:
    
    mvn compile exec:java \
    -Dexec.mainClass=com.tikal.turkel.utils.Injector \
    -Dexec.args="backend-ct \
    topic \
    ./src/test/resources/traffic_sensor_test2.csv \
    ./src/main/resources/Backend-CT-4641c937bd57.json"
    ```

backend-ct one /Users/chaimt/Downloads/traffic_sensor_test2.csv /Users/chaimt/workspace/chaim/beam-workshop/java/src/main/resources/Backend-CT-4641c937bd57.json


   

          
