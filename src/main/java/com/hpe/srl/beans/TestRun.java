package com.hpe.srl.beans;

/**
 * Created by embons on 20/09/2016.
 */
public class TestRun {

    private String runId;
    private String status_code;
    private ExtraData extra_data;
    private String message;

    public String getRunId() {
        return runId;
    }

    public String getStatus_code() {
        return status_code;
    }

    public ExtraData getExtraData() {
        return extra_data;
    }

    public String getMessage() {
        return message;
    }
}
