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

package dev.flange.cloud.aws;

import static java.net.HttpURLConnection.*;
import static java.util.Collections.*;
import static java.util.Objects.*;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.*;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.globalmentor.java.StackTrace;

import dev.flange.cloud.FlangeMarshalException;
import io.clogr.Clogged;

/**
 * Definitions and utilities for working with <a href="https://aws.amazon.com/lambda/">AWS Lambda</a>.
 * @author Garret Wilson
 */
public final class AwsLambda {

	/**
	 * The HTTP status code of <code>200</code> for the <code>RequestResponse</code> invocation type.
	 * @see software.amazon.awssdk.services.lambda.model.InvokeResponse#statusCode()
	 */
	public static final int STATUS_CODE_REQUEST_RESPONSE_SUCCESS = HTTP_OK;

	/**
	 * The HTTP status code of <code>202</code> for the <code>Event</code> invocation type.
	 * @see software.amazon.awssdk.services.lambda.model.InvokeResponse#statusCode()
	 */
	public static final int STATUS_CODE_EVENT_SUCCESS = HTTP_ACCEPTED;

	/**
	 * The HTTP status code of <code>204</code> for the <code>DryRun</code> invocation type.
	 * @see software.amazon.awssdk.services.lambda.model.InvokeResponse#statusCode()
	 */
	public static final int STATUS_CODE_DRY_RUN_SUCCESS = HTTP_NO_CONTENT;

	/**
	 * The identifier for an unhandled invoke response function error.
	 * @see software.amazon.awssdk.services.lambda.model.InvokeResponse#functionError()
	 */
	public static final String FUNCTION_ERROR_UNHANDLED = "Unhandled";

	/**
	 * Deserializes an AWS Lambda unhandled error marshalled as JSON, typically in the payload of a lambda invocation response.
	 * @param inputStream The input stream from which to read the value.
	 * @return The the unmarshalled unhandled error.
	 * @throws IOException If an I/O error occurs.
	 * @throws FlangeMarshalException if a data conversion or mapping exception occurs.
	 */
	public static UnhandledError unmarshalUnhandledError(@Nonnull final InputStream inputStream) throws IOException, FlangeMarshalException {
		try {
			return Marshalling.jsonReaderForType(UnhandledError.class).readValue(inputStream);
		} catch(final StreamReadException | DatabindException jacksonException) {
			throw new FlangeMarshalException("Error unmarshalling value.", jacksonException);
		}
	}

