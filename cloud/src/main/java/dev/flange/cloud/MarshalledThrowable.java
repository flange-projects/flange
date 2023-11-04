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

import static java.util.Objects.*;

import javax.annotation.*;

/**
 * A wrapper exception used to marshal exceptions thrown by a remote invocation. The actual throwable being marshalled is accessible via {@link #getCause()}.
 * @author Garret Wilson
 * @see java.lang.reflect.InvocationTargetException
 * @see java.util.concurrent.ExecutionException
 * @see java.util.concurrent.CompletionException
 */
public class MarshalledThrowable extends FlangeCloudException {

	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new exception with the specified detail message and cause.
	 * @param message The detail message (which is saved for later retrieval by the {@link Throwable#getMessage()} method).
	 * @param cause The marshalled throwable, which is saved for later retrieval by the {@link Throwable#getCause()} method.
	 * @throws NullPointerException if the given cause is <code>null</code>.
	 */
	public MarshalledThrowable(@Nullable final String message, @Nonnull final Throwable cause) {
		super(message, requireNonNull(cause));
	}

	/**
	 * Constructs a new exception with the specified cause and a detail message of <code>(cause==null ? null : cause.toString())</code>.
	 * @param cause The marshalled throwable, which is saved for later retrieval by the {@link Throwable#getCause()} method.
	 * @throws NullPointerException if the given cause is <code>null</code>.
	 */
	public MarshalledThrowable(@Nonnull final Throwable cause) {
		super(requireNonNull(cause));
	}

}
