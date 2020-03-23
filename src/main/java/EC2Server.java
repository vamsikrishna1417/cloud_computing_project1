import java.util.ArrayList;
import java.util.List;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceNetworkInterfaceSpecification;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TagSpecification;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.InstanceStateName;
import com.amazonaws.services.ec2.model.InstanceStatus;


import java.util.List;

public class EC2Server {
	static {
        // Your accesskey and secretkey
        AWS_CREDENTIALS = new BasicAWSCredentials(
                "ASIA3HIKMVVQNLLAURHS",
                "JSuu7yX706QsioLEhGUzHpDBn1/0IsQEemdr6ecU"
        );
    }
    // private List<AmazonEC2> ec2clientList;
    private AmazonEC2 ec2Client;
    private String ami_id;
    private String key_pair;
    private List<String> instanceIDs;
    public EC2Server()
    {
    	ec2Client = createEC2Client();
    	ami_id = "ami-0903fd482d7208724";
        key_pair = "ccproj";
    	// int numofInstances = CreateInstance(ec2Client, 3, -1, ami_id, key_pair);
        // Create 2 instances
        // for(int i=1;i<3;i++){
        // 	String instanceId = CreateInstance(ec2Client, i);
        // 	ec2clientList.add(ec2Client);
        // 	instanceIDs.add(instanceId);
        // }
        
        

    }

    public int startInstances(int count, int minCount){
    	return CreateInstance(ec2Client, count, minCount);
    }

    public static void main(String[] args)
    {
        Regions region = Regions.US_EAST_1;
        List<String> messagelist;
        try
        {
            SqsHandler sqhandle = new SqsHandler("input.fifo");
            S3Handler s3handle = new S3Handler(region, "ccfoebucket");

            messagelist = sqhandle.ReceiveMessage();
            System.out.println(messagelist.toString());

            // For each message received start instance
            for(String m: messagelist)
            {

            }

        }
        catch (AmazonServiceException e)
        {
            e.printStackTrace();
        }
        catch (SdkClientException e)
        {
            e.printStackTrace();
        }
    }

    public AmazonEC2 createEC2Client(){
    	AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard()
    					.withCredentials(new AWSStaticCredentialsProvider(AWS_CREDENTIALS))
    					.withRegion(Regions.US_EAST_1)
    					.build();
    	return ec2;
    }

    public int CreateInstance(AmazonEC2 ec2Client, int maxNumberInstance, int count, String imageId, String key_pair) throws AmazonServiceException, SdkClientException
    {
    	int minInstance = maxNumberInstance -1;
    	int maxInstance = maxNumberInstance;
    	if(minInstance == 0)
    		minInstance = 1;
    	List<String> securityIds = new ArrayList<>();
    	securityIds.add("*******");
    	List<TagSpecification> tagSpecsList = new ArrayList<>();
    	TagSpecification tagSpec = new TagSpecification();
    	List<Tag> tagList = new ArrayList<>();
    	Tag tag = new Tag();
    	tag.setKey("Name");
    	tag.setValue("EC2 - App");
    	tagList.add(tag);
    	tagSpec.setResourceType("instance");
    	tagSpec.setTags(tagList);
    	tagSpecsList.add(tagSpec);

        RunInstancesRequest run_request = new RunInstancesRequest()
                .withImageId(imageId)
                .withInstanceType(InstanceType.T2Micro)
                .withMaxCount(maxInstance)
                .withMinCount(minInstance)
                .withKeyName(key_pair);
        RunInstancesResult run_response = null;
        try{
        	run_response = ec2Client.runInstances(run_request);
        } catch(AmazonEC2Exception amzec2Exp){
        	return count;
        } catch(Exception e){
        	return count;
        }
        // RunInstancesResult run_response = ec2Client.runInstances(run_request);
        // String instanceId = instanceResult.getReservation().getInstances().get(0).getInstanceId();
        
        //I'll associate a tag for the resource, so that we can identify resource with tag name later
        // CreateTagsRequest createTagsRequest = new CreateTagsRequest()
        //         .withResources(instanceId)
        //         .withTags(new Tag("ec2client"+number, "ec2client"+number));
        // ec2Client.createTags(createTagsRequest);
        return count;
    }

    public void startInstance(String instanceId, AmazonEC2 ec2Client){
    	StartInstancesRequest startRequest = new StartInstancesRequest().withInstanceIds(instanceId);
    	ec2Client.startInstances(startRequest);
    }

    public void stopInstance(String instanceId, AmazonEC2 ec2Client){
    	StopInstancesRequest stopRequest = new StopInstancesRequest().withInstanceIds(instanceId);
    	ec2Client.stopInstances(stopRequest);
    }

    public void terminateInstance(String instanceId, AmazonEC2 ec2Client){
    	TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest().withInstanceIds(instanceId);
    	ec2Client.terminateInstance(terminateRequest);
    }

    public DescribeInstanceStatusResult describeInstanceStatus(DescribeInstanceStatusRequest instanceRequest, AmazonEC2 ec2Client){
    	return ec2Client.describeInstanceStatus(instanceRequest);
    }

    public int getNumberOfInstances(){
    	DescribeInstanceStatusRequest describeRequest = new DescribeInstanceStatusRequest();
    	describeRequest.setIncludeAllInstances(true);
    	DescribeInstanceStatusResult describeInstances = describeInstanceStatus(describeRequest);
    	List<InstanceStatus> instanceStatusList = describeInstances.getInstanceState();
    	int liveInstancesCount=0;
    	for(InstanceStatus instanceStatus: instanceStatusList){
    		InstanceStatus instanceState = instanceStatus.getInstanceState();
    		if(InstanceStateName.Running.toString().equals(instanceState.getName())){
    			liveInstancesCount++;
    		}
    	}
    	return liveInstancesCount;
    }
}