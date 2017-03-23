package com.hpe.srl.tasks;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.configuration.ConfigurationMap;
import com.atlassian.bamboo.task.*;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.hpe.srl.beans.Project;
import com.hpe.srl.beans.TestRun;
import com.hpe.srl.beans.TestRunStatus;
import com.hpe.srl.beans.Token;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

/**
 * Created by embons on 21/08/2016.
 */
public class SRLTestTask implements CommonTaskType {

    private URL proxyURL = null;
    private String serverHost = "https://stormrunner-load.saas.hpe.com/";
    private String cookieStr = null;
    private RequestConfig reqConfig = null;
    private String runId = null;

    private ConfigurationMap bambooConfig = null;

    private com.google.gson.Gson gson = new com.google.gson.Gson();
    private HttpClient httpClient = HttpClientBuilder.create().build();


    private void getEnvVar(BuildLogger logger) {
        String proxy = System.getenv("http_proxy");
        String server = System.getenv("SRL_BAMBOO_SERVER_URL");
        logger.addBuildLogEntry(String.format("Get from env http_proxy: %1$s, serverurl: %2$s", proxy, server));

        if (proxy != null) {
            try {
                this.proxyURL = new URL(proxy);
            } catch (MalformedURLException e) {
                logger.addBuildLogEntry(String.format("Get invalid proxy from env http_proxy: %1$s, no proxy will be set.", proxy));
            }
        }

        if (server != null) {
            try {
                URL serverURL = new URL(server);
                this.serverHost = server;
            } catch (MalformedURLException e) {
                logger.addBuildLogEntry(String.format("Get invalid server address from env SRL_BAMBOO_SERVER_URL: %1$s, fall back to default server.", server));
            }
        }
    }

    private void buildReqConfig() {
        if (this.proxyURL != null) {
            HttpHost proxy = new HttpHost(this.proxyURL.getHost(), this.proxyURL.getPort(), this.proxyURL.getProtocol());
            this.reqConfig = RequestConfig.custom().setProxy(proxy).build();
        }
    }

    private String getAPIUrl(String apiName) {
        if (apiName.equals("login")) {
            return String.format("%1$s/v1/login?TENANTID=%2$s",
                    this.serverHost,
                    this.bambooConfig.get("tenantId"));
        }

        if (apiName.equals("runTest")) {
            return String.format("%1$s/v1/projects/%2$s/load-tests/%3$s/runs?TENANTID=%4$s",
                    this.serverHost,
                    this.bambooConfig.get("projectId"),
                    this.bambooConfig.get("testId"),
                    this.bambooConfig.get("tenantId"));
        }

        if (apiName.equals("checkStatus")) {
            return String.format("%1$s/v1/test-runs/%2$s/status?TENANTID=%3$s",
                    this.serverHost,
                    this.runId,
                    this.bambooConfig.get("tenantId"));
        }

        return null;
    }

    private String login()
            throws IOException {
        HttpPost request = new HttpPost(getAPIUrl("login"));
        if (this.reqConfig != null) {
            request.setConfig(this.reqConfig);
        }

        StringEntity params = new StringEntity("{\"user\":\"" + this.bambooConfig.get("userName") + "\",\"password\":\"" + this.bambooConfig.get("password") + "\"} ");
        request.addHeader("content-type", "application/json");
        request.addHeader("Accept", "application/json");
        request.setEntity(params);
        HttpResponse response = this.httpClient.execute(request);
        String json = EntityUtils.toString(response.getEntity(), "UTF-8");
        Token tokenResponse = this.gson.fromJson(json, Token.class);

        if (tokenResponse.getError() != null) {
            return tokenResponse.getError();
        }

        String csrfCookie = "HPSSO_COOKIE_CSRF=" + response.getFirstHeader("HPSSO_COOKIE_CSRF").getValue();
        String tokenCookie = "LWSSO_COOKIE_KEY=" + tokenResponse.getToken();
        this.cookieStr = tokenCookie + ";" + csrfCookie;
        return null;
    }

    private String runTest() throws IOException {
        HttpPost request = new HttpPost(getAPIUrl("runTest"));
        request.addHeader("Accept", "application/json");
        request.addHeader("Cookie", this.cookieStr);
        if (this.reqConfig != null) {
            request.setConfig(this.reqConfig);
        }
        HttpResponse response = this.httpClient.execute(request);

        String json = EntityUtils.toString(response.getEntity(), "UTF-8");

        TestRun testRun = gson.fromJson(json, TestRun.class);

        if (testRun.getMessage() != null && response.getStatusLine().getStatusCode() != 200) {
            return testRun.getMessage();
        }

        this.runId = testRun.getRunId();

        return null;
    }

