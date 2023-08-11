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

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.publish.internal.validation.VariantWarningCollector;
import org.gradle.internal.component.local.model.ProjectComponentSelectorInternal;
import org.gradle.util.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class ResolutionBackedVariantDependencyResolver implements VariantDependencyResolver {

    private final VariantDependencyResolverFactory.DeclaredVersionTransformer declaredVersionTransformer;
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final Configuration resolutionConfiguration;
    private final AttributesSchemaInternal consumerSchema;
    private final ImmutableAttributesFactory attributesFactory;

    /**
     * If true, if the variant coordinates are different from the component coordinates, those
     * coordinates are returned. This is the desired behavior for Maven and Ivy publishing. However,
     * the legacy "version mapping" functionality and GMM publishing wants the resolved component coordinates.
     */
    private final boolean resolveVariantCoordinates;

    private ResolvedMappings mappings;
    private Map<ModuleIdentifier, String> resolvedComponentVersions;

    public ResolutionBackedVariantDependencyResolver(
        VariantDependencyResolverFactory.DeclaredVersionTransformer declaredVersionTransformer,
        ProjectDependencyPublicationResolver projectDependencyResolver,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        Configuration resolutionConfiguration,
        boolean resolveVariantCoordinates,
        AttributesSchemaInternal consumerSchema,
        ImmutableAttributesFactory attributesFactory
    ) {
        this.declaredVersionTransformer = declaredVersionTransformer;
        this.projectDependencyResolver = projectDependencyResolver;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.resolutionConfiguration = resolutionConfiguration;
        this.resolveVariantCoordinates = resolveVariantCoordinates;
        this.consumerSchema = consumerSchema;
        this.attributesFactory = attributesFactory;
    }

    private ResolvedMappings calculateMappings() {
        if (!resolveVariantCoordinates) {
            return new ResolvedMappings(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet(), Collections.emptySet());
        }

        Map<ModuleDependencyKey, ModuleVersionIdentifier> resolvedModules = new HashMap<>();
        Map<ProjectDependencyKey, ModuleVersionIdentifier> resolvedProjects = new HashMap<>();
        Set<ModuleDependencyKey> incompatibleModules = new HashSet<>();
        Set<ProjectDependencyKey> incompatibleProjects = new HashSet<>();

        ResolutionResult resolutionResult = resolutionConfiguration.getIncoming().getResolutionResult();
        ResolvedComponentResult rootComponent = resolutionResult.getRoot();
        ResolvedVariantResult rootVariant = getRootVariant(rootComponent);
        ImmutableAttributes requestAttributes = ((AttributeContainerInternal) rootVariant.getAttributes()).asImmutable();
        visitFirstLevelEdges(rootComponent, rootVariant, edge -> {
            ComponentSelector requested = edge.getRequested();

            // TODO: What happens if there are duplicate declared dependencies?
            // Constraints?
            // TODO: What happens if someone requests a target component?
            // What about if it endorses strict versions?
            // Transitive? Excludes? Artifacts?

            ModuleVersionIdentifier coordinates = getVariantCoordinates(edge, requestAttributes);
            if (requested instanceof ModuleComponentSelector) {
                ModuleComponentSelector requestedModule = (ModuleComponentSelector) requested;

                ModuleDependencyKey key = new ModuleDependencyKey(requestedModule.getModuleIdentifier(), ModuleDependencyDetails.from(requested));
                if (incompatibleModules.contains(key)) {
                    return;
                }

                ModuleVersionIdentifier existing = resolvedModules.put(key, coordinates);
                if (existing != null && !existing.equals(coordinates)) {
                    resolvedModules.remove(key);
                    incompatibleModules.add(key);
                }
            } else if (requested instanceof ProjectComponentSelector) {
                ProjectComponentSelectorInternal requestedProject = (ProjectComponentSelectorInternal) requested;

                ProjectDependencyKey key = new ProjectDependencyKey(requestedProject.getIdentityPath(), ModuleDependencyDetails.from(requested));
                if (incompatibleProjects.contains(key)) {
                    return;
                }

                ModuleVersionIdentifier existing = resolvedProjects.put(key, coordinates);
                if (existing != null && !existing.equals(coordinates)) {
                    resolvedProjects.remove(key);
                    incompatibleProjects.add(key);
                }
            }
        });

        return new ResolvedMappings(resolvedModules, resolvedProjects, incompatibleModules, incompatibleProjects);
    }

    private static void visitFirstLevelEdges(ResolvedComponentResult rootComponent, ResolvedVariantResult rootVariant, Consumer<ResolvedDependencyResult> visitor) {
        List<DependencyResult> rootEdges = rootComponent.getDependenciesForVariant(rootVariant);
        for (DependencyResult dependencyResult : rootEdges) {
            if (!(dependencyResult instanceof ResolvedDependencyResult)) {
                UnresolvedDependencyResult unresolved = (UnresolvedDependencyResult) dependencyResult;
                throw new GradleException("Could not map coordinates for " + unresolved.getAttempted().getDisplayName() + ".", unresolved.getFailure());
            }
            if (dependencyResult.isConstraint()) {
                // Constraints also appear in the graph if they contributed to it.
                // TODO: Where do these constraints point to?
                //       Can they point to nodes that the root node does not depend on?
                continue;
            }

            visitor.accept((ResolvedDependencyResult) dependencyResult);
        }
    }

    @NotNull
    private ResolvedVariantResult getRootVariant(ResolvedComponentResult rootComponent) {
        return rootComponent.getVariants().stream()
            .filter(x -> x.getDisplayName().equals(resolutionConfiguration.getName()))
            .findFirst().get();
    }

    private Map<ModuleIdentifier, String> calculateResolvedComponentVersions() {
        ResolutionResult resolutionResult = resolutionConfiguration.getIncoming().getResolutionResult();
        Map<ModuleIdentifier, String> resolvedComponentVersions = new HashMap<>();
        resolutionResult.allComponents(component -> {
            ModuleVersionIdentifier moduleVersion = getComponentCoordinates(component);
            resolvedComponentVersions.put(moduleVersion.getModule(), moduleVersion.getVersion());
        });
        return resolvedComponentVersions;
    }

    private ResolvedMappings getMappings() {
        if (mappings == null) {
            mappings = calculateMappings();
        }
        return mappings;
    }

    private Map<ModuleIdentifier, String> getResolvedComponentVersions() {
        if (resolvedComponentVersions == null) {
            resolvedComponentVersions = calculateResolvedComponentVersions();
        }
        return resolvedComponentVersions;
    }

    private ModuleVersionIdentifier getVariantCoordinates(ResolvedDependencyResult edge, ImmutableAttributes requestAttributes) {
        ResolvedVariantResult variant = edge.getResolvedVariant();
        ComponentIdentifier componentId = variant.getOwner();

        if (componentId instanceof ProjectComponentIdentifier) {
            Path identityPath = ((ProjectComponentIdentifierInternal) componentId).getIdentityPath();

            // Using the display name here is ugly, but it is the same as the published configuration name.
            String variantName = variant.getDisplayName();

            // TODO: Eventually, this data should be exposed by getExternalVariant in the resolution result.
            ModuleVersionIdentifier coordinates = projectDependencyResolver.resolveVariant(ModuleVersionIdentifier.class, identityPath, variantName);
            if (coordinates == null) {
                // The variant we resolved has not been published. Kotlin and Android use separate local and published configurations
                // (Kotlin's -published configurations). Perform a second round of attributes matching on all published configurations
                // to see if we find a match.
                // Eventually, we should help users migrate to using the same model to represent their local and published variants,
                // so that we can avoid this second round of matching.

                // dependency-management implements a more complex version of this logic where attributes on constraints
                // that select the variant are also included as part of the variant matching attributes. This logic is too complex
                // to duplicate here, so we implement a _mostly_ correct version of it here.
                ImmutableAttributes edgeAttributes = ((AttributeContainerInternal) edge.getRequested().getAttributes()).asImmutable();
                ImmutableAttributes attributes = attributesFactory.concat(requestAttributes, edgeAttributes);
                Collection<? extends Capability> capabilities = edge.getRequested().getRequestedCapabilities();

                coordinates = projectDependencyResolver.resolveVariantWithAttributeMatching(
                    ModuleVersionIdentifier.class, identityPath, attributes, capabilities, consumerSchema
                );
            }

            if (coordinates == null) {
                throw new InvalidUserDataException("Could not resolve coordinates for variant " + variantName + " of project " + identityPath + ".");
            }

            return coordinates;
        } else if (componentId instanceof ModuleComponentIdentifier) {
            ResolvedVariantResult externalVariant = variant.getExternalVariant().orElse(null);
            if (externalVariant != null) {
                ComponentIdentifier owningComponent = externalVariant.getOwner();
                if (owningComponent instanceof ModuleComponentIdentifier) {
                    ModuleComponentIdentifier moduleComponentId = (ModuleComponentIdentifier) owningComponent;
                    return moduleIdentifierFactory.moduleWithVersion(moduleComponentId.getModuleIdentifier(), moduleComponentId.getVersion());
                }
                throw new GradleException("Expected owning component of module component to be a module component: " + owningComponent);
            }

            // TODO: What if the resolved variant is published alongside a primary variant?
            // Do we use a classifier to reference it?
            // What if my module dependency ISN'T maven compatible?
            // e.g. I depend on test fixtures, and they are not their own module.

            ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) componentId;

            return moduleIdentifierFactory.moduleWithVersion(moduleId.getModuleIdentifier(), moduleId.getVersion());
        } else {
            throw new UnsupportedOperationException("Unexpected component identifier type: " + componentId);
        }
    }

    private ModuleVersionIdentifier getComponentCoordinates(ResolvedComponentResult component) {
        ComponentIdentifier componentId = component.getId();
        if (componentId instanceof ProjectComponentIdentifier) {
            Path identityPath = ((ProjectComponentIdentifierInternal) componentId).getIdentityPath();
            return projectDependencyResolver.resolveComponent(ModuleVersionIdentifier.class, identityPath);
        } else if (componentId instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) componentId;
            return moduleIdentifierFactory.moduleWithVersion(moduleId.getModuleIdentifier(), moduleId.getVersion());
        } else {
            throw new UnsupportedOperationException("Unexpected component identifier type: " + componentId);
        }
    }



    @Override
    public Coordinates resolveVariantCoordinates(ModuleDependency dependency, VariantWarningCollector warnings) {
        if (dependency instanceof ProjectDependency) {
            return resolveProjectVariantCoordinates((ProjectDependency) dependency, warnings);
        } else {
            return resolveModuleVariantCoordinates(dependency, warnings);
        }
    }

    private Coordinates resolveModuleVariantCoordinates(ModuleDependency dependency, VariantWarningCollector warnings) {
        ModuleIdentifier module = moduleIdentifierFactory.module(dependency.getGroup(), dependency.getName());
        ModuleDependencyKey key = new ModuleDependencyKey(module, ModuleDependencyDetails.from(dependency));

        ModuleVersionIdentifier resolved = getMappings().resolvedModules.get(key);
        if (resolved != null) {
            return Coordinates.from(resolved);
        }

        // The resolved graph did not have the requested module. This is likely user
        // error, as the resolution configuration should have the same dependencies
        // as the published variant. Fallback to version mapping only.

        // TODO: Emit warning. Use:
        // getMappings().incompatibleModules.contains(key)

        return resolveModuleComponentCoordinates(module, dependency.getVersion());
    }

    private Coordinates resolveProjectVariantCoordinates(ProjectDependency dependency, VariantWarningCollector warnings) {
        Path identityPath = getIdentityPath(dependency);
        ProjectDependencyKey key = new ProjectDependencyKey(identityPath, ModuleDependencyDetails.from(dependency));

        ModuleVersionIdentifier resolved = getMappings().resolvedProjects.get(key);
        if (resolved != null) {
            return Coordinates.from(resolved);
        }

        // The resolved graph did not have the requested project. This is likely user
        // error, as the resolution configuration should have the same dependencies
        // as the published variant. Fallback to version mapping only.

        // TODO: Emit warning. Use:
        // getMappings().incompatibleProjects.contains(key)

        return resolveProjectComponentCoordinates(identityPath);
    }

    @Override
    public Coordinates resolveVariantCoordinates(DependencyConstraint dependency, VariantWarningCollector warnings) {
        // We currently do not support resolving constraints to variant-level precision.
        // This is not a _huge_ problem, since this is only used by Maven for the dependencyManagement block,
        // which itself is only used for publishing Platforms.

        // The difficulty in implementing this is that a single constraint for some coordinates can actually
        // be equivalent to multiple constraints for different coordinates if the original component
        // is multi-coordinate.

        // We would need to publish _all_ coordinates of the component referenced by the constraint, however
        // the resolution result does not expose this data.

        // For now, return the component coordinates.
        return resolveComponentCoordinates(dependency);
    }

    @Override
    public Coordinates resolveComponentCoordinates(ModuleDependency dependency) {
        if (dependency instanceof ProjectDependency) {
            return resolveProjectComponentCoordinates(getIdentityPath((ProjectDependency) dependency));
        } else {
            ModuleIdentifier module = moduleIdentifierFactory.module(dependency.getGroup(), dependency.getName());
            return resolveModuleComponentCoordinates(module, dependency.getVersion());
        }
    }

    @Override
    public Coordinates resolveComponentCoordinates(DependencyConstraint dependency) {
        if (dependency instanceof DefaultProjectDependencyConstraint) {
            return resolveProjectComponentCoordinates(getIdentityPath(((DefaultProjectDependencyConstraint) dependency).getProjectDependency()));
        } else {
            ModuleIdentifier module = moduleIdentifierFactory.module(dependency.getGroup(), dependency.getName());
            return resolveModuleComponentCoordinates(module, dependency.getVersion());
        }
    }

    private Coordinates resolveProjectComponentCoordinates(Path identityPath) {
        ModuleVersionIdentifier identifier = projectDependencyResolver.resolveComponent(ModuleVersionIdentifier.class, identityPath);
        String resolvedVersion = getResolvedComponentVersions().get(identifier.getModule());

        if (resolvedVersion == null) {
            return Coordinates.from(identifier);
        }

        return Coordinates.create(identifier.getGroup(), identifier.getName(), resolvedVersion);
    }

    private Coordinates resolveModuleComponentCoordinates(ModuleIdentifier module, @Nullable String declaredVersion) {
        String resolvedVersion = getResolvedComponentVersions().get(module);

        if (resolvedVersion != null) {
            return Coordinates.create(module.getGroup(), module.getName(), resolvedVersion);
        }

        return Coordinates.create(
            module.getGroup(),
            module.getName(),
            declaredVersionTransformer.transform(module.getGroup(), module.getName(), declaredVersion)
        );
    }

    private static Path getIdentityPath(ProjectDependency dependency) {
        return ((ProjectDependencyInternal) dependency).getIdentityPath();
    }

    static class ResolvedMappings {
        final Map<ModuleDependencyKey, ModuleVersionIdentifier> resolvedModules;
        final Map<ProjectDependencyKey, ModuleVersionIdentifier> resolvedProjects;

        // Incompatible modules and projects are those that have multiple dependencies with the same
        // attributes and capabilities, but have somehow resolved to different coordinates. This can
        // likely happen when the dependency is declared with a targetConfiguration.
        final Set<ModuleDependencyKey> incompatibleModules;
        final Set<ProjectDependencyKey> incompatibleProjects;

        ResolvedMappings(
            Map<ModuleDependencyKey, ModuleVersionIdentifier> resolvedModules,
            Map<ProjectDependencyKey, ModuleVersionIdentifier> resolvedProjects,
            Set<ModuleDependencyKey> incompatibleModules,
            Set<ProjectDependencyKey> incompatibleProjects
        ) {
            this.resolvedModules = resolvedModules;
            this.resolvedProjects = resolvedProjects;
            this.incompatibleModules = incompatibleModules;
            this.incompatibleProjects = incompatibleProjects;
        }
    }

    static class ModuleDependencyKey {
        private final ModuleIdentifier module;
        private final ModuleDependencyDetails details;

        public ModuleDependencyKey(ModuleIdentifier module, ModuleDependencyDetails details) {
            this.module = module;
            this.details = details;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ModuleDependencyKey that = (ModuleDependencyKey) o;
            return Objects.equals(module, that.module) && Objects.equals(details, that.details);
        }

        @Override
        public int hashCode() {
            return Objects.hash(module, details);
        }
    }

    static class ProjectDependencyKey {
        private final Path idenityPath;
        private final ModuleDependencyDetails details;

        public ProjectDependencyKey(Path idenityPath, ModuleDependencyDetails details) {
            this.idenityPath = idenityPath;
            this.details = details;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProjectDependencyKey that = (ProjectDependencyKey) o;
            return Objects.equals(idenityPath, that.idenityPath) && Objects.equals(details, that.details);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idenityPath, details);
        }
    }

    private static class ModuleDependencyDetails {
        final AttributeContainer requestAttributes;
        final List<Capability> requestCapabilities;

        public ModuleDependencyDetails(
            AttributeContainer requestAttributes,
            List<Capability> requestCapabilities
        ) {
            this.requestAttributes = requestAttributes;
            this.requestCapabilities = requestCapabilities;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ModuleDependencyDetails that = (ModuleDependencyDetails) o;
            return Objects.equals(requestAttributes, that.requestAttributes) && Objects.equals(requestCapabilities, that.requestCapabilities);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestAttributes, requestCapabilities);
        }

        public static ModuleDependencyDetails from(ModuleDependency dependency) {
            return new ModuleDependencyDetails(
                dependency.getAttributes(),
                dependency.getRequestedCapabilities()
            );
        }

        public static ModuleDependencyDetails from(ComponentSelector componentSelector) {
            return new ModuleDependencyDetails(
                componentSelector.getAttributes(),
                componentSelector.getRequestedCapabilities()
            );
        }
    }
}
