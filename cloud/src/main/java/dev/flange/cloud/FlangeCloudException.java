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

package dev.flange.cloud;

/**
 * A general base unchecked exception representing a problem with cloud infrastructure, such as provisioning errors, marshalling errors, or cloud SDK errors
 * distinct from purely I/O communication problems.
 * @author Garret Wilson
 */
public class FlangeCloudException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** Constructs a new exception with no detail message. */
	public FlangeCloudException() {
		super();
	}

	/**
	 * Constructs a new exception with the specified detail message.
	 * @param message The detail message.
	 */
	public FlangeCloudException(final String message) {
		super(message);
	}

	/**
	 * Constructs a new exception with the specified detail message and cause.
	 * @param message The detail message (which is saved for later retrieval by the {@link Throwable#getMessage()} method).
	 * @param cause The cause (which is saved for later retrieval by the {@link Throwable#getCause()} method), or <code>null</code> if the cause is nonexistent or
	 *          unknown.
	 */
	public FlangeCloudException(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a new exception with the specified cause and a detail message of <code>(cause==null ? null : cause.toString())</code>.
	 * @param cause The cause (which is saved for later retrieval by the {@link Throwable#getCause()} method), or <code>null</code> if the cause is nonexistent or
	 *          unknown.
	 */
	public FlangeCloudException(final Throwable cause) {
		super(cause);
	}

}
