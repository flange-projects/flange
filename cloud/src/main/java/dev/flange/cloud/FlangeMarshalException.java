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

import java.io.IOException;

/**
 * A Flange cloud infrastructure exception representing a problem related to serializing, deserializing, and/or conversion of data for transferring across a
 * "perforation". In contexts where a method also throws an {@link IOException}, this exception represents marshalling errors not purely related to I/O. In
 * contexts where an {@link IOException} is not thrown, this exception may wrap an {@link IOException} that occurred during marshalling.
 * @author Garret Wilson
 */
public class FlangeMarshalException extends FlangeCloudException {

	private static final long serialVersionUID = 1L;

	/** Constructs a new exception with no detail message. */
	public FlangeMarshalException() {
		super();
	}

	/**
	 * Constructs a new exception with the specified detail message.
	 * @param message The detail message.
	 */
	public FlangeMarshalException(final String message) {
		super(message);
	}

	/**
	 * Constructs a new exception with the specified detail message and cause.
	 * @param message The detail message (which is saved for later retrieval by the {@link Throwable#getMessage()} method).
	 * @param cause The cause (which is saved for later retrieval by the {@link Throwable#getCause()} method), or <code>null</code> if the cause is nonexistent or
	 *          unknown.
	 */
	public FlangeMarshalException(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructs a new exception with the specified cause and a detail message of <code>(cause==null ? null : cause.toString())</code>.
	 * @param cause The cause (which is saved for later retrieval by the {@link Throwable#getCause()} method), or <code>null</code> if the cause is nonexistent or
	 *          unknown.
	 */
	public FlangeMarshalException(final Throwable cause) {
		super(cause);
	}

}
