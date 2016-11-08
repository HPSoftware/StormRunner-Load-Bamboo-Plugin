package com.hpe.srl.tasks;

import com.atlassian.bamboo.task.*;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.hpe.srl.beans.Project;
import com.hpe.srl.beans.Token;
import org.apache.http.client.methods.HttpGet;
import com.hpe.srl.beans.TestRun;
import com.hpe.srl.beans.TestRunStatus;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * Created by embons on 21/08/2016.
 */
public class SRLTestTask implements CommonTaskType {


    public TaskResult execute(CommonTaskContext commonTaskContext) throws TaskException {
        HttpClient httpClient = HttpClientBuilder.create().build(); //Use this instead

        try {
            TaskResultBuilder taskResultBuilder = TaskResultBuilder.newBuilder(commonTaskContext);
            //get all the parameters entered by the user
            String projectId = commonTaskContext.getConfigurationMap().get("projectId");
            String testId = commonTaskContext.getConfigurationMap().get("testId");
            String tenantId = commonTaskContext.getConfigurationMap().get("tenantId");
            Boolean sync = Boolean.valueOf(commonTaskContext.getConfigurationMap().get("sync"));
            String userName = commonTaskContext.getConfigurationMap().get("userName");
            String password = commonTaskContext.getConfigurationMap().get("password");
            Integer testTimeOut;
            try {
                testTimeOut = Integer.valueOf(commonTaskContext.getConfigurationMap().get("testTimeOut"));
            } catch(NumberFormatException ex) {
                testTimeOut = 60;
            }

            //get the security token
            commonTaskContext.getBuildLogger().addBuildLogEntry("Getting security token from SaaS for the configured user name: "+ userName+" and password");
            HttpPost request = new HttpPost("https://stormrunner-load.saas.hpe.com/v1/login?TENANTID=" + tenantId);
            StringEntity params = new StringEntity("{\"user\":\"" + userName + "\",\"password\":\"" + password + "\"} ");
            request.addHeader("content-type", "application/json");
            request.addHeader("Accept", "application/json");
            request.setEntity(params);
            HttpResponse response = httpClient.execute(request);
            String json = EntityUtils.toString(response.getEntity(), "UTF-8");

            com.google.gson.Gson gson = new com.google.gson.Gson();
            Token tokenResponse = gson.fromJson(json, Token.class);

            if(tokenResponse.getError() != null) {
                commonTaskContext.getBuildLogger().addErrorLogEntry(tokenResponse.getError());
                return taskResultBuilder.failedWithError().build();
            }


            //run the test
            commonTaskContext.getBuildLogger().addBuildLogEntry("Running test: " + testId + " under the project: " + projectId + " against SRL SaaS API");
            request = new HttpPost("https://stormrunner-load.saas.hpe.com/v1/projects/"+projectId+"/load-tests/"+testId+"/runs?TENANTID=" + tenantId);
            request.addHeader("Accept", "application/json");
            request.addHeader("Cookie:", "LWSSO_COOKIE_KEY=" + tokenResponse.getToken());
            response = httpClient.execute(request);

            json = EntityUtils.toString(response.getEntity(), "UTF-8");

            TestRun testRun = gson.fromJson(json, TestRun.class);

            if(testRun.getMessage() != null && response.getStatusLine().getStatusCode() != 200){
                commonTaskContext.getBuildLogger().addErrorLogEntry(testRun.getMessage());
                return taskResultBuilder.failedWithError().build();
            }else{
                commonTaskContext.getBuildLogger().addBuildLogEntry("test " + testId + " has started");
            }



            if(sync){//check if we want to wait for the test to finish
                String runId = testRun.getRunId();
                //prepare the check run status request
                commonTaskContext.getBuildLogger().addBuildLogEntry("Checking run status for the test using run id: "+ runId);
                HttpGet getRequest = new HttpGet("https://stormrunner-load.saas.hpe.com/v1/test-runs/" + runId + "/status?TENANTID=" + tenantId);
                getRequest.addHeader("Accept", "application/json");
                getRequest.addHeader("Cookie:", "LWSSO_COOKIE_KEY=" + tokenResponse.getToken());
                long testStartTime = System.currentTimeMillis();
                long testTimeOutInMillis = testTimeOut * 60 * 1000;
                while(true) {
                    response = httpClient.execute(getRequest);
                    json = EntityUtils.toString(response.getEntity(), "UTF-8");
                    TestRunStatus testRunStatus = gson.fromJson(json, TestRunStatus.class);
                    switch(TestStatus.valueOf(testRunStatus.getDetailedStatus())){
                        case INITIALIZING:
                        case CHECKING_STATUS:
                        case RUNNING:
                        case STOPPING:
                            commonTaskContext.getBuildLogger().addBuildLogEntry("Test " + testId + " is still running. detailed run status: " + testRunStatus.getDetailedStatus());
                            break;
                        case SYSTEM_ERROR:
                        case HALTED:
                        case STOPPED:
                            commonTaskContext.getBuildLogger().addErrorLogEntry("Test " + testId + " didn't finish gracefully due to following reason: " + testRunStatus.getDetailedStatus());
                            return taskResultBuilder.failedWithError().build();
                        case FAILED:
                            commonTaskContext.getBuildLogger().addErrorLogEntry("Test " + testId + " failed");
                            return taskResultBuilder.failed().build();
                        case PASSED:
                            commonTaskContext.getBuildLogger().addBuildLogEntry("Test "+ testId + " passed");
                            return taskResultBuilder.success().build();
                        default:

                    }
                    //check if the test has passed its configured timeout
                    if(System.currentTimeMillis() - testStartTime > testTimeOutInMillis) {
                        commonTaskContext.getBuildLogger().addErrorLogEntry("Test " + testId + " has timed out, aborting task");
                        return taskResultBuilder.failedWithError().build();
                    }
                    Thread.sleep(30000);
                }
            }
            commonTaskContext.getBuildLogger().addBuildLogEntry("Test is configured to run asynchronously, returning to job process");
            return taskResultBuilder.success().build();
        } catch (Exception ex) {
            commonTaskContext.getBuildLogger().addBuildLogEntry("Exception");
            commonTaskContext.getBuildLogger().addBuildLogEntry(ex.toString());
            if(ex.getStackTrace() != null) {
                commonTaskContext.getBuildLogger().addBuildLogEntry(Arrays.toString(ex.getStackTrace()));
            }
        } finally {
            httpClient.getConnectionManager().shutdown(); //Deprecated
        }
        commonTaskContext.getBuildLogger().addErrorLogEntry("Unexpected error occurred. Please open a support ticket and attach log data at: https://saas.hpe.com/.");
        TaskResultBuilder taskResultBuilder = TaskResultBuilder.newBuilder(commonTaskContext);
        return taskResultBuilder.failedWithError().build();
    }

    enum TestStatus {
        INITIALIZING, CHECKING_STATUS, RUNNING, STOPPING, SYSTEM_ERROR, HALTED, PASSED, FAILED, STOPPED;
    }

    class CollectionAdapter implements JsonDeserializer<Project[]> {

        public Project[] deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            if(jsonElement.toString().equals("[{}]"))
                return new Project[0];
            return jsonDeserializationContext.deserialize(jsonElement, type);
        }
    }
}