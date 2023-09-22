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

import javax.annotation.*;

import io.csar.*;

/**
 * The concern for dependency registration and lookup—in essence, the Flange interface providing access to some dependency injection implementation.
 * @author Garret Wilson
 * @see Csar
 */
public interface DependencyConcern extends Concern {

	@Override
	public default Class<DependencyConcern> getConcernType() {
		return DependencyConcern.class;
	}

	/**
	 * Registers a dependency.
	 * @param dependencyClass The concrete class of the dependency to be instantiated when needed.
	 * @throws DependencyException if there is a general error registering the dependency.
	 */
	void registerDependency(@Nonnull final Class<?> dependencyClass); //TODO probably extract some registry interface and provide some less direct way to register dependencies, only allowed in certain states

	/**
	 * Returns an instance of a dependency that matches the given type.
	 * @param <T> The type of dependency desired.
	 * @param dependencyType The class representing the type of dependency.
	 * @return An instance of the requested dependency.
	 * @throws MissingDependencyException if an appropriate dependency of the requested type could not be found.
	 * @throws DependencyException if there is some general error retrieving or creating the dependency.
	 */
	<T> T getDependencyInstanceByType(@Nonnull Class<T> dependencyType) throws MissingDependencyException;

}
