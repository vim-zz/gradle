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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
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
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.io.File;
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
        // TODO:
    }

    private final Set<Configuration> extendsFrom = new LinkedHashSet<>();
    private final DisplayName displayName;
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

    public DefaultBucket(String name,
                         DomainObjectContext domainObjectContext,
                         ConfigurationsProvider configurationsProvider,
                         DomainObjectCollectionFactory domainObjectCollectionFactory,
                         FileCollectionFactory fileCollectionFactory) {
        super(name, configurationsProvider);
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
    }

    @Override
    public ResolutionStrategyInternal getResolutionStrategy() {
        return NoOpResolutionStrategy.INSTANCE;
    }

    @Override
    public LocalComponentMetadata toRootComponentMetaData() {
        return null; // TODO:
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
        return extendsFrom;
    }

    @Override
    public Configuration setExtendsFrom(Iterable<Configuration> superConfigs) {
        extendsFrom.clear();
        CollectionUtils.addAll(extendsFrom, superConfigs);
        return this;
    }

    @Override
    public Configuration extendsFrom(Configuration... superConfigs) {
        CollectionUtils.addAll(extendsFrom, superConfigs);
        return this;
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

    @Override
    public void markAsObserved(InternalState requestedState) {
        // TODO:
    }

    @Override
    public void addMutationValidator(MutationValidator validator) {
        // TODO:
    }

    @Override
    public void removeMutationValidator(MutationValidator validator) {
        // TODO:
    }

    @Override
    public OutgoingVariant convertToOutgoingVariant() {
        // TODO:
        return null;
    }

    @Override
    public void collectVariants(VariantVisitor visitor) {

    }

    @Override
    public void beforeLocking(Action<? super ConfigurationInternal> action) {

    }

    @Override
    public boolean isCanBeMutated() {
        return true;
    }

    @Override
    public void preventFromFurtherMutation() {
        // TODO:
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
        return null; // TODO:
    }

    @Nullable
    @Override
    public ConfigurationInternal getConsistentResolutionSource() {
        return null; // TODO:
    }

    @Override
    public Supplier<List<DependencyConstraint>> getConsistentResolutionConstraints() {
        return Collections::emptyList;
    }

    @Override
    public ResolveException maybeAddContext(ResolveException e) {
        return null; // TODO:
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
        assertIsResolvable();
        return Collections.emptySet(); // unreachable
    }

    @Override
    public Set<File> files(Closure dependencySpecClosure) {
        return resolve();
    }

    @Override
    public Set<File> files(Spec<? super Dependency> dependencySpec) {
        return resolve();
    }

    @Override
    public Set<File> files(Dependency... dependencies) {
        return resolve();
    }

    @Override
    public FileCollection fileCollection(Spec<? super Dependency> dependencySpec) {
        assertIsResolvable();
        return null; // unreachable
    }

    @Override
    public FileCollection fileCollection(Closure dependencySpecClosure) {
        assertIsResolvable();
        return null; // unreachable
    }

    @Override
    public FileCollection fileCollection(Dependency... dependencies) {
        assertIsResolvable();
        return null; // unreachable
    }

    @Override
    public ResolvedConfiguration getResolvedConfiguration() {
        assertIsResolvable();
        return null; // unreachable
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
        assertIsResolvable();
        return null; // unreachable
    }

    @Override
    public ConfigurationPublications getOutgoing() {
        assertIsConsumable();
        return null; // unreachable
    }

    @Override
    public void outgoing(Action<? super ConfigurationPublications> action) {

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
        return null; // TODO:
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