	/**
	 * Represents an AWS Lambda unhandled error as marshalled in the response body when the invoke response indicates {@value AwsLambda#FUNCTION_ERROR_UNHANDLED}.
	 * @see <a href="https://docs.aws.amazon.com/lambda/latest/dg/java-exceptions.html">AWS Lambda function errors in Java</a>
	 */
	public record UnhandledError(@Nullable String errorMessage, @Nonnull String errorType, @Nonnull List<String> stackTrace, @Nullable UnhandledError cause)
			implements Clogged {

		/**
		 * Validating constructor.
		 * @implSpec Ensures that the error type is not <code>null</code>, and converts any <code>null</code> stack trace to an empty list (e.g. for a missing stack
		 *           trace in the serialization).
		 * @param errorMessage The error message.
		 * @param errorType The name of the class of the type of error.
		 * @param stackTrace The string forms of the stack trace elements.
		 * @param cause The unhandled error cause, or <code>null</code> if not present.
		 */
		public UnhandledError {
			requireNonNull(errorType);
			if(stackTrace == null) {
				stackTrace = emptyList();
			}
		}

		/**
		 * Constructs an instance of this class from an existing {@link Throwable} and a series of stack trace strings. The cause and stack trace of the given
		 * throwable are ignored.
		 * @apiNote This factory method is useful for constructing an expected unhandled error instance for testing.
		 * @param throwable The throwable from which to create the unhandled error.
		 * @param stackTrace The string serializations of the stack trace.
		 * @return An instance of an unhandled error from the given information.
		 */
		static UnhandledError fromThrowable(@Nonnull final Throwable throwable, @Nonnull final String... stackTrace) {
			return fromThrowable(throwable, null, stackTrace);
		}

		/**
		 * Constructs an instance of this class from an existing {@link Throwable} and a series of stack trace strings. The cause and stack trace of the given
		 * throwable are ignored.
		 * @apiNote This factory method is useful for constructing an expected unhandled error instance for testing.
		 * @param throwable The throwable from which to create the unhandled error.
		 * @param cause The unhandled error cause, or <code>null</code> if not present.
		 * @param stackTrace The string serializations of the stack trace.
		 * @return An instance of an unhandled error from the given information.
		 */
		static UnhandledError fromThrowable(@Nonnull final Throwable throwable, @Nullable UnhandledError cause, @Nonnull final String... stackTrace) {
			return new UnhandledError(throwable.getMessage(), throwable.getClass().getName(), List.of(stackTrace), cause);
		}

		/**
		 * Constructs an instance of this class from an existing {@link Throwable} and a series of stack trace strings. The cause and stack trace of the given
		 * throwable are ignored.
		 * @apiNote This factory method is useful for constructing an expected unhandled error instance for testing. In particular this factory method uses a more
		 *          natural order for stack traces and nested causes.
		 * @param throwable The throwable from which to create the unhandled error.
		 * @param cause The unhandled error cause, or <code>null</code> if not present.
		 * @param stackTrace The string serializations of the stack trace.
		 * @return An instance of an unhandled error from the given information.
		 */
		static UnhandledError fromThrowable(@Nonnull final Throwable throwable, @Nonnull List<String> stackTrace, @Nullable UnhandledError cause) {
			return fromThrowable(throwable, cause, stackTrace.toArray(String[]::new)); //passing an array to the other factory method effectively makes a defensive copy
		}

		/**
		 * Creates an appropriate throwable to represent this unhandled error.
		 * @implNote If the actual throwable indicated by {@link #errorType} cannot be instantiated for some reason (e.g. its class cannot be found or it does not
		 *           have an appropriate constructor), a placeholder exception will be returned.
		 * @return A throwable representing the information in this unhandled error.
		 */
		public Throwable toThrowable() {
			//create bare `Throwable`
			final Throwable throwable = createThrowable();
			//set stack trace
			final AtomicBoolean hasUnparseableStackTraceElement = new AtomicBoolean(false);
			final StackTraceElement[] stackTraceElements = stackTrace().stream().map(stackTraceElementString -> {
				try {
					return StackTrace.parseElement(stackTraceElementString);
				} catch(final IllegalArgumentException IllegalArgumentException) { //unparsable stack trace element
					if(hasUnparseableStackTraceElement.compareAndSet(false, true)) { //only log one warning per stack trace (if one doesn't parse, multiple elements probably won't parse)
						getLogger().atWarn().log("Unable to parse AWS Lambda marshalled stack trace element `{}`.", stackTraceElementString);
					}
					return new StackTraceElement("Unparsable", "seeLog", null, -1); //create an artificial stack trace element to represent the unparsable stack trace element
				}
			}).toArray(StackTraceElement[]::new);
			throwable.setStackTrace(stackTraceElements);
			return throwable;

		}

		/**
		 * Creates an appropriate bare throwable to represent this unhandled error. The cause will have been set, but the stack trace will not yet have been
		 * updated.
		 * @implNote This implementation supports instances of {@link Throwable} with a constructor in the form <code>(String message, final Throwable cause)</code>
		 *           or, if this class has no {@link #cause()} specified, the form <code>(String message, final Throwable cause)</code>. If the actual throwable
		 *           indicated by {@link #errorType} cannot be instantiated for some reason (e.g. its class cannot be found or it does not have an appropriate
		 *           constructor), a placeholder exception will be returned.
		 * @return A bare throwable from the information in this unhandled error, which may be {@link PlaceholderThrowable} if the indicated exception cannot be
		 *         created.
		 */
		Throwable createThrowable() {
			@Nullable
			final Throwable throwableCause = cause != null ? cause.toThrowable() : null;
			try {
				final String errorType = errorType();
				final Class<? extends Throwable> throwableClass;
				try {
					final Class<?> classForErrorType = Class.forName(errorType);
					if(!Throwable.class.isAssignableFrom(classForErrorType)) { //we expect AWS Lambda to only give us errors that are of type `Throwable`
						getLogger().atWarn().log("Marshalled AWS Lambda error type `{}` is not a `Throwable` type; using placeholder.", errorType);
						return new PlaceholderThrowable(errorType, errorMessage(), throwableCause);
					}
					@SuppressWarnings("unchecked")
					final Class<? extends Throwable> classForThrowableErrorType = (Class<? extends Throwable>)classForErrorType;
					throwableClass = classForThrowableErrorType;
				} catch(final ClassNotFoundException classNotFoundException) {
					//It's not unexpected that some classes thrown remotely might not be available on the client side;
					//just use a placeholder—no warning needed.
					return new PlaceholderThrowable(errorType(), errorMessage(), throwableCause);
				}

				//Try to find a constructor in the following order (assuming purposes of the parameters, as most exceptions follow this pattern):
				//* `(String message, final Throwable cause)`
				//* `(String message)` (only if `throwableCause` is `null` 
				try {
					final Constructor<? extends Throwable> messageCauseConstructor = throwableClass.getDeclaredConstructor(String.class, Throwable.class);
					return messageCauseConstructor.newInstance(errorMessage(), throwableCause);
				} catch(final NoSuchMethodException noSuchMethodException) {
					if(throwableCause == null) { //if we don't have a cause, try to construct with just the message
						final Constructor<? extends Throwable> messageConstructor = throwableClass.getDeclaredConstructor(String.class);
						return messageConstructor.newInstance(errorMessage());
					}
					throw noSuchMethodException; //otherwise don't even attempt a message-only constructor; let the outer `catch` deal with it
				}
			} catch(final LinkageError | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
					| NoSuchMethodException | SecurityException creationException) {
				getLogger().atWarn().log("Marshalled AWS Lambda error type `{}` could not be created; using placeholder.", errorType);
				return new PlaceholderThrowable(errorType, errorMessage(), throwableCause);
			}
		}

		/**
		 * A placeholder exception for a throwable that could not be instantiated for some reason, such as its class not being available or its not having an
		 * appropriate constructor.
		 * @implNote This exception itself does not have the required constructors for unmarshalling and converting to a throwable, so if it were marshalled it
		 *           would be in turn wrapped in another placeholder exception.
		 */
		public static class PlaceholderThrowable extends RuntimeException {

			private static final long serialVersionUID = 1L;

			/** The name of the class of the throwable being represented by this placeholder. */
			private final String throwableClassName;

			/** @return The name of the class of the throwable being represented by this placeholder. */
			@Nonnull
			public String getMarshalledClassName() {
				return throwableClassName;
			}

			/** The detail message intended for the represented throwable. */
			private final String throwableMessage;

			/** @return The detail message intended for the represented throwable. */
			@Nullable
			public String getThrowableMessage() {
				return throwableMessage;
			}

			/**
			 * Throwable class name and throwable message.
			 * @param throwableClassName The name of the class of the throwable being represented by this placeholder.
			 * @param throwableMessage The detail message intended for the represented throwable.
			 */
			public PlaceholderThrowable(@Nonnull final String throwableClassName, @Nullable final String throwableMessage) {
				this(throwableClassName, throwableMessage, null); //construct the exception with no cause
			}

			/**
			 * Throwable class name, throwable message, and cause constructor.
			 * @param throwableClassName The name of the class of the throwable being represented by this placeholder.
			 * @param throwableMessage The detail message intended for the represented throwable.
			 * @param throwableCause The cause intended for the represented throwable.
			 */
			public PlaceholderThrowable(@Nonnull final String throwableClassName, @Nullable final String throwableMessage, @Nullable final Throwable throwableCause) {
				super(throwableClassName + (throwableMessage != null ? ": " + throwableMessage : ""), throwableCause); //`com.example.Throwable`/`com.example.Throwable: <throwableMessage>`
				this.throwableClassName = requireNonNull(throwableClassName);
				this.throwableMessage = throwableMessage;
			}

		}

	}

}
