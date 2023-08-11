/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.publish.internal.mapping;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.publish.internal.component.ResolutionBackedVariant;
import org.gradle.api.publish.internal.versionmapping.VariantVersionMappingStrategyInternal;
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal;

import javax.inject.Inject;

/**
 * Default implementation of {@link VariantDependencyResolverFactory} that
 * resolves dependencies using version mapping.
 */
public class DefaultVariantDependencyResolverFactory implements VariantDependencyResolverFactory {

    // TODO: We should eventually make this false -- therefore implementing legacy version
    //       mapping with the new dependency mapping implementation.
    private static final boolean USE_LEGACY_VERSION_MAPPING = true;

    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final VersionMappingStrategyInternal versionMappingStrategy;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final AttributesSchemaInternal consumerSchema;
    private final ImmutableAttributesFactory attributesFactory;

    @Inject
    public DefaultVariantDependencyResolverFactory(
        ProjectDependencyPublicationResolver projectDependencyResolver,
        VersionMappingStrategyInternal versionMappingStrategy,
        ImmutableModuleIdentifierFactory moduleIdentifierFacatory,
        AttributesSchemaInternal consumerSchema,
        ImmutableAttributesFactory attributesFactory
    ) {
        this.projectDependencyResolver = projectDependencyResolver;
        this.versionMappingStrategy = versionMappingStrategy;
        this.moduleIdentifierFactory = moduleIdentifierFacatory;
        this.consumerSchema = consumerSchema;
        this.attributesFactory = attributesFactory;
    }

    @Override
    public VariantDependencyResolver createResolver(
        SoftwareComponentVariant variant,
        DeclaredVersionTransformer declaredVersionTransformer
    ) {
        Configuration configuration = null;
        if (variant instanceof ResolutionBackedVariant) {
            ResolutionBackedVariant resolutionBackedVariant = (ResolutionBackedVariant) variant;
            configuration = resolutionBackedVariant.getResolutionConfiguration();

            boolean useResolvedCoordinates = resolutionBackedVariant.getPublishResolvedCoordinates();
            if (useResolvedCoordinates && configuration == null) {
                throw new InvalidUserCodeException("Cannot enable dependency mapping without configuring a resolution configuration.");
            } else if (useResolvedCoordinates) {
                return new ResolutionBackedVariantDependencyResolver(
                    declaredVersionTransformer,
                    projectDependencyResolver,
                    moduleIdentifierFactory,
                    configuration,
                    true,
                    consumerSchema,
                    attributesFactory
                );
            }
        }

        ImmutableAttributes attributes = ((AttributeContainerInternal) variant.getAttributes()).asImmutable();
        VariantVersionMappingStrategyInternal versionMapping = versionMappingStrategy.findStrategyForVariant(attributes);

        // Fallback to version mapping if dependency mapping is not enabled
        if (versionMapping.isEnabled()) {
            if (versionMapping.getUserResolutionConfiguration() != null) {
                configuration = versionMapping.getUserResolutionConfiguration();
            } else if (versionMapping.getDefaultResolutionConfiguration() != null && configuration == null) {
                // The configuration set on the variant is almost always more correct than the
                // default version mapping configuration, which is currently set project-wide
                // by the Java plugin. For this reason, we only use the version mapping default
                // if the dependency mapping configuration is not set.
                configuration = versionMapping.getDefaultResolutionConfiguration();
            }
        } else {
            configuration = null;
        }

        if (configuration != null && !USE_LEGACY_VERSION_MAPPING) {
            return new ResolutionBackedVariantDependencyResolver(
                declaredVersionTransformer,
                projectDependencyResolver,
                moduleIdentifierFactory,
                configuration,
                false,
                consumerSchema,
                attributesFactory
            );
        } else {
            return new VersionMappingVariantDependencyResolver(
                projectDependencyResolver,
                configuration,
                declaredVersionTransformer
            );
        }
    }
}
