/*
 * Copyright 2007-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ExcludeRuleConverter;
import org.gradle.util.WrapUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public abstract class AbstractDependencyDescriptorFactoryInternal implements DependencyDescriptorFactoryInternal {
    private ExcludeRuleConverter excludeRuleConverter;

    public AbstractDependencyDescriptorFactoryInternal(ExcludeRuleConverter excludeRuleConverter) {
        this.excludeRuleConverter = excludeRuleConverter;
    }

    public void addDependencyDescriptor(String configuration, DefaultModuleDescriptor moduleDescriptor, ModuleDependency dependency) {
        ModuleRevisionId moduleRevisionId = createModuleRevisionId(dependency);
        DependencyDescriptor newDescriptor = createDependencyDescriptor(dependency, configuration, moduleDescriptor, moduleRevisionId);

        DefaultDependencyDescriptor matchingDependencyDescriptor = findMatchingDescriptorForSameConfiguration(moduleDescriptor, newDescriptor);
        if (matchingDependencyDescriptor != null) {
            mergeDescriptors(configuration, matchingDependencyDescriptor, newDescriptor, dependency);
            return;
        }

        moduleDescriptor.addDependency(newDescriptor);
    }

    protected abstract DependencyDescriptor createDependencyDescriptor(ModuleDependency dependency, String configuration,
                                                            ModuleDescriptor moduleDescriptor, ModuleRevisionId moduleRevisionId);

    private DefaultDependencyDescriptor findMatchingDescriptorForSameConfiguration(DefaultModuleDescriptor moduleDescriptor, DependencyDescriptor targetDescriptor) {
        for (DependencyDescriptor dependencyDescriptor : moduleDescriptor.getDependencies()) {
            if (dependencyDescriptor.getDependencyRevisionId().equals(targetDescriptor.getDependencyRevisionId())
                    && Arrays.equals(dependencyDescriptor.getModuleConfigurations(), targetDescriptor.getModuleConfigurations())) {
                return (DefaultDependencyDescriptor) dependencyDescriptor;
            }
        }
        return null;
    }

    private void mergeDescriptors(String masterConfiguration, DefaultDependencyDescriptor matchingDependencyDescriptor, DependencyDescriptor newDescriptor, ModuleDependency newDependency) {
        // TODO Merge transitive, excludeRules and force flags

        // Merge dependency configurations
        if (newDependency.getConfiguration() != null) {
            matchingDependencyDescriptor.addDependencyConfiguration(masterConfiguration, newDependency.getConfiguration());
        }

        // Add 'default' artifact if one configuration has no defined artifacts and the other has defined artifacts - that's the only way we can combine them
        if (matchingDependencyDescriptor.getAllDependencyArtifacts().length == 0 ^ newDescriptor.getAllDependencyArtifacts().length == 0) {
            matchingDependencyDescriptor.addDependencyArtifact(masterConfiguration, createDefaultArtifact(matchingDependencyDescriptor));
        }

        // Copy across all defined artifacts
        for (DependencyArtifactDescriptor artifactDescriptor : newDescriptor.getAllDependencyArtifacts()) {
            matchingDependencyDescriptor.addDependencyArtifact(masterConfiguration, artifactDescriptor);
        }
    }

    private DefaultDependencyArtifactDescriptor createDefaultArtifact(DefaultDependencyDescriptor dependencyDescriptor) {
        return new DefaultDependencyArtifactDescriptor(dependencyDescriptor, dependencyDescriptor.getDependencyId().getName(),
                DependencyArtifact.DEFAULT_TYPE, DependencyArtifact.DEFAULT_TYPE, null, null);
    }

    protected void addExcludesArtifactsAndDependencies(String configuration, ModuleDependency dependency,
                                                     DefaultDependencyDescriptor dependencyDescriptor) {
        addArtifacts(configuration, dependency.getArtifacts(), dependencyDescriptor);
        addExcludes(configuration, dependency.getExcludeRules(), dependencyDescriptor);
        addDependencyConfiguration(configuration, dependency, dependencyDescriptor);
    }

    private void addArtifacts(String configuration, Set<DependencyArtifact> artifacts,
                              DefaultDependencyDescriptor dependencyDescriptor) {
        for (DependencyArtifact artifact : artifacts) {
            DefaultDependencyArtifactDescriptor artifactDescriptor;
            try {
                artifactDescriptor = new DefaultDependencyArtifactDescriptor(dependencyDescriptor, artifact.getName(),
                        artifact.getType(),
                        artifact.getExtension() != null ? artifact.getExtension() : artifact.getType(),
                        artifact.getUrl() != null ? new URL(artifact.getUrl()) : null,
                        artifact.getClassifier() != null ? WrapUtil.toMap(Dependency.CLASSIFIER,
                                artifact.getClassifier()) : null);
            } catch (MalformedURLException e) {
                throw new InvalidUserDataException("URL for artifact can't be parsed: " + artifact.getUrl(), e);
            }
            dependencyDescriptor.addDependencyArtifact(configuration, artifactDescriptor);
        }
    }

    private void addDependencyConfiguration(String configuration, ModuleDependency dependency,
                                            DefaultDependencyDescriptor dependencyDescriptor) {
        dependencyDescriptor.addDependencyConfiguration(configuration, dependency.getConfiguration());
    }

    private void addExcludes(String configuration, Set<ExcludeRule> excludeRules,
                             DefaultDependencyDescriptor dependencyDescriptor) {
        for (ExcludeRule excludeRule : excludeRules) {
            dependencyDescriptor.addExcludeRule(configuration, excludeRuleConverter.createExcludeRule(configuration,
                    excludeRule));
        }
    }

    public ExcludeRuleConverter getExcludeRuleConverter() {
        return excludeRuleConverter;
    }

    public void setExcludeRuleConverter(ExcludeRuleConverter excludeRuleConverter) {
        this.excludeRuleConverter = excludeRuleConverter;
    }

}