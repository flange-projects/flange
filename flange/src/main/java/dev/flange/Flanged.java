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

/**
 * Mixin interface to provide quick-and-easy dependency support to a class.
 * <p>
 * A class implementing this interface can simply call the {@link #getDependencyInstanceByType(Class)} to look up a resource.
 * </p>
 * @apiNote Use of this interface is normally only appropriate in a bootstrap context to retrieve the root of a dependency tree. Looking up a dependency in the
 *          middle of a dependency tree is usually a manifestation of the <dfn>dependency service locator anti-pattern</dfn>.
 * @author Garret Wilson
 */
public interface Flanged {

	/**
	 * Returns an instance of a dependency that matches the given type.
	 * @param <T> The type of dependency desired.
	 * @param dependencyType The class representing the type of dependency.
	 * @return An instance of the requested dependency.
	 * @throws MissingDependencyException if an appropriate dependency of the requested type could not be found.
	 * @throws DependencyException if there is some general error retrieving or creating the dependency.
	 */
	default <T> T getDependencyInstanceByType(@Nonnull Class<T> dependencyType) throws MissingDependencyException {
		return Flange.getDependencyConcern().getDependencyInstanceByType(dependencyType);
	}

}
