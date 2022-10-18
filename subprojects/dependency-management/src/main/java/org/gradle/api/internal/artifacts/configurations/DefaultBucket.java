/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.DependencyConstraintSet;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishArtifactSet;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.CompositeDomainObjectSet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.DelegatingDomainObjectSet;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.DefaultDependencyConstraintSet;
import org.gradle.api.internal.artifacts.DefaultDependencySet;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
import org.gradle.api.internal.artifacts.transform.ExtraExecutionGraphDependenciesResolverFactory;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
public class DefaultBucket extends AbstractConfiguration {
    @Nullable
    @Override
    public List<String> getDeclarationAlternatives() {
        return null;
    }

    @Nullable
    @Override
    public DeprecationMessageBuilder.WithDocumentation getConsumptionDeprecation() {
        return null;
    }

    @Nullable
    @Override
    public List<String> getResolutionAlternatives() {
        return Collections.emptyList();
    }

    @Override
    public boolean isFullyDeprecated() {
        return false;
    }

    @Override
    public DeprecatableConfiguration deprecateForDeclaration(String... alternativesForDeclaring) {
        return this;
    }

    @Override
    public DeprecatableConfiguration deprecateForConsumption(Function<DeprecationMessageBuilder.DeprecateConfiguration, DeprecationMessageBuilder.WithDocumentation> deprecation) {
        return this;
    }

    @Override
    public DeprecatableConfiguration deprecateForResolution(String... alternativesForResolving) {
        return this;
    }

    @Override
    public Module getModule() {
        return null; // TODO:
    }

    @Override
    public void validateMutation(MutationType type) {
        switch (type) {
            case DEPENDENCIES: // fall-through
            case DEPENDENCY_ATTRIBUTES:
                if (!mutable) {
                    throw new IllegalStateException("Not mutable!");
                }
                break;
            default:
                // Don't care
        }

        for(MutationValidator validator : childMutationValidators) {
            validator.validateMutation(type);
        }
    }

    private final Set<Configuration> extendsFrom = new LinkedHashSet<>();
    private final DisplayName displayName;
    private final RootComponentMetadataBuilder rootComponentMetadataBuilder;
    private final Path identityPath;

    private final DefaultDependencySet dependencies;
    private final DefaultDependencyConstraintSet dependencyConstraints;
    private final DefaultDomainObjectSet<Dependency> ownDependencies;
    private final DefaultDomainObjectSet<DependencyConstraint> ownDependencyConstraints;
    private CompositeDomainObjectSet<Dependency> inheritedDependencies;
    private CompositeDomainObjectSet<DependencyConstraint> inheritedDependencyConstraints;
    private DefaultDependencySet allDependencies;
    private DefaultDependencyConstraintSet allDependencyConstraints;
    private ImmutableActionSet<DependencySet> defaultDependencyActions = ImmutableActionSet.empty();
    private ImmutableActionSet<DependencySet> withDependencyActions = ImmutableActionSet.empty();

    private final PublishArtifactSet artifacts;

    private final DomainObjectCollectionFactory domainObjectCollectionFactory;

    private final Path path;

    public DefaultBucket(String name,
                         RootComponentMetadataBuilder rootComponentMetadataBuilder,
                         DomainObjectContext domainObjectContext,
                         ConfigurationsProvider configurationsProvider,
                         DomainObjectCollectionFactory domainObjectCollectionFactory,
                         FileCollectionFactory fileCollectionFactory) {
        super(name, configurationsProvider);
        this.rootComponentMetadataBuilder = rootComponentMetadataBuilder;
        this.identityPath = domainObjectContext.identityPath(name);
        this.domainObjectCollectionFactory = domainObjectCollectionFactory;
        this.displayName = Describables.memoize(new DefaultConfiguration.ConfigurationDescription(identityPath));

        this.ownDependencies = (DefaultDomainObjectSet<Dependency>) domainObjectCollectionFactory.newDomainObjectSet(Dependency.class);
        this.ownDependencies.beforeCollectionChanges(validateMutationType(this, MutationType.DEPENDENCIES));
        this.ownDependencyConstraints = (DefaultDomainObjectSet<DependencyConstraint>) domainObjectCollectionFactory.newDomainObjectSet(DependencyConstraint.class);
        this.ownDependencyConstraints.beforeCollectionChanges(validateMutationType(this, MutationType.DEPENDENCIES));

        this.dependencies = new DefaultDependencySet(Describables.of(displayName, "dependencies"), this, ownDependencies);
        this.dependencyConstraints = new DefaultDependencyConstraintSet(Describables.of(displayName, "dependency constraints"), this, ownDependencyConstraints);

        this.artifacts = new EmptyPublishArtifactSet(domainObjectCollectionFactory.newDomainObjectSet(PublishArtifact.class), fileCollectionFactory);

        this.path = domainObjectContext.projectPath(name);
    }

