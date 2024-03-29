import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.util.EC2MetadataUtils;

import java.io.*;
import java.util.ArrayList;

public class EC2Client {

    S3Handler s3Input;
    S3Handler s3Output;
    SqsHandler sqsInputQueue;
    SqsHandler sqsInstanceQueue;
    EC2Handler ec2;

    public EC2Client()
    {
        Regions region = Regions.US_EAST_1;
        sqsInputQueue = new SqsHandler("inputqueue");
        sqsInstanceQueue = new SqsHandler("instancequeue");
        s3Input = new S3Handler(region, "ccproj1inputbucket");
        s3Output = new S3Handler(region, "ccproj1outputbucket");
        ec2 = new EC2Handler();

        File f = new File("/home/ubuntu/darknet/results_parsed");
        if (!f.exists())
            f.mkdir();

        File saveDir = new File("/home/ubuntu/darknet/videos");

        if (!saveDir.exists())
            saveDir.mkdir();
    }
    public int downloadVideo(String filename) throws InterruptedException {
        int res=0;
        int retries = 0;
        while(res == 0 && retries < 2)
        {
            res = s3Input.Download(filename, "/home/ubuntu/darknet/videos/");
            if(res ==0 )
            {
                Thread.sleep(5000);
                System.out.println("Retrying download...");
            }
            retries++;
        }


        return res;

    }
    public void runDarknet(String filename) throws IOException, InterruptedException
    {
        String line;

        ProcessBuilder p = new ProcessBuilder()
                .command("/home/ubuntu/darknet/darknet", "detector", "demo", "/home/ubuntu/darknet/cfg/coco.data", "/home/ubuntu/darknet/cfg/yolov3-tiny.cfg", "/home/ubuntu/darknet/yolov3-tiny.weights", "/home/ubuntu/darknet/videos/" + filename)
                .redirectOutput(new File("/home/ubuntu/darknet/results/" + filename));

        Process process = p.start();
        process.waitFor();

        // Read and parse results
        BufferedReader reader = new BufferedReader(new FileReader("/home/ubuntu/darknet/results/" + filename));
        String resultString = "";
        int flag = 0;
        ArrayList<String> objectList = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            //System.out.println("reading file");
            if (line.equals("Objects:")) {
                //System.out.println("flag set");
                flag = 1;

            } else if (line.contains("FPS:")) {
                //System.out.println("flag unset");
                flag = 0;
            } else if (flag == 1 && !line.isEmpty() && !line.contains("[1;1H")) {
                //System.out.println("got object");
                System.out.println(line);
                String object = line.split(":")[0];
                if (!objectList.contains(object)) {
                    objectList.add(object);
                    resultString += ',' + object;
                }
            }
        }


        System.out.println("result" + resultString);
        if (resultString.isEmpty())
            resultString = "No Object Detected";
        else
            resultString = resultString.substring(1);

        System.out.println("result" + resultString);
        BufferedWriter writer = new BufferedWriter(new FileWriter("/home/ubuntu/darknet/results_parsed/" + filename));
        writer.write(resultString);
        writer.close();
    }

    public String getFilename()
    {
        Message m = sqsInputQueue.ReceiveMessage();
        String filename;

        if (m != null)
        {
            filename = m.getBody();
            sqsInputQueue.deleteMessage(m);
        }
        else
            return null;

        return filename;
    }

    public void uploadResult(String filename)
    {
        s3Output.Upload("/home/ubuntu/darknet/results_parsed/" + filename);
    }

    public void stopClient()
    {
        String idOfThisInstance = EC2MetadataUtils.getInstanceId();
        ec2.StopInstance(idOfThisInstance);
    }

    public void publishInstanceID()
    {
        String idOfThisInstance = EC2MetadataUtils.getInstanceId();
        sqsInstanceQueue.SendMessage(idOfThisInstance, 5);
    }
    public static void main(String[] args)
    {
        try
        {
            EC2Client client = new EC2Client();
            while(true)
            {
                // Get input video filename
                String filename = client.getFilename();

                if (filename == null)
                {
                    Thread.sleep(5000);

                    filename = client.getFilename();

                    if(filename == null)
                    {
                        // Push instanceID to instance queue
                        client.publishInstanceID();
                        // stop self
                        client.stopClient();
                    }

                }
                // Download video from S3
                int r = client.downloadVideo(filename);
                if(r == 0)
                {
                    // Shutdown if no video
                    System.out.println("No video to work with. Shutting down...");
                    // Push instanceID to instance queue
                    client.publishInstanceID();
                    // stop self
                    client.stopClient();
                    return;
                }
                // Run darknet with video as input to darknet
                client.runDarknet(filename);
                // Upload results to S3
                client.uploadResult(filename);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
