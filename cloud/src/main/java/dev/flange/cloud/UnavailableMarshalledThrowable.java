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

import java.util.Optional;
import java.util.regex.*;

import javax.annotation.*;

/**
 * A placeholder exception for a marshalled throwable that could not be instantiated for some reason, such as its class not being available or its not having an
 * appropriate constructor.
 * @implSpec This throwable relies on using a message of the form <code>(<var>marshalledClassName</var>)</code> or
 *           <code>(<var>marshalledClassName</var>) <var>marshalledMessage</var></code> so that it may extract the marshalled class name and message if this
 *           exception itself is marshalled.
 * @implNote Even though a throwable may be unavailable in one layer, this exception may be re-marshalled and successfully unmarshalled at another layer in
 *           which the represented exception is in fact available.
 */
public class UnavailableMarshalledThrowable extends FlangeMarshalException {

	private static final long serialVersionUID = 1L;

	/** The description containing the class name and message of the marshalled throwable. */
	private final MarshalledThrowableDescription marshalledThrowableDescription;

	/** @return The description containing the class name and message of the marshalled throwable. */
	public MarshalledThrowableDescription getMarshalledThrowableDescription() {
		return marshalledThrowableDescription;
	}

	/**
	 * Marshalled class name and marshalled message constructor
	 * @param throwableClassName The name of the class of the original throwable as was marshalled.
	 * @param throwableMessage The detail message of the original throwable as was marshalled, which may be <code>null</code>.
	 * @throws IllegalArgumentException if the marshalled class name contains a <code>)</code> character.
	 */
	public UnavailableMarshalledThrowable(@Nonnull final String throwableClassName, @Nullable final String throwableMessage) {
		this(throwableClassName, throwableMessage, null); //construct the exception with no cause
	}

	/**
	 * Marshalled class name, marshalled message, and cause constructor.
	 * @param marshalledClassName The name of the class of the original throwable as was marshalled.
	 * @param marshalledMessage The detail message of the original throwable as was marshalled, which may be <code>null</code>.
	 * @param marshalledCause The cause of the marshalled throwable, which becomes the cause of this exception and accessible via its {@link Throwable#getCause()}
	 *          method.
	 * @throws IllegalArgumentException if the marshalled class name contains a <code>)</code> character.
	 */
	public UnavailableMarshalledThrowable(@Nonnull final String marshalledClassName, @Nullable final String marshalledMessage,
			@Nullable final Throwable marshalledCause) {
		this(new MarshalledThrowableDescription(marshalledClassName, marshalledMessage), marshalledCause);
	}

	/**
	 * Marshalled throwable description and cause constructor.
	 * @param marshalledThrowableDescription The description containing the class name and detail message of the original throwable as was marshalled.
	 * @param cause The cause (which is saved for later retrieval by the {@link Throwable#getCause()} method), or <code>null</code> if the cause is nonexistent or
	 *          unknown.
	 */
	public UnavailableMarshalledThrowable(@Nonnull final MarshalledThrowableDescription marshalledThrowableDescription, @Nullable final Throwable cause) {
		super(marshalledThrowableDescription.toMessage(), cause);
		this.marshalledThrowableDescription = requireNonNull(marshalledThrowableDescription);
	}

	/**
	 * Full message and cause constructor.
	 * @apiNote This constructor is provided primarily to allow unmarshalling of this class itself it is has been marshalled.
	 * @param message The detail message (which is saved for later retrieval by the {@link Throwable#getMessage()} method).
	 * @param cause The cause (which is saved for later retrieval by the {@link Throwable#getCause()} method), or <code>null</code> if the cause is nonexistent or
	 *          unknown.
	 * @throws NullPointerException if the given message is <code>null</code>.
	 * @throws IllegalArgumentException if the given message is not in the form <code>(<var>marshalledClassName</var>)</code> or
	 *           <code>(<var>marshalledClassName</var>) <var>marshalledMessage</var></code>.
	 */
	public UnavailableMarshalledThrowable(@Nonnull final String message, @Nullable final Throwable cause) {
		this(MarshalledThrowableDescription.fromMessage(message), cause);
	}

	/**
	 * Encapsulates the marshalled class name and optional marshalled message.
	 * @param marshalledClassName The name of the class of the original throwable as was marshalled.
	 * @param marshalledMessage The detail message of the original throwable as was marshalled, which may be <code>null</code>.
	 */
	public record MarshalledThrowableDescription(@Nonnull String marshalledClassName, @Nullable String marshalledMessage) {

		/**
		 * The character prohibited from appearing in the marshalled class name, which would make parsing ambiguous.
		 */
		private static final char INVALID_MARSHALLED_CLASS_NAME_CHARACTER = ')';

		/**
		 * The pattern for extracting the marshalled class name (group <code>1</code>) and marshalled message (nullable group <code>2</code>) from the full message.
		 */
		private static final Pattern MESSAGE_PATTERN = Pattern.compile("\\(([^)]*)\\)(?: (.*))?");

		/** @return The detail message of the original throwable as was marshalled which may or may not be present. */
		public Optional<String> findMarshalledMessage() {
			return Optional.ofNullable(marshalledMessage());
		}

		/**
		 * Validation constructor.
		 * @param marshalledClassName The name of the class of the original throwable as was marshalled.
		 * @param marshalledMessage The detail message of the original throwable as was marshalled, which may be <code>null</code>.
		 * @throws IllegalArgumentException if the marshalled class name contains a <code>)</code> character.
		 */
		public MarshalledThrowableDescription {
			requireNonNull(marshalledClassName);
			if(marshalledClassName.indexOf(INVALID_MARSHALLED_CLASS_NAME_CHARACTER) >= 0) {
				throw new IllegalArgumentException(
						"Marshalled class name `%s` cannot contain `%s`.".formatted(marshalledClassName, INVALID_MARSHALLED_CLASS_NAME_CHARACTER));
			}
		}

		/**
		 * Parses a message description from the full message text.
		 * @implSpec This implementation produces a message in the form <code>(<var>marshalledClassName</var>)</code> or
		 *           <code>(<var>marshalledClassName</var>) <var>marshalledMessage</var></code>.
		 * @param message The message to be parsed; typically the full message of an existing {@link UnavailableMarshalledThrowable}.
		 * @return The description containing the parsed components.
		 * @see #toMessage()
		 */
		public static MarshalledThrowableDescription fromMessage(@Nonnull final CharSequence message) {
			final Matcher matcher = MESSAGE_PATTERN.matcher(message);
			if(!matcher.matches()) {
				throw new IllegalArgumentException("Marshalled class name and marshalled message could not be parsed from message `%s`.".formatted(message));
			}
			return new MarshalledThrowableDescription(matcher.group(1), matcher.group(2));
		}

		/**
		 * Constructs a detail message for {@link UnavailableMarshalledThrowable} that contains the description information and can later be parsed back out.
		 * @return A string form of the description appropriate for an exception message.
		 * @see #fromMessage(CharSequence)
		 */
		public String toMessage() {
			final StringBuilder stringBuilder = new StringBuilder();
			stringBuilder.append('(').append(marshalledClassName()).append(')');
			findMarshalledMessage().ifPresent(marshalledMessage -> stringBuilder.append(' ').append(marshalledMessage));
			return stringBuilder.toString();
		}

	}

}