    @Override
    public ResolutionStrategyInternal getResolutionStrategy() {
        return NoOpResolutionStrategy.INSTANCE;
    }

    @Override
    public LocalComponentMetadata toRootComponentMetaData() {
        return rootComponentMetadataBuilder.toRootComponentMetaData();
    }

    @Override
    public Configuration resolutionStrategy(Closure closure) {
        return this;
    }

    @Override
    public Configuration resolutionStrategy(Action<? super ResolutionStrategy> action) {
        return this;
    }

    @Override
    public State getState() {
        return State.UNRESOLVED;
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public Configuration setVisible(boolean visible) {
        return this;
    }

    @Override
    public Set<Configuration> getExtendsFrom() {
        return Collections.unmodifiableSet(extendsFrom);
    }

    @Override
    public Configuration setExtendsFrom(Iterable<Configuration> superConfigs) {
        validateMutation(MutationType.DEPENDENCIES);

        for (Configuration configuration : this.extendsFrom) {
            if (inheritedDependencies != null) {
                inheritedDependencies.removeCollection(configuration.getAllDependencies());
            }
            if (inheritedDependencyConstraints != null) {
                inheritedDependencyConstraints.removeCollection(configuration.getAllDependencyConstraints());
            }
        }
        extendsFrom.clear();

        extendsFrom(superConfigs);
        return this;
    }

    @Override
    public Configuration extendsFrom(Configuration... superConfigs) {
        extendsFrom(Arrays.asList(superConfigs));
        return this;
    }

    void extendsFrom(Iterable<Configuration> superConfigs) {
        validateMutation(MutationType.DEPENDENCIES);
        for (Configuration configuration : superConfigs) {
            if (configuration.getHierarchy().contains(this)) {
                throw new InvalidUserDataException(String.format(
                    "Cyclic extendsFrom from %s and %s is not allowed. See existing hierarchy: %s", this,
                    configuration, configuration.getHierarchy()));
            }
            if (this.extendsFrom.add(configuration)) {
                if (inheritedDependencies != null) {
                    inheritedDependencies.addCollection(configuration.getAllDependencies());
                }
                if (inheritedDependencyConstraints != null) {
                    inheritedDependencyConstraints.addCollection(configuration.getAllDependencyConstraints());
                }
            }
        }
    }

    @Override
    public DependencySet getDependencies() {
        return dependencies;
    }

    @Override
    public DependencySet getAllDependencies() {
        if (allDependencies == null) {
            initAllDependencies();
        }
        return allDependencies;
    }

    private synchronized void initAllDependencies() {
        if (allDependencies != null) {
            return;
        }
        inheritedDependencies = domainObjectCollectionFactory.newDomainObjectSet(Dependency.class, ownDependencies);
        for (Configuration configuration : this.extendsFrom) {
            inheritedDependencies.addCollection(configuration.getAllDependencies());
        }
        allDependencies = new DefaultDependencySet(Describables.of(displayName, "all dependencies"), this, inheritedDependencies);
    }

    @Override
    public DependencyConstraintSet getDependencyConstraints() {
        return dependencyConstraints;
    }

    @Override
    public DependencyConstraintSet getAllDependencyConstraints() {
        if (allDependencyConstraints == null) {
            initAllDependencyConstraints();
        }
        return allDependencyConstraints;
    }

    private synchronized void initAllDependencyConstraints() {
        if (allDependencyConstraints != null) {
            return;
        }
        inheritedDependencyConstraints = domainObjectCollectionFactory.newDomainObjectSet(DependencyConstraint.class, ownDependencyConstraints);
        for (Configuration configuration : this.extendsFrom) {
            inheritedDependencyConstraints.addCollection(configuration.getAllDependencyConstraints());
        }
        allDependencyConstraints = new DefaultDependencyConstraintSet(Describables.of(displayName, "all dependency constraints"), this, inheritedDependencyConstraints);
    }

    @Override
    public Configuration defaultDependencies(final Action<? super DependencySet> action) {
        validateMutation(MutationValidator.MutationType.DEPENDENCIES);
        defaultDependencyActions = defaultDependencyActions.add(dependencies -> {
            if (dependencies.isEmpty()) {
                action.execute(dependencies);
            }
        });
        return this;
    }

    @Override
    public Configuration withDependencies(final Action<? super DependencySet> action) {
        validateMutation(MutationValidator.MutationType.DEPENDENCIES);
        withDependencyActions = withDependencyActions.add(action);
        return this;
    }

    @Override
    public void runDependencyActions() {
        defaultDependencyActions.execute(dependencies);
        withDependencyActions.execute(dependencies);

        // Discard actions after execution
        defaultDependencyActions = ImmutableActionSet.empty();
        withDependencyActions = ImmutableActionSet.empty();

        for (Configuration superConfig : extendsFrom) {
            ((ConfigurationInternal) superConfig).runDependencyActions();
        }
    }

    boolean mutable = false;

    @Override
    public void markAsObserved(InternalState requestedState) {
        if (requestedState.compareTo(InternalState.UNRESOLVED) > 0) {
            preventFromFurtherMutation();
            extendsFrom.forEach(it -> ((ConfigurationInternal) it).markAsObserved(requestedState));
        }
    }

    private final Set<MutationValidator> childMutationValidators = Sets.newHashSet();

    @Override
    public void addMutationValidator(MutationValidator validator) {
        childMutationValidators.add(validator);
    }

    @Override
    public void removeMutationValidator(MutationValidator validator) {
        childMutationValidators.remove(validator);
    }

    @Override
    public OutgoingVariant convertToOutgoingVariant() {
        return new OutgoingVariant() {
            @Override
            public DisplayName asDescribable() {
                return Describables.of("Empty outgoing variant from '" + getName() + "'");
            }

            @Override
            public AttributeContainerInternal getAttributes() {
                return ImmutableAttributes.EMPTY;
            }

            @Override
            public Set<? extends PublishArtifact> getArtifacts() {
                return Collections.emptySet();
            }

            @Override
            public Set<? extends OutgoingVariant> getChildren() {
                return Collections.emptySet();
            }
        };
    }

    @Override
    public void collectVariants(VariantVisitor visitor) {
        // do nothing
    }

    List<Action<? super ConfigurationInternal>> beforeLocking = new ArrayList<>();

    @Override
    public void beforeLocking(Action<? super ConfigurationInternal> action) {
        beforeLocking.add(action);
    }

    @Override
    public boolean isCanBeMutated() {
        return mutable;
    }

    @Override
    public void preventFromFurtherMutation() {
        if (mutable) {
            if (beforeLocking != null) {
                beforeLocking.forEach(it -> it.execute(this));
                beforeLocking.clear();
            }
        }
        mutable = false;
    }

    @Override
    public boolean isIncubating() {
        return false;
    }

    @Override
    public Set<ExcludeRule> getAllExcludeRules() {
        return Collections.emptySet();
    }

    @Override
    public ExtraExecutionGraphDependenciesResolverFactory getDependenciesResolver() {
        throw new UnsupportedOperationException("Cannot resolve a bucket configuration");
    }

    @Nullable
    @Override
    public ConfigurationInternal getConsistentResolutionSource() {
        return null;
    }

    @Override
    public Supplier<List<DependencyConstraint>> getConsistentResolutionConstraints() {
        return Collections::emptyList;
    }

    @Override
    public ResolveException maybeAddContext(ResolveException e) {
        return e;
    }

    @Override
    public Configuration copy() {
        return null; // TODO:
    }

    @Override
    public Configuration copyRecursive() {
        return null; // TODO:
    }

    @Override
    public Configuration copy(Spec<? super Dependency> dependencySpec) {
        return null; // TODO:
    }

    @Override
    public Configuration copyRecursive(Spec<? super Dependency> dependencySpec) {
        return null; // TODO:
    }

    @Override
    public Configuration copy(Closure dependencySpec) {
        return null; // TODO:
    }

    @Override
    public Configuration copyRecursive(Closure dependencySpec) {
        return null; // TODO:
    }

    @Override
    public boolean isTransitive() {
        return false;
    }

    @Override
    public Configuration setTransitive(boolean t) {
        return this;
    }

    @Override
    public Set<File> resolve() {
        throw new UnsupportedOperationException("Cannot resolve bucket");
    }

    @Override
    public Set<File> files(Closure dependencySpecClosure) {
        throw new UnsupportedOperationException("Cannot get bucket files");
    }

    @Override
    public Set<File> files(Spec<? super Dependency> dependencySpec) {
        throw new UnsupportedOperationException("Cannot get bucket files");
    }

    @Override
    public Set<File> files(Dependency... dependencies) {
        throw new UnsupportedOperationException("Cannot get bucket files");
    }

    @Override
    public FileCollection fileCollection(Spec<? super Dependency> dependencySpec) {
        throw new UnsupportedOperationException("Cannot get bucket files");
    }

    @Override
    public FileCollection fileCollection(Closure dependencySpecClosure) {
        throw new UnsupportedOperationException("Cannot get bucket files");
    }

    @Override
    public FileCollection fileCollection(Dependency... dependencies) {
        throw new UnsupportedOperationException("Cannot get bucket files");
    }

    @Override
    public ResolvedConfiguration getResolvedConfiguration() {
        throw new UnsupportedOperationException("Cannot get bucket files");
    }

    @Override
    public String getDisplayName() {
        return displayName.getDisplayName();
    }

    @Override
    public TaskDependency getTaskDependencyFromProjectDependency(boolean useDependedOn, String taskName) {
        return DefaultTaskDependency.EMPTY;
    }

    @Override
    public PublishArtifactSet getArtifacts() {
        return artifacts;
    }

    @Override
    public PublishArtifactSet getAllArtifacts() {
        return artifacts;
    }

    @Override
    public Set<ExcludeRule> getExcludeRules() {
        return Collections.emptySet();
    }

    @Override
    public Configuration exclude(Map<String, String> excludeProperties) {
        return this;
    }

    @Override
    public ResolvableDependencies getIncoming() {
        throw new UnsupportedOperationException("Cannot get bucket files");
    }

    @Override
    public ConfigurationPublications getOutgoing() {
        throw new UnsupportedOperationException("Cannot get bucket files");
    }

    @Override
    public void outgoing(Action<? super ConfigurationPublications> action) {
        // no-op
    }

    @Override
    public void setCanBeConsumed(boolean allowed) {
        // no-op
    }

    @Override
    public boolean isCanBeConsumed() {
        return false;
    }

    @Override
    public void setCanBeResolved(boolean allowed) {
        // no-op
    }

    @Override
    public boolean isCanBeResolved() {
        return false;
    }

    @Override
    public Configuration shouldResolveConsistentlyWith(Configuration versionsSource) {
        return this;
    }

    @Override
    public Configuration disableConsistentResolution() {
        return this;
    }

    @Override
    public AttributeContainerInternal getAttributes() {
        return ImmutableAttributes.EMPTY;
    }

    @Override
    public String getPath() {
        return path.getPath();
    }

    @Override
    public Path getIdentityPath() {
        return identityPath;
    }

    @Override
    public void setReturnAllVariants(boolean returnAllVariants) {

    }

    @Override
    public boolean getReturnAllVariants() {
        return false; // TODO:
    }

    @Override
    public Configuration attributes(Action<? super AttributeContainer> action) {
        return this;
    }

    @Override
    public Set<File> getFiles() {
        return resolve();
    }

    private static class EmptyPublishArtifactSet extends DelegatingDomainObjectSet<PublishArtifact> implements PublishArtifactSet {
        private final FileCollection empty;

        EmptyPublishArtifactSet(DomainObjectSet<PublishArtifact> delegate, FileCollectionFactory factory) {
            super(delegate);
            this.empty = factory.empty();
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return DefaultTaskDependency.EMPTY;
        }

        @Override
        public FileCollection getFiles() {
            return empty;
        }
    }
}
