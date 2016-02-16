/*
 *
 *  Copyright 2015 Netflix, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.netflix.genie.core.jobs.workflow.impl;

import com.netflix.genie.common.dto.Application;
import com.netflix.genie.common.exceptions.GenieBadRequestException;
import com.netflix.genie.common.exceptions.GenieException;
import com.netflix.genie.common.exceptions.GeniePreconditionException;
import com.netflix.genie.core.jobs.JobExecutionEnvironment;
import com.netflix.genie.core.jobs.workflow.Context;
import com.netflix.genie.core.jobs.workflow.WorkflowTask;
import com.netflix.genie.core.services.GenieFileTransferService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotNull;
import java.io.Writer;

/**
 * Implementation of the workflow task for handling Applications that a job needs.
 *
 * @author amsharma
 * @since 3.0.0
 */
@Slf4j
public class ApplicationTask extends GenieBaseTask implements WorkflowTask {

    private static final String APPLICATION_PATH_VAR = "applications";
    private GenieFileTransferService fts;

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeTask(
        @NotNull
        final Context context
    ) throws GenieException {
        log.info("Execution Application Task in the workflow.");

        final JobExecutionEnvironment jobExecEnv =
            (JobExecutionEnvironment) context.getAttribute(JOB_EXECUTION_ENV_KEY);

        if (jobExecEnv == null) {
            throw new GeniePreconditionException("Cannot run application task as jobExecutionEnvironment is null");
        }

        this.fts = (GenieFileTransferService) context.getAttribute(FILE_TRANSFER_SERVICE_KEY);

        final String jobLauncherScriptPath = jobExecEnv.getJobWorkingDir() + "/" + GENIE_JOB_LAUNCHER_SCRIPT;
        final Writer writer = getWriter(jobLauncherScriptPath);

        for (Application application : jobExecEnv.getApplications()) {
            createDirectory(jobExecEnv.getJobWorkingDir() + "/applications/" + application.getId());

            final String applicationSetupFile = application.getSetupFile();

            // Get the setup file if specified
            if (applicationSetupFile != null && StringUtils.isNotBlank(applicationSetupFile)) {
                final String localPath = fetchLocalFile(
                    jobExecEnv.getJobWorkingDir(),
                    application.getId(),
                    applicationSetupFile,
                    SETUP_FILE_PATH_PREFIX
                );

                fts.getFile(applicationSetupFile, localPath);
                appendToWriter(writer, "source " + localPath + ";");
            }

            // Iterate over and get all dependencies
            for (final String dependencyFile: application.getDependencies()) {
                fetchLocalFile(
                    jobExecEnv.getJobWorkingDir(),
                    application.getId(),
                    applicationSetupFile,
                    DEPENDENCY_FILE_PATH_PREFIX
                );
            }

            // Iterate over and get all configuration files
            for (final String configFile: application.getConfigs()) {
                fetchLocalFile(
                    jobExecEnv.getJobWorkingDir(),
                    application.getId(),
                    applicationSetupFile,
                    CONFIG_FILE_PATH_PREFIX
                );
            }
        }
        closeWriter(writer);
    }

    /**
     * Helper Function to fetch file to local dir.
     *
     * @param dir The directory where to copy the file
     * @param id The id to be appended to the destination path
     * @param filePath Source file path
     * @param fileType Type of file like setup, config or dependency
     * @return Local file path constructed where the file is copied to
     *
     * @throws GenieException If there is any problem
     */
    private String fetchLocalFile(
        final String dir,
        final String id,
        final String filePath,
        final String fileType
    ) throws GenieException {
        if (filePath != null && StringUtils.isNotBlank(filePath)) {
            final String fileName = getFileNameFromPath(filePath);
            final String localPath = new StringBuilder()
                .append(dir)
                .append(FILE_PATH_DELIMITER)
                .append(APPLICATION_PATH_VAR)
                .append(FILE_PATH_DELIMITER)
                .append(id)
                .append(FILE_PATH_DELIMITER)
                .append(fileType)
                .append(FILE_PATH_DELIMITER)
                .append(fileName)
                .toString();

            this.fts.getFile(filePath, localPath);
            return localPath;
        } else {
            throw new GenieBadRequestException("Invalid file path");
        }
    }
}
