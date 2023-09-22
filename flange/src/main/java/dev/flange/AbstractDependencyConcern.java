/*
 * Copyright © 2023 GlobalMentor, Inc. <https://www.globalmentor.com/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.flange;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Objects.*;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.locks.*;

import javax.annotation.*;

/**
 * Abstract base class for implementing a dependency concern backed by an existing library using an dependency injection container.
 * @param <C> The type of dependency injection container used by the underlying implementation.
 * @author Garret Wilson
 */
public abstract class AbstractDependencyConcern<C> implements DependencyConcern {

	/** The possible states of container initialization. */
	private enum ContainerState {
		UNINITIALIZED, INITIALIZING, INITIALIZED, INVALID
	}

	@Nullable
	private final C container;

	/**
	 * Container constructor.
	 * @param container The dependency injection container.
	 */
	public AbstractDependencyConcern(@Nonnull final C container) {
		this.container = requireNonNull(container);
	}

	private final Lock containerInitializationLock = new ReentrantLock();

	@Nullable
	private ContainerState containerState = ContainerState.UNINITIALIZED;

	/**
	 * Returns the container, possibly in an uninitialized state.
	 * @return The dependency container, which may not yet be initialized.
	 */
	protected C getContainer() {
		if(containerState == ContainerState.INVALID) {
			throw new IllegalStateException("Dependency container in incomplete initialization state.");
		}
		return container;
	}

	/**
	 * Returns the container, first ensuring that it has been initialized.
	 * @return The dependency container, guaranteed to be initialized.
	 */
	protected C getContainerInitialized() { //TODO consider adding an initialize/load invocation from Csar
		if(containerState == ContainerState.UNINITIALIZED) {
			containerInitializationLock.lock();
			try {
				try {
					if(containerState == ContainerState.UNINITIALIZED) { //check again under the lock to prevent multiple instantiations
						containerState = ContainerState.INITIALIZING; //indicate initialization in progress
						try {
							initialize(container);
						} catch(final IOException ioException) { //TODO remove when initialize throws general dependency exception
							throw new UncheckedIOException(ioException);
						}
						containerState = ContainerState.INITIALIZED;
					}
				} finally {
					if(containerState == ContainerState.INITIALIZING) { //rather than checking errors, simply detect if the container is still initializing at this point, which indicates an error
						containerState = ContainerState.INVALID;
					}
				}
			} finally {
				containerInitializationLock.unlock();
			}
		}
		if(containerState == ContainerState.INVALID) { //could have already been invalid, or set invalid when requesting the lock
			throw new IllegalStateException("Dependency container in incomplete initialization state.");
		}
		return container;
	}

	/**
	 * Initializes the container. Does not update the record of initialization state.
	 * @implSpec This implementation loads definitions by calling {@link #loadDefinitions(Object)}.
	 * @param container The container being initialized.
	 * @throws IOException If there is an I/O error loading container configuration information.
	 * @exception DependencyException if there is a general error initializing the container.
	 */
	protected void initialize(@Nonnull final C container) throws IOException {
		loadDefinitions(container);
	}

	/** The name of the resource containing a list of dependencies. */
	public static final String DEPENDENCIES_LIST_RESOURCE_NAME = "/flange-dependencies.lst";

	/**
	 * Loads persistent definitions.
	 * @implSpec This implementation loads dependencies from a dependencies list.
	 * @param container The container being initialized.
	 * @throws IOException If there is an I/O error loading container definitions.
	 * @exception DependencyException if there is a general error loading container definitions.
	 * @see #DEPENDENCIES_LIST_RESOURCE_NAME
	 */
	protected void loadDefinitions(@Nonnull final C container) throws IOException {
		loadDefinitionListResource(DEPENDENCIES_LIST_RESOURCE_NAME).stream().flatMap(Set::stream).forEach(dependencyName -> {
			final Class<?> dependencyClass;
			try {
				dependencyClass = Class.forName(dependencyName);
			} catch(final ClassNotFoundException classNotFoundException) {
				throw new DependencyException(classNotFoundException.getMessage(), classNotFoundException);
			}
			registerDependency(dependencyClass);
		});
	}

	/**
	 * Loads a resource definition list. The resource definition list consists of a UTF-8 encoded text file containing full class names of dependencies, separated
	 * by newlines. Duplicates are not allowed.
	 * @param resourceName The name of the desired resource, relative to {@link AbstractDependencyConcern}.
	 * @return A set of class names of dependencies loaded from the resource, which will not be present if the resource does not exist.
	 * @throws IOException If there is an I/O error loading container definitions
	 */
	static Optional<Set<String>> loadDefinitionListResource(@Nonnull final String resourceName) throws IOException { //TODO switch to SequencedSet in Java 21
		final URL resourceUrl = AbstractDependencyConcern.class.getResource(resourceName);
		if(resourceUrl == null) {
			return Optional.empty();
		}
		final Set<String> dependencyClassNames = new LinkedHashSet<>();
		try (final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resourceUrl.openStream(), UTF_8))) {
			String line;
			while((line = bufferedReader.readLine()) != null) {
				if(!dependencyClassNames.add(line)) {
					throw new IOException("Duplicate dependency `%s`.".formatted(line));
				}
			}
		}
		return Optional.of(dependencyClassNames);
	}

}
