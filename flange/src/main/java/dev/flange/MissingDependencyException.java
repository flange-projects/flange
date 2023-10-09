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

import javax.annotation.*;

/**
 * An unchecked illegal state exception to indicate that a dependency could not be resolved.
 * @apiNote Analogous to Spring <code>org.springframework.beans.factory.NoSuchBeanDefinitionException</code>.
 * @author Garret Wilson
 */
public class MissingDependencyException extends DependencyException {

	private static final long serialVersionUID = 1L;

	/** The class indicating the type of the missing dependency. */
	private final Class<?> dependencyType; //TODO switch to a more comprehensive type representation, perhaps from Classmate

	/** @return The class indicating the type of the missing dependency. */
	public Class<?> getDependencyType() {
		return dependencyType;
	}

	/**
	 * Dependency type constructor.
	 * @param dependencyType The class indicating the type of the missing dependency
	 */
	public MissingDependencyException(@Nonnull final Class<?> dependencyType) {
		this(null, dependencyType);
	}

	/**
	 * Message and dependency type constructor.
	 * @param message An explanation of why the input could not be parsed, or <code>null</code> if no message should be used.
	 * @param dependencyType The class indicating the type of the missing dependency
	 */
	public MissingDependencyException(@Nullable final String message, @Nonnull final Class<?> dependencyType) {
		this(message, dependencyType, null);
	}

	/**
	 * Cause constructor. The message of the cause will be used if available.
	 * @param dependencyType The class indicating the type of the missing dependency
	 * @param cause The cause error or <code>null</code> if the cause is nonexistent or unknown.
	 */
	public MissingDependencyException(@Nonnull final Class<?> dependencyType, @Nullable final Throwable cause) {
		this(cause == null ? null : cause.toString(), dependencyType, cause);
	}

	/**
	 * Message, dependency type, and cause constructor.
	 * @param message An explanation of why the input could not be parsed, or <code>null</code> if no message should be used.
	 * @param dependencyType The class indicating the type of the missing dependency
	 * @param cause The cause error or <code>null</code> if the cause is nonexistent or unknown.
	 */
	public MissingDependencyException(@Nullable final String message, @Nonnull final Class<?> dependencyType, @Nullable final Throwable cause) {
		super(message, cause);
		this.dependencyType = requireNonNull(dependencyType);
	}

}
