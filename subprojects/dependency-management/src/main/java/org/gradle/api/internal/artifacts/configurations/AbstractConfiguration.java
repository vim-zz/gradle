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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.file.AbstractFileCollection;
import org.gradle.util.internal.WrapUtil;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

abstract class AbstractConfiguration extends AbstractFileCollection implements ConfigurationInternal, MutationValidator {

    // These fields are not covered by mutation lock
    private final String name;
    private String description;

    @Nullable
    private final ConfigurationsProvider configurationsProvider;

    AbstractConfiguration(String name, @Nullable ConfigurationsProvider configurationsProvider) {
        this.name = name;
        this.configurationsProvider = configurationsProvider;
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    @Nullable
    public final String getDescription() {
        return description;
    }

    @Override
    public final Configuration setDescription(@Nullable String description) {
        this.description = description;
        return this;
    }

    @Deprecated
    @Override
    public final String getUploadTaskName() {
        return Configurations.uploadTaskName(getName());
    }

    @Override
    public final Set<Configuration> getHierarchy() {
        if (getExtendsFrom().isEmpty()) {
            return Collections.singleton(this);
        }
        Set<Configuration> result = WrapUtil.toLinkedSet(this);
        collectSuperConfigs(this, result);
        return result;
    }

    @Override
    public final Set<Configuration> getAll() {
        if (configurationsProvider != null) {
            return ImmutableSet.copyOf(configurationsProvider.getAll());
        }
        return Collections.emptySet();
    }

    @Override
    protected void assertCanCarryBuildDependencies() {
        assertIsResolvable();
    }

    private void collectSuperConfigs(Configuration configuration, Set<Configuration> result) {
        for (Configuration superConfig : configuration.getExtendsFrom()) {
            // The result is an ordered set - so seeing the same value a second time pushes further down
            result.remove(superConfig);
            result.add(superConfig);
            collectSuperConfigs(superConfig, result);
        }
    }

    protected void assertIsResolvable() {
        if (!isCanBeResolved()) {
            throw new IllegalStateException("Resolving dependency configuration '" + name + "' is not allowed as it is defined as 'canBeResolved=false'.\nInstead, a resolvable ('canBeResolved=true') dependency configuration that extends '" + name + "' should be resolved.");
        }
    }

    protected void assertIsConsumable() {
        if (!isCanBeConsumed()) {
            throw new IllegalStateException("Consuming dependency configuration '" + name + "' is not allowed as it is defined as 'canBeConsumed=false'.\nInstead, a consumable ('canBeConsumed=true') dependency configuration that extends '" + name + "' should be resolved.");
        }
    }

    protected static Action<Void> validateMutationType(final MutationValidator mutationValidator, final MutationType type) {
        return arg -> mutationValidator.validateMutation(type);
    }
}
