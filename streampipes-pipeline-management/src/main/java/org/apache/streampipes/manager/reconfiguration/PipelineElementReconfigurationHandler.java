/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.streampipes.manager.reconfiguration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.streampipes.commons.exceptions.SpRuntimeException;
import org.apache.streampipes.manager.execution.pipeline.PipelineElementReconfigurationExecutor;
import org.apache.streampipes.manager.operations.Operations;
import org.apache.streampipes.model.graph.DataProcessorInvocation;
import org.apache.streampipes.model.pipeline.*;
import org.apache.streampipes.model.staticproperty.FreeTextStaticProperty;
import org.apache.streampipes.model.staticproperty.StaticProperty;
import org.apache.streampipes.storage.api.IPipelineStorage;
import org.apache.streampipes.storage.management.StorageDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PipelineElementReconfigurationHandler {

    private final PipelineOperationStatus pipelineReconfigurationStatus;
    private final Pipeline reconfiguredPipeline;
    private Pipeline storedPipeline;
    private final boolean storeStatus;

    public PipelineElementReconfigurationHandler(Pipeline reconfiguredPipeline, boolean storeStatus) {
        this.pipelineReconfigurationStatus = new PipelineOperationStatus();
        this.reconfiguredPipeline = reconfiguredPipeline;
        this.storedPipeline = getPipelineById(reconfiguredPipeline.getPipelineId());
        this.storeStatus = storeStatus;
    }

    public PipelineOperationStatus handleReconfiguration() {
        reconfigurePipelineElementOrRollback();
        return verifyPipelineReconfigurationStatus(pipelineReconfigurationStatus,
                "Successfully reconfigured Pipeline Elements in Pipeline " + reconfiguredPipeline.getName(),
                "Could not reconfigure all Pipeline Elements in Pipeline " + reconfiguredPipeline.getName());
    }

    private void reconfigurePipelineElementOrRollback() {
        List<PipelineElementReconfigurationEntity> reconfiguredEntityList = comparePipelinesAndGetReconfiguration();

        reconfiguredEntityList.forEach(entity -> {
            PipelineOperationStatus entityStatus = reconfigurePipelineElement(entity);

            entityStatus.getElementStatus().forEach(pipelineReconfigurationStatus::addPipelineElementStatus);

            // TODO needed ?
            if (entityStatus.isSuccess()) {
                try {
                    storedPipeline = deepCopyPipeline(reconfiguredPipeline);
                } catch (JsonProcessingException e) {
                    throw new SpRuntimeException("Could not deep copy pipeline for reconfiguration: " + e.getMessage(),
                            e);
                }
            } else {
                //TODO: what to do when not successful?
            }
        });

        if (storeStatus) {
            Operations.overwritePipeline(reconfiguredPipeline);
        }
    }

    private PipelineOperationStatus reconfigurePipelineElement(PipelineElementReconfigurationEntity entity) {
        return new PipelineElementReconfigurationExecutor(reconfiguredPipeline, entity).reconfigurePipelineElement();
    }

    private List<PipelineElementReconfigurationEntity> comparePipelinesAndGetReconfiguration() {
        List<PipelineElementReconfigurationEntity> delta = new ArrayList<>();

        List<DataProcessorInvocation> reconfiguredGraphs = filterReconfigurableFsp(reconfiguredPipeline);
        List<DataProcessorInvocation> currentGraphs = filterReconfigurableFsp(storedPipeline);

        reconfiguredGraphs.forEach(reconfiguredProcessor -> currentGraphs.forEach(storedProcessor -> {
            if (matchingElementIds(reconfiguredProcessor, storedProcessor)) {
                List<StaticProperty> list = new ArrayList<>();
                getReconfigurableFsp(reconfiguredProcessor).forEach(reconfiguredFsp ->
                        getReconfigurableFsp(storedProcessor).forEach(storedFsp -> {
                    if (compareForChanges(reconfiguredFsp, storedFsp)) {
                        list.add(reconfiguredFsp);
                    }
                }));
                PipelineElementReconfigurationEntity entity = reconfigurationEntity(reconfiguredProcessor, list);
                if (list.size() > 0 && !exists(delta, entity)) {
                    delta.add(entity);
                }
            }
        }));

        return delta;
    }

    private boolean exists(List<PipelineElementReconfigurationEntity> delta,
                           PipelineElementReconfigurationEntity entity) {
        return delta.stream()
                .anyMatch(e -> e.getDeploymentRunningInstanceId().equals(entity.getDeploymentRunningInstanceId()));
    }

    private PipelineElementReconfigurationEntity reconfigurationEntity(DataProcessorInvocation graph,
                                                                       List<StaticProperty> adaptedStaticProperty) {
        PipelineElementReconfigurationEntity entity = new PipelineElementReconfigurationEntity();
        entity.setDeploymentRunningInstanceId(graph.getDeploymentRunningInstanceId());
        entity.setPipelineElementName(graph.getName());
        entity.setDeploymentTargetNodeId(graph.getDeploymentTargetNodeId());
        entity.setDeploymentTargetNodeHostname(graph.getDeploymentTargetNodeHostname());
        entity.setDeploymentTargetNodePort(graph.getDeploymentTargetNodePort());
        entity.setReconfiguredStaticProperties(adaptedStaticProperty);
        return entity;
    }

    private boolean compareForChanges(FreeTextStaticProperty one, FreeTextStaticProperty two) {
        return one.getInternalName().equals(two.getInternalName()) && !one.getValue().equals(two.getValue());
    }

    private List<FreeTextStaticProperty> getReconfigurableFsp(DataProcessorInvocation graph) {
        return graph.getStaticProperties().stream()
                .filter(FreeTextStaticProperty.class::isInstance)
                .map(FreeTextStaticProperty.class::cast)
                .filter(FreeTextStaticProperty::isReconfigurable)
                .collect(Collectors.toList());
    }

    private boolean matchingElementIds(DataProcessorInvocation one, DataProcessorInvocation two) {
        return one.getElementId().equals(two.getElementId());
    }

    private List<DataProcessorInvocation> filterReconfigurableFsp(Pipeline pipeline) {
        List<DataProcessorInvocation> filtered = new ArrayList<>();
        pipeline.getSepas().forEach(processor -> {
            List<StaticProperty> fsp = new ArrayList<>();
            processor.getStaticProperties().forEach(sp -> {
                if (sp instanceof FreeTextStaticProperty && ((FreeTextStaticProperty) sp).isReconfigurable()) {
                    fsp.add(sp);
                }
            });
            processor.setStaticProperties(fsp);
            filtered.add(processor);
        });

        return filtered;
    }



    // Helpers

    private PipelineOperationStatus verifyPipelineReconfigurationStatus(PipelineOperationStatus status,
                                                                        String successMessage,
                                                                        String errorMessage) {
        status.setSuccess(status.getElementStatus().stream().allMatch(PipelineElementStatus::isSuccess));
        if (status.isSuccess()) {
            status.setTitle(successMessage);
        } else {
            status.setTitle(errorMessage);
        }
        return status;
    }

    private Pipeline getPipelineById(String pipelineId) {
        return getPipelineStorageApi().getPipeline(pipelineId);
    }

    private IPipelineStorage getPipelineStorageApi() {
        return StorageDispatcher.INSTANCE.getNoSqlStore().getPipelineStorageAPI();
    }

    public static Pipeline deepCopyPipeline(Pipeline object) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(objectMapper.writeValueAsString(object), Pipeline.class);
    }

}
