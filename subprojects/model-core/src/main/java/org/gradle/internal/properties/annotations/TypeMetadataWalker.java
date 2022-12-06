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

package org.gradle.internal.properties.annotations;

import com.google.common.reflect.TypeToken;
import org.gradle.api.provider.Provider;

import java.lang.annotation.Annotation;
import java.util.function.Supplier;

/***
 * A generalized type metadata walker for traversing annotated types and instances using their {@link TypeMetadata}.
 *
 * During the walk we first visit the root (the type or instance passed to {@link #walk(Object, TypeMetadataVisitor)},
 * and then the properties are visited in depth-first order.
 * Nested properties are marked with a nested annotation and their child properties are visited next.
 *
 * The {@link TypeMetadataStore} associated with the walker determines which property annotations are recognized
 * during the walk.
 * Nested {@code Map}s, {@code Iterable}s are resolved as child properties.
 * Iterables and maps can be nested, i.e. {@code Map<String, Iterable<Iterable<String>>>} is supported.
 * Nested {@link Provider}s are unpacked, and the provided type is traversed transparaently.
 */
public interface TypeMetadataWalker<T> {

    /**
     * A factory method for a walker that can visit the property hierarchy of an instance.
     *
     * When visiting a nested property, child properties are discovered using the type of the
     * return property value. This can be a more specific type than the return type of the property's
     * getter method (and can declare additional child properties).
     *
     * Instance walker will throw {@link IllegalStateException} in case a nested property cycle is detected.
     */
    static TypeMetadataWalker<Object> instanceWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
        return new AbstractTypeMetadataWalker.InstanceTypeMetadataWalker(typeMetadataStore, nestedAnnotation);
    }

    /**
     * A factory method for a walker that can visit property hierarchy declared by a type.
     *
     * Type walker can detect a nested property cycle and stop walking the path with a cycle, no exception is thrown.
     */
    static TypeMetadataWalker<TypeToken<?>> typeWalker(TypeMetadataStore typeMetadataStore, Class<? extends Annotation> nestedAnnotation) {
        return new AbstractTypeMetadataWalker.StaticTypeMetadataWalker(typeMetadataStore, nestedAnnotation);
    }

    void walk(T root, TypeMetadataVisitor<T> visitor);

    interface TypeMetadataVisitor<T> {
        void visitRoot(TypeMetadata typeMetadata, T value);

        void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, T value);

        void visitLeaf(String qualifiedName, PropertyMetadata propertyMetadata, Supplier<T> value);
    }
}