    private String checkStatus() throws IOException {
        HttpGet request = new HttpGet(getAPIUrl("checkStatus"));
        request.addHeader("Accept", "application/json");
        request.addHeader("Cookie", cookieStr);
        if (this.reqConfig != null) {
            request.setConfig(this.reqConfig);
        }
        HttpResponse response = httpClient.execute(request);
        String json = EntityUtils.toString(response.getEntity(), "UTF-8");
        TestRunStatus testRunStatus = gson.fromJson(json, TestRunStatus.class);
        return testRunStatus.getDetailedStatus();
    }


    public TaskResult execute(CommonTaskContext commonTaskContext) throws TaskException {
        try {
            //get all the parameters entered by the user
            TaskResultBuilder taskResultBuilder = TaskResultBuilder.newBuilder(commonTaskContext);
            this.bambooConfig = commonTaskContext.getConfigurationMap();
            Integer testTimeOut;
            getEnvVar(commonTaskContext.getBuildLogger());
            buildReqConfig();

            try {
                testTimeOut = Integer.valueOf(this.bambooConfig.get("testTimeOut"));
            } catch (NumberFormatException ex) {
                testTimeOut = 60;
            }

            //get the security token
            commonTaskContext.getBuildLogger().addBuildLogEntry("Getting security token from SaaS for the configured user name: " + this.bambooConfig.get("userName") + " and password");
            String loginError = login();
            if (loginError != null) {
                commonTaskContext.getBuildLogger().addErrorLogEntry(loginError);
                return taskResultBuilder.failedWithError().build();
            }

            //run the test
            commonTaskContext.getBuildLogger().addBuildLogEntry("Running test: " + this.bambooConfig.get("testId") + " under the project: " + this.bambooConfig.get("projectId") + " against SRL SaaS API");
            String runError = runTest();

            if (runError != null){
                commonTaskContext.getBuildLogger().addErrorLogEntry(runError);
                return taskResultBuilder.failedWithError().build();
            } else{
                commonTaskContext.getBuildLogger().addBuildLogEntry("test " + this.bambooConfig.get("testId") + " has started");
            }


            if (Boolean.valueOf(this.bambooConfig.get("sync"))) {//check if we want to wait for the test to finish

                long testStartTime = System.currentTimeMillis();
                long testTimeOutInMillis = testTimeOut * 60 * 1000;
                while (true) {
                    String status = checkStatus();
                    switch (TestStatus.valueOf(status)) {
                        case INITIALIZING:
                        case CHECKING_STATUS:
                        case RUNNING:
                        case STOPPING:
                            commonTaskContext.getBuildLogger().addBuildLogEntry("Test " + this.bambooConfig.get("testId") + " is still running. detailed run status: " + status);
                            break;
                        case SYSTEM_ERROR:
                        case HALTED:
                        case STOPPED:
                        case ABORTED:
                            commonTaskContext.getBuildLogger().addErrorLogEntry("Test " + this.bambooConfig.get("testId") + " didn't finish gracefully due to following reason: " + status);
                            return taskResultBuilder.failedWithError().build();
                        case FAILED:
                            commonTaskContext.getBuildLogger().addErrorLogEntry("Test " + this.bambooConfig.get("testId") + " failed");
                            return taskResultBuilder.failed().build();
                        case PASSED:
                            commonTaskContext.getBuildLogger().addBuildLogEntry("Test " + this.bambooConfig.get("testId") + " passed");
                            return taskResultBuilder.success().build();
                        default:

                    }
                    //check if the test has passed its configured timeout
                    if (System.currentTimeMillis() - testStartTime > testTimeOutInMillis) {
                        commonTaskContext.getBuildLogger().addErrorLogEntry("Test " + this.bambooConfig.get("testId") + " has timed out, aborting task");
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
            if (ex.getStackTrace() != null) {
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
        INITIALIZING,
        CHECKING_STATUS,
        RUNNING,
        STOPPING,
        SYSTEM_ERROR,
        HALTED,
        PASSED,
        FAILED,
        STOPPED,
        ABORTED;
    }

    class CollectionAdapter implements JsonDeserializer<Project[]> {

        public Project[] deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            if (jsonElement.toString().equals("[{}]"))
                return new Project[0];
            return jsonDeserializationContext.deserialize(jsonElement, type);
        }
    }
}