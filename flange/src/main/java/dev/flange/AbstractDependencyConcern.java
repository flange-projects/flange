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

import static java.util.Objects.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.locks.*;

import javax.annotation.*;

/**
 * Abstract base class for implementing a dependency concern backed by an existing library using an dependency injection container.
 * @param <C> The type of dependency injection container used by the underlying implementation.
 * @author Garret Wilson
 */
public abstract class AbstractDependencyConcern<C> implements DependencyConcern {

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
	private Boolean initialized = false; //set to null to indicate initialization error

	/**
	 * Returns the container, possibly in an uninitialized state.
	 * @return The dependency container, which may not yet be initialized.
	 */
	protected C getContainer() {
		if(initialized == null) {
			throw new IllegalStateException("Dependency container in incomplete initialization state.");
		}
		return container;
	}

	/**
	 * Returns the container, first ensuring that it has been initialized.
	 * @return The dependency container, guaranteed to be initialized.
	 */
	protected C getContainerInitialized() { //TODO consider adding an initialize/load invocation from Csar
		if(Boolean.FALSE.equals(initialized)) {
			containerInitializationLock.lock();
			try {
				if(Boolean.FALSE.equals(initialized)) { //check again under the lock to prevent multiple instantiations; could be null at this point
					initialized = null; //indicate initialization in progress
					try {
						initialize(container);
					} catch(final IOException ioException) { //TODO remove when initialize throws general dependency exception
						throw new UncheckedIOException(ioException);
					}
					initialized = true;
				}
			} finally {
				containerInitializationLock.unlock();
			}
		}
		if(initialized == null) { //could have already been set to null, or set to null when requesting the lock
			throw new IllegalStateException("Dependency container in incomplete initialization state.");
		}
		return container;
	}

	/**
	 * Initializes the container.
	 * @param container The container being initialized.
	 * @throws IOException If there is an I/O error loading container configuration information. TODO general dependency exception
	 */
	protected void initialize(@Nonnull final C container) throws IOException {
	}

}
