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
 * An unchecked illegal state exception to indicate a general problem with dependencies.
 * @apiNote Analogous to Spring <code>org.springframework.beans.BeansException</code>.
 * @author Garret Wilson
 */
public class DependencyException extends IllegalStateException { //TODO maybe extend ConfiguredStateException 

	private static final long serialVersionUID = 1L;

	/** Constructor with no message. */
	public DependencyException() {
		this((String)null);
	}

	/**
	 * Message constructor.
	 * @param message An explanation of why the input could not be parsed, or <code>null</code> if no message should be used.
	 */
	public DependencyException(@Nullable final String message) {
		this(message, null);
	}

	/**
	 * Cause constructor. The message of the cause will be used if available.
	 * @param cause The cause error or <code>null</code> if the cause is nonexistent or unknown.
	 */
	public DependencyException(@Nullable final Throwable cause) {
		this(cause == null ? null : cause.toString(), cause);
	}

	/**
	 * Message and cause constructor.
	 * @param message An explanation of why the input could not be parsed, or <code>null</code> if no message should be used.
	 * @param cause The cause error or <code>null</code> if the cause is nonexistent or unknown.
	 */
	public DependencyException(@Nullable final String message, @Nullable final Throwable cause) {
		super(message, cause);
	}

}
