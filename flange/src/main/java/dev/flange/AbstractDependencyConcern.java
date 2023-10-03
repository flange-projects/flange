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
import static java.nio.file.Files.*;
import static java.util.Objects.*;
import static org.zalando.fauxpas.FauxPas.*;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.util.function.Function;
import java.util.stream.Stream;

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
	 * @throws DependencyException if there is a general error initializing the container.
	 */
	protected void initialize(@Nonnull final C container) throws IOException {
		loadDefinitions(container);
	}

	/** The name of the resource containing a list of dependencies. */
	public static final String DEPENDENCIES_LIST_RESOURCE_NAME = "/flange-dependencies.lst";
	/** The format string for the name of the resource containing a list of dependencies for a particular platform. */
	public static final String DEPENDENCIES_LIST_PLATFORM_RESOURCE_NAME_FORMAT = "/flange-dependencies_platform-%s.lst";
	/** The format string for the name of the file containing a list of dependencies for a particular platform. */
	public static final String DEPENDENCIES_LIST_PLATFORM_FILENAME_FORMAT = "flange-dependencies_platform-%s.lst"; //TODO consolidate

	/**
	 * Loads and registers persisted definitions.
	 * @implSpec This implementation determines dependencies using {@link #determineDependencies(Object)} and then registers those dependencies via
	 *           {@link #registerDependency(Class)}.
	 * @param container The container being initialized.
	 * @throws IOException If there is an I/O error loading container definitions.
	 * @throws DependencyException if there is a general error loading container definitions.
	 * @see #DEPENDENCIES_LIST_RESOURCE_NAME
	 */
	protected void loadDefinitions(@Nonnull final C container) throws IOException {
		determineDependencies(container).forEach(this::registerDependency);
	}

	/**
	 * Loads persisted dependencies from various sources, resolving class names and determining an ultimate list of classes to register.
	 * @implSpec This implementation loads dependencies from one or more dependencies lists.
	 * @param container The container being initialized.
	 * @throws IOException If there is an I/O error loading container definitions.
	 * @throws DependencyException if there is a general error loading container definitions.
	 * @return The dependencies to be registered.
	 * @see #DEPENDENCIES_LIST_RESOURCE_NAME
	 * @see #DEPENDENCIES_LIST_PLATFORM_RESOURCE_NAME_FORMAT
	 */
	protected Collection<Class<?>> determineDependencies(@Nonnull final C container) throws IOException {
		//Interpolate the various dependencies sources, with the default dependency list getting lowest priority.
		//The map will contain implementation classes mapped to each implemented interface(s) of each class,
		//or the class itself if it implements no interfaces. This addresses the most common use case of looking
		//up dependencies by interface, but also allows for other concrete classes to be registered (e.g. `DatabaseProperties`).
		//The current algorithm will not detect interpolated implementations implementing overlapping interfaces,
		//but presumably the container at some point will recognize conflicting definitions for some requested type;
		//if this proves a problem in practice, the algorithm can easily be improved to detect that here.
		//If supporting non-interface subclasses in the future, a more complicated algorithm will be needed that
		//also looks all non-Object ancestor classes; but this may introduce tedious corner cases.
		final Map<Class<?>, Class<?>> dependenciesByType = new LinkedHashMap<>(); //maintain definition order to some extent
		//flange-dependencies.lst
		dependenciesLoadedFromListResource(DEPENDENCIES_LIST_RESOURCE_NAME).forEach(dependency -> {
			final Class<?>[] interfaces = dependency.getInterfaces();
			final Stream<Class<?>> types = interfaces.length > 0 ? Stream.of(interfaces) : Stream.of(dependency);
			types.forEach(type -> dependenciesByType.put(type, dependency));
		});
		//flange-dependencies_platform-xxx.lst
		final Optional<String> foundPlatform = Optional.ofNullable(System.getProperty("flange.platform"))
				.or(() -> Optional.ofNullable(System.getenv("FLANGE_PLATFORM"))); //TODO use constants; refactor
		System.out.println("found platform: " + foundPlatform); //TODO delete
		foundPlatform.ifPresent(throwingConsumer(platform -> {
			dependenciesLoadedFromListResource(DEPENDENCIES_LIST_PLATFORM_RESOURCE_NAME_FORMAT.formatted(platform)).forEach(dependency -> { //TODO consolidate interpolation code
				final Class<?>[] interfaces = dependency.getInterfaces();
				final Stream<Class<?>> types = interfaces.length > 0 ? Stream.of(interfaces) : Stream.of(dependency);
				types.forEach(type -> dependenciesByType.put(type, dependency));
			});
		}));

		//TODO revamp; temporary hack to detect platform-specific dependencies when running from IDE or command line; probably fix using Maven plugin
		foundPlatform.ifPresent(throwingConsumer(platform -> {
			final Path localPlatformDependenciesListPath = Paths.get(System.getProperty("user.dir")).resolve("target").resolve("generated-sources")
					.resolve("annotations").resolve(DEPENDENCIES_LIST_PLATFORM_FILENAME_FORMAT.formatted(platform));
			if(exists(localPlatformDependenciesListPath)) {
				readAllLines(localPlatformDependenciesListPath).stream().map(DEPENDENCY_FOR_NAME).forEach(dependency -> { //TODO consolidate interpolation code
					final Class<?>[] interfaces = dependency.getInterfaces();
					final Stream<Class<?>> types = interfaces.length > 0 ? Stream.of(interfaces) : Stream.of(dependency);
					types.forEach(type -> dependenciesByType.put(type, dependency));
				});
			}
		}));
		return dependenciesByType.values();
	}

	/**
	 * Loads the classes in a resource definition list. The resource definition list consists of a UTF-8 encoded text file containing full class names of
	 * dependencies, separated by newlines. Duplicates are not allowed.
	 * @implSpec This implementation delegates calls {@link #loadDependenciesListResource(String)}.
	 * @param resourceName The name of the desired resource, relative to {@link AbstractDependencyConcern}.
	 * @return The lazily-loaded classes from the dependencies list resource; empty if the resource does not exist.
	 * @throws IOException If there is an I/O error loading the dependencies list.
	 * @throws DependencyException if there is an error loading one of the dependency classes.
	 * @see #DEPENDENCY_FOR_NAME
	 */
	static Stream<Class<?>> dependenciesLoadedFromListResource(@Nonnull final String resourceName) throws IOException { //TODO switch to SequencedSet in Java 21
		return loadDependenciesListResource(resourceName).stream().flatMap(Set::stream).map(DEPENDENCY_FOR_NAME);
	}

	/** Convenience function for loading a dependency class by its class names, throwing a {@link DependencyException} if the class could not be loaded.> */
	static final Function<String, Class<?>> DEPENDENCY_FOR_NAME = dependencyName -> {
		try {
			return Class.forName(dependencyName);
		} catch(final ClassNotFoundException classNotFoundException) {
			throw new DependencyException(classNotFoundException.getMessage(), classNotFoundException);
		}
	};

	/**
	 * Loads a resource definition list. The resource definition list consists of a UTF-8 encoded text file containing full class names of dependencies, separated
	 * by newlines. Duplicates are not allowed.
	 * @param resourceName The name of the desired resource, relative to {@link AbstractDependencyConcern}.
	 * @return A set of class names of dependencies loaded from the resource, which will not be present if the resource does not exist.
	 * @throws IOException If there is an I/O error loading the dependency list.
	 */
	static Optional<Set<String>> loadDependenciesListResource(@Nonnull final String resourceName) throws IOException { //TODO switch to SequencedSet in Java 21
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
