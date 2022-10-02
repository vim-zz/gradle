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
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.CapabilitiesResolution;
import org.gradle.api.artifacts.CapabilityResolutionDetails;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ComponentSelectionRules;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.VariantSelectionDetails;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.Expiry;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CapabilitiesConflictHandler;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.provider.Property;
import org.gradle.internal.Actions;
import org.gradle.internal.locking.NoOpDependencyLockingProvider;
import org.gradle.internal.rules.RuleAction;
import org.gradle.internal.rules.SpecRuleAction;

import javax.annotation.Nullable;
import java.io.File;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class NoOpResolutionStrategy implements ResolutionStrategyInternal {
    static final ResolutionStrategyInternal INSTANCE = new NoOpResolutionStrategy();

    @Override
    public ResolutionStrategy failOnVersionConflict() {
        return this;
    }

    @Override
    public ResolutionStrategy failOnDynamicVersions() {
        return this;
    }

    @Override
    public ResolutionStrategy failOnChangingVersions() {
        return this;
    }

    @Override
    public ResolutionStrategy failOnNonReproducibleResolution() {
        return this;
    }

    @Override
    public void preferProjectModules() {

    }

    @Override
    public ResolutionStrategy activateDependencyLocking() {
        return this;
    }

    @Override
    public ResolutionStrategy deactivateDependencyLocking() {
        return this;
    }

    @Override
    public ResolutionStrategy disableDependencyVerification() {
        return this;
    }

    @Override
    public ResolutionStrategy enableDependencyVerification() {
        return this;
    }

    @Override
    public ResolutionStrategy force(Object... moduleVersionSelectorNotations) {
        return this;
    }

    @Override
    public ResolutionStrategy setForcedModules(Object... moduleVersionSelectorNotations) {
        return this;
    }

    @Override
    public Set<ModuleVersionSelector> getForcedModules() {
        return Collections.emptySet();
    }

    @Override
    public ResolutionStrategy eachDependency(Action<? super DependencyResolveDetails> rule) {
        return this;
    }

    @Override
    public void cacheDynamicVersionsFor(int value, String units) {

    }

    @Override
    public void cacheDynamicVersionsFor(int value, TimeUnit units) {

    }

    @Override
    public void cacheChangingModulesFor(int value, String units) {

    }

    @Override
    public void cacheChangingModulesFor(int value, TimeUnit units) {

    }

    @Override
    public ComponentSelectionRulesInternal getComponentSelection() {
        return NoOpDependencySubstitutions.NoOpComponentSelectionRules.INSTANCE;
    }

    @Override
    public ResolutionStrategyInternal copy() {
        return this;
    }

    @Override
    public void setMutationValidator(MutationValidator action) {

    }

    @Override
    public DependencyLockingProvider getDependencyLockingProvider() {
        return NoOpDependencyLockingProvider.getInstance();
    }

    @Override
    public boolean isDependencyLockingEnabled() {
        return false;
    }

    @Override
    public void confirmUnlockedConfigurationResolved(String configurationName) {

    }

    @Override
    public CapabilitiesResolutionInternal getCapabilitiesResolutionRules() {
        return NoOpCapabilitiesResolution.INSTANCE;
    }

    @Override
    public boolean isFailingOnDynamicVersions() {
        return false;
    }

    @Override
    public boolean isFailingOnChangingVersions() {
        return false;
    }

    @Override
    public boolean isDependencyVerificationEnabled() {
        return false;
    }

    @Override
    public ResolutionStrategy componentSelection(Action<? super ComponentSelectionRules> action) {
        return this;
    }

    @Override
    public CachePolicy getCachePolicy() {
        return NoOpCachePolicy.INSTANCE;
    }

    @Override
    public ConflictResolution getConflictResolution() {
        return ConflictResolution.latest;
    }

    @Override
    public Action<DependencySubstitution> getDependencySubstitutionRule() {
        return Actions.doNothing();
    }

    @Override
    public void assumeFluidDependencies() {

    }

    @Override
    public boolean resolveGraphToDetermineTaskDependencies() {
        return false;
    }

    @Override
    public SortOrder getSortOrder() {
        return SortOrder.DEFAULT;
    }

    @Override
    public DependencySubstitutionsInternal getDependencySubstitution() {
        return NoOpDependencySubstitutions.INSTANCE;
    }

    @Override
    public ResolutionStrategy dependencySubstitution(Action<? super DependencySubstitutions> action) {
        return this;
    }

    @Override
    public Property<Boolean> getUseGlobalDependencySubstitutionRules() {
        return new DefaultProperty<>(PropertyHost.NO_OP, Boolean.class);
    }

    @Override
    public void sortArtifacts(SortOrder sortOrder) {

    }

    @Override
    public ResolutionStrategy capabilitiesResolution(Action<? super CapabilitiesResolution> action) {
        return this;
    }

    @Override
    public CapabilitiesResolution getCapabilitiesResolution() {
        return NoOpCapabilitiesResolution.INSTANCE;
    }


    private static class NoOpCachePolicy implements CachePolicy {
        private static final CachePolicy INSTANCE = new NoOpCachePolicy();
        private static final Expiry UNKNOWN_EXPIRY = new Expiry() {
            @Override
            public boolean isMustCheck() {
                return true;
            }

            @Override
            public Duration getKeepFor() {
                return Duration.ZERO;
            }
        };

        @Override
        public Expiry versionListExpiry(ModuleIdentifier selector, Set<ModuleVersionIdentifier> moduleVersions, Duration age) {
            return UNKNOWN_EXPIRY;
        }

        @Override
        public Expiry missingModuleExpiry(ModuleComponentIdentifier component, Duration age) {
            return UNKNOWN_EXPIRY;
        }

        @Override
        public Expiry moduleExpiry(ModuleComponentIdentifier component, ResolvedModuleVersion resolvedModuleVersion, Duration age) {
            return UNKNOWN_EXPIRY;
        }

        @Override
        public Expiry moduleExpiry(ResolvedModuleVersion resolvedModuleVersion, Duration age, boolean changing) {
            return UNKNOWN_EXPIRY;
        }

        @Override
        public Expiry changingModuleExpiry(ModuleComponentIdentifier component, ResolvedModuleVersion resolvedModuleVersion, Duration age) {
            return UNKNOWN_EXPIRY;
        }

        @Override
        public Expiry moduleArtifactsExpiry(ModuleVersionIdentifier moduleVersionId, Set<ArtifactIdentifier> artifacts, Duration age, boolean belongsToChangingModule, boolean moduleDescriptorInSync) {
            return UNKNOWN_EXPIRY;
        }

        @Override
        public Expiry artifactExpiry(ArtifactIdentifier artifactIdentifier, @Nullable File cachedArtifactFile, Duration age, boolean belongsToChangingModule, boolean moduleDescriptorInSync) {
            return UNKNOWN_EXPIRY;
        }

        @Override
        public void setOffline() {

        }

        @Override
        public void setRefreshDependencies() {

        }
    }

    private static class NoOpCapabilitiesResolution implements CapabilitiesResolutionInternal {
        private static final CapabilitiesResolutionInternal INSTANCE = new NoOpCapabilitiesResolution();

        @Override
        public void all(Action<? super CapabilityResolutionDetails> action) {

        }

        @Override
        public void withCapability(Capability capability, Action<? super CapabilityResolutionDetails> action) {

        }

        @Override
        public void withCapability(String group, String name, Action<? super CapabilityResolutionDetails> action) {

        }

        @Override
        public void withCapability(Object notation, Action<? super CapabilityResolutionDetails> action) {

        }

        @Override
        public void apply(CapabilitiesConflictHandler.ResolutionDetails details) {

        }
    }
    private static class NoOpDependencySubstitutions implements DependencySubstitutionsInternal {
        private static final DependencySubstitutionsInternal INSTANCE = new NoOpDependencySubstitutions();

        @Override
        public DependencySubstitutions all(Action<? super DependencySubstitution> rule) {
            return this;
        }

        @Override
        public ComponentSelector module(String notation) {
            return NoOpComponentSelector.INSTANCE;
        }

        @Override
        public ComponentSelector project(String path) {
            return NoOpComponentSelector.INSTANCE;
        }

        @Override
        public ComponentSelector variant(ComponentSelector selector, Action<? super VariantSelectionDetails> detailsAction) {
            return NoOpComponentSelector.INSTANCE;
        }

        @Override
        public ComponentSelector platform(ComponentSelector selector) {
            return NoOpComponentSelector.INSTANCE;
        }

        @Override
        public Substitution substitute(ComponentSelector substitutedDependency) {
            return NoOpSubstitution.INSTANCE;
        }

        @Override
        public Action<DependencySubstitution> getRuleAction() {
            return Actions.doNothing();
        }

        @Override
        public boolean rulesMayAddProjectDependency() {
            return false;
        }

        @Override
        public DependencySubstitutions allWithDependencyResolveDetails(Action<? super DependencyResolveDetails> rule, ComponentSelectorConverter componentSelectorConverter) {
            return this;
        }

        @Override
        public void setMutationValidator(MutationValidator validator) {

        }

        @Override
        public DependencySubstitutionsInternal copy() {
            return this;
        }

        private static class NoOpComponentSelector implements ComponentSelector {
            private static final ComponentSelector INSTANCE = new NoOpComponentSelector();

            @Override
            public String getDisplayName() {
                return "no-op";
            }

            @Override
            public boolean matchesStrictly(ComponentIdentifier identifier) {
                return false;
            }

            @Override
            public AttributeContainer getAttributes() {
                return ImmutableAttributes.EMPTY;
            }

            @Override
            public List<Capability> getRequestedCapabilities() {
                return Collections.emptyList();
            }
        }

        private static class NoOpSubstitution implements Substitution {
            private static final Substitution INSTANCE = new NoOpSubstitution();

            @Override
            public Substitution because(String reason) {
                return this;
            }

            @Override
            public Substitution withClassifier(String classifier) {
                return this;
            }

            @Override
            public Substitution withoutClassifier() {
                return this;
            }

            @Override
            public Substitution withoutArtifactSelectors() {
                return this;
            }

            @Override
            @Deprecated
            public void with(ComponentSelector notation) {

            }

            @Override
            public Substitution using(ComponentSelector notation) {
                return this;
            }
        }

        private static class NoOpComponentSelectionRules implements ComponentSelectionRulesInternal {
            private static final ComponentSelectionRulesInternal INSTANCE = new NoOpComponentSelectionRules();

            @Override
            public ComponentSelectionRules all(Action<? super ComponentSelection> selectionAction) {
                return this;
            }

            @Override
            public ComponentSelectionRules all(Closure<?> closure) {
                return this;
            }

            @Override
            public ComponentSelectionRules all(Object ruleSource) {
                return this;
            }

            @Override
            public ComponentSelectionRules withModule(Object id, Action<? super ComponentSelection> selectionAction) {
                return this;
            }

            @Override
            public ComponentSelectionRules withModule(Object id, Closure<?> closure) {
                return this;
            }

            @Override
            public ComponentSelectionRules withModule(Object id, Object ruleSource) {
                return this;
            }

            @Override
            public Collection<SpecRuleAction<? super ComponentSelection>> getRules() {
                return Collections.emptyList();
            }

            @Override
            public ComponentSelectionRules addRule(SpecRuleAction<? super ComponentSelection> specRuleAction) {
                return this;
            }

            @Override
            public ComponentSelectionRules addRule(RuleAction<? super ComponentSelection> specRuleAction) {
                return this;
            }
        }
    }
}
