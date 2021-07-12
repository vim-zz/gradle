/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.resolvers;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.model.ObjectFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class ResolverSpec {
    protected final ObjectFactory objectFactory;

    private final List<Configuration> extendsFrom = new ArrayList<>();
    private boolean lenient = false;

    protected ResolverSpec(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    public void from(Configuration configuration) {
        extendsFrom.add(configuration);
    }

    public void lenient() {
        this.lenient = true;
    }

    // TODO: this should not be public API - figure a better way
    public boolean isLenient() {
        return lenient;
    }

    // TODO: this should not be public API - figure a better way
    public void configure(Configuration resolver) {
        resolver.setVisible(false);
        resolver.setCanBeConsumed(false);
        resolver.setCanBeResolved(true);
        resolver.extendsFrom(extendsFrom.toArray(new Configuration[0]));
    }
}
