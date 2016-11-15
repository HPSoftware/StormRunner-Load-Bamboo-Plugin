package com.hpe.srl.taskConfigurator;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.util.concurrent.NotNull;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * Created by embons on 01/09/2016.
 */
public class SRLTaskConfigurator extends AbstractTaskConfigurator {

    private static final Set<String> FIELDS_TO_COPY = ImmutableSet.<String>builder().add(new String[]{"projectId", "testId", "sync", "tenantId", "userName", "password", "testTimeOut"}).build();
    public Map<String, String> generateTaskConfigMap(@NotNull final ActionParametersMap params, @Nullable final TaskDefinition previousTaskDefinition)
    {
        final Map<String, String> config = super.generateTaskConfigMap(params, previousTaskDefinition);
//        this.taskConfiguratorHelper.populateTaskConfigMapWithActionParameters(config, params, FIELDS_TO_COPY);

        config.put("tenantId", params.getString("tenantId"));
        config.put("projectId", params.getString("projectId"));
        config.put("testId", params.getString("testId"));
        config.put("sync", String.valueOf(params.getBoolean("sync")));
        config.put("testTimeOut", params.getString("testTimeOut"));
        config.put("userName", params.getString("userName"));
        config.put("password", params.getString("password"));

        return config;
    }

    public void validate(@NotNull final ActionParametersMap params, @NotNull final ErrorCollection errorCollection)
    {
        super.validate(params, errorCollection);

        final String projectIdValue = params.getString("projectId");
        if (StringUtils.isEmpty(projectIdValue)){
            errorCollection.addError("projectId", "You must enter project id");
        } else if(!StringUtils.isNumeric(projectIdValue)) {
            errorCollection.addError("projectId", "Project id must be a number");
        } else if (StringUtils.length(projectIdValue) > 20) {
            errorCollection.addError("projectId", "Project id variable cannot extend 20 characters");
        }

        final String testIdValue = params.getString("testId");
        if (StringUtils.isEmpty(testIdValue)){
            errorCollection.addError("testId", "You must enter test id");
        } else if(!StringUtils.isNumeric(testIdValue)) {
            errorCollection.addError("testId", "Test id must be a number");
        } else if (StringUtils.length(testIdValue) > 20) {
            errorCollection.addError("testId", "Test id variable cannot extend 20 characters");
        }


        final String tenantId = params.getString("tenantId");
        if (StringUtils.isEmpty(tenantId)){
            errorCollection.addError("tenantId", "You must enter tenant id");
        } else if(!StringUtils.isNumeric(tenantId)) {
            errorCollection.addError("tenantId", "Tenant id must be a number");
        } else if (StringUtils.length(tenantId) > 20) {
            errorCollection.addError("tenantId", "Tenant id variable cannot extend 20 characters");
        }

        final String userName = params.getString("userName");
        if (StringUtils.isEmpty(userName)){
            errorCollection.addError("userName", "You must enter user name");
        } else if (StringUtils.length(projectIdValue) > 20) {
            errorCollection.addError("userName", "User name variable cannot extend 20 characters");
        }

        final String password = params.getString("password");
        if (StringUtils.isEmpty(password)){
            errorCollection.addError("password", "You must enter password");
        } else if (StringUtils.length(projectIdValue) > 20) {
            errorCollection.addError("password", "Password variable cannot extend 20 characters");
        }

        final String testTimeOut = params.getString("testTimeOut");
        if (!StringUtils.isEmpty(password) && !StringUtils.isNumeric(testTimeOut)){
            errorCollection.addError("testTimeOut", "Test time out must be a number");
        } else if (StringUtils.length(projectIdValue) > 20) {
            errorCollection.addError("testTimeOut", "Test time out variable cannot extend 20 characters");
        }
    }

    public void populateContextForEdit(@NotNull Map<String, Object> context, @NotNull TaskDefinition taskDefinition)
    {
        super.populateContextForEdit(context, taskDefinition);

        context.put("tenantId", taskDefinition.getConfiguration().get("tenantId"));
        context.put("projectId", taskDefinition.getConfiguration().get("projectId"));
        context.put("testId", taskDefinition.getConfiguration().get("testId"));
        context.put("sync", String.valueOf(taskDefinition.getConfiguration().get("sync")));
        context.put("testTimeOut", taskDefinition.getConfiguration().get("testTimeOut"));
        context.put("userName", taskDefinition.getConfiguration().get("userName"));
        context.put("password", taskDefinition.getConfiguration().get("password"));

    }
}
