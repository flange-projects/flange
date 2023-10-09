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

import java.util.Optional;

import javax.annotation.*;

import io.csar.*;

/**
 * Flange is a dependency management library providing dependency registration and lookup using a {@link DependencyConcern} dependency injection implementation
 * via {@link Csar}.
 * <p>
 * Flange uses the Csar {@link ConcernProvider} mechanism, so that an application can have access to a globally configured default Flange implementation simply
 * by including that implementation's dependency. For example merely including the dependency
 * <code>io.flange:flange-foo-provider:<var>x</var>.<var>x</var>.<var>x</var></code> will automatically provide dependency injection backed by the FooBar
 * (example) library. Bootstrap classes desiring dependency lookup may then then implement {@link Flanged} for simplified retrieval of dependency instances by
 * type.
 * </p>
 * <p>
 * More complex configuration may be done by manual configuration using {@link Flange#setDefaultDependencyConcern(DependencyConcern)} with the concern of
 * choice, as in the following example:
 * </p>
 * 
 * <pre>
 * {@code
 * Flange.setDefaultDependencyConcern(new MyDependencyInjectionSupportImpl());
 * }
 * </pre>
 * @author Garret Wilson
 * @see Csar
 */
public class Flange {

	/**
	 * Returns the default dependency concern.
	 * @return The default dependency concern.
	 * @see Csar#findDefaultConcern(Class)
	 */
	public static Optional<DependencyConcern> findDefaultDependencyConcern() {
		return Csar.findDefaultConcern(DependencyConcern.class);
	}

	/**
	 * Sets the default dependency concern.
	 * @param dependencyConcern The default dependency concern to set.
	 * @return The previous concern, or <code>null</code> if there was no previous concern.
	 * @throws NullPointerException if the given concern is <code>null</code>.
	 * @see Csar#registerDefaultConcern(Class, Concern)
	 */
	public static Optional<DependencyConcern> setDefaultDependencyConcern(@Nonnull final DependencyConcern dependencyConcern) {
		return Csar.registerDefaultConcern(DependencyConcern.class, dependencyConcern);
	}

	/**
	 * Returns the configured dependency concern for the current context.
	 * @return The configured dependency concern for the current context.
	 * @throws ConcernNotFoundException if no concern of the requested type could be found.
	 * @see Csar#getConcern(Class)
	 */
	public static @Nonnull DependencyConcern getDependencyConcern() {
		return Csar.getConcern(DependencyConcern.class);
	}

}
