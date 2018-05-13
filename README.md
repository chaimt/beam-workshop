# beam-workshop
workshop for apache beam


https://cloud.google.com/dataflow/docs/quickstarts/quickstart-java-maven


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
          -Dexec.mainClass=com.tikal.beam.TrafficMaxLaneFlow \
          -Dexec.args="--project=backend-ct \
          --stagingLocation=gs://backend-ct/df/stage/ \
          --tempLocation=gs://backend-ct/df/temp/ \
          --serviceAccount=beam-workshop-may@backend-ct.iam.gserviceaccount.com \
          --googleCredentials=/Users/chaimt/workspace/chaim/beam-workshop/java/src/main/resources/Backend-CT-2b1affb0752f.json \
          --filesToStage=./target/beam-workshop-1.0.0-SNAPSHOT.jar \
          --runner=DataflowRunner"
          
mvn compile exec:java \
          -Dexec.mainClass=com.tikal.beam.MinimalWordCount \
          -Dexec.args="--project=backend-ct \
          --stagingLocation=gs://backend-ct/df/stage/ \
          --tempLocation=gs://backend-ct/df/temp/ \
          --inputFile=gs://backend-ct/Freeways-5Minaa2010-01-01_to_2010-02-15_test2.csv \
          --serviceAccount=beam-workshop-may@backend-ct.iam.gserviceaccount.com \
          --googleCredentials=/Users/chaimt/workspace/chaim/beam-workshop/java/src/main/resources/Backend-CT-2b1affb0752f.json \
          --filesToStage=./target/beam-workshop-1.0.0-SNAPSHOT.jar \
          --runner=DataflowRunner"
          



flink
    -Pflink-runner \
              
mvn exec:java -Dexec.mainClass=com.tikal.beam.TrafficMaxLaneFlow \
    -Dexec.args="--runner=FlinkRunner \
      --project=backend-ct \
      --tempLocation=gs://backend-ct/df/ \
      --inputFile=/Users/chaimt/workspace/chaim/beam-workshop/java/pom.xml \
      --flinkMaster=http://localhost:6123/ \
      --serviceAccount=beam-workshop1@backend-ct.iam.gserviceaccount.com \
      --filesToStage=target/original-beam-workshop-1.0.0-SNAPSHOT.jar"

          
